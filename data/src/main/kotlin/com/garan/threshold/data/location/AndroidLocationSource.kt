package com.garan.threshold.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.port.LocationSource
import com.garan.threshold.core.port.LocationTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlin.coroutines.resume

/**
 * Android platform [LocationManager] implementation of [LocationSource].
 *
 * Requires zero Google Play Services dependencies and enforces strict power-efficiency invariants:
 * 1. **Cache-first (`getLastKnownLocation`):** Reads cached fixes across available providers (`fused`,
 *    `network`, `gps`, `passive`) with zero radio wake-up when a fix is fresher than [STALE_FIX_THRESHOLD_MS] (30s).
 * 2. **Single Low-Power Provider Selection:** When a refresh is needed, queries only the single lowest-power
 *    enabled provider (`FUSED_PROVIDER` with `QUALITY_BALANCED_POWER_ACCURACY` -> `NETWORK_PROVIDER` -> `GPS_PROVIDER`
 *    only if neither Fused nor Network is enabled).
 * 3. **In-Flight Deduplication & Cooldown Throttle:** Guarantees at most 1 active OS location request per
 *    [MIN_ACTIVE_REQUEST_INTERVAL_MS] (30s), preventing `ON_START` + `ON_RESUME` or rapid app bounces from
 *    triggering redundant scans.
 * 4. **Bounded 5-Second Hard Cutoff:** Every active request is bounded by [ACTIVE_REQUEST_TIMEOUT_MS] (5s)
 *    with guaranteed `CancellationSignal.cancel()` and `removeUpdates()` cleanup.
 * 5. **Rolling 1h / 24h Telemetry:** Tracks active checks, cache hits, throttled skips, timeouts, provider,
 *    and fix latency in [telemetry].
 */
class AndroidLocationSource(context: Context, private val clockMs: () -> Long = System::currentTimeMillis) :
    LocationSource {

    private val appContext = context.applicationContext ?: context
    private val locationManager: LocationManager? =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val telemetryPrefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentLocation = MutableStateFlow<LocationFix?>(null)
    override val currentLocation: StateFlow<LocationFix?> = _currentLocation.asStateFlow()

    private val _telemetry = MutableStateFlow(LocationTelemetry())
    override val telemetry: StateFlow<LocationTelemetry> = _telemetry.asStateFlow()

    private val isRefreshInFlight = AtomicBoolean(false)
    private val activeListenersCount = AtomicInteger(0)

    @Volatile
    private var lastActiveRequestWallClockMs: Long = 0L

    private val telemetryLock = Any()
    private val activeRequestTimestampsMs = ArrayDeque<Long>()
    private val cacheHitTimestampsMs = ArrayDeque<Long>()
    private val throttledSkipTimestampsMs = ArrayDeque<Long>()
    private val timeoutTimestampsMs = ArrayDeque<Long>()
    private var lastProviderUsed: String? = null
    private var lastFixLatencyMs: Long? = null

    init {
        loadPersistedActiveTimestamps()
    }

    internal val activeListenerCountForTesting: Int
        get() = activeListenersCount.get()

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationServicesEnabled(): Boolean {
        val manager = locationManager ?: return false
        return runCatching { LocationManagerCompat.isLocationEnabled(manager) }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    override suspend fun refreshLocation(): LocationFix? {
        if (!hasLocationPermission() || !isLocationServicesEnabled()) {
            _currentLocation.value = null
            return null
        }

        val manager = locationManager ?: run {
            _currentLocation.value = null
            return null
        }

        val candidateProviders = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
            runCatching { addAll(manager.allProviders) }
        }.distinct()

        var bestLocation: Location? = null
        for (provider in candidateProviders) {
            val candidate = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (isValidCoordinate(candidate)) {
                if (bestLocation == null || isNewer(candidate, bestLocation)) {
                    bestLocation = candidate
                }
            }
        }

        if (bestLocation != null) {
            val fix = LocationFix(
                latitude = bestLocation.latitude,
                longitude = bestLocation.longitude,
            )
            _currentLocation.value = fix

            if (isStale(bestLocation)) {
                triggerBoundedCurrentLocationRefresh(manager)
            } else {
                recordCacheHit(bestLocation.provider)
            }
            return fix
        }

        // If no lastKnownLocation is available yet, request a current fix (waiting up to 1.5s for UI,
        // while the bounded 5s request continues on the main looper if needed).
        val freshFix = requestCurrentFixInternal(manager, waitTimeoutMs = 1_500L)
        if (freshFix != null) {
            _currentLocation.value = freshFix
            return freshFix
        }

        // Retain existing fix if Location Services & permission are still active during temporary signal loss
        return _currentLocation.value
    }

    @SuppressLint("MissingPermission")
    private fun triggerBoundedCurrentLocationRefresh(
        manager: LocationManager,
        onFixResolved: ((LocationFix?) -> Unit)? = null,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onFixResolved?.invoke(null)
            return
        }

        val provider = selectPreferredProvider(manager)
        if (provider == null) {
            onFixResolved?.invoke(null)
            return
        }

        val nowMs = clockMs()
        val withinCooldown = lastActiveRequestWallClockMs > 0L &&
            nowMs >= lastActiveRequestWallClockMs &&
            (nowMs - lastActiveRequestWallClockMs) < MIN_ACTIVE_REQUEST_INTERVAL_MS

        if (withinCooldown || !isRefreshInFlight.compareAndSet(false, true)) {
            recordThrottledSkip()
            onFixResolved?.invoke(null)
            return
        }

        lastActiveRequestWallClockMs = nowMs
        recordActiveRequestStarted(provider, nowMs)

        val completed = AtomicBoolean(false)
        val cancellationSignal = CancellationSignal()
        var registeredListener: LocationListener? = null

        val timeoutRunnable = Runnable {
            if (completed.compareAndSet(false, true)) {
                registeredListener?.let { listener ->
                    runCatching { manager.removeUpdates(listener) }
                    activeListenersCount.decrementAndGet()
                }
                runCatching { cancellationSignal.cancel() }
                isRefreshInFlight.set(false)
                recordTimeout(provider)
                onFixResolved?.invoke(null)
            }
        }

        val handleLocationResult: (Location?) -> Unit = { location ->
            if (location != null && isValidCoordinate(location) && !isStale(location)) {
                if (completed.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    registeredListener?.let { listener ->
                        runCatching { manager.removeUpdates(listener) }
                        activeListenersCount.decrementAndGet()
                    }
                    runCatching { cancellationSignal.cancel() }
                    isRefreshInFlight.set(false)

                    val latencyMs = (clockMs() - nowMs).coerceAtLeast(0L)
                    recordFixResolved(provider, latencyMs)

                    if (hasLocationPermission() && isLocationServicesEnabled()) {
                        val fix = LocationFix(location.latitude, location.longitude)
                        _currentLocation.value = fix
                        onFixResolved?.invoke(fix)
                    } else {
                        onFixResolved?.invoke(null)
                    }
                }
            }
        }

        val oneShotListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleLocationResult(location)
            }
        }
        registeredListener = oneShotListener

        mainHandler.postDelayed(timeoutRunnable, ACTIVE_REQUEST_TIMEOUT_MS)

        runCatching {
            activeListenersCount.incrementAndGet()
            manager.requestLocationUpdates(
                provider,
                0L,
                0f,
                oneShotListener,
                Looper.getMainLooper(),
            )
        }.onFailure {
            activeListenersCount.decrementAndGet()
        }

        runCatching {
            val executor = ContextCompat.getMainExecutor(appContext)
            val consumer = Consumer<Location?> { location -> handleLocationResult(location) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val request = LocationRequest.Builder(0L)
                    .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                    .setDurationMillis(ACTIVE_REQUEST_TIMEOUT_MS)
                    .build()
                manager.getCurrentLocation(
                    provider,
                    request,
                    cancellationSignal,
                    executor,
                    consumer,
                )
            } else {
                manager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    executor,
                    consumer,
                )
            }
        }
    }

    private suspend fun requestCurrentFixInternal(manager: LocationManager, waitTimeoutMs: Long): LocationFix? =
        withTimeoutOrNull(waitTimeoutMs) {
            suspendCancellableCoroutine { cont ->
                triggerBoundedCurrentLocationRefresh(manager) { fix ->
                    if (!cont.isCompleted) {
                        cont.resume(fix)
                    }
                }
            }
        }

    /**
     * Selects the single lowest-power active location provider.
     *
     * Priority:
     * 1. [LocationManager.FUSED_PROVIDER] (API 31+, Wi-Fi/Cell/Sensors with balanced power)
     * 2. [LocationManager.NETWORK_PROVIDER] (Wi-Fi/Cell triangulation, milliwatts)
     * 3. [LocationManager.GPS_PROVIDER] (Fallback only when neither Fused nor Network is enabled)
     */
    internal fun selectPreferredProvider(manager: LocationManager): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)
        ) {
            return LocationManager.FUSED_PROVIDER
        }
        if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
            return LocationManager.NETWORK_PROVIDER
        }
        if (runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
            return LocationManager.GPS_PROVIDER
        }
        return null
    }

    private fun recordCacheHit(provider: String?) {
        val now = clockMs()
        synchronized(telemetryLock) {
            cacheHitTimestampsMs.addLast(now)
            trimDeque(cacheHitTimestampsMs, now, WINDOW_24H_MS, MAX_EVENTS_BUFFER)
            if (provider != null && lastProviderUsed == null) {
                lastProviderUsed = provider
            }
            publishTelemetryLocked(now)
        }
    }

    private fun recordActiveRequestStarted(provider: String, now: Long) {
        synchronized(telemetryLock) {
            activeRequestTimestampsMs.addLast(now)
            trimDeque(activeRequestTimestampsMs, now, WINDOW_24H_MS, MAX_EVENTS_BUFFER)
            lastProviderUsed = provider
            persistActiveTimestampsLocked()
            val snapshot = publishTelemetryLocked(now)
            runCatching {
                Log.d(
                    LOG_TAG,
                    "event=ACTIVE_REQUEST provider=$provider " +
                        "active1h=${snapshot.activeChecksLastHour} active24h=${snapshot.activeChecksLast24h} " +
                        "cached24h=${snapshot.cacheHitsLast24h} throttled1h=${snapshot.throttledSkipsLastHour}",
                )
            }
        }
    }

    private fun recordFixResolved(provider: String, latencyMs: Long) {
        val now = clockMs()
        synchronized(telemetryLock) {
            lastProviderUsed = provider
            lastFixLatencyMs = latencyMs
            val snapshot = publishTelemetryLocked(now)
            runCatching {
                Log.d(
                    LOG_TAG,
                    "event=FIX_RESOLVED provider=$provider latencyMs=$latencyMs " +
                        "active1h=${snapshot.activeChecksLastHour} active24h=${snapshot.activeChecksLast24h}",
                )
            }
        }
    }

    private fun recordThrottledSkip() {
        val now = clockMs()
        synchronized(telemetryLock) {
            throttledSkipTimestampsMs.addLast(now)
            trimDeque(throttledSkipTimestampsMs, now, WINDOW_1H_MS, MAX_EVENTS_BUFFER)
            val snapshot = publishTelemetryLocked(now)
            runCatching {
                Log.d(
                    LOG_TAG,
                    "event=THROTTLED_SKIP throttled1h=${snapshot.throttledSkipsLastHour} " +
                        "active1h=${snapshot.activeChecksLastHour}",
                )
            }
        }
    }

    private fun recordTimeout(provider: String) {
        val now = clockMs()
        synchronized(telemetryLock) {
            timeoutTimestampsMs.addLast(now)
            trimDeque(timeoutTimestampsMs, now, WINDOW_24H_MS, MAX_EVENTS_BUFFER)
            val snapshot = publishTelemetryLocked(now)
            runCatching {
                Log.d(
                    LOG_TAG,
                    "event=TIMEOUT provider=$provider timeouts24h=${snapshot.timeoutsLast24h}",
                )
            }
        }
    }

    private fun publishTelemetryLocked(now: Long): LocationTelemetry {
        trimDeque(activeRequestTimestampsMs, now, WINDOW_24H_MS, MAX_EVENTS_BUFFER)
        trimDeque(cacheHitTimestampsMs, now, WINDOW_24H_MS, MAX_EVENTS_BUFFER)
        trimDeque(throttledSkipTimestampsMs, now, WINDOW_1H_MS, MAX_EVENTS_BUFFER)
        trimDeque(timeoutTimestampsMs, now, WINDOW_24H_MS, MAX_EVENTS_BUFFER)

        val active1h = activeRequestTimestampsMs.count { now - it <= WINDOW_1H_MS }
        val active24h = activeRequestTimestampsMs.size
        val cache1h = cacheHitTimestampsMs.count { now - it <= WINDOW_1H_MS }
        val cache24h = cacheHitTimestampsMs.size
        val throttled1h = throttledSkipTimestampsMs.size
        val timeouts24h = timeoutTimestampsMs.size

        return LocationTelemetry(
            activeChecksLastHour = active1h,
            activeChecksLast24h = active24h,
            cacheHitsLastHour = cache1h,
            cacheHitsLast24h = cache24h,
            throttledSkipsLastHour = throttled1h,
            timeoutsLast24h = timeouts24h,
            lastProviderUsed = lastProviderUsed,
            lastFixLatencyMs = lastFixLatencyMs,
        ).also { _telemetry.value = it }
    }

    private fun trimDeque(deque: ArrayDeque<Long>, now: Long, windowMs: Long, maxEntries: Int) {
        while (deque.isNotEmpty() && (now - deque.first() > windowMs || deque.size > maxEntries)) {
            deque.removeFirst()
        }
    }

    private fun loadPersistedActiveTimestamps() {
        val now = clockMs()
        synchronized(telemetryLock) {
            val raw = telemetryPrefs.getString(KEY_ACTIVE_TIMESTAMPS, null).orEmpty()
            if (raw.isNotBlank()) {
                raw.split(',')
                    .mapNotNull { it.toLongOrNull() }
                    .filter { now >= it && (now - it) <= WINDOW_24H_MS }
                    .takeLast(MAX_EVENTS_BUFFER)
                    .forEach { activeRequestTimestampsMs.addLast(it) }
            }
            publishTelemetryLocked(now)
        }
    }

    private fun persistActiveTimestampsLocked() {
        val serialized = activeRequestTimestampsMs.joinToString(",")
        telemetryPrefs.edit().putString(KEY_ACTIVE_TIMESTAMPS, serialized).apply()
    }

    private fun isValidCoordinate(location: Location): Boolean = !location.latitude.isNaN() &&
        !location.longitude.isNaN() &&
        location.latitude in -90.0..90.0 &&
        location.longitude in -180.0..180.0

    private fun isStale(location: Location): Boolean {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val locNanos = location.elapsedRealtimeNanos
        val staleByNanos = nowNanos > 0L &&
            locNanos > 0L &&
            nowNanos >= locNanos &&
            (nowNanos - locNanos) > STALE_FIX_THRESHOLD_NANOS

        val nowMs = clockMs()
        val locMs = location.time
        val staleByWallClock = nowMs > 0L &&
            locMs > 0L &&
            nowMs >= locMs &&
            (nowMs - locMs) > STALE_FIX_THRESHOLD_MS

        if (locNanos <= 0L && locMs <= 0L) return true
        return staleByNanos || staleByWallClock
    }

    private fun isNewer(candidate: Location, currentBest: Location): Boolean {
        val candidateMock = isMockLocation(candidate)
        val bestMock = isMockLocation(currentBest)
        if (candidateMock != bestMock) {
            return candidateMock
        }
        val candidateNanos = candidate.elapsedRealtimeNanos
        val bestNanos = currentBest.elapsedRealtimeNanos
        return if (candidateNanos > 0L && bestNanos > 0L) {
            candidateNanos >= bestNanos
        } else {
            candidate.time >= currentBest.time
        }
    }

    @Suppress("DEPRECATION")
    private fun isMockLocation(location: Location): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        location.isMock
    } else {
        location.isFromMockProvider
    }

    companion object {
        private const val LOG_TAG = "ThresholdLocation"
        private const val PREFS_NAME = "threshold_location_telemetry"
        private const val KEY_ACTIVE_TIMESTAMPS = "active_request_timestamps_ms"
        private const val MAX_EVENTS_BUFFER = 500
        private const val WINDOW_1H_MS = 60 * 60 * 1_000L
        private const val WINDOW_24H_MS = 24 * 60 * 60 * 1_000L

        internal const val STALE_FIX_THRESHOLD_MS = 30_000L
        internal const val STALE_FIX_THRESHOLD_NANOS = STALE_FIX_THRESHOLD_MS * 1_000_000L
        internal const val MIN_ACTIVE_REQUEST_INTERVAL_MS = 30_000L
        internal const val ACTIVE_REQUEST_TIMEOUT_MS = 5_000L
    }
}

package com.garan.threshold.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.port.LocationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android platform [LocationManager] implementation of [LocationSource].
 *
 * Requires zero Google Play Services dependencies.
 * Reads cached last-known fixes across available providers (`fused`, `gps`, `network`, `passive`).
 * If the cached fix is older than [STALE_FIX_THRESHOLD_MS] (30 seconds), it immediately emits the
 * cached fix for zero-latency evaluation and asynchronously requests a fresh OS fix via
 * `LocationManager.getCurrentLocation` (without cancelling on UI pause, so rapid app launches still
 * complete the Wi-Fi/cell scan during the OS foreground grace window).
 *
 * When Location Services are turned off or location permissions are missing, [currentLocation]
 * immediately clears to `null` so Geo profiles fail-safe to inactive.
 */
class AndroidLocationSource(context: Context) : LocationSource {

    private val appContext = context.applicationContext ?: context
    private val locationManager: LocationManager? =
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentLocation = MutableStateFlow<LocationFix?>(null)
    override val currentLocation: StateFlow<LocationFix?> = _currentLocation.asStateFlow()

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
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
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
                triggerUncancelledCurrentLocationRefresh(manager)
            }
            return fix
        }

        // If no lastKnownLocation is available yet, request a current fix (waiting up to 1.5s,
        // while still allowing the callback to update _currentLocation if it resolves slightly later).
        val freshFix = requestCurrentFixInternal(manager, timeoutMs = 1_500L)
        if (freshFix != null) {
            _currentLocation.value = freshFix
            return freshFix
        }

        // Retain existing fix if Location Services & permission are still active during temporary signal loss
        return _currentLocation.value
    }

    @SuppressLint("MissingPermission")
    private fun triggerUncancelledCurrentLocationRefresh(manager: LocationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val providers = selectActiveProviders(manager)
        if (providers.isEmpty()) return

        for (provider in providers) {
            runCatching {
                val oneShotListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        runCatching { manager.removeUpdates(this) }
                        if (isValidCoordinate(location) &&
                            hasLocationPermission() && isLocationServicesEnabled()
                        ) {
                            _currentLocation.value = LocationFix(location.latitude, location.longitude)
                        }
                    }
                }
                manager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    oneShotListener,
                    Looper.getMainLooper(),
                )
            }
            runCatching {
                manager.getCurrentLocation(
                    provider,
                    null,
                    ContextCompat.getMainExecutor(appContext),
                ) { location ->
                    if (location != null && isValidCoordinate(location) &&
                        !isStale(location) &&
                        hasLocationPermission() && isLocationServicesEnabled()
                    ) {
                        _currentLocation.value = LocationFix(location.latitude, location.longitude)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentFixInternal(manager: LocationManager, timeoutMs: Long): LocationFix? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val providers = selectActiveProviders(manager)
        if (providers.isEmpty()) return null

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                // Intentionally pass null CancellationSignal so if the coroutine times out or
                // the user launches an app quickly, the OS still completes the Wi-Fi/cell fix
                // during the foreground transition grace window and updates _currentLocation.
                for (provider in providers) {
                    runCatching {
                        manager.getCurrentLocation(
                            provider,
                            null,
                            ContextCompat.getMainExecutor(appContext),
                        ) { location ->
                            val fix = if (location != null && isValidCoordinate(location) &&
                                hasLocationPermission() && isLocationServicesEnabled()
                            ) {
                                LocationFix(location.latitude, location.longitude).also {
                                    _currentLocation.value = it
                                }
                            } else {
                                null
                            }
                            if (fix != null && !cont.isCompleted) {
                                cont.resume(fix)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun selectActiveProviders(manager: LocationManager): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false)
        ) {
            add(LocationManager.FUSED_PROVIDER)
        }
        if (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
            add(LocationManager.NETWORK_PROVIDER)
        }
        if (runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
            add(LocationManager.GPS_PROVIDER)
        }
    }

    private fun isValidCoordinate(location: Location): Boolean =
        !location.latitude.isNaN() && !location.longitude.isNaN() &&
            location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0

    private fun isStale(location: Location): Boolean {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val locNanos = location.elapsedRealtimeNanos
        val staleByNanos = nowNanos > 0L && locNanos > 0L && nowNanos >= locNanos &&
            (nowNanos - locNanos) > STALE_FIX_THRESHOLD_NANOS

        val nowMs = System.currentTimeMillis()
        val locMs = location.time
        val staleByWallClock = nowMs > 0L && locMs > 0L && nowMs >= locMs &&
            (nowMs - locMs) > STALE_FIX_THRESHOLD_MS

        if (locNanos <= 0L && locMs <= 0L) return true
        return staleByNanos || staleByWallClock
    }

    private fun isNewer(candidate: Location, currentBest: Location): Boolean {
        val candidateNanos = candidate.elapsedRealtimeNanos
        val bestNanos = currentBest.elapsedRealtimeNanos
        return if (candidateNanos > 0L && bestNanos > 0L) {
            candidateNanos >= bestNanos
        } else {
            candidate.time >= currentBest.time
        }
    }

    companion object {
        internal const val STALE_FIX_THRESHOLD_MS = 30_000L
        internal const val STALE_FIX_THRESHOLD_NANOS = STALE_FIX_THRESHOLD_MS * 1_000_000L
    }
}

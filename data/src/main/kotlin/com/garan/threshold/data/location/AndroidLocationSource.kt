package com.garan.threshold.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
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
 * Reads cached last-known fixes across available providers (`fused`, `gps`, `network`, `passive`)
 * and supports active on-demand location requests when the user taps "Use Current Location".
 *
 * When Location Services are turned off or location permissions are missing, [currentLocation]
 * immediately clears to `null` so Geo profiles fail-safe to inactive. When Location Services and
 * permissions remain active during a temporary indoor/underground signal drop, the last known
 * [LocationFix] is retained until a newer fix arrives.
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
            if (!candidate.latitude.isNaN() &&
                !candidate.longitude.isNaN() &&
                candidate.latitude in -90.0..90.0 &&
                candidate.longitude in -180.0..180.0
            ) {
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
            return fix
        }

        // If no lastKnownLocation is available yet, try requesting a quick current fix on API 30+
        val freshFix = requestCurrentFixInternal(manager, timeoutMs = 1_500L)
        if (freshFix != null) {
            _currentLocation.value = freshFix
            return freshFix
        }

        // Retain existing fix if Location Services & permission are still active during temporary signal loss
        return _currentLocation.value
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentFixInternal(manager: LocationManager, timeoutMs: Long): LocationFix? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                runCatching { manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) }.getOrDefault(false) ->
                LocationManager.FUSED_PROVIDER
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cancellationSignal = CancellationSignal()
                cont.invokeOnCancellation { cancellationSignal.cancel() }
                runCatching {
                    manager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        ContextCompat.getMainExecutor(appContext),
                    ) { location ->
                        if (!cont.isCompleted) {
                            val fix = if (location != null &&
                                !location.latitude.isNaN() &&
                                !location.longitude.isNaN() &&
                                location.latitude in -90.0..90.0 &&
                                location.longitude in -180.0..180.0
                            ) {
                                LocationFix(location.latitude, location.longitude)
                            } else {
                                null
                            }
                            cont.resume(fix)
                        }
                    }
                }.onFailure {
                    if (!cont.isCompleted) {
                        cont.resume(null)
                    }
                }
            }
        }
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
}

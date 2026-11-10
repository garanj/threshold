package com.garan.threshold.core.port

import com.garan.threshold.core.model.LocationFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rolling-window telemetry for location evaluations, enabling power-efficiency monitoring
 * and automated over-use detection in tests and on-device diagnostics.
 */
data class LocationTelemetry(
    val activeChecksLastHour: Int = 0,
    val activeChecksLast24h: Int = 0,
    val cacheHitsLastHour: Int = 0,
    val cacheHitsLast24h: Int = 0,
    val throttledSkipsLastHour: Int = 0,
    val timeoutsLast24h: Int = 0,
    val lastProviderUsed: String? = null,
    val lastFixLatencyMs: Long? = null,
)

/**
 * Outbound port for observing and refreshing the device's current geographic location.
 *
 * When Location Services are disabled or location permission is not granted, [currentLocation]
 * emits `null` and [refreshLocation] returns `null` so Geo profiles fail-safe to inactive.
 */
interface LocationSource {
    /**
     * Reactive stream of the most recent valid [LocationFix], or `null` if unknown / unauthorized / disabled.
     */
    val currentLocation: StateFlow<LocationFix?>

    /**
     * Reactive rolling-window telemetry for location evaluations (`1h` and `24h` windows).
     */
    val telemetry: StateFlow<LocationTelemetry>
        get() = EMPTY_TELEMETRY

    /**
     * Requests a fresh location fix or reconciles the latest OS location state, updating [currentLocation].
     */
    suspend fun refreshLocation(): LocationFix?

    companion object {
        private val EMPTY_TELEMETRY: StateFlow<LocationTelemetry> =
            MutableStateFlow(LocationTelemetry()).asStateFlow()
    }
}

package com.garan.threshold.core.port

import com.garan.threshold.core.model.LocationFix
import kotlinx.coroutines.flow.StateFlow

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
     * Requests a fresh location fix or reconciles the latest OS location state, updating [currentLocation].
     */
    suspend fun refreshLocation(): LocationFix?
}

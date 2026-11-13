package com.garan.threshold.core.model

/**
 * Trigger criteria governing when a custom profile activates.
 *
 * Designed to be extended across temporal schedules, geographic zones, and physical NFC relays.
 */
sealed interface ProfileTrigger {

    /**
     * Temporal trigger evaluated on a recurring schedule.
     */
    data class Schedule(val schedule: ScheduleTrigger = ScheduleTrigger()) : ProfileTrigger {
        override val requiredCapabilities: Set<ProfileCapability>
            get() = setOf(ProfileCapability.EXACT_ALARM)

        override fun isActive(
            currentDay: DayOfWeek,
            currentTime: TimeOfDay,
            currentLocation: LocationFix?,
            wasPreviouslyActive: Boolean,
        ): Boolean = schedule.isActive(currentDay, currentTime)
    }

    /**
     * Spatial trigger evaluated against a circular geographic zone with boundary hysteresis.
     */
    data class Geo(val geo: GeoTrigger = GeoTrigger()) : ProfileTrigger {
        override val requiredCapabilities: Set<ProfileCapability>
            get() = setOf(ProfileCapability.BACKGROUND_LOCATION)

        override fun isActive(
            currentDay: DayOfWeek,
            currentTime: TimeOfDay,
            currentLocation: LocationFix?,
            wasPreviouslyActive: Boolean,
        ): Boolean = geo.isActive(currentLocation, wasPreviouslyActive)
    }

    /**
     * Physical NFC tag trigger evaluated via foreground tag tap latching and session expiry.
     */
    data class Nfc(val nfc: NfcTrigger = NfcTrigger()) : ProfileTrigger {
        override val requiredCapabilities: Set<ProfileCapability>
            get() = setOf(ProfileCapability.NFC_HARDWARE_ENABLED)

        override fun isActive(
            currentDay: DayOfWeek,
            currentTime: TimeOfDay,
            currentLocation: LocationFix?,
            wasPreviouslyActive: Boolean,
        ): Boolean = nfc.tags.isNotEmpty() && wasPreviouslyActive
    }

    /**
     * OS permissions or hardware capabilities required for this trigger to function reliably.
     */
    val requiredCapabilities: Set<ProfileCapability>

    /**
     * Evaluates whether this trigger is currently active given temporal and spatial context.
     */
    fun isActive(
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        currentLocation: LocationFix? = null,
        wasPreviouslyActive: Boolean = false,
    ): Boolean
}

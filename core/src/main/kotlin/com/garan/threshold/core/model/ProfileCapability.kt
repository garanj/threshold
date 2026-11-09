package com.garan.threshold.core.model

/**
 * OS permissions or hardware capabilities required by profile triggers.
 */
enum class ProfileCapability {
    /** Required by [ScheduleTrigger] (`SCHEDULE_EXACT_ALARM`). */
    EXACT_ALARM,

    /** Required by Geofence triggers (`ACCESS_FINE_LOCATION` / `ACCESS_BACKGROUND_LOCATION`). */
    BACKGROUND_LOCATION,

    /** Required by NFC triggers (`NfcAdapter.isEnabled`). */
    NFC_HARDWARE_ENABLED,
}

/**
 * Describes an enabled [CustomProfile] that is missing one or more required [ProfileCapability] grants.
 */
data class ProfileCapabilityIssue(val profile: CustomProfile, val missingCapabilities: Set<ProfileCapability>)

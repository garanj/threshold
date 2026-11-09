package com.garan.threshold.core.model

/**
 * Top-level application settings encapsulating general preferences, base profile configurations,
 * and user-defined custom profiles.
 */
data class ThresholdSettings(
    val general: GeneralSettings = GeneralSettings(),
    val baseProfile: BaseProfileSettings = BaseProfileSettings(),
    val customProfiles: List<CustomProfile> = emptyList(),
)

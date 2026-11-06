package com.garan.threshold.core.model

/**
 * Top-level application settings encapsulating general preferences and base profile configurations.
 */
data class ThresholdSettings(
    val general: GeneralSettings = GeneralSettings(),
    val baseProfile: BaseProfileSettings = BaseProfileSettings(),
)

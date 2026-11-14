package com.garan.threshold.core.model

/**
 * Top-level application settings encapsulating general preferences, base profile configurations,
 * reusable triggers, and user-defined custom profiles.
 */
data class ThresholdSettings(
    val general: GeneralSettings = GeneralSettings(),
    val baseProfile: BaseProfileSettings = BaseProfileSettings(),
    val customProfiles: List<CustomProfile> = emptyList(),
    val savedTriggers: List<SavedTrigger> = emptyList(),
    val nfcLatches: Map<String, NfcLatchState> = emptyMap(),
) {
    val savedTriggersById: Map<String, SavedTrigger> =
        savedTriggers.associateBy { it.id }
}

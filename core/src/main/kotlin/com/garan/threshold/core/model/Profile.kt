package com.garan.threshold.core.model

/**
 * Represents a behavioral mode/profile in Threshold (e.g., Ambient, Bedtime, Work).
 *
 * @property id Stable identifier for this profile.
 * @property name Human-readable display name.
 * @property dndPolicy The notification filtration policy active during this profile.
 * @property defaultAccess The fallback access tier for apps not explicitly listed in [appRules].
 * @property appRules Explicit per-app access tier mappings (`Direct`, `Guarded`, or `Blocked`).
 */
data class Profile(
    val id: String,
    val name: String,
    val dndPolicy: DndPolicy = DndPolicy.Off,
    val defaultAccess: AppAccessState = AppAccessState.Direct,
    val appRules: Map<ComponentKey, AppAccessState> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "Profile id must not be blank" }
        require(name.isNotBlank()) { "Profile name must not be blank" }
    }

    /**
     * Resolves the effective [AppAccessState] for a given [component] under this profile.
     *
     * Invariant: The system default dialer (`isDefaultDialer = true`) is permanently and
     * unconditionally evaluated as [AppAccessState.Direct], regardless of profile rules or defaults.
     */
    fun resolveAccess(component: ComponentKey, isDefaultDialer: Boolean): AppAccessState {
        if (isDefaultDialer) {
            return AppAccessState.Direct
        }
        return appRules[component] ?: defaultAccess
    }
}

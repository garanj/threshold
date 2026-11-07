package com.garan.threshold.core.model

/**
 * A user-defined behavioral profile overriding base app access rules when triggered.
 *
 * @property id Stable unique identifier for this profile.
 * @property name User-facing display name (e.g. "Deep Work", "Bedtime").
 * @property enabled Whether this profile is active for trigger evaluation.
 * @property priority User-ordered priority ranking (0 = highest priority). Evaluated top-to-bottom.
 * @property trigger The contextual trigger condition (e.g. [ProfileTrigger.Schedule]).
 * @property appAssignments Explicit app overrides (`Standard`/Allow, `Guarded`, `Blocked`). Apps omitted here cascade.
 * @property frictionOverride Optional profile-wide friction override for guarded apps in this profile.
 */
data class CustomProfile(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val trigger: ProfileTrigger = ProfileTrigger.Schedule(),
    val appAssignments: Map<ComponentKey, AppAssignment> = emptyMap(),
    val frictionOverride: FrictionChoice? = null,
) {
    init {
        require(id.isNotBlank()) { "Profile id must not be blank" }
        require(name.isNotBlank()) { "Profile name must not be blank" }
        require(priority >= 0) { "Profile priority must be non-negative, was $priority" }
    }

    /**
     * Determines whether this profile is currently active based on its enabled state and trigger condition.
     */
    fun isActive(currentDay: DayOfWeek, currentTime: TimeOfDay): Boolean =
        enabled && trigger.isActive(currentDay, currentTime)
}

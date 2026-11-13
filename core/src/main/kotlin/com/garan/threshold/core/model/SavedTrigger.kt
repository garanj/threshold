package com.garan.threshold.core.model

/**
 * A reusable, named trigger building block (e.g. "Work Hours", "At Office")
 * that can be attached to one or more [CustomProfile] activation rules.
 *
 * @property id Stable unique identifier for this trigger.
 * @property name User-facing display name (e.g. "Work Hours", "Office").
 * @property trigger The underlying temporal or spatial trigger condition.
 */
data class SavedTrigger(val id: String, val name: String, val trigger: ProfileTrigger) {
    init {
        require(id.isNotBlank()) { "Trigger id must not be blank" }
        require(name.isNotBlank()) { "Trigger name must not be blank" }
    }

    val requiredCapabilities: Set<ProfileCapability>
        get() = trigger.requiredCapabilities

    fun isActive(
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        currentLocation: LocationFix? = null,
        wasPreviouslyActive: Boolean = false,
    ): Boolean = trigger.isActive(
        currentDay = currentDay,
        currentTime = currentTime,
        currentLocation = currentLocation,
        wasPreviouslyActive = wasPreviouslyActive,
    )
}

/**
 * A single conjunction (`AND` group) of [SavedTrigger] IDs attached to a [CustomProfile].
 *
 * All [triggerIds] in the rule must be simultaneously active for the rule to evaluate to `true`.
 * A [CustomProfile] with multiple [TriggerRule]s evaluates them as a disjunction (`OR`).
 */
data class TriggerRule(val triggerIds: List<String>) {
    init {
        require(triggerIds.all { it.isNotBlank() }) { "Trigger IDs must not be blank" }
    }

    /**
     * Evaluates to `true` when the rule is non-empty and every referenced trigger ID is present
     * in [activeTriggerIds] (AND semantics).
     */
    fun isActive(activeTriggerIds: Set<String>): Boolean =
        triggerIds.isNotEmpty() && triggerIds.all { it in activeTriggerIds }
}

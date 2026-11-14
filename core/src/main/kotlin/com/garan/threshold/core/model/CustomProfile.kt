package com.garan.threshold.core.model

/**
 * A user-defined behavioral profile overriding base app access rules when triggered.
 *
 * @property id Stable unique identifier for this profile.
 * @property name User-facing display name (e.g. "Deep Work", "Bedtime").
 * @property enabled Whether this profile is active for trigger evaluation.
 * @property priority User-ordered priority ranking (0 = highest priority). Evaluated top-to-bottom.
 * @property trigger Optional legacy inline trigger fallback when [activationRules] is not populated.
 * @property activationRules Disjunction (`OR` list) of [TriggerRule] conjunctions (`AND` groups) of [SavedTrigger] IDs.
 * @property appAssignments Explicit app overrides (`Standard`/Allow, `Guarded`, `Blocked`). Apps omitted here cascade.
 * @property frictionOverride Optional profile-wide friction override for guarded apps in this profile.
 * @property colorIndex Index into the profile color palette assigned when the profile is created.
 */
data class CustomProfile(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val trigger: ProfileTrigger? = ProfileTrigger.Schedule(),
    val activationRules: List<TriggerRule> = emptyList(),
    val appAssignments: Map<ComponentKey, AppAssignment> = emptyMap(),
    val frictionOverride: FrictionChoice? = null,
    val colorIndex: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "Profile id must not be blank" }
        require(name.isNotBlank()) { "Profile name must not be blank" }
        require(priority >= 0) { "Profile priority must be non-negative, was $priority" }
    }

    /**
     * Single uppercase starting letter of [name] for compact square status indicators.
     */
    val initialLetter: String
        get() = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P"

    /**
     * Ordered set of all [SavedTrigger] IDs referenced across [activationRules].
     */
    val referencedTriggerIds: Set<String>
        get() = activationRules.flatMapTo(linkedSetOf()) { it.triggerIds }

    /**
     * Resolves all [ProfileTrigger] instances attached to this profile via [activationRules]
     * (looking up IDs in [savedTriggersById]), falling back to legacy inline [trigger] only when
     * [activationRules] is empty and [trigger] is non-null.
     */
    fun resolvedTriggers(savedTriggersById: Map<String, SavedTrigger> = emptyMap()): List<ProfileTrigger> {
        if (activationRules.isNotEmpty()) {
            return referencedTriggerIds.mapNotNull { savedTriggersById[it]?.trigger }
        }
        return listOfNotNull(trigger)
    }

    /**
     * OS permissions or hardware capabilities required by this profile's inline [trigger]
     * when [activationRules] is empty.
     */
    val requiredCapabilities: Set<ProfileCapability>
        get() = if (activationRules.isNotEmpty()) emptySet() else trigger?.requiredCapabilities ?: emptySet()

    /**
     * OS permissions or hardware capabilities required across all [activationRules] (or legacy [trigger]).
     */
    fun requiredCapabilities(savedTriggersById: Map<String, SavedTrigger>): Set<ProfileCapability> =
        resolvedTriggers(savedTriggersById).flatMapTo(linkedSetOf()) { it.requiredCapabilities }

    /**
     * Determines whether this profile is currently active based on its enabled state and trigger rules.
     *
     * - When [activationRules] is non-empty, evaluates as `OR` of `AND`s against [activeTriggerIds].
     * - When [activationRules] is empty and [trigger] is `null` (an unassigned profile), returns `false`.
     * - When [activationRules] is empty and [trigger] is non-null (legacy inline trigger), delegates to [trigger].
     */
    fun isActive(
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        currentLocation: LocationFix? = null,
        wasPreviouslyActive: Boolean = false,
        activeTriggerIds: Set<String>? = null,
    ): Boolean {
        if (!enabled) return false
        if (activationRules.isNotEmpty()) {
            val activeIds = activeTriggerIds ?: return false
            return activationRules.any { rule -> rule.isActive(activeIds) }
        }
        return trigger?.isActive(currentDay, currentTime, currentLocation, wasPreviouslyActive) ?: false
    }

    /**
     * Removes [deletedTriggerId] from all [activationRules], dropping any rule that becomes empty.
     * If all rules are removed as a result, automatically sets [enabled] to `false` and clears [trigger]
     * so a profile left with zero triggers is never active.
     */
    fun withoutTrigger(deletedTriggerId: String): CustomProfile {
        if (deletedTriggerId !in referencedTriggerIds) return this
        val updatedRules = activationRules.mapNotNull { rule ->
            val remaining = rule.triggerIds.filterNot { it == deletedTriggerId }
            if (remaining.isEmpty()) null else TriggerRule(remaining)
        }
        return if (updatedRules.isEmpty()) {
            copy(
                enabled = false,
                trigger = null,
                activationRules = emptyList(),
            )
        } else {
            copy(
                trigger = null,
                activationRules = updatedRules,
            )
        }
    }

    companion object {
        const val PALETTE_SIZE = 6

        /**
         * Selects the next available palette index not currently used by [existingProfiles],
         * cycling deterministically when all palette slots are in use.
         */
        fun nextColorIndex(existingProfiles: List<CustomProfile>): Int {
            val usedIndices = existingProfiles.map {
                ((it.colorIndex % PALETTE_SIZE) + PALETTE_SIZE) % PALETTE_SIZE
            }.toSet()
            return (0 until PALETTE_SIZE).firstOrNull { it !in usedIndices }
                ?: (existingProfiles.size % PALETTE_SIZE)
        }
    }
}

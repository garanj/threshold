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
 * @property colorIndex Index into the profile color palette assigned when the profile is created.
 */
data class CustomProfile(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val trigger: ProfileTrigger = ProfileTrigger.Schedule(),
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
     * OS permissions or hardware capabilities required by this profile's [trigger].
     */
    val requiredCapabilities: Set<ProfileCapability>
        get() = trigger.requiredCapabilities

    /**
     * Determines whether this profile is currently active based on its enabled state and trigger condition.
     */
    fun isActive(currentDay: DayOfWeek, currentTime: TimeOfDay): Boolean =
        enabled && trigger.isActive(currentDay, currentTime)

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

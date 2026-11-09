package com.garan.threshold.core.model

/**
 * Trigger criteria governing when a custom profile activates.
 *
 * Designed to be extended in future milestones (e.g. NFC physical relay, Geofencing, Manual toggle).
 */
sealed interface ProfileTrigger {

    /**
     * Temporal trigger evaluated on a recurring schedule.
     */
    data class Schedule(val schedule: ScheduleTrigger = ScheduleTrigger()) : ProfileTrigger {
        override val requiredCapabilities: Set<ProfileCapability>
            get() = setOf(ProfileCapability.EXACT_ALARM)

        override fun isActive(currentDay: DayOfWeek, currentTime: TimeOfDay): Boolean =
            schedule.isActive(currentDay, currentTime)
    }

    /**
     * OS permissions or hardware capabilities required for this trigger to function reliably.
     */
    val requiredCapabilities: Set<ProfileCapability>

    /**
     * Evaluates whether this trigger is currently active given temporal context.
     */
    fun isActive(currentDay: DayOfWeek, currentTime: TimeOfDay): Boolean
}

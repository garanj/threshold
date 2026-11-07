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
        override fun isActive(currentDay: DayOfWeek, currentTime: TimeOfDay): Boolean =
            schedule.isActive(currentDay, currentTime)
    }

    /**
     * Evaluates whether this trigger is currently active given temporal context.
     */
    fun isActive(currentDay: DayOfWeek, currentTime: TimeOfDay): Boolean
}

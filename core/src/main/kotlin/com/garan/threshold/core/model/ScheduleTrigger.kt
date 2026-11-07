package com.garan.threshold.core.model

/**
 * Defines a recurring temporal trigger window across specified days of the week.
 *
 * Supports both same-day intervals (e.g. 09:00 to 17:00) and overnight intervals
 * that span midnight into the following morning (e.g. 22:00 to 06:00).
 *
 * @property days The set of days on which this schedule originates.
 * @property startTime The inclusive starting time of the trigger window.
 * @property endTime The exclusive ending time of the trigger window.
 * @property isAllDay When true, the schedule remains active throughout the entire 24 hours of selected [days].
 */
data class ScheduleTrigger(
    val days: Set<DayOfWeek> = emptySet(),
    val startTime: TimeOfDay = TimeOfDay(9, 0),
    val endTime: TimeOfDay = TimeOfDay(17, 0),
    val isAllDay: Boolean = false,
) {

    /**
     * Determines whether this schedule is currently active at [currentDay] and [currentTime].
     */
    fun isActive(currentDay: DayOfWeek, currentTime: TimeOfDay): Boolean {
        if (days.isEmpty()) return false

        if (isAllDay) {
            return currentDay in days
        }

        return if (startTime <= endTime) {
            // Same-day window (e.g., 09:00 to 17:00)
            currentDay in days && currentTime >= startTime && currentTime < endTime
        } else {
            // Overnight window spanning midnight (e.g., 22:00 to 06:00)
            val isEveningOfScheduledDay = currentDay in days && currentTime >= startTime
            val isMorningAfterScheduledDay = currentDay.previousDay() in days && currentTime < endTime
            isEveningOfScheduledDay || isMorningAfterScheduledDay
        }
    }
}

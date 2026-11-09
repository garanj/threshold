package com.garan.threshold.core.model

/**
 * Defines a start and end time window originating on a specific day.
 *
 * When [endTime] is strictly before [startTime] (e.g. 19:00 to 06:00), [isOvernight] is true,
 * meaning the window starts at [startTime] on the scheduled day and ends at [endTime] on the
 * following calendar day.
 */
data class TimeWindow(val startTime: TimeOfDay = TimeOfDay(9, 0), val endTime: TimeOfDay = TimeOfDay(17, 0)) {
    /**
     * True when the end time is earlier on the clock than the start time (e.g. 19:00 to 06:00),
     * indicating that the window spans midnight into the next day.
     */
    val isOvernight: Boolean
        get() = endTime < startTime

    fun formatted(): String = "${startTime.formatted()} – ${endTime.formatted()}"
}

/**
 * Defines a recurring temporal trigger window across specified days of the week.
 *
 * Supports both a uniform time window across all active days ([sameScheduleEveryDay] = true)
 * and distinct per-day time windows ([sameScheduleEveryDay] = false), as well as overnight
 * intervals where [TimeWindow.endTime] is before [TimeWindow.startTime] (e.g. Monday 19:00–06:00
 * runs from Monday 19:00 until Tuesday 06:00).
 *
 * @property dailyWindows Map of active days of the week to their respective [TimeWindow].
 * @property sameScheduleEveryDay Whether all selected days share a single uniform time window in the UI.
 */
data class ScheduleTrigger(
    val dailyWindows: Map<DayOfWeek, TimeWindow> = emptyMap(),
    val sameScheduleEveryDay: Boolean = true,
) {

    /**
     * Convenience constructor for uniform schedules where all selected [days] share [startTime] and [endTime].
     */
    constructor(
        days: Set<DayOfWeek>,
        startTime: TimeOfDay = TimeOfDay(9, 0),
        endTime: TimeOfDay = TimeOfDay(17, 0),
        sameScheduleEveryDay: Boolean = true,
    ) : this(
        dailyWindows = days.associateWith { TimeWindow(startTime, endTime) },
        sameScheduleEveryDay = sameScheduleEveryDay,
    )

    /**
     * The set of days on which a time window originates.
     */
    val days: Set<DayOfWeek>
        get() = dailyWindows.keys

    /**
     * The primary start time (used when [sameScheduleEveryDay] is true or as default fallback).
     */
    val startTime: TimeOfDay
        get() = dailyWindows.values.firstOrNull()?.startTime ?: TimeOfDay(9, 0)

    /**
     * The primary end time (used when [sameScheduleEveryDay] is true or as default fallback).
     */
    val endTime: TimeOfDay
        get() = dailyWindows.values.firstOrNull()?.endTime ?: TimeOfDay(17, 0)

    /**
     * Determines whether this schedule is currently active at [currentDay] and [currentTime].
     */
    fun isActive(currentDay: DayOfWeek, currentTime: TimeOfDay): Boolean {
        if (dailyWindows.isEmpty()) return false

        val todayWindow = dailyWindows[currentDay]
        val activeToday = if (todayWindow != null) {
            if (!todayWindow.isOvernight) {
                // Same-day window (e.g., 09:00 to 17:00)
                currentTime >= todayWindow.startTime && currentTime < todayWindow.endTime
            } else {
                // Overnight window starting today (e.g., Monday 19:00 to Tuesday 06:00)
                currentTime >= todayWindow.startTime
            }
        } else {
            false
        }

        // Check if yesterday had an overnight window that spills into this morning
        val yesterdayWindow = dailyWindows[currentDay.previousDay()]
        val activeFromYesterdayOvernight = yesterdayWindow != null &&
            yesterdayWindow.isOvernight &&
            currentTime < yesterdayWindow.endTime

        return activeToday || activeFromYesterdayOvernight
    }
}

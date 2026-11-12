package com.garan.threshold.core.cascading

import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.TimeOfDay

/**
 * Represents the next chronological schedule boundary where one or more enabled custom profiles
 * will activate or deactivate.
 */
data class NextScheduleTransition(val targetDay: DayOfWeek, val targetTime: TimeOfDay, val minutesFromNow: Int)

/**
 * Pure Kotlin calculator that inspects all enabled [CustomProfile] schedule triggers and
 * computes the earliest upcoming start or end boundary across the 7-day weekly cycle.
 */
class ScheduleHorizonCalculator {

    fun findNextTransition(
        customProfiles: List<CustomProfile>,
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        savedTriggers: List<SavedTrigger> = emptyList(),
    ): NextScheduleTransition? {
        val nowMinuteOfWeek = currentDay.ordinal * MINUTES_PER_DAY + currentTime.totalMinutes
        val savedTriggersById = savedTriggers.associateBy { it.id }
        var bestDelta = Int.MAX_VALUE
        var bestDay: DayOfWeek? = null
        var bestTime: TimeOfDay? = null

        for (profile in customProfiles) {
            if (!profile.enabled) continue
            val resolvedTriggers = profile.resolvedTriggers(savedTriggersById)
            for (trigger in resolvedTriggers) {
                val scheduleTrigger = trigger as? ProfileTrigger.Schedule ?: continue
                val dailyWindows = scheduleTrigger.schedule.dailyWindows
                if (dailyWindows.isEmpty()) continue

                for ((day, window) in dailyWindows) {
                    // 1. Start boundary on `day` at `window.startTime`
                    val startDelta = computeDeltaMinutes(nowMinuteOfWeek, day, window.startTime)
                    if (startDelta < bestDelta) {
                        bestDelta = startDelta
                        bestDay = day
                        bestTime = window.startTime
                    }

                    // 2. End boundary on `day` (or `day.nextDay()` if overnight) at `window.endTime`
                    val endDay = if (window.isOvernight) day.nextDay() else day
                    val endDelta = computeDeltaMinutes(nowMinuteOfWeek, endDay, window.endTime)
                    if (endDelta < bestDelta) {
                        bestDelta = endDelta
                        bestDay = endDay
                        bestTime = window.endTime
                    }
                }
            }
        }

        val resolvedDay = bestDay ?: return null
        val resolvedTime = bestTime ?: return null
        return NextScheduleTransition(
            targetDay = resolvedDay,
            targetTime = resolvedTime,
            minutesFromNow = bestDelta,
        )
    }

    private fun computeDeltaMinutes(nowMinuteOfWeek: Int, targetDay: DayOfWeek, targetTime: TimeOfDay): Int {
        val targetMinuteOfWeek = targetDay.ordinal * MINUTES_PER_DAY + targetTime.totalMinutes
        val rawDelta = (targetMinuteOfWeek - nowMinuteOfWeek + MINUTES_PER_WEEK) % MINUTES_PER_WEEK
        return if (rawDelta == 0) MINUTES_PER_WEEK else rawDelta
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60
        private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY
    }
}

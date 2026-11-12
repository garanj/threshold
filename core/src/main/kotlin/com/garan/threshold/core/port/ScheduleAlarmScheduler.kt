package com.garan.threshold.core.port

import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.SavedTrigger

/**
 * Outbound port for scheduling and managing exact OS alarms at custom profile schedule boundaries.
 */
interface ScheduleAlarmScheduler {
    /**
     * Returns true if the application currently holds permission to schedule exact alarms
     * (`AlarmManager.canScheduleExactAlarms()` on Android 12+).
     */
    fun canScheduleExactAlarms(): Boolean

    fun syncNextScheduleAlarm(profiles: List<CustomProfile>)

    /**
     * Computes the next transition boundary from [profiles] and [savedTriggers] and schedules
     * (or cancels) the system alarm accordingly.
     */
    fun syncNextScheduleAlarm(profiles: List<CustomProfile>, savedTriggers: List<SavedTrigger>) {
        syncNextScheduleAlarm(profiles)
    }
}

object NoOpScheduleAlarmScheduler : ScheduleAlarmScheduler {
    override fun canScheduleExactAlarms(): Boolean = true
    override fun syncNextScheduleAlarm(profiles: List<CustomProfile>) = Unit
}

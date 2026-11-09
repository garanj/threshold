package com.garan.threshold.core.port

import com.garan.threshold.core.model.CustomProfile

/**
 * Outbound port for scheduling and managing exact OS alarms at custom profile schedule boundaries.
 */
interface ScheduleAlarmScheduler {
    /**
     * Returns true if the application currently holds permission to schedule exact alarms
     * (`AlarmManager.canScheduleExactAlarms()` on Android 12+).
     */
    fun canScheduleExactAlarms(): Boolean

    /**
     * Computes the next transition boundary from [profiles] and schedules (or cancels)
     * the system alarm accordingly.
     */
    fun syncNextScheduleAlarm(profiles: List<CustomProfile>)
}

object NoOpScheduleAlarmScheduler : ScheduleAlarmScheduler {
    override fun canScheduleExactAlarms(): Boolean = true
    override fun syncNextScheduleAlarm(profiles: List<CustomProfile>) = Unit
}

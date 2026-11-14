package com.garan.threshold.core.port

import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.TimeOfDay
import java.time.LocalDateTime

/**
 * Snapshot of the current local day of week, 24-hour time of day, and ISO-8601 local calendar date (`"YYYY-MM-DD"`).
 */
data class LocalDayTime(
    val dayOfWeek: DayOfWeek,
    val timeOfDay: TimeOfDay,
    val localDate: String = DEFAULT_LOCAL_DATE,
) {
    companion object {
        const val DEFAULT_LOCAL_DATE = "1970-01-01"
    }
}

/**
 * Outbound port providing the current local day of week and 24-hour time of day.
 * Enables deterministic unit testing of schedule evaluation and horizon calculation.
 */
fun interface ClockSource {
    fun now(): LocalDayTime
}

object SystemClockSource : ClockSource {
    override fun now(): LocalDayTime {
        val now = LocalDateTime.now()
        val day = when (now.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> DayOfWeek.MONDAY
            java.time.DayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
            java.time.DayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
            java.time.DayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
            java.time.DayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
            java.time.DayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
            java.time.DayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
        }
        return LocalDayTime(
            dayOfWeek = day,
            timeOfDay = TimeOfDay(now.hour, now.minute),
            localDate = now.toLocalDate().toString(),
        )
    }
}

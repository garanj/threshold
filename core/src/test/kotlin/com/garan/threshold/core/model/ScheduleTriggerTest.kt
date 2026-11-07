package com.garan.threshold.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScheduleTriggerTest {

    @Test
    fun sameDaySchedule_activeDuringWindow() {
        val schedule = ScheduleTrigger(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            startTime = TimeOfDay(9, 0),
            endTime = TimeOfDay(17, 0),
        )

        // Monday at 08:59 -> Inactive
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(8, 59))).isFalse()
        // Monday at 09:00 -> Active (inclusive start)
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(9, 0))).isTrue()
        // Monday at 12:30 -> Active
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(12, 30))).isTrue()
        // Monday at 17:00 -> Inactive (exclusive end)
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(17, 0))).isFalse()

        // Wednesday (not in days) at 12:00 -> Inactive
        assertThat(schedule.isActive(DayOfWeek.WEDNESDAY, TimeOfDay(12, 0))).isFalse()
    }

    @Test
    fun overnightSchedule_spansMidnightCorrectly() {
        // Monday & Tuesday nights: 22:00 to 06:00
        val schedule = ScheduleTrigger(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            startTime = TimeOfDay(22, 0),
            endTime = TimeOfDay(6, 0),
        )

        // Monday before start -> Inactive
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(21, 59))).isFalse()
        // Monday after start -> Active
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(22, 0))).isTrue()
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(23, 59))).isTrue()

        // Tuesday early morning (from Monday night) -> Active
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(0, 0))).isTrue()
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(5, 59))).isTrue()
        // Tuesday at 06:00 -> Inactive
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(6, 0))).isFalse()

        // Tuesday afternoon -> Inactive
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(15, 0))).isFalse()

        // Tuesday night -> Active
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(22, 30))).isTrue()

        // Wednesday morning (from Tuesday night) -> Active
        assertThat(schedule.isActive(DayOfWeek.WEDNESDAY, TimeOfDay(4, 0))).isTrue()
        assertThat(schedule.isActive(DayOfWeek.WEDNESDAY, TimeOfDay(6, 0))).isFalse()

        // Wednesday night (Wednesday not in schedule) -> Inactive
        assertThat(schedule.isActive(DayOfWeek.WEDNESDAY, TimeOfDay(22, 30))).isFalse()

        // Thursday morning (preceding day Wednesday was not in schedule) -> Inactive
        assertThat(schedule.isActive(DayOfWeek.THURSDAY, TimeOfDay(4, 0))).isFalse()
    }

    @Test
    fun sundayToMonday_overnightTransition() {
        val schedule = ScheduleTrigger(
            days = setOf(DayOfWeek.SUNDAY),
            startTime = TimeOfDay(23, 0),
            endTime = TimeOfDay(7, 0),
        )

        // Sunday night -> Active
        assertThat(schedule.isActive(DayOfWeek.SUNDAY, TimeOfDay(23, 30))).isTrue()
        // Monday morning -> Active
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(6, 30))).isTrue()
        // Monday afternoon -> Inactive
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(12, 0))).isFalse()
        // Sunday morning -> Inactive (preceding Saturday was not in schedule)
        assertThat(schedule.isActive(DayOfWeek.SUNDAY, TimeOfDay(6, 0))).isFalse()
    }

    @Test
    fun allDaySchedule_activeAllDayOnSelectedDays() {
        val schedule = ScheduleTrigger(
            days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            isAllDay = true,
        )

        assertThat(schedule.isActive(DayOfWeek.SATURDAY, TimeOfDay(0, 0))).isTrue()
        assertThat(schedule.isActive(DayOfWeek.SATURDAY, TimeOfDay(14, 25))).isTrue()
        assertThat(schedule.isActive(DayOfWeek.SUNDAY, TimeOfDay(23, 59))).isTrue()
        assertThat(schedule.isActive(DayOfWeek.FRIDAY, TimeOfDay(23, 59))).isFalse()
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(0, 0))).isFalse()
    }

    @Test
    fun timeOfDay_comparisonAndFormatting() {
        val t1 = TimeOfDay(9, 5)
        val t2 = TimeOfDay(14, 30)

        assertThat(t1 < t2).isTrue()
        assertThat(t1.formatted()).isEqualTo("09:05")
        assertThat(t2.formatted()).isEqualTo("14:30")
        assertThat(t1.totalMinutes).isEqualTo(9 * 60 + 5)
    }
}

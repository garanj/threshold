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
    fun perDaySchedule_differentTimesOnDifferentDays() {
        // Monday 16:00-18:00, Tuesday 10:00-11:00
        val schedule = ScheduleTrigger(
            dailyWindows = mapOf(
                DayOfWeek.MONDAY to TimeWindow(TimeOfDay(16, 0), TimeOfDay(18, 0)),
                DayOfWeek.TUESDAY to TimeWindow(TimeOfDay(10, 0), TimeOfDay(11, 0)),
            ),
            sameScheduleEveryDay = false,
        )

        // Monday 10:30 -> Inactive (that's Tuesday's slot)
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(10, 30))).isFalse()
        // Monday 16:30 -> Active
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(16, 30))).isTrue()
        // Monday 18:00 -> Inactive
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(18, 0))).isFalse()

        // Tuesday 10:30 -> Active
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(10, 30))).isTrue()
        // Tuesday 16:30 -> Inactive (that's Monday's slot)
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(16, 30))).isFalse()
    }

    @Test
    fun perDaySchedule_overnightMondaySpillsIntoTuesdayAlongsideTuesdayDaytimeWindow() {
        // Monday 19:00-06:00 (spills into Tuesday 06:00), plus Tuesday 10:00-11:00
        val schedule = ScheduleTrigger(
            dailyWindows = mapOf(
                DayOfWeek.MONDAY to TimeWindow(TimeOfDay(19, 0), TimeOfDay(6, 0)),
                DayOfWeek.TUESDAY to TimeWindow(TimeOfDay(10, 0), TimeOfDay(11, 0)),
            ),
            sameScheduleEveryDay = false,
        )

        // Monday 18:59 -> Inactive
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(18, 59))).isFalse()
        // Monday 19:00 -> Active
        assertThat(schedule.isActive(DayOfWeek.MONDAY, TimeOfDay(19, 0))).isTrue()
        // Tuesday 05:59 -> Active (from Monday 19:00-06:00 overnight spillover)
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(5, 59))).isTrue()
        // Tuesday 06:00 -> Inactive
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(6, 0))).isFalse()
        // Tuesday 10:30 -> Active (from Tuesday's own 10:00-11:00 window)
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(10, 30))).isTrue()
        // Tuesday 11:00 -> Inactive
        assertThat(schedule.isActive(DayOfWeek.TUESDAY, TimeOfDay(11, 0))).isFalse()
        // Wednesday 02:00 -> Inactive (Tuesday's window was not overnight)
        assertThat(schedule.isActive(DayOfWeek.WEDNESDAY, TimeOfDay(2, 0))).isFalse()
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

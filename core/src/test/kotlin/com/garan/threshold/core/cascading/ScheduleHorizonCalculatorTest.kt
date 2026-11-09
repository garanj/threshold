package com.garan.threshold.core.cascading

import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.model.TimeWindow
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScheduleHorizonCalculatorTest {

    private val calculator = ScheduleHorizonCalculator()

    @Test
    fun findNextTransition_returnsNullWhenNoProfilesEnabled() {
        val disabledProfile = CustomProfile(
            id = "p1",
            name = "Disabled",
            enabled = false,
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(9, 0),
                    endTime = TimeOfDay(17, 0),
                ),
            ),
        )

        val result = calculator.findNextTransition(
            customProfiles = listOf(disabledProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(8, 0),
        )

        assertThat(result).isNull()
    }

    @Test
    fun findNextTransition_beforeSameDayStart_returnsStartBoundary() {
        val profile = CustomProfile(
            id = "work",
            name = "Work",
            enabled = true,
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(9, 0),
                    endTime = TimeOfDay(17, 0),
                ),
            ),
        )

        val result = calculator.findNextTransition(
            customProfiles = listOf(profile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(8, 30),
        )

        assertThat(result).isEqualTo(
            NextScheduleTransition(
                targetDay = DayOfWeek.MONDAY,
                targetTime = TimeOfDay(9, 0),
                minutesFromNow = 30,
            ),
        )
    }

    @Test
    fun findNextTransition_atExactStartTime_returnsEndBoundary() {
        val profile = CustomProfile(
            id = "work",
            name = "Work",
            enabled = true,
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(9, 0),
                    endTime = TimeOfDay(17, 0),
                ),
            ),
        )

        // When alarm fires right at 09:00 Monday, the next transition is 17:00 Monday (8 hours = 480 mins)
        val result = calculator.findNextTransition(
            customProfiles = listOf(profile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(9, 0),
        )

        assertThat(result).isEqualTo(
            NextScheduleTransition(
                targetDay = DayOfWeek.MONDAY,
                targetTime = TimeOfDay(17, 0),
                minutesFromNow = 8 * 60,
            ),
        )
    }

    @Test
    fun findNextTransition_overnightSchedule_targetsNextMorningEndTime() {
        // Monday 19:00 -> Tuesday 06:00
        val profile = CustomProfile(
            id = "bedtime",
            name = "Bedtime",
            enabled = true,
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    dailyWindows = mapOf(
                        DayOfWeek.MONDAY to TimeWindow(TimeOfDay(19, 0), TimeOfDay(6, 0)),
                    ),
                    sameScheduleEveryDay = false,
                ),
            ),
        )

        // At Monday 20:00, next transition is Tuesday 06:00 (10 hours = 600 mins)
        val result = calculator.findNextTransition(
            customProfiles = listOf(profile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(20, 0),
        )

        assertThat(result).isEqualTo(
            NextScheduleTransition(
                targetDay = DayOfWeek.TUESDAY,
                targetTime = TimeOfDay(6, 0),
                minutesFromNow = 10 * 60,
            ),
        )
    }

    @Test
    fun findNextTransition_wrapsAroundFromSundayNightToMondayMorning() {
        // Sunday 22:00 -> Monday 06:00
        val profile = CustomProfile(
            id = "sunday-sleep",
            name = "Sunday Sleep",
            enabled = true,
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.SUNDAY),
                    startTime = TimeOfDay(22, 0),
                    endTime = TimeOfDay(6, 0),
                ),
            ),
        )

        // At Sunday 23:00, next transition is Monday 06:00 (7 hours = 420 mins)
        val result = calculator.findNextTransition(
            customProfiles = listOf(profile),
            currentDay = DayOfWeek.SUNDAY,
            currentTime = TimeOfDay(23, 0),
        )

        assertThat(result).isEqualTo(
            NextScheduleTransition(
                targetDay = DayOfWeek.MONDAY,
                targetTime = TimeOfDay(6, 0),
                minutesFromNow = 7 * 60,
            ),
        )
    }

    @Test
    fun findNextTransition_multipleProfiles_picksEarliestBoundary() {
        val p1 = CustomProfile(
            id = "p1",
            name = "P1",
            enabled = true,
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.TUESDAY),
                    startTime = TimeOfDay(14, 0),
                    endTime = TimeOfDay(16, 0),
                ),
            ),
        )
        val p2 = CustomProfile(
            id = "p2",
            name = "P2",
            enabled = true,
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.TUESDAY),
                    startTime = TimeOfDay(10, 0),
                    endTime = TimeOfDay(11, 0),
                ),
            ),
        )

        val result = calculator.findNextTransition(
            customProfiles = listOf(p1, p2),
            currentDay = DayOfWeek.TUESDAY,
            currentTime = TimeOfDay(9, 15),
        )

        assertThat(result).isEqualTo(
            NextScheduleTransition(
                targetDay = DayOfWeek.TUESDAY,
                targetTime = TimeOfDay(10, 0),
                minutesFromNow = 45,
            ),
        )
    }
}

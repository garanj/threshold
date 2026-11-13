package com.garan.threshold.core.cascading

import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.model.TimeWindow
import com.garan.threshold.core.model.TriggerRule
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

    @Test
    fun findNextTransition_savedTriggersWithAndOrRules_picksEarliestReferencedScheduleAndIgnoresUnreferenced() {
        val morningSchedule = SavedTrigger(
            id = "t-morning",
            name = "Morning Focus",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(9, 0),
                    endTime = TimeOfDay(12, 0),
                ),
            ),
        )
        val officeGeo = SavedTrigger(
            id = "t-office",
            name = "At Office",
            trigger = ProfileTrigger.Geo(GeoTrigger(latitude = 51.5, longitude = -0.1)),
        )
        val eveningSchedule = SavedTrigger(
            id = "t-evening",
            name = "Evening Wind Down",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(18, 0),
                    endTime = TimeOfDay(21, 0),
                ),
            ),
        )
        // Unreferenced trigger with an earlier 08:15 boundary that MUST be ignored
        val unreferencedEarly = SavedTrigger(
            id = "t-unreferenced",
            name = "Unreferenced Early",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(8, 15),
                    endTime = TimeOfDay(8, 45),
                ),
            ),
        )
        // Trigger referenced only by a disabled profile that MUST also be ignored
        val disabledOnlyTrigger = SavedTrigger(
            id = "t-disabled",
            name = "Disabled Only",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(8, 20),
                    endTime = TimeOfDay(8, 50),
                ),
            ),
        )

        val enabledCompositeProfile = CustomProfile(
            id = "composite",
            name = "Composite",
            enabled = true,
            trigger = null,
            activationRules = listOf(
                TriggerRule(listOf("t-morning", "t-office")),
                TriggerRule(listOf("t-evening")),
            ),
        )
        val disabledProfile = CustomProfile(
            id = "disabled",
            name = "Disabled",
            enabled = false,
            trigger = null,
            activationRules = listOf(TriggerRule(listOf("t-disabled"))),
        )

        val savedTriggers = listOf(
            morningSchedule,
            officeGeo,
            eveningSchedule,
            unreferencedEarly,
            disabledOnlyTrigger,
        )

        // At Monday 08:00, earliest referenced active schedule boundary is t-morning at 09:00 (60m)
        val atEight = calculator.findNextTransition(
            customProfiles = listOf(enabledCompositeProfile, disabledProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(8, 0),
            savedTriggers = savedTriggers,
        )
        assertThat(atEight).isEqualTo(
            NextScheduleTransition(
                targetDay = DayOfWeek.MONDAY,
                targetTime = TimeOfDay(9, 0),
                minutesFromNow = 60,
            ),
        )

        // At Monday 13:00 (after morning ends), next referenced boundary is t-evening at 18:00 (300m)
        val atOnePm = calculator.findNextTransition(
            customProfiles = listOf(enabledCompositeProfile, disabledProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(13, 0),
            savedTriggers = savedTriggers,
        )
        assertThat(atOnePm).isEqualTo(
            NextScheduleTransition(
                targetDay = DayOfWeek.MONDAY,
                targetTime = TimeOfDay(18, 0),
                minutesFromNow = 5 * 60,
            ),
        )
    }
}

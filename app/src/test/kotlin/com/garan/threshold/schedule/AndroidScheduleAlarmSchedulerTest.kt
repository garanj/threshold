package com.garan.threshold.schedule

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.model.TriggerRule
import com.garan.threshold.core.port.LocalDayTime
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidScheduleAlarmSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Test
    fun syncNextScheduleAlarm_schedulesAlarmAtNextBoundaryAndCancelsWhenEmpty() {
        val baseMillis = 1_700_000_000_000L
        val scheduler = AndroidScheduleAlarmScheduler(
            context = context,
            clockSource = {
                LocalDayTime(
                    dayOfWeek = DayOfWeek.MONDAY,
                    timeOfDay = TimeOfDay(8, 30),
                )
            },
            nowEpochMillisProvider = { baseMillis },
        )

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

        scheduler.syncNextScheduleAlarm(listOf(profile))

        val shadowAlarmManager = shadowOf(alarmManager)
        val alarms = shadowAlarmManager.scheduledAlarms
        assertThat(alarms).hasSize(1)
        assertThat(alarms.first().triggerAtMs).isEqualTo(baseMillis + 30 * 60_000L)

        // When profile is disabled, alarm is cancelled
        scheduler.syncNextScheduleAlarm(listOf(profile.copy(enabled = false)))
        assertThat(shadowAlarmManager.scheduledAlarms).isEmpty()
    }

    @Test
    fun syncNextScheduleAlarm_withSavedTriggersAndOrRules_schedulesEarliestReferencedBoundary() {
        val baseMillis = 1_700_000_000_000L
        val scheduler = AndroidScheduleAlarmScheduler(
            context = context,
            clockSource = {
                LocalDayTime(
                    dayOfWeek = DayOfWeek.MONDAY,
                    timeOfDay = TimeOfDay(8, 30),
                )
            },
            nowEpochMillisProvider = { baseMillis },
        )

        val workHours = SavedTrigger(
            id = "t-work",
            name = "Work Hours",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(9, 15),
                    endTime = TimeOfDay(17, 0),
                ),
            ),
        )
        val atOffice = SavedTrigger(
            id = "t-office",
            name = "At Office",
            trigger = ProfileTrigger.Geo(GeoTrigger(latitude = 51.5, longitude = -0.1)),
        )
        val unreferencedEarly = SavedTrigger(
            id = "t-unused",
            name = "Unused Early",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(8, 40),
                    endTime = TimeOfDay(9, 0),
                ),
            ),
        )

        val focusProfile = CustomProfile(
            id = "focus",
            name = "Focus",
            enabled = true,
            trigger = null,
            activationRules = listOf(
                TriggerRule(listOf("t-work", "t-office")),
            ),
        )

        scheduler.syncNextScheduleAlarm(
            profiles = listOf(focusProfile),
            savedTriggers = listOf(workHours, atOffice, unreferencedEarly),
        )

        val shadowAlarmManager = shadowOf(alarmManager)
        val alarms = shadowAlarmManager.scheduledAlarms
        assertThat(alarms).hasSize(1)
        // Must schedule for t-work at 09:15 (45 mins from 08:30), ignoring t-unused at 08:40
        assertThat(alarms.first().triggerAtMs).isEqualTo(baseMillis + 45 * 60_000L)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun scheduleAlarmReceiver_emitsTransitionSignalOnHandledActionsAndIgnoresUnknownActions() =
        runTest(UnconfinedTestDispatcher()) {
            val receiver = ScheduleAlarmReceiver()
            var transitionCount = 0
            val job = launch {
                ScheduleTransitionNotifier.events.collect {
                    transitionCount++
                }
            }

            // Unhandled action -> ignored
            receiver.onReceive(context, Intent("com.example.UNRELATED_ACTION"))
            assertThat(transitionCount).isEqualTo(0)

            // Handled schedule alarm action -> emits transition event
            receiver.onReceive(
                context,
                Intent(AndroidScheduleAlarmScheduler.ACTION_SCHEDULE_TRANSITION_ALARM),
            )
            assertThat(transitionCount).isEqualTo(1)

            // Exact alarm permission state changed -> emits transition event
            receiver.onReceive(
                context,
                Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED),
            )
            assertThat(transitionCount).isEqualTo(2)

            job.cancel()
        }
}

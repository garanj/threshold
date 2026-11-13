package com.garan.threshold.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.garan.threshold.MainActivity
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.NfcTagEntry
import com.garan.threshold.core.model.NfcTrigger
import com.garan.threshold.core.model.NfcTriggerMode
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.model.TimeWindow
import com.garan.threshold.core.model.TriggerRule
import com.garan.threshold.ui.launcher.DialerPackageResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThresholdScheduleProfileInstrumentedTest {

    private lateinit var device: UiDevice
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.QUERY_ALL_PACKAGES")

        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
    }

    @After
    fun tearDown() {
        scenario?.onActivity { activity ->
            activity.setExactAlarmPermissionForTesting(null)
            activity.setNfcCapabilityForTesting(null)
            activity.setNowEpochMillisForTesting(null)
            runBlocking {
                activity.configureScheduleProfilesForTesting(
                    baseProfile = BaseProfileSettings(),
                    customProfiles = emptyList(),
                )
            }
        }
        scenario?.close()
        try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        } catch (_: Exception) {}
        device.pressHome()
    }

    @Test
    fun scheduleProfile_blocksTargetAppAndDisplaysSquareActiveIndicatorAcrossOvernightAndPerDayWindows() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = targetContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = targetContext.getSystemService(Context.USER_SERVICE) as UserManager
        val myUser = Process.myUserHandle()
        val userSerial = userManager.getSerialNumberForUser(myUser)
        val dialerPkg = DialerPackageResolver.resolveDefaultDialerPackage(targetContext)

        // Pick a real non-dialer launchable app with a unique label on the device to block
        val allActivities = launcherApps.getActivityList(null, myUser)
        val labelCounts = allActivities.groupingBy { it.label.toString().trim() }.eachCount()
        val targetActivity = allActivities.first { info ->
            val label = info.label.toString().trim()
            info.applicationInfo.packageName != targetContext.packageName &&
                info.applicationInfo.packageName != dialerPkg &&
                label.length >= 4 &&
                labelCounts[label] == 1
        }
        val targetLabel = targetActivity.label.toString().trim()
        val searchPrefix = targetLabel.take(3)
        val targetComponent = ComponentKey(
            packageName = targetActivity.componentName.packageName,
            activityName = targetActivity.componentName.className,
            userSerial = userSerial,
        )

        val intent = Intent(targetContext, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        scenario = ActivityScenario.launch(intent)
        scenario?.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }

        // Configure "Deep Work" profile:
        // - Monday 19:00-06:00 (overnight into Tuesday 06:00)
        // - Tuesday 10:00-11:00
        val deepWorkProfile = CustomProfile(
            id = "deep-work",
            name = "Deep Work",
            priority = 0,
            enabled = true,
            colorIndex = 0,
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    dailyWindows = mapOf(
                        DayOfWeek.MONDAY to TimeWindow(TimeOfDay(19, 0), TimeOfDay(6, 0)),
                        DayOfWeek.TUESDAY to TimeWindow(TimeOfDay(10, 0), TimeOfDay(11, 0)),
                    ),
                    sameScheduleEveryDay = false,
                ),
            ),
            appAssignments = mapOf(
                targetComponent to AppAssignment.Blocked,
            ),
        )

        // 1. Set clock to Monday 18:30 (before schedule starts) and apply profile
        scenario?.onActivity { activity ->
            activity.setClockAndTriggerAlarmForTesting(DayOfWeek.MONDAY, TimeOfDay(18, 30))
            runBlocking {
                activity.configureScheduleProfilesForTesting(
                    baseProfile = BaseProfileSettings(),
                    customProfiles = listOf(deepWorkProfile),
                )
            }
            activity.searchViewModel.activateSearch()
            activity.searchViewModel.onQueryChanged(searchPrefix)
        }
        device.waitForIdle()

        // At Monday 18:30: target app IS visible in search results, and active indicator is absent
        val visibleBeforeWindow = device.waitForFresh(By.text(targetLabel), 5000L)
        assertThat(visibleBeforeWindow).isNotNull()
        val badgeAbsentBeforeWindow = device.waitUntilGoneFresh(By.desc("Active profile: Deep Work"), 0L)
        assertThat(badgeAbsentBeforeWindow).isTrue()

        // 2. Advance clock to Tuesday 02:00 (inside Monday's 19:00-06:00 overnight spillover)
        scenario?.onActivity { activity ->
            activity.setClockAndTriggerAlarmForTesting(DayOfWeek.TUESDAY, TimeOfDay(2, 0))
        }
        device.waitForIdle()

        // At Tuesday 02:00: square active profile indicator ("D") IS visible, and target app disappears
        val badgeDuringOvernight = device.waitForFresh(By.desc("Active profile: Deep Work"), 5000L)
        assertThat(badgeDuringOvernight).isNotNull()
        val goneDuringOvernight = device.waitUntilGoneFresh(By.text(targetLabel), 5000L)
        assertThat(goneDuringOvernight).isTrue()

        // 3. Advance clock to Tuesday 07:00 (after overnight window ends, before Tuesday 10:00 window)
        scenario?.onActivity { activity ->
            activity.setClockAndTriggerAlarmForTesting(DayOfWeek.TUESDAY, TimeOfDay(7, 0))
        }
        device.waitForIdle()

        // At Tuesday 07:00: target app reappears in search results and active indicator clears
        val visibleBetweenWindows = device.waitForFresh(By.text(targetLabel), 5000L)
        assertThat(visibleBetweenWindows).isNotNull()
        val badgeGoneBetweenWindows = device.waitUntilGoneFresh(By.desc("Active profile: Deep Work"), 5000L)
        assertThat(badgeGoneBetweenWindows).isTrue()

        // 4. Advance clock to Tuesday 10:30 (inside Tuesday's 10:00-11:00 daytime window)
        scenario?.onActivity { activity ->
            activity.setClockAndTriggerAlarmForTesting(DayOfWeek.TUESDAY, TimeOfDay(10, 30))
        }
        device.waitForIdle()

        // At Tuesday 10:30: square active profile indicator is back, and target app is blocked again
        val badgeDuringTuesdaySlot = device.waitForFresh(By.desc("Active profile: Deep Work"), 5000L)
        assertThat(badgeDuringTuesdaySlot).isNotNull()
        val goneDuringTuesdaySlot = device.waitUntilGoneFresh(By.text(targetLabel), 5000L)
        assertThat(goneDuringTuesdaySlot).isTrue()

        // 5. Recreate MainActivity (simulating fontScale/density/locale change) and verify test hooks
        //    and shared state continue to drive the surviving SearchViewModel on the new Activity instance.
        var locationSourceBeforeRecreate: Any? = null
        scenario?.onActivity { activity ->
            locationSourceBeforeRecreate = activity.locationSource
        }
        scenario?.recreate()
        scenario?.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            assertThat(activity.locationSource).isSameInstanceAs(locationSourceBeforeRecreate)
            activity.setExactAlarmPermissionForTesting(false)
        }
        device.waitForIdle()

        val faultBadge = device.waitForFresh(By.desc("Missing profile permissions"), 5000L)
        assertThat(faultBadge).isNotNull()

        scenario?.onActivity { activity ->
            activity.setExactAlarmPermissionForTesting(true)
            activity.setClockAndTriggerAlarmForTesting(DayOfWeek.TUESDAY, TimeOfDay(12, 0))
        }
        device.waitForIdle()

        val faultBadgeCleared = device.waitUntilGoneFresh(By.desc("Missing profile permissions"), 5000L)
        assertThat(faultBadgeCleared).isTrue()
        val badgeClearedAfterRecreateClockUpdate = device.waitUntilGoneFresh(
            By.desc("Active profile: Deep Work"),
            5000L,
        )
        assertThat(badgeClearedAfterRecreateClockUpdate).isTrue()
    }

    @Test
    fun nfcAndScheduleCompositeRule_rejectsEarlyTapLatchesDuringWindowAndAutoUnlatchesWhenScheduleEnds() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = targetContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = targetContext.getSystemService(Context.USER_SERVICE) as UserManager
        val myUser = Process.myUserHandle()
        val userSerial = userManager.getSerialNumberForUser(myUser)
        val dialerPkg = DialerPackageResolver.resolveDefaultDialerPackage(targetContext)

        val allActivities = launcherApps.getActivityList(null, myUser)
        val targetActivity = allActivities.first { info ->
            val pkg = info.componentName.packageName
            val label = info.label?.toString()?.trim().orEmpty()
            pkg != dialerPkg &&
                pkg != targetContext.packageName &&
                label.length >= 4 &&
                allActivities.count { it.label?.toString()?.trim().equals(label, ignoreCase = true) } == 1
        }
        val targetLabel = targetActivity.label.toString().trim()
        val targetComponent = ComponentKey(
            packageName = targetActivity.componentName.packageName,
            activityName = targetActivity.componentName.className,
            userSerial = userSerial,
        )

        val lateNightTrigger = SavedTrigger(
            id = "t-late-night",
            name = "Late Night",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    dailyWindows = mapOf(
                        DayOfWeek.MONDAY to TimeWindow(TimeOfDay(22, 0), TimeOfDay(6, 0)),
                    ),
                    sameScheduleEveryDay = false,
                ),
            ),
        )
        val bedsideNfcTrigger = SavedTrigger(
            id = "t-bedside-nfc",
            name = "Bedside Tag",
            trigger = ProfileTrigger.Nfc(
                NfcTrigger(
                    tags = listOf(NfcTagEntry(uid = "04:A2:3F:8B:1C:90:80", label = "Bedside")),
                    mode = NfcTriggerMode.TOGGLE_ON_TAP,
                ),
            ),
        )
        val sleepProfile = CustomProfile(
            id = "sleep-mode",
            name = "Sleep Mode",
            priority = 0,
            enabled = true,
            trigger = null,
            activationRules = listOf(
                TriggerRule(listOf("t-late-night", "t-bedside-nfc")),
            ),
            appAssignments = mapOf(targetComponent to AppAssignment.Blocked),
            colorIndex = 2,
        )

        val launchIntent = Intent(targetContext, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        scenario = ActivityScenario.launch(launchIntent)
        scenario?.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.setExactAlarmPermissionForTesting(true)
            activity.setNfcCapabilityForTesting(true)
            activity.setNowEpochMillisForTesting(1_700_000_000_000L)
            activity.setClockAndTriggerAlarmForTesting(DayOfWeek.MONDAY, TimeOfDay(21, 0))
            runBlocking {
                activity.configureScheduleProfilesForTesting(
                    baseProfile = BaseProfileSettings(
                        appAssignments = mapOf(targetComponent to AppAssignment.Standard),
                    ),
                    customProfiles = listOf(sleepProfile),
                    savedTriggers = listOf(lateNightTrigger, bedsideNfcTrigger),
                )
            }
        }
        device.waitForIdle()

        // 1. At Monday 21:00 (before Late Night starts at 22:00), simulate early NFC tap -> rejected!
        val queryPrefix = targetLabel.take(3)
        scenario?.onActivity { activity ->
            activity.simulateNfcTapForTesting("04:A2:3F:8B:1C:90:80")
            activity.searchViewModel.activateSearch()
            activity.searchViewModel.onQueryChanged(queryPrefix)
        }
        device.waitForIdle()
        assertThat(device.waitUntilGoneFresh(By.desc("Active profile: Sleep Mode"), 0L)).isTrue()
        assertThat(device.waitForFresh(By.text(targetLabel), 5000L)).isNotNull()

        // 2. Advance clock to Monday 22:15 (inside Late Night window) -> still inactive until NFC tap
        scenario?.onActivity { activity ->
            activity.setClockAndTriggerAlarmForTesting(DayOfWeek.MONDAY, TimeOfDay(22, 15))
        }
        device.waitForIdle()
        assertThat(device.waitUntilGoneFresh(By.desc("Active profile: Sleep Mode"), 0L)).isTrue()

        // 3. Simulate NFC tap at Monday 22:15 -> latches ON, activates Sleep Mode, blocks target app
        scenario?.onActivity { activity ->
            activity.simulateNfcTapForTesting("04a23f8b1c9080")
        }
        device.waitForIdle()
        assertThat(device.waitForFresh(By.desc("Active profile: Sleep Mode"), 5000L)).isNotNull()
        assertThat(device.waitUntilGoneFresh(By.text(targetLabel), 5000L)).isTrue()

        // 4. Advance clock to Tuesday 07:00 (after overnight Late Night window ends) -> auto-unlatches NFC!
        scenario?.onActivity { activity ->
            activity.setClockAndTriggerAlarmForTesting(DayOfWeek.TUESDAY, TimeOfDay(7, 0))
        }
        device.waitForIdle()
        assertThat(device.waitUntilGoneFresh(By.desc("Active profile: Sleep Mode"), 5000L)).isTrue()
        assertThat(device.waitForFresh(By.text(targetLabel), 5000L)).isNotNull()
    }
}

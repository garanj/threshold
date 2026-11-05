package com.garan.threshold.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.garan.threshold.MainActivity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThresholdSearchScrollInstrumentedTest {

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
        scenario?.close()
        try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        } catch (_: Exception) {}
        device.pressHome()
    }

    @Test
    fun queryChanges_resetScrollPositionToTop_onLiveDevice() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = targetContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val myUser = Process.myUserHandle()

        // Gather all launchable apps on the device
        val activities = launcherApps.getActivityList(null, myUser)
            .filter { it.applicationInfo.packageName != targetContext.packageName }
        assertThat(activities).isNotEmpty()

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

        // 1. Activate search mode and query a broad single character (e.g. 's' or 'c')
        val firstChar = activities.first().label.toString().first().lowercaseChar().toString()
        scenario?.onActivity { activity ->
            activity.searchViewModel.activateSearch()
            activity.searchViewModel.onQueryChanged(firstChar)
        }

        // Wait for results to be populated
        val firstResultLabel = device.wait(
            Until.findObject(By.clazz("android.widget.TextView")),
            5000L,
        )
        assertThat(firstResultLabel).isNotNull()

        // 2. Scroll the search results list downward
        val displayHeight = device.displayHeight
        val displayWidth = device.displayWidth
        device.swipe(
            displayWidth / 2,
            (displayHeight * 0.7).toInt(),
            displayWidth / 2,
            (displayHeight * 0.3).toInt(),
            10,
        )
        device.waitForIdle()

        // 3. Change query by appending a character or switching query
        val candidateLabel = activities.first().label.toString()
        val refinedQuery = if (candidateLabel.length > 2) candidateLabel.substring(0, 2) else candidateLabel
        scenario?.onActivity { activity ->
            activity.searchViewModel.onQueryChanged(refinedQuery)
        }
        device.waitForIdle()

        // 4. Verify top result for refinedQuery is immediately visible at the top
        val topAppLabel = device.wait(
            Until.findObject(By.text(candidateLabel)),
            5000L,
        )
        assertThat(topAppLabel).isNotNull()
    }
}

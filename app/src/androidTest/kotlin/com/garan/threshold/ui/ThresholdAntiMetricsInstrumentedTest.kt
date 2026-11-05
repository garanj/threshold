package com.garan.threshold.ui

import android.content.Context
import android.content.Intent
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
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class ThresholdAntiMetricsInstrumentedTest {

    private lateinit var device: UiDevice
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.QUERY_ALL_PACKAGES")

        // Reset anti-metrics storage for test isolation
        instrumentation.targetContext.getSharedPreferences("threshold_anti_metrics", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

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
    fun sleepAndUnlock_updatesAntiMetricsReadout() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
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

        // Wait for Home Canvas to be visible
        val initialStatus = device.wait(
            Until.findObject(By.text(Pattern.compile(".*UNLOCK.*"))),
            5000L,
        )
        assertThat(initialStatus).isNotNull()

        // 1. Simulate screen-off sleep event (48 minutes prior)
        val screenOffTime = System.currentTimeMillis() - 48 * 60_000L
        scenario?.onActivity { activity ->
            activity.antiMetricsRepository.onScreenOff(screenOffTime)
        }

        // 2. Simulate device unlock event
        scenario?.onActivity { activity ->
            activity.antiMetricsRepository.onUnlock(System.currentTimeMillis())
        }
        device.waitForIdle()

        // 3. Verify anti-metric readout updates with rest duration and incremented unlock count on live UI canvas
        val updatedStatus = device.wait(
            Until.findObject(By.text(Pattern.compile("REST 48M.*·.*UNLOCK.*"))),
            5000L,
        )
        assertThat(updatedStatus).isNotNull()
    }
}

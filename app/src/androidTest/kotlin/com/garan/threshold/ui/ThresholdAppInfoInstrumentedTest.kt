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
import com.garan.threshold.MainActivity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class ThresholdAppInfoInstrumentedTest {

    private lateinit var device: UiDevice
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.QUERY_ALL_PACKAGES")

        // Ensure device is awake, keyguard dismissed, and no stale Settings task is left from a prior run
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.executeShellCommand("am force-stop com.android.settings")
    }

    @After
    fun tearDown() {
        scenario?.close()
        device.executeShellCommand("am force-stop com.android.settings")
        try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        } catch (_: Exception) {}
        device.pressHome()
    }

    @Test
    fun longPressAppSearchResult_opensSystemAppInfoAndDismissesSearch() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = targetContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val myUser = Process.myUserHandle()

        // Locate an installed launcher app (prefer third-party/non-settings app to avoid action collision)
        val activities = launcherApps.getActivityList(null, myUser)
        val candidate = activities.firstOrNull {
            it.applicationInfo.packageName != targetContext.packageName &&
                it.applicationInfo.packageName != "com.android.settings"
        } ?: activities.firstOrNull { it.applicationInfo.packageName != targetContext.packageName }
            ?: error("No launchable activities found on device for testing")

        val targetLabel = candidate.label.toString()
        // Use a prefix query so the search input box text differs from the result item label
        val searchQuery = if (targetLabel.length > 3) targetLabel.substring(0, targetLabel.length - 1) else targetLabel

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

        // 1. Activate search mode and set query to target app prefix
        scenario?.onActivity { activity ->
            activity.searchViewModel.activateSearch()
            activity.searchViewModel.onQueryChanged(searchQuery)
        }

        // 2. Find target app in search results and perform long click
        val resultRow = device.waitForFresh(
            By.text(targetLabel).clazz("android.widget.TextView"),
            10000L,
        ) ?: device.waitForFresh(By.text(targetLabel), 5000L)
        assertThat(resultRow).isNotNull()
        checkNotNull(resultRow).longClick()

        // 3. Verify that Android App Info / Settings screen is launched
        val settingsPattern = Pattern.compile("com\\.android\\.settings|com\\.google\\.android\\.settings.*")
        val appDetailsAppeared = device.waitForFresh(
            By.pkg(settingsPattern),
            15000L,
        ) != null ||
            settingsPattern.matcher(device.currentPackageName ?: "").matches()
        assertThat(appDetailsAppeared).isTrue()

        // 4. Navigate back to Threshold and verify search state was dismissed
        device.pressBack()
        val restoredPlaceholder = device.waitForFresh(By.text("Type to launch..."), 5000L)
        assertThat(restoredPlaceholder).isNotNull()
    }
}

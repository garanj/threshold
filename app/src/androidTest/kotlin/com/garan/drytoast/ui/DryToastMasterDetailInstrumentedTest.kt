package com.garan.drytoast.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import com.garan.drytoast.DryToastAppContainer
import com.garan.drytoast.MainActivity
import com.garan.drytoast.ui.settings.AdaptiveSettingsTestTags
import com.garan.drytoast.ui.settings.BreakGlassTestTags
import com.garan.drytoast.ui.settings.HardenedModeTestTags
import com.garan.drytoast.ui.settings.ProfilesTestTags
import com.garan.drytoast.ui.settings.SettingsTestTags
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DryToastMasterDetailInstrumentedTest {

    private lateinit var device: UiDevice
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.QUERY_ALL_PACKAGES")
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        device.setOrientationNatural()

        val container = DryToastAppContainer.from(instrumentation.targetContext)
        container.defaultLauncherOverrideForTesting = true
        runBlocking {
            val currentGeneral = container.settingsRepository.getSettings().editableSnapshot.general
            container.settingsRepository.updateGeneralSettings(
                currentGeneral.copy(hasCompletedOnboarding = true),
            )
            container.settingsRepository.setHasCompletedOnboarding(true)
        }
    }

    @After
    fun tearDown() {
        runCatching { scenario?.close() }
        runCatching { device.executeShellCommand("wm size reset") }
        runCatching { device.executeShellCommand("wm density reset") }
        runCatching { device.setOrientationNatural() }
        runCatching { device.unfreezeRotation() }
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.dropShellPermissionIdentity()
        }
        device.pressHome()
    }

    @Test
    fun tablet2560x1600_rendersTwoPaneMasterDetail_switchesSections_andReleasesAppListOnHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext

        try {
            // 1. Configure 2560x1600 @ 320dpi (1280x800dp EXPANDED tablet viewport)
            device.executeShellCommand("wm size 2560x1600")
            device.executeShellCommand("wm density 320")
            assertThat(
                pollFresh(10_000L) {
                    device.displayWidth == 2560 && device.displayHeight == 1600
                },
            ).isTrue()

            val homeIntent = Intent(targetContext, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            scenario = ActivityScenario.launch(homeIntent)
            scenario?.onActivity { activity ->
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                activity.searchViewModel.activateSearch()
                activity.searchViewModel.onQueryChanged(":settings")
            }
            assertThat(waitForActivityWindowFocus()).isTrue()

            // 2. Open Settings via ':settings' command
            checkNotNull(
                device.waitForFresh(By.text("Dry Toast Settings"), 10_000L),
            ) { "Expected ':settings' action row on Home" }.click()
            device.waitForIdle()

            // 3. Verify simultaneous 2-pane Settings master rail + default ConfigureApps detail pane
            assertThat(device.waitForFresh(By.res(AdaptiveSettingsTestTags.TWO_PANE_ROOT), 10_000L)).isNotNull()
            val masterPane = checkNotNull(
                device.waitForFresh(By.res(AdaptiveSettingsTestTags.MASTER_PANE), 5_000L),
            ) { "Expected Settings master pane" }
            assertThat(device.waitForFresh(By.res(AdaptiveSettingsTestTags.HAIRLINE_DIVIDER), 5_000L)).isNotNull()
            val detailPane = checkNotNull(
                device.waitForFresh(By.res(AdaptiveSettingsTestTags.DETAIL_PANE), 5_000L),
            ) { "Expected Settings detail pane" }
            assertThat(masterPane.visibleBounds.right).isAtMost(detailPane.visibleBounds.left)
            assertThat(device.waitForFresh(By.res(SettingsTestTags.SETTINGS_LIST), 5_000L)).isNotNull()
            assertThat(device.waitForFresh(By.res("configure_apps_list"), 5_000L)).isNotNull()

            // 4. Switch across Profiles (2-column grid in 919dp detail pane), Hardened Mode, and Break-Glass
            findInMasterSettingsList(SettingsTestTags.NAV_PROFILES).click()
            device.waitForIdle()
            assertThat(device.waitForFresh(By.res(ProfilesTestTags.PROFILES_GRID), 10_000L)).isNotNull()
            assertThat(device.waitForFresh(By.res(SettingsTestTags.SETTINGS_LIST), 5_000L)).isNotNull()

            findInMasterSettingsList(SettingsTestTags.NAV_HARDENED_MODE).click()
            device.waitForIdle()
            assertThat(device.waitForFresh(By.res(HardenedModeTestTags.SCREEN_LIST), 10_000L)).isNotNull()
            assertThat(device.waitForFresh(By.res(SettingsTestTags.SETTINGS_LIST), 5_000L)).isNotNull()

            findInMasterSettingsList(SettingsTestTags.NAV_BREAK_GLASS).click()
            device.waitForIdle()
            assertThat(device.waitForFresh(By.res(BreakGlassTestTags.SCREEN), 10_000L)).isNotNull()
            assertThat(device.waitForFresh(By.res(SettingsTestTags.SETTINGS_LIST), 5_000L)).isNotNull()

            // 5. Back 1 pops Break-Glass back to default ConfigureApps; Back 2 exits 2-pane Settings to Home
            device.pressBack()
            device.waitForIdle()
            assertThat(device.waitUntilGoneFresh(By.res(BreakGlassTestTags.SCREEN), 5_000L)).isTrue()
            assertThat(device.waitForFresh(By.res("configure_apps_list"), 5_000L)).isNotNull()

            device.pressBack()
            device.waitForIdle()
            assertThat(device.waitUntilGoneFresh(By.res(AdaptiveSettingsTestTags.TWO_PANE_ROOT), 5_000L)).isTrue()

            // 6. Verify Settings app metadata is released upon returning to Home
            assertThat(
                pollFresh(5_000L) {
                    var released = false
                    scenario?.onActivity { activity ->
                        released = activity.settingsViewModel.uiState.value.availableApps.isEmpty()
                    }
                    released
                },
            ).isTrue()
        } finally {
            runCatching { device.executeShellCommand("wm size reset") }
            runCatching { device.executeShellCommand("wm density reset") }
        }
    }

    private fun findInMasterSettingsList(tag: String): UiObject2 {
        device.waitForFresh(By.res(tag), 1_500L)?.let { return it }
        repeat(5) {
            val listNode = checkNotNull(
                device.waitForFresh(By.res(SettingsTestTags.SETTINGS_LIST), 5_000L),
            ) { "Expected settings_list in master pane" }
            val bounds = listNode.visibleBounds
            val centerX = bounds.centerX()
            val startY = (bounds.top + bounds.height() * 0.75f).toInt()
            val endY = (bounds.top + bounds.height() * 0.25f).toInt()
            device.swipe(centerX, startY, centerX, endY, 15)
            device.waitForIdle()
            device.waitForFresh(By.res(tag), 2_000L)?.let { return it }
        }
        error("Expected '$tag' inside master settings_list after scrolling")
    }

    private fun waitForActivityWindowFocus(): Boolean = pollFresh(5_000L) {
        var focused = false
        runCatching {
            scenario?.onActivity { activity ->
                focused = activity.hasWindowFocus()
            }
        }
        focused
    }
}

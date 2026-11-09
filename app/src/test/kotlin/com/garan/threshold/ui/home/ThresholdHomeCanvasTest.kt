package com.garan.threshold.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.ui.theme.ThresholdTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThresholdHomeCanvasTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun defaultCanvas_registersReceiverAndUpdatesDisplayedTimeOnTick() {
        var simulatedNow = ZonedDateTime.of(2026, 11, 2, 10, 9, 59, 0, java.time.ZoneId.of("UTC"))

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    clockProvider = { simulatedNow },
                )
            }
        }

        composeTestRule.onNodeWithText("10:09").assertIsDisplayed()
        composeTestRule.onNodeWithText("Monday, November 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("THRESHOLD").assertIsDisplayed()

        // Advance clock across minute boundary and send ACTION_TIME_TICK broadcast
        simulatedNow = simulatedNow.plusSeconds(1)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("10:10").assertIsDisplayed()
        composeTestRule.onNodeWithText("THRESHOLD").assertIsDisplayed()
    }

    @Test
    fun defaultCanvas_unregistersTimeTickReceiverOnLifecycleStopAndReregistersOnStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        val lifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.STARTED
            }
            override val lifecycle: Lifecycle get() = registry
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                ThresholdTheme {
                    ThresholdHomeCanvas()
                }
            }
        }
        composeTestRule.waitForIdle()

        val hasTimeTickReceiverWhenStarted = shadowApp.registeredReceivers.any {
            it.intentFilter.hasAction(Intent.ACTION_TIME_TICK)
        }
        assertThat(hasTimeTickReceiverWhenStarted).isTrue()

        // Transition to CREATED (ON_STOP) -> receiver must be unregistered
        composeTestRule.runOnIdle {
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        composeTestRule.waitForIdle()

        val hasTimeTickReceiverWhenStopped = shadowApp.registeredReceivers.any {
            it.intentFilter.hasAction(Intent.ACTION_TIME_TICK)
        }
        assertThat(hasTimeTickReceiverWhenStopped).isFalse()

        // Transition back to STARTED (ON_START) -> receiver must re-register
        composeTestRule.runOnIdle {
            lifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        composeTestRule.waitForIdle()

        val hasTimeTickReceiverWhenRestarted = shadowApp.registeredReceivers.any {
            it.intentFilter.hasAction(Intent.ACTION_TIME_TICK)
        }
        assertThat(hasTimeTickReceiverWhenRestarted).isTrue()
    }

    @Test
    fun canvas_displaysCapabilityFaultBadgeAndActiveProfileBadges_andInvokesCallbackOnClick() {
        var faultClicked = false
        val workProfile = CustomProfile(id = "work", name = "Work Focus", colorIndex = 0)

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "09:41",
                    dateText = "Monday, November 2",
                    statusText = "REST 8H 12M  •  4 UNLOCKS",
                    activeProfiles = persistentListOf(workProfile),
                    hasCapabilityIssues = true,
                    onCapabilityFaultClick = { faultClicked = true },
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Active profile: Work Focus").assertIsDisplayed()
        composeTestRule.onNodeWithText("W").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Missing profile permissions").assertIsDisplayed()
        composeTestRule.onNodeWithText("!").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Missing profile permissions").performClick()
        assertThat(faultClicked).isTrue()
    }
}

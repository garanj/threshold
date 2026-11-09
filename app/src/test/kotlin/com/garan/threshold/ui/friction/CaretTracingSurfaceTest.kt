package com.garan.threshold.ui.friction

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.theme.ThresholdTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CaretTracingSurfaceTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun initialState_showsGoBackButton_andDismissesOnClick() {
        var dismissed = false
        var confirmed = false

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            ThresholdTheme {
                CaretTracingSurface(
                    onConfirmOpen = { confirmed = true },
                    onDismiss = { dismissed = true },
                    totalDurationMs = 10_000L,
                )
            }
        }

        composeTestRule.onNodeWithTag("caret_tracing_surface").assertIsDisplayed()
        composeTestRule.onNodeWithTag("caret_tracing_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go back").assertIsDisplayed()

        composeTestRule.onNodeWithText("Go back").performClick()
        assertThat(dismissed).isTrue()
        assertThat(confirmed).isFalse()
    }

    @Test
    fun animationCompletion_triggersHapticCadence_andRevealsOpenButton() {
        var confirmed = false
        var dismissed = false
        val haptic = TestHapticEngine()

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            ThresholdTheme {
                CaretTracingSurface(
                    onConfirmOpen = { confirmed = true },
                    onDismiss = { dismissed = true },
                    hapticEngine = haptic,
                    totalDurationMs = 1000L, // 100ms per half-pass across 5 passes (200ms per pass)
                )
            }
        }

        composeTestRule.waitForIdle()
        // Latch the animation start time on the first frame
        composeTestRule.mainClock.advanceTimeByFrame()

        // Pass 1: Apex at 100ms (playTime = 112ms)
        composeTestRule.mainClock.advanceTimeBy(112L)
        assertThat(haptic.rotaryDetentCount).isEqualTo(1)
        assertThat(haptic.targetSnapCount).isEqualTo(0)
        assertThat(haptic.relaySnapCount).isEqualTo(0)

        // Pass 1: Complete at 200ms (playTime = 208ms)
        composeTestRule.mainClock.advanceTimeBy(96L)
        assertThat(haptic.rotaryDetentCount).isEqualTo(1)
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        // Pass 2: Apex at 300ms (playTime = 304ms)
        composeTestRule.mainClock.advanceTimeBy(96L)
        assertThat(haptic.rotaryDetentCount).isEqualTo(2)

        // Pass 2: Complete at 400ms (playTime = 400ms)
        composeTestRule.mainClock.advanceTimeBy(96L)
        assertThat(haptic.targetSnapCount).isEqualTo(2)

        // Pass 3: Apex at 500ms (playTime = 512ms)
        composeTestRule.mainClock.advanceTimeBy(112L)
        assertThat(haptic.rotaryDetentCount).isEqualTo(3)

        // Pass 3: Complete at 600ms (playTime = 608ms)
        composeTestRule.mainClock.advanceTimeBy(96L)
        assertThat(haptic.targetSnapCount).isEqualTo(3)

        // Pass 4: Apex at 700ms (playTime = 704ms)
        composeTestRule.mainClock.advanceTimeBy(96L)
        assertThat(haptic.rotaryDetentCount).isEqualTo(4)

        // Pass 4: Complete at 800ms (playTime = 800ms)
        composeTestRule.mainClock.advanceTimeBy(96L)
        assertThat(haptic.targetSnapCount).isEqualTo(4)

        // Pass 5: Apex at 900ms (playTime = 912ms)
        composeTestRule.mainClock.advanceTimeBy(112L)
        assertThat(haptic.rotaryDetentCount).isEqualTo(5)
        assertThat(haptic.relaySnapCount).isEqualTo(0)

        // Pass 5: Complete at 1000ms (playTime = 1008ms)
        composeTestRule.mainClock.advanceTimeBy(96L)
        assertThat(haptic.relaySnapCount).isEqualTo(1)

        // "Open" action button is revealed and active
        composeTestRule.mainClock.advanceTimeBy(ThresholdMotion.SHUTTER_OPEN_DURATION_MS.toLong() + 50L)
        composeTestRule.onNodeWithText("Open").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open").performClick()

        assertThat(confirmed).isTrue()
        assertThat(dismissed).isFalse()
    }

    @Test
    fun systemBack_invokesDismiss() {
        var dismissed = false

        composeTestRule.setContent {
            ThresholdTheme {
                CaretTracingSurface(
                    onConfirmOpen = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        assertThat(dismissed).isTrue()
    }

    @Test
    fun systemAnimatorDurationScaleZero_doesNotBypassFrictionDelay() {
        val haptic = TestHapticEngine()
        composeTestRule.mainClock.autoAdvance = false

        val contentResolver = composeTestRule.activity.contentResolver
        try {
            Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

            composeTestRule.setContent {
                ThresholdTheme {
                    CaretTracingSurface(
                        onConfirmOpen = {},
                        onDismiss = {},
                        hapticEngine = haptic,
                        totalDurationMs = 600L,
                    )
                }
            }

            // Advance a single frame: with UnscaledMotionDurationScale, it must NOT jump to Open
            composeTestRule.mainClock.advanceTimeBy(32L)
            composeTestRule.onNodeWithText("Go back").assertIsDisplayed()
            assertThat(haptic.relaySnapCount).isEqualTo(0)

            // Only after advancing the full duration does it complete
            composeTestRule.mainClock.advanceTimeBy(700L)
            assertThat(haptic.relaySnapCount).isEqualTo(1)
            composeTestRule.onNodeWithText("Open").assertIsDisplayed()
        } finally {
            Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    private class TestHapticEngine : HapticEngine {
        var rotaryDetentCount = 0
        var relaySnapCount = 0
        var resistanceDetentCount = 0
        var targetSnapCount = 0

        override fun rotaryDetent() {
            rotaryDetentCount++
        }
        override fun relaySnap() {
            relaySnapCount++
        }
        override fun resistanceDetent() {
            resistanceDetentCount++
        }
        override fun targetSnap() {
            targetSnapCount++
        }
    }
}

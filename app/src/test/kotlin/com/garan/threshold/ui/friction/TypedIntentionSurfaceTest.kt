package com.garan.threshold.ui.friction

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.garan.threshold.core.port.HapticEngine
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
class TypedIntentionSurfaceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysHeaderAndTargetPhraseWithGoBackButton() {
        var dismissed = false
        var confirmed = false

        composeTestRule.setContent {
            ThresholdTheme {
                TypedIntentionSurface(
                    appLabel = "YouTube",
                    overridePhrase = "I need to complete a task",
                    onConfirmOpen = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("TYPED INTENTION").assertIsDisplayed()
        composeTestRule.onNodeWithText("I need to complete a task").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go back").assertIsDisplayed()

        // Clicking "Go back" triggers onDismiss
        composeTestRule.onNodeWithText("Go back").performClick()
        assertThat(dismissed).isTrue()
        assertThat(confirmed).isFalse()
    }

    @Test
    fun typingWrongLetter_triggersResistanceDetentAndDoesNotAdvance() {
        val haptic = TestHapticEngine()

        composeTestRule.setContent {
            ThresholdTheme {
                TypedIntentionSurface(
                    appLabel = "YouTube",
                    overridePhrase = "Focus now",
                    onConfirmOpen = {},
                    onDismiss = {},
                    hapticEngine = haptic,
                )
            }
        }

        // Expected first letter is 'F'. User types 'Z'.
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Z")
        composeTestRule.waitForIdle()

        assertThat(haptic.resistanceDetentCount).isEqualTo(1)
        assertThat(haptic.targetSnapCount).isEqualTo(0)
        composeTestRule.onNodeWithText("Go back").assertIsDisplayed()
    }

    @Test
    fun typingFullPhrase_morphsButtonToOpen_andClickingOpenTriggersConfirm() {
        val haptic = TestHapticEngine()
        var confirmed = false

        composeTestRule.setContent {
            ThresholdTheme {
                TypedIntentionSurface(
                    appLabel = "YouTube",
                    overridePhrase = "Go",
                    onConfirmOpen = { confirmed = true },
                    onDismiss = {},
                    hapticEngine = haptic,
                )
            }
        }

        // Type 'g' and 'o'
        composeTestRule.onNode(hasSetTextAction()).performTextInput("g")
        composeTestRule.waitForIdle()
        assertThat(haptic.targetSnapCount).isEqualTo(0)

        composeTestRule.onNode(hasSetTextAction()).performTextInput("o")
        composeTestRule.waitForIdle()

        // Phrase complete -> target snap fired, button is "Open"
        assertThat(haptic.targetSnapCount).isEqualTo(1)
        composeTestRule.onNodeWithText("Open").assertIsDisplayed()

        // Clicking "Open" confirms launch
        composeTestRule.onNodeWithText("Open").performClick()
        assertThat(confirmed).isTrue()
    }

    private class TestHapticEngine : HapticEngine {
        var rotaryDetentCount = 0
            private set
        var targetSnapCount = 0
            private set
        var resistanceDetentCount = 0
            private set
        var relaySnapCount = 0
            private set

        override fun rotaryDetent() {
            rotaryDetentCount++
        }

        override fun targetSnap() {
            targetSnapCount++
        }

        override fun resistanceDetent() {
            resistanceDetentCount++
        }

        override fun relaySnap() {
            relaySnapCount++
        }
    }
}

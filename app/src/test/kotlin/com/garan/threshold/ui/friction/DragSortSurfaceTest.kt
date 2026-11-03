package com.garan.threshold.ui.friction

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class DragSortSurfaceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysTargetsAndDraggableGlyphsWithGoBackButton() {
        var dismissed = false
        var confirmed = false

        composeTestRule.setContent {
            ThresholdTheme {
                DragSortSurface(
                    pairCount = 6,
                    seed = 42L,
                    onConfirmOpen = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithTag("drag_sort_surface").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go back").assertIsDisplayed()

        for (i in 0 until 6) {
            composeTestRule.onNodeWithTag("target_glyph_$i").assertIsDisplayed()
            composeTestRule.onNodeWithTag("draggable_glyph_$i").assertIsDisplayed()
        }

        // Clicking "Go back" triggers onDismiss
        composeTestRule.onNodeWithText("Go back").performClick()
        assertThat(dismissed).isTrue()
        assertThat(confirmed).isFalse()
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

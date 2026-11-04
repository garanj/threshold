package com.garan.threshold.ui.friction

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.garan.threshold.core.friction.DragSortSession
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.ui.theme.ThresholdTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DragSortSurfaceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysCanvasWithGoBackButton_andDismissesOnClick() {
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
        composeTestRule.onNodeWithTag("drag_sort_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithText("Go back").assertIsDisplayed()

        composeTestRule.onNodeWithText("Go back").performClick()
        assertThat(dismissed).isTrue()
        assertThat(confirmed).isFalse()
    }

    @Test
    fun draggingGlyphOffTarget_triggersResistanceDetentAndSnapsBack() {
        val haptic = TestHapticEngine()

        composeTestRule.setContent {
            ThresholdTheme {
                DragSortSurface(
                    pairCount = 6,
                    seed = 42L,
                    hapticEngine = haptic,
                    onConfirmOpen = {},
                    onDismiss = {},
                )
            }
        }

        val canvasNode = composeTestRule.onNodeWithTag("drag_sort_canvas")
        val canvasSize = canvasNode.fetchSemanticsNode().size
        val itemRadiusPx = with(composeTestRule.density) { 28.dp.toPx() }
        val pairs = DragSortSession.generateLayout(
            pairCount = 6,
            containerWidth = canvasSize.width.toFloat(),
            containerHeight = canvasSize.height.toFloat(),
            itemRadius = itemRadiusPx,
            minPairSeparation = canvasSize.width.toFloat() * 0.45f,
            random = Random(42L),
        )

        val firstPair = pairs.first()
        val wrongDrop = Offset(
            x = (firstPair.startPosition.x + 20f).coerceIn(10f, canvasSize.width - 10f),
            y = (firstPair.startPosition.y + 20f).coerceIn(10f, canvasSize.height - 10f),
        )

        canvasNode.performTouchInput {
            down(Offset(firstPair.startPosition.x, firstPair.startPosition.y))
            moveTo(wrongDrop)
            up()
        }
        composeTestRule.waitForIdle()

        assertThat(haptic.rotaryDetentCount).isEqualTo(1)
        assertThat(haptic.resistanceDetentCount).isEqualTo(1)
        assertThat(haptic.relaySnapCount).isEqualTo(0)
        composeTestRule.onNodeWithText("Go back").assertIsDisplayed()
    }

    @Test
    fun draggingAllSixGlyphsToExactTargets_matchesEveryPairAndTransitionsToOpen() {
        val haptic = TestHapticEngine()
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            ThresholdTheme {
                DragSortSurface(
                    pairCount = 6,
                    seed = 42L,
                    hapticEngine = haptic,
                    onConfirmOpen = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        val canvasNode = composeTestRule.onNodeWithTag("drag_sort_canvas")
        val canvasSize = canvasNode.fetchSemanticsNode().size
        val itemRadiusPx = with(composeTestRule.density) { 28.dp.toPx() }
        val pairs = DragSortSession.generateLayout(
            pairCount = 6,
            containerWidth = canvasSize.width.toFloat(),
            containerHeight = canvasSize.height.toFloat(),
            itemRadius = itemRadiusPx,
            minPairSeparation = canvasSize.width.toFloat() * 0.45f,
            random = Random(42L),
        )

        for (pair in pairs) {
            canvasNode.performTouchInput {
                down(Offset(pair.startPosition.x, pair.startPosition.y))
                moveTo(Offset(pair.targetPosition.x, pair.targetPosition.y))
                up()
            }
            composeTestRule.waitForIdle()
        }

        assertThat(haptic.rotaryDetentCount).isEqualTo(6)
        assertThat(haptic.relaySnapCount).isEqualTo(6)
        assertThat(haptic.resistanceDetentCount).isEqualTo(0)
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        composeTestRule.onNodeWithText("Open").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open").performClick()

        assertThat(confirmed).isTrue()
        assertThat(dismissed).isFalse()
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

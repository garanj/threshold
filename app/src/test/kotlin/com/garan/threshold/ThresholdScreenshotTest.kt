package com.garan.threshold

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.garan.threshold.ui.theme.ThresholdTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThresholdScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun snapshotHomeCanvas() {
        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas()
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_canvas.png")
    }
}

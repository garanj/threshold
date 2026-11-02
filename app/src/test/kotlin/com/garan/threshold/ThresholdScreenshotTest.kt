package com.garan.threshold

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.garan.threshold.ui.home.ThresholdHomeCanvas
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
    fun snapshotHomeCanvasDefault() {
        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Threshold",
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_canvas.png")
    }

    @Test
    fun snapshotHomeCanvasWithZeros() {
        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "00:00",
                    dateText = "Sunday, October 31",
                    statusText = "Threshold · Night Rest",
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_canvas_zeros.png")
    }

    @Test
    fun snapshotHomeCanvasWithProfileStatus() {
        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas(
                    timeText = "14:32",
                    dateText = "Wednesday, November 4",
                    statusText = "Focus · Deep Work (25m remaining)",
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/home_canvas_status.png")
    }
}

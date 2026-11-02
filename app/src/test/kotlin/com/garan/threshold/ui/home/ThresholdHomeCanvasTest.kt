package com.garan.threshold.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.garan.threshold.ui.theme.ThresholdTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThresholdHomeCanvasTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun defaultCanvas_registersReceiverAndDisplaysCurrentTime() {
        val now = ZonedDateTime.now()
        val expectedTime = now.format(DateTimeFormatter.ofPattern("HH:mm"))

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeCanvas()
            }
        }

        composeTestRule.onNodeWithText(expectedTime).assertIsDisplayed()
        composeTestRule.onNodeWithText("THRESHOLD").assertIsDisplayed()

        // Send ACTION_TIME_TICK broadcast to verify receiver handles it without crash
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(expectedTime).assertIsDisplayed()
        composeTestRule.onNodeWithText("THRESHOLD").assertIsDisplayed()
    }
}

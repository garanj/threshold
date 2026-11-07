package com.garan.threshold.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.ui.theme.ThresholdTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigureAppsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val chromeKey = ComponentKey("com.android.chrome", "Main")
    private val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
    private val socialKey = ComponentKey("com.instagram.android", "MainActivity")

    private val apps = persistentListOf(
        InstalledApp(chromeKey, "Chrome"),
        InstalledApp(clockKey, "Clock"),
        InstalledApp(socialKey, "Instagram"),
    )

    @Test
    fun initialCountsAndAppsDisplayedCorrectly() {
        val initial = mapOf(
            chromeKey to AppAssignment.Guarded(),
            socialKey to AppAssignment.Blocked,
        )

        composeTestRule.setContent {
            ThresholdTheme {
                ConfigureAppsScreen(
                    availableApps = apps,
                    initialAssignments = initial,
                    defaultFriction = FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                )
            }
        }

        // Header counts
        composeTestRule.onNodeWithText("ALL (3)").assertIsDisplayed()
        composeTestRule.onNodeWithText("BLOCKED (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("GUARDED (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("ALLOW (1)").assertIsDisplayed()

        // All 3 apps visible in ALL filter
        composeTestRule.onNodeWithText("Chrome").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clock").assertIsDisplayed()
        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
    }

    @Test
    fun filterHeaderFiltersAppsList() {
        val initial = mapOf(
            chromeKey to AppAssignment.Guarded(),
            socialKey to AppAssignment.Blocked,
        )

        composeTestRule.setContent {
            ThresholdTheme {
                ConfigureAppsScreen(
                    availableApps = apps,
                    initialAssignments = initial,
                    defaultFriction = FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                )
            }
        }

        // Click BLOCKED filter
        composeTestRule.onNodeWithText("BLOCKED (1)").performClick()
        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chrome").assertDoesNotExist()
        composeTestRule.onNodeWithText("Clock").assertDoesNotExist()

        // Click GUARDED filter
        composeTestRule.onNodeWithText("GUARDED (1)").performClick()
        composeTestRule.onNodeWithText("Chrome").assertIsDisplayed()
        composeTestRule.onNodeWithText("Instagram").assertDoesNotExist()

        // Click ALLOW filter
        composeTestRule.onNodeWithText("ALLOW (1)").performClick()
        composeTestRule.onNodeWithText("Clock").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chrome").assertDoesNotExist()
    }

    @Test
    fun savingAssignmentsInvokesCallbackWithUpdatedMap() {
        var savedMap: Map<ComponentKey, AppAssignment>? = null
        var backCalled = false

        val initial = mapOf(
            chromeKey to AppAssignment.Guarded(),
        )

        composeTestRule.setContent {
            ThresholdTheme {
                ConfigureAppsScreen(
                    availableApps = apps,
                    initialAssignments = initial,
                    defaultFriction = FrictionChoice.TYPED_INTENTION,
                    onSave = { savedMap = it },
                    onBack = { backCalled = true },
                )
            }
        }

        // Click Save
        composeTestRule.onNodeWithText("Save").performClick()

        assertThat(savedMap).isNotNull()
        assertThat(savedMap).containsKey(chromeKey)
        assertThat(backCalled).isTrue()
    }
}

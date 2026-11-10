package com.garan.threshold.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.model.TimeWindow
import com.garan.threshold.ui.theme.ThresholdTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1200dp")
class ProfilesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileEditorSheet_togglingSameScheduleEveryDayOff_displaysPerDayRowsAndOvernightLabel() {
        var savedSchedule: ScheduleTrigger? = null
        val initialSchedule = ScheduleTrigger(
            dailyWindows = mapOf(
                DayOfWeek.MONDAY to TimeWindow(TimeOfDay(19, 0), TimeOfDay(6, 0)),
                DayOfWeek.TUESDAY to TimeWindow(TimeOfDay(10, 0), TimeOfDay(11, 0)),
            ),
            sameScheduleEveryDay = true,
        )

        composeTestRule.setContent {
            ThresholdTheme {
                ProfileEditorSheet(
                    initialName = "Deep Work",
                    initialSchedule = initialSchedule,
                    isNewProfile = false,
                    onSave = { _, schedule -> savedSchedule = schedule },
                    onDismiss = {},
                )
            }
        }

        // Initially in uniform mode ("Start time" and "End time" rows visible)
        composeTestRule.onNodeWithText("Start time").assertExists()
        composeTestRule.onNodeWithText("End time").assertExists()
        composeTestRule.onNodeWithText("Ends at 06:00 the following day").assertExists()

        // Toggle "Same schedule every day" OFF
        composeTestRule
            .onNodeWithText("Same schedule every day")
            .performSemanticsAction(SemanticsActions.OnClick)

        // Individual day rows for Monday and Tuesday appear with overnight indicator on Monday
        composeTestRule.onNodeWithText("Monday").assertExists()
        composeTestRule.onNodeWithText("19:00 Mon – 06:00 Tue (next day)").assertExists()
        composeTestRule.onNodeWithText("Tuesday").assertExists()
        composeTestRule.onNodeWithText("10:00").assertExists()
        composeTestRule.onNodeWithText("11:00").assertExists()

        // Save and verify per-day windows and sameScheduleEveryDay = false are preserved
        composeTestRule
            .onNodeWithText("Save")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(savedSchedule).isNotNull()
        assertThat(savedSchedule?.sameScheduleEveryDay).isFalse()
        assertThat(savedSchedule?.dailyWindows?.get(DayOfWeek.MONDAY))
            .isEqualTo(TimeWindow(TimeOfDay(19, 0), TimeOfDay(6, 0)))
        assertThat(savedSchedule?.dailyWindows?.get(DayOfWeek.TUESDAY))
            .isEqualTo(TimeWindow(TimeOfDay(10, 0), TimeOfDay(11, 0)))
    }

    @Test
    fun profilesScreen_creatingProfileWhenExactAlarmsNotGranted_promptsForExactAlarmPermission() {
        var permissionRequested = false
        val savedProfiles = mutableListOf<CustomProfile>()

        composeTestRule.setContent {
            ThresholdTheme {
                ProfilesScreen(
                    profiles = emptyList(),
                    onSaveProfile = { savedProfiles.add(it) },
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                    canScheduleExactAlarms = { false },
                    onRequestExactAlarmPermission = { permissionRequested = true },
                )
            }
        }

        // Open new profile editor sheet and click Create
        composeTestRule.onNodeWithText("Add Profile").performClick()
        composeTestRule
            .onNodeWithText("Create")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(savedProfiles).hasSize(1)
        assertThat(savedProfiles.first().enabled).isTrue()

        // Exact alarm permission bottom sheet should be displayed
        composeTestRule.onNodeWithText("Allow exact schedule alarms").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Open Settings")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(permissionRequested).isTrue()
    }

    @Test
    fun profilesScreen_enablingExistingDisabledProfileWhenExactAlarmsNotGranted_promptsForExactAlarmPermission() {
        var permissionRequested = false
        val savedProfiles = mutableListOf<CustomProfile>()
        val disabledProfile = CustomProfile(
            id = "p1",
            name = "Evening Wind Down",
            priority = 0,
            enabled = false,
            colorIndex = 2,
        )

        composeTestRule.setContent {
            ThresholdTheme {
                ProfilesScreen(
                    profiles = listOf(disabledProfile),
                    onSaveProfile = { savedProfiles.add(it) },
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                    canScheduleExactAlarms = { false },
                    onRequestExactAlarmPermission = { permissionRequested = true },
                )
            }
        }

        // Toggle the existing disabled profile ON via its Switch
        composeTestRule.onNodeWithText("Evening Wind Down").assertIsDisplayed()
        composeTestRule
            .onAllNodesWithContentDescription("Delete Profile")[0] // verify card rendered
            .assertIsDisplayed()
        composeTestRule
            .onNode(androidx.compose.ui.test.isToggleable())
            .performClick()

        assertThat(savedProfiles).hasSize(1)
        assertThat(savedProfiles.first().enabled).isTrue()

        // Exact alarm permission bottom sheet should be displayed
        composeTestRule.onNodeWithText("Allow exact schedule alarms").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Open Settings")
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(permissionRequested).isTrue()
    }

    @Test
    fun profilesScreen_reorderAndDeleteCallbacksInvokedCorrectly() {
        val p1 = CustomProfile(id = "p1", name = "Work", priority = 0, colorIndex = 0)
        val p2 = CustomProfile(id = "p2", name = "Bedtime", priority = 1, colorIndex = 1)
        var reorderedList: List<CustomProfile>? = null
        var deletedId: String? = null

        composeTestRule.setContent {
            ThresholdTheme {
                ProfilesScreen(
                    profiles = listOf(p1, p2),
                    onSaveProfile = {},
                    onDeleteProfile = { deletedId = it },
                    onReorderProfiles = { reorderedList = it },
                    onConfigureProfileApps = {},
                    onBack = {},
                )
            }
        }

        // Schedule profile type icons are displayed on both profile cards
        assertThat(composeTestRule.onAllNodesWithContentDescription("Schedule profile").fetchSemanticsNodes())
            .hasSize(2)

        // Click "Move profile down" on the first profile card
        composeTestRule.onAllNodesWithContentDescription("Move profile down")[0].performClick()
        assertThat(reorderedList?.map { it.id }).containsExactly("p2", "p1").inOrder()

        // Click "Delete Profile" on the first profile card -> opens confirmation AlertDialog
        composeTestRule.onAllNodesWithContentDescription("Delete Profile")[0].performClick()
        composeTestRule.onNodeWithText("Delete profile?").assertIsDisplayed()
        assertThat(deletedId).isNull()

        // Clicking "Cancel" dismisses the dialog without deleting
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertThat(deletedId).isNull()

        // Click "Delete Profile" again and confirm "Delete"
        composeTestRule.onAllNodesWithContentDescription("Delete Profile")[0].performClick()
        composeTestRule.onNodeWithText("Delete").performClick()
        assertThat(deletedId).isEqualTo("p1")
    }
}

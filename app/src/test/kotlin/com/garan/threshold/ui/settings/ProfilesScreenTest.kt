package com.garan.threshold.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.GeoTriggerMode
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.NfcTagEntry
import com.garan.threshold.core.model.NfcTriggerMode
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.model.TimeWindow
import com.garan.threshold.core.model.TriggerRule
import com.garan.threshold.core.port.LocationTelemetry
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

        // Open new profile editor sheet, add a schedule trigger inline, and click Create using deterministic testTags
        composeTestRule.onNodeWithTag(ProfilesTestTags.ADD_PROFILE_BUTTON).performClick()
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_ADD_FIRST_TRIGGER)
            .performSemanticsAction(SemanticsActions.OnClick)
        // Create the inline schedule trigger inside TriggerEditorSheet
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.TRIGGER_EDITOR_SAVE_BUTTON)
            .performSemanticsAction(SemanticsActions.OnClick)
        // Create the profile inside ProfileRulesEditorSheet
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_SAVE_BUTTON)
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(savedProfiles).hasSize(1)
        assertThat(savedProfiles.first().enabled).isTrue()
        assertThat(savedProfiles.first().activationRules).hasSize(1)

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

        // Toggle the existing disabled profile ON via its Switch testTag
        composeTestRule.onNodeWithText("Evening Wind Down").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.profileDelete("p1"))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.profileToggle("p1"))
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

        // Click "Move profile down" on the first profile card via its keyed testTag
        composeTestRule.onNodeWithTag(ProfilesTestTags.profileMoveDown("p1")).performClick()
        assertThat(reorderedList?.map { it.id }).containsExactly("p2", "p1").inOrder()

        // Click "Delete Profile" on the first profile card -> opens confirmation AlertDialog
        composeTestRule.onNodeWithTag(ProfilesTestTags.profileDelete("p1")).performClick()
        composeTestRule.onNodeWithText("Delete profile?").assertIsDisplayed()
        assertThat(deletedId).isNull()

        // Clicking "Cancel" dismisses the dialog without deleting
        composeTestRule.onNodeWithTag(ProfilesTestTags.DELETE_CANCEL_BUTTON).performClick()
        assertThat(deletedId).isNull()

        // Click "Delete Profile" again and confirm "Delete"
        composeTestRule.onNodeWithTag(ProfilesTestTags.profileDelete("p1")).performClick()
        composeTestRule.onNodeWithTag(ProfilesTestTags.DELETE_CONFIRM_BUTTON).performClick()
        assertThat(deletedId).isEqualTo("p1")
    }

    @Test
    fun profilesScreen_displaysGeoProfileCardWithLocationIconAndLiveProximitySummary() {
        val center = LocationFix(51.5074, -0.1278)
        val nearby40m = LocationFix(51.5074 + (40.0 / 111_194.9266), -0.1278)
        val geoProfile = CustomProfile(
            id = "office",
            name = "Office",
            priority = 0,
            enabled = true,
            trigger = ProfileTrigger.Geo(
                GeoTrigger(
                    latitude = center.latitude,
                    longitude = center.longitude,
                    radiusMeters = 250,
                    mode = GeoTriggerMode.INSIDE,
                ),
            ),
        )

        val telemetry = LocationTelemetry(
            activeChecksLastHour = 2,
            activeChecksLast24h = 9,
            cacheHitsLastHour = 8,
            cacheHitsLast24h = 34,
            lastProviderUsed = "fused",
            lastFixLatencyMs = 95L,
        )

        composeTestRule.setContent {
            ThresholdTheme {
                ProfilesScreen(
                    profiles = listOf(geoProfile),
                    currentLocation = nearby40m,
                    locationTelemetry = telemetry,
                    onSaveProfile = {},
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                )
            }
        }

        // Verify Geo profile pin icon, live summary, "Location" action button, and telemetry footer
        assertThat(composeTestRule.onAllNodesWithContentDescription("Geo profile").fetchSemanticsNodes())
            .hasSize(1)
        composeTestRule.onNodeWithText("Inside 250m · Currently inside (40m away)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Location").assertIsDisplayed()
        composeTestRule.onNodeWithText("Location checks: 2/hr · 9/24h (34 cached) · fused (95ms)")
            .assertIsDisplayed()
    }

    @Test
    fun profilesScreen_creatingGeoProfileViaSegmentedTypeSelector_savesGeoTrigger() {
        val savedProfiles = mutableListOf<CustomProfile>()
        val savedTriggers = mutableListOf<SavedTrigger>()
        val mockedFix = LocationFix(51.5074, -0.1278)
        var locationPermissionEnsured = false

        composeTestRule.setContent {
            ThresholdTheme {
                ProfilesScreen(
                    profiles = emptyList(),
                    onSaveProfile = { savedProfiles.add(it) },
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                    onUseCurrentLocation = { callback -> callback(mockedFix) },
                    onEnsureLocationPermissionForProfile = { locationPermissionEnsured = true },
                    onSaveTrigger = { savedTriggers.add(it) },
                )
            }
        }

        // Open New Profile sheet and tap Add Trigger to create a Geo trigger inline
        composeTestRule.onNodeWithTag(ProfilesTestTags.ADD_PROFILE_BUTTON).performClick()
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_ADD_FIRST_TRIGGER)
            .performSemanticsAction(SemanticsActions.OnClick)

        // Switch from Schedule to Geo via the segmented type selector
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.TRIGGER_EDITOR_TYPE_GEO)
            .performSemanticsAction(SemanticsActions.OnClick)

        // Select "Outside zone" and "500m" radius preset
        composeTestRule
            .onNodeWithText("Outside zone")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule
            .onNodeWithText("500m")
            .performSemanticsAction(SemanticsActions.OnClick)

        // Tap "Use Current Location" to populate coordinates
        composeTestRule
            .onNodeWithText("Use Current Location")
            .performSemanticsAction(SemanticsActions.OnClick)

        // Tap "Create" in TriggerEditorSheet
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.TRIGGER_EDITOR_SAVE_BUTTON)
            .performSemanticsAction(SemanticsActions.OnClick)

        // Tap "Create" in ProfileRulesEditorSheet
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_SAVE_BUTTON)
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(savedTriggers).hasSize(1)
        val createdTrigger = savedTriggers.first()
        val geo = (createdTrigger.trigger as ProfileTrigger.Geo).geo
        assertThat(geo.latitude).isWithin(0.0001).of(51.5074)
        assertThat(geo.longitude).isWithin(0.0001).of(-0.1278)
        assertThat(geo.radiusMeters).isEqualTo(500)
        assertThat(geo.mode).isEqualTo(GeoTriggerMode.OUTSIDE)

        assertThat(savedProfiles).hasSize(1)
        val createdProfile = savedProfiles.first()
        assertThat(createdProfile.activationRules).containsExactly(TriggerRule(listOf(createdTrigger.id)))
        assertThat(locationPermissionEnsured).isTrue()
    }

    @Test
    fun profilesScreen_combinesMultipleTriggersWithAndOrRules() {
        val workHours = SavedTrigger(
            id = "t-work",
            name = "Work Hours",
            trigger = ProfileTrigger.Schedule(),
        )
        val atOffice = SavedTrigger(
            id = "t-office",
            name = "At Office",
            trigger = ProfileTrigger.Geo(GeoTrigger(latitude = 51.5, longitude = -0.1)),
        )
        val lateNight = SavedTrigger(
            id = "t-night",
            name = "Late Night",
            trigger = ProfileTrigger.Schedule(),
        )
        val savedProfiles = mutableListOf<CustomProfile>()

        composeTestRule.setContent {
            ThresholdTheme {
                ProfilesScreen(
                    profiles = emptyList(),
                    savedTriggers = listOf(workHours, atOffice, lateNight),
                    onSaveProfile = { savedProfiles.add(it) },
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                )
            }
        }

        // Open New Profile sheet
        composeTestRule.onNodeWithTag(ProfilesTestTags.ADD_PROFILE_BUTTON).performClick()

        // Tap "Add Trigger" and pick "Work Hours"
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_ADD_FIRST_TRIGGER)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.triggerPickerItem("t-work"))
            .performSemanticsAction(SemanticsActions.OnClick)

        // Tap "Add AND trigger" on rule 0 and pick "At Office"
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.profileRuleAddAnd(0))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.triggerPickerItem("t-office"))
            .performSemanticsAction(SemanticsActions.OnClick)

        // Tap "Add OR condition" and pick "Late Night"
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_ADD_OR_RULE)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.triggerPickerItem("t-night"))
            .performSemanticsAction(SemanticsActions.OnClick)

        // Save profile
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_SAVE_BUTTON)
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(savedProfiles).hasSize(1)
        assertThat(savedProfiles.single().activationRules).containsExactly(
            TriggerRule(listOf("t-work", "t-office")),
            TriggerRule(listOf("t-night")),
        ).inOrder()
    }

    @Test
    fun profilesScreen_editingExistingProfileRules_removesTriggerFromAndGroupAndRemovesOrGroupViaTestTags() {
        val workHours = SavedTrigger(
            id = "t-work",
            name = "Work Hours",
            trigger = ProfileTrigger.Schedule(),
        )
        val atOffice = SavedTrigger(
            id = "t-office",
            name = "At Office",
            trigger = ProfileTrigger.Geo(GeoTrigger(latitude = 51.5, longitude = -0.1)),
        )
        val lateNight = SavedTrigger(
            id = "t-night",
            name = "Late Night",
            trigger = ProfileTrigger.Schedule(),
        )
        val focusProfile = CustomProfile(
            id = "p-focus",
            name = "Focus",
            enabled = true,
            priority = 0,
            trigger = null,
            activationRules = listOf(
                TriggerRule(listOf("t-work", "t-office")),
                TriggerRule(listOf("t-night")),
            ),
        )
        val savedProfiles = mutableListOf<CustomProfile>()

        composeTestRule.setContent {
            ThresholdTheme {
                ProfilesScreen(
                    profiles = listOf(focusProfile),
                    savedTriggers = listOf(workHours, atOffice, lateNight),
                    onSaveProfile = { savedProfiles.add(it) },
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                )
            }
        }

        // Verify card summary shows "Work Hours + At Office OR Late Night"
        composeTestRule.onNodeWithText("Work Hours + At Office OR Late Night").assertIsDisplayed()

        // Open Edit Profile sheet via the profile's edit rules button testTag
        composeTestRule.onNodeWithTag(ProfilesTestTags.profileEditRules("p-focus")).performClick()

        // Remove "At Office" from Rule 0 (leaving "Work Hours" in Rule 0)
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.profileRuleRemoveTrigger(0, "t-office"))
            .performSemanticsAction(SemanticsActions.OnClick)

        // Remove "Late Night" from Rule 1 (which empties and removes Rule 1 completely)
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.profileRuleRemoveTrigger(1, "t-night"))
            .performSemanticsAction(SemanticsActions.OnClick)

        // Save updated profile rules
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_SAVE_BUTTON)
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(savedProfiles).hasSize(1)
        val updated = savedProfiles.single()
        assertThat(updated.enabled).isTrue()
        assertThat(updated.activationRules).containsExactly(
            TriggerRule(listOf("t-work")),
        )
    }

    @Test
    fun profilesScreen_triggersTab_deletesTriggerWithWarningAndInvokesDeleteCallback() {
        val workHours = SavedTrigger(
            id = "t-work",
            name = "Work Hours",
            trigger = ProfileTrigger.Schedule(),
        )
        val deepWork = CustomProfile(
            id = "p-work",
            name = "Deep Work",
            enabled = true,
            priority = 0,
            activationRules = listOf(TriggerRule(listOf("t-work"))),
            trigger = null,
        )
        var deletedTriggerId: String? = null

        composeTestRule.setContent {
            ThresholdTheme {
                ProfilesScreen(
                    profiles = listOf(deepWork),
                    savedTriggers = listOf(workHours),
                    onSaveProfile = {},
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                    onDeleteTrigger = { deletedTriggerId = it },
                )
            }
        }

        // Switch to "Triggers" tab in the top segmented button row via testTag
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.TAB_TRIGGERS)
            .performSemanticsAction(SemanticsActions.OnClick)

        // Verify trigger card shows usage by "Deep Work"
        composeTestRule.onNodeWithTag(ProfilesTestTags.triggerCard("t-work")).assertIsDisplayed()
        composeTestRule.onNodeWithText("Work Hours").assertIsDisplayed()
        composeTestRule.onNodeWithText("Used by: Deep Work").assertIsDisplayed()

        // Click Delete Trigger via keyed testTag -> shows warning naming "Deep Work"
        composeTestRule.onNodeWithTag(ProfilesTestTags.triggerDelete("t-work")).performClick()
        composeTestRule.onNodeWithText("Delete trigger?").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "“Work Hours” is used by “Deep Work”. Deleting it will remove it from those profiles " +
                "and disable any profile left with no triggers.",
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag(ProfilesTestTags.DELETE_CONFIRM_BUTTON).performClick()
        assertThat(deletedTriggerId).isEqualTo("t-work")
    }

    @Test
    fun profilesScreen_creatingNfcProfileViaSegmentedTypeSelector_scansTagSelectsTimedSessionAndSaves() {
        val savedProfiles = mutableListOf<CustomProfile>()
        val savedTriggers = mutableListOf<SavedTrigger>()
        var activeScanListener: ((String) -> Unit)? = null

        composeTestRule.setContent {
            ThresholdTheme {
                ProfilesScreen(
                    profiles = emptyList(),
                    onSaveProfile = { savedProfiles.add(it) },
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                    onSaveTrigger = { savedTriggers.add(it) },
                    onRegisterNfcScanListener = { listener -> activeScanListener = listener },
                )
            }
        }

        // Open New Profile sheet and tap Add Trigger to create an NFC trigger inline
        composeTestRule.onNodeWithTag(ProfilesTestTags.ADD_PROFILE_BUTTON).performClick()
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_ADD_FIRST_TRIGGER)
            .performSemanticsAction(SemanticsActions.OnClick)

        // Switch from Schedule to NFC via the segmented type selector
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.TRIGGER_EDITOR_TYPE_NFC)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Listener should be registered while NFC editor is open
        assertThat(activeScanListener).isNotNull()

        // Simulate scanning two NFC tags
        composeTestRule.runOnIdle {
            activeScanListener?.invoke("04a23f8b1c9080")
            activeScanListener?.invoke("04:11:22:33")
        }
        composeTestRule.waitForIdle()

        // Label the first tag and remove the second tag
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.triggerEditorNfcTagLabel("04:A2:3F:8B:1C:90:80"))
            .performTextInput("Bedside")
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.triggerEditorNfcTagRemove("04:11:22:33"))
            .performSemanticsAction(SemanticsActions.OnClick)

        // Select "Timed session" mode and "2h" duration preset (120 minutes)
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.TRIGGER_EDITOR_NFC_MODE_TIMED)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.triggerEditorNfcDuration(120))
            .performSemanticsAction(SemanticsActions.OnClick)

        // Tap "Create" in TriggerEditorSheet
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.TRIGGER_EDITOR_SAVE_BUTTON)
            .performSemanticsAction(SemanticsActions.OnClick)

        // Tap "Create" in ProfileRulesEditorSheet
        composeTestRule
            .onNodeWithTag(ProfilesTestTags.PROFILE_EDITOR_SAVE_BUTTON)
            .performSemanticsAction(SemanticsActions.OnClick)

        assertThat(savedTriggers).hasSize(1)
        val createdTrigger = savedTriggers.single()
        val nfc = (createdTrigger.trigger as ProfileTrigger.Nfc).nfc
        assertThat(nfc.mode).isEqualTo(NfcTriggerMode.TIMED_SESSION)
        assertThat(nfc.durationMinutes).isEqualTo(120)
        assertThat(nfc.tags).containsExactly(
            NfcTagEntry(uid = "04:A2:3F:8B:1C:90:80", label = "Bedside"),
        )

        assertThat(savedProfiles).hasSize(1)
        assertThat(savedProfiles.single().activationRules).containsExactly(
            TriggerRule(listOf(createdTrigger.id)),
        )
        assertThat(activeScanListener).isNull()
    }
}

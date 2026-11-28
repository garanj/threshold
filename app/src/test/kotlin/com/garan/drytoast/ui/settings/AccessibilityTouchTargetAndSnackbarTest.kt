package com.garan.drytoast.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.garan.drytoast.core.model.AppAssignment
import com.garan.drytoast.core.model.ComponentKey
import com.garan.drytoast.core.model.CustomProfile
import com.garan.drytoast.core.model.DayOfWeek
import com.garan.drytoast.core.model.FrictionChoice
import com.garan.drytoast.core.model.InstalledApp
import com.garan.drytoast.core.model.ProfileTrigger
import com.garan.drytoast.core.model.SavedTrigger
import com.garan.drytoast.core.model.ScheduleTrigger
import com.garan.drytoast.core.model.TimeOfDay
import com.garan.drytoast.core.model.TriggerRule
import com.garan.drytoast.ui.theme.DryToastTheme
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
class AccessibilityTouchTargetAndSnackbarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleApps = persistentListOf(
        InstalledApp(ComponentKey("com.android.camera", "CameraActivity"), "Camera"),
        InstalledApp(ComponentKey("com.google.android.apps.maps", "MapsActivity"), "Google Maps"),
        InstalledApp(ComponentKey("com.instagram.android", "MainActivity"), "Instagram"),
    )

    private val sampleTriggers = listOf(
        SavedTrigger(
            id = "t-work",
            name = "Workday Focus",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
                    startTime = TimeOfDay(9, 0),
                    endTime = TimeOfDay(17, 0),
                ),
            ),
        ),
        SavedTrigger(
            id = "t-rest",
            name = "Evening Rest",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                    startTime = TimeOfDay(21, 0),
                    endTime = TimeOfDay(23, 0),
                ),
            ),
        ),
    )

    private val sampleProfiles = listOf(
        CustomProfile(
            id = "p-work",
            name = "Deep Work",
            priority = 0,
            enabled = true,
            colorIndex = 0,
            activationRules = listOf(TriggerRule(listOf("t-work"))),
        ),
        CustomProfile(
            id = "p-rest",
            name = "Evening Rest",
            priority = 1,
            enabled = true,
            colorIndex = 2,
            activationRules = listOf(TriggerRule(listOf("t-rest"))),
        ),
    )

    @Test
    fun profilesScreen_allInteractiveNodesMeet48dpMinimumTouchTargetWithoutOverlap() {
        composeRule.setContent {
            DryToastTheme {
                ProfilesScreen(
                    profiles = sampleProfiles,
                    savedTriggers = sampleTriggers,
                    onSaveProfile = {},
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()

        assertAllInteractiveNodesAtLeast48dp()

        // Specifically verify ProfileCard reorder buttons have non-overlapping >= 48dp touch bounds
        val moveUpNode = composeRule.onNodeWithTag(ProfilesTestTags.profileMoveUp("p-work")).fetchSemanticsNode()
        val moveDownNode = composeRule.onNodeWithTag(ProfilesTestTags.profileMoveDown("p-work")).fetchSemanticsNode()
        assertThat(moveUpNode.touchBoundsInRoot.width).isAtLeast(47.5f)
        assertThat(moveUpNode.touchBoundsInRoot.height).isAtLeast(47.5f)
        assertThat(moveDownNode.touchBoundsInRoot.width).isAtLeast(47.5f)
        assertThat(moveDownNode.touchBoundsInRoot.height).isAtLeast(47.5f)
        assertThat(moveUpNode.touchBoundsInRoot.bottom).isAtMost(moveDownNode.touchBoundsInRoot.top + 0.5f)

        // Switch to Triggers tab and audit all interactive nodes there too
        composeRule.onNodeWithTag(ProfilesTestTags.TAB_TRIGGERS)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertAllInteractiveNodesAtLeast48dp()
    }

    @Test
    fun configureAppsScreen_baseProfile_allInteractiveNodesMeet48dpMinimumTouchTarget() {
        val assignments = mapOf(
            sampleApps[1].component to AppAssignment.Guarded(),
            sampleApps[2].component to AppAssignment.Blocked,
        )

        composeRule.setContent {
            DryToastTheme {
                ConfigureAppsScreen(
                    availableApps = sampleApps,
                    initialAssignments = assignments,
                    defaultFriction = FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                    isCustomProfile = false,
                )
            }
        }
        composeRule.waitForIdle()
        assertAllInteractiveNodesAtLeast48dp()
    }

    @Test
    fun configureAppsScreen_customProfile_allInteractiveNodesMeet48dpMinimumTouchTarget() {
        val assignments = mapOf(
            sampleApps[1].component to AppAssignment.Guarded(),
            sampleApps[2].component to AppAssignment.Blocked,
        )

        composeRule.setContent {
            DryToastTheme {
                ConfigureAppsScreen(
                    availableApps = sampleApps,
                    initialAssignments = assignments,
                    defaultFriction = FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                    isCustomProfile = true,
                    customProfileName = "Deep Work",
                )
            }
        }
        composeRule.waitForIdle()
        assertAllInteractiveNodesAtLeast48dp()
    }

    @Test
    fun narrowFoldableCover_360x800_atFontScale1_5_hasZeroControlTruncationOrTouchOverlap() {
        val assignments = mapOf(
            sampleApps[1].component to AppAssignment.Guarded(),
            sampleApps[2].component to AppAssignment.Blocked,
        )

        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f),
            ) {
                DryToastTheme {
                    ConfigureAppsScreen(
                        availableApps = sampleApps,
                        initialAssignments = assignments,
                        defaultFriction = FrictionChoice.TYPED_INTENTION,
                        onSave = {},
                        onBack = {},
                        isCustomProfile = false,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertAllInteractiveNodesAtLeast48dp()
        assertNoHorizontalTruncationForTexts(listOf("Camera", "Google Maps", "Instagram", "ALLOW", "GUARD", "BLOCK"))
    }

    @Test
    fun narrowFoldableCover_profiles_360x800_atFontScale1_5_hasZeroActionButtonTruncation() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f),
            ) {
                DryToastTheme {
                    ProfilesScreen(
                        profiles = sampleProfiles,
                        savedTriggers = sampleTriggers,
                        onSaveProfile = {},
                        onDeleteProfile = {},
                        onReorderProfiles = {},
                        onConfigureProfileApps = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertAllInteractiveNodesAtLeast48dp()
        assertNoHorizontalTruncationForTexts(listOf("Schedule", "Configure Apps"))
    }

    @Test
    fun settingsScreen_applyBlackWallpaper_routesThroughSnackbarHostInsteadOfSystemToast() {
        ShadowToast.reset()
        var wallpaperInvoked = false

        composeRule.setContent {
            DryToastTheme {
                DryToastSettingsScreen(
                    uiState = SettingsUiState(isLoading = false),
                    onBack = {},
                    onNavigateToConfigureApps = {},
                    onUpdateShowUnlocks = {},
                    onUpdateShowRest = {},
                    onUpdateRequireFullTyping = {},
                    onUpdateDefaultFriction = {},
                    onAddQuickAction = {},
                    onRemoveQuickAction = {},
                    onApplyPureBlackWallpaper = { wallpaperInvoked = true },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST)
            .performScrollToNode(hasTestTag(SettingsTestTags.SET_BLACK_WALLPAPER_ACTION))
        composeRule.onNodeWithTag(SettingsTestTags.SET_BLACK_WALLPAPER_ACTION).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SettingsTestTags.CONFIRM_BLACK_WALLPAPER_BUTTON).performClick()
        composeRule.waitForIdle()

        assertThat(wallpaperInvoked).isTrue()
        composeRule.onNodeWithText("Pure black wallpaper applied").assertIsDisplayed()
        assertThat(ShadowToast.getLatestToast()).isNull()
    }

    @Test
    fun settingsScreen_copyConsoleLink_routesThroughSnackbarHostInsteadOfSystemToast() {
        ShadowToast.reset()

        composeRule.setContent {
            DryToastTheme {
                DryToastSettingsScreen(
                    uiState = SettingsUiState(isLoading = false),
                    onBack = {},
                    onNavigateToConfigureApps = {},
                    onUpdateShowUnlocks = {},
                    onUpdateShowRest = {},
                    onUpdateRequireFullTyping = {},
                    onUpdateDefaultFriction = {},
                    onAddQuickAction = {},
                    onRemoveQuickAction = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST)
            .performScrollToNode(hasTestTag(SettingsTestTags.CONSOLE_LINK_ROW))
        composeRule.onNodeWithTag(SettingsTestTags.CONSOLE_LINK_ROW).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SecondDeviceSheetTestTags.COPY_BUTTON)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Link copied").assertIsDisplayed()
        assertThat(ShadowToast.getLatestToast()).isNull()
    }

    private fun assertAllInteractiveNodesAtLeast48dp() {
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val interactiveNodes = mutableListOf<SemanticsNode>()
        fun collect(node: SemanticsNode) {
            if (SemanticsActions.OnClick in node.config || SemanticsActions.OnLongClick in node.config) {
                interactiveNodes += node
            }
            node.children.forEach(::collect)
        }
        collect(root)

        assertThat(interactiveNodes).isNotEmpty()
        for (node in interactiveNodes) {
            val touch = node.touchBoundsInRoot
            val bounds = node.boundsInRoot
            // Ignore off-screen scrolled nodes clipped to 0 height/width
            if (bounds.width <= 0.5f || bounds.height <= 0.5f) continue
            val desc = node.config.toString()
            assertWithMessage("Expected touchBoundsInRoot.width >= 48dp for node $desc (bounds=$bounds, touch=$touch)")
                .that(touch.width)
                .isAtLeast(47.5f)
            assertWithMessage("Expected touchBoundsInRoot.height >= 48dp for node $desc (bounds=$bounds, touch=$touch)")
                .that(touch.height)
                .isAtLeast(47.5f)
        }
    }

    private fun assertNoHorizontalTruncationForTexts(expectedSubstrings: List<String>) {
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val allNodes = mutableListOf<SemanticsNode>()
        fun collect(node: SemanticsNode) {
            allNodes += node
            node.children.forEach(::collect)
        }
        collect(root)

        for (expected in expectedSubstrings) {
            val matching = allNodes.filter { node ->
                val texts = node.config.getOrNull(SemanticsProperties.Text)
                texts?.any { it.text == expected } == true
            }
            assertWithMessage("Expected to find text node '$expected'").that(matching).isNotEmpty()
            for (node in matching) {
                val results = mutableListOf<TextLayoutResult>()
                val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
                action?.invoke(results)
                for (layoutResult in results) {
                    val renderedCharCount = layoutResult.getLineEnd(layoutResult.lineCount - 1, visibleEnd = true)
                    assertWithMessage("Expected all ${expected.length} chars rendered without clipping on '$expected'")
                        .that(renderedCharCount)
                        .isEqualTo(expected.length)
                    for (line in 0 until layoutResult.lineCount) {
                        assertWithMessage("Expected line $line not ellipsized on '$expected'")
                            .that(layoutResult.isLineEllipsized(line))
                            .isFalse()
                    }
                }
            }
        }
    }
}

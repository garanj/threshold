package com.garan.drytoast.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.garan.drytoast.DryToastRoute
import com.garan.drytoast.core.model.AppAssignment
import com.garan.drytoast.core.model.BaseProfileSettings
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
import com.garan.drytoast.ui.motion.DryToastMotion
import com.garan.drytoast.ui.theme.DryToastTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveSettingsLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleApps = persistentListOf(
        InstalledApp(ComponentKey("com.android.camera", "CameraActivity"), "Camera"),
        InstalledApp(ComponentKey("com.android.chrome", "Main"), "Chrome"),
    )

    private val sampleTriggers = persistentListOf(
        SavedTrigger(
            id = "t-morning",
            name = "Morning Focus",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
                    startTime = TimeOfDay(8, 0),
                    endTime = TimeOfDay(12, 0),
                ),
            ),
        ),
        SavedTrigger(
            id = "t-evening",
            name = "Evening Wind-Down",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
                    startTime = TimeOfDay(21, 0),
                    endTime = TimeOfDay(23, 0),
                ),
            ),
        ),
    )

    private val sampleProfiles = persistentListOf(
        CustomProfile(
            id = "p-work",
            name = "Deep Work",
            priority = 0,
            enabled = true,
            activationRules = listOf(TriggerRule(listOf("t-morning"))),
            colorIndex = 0,
        ),
        CustomProfile(
            id = "p-sleep",
            name = "Evening Rest",
            priority = 1,
            enabled = true,
            activationRules = listOf(TriggerRule(listOf("t-evening"))),
            colorIndex = 2,
        ),
    )

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-mdpi")
    fun compactViewport_usesSinglePanePushAndPopNavigation() {
        lateinit var backStackRef: MutableList<DryToastRoute>

        composeRule.setContent {
            val backStack = remember {
                mutableStateListOf<DryToastRoute>(DryToastRoute.Home, DryToastRoute.Settings)
            }
            backStackRef = backStack
            DryToastTheme {
                TestAdaptiveSettingsNavHost(backStack = backStack)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AdaptiveSettingsTestTags.TWO_PANE_ROOT).assertDoesNotExist()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST).assertIsDisplayed()

        composeRule.onNodeWithTag(SettingsTestTags.NAV_CONFIGURE_APPS).performClick()
        composeRule.waitForIdle()
        assertThat(backStackRef.toList()).containsExactly(
            DryToastRoute.Home,
            DryToastRoute.Settings,
            DryToastRoute.ConfigureApps(profileId = null),
        ).inOrder()
        composeRule.onNodeWithTag("configure_apps_list").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w673dp-h841dp-mdpi")
    fun unfoldedFoldableViewport_673x841_keepsSinglePaneNavigation() {
        composeRule.setContent {
            val backStack = remember {
                mutableStateListOf<DryToastRoute>(
                    DryToastRoute.Home,
                    DryToastRoute.Settings,
                    DryToastRoute.ConfigureApps(profileId = null),
                )
            }
            DryToastTheme {
                TestAdaptiveSettingsNavHost(backStack = backStack)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AdaptiveSettingsTestTags.TWO_PANE_ROOT).assertDoesNotExist()
        composeRule.onNodeWithTag("configure_apps_list").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w768dp-h1024dp-mdpi")
    fun mediumViewport_keepsSinglePaneAndCentersConfigurationContentWithin720dp() {
        composeRule.setContent {
            val backStack = remember {
                mutableStateListOf<DryToastRoute>(
                    DryToastRoute.Home,
                    DryToastRoute.Settings,
                    DryToastRoute.ConfigureApps(profileId = null),
                )
            }
            DryToastTheme {
                TestAdaptiveSettingsNavHost(backStack = backStack)
            }
        }
        composeRule.waitForIdle()

        // 768dp is MEDIUM (600-839dp): must stay single-pane and constrain list width to <= 720dp centered
        composeRule.onNodeWithTag(AdaptiveSettingsTestTags.TWO_PANE_ROOT).assertDoesNotExist()
        val listBounds = composeRule.onNodeWithTag("configure_apps_list")
            .fetchSemanticsNode()
            .boundsInRoot
        assertThat(listBounds.width).isAtMost(720.5f)
        assertThat(abs(listBounds.center.x - 384f)).isAtMost(2f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
    fun expandedTabletViewport_rendersTwoPaneMasterDetail_switchesWithoutStackDuplication_andHandlesBack() {
        lateinit var backStackRef: MutableList<DryToastRoute>

        composeRule.setContent {
            val backStack = remember {
                mutableStateListOf<DryToastRoute>(DryToastRoute.Home, DryToastRoute.Settings)
            }
            backStackRef = backStack
            DryToastTheme {
                TestAdaptiveSettingsNavHost(backStack = backStack)
            }
        }
        composeRule.waitForIdle()

        // 1. Both left Settings master rail and right default ConfigureApps detail pane render simultaneously
        composeRule.onNodeWithTag(AdaptiveSettingsTestTags.TWO_PANE_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(AdaptiveSettingsTestTags.MASTER_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(AdaptiveSettingsTestTags.HAIRLINE_DIVIDER).assertIsDisplayed()
        composeRule.onNodeWithTag(AdaptiveSettingsTestTags.DETAIL_PANE).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag("configure_apps_list").assertIsDisplayed()

        // 2. Switch across Profiles -> Hardened Mode -> Break-Glass -> Profiles without back-stack duplication
        composeRule.onNodeWithTag(SettingsTestTags.NAV_PROFILES).performClick()
        composeRule.waitForIdle()
        assertThat(backStackRef.toList()).containsExactly(
            DryToastRoute.Home,
            DryToastRoute.Settings,
            DryToastRoute.Profiles,
        ).inOrder()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Deep Work").assertIsDisplayed()

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST)
            .performScrollToNode(hasTestTag(SettingsTestTags.NAV_HARDENED_MODE))
        composeRule.onNodeWithTag(SettingsTestTags.NAV_HARDENED_MODE).performClick()
        composeRule.waitForIdle()
        assertThat(backStackRef.toList()).containsExactly(
            DryToastRoute.Home,
            DryToastRoute.Settings,
            DryToastRoute.HardenedMode,
        ).inOrder()
        composeRule.onNodeWithTag(HardenedModeTestTags.SCREEN_LIST).assertIsDisplayed()

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST)
            .performScrollToNode(hasTestTag(SettingsTestTags.NAV_BREAK_GLASS))
        composeRule.onNodeWithTag(SettingsTestTags.NAV_BREAK_GLASS).performClick()
        composeRule.waitForIdle()
        assertThat(backStackRef.toList()).containsExactly(
            DryToastRoute.Home,
            DryToastRoute.Settings,
            DryToastRoute.BreakGlass,
        ).inOrder()
        composeRule.onNodeWithTag(BreakGlassTestTags.SCREEN).assertIsDisplayed()

        // 3. Switch back to Profiles, drill down into profile-specific ConfigureApps, then step Back to Home
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_LIST)
            .performScrollToNode(hasTestTag(SettingsTestTags.NAV_PROFILES))
        composeRule.onNodeWithTag(SettingsTestTags.NAV_PROFILES).performClick()
        composeRule.waitForIdle()
        assertThat(backStackRef).hasSize(3)

        composeRule.onNodeWithTag(ProfilesTestTags.profileConfigureApps("p-work")).performClick()
        composeRule.waitForIdle()
        assertThat(backStackRef.toList()).containsExactly(
            DryToastRoute.Home,
            DryToastRoute.Settings,
            DryToastRoute.Profiles,
            DryToastRoute.ConfigureApps(profileId = "p-work"),
        ).inOrder()
        composeRule.onNodeWithText("Deep Work").assertIsDisplayed()

        // Back 1: ConfigureApps("p-work") -> Profiles
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertThat(backStackRef.last()).isEqualTo(DryToastRoute.Profiles)

        // Back 2: Profiles -> Settings (default ConfigureApps)
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertThat(backStackRef.last()).isEqualTo(DryToastRoute.Settings)

        // Back 3: Settings -> Home
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertThat(backStackRef.toList()).containsExactly(DryToastRoute.Home)
        composeRule.onNodeWithTag("stub_home").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
    fun profilesScreen_rendersTwoColumnStaggeredGridWhenPaneWidthAtLeast720dp() {
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

        composeRule.onNodeWithTag(ProfilesTestTags.PROFILES_GRID).assertIsDisplayed()
        val firstProfileBounds = composeRule.onNodeWithTag(ProfilesTestTags.profileCard("p-work"))
            .fetchSemanticsNode()
            .boundsInRoot
        val secondProfileBounds = composeRule.onNodeWithTag(ProfilesTestTags.profileCard("p-sleep"))
            .fetchSemanticsNode()
            .boundsInRoot

        // In a 2-column grid, the first two cards sit side-by-side in separate columns at the same top y
        assertThat(abs(firstProfileBounds.top - secondProfileBounds.top)).isAtMost(2f)
        assertThat(secondProfileBounds.left).isGreaterThan(firstProfileBounds.right)

        // Switch to Triggers tab and verify 2-column grid for TriggerCards too
        composeRule.onNodeWithTag(ProfilesTestTags.TAB_TRIGGERS)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ProfilesTestTags.TRIGGERS_GRID).assertIsDisplayed()
        val firstTriggerBounds = composeRule.onNodeWithTag(ProfilesTestTags.triggerCard("t-morning"))
            .fetchSemanticsNode()
            .boundsInRoot
        val secondTriggerBounds = composeRule.onNodeWithTag(ProfilesTestTags.triggerCard("t-evening"))
            .fetchSemanticsNode()
            .boundsInRoot
        assertThat(abs(firstTriggerBounds.top - secondTriggerBounds.top)).isAtMost(2f)
        assertThat(secondTriggerBounds.left).isGreaterThan(firstTriggerBounds.right)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun TestAdaptiveSettingsNavHost(backStack: MutableList<DryToastRoute>) {
        val isExpanded = LocalConfiguration.current.screenWidthDp.dp >= SETTINGS_EXPANDED_BREAKPOINT
        val uiState = SettingsUiState(
            availableApps = sampleApps,
            baseProfile = BaseProfileSettings(
                appAssignments = mapOf(sampleApps[1].component to AppAssignment.Guarded()),
            ),
            customProfiles = sampleProfiles,
            savedTriggers = sampleTriggers,
            isLoading = false,
        )
        val twoPaneStrategy = rememberSettingsTwoPaneSceneStrategy<DryToastRoute>(
            isExpanded = isExpanded,
        ) {
            ConfigureAppsScreen(
                availableApps = uiState.availableApps,
                initialAssignments = uiState.baseProfile.appAssignments,
                defaultFriction = FrictionChoice.TYPED_INTENTION,
                onSave = {},
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            )
        }
        val sceneStrategies = remember(twoPaneStrategy) {
            listOf(twoPaneStrategy, SinglePaneSceneStrategy())
        }

        NavDisplay(
            backStack = backStack,
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            sceneStrategies = sceneStrategies,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            transitionSpec = {
                fadeIn(DryToastMotion.opticalTween(DryToastMotion.SHUTTER_OPEN_DURATION_MS)) togetherWith
                    fadeOut(DryToastMotion.opticalTween(DryToastMotion.SHUTTER_CLOSE_DURATION_MS))
            },
            popTransitionSpec = {
                fadeIn(DryToastMotion.opticalTween(DryToastMotion.SHUTTER_OPEN_DURATION_MS)) togetherWith
                    fadeOut(DryToastMotion.opticalTween(DryToastMotion.SHUTTER_CLOSE_DURATION_MS))
            },
            entryProvider = entryProvider {
                entry<DryToastRoute.Home> {
                    Box(modifier = Modifier.fillMaxSize().testTag("stub_home")) {
                        Text("Home")
                    }
                }
                entry<DryToastRoute.Settings>(metadata = SettingsTwoPaneSceneStrategy.masterMetadata()) {
                    DryToastSettingsScreen(
                        uiState = uiState,
                        onBack = { exitSettingsFromMaster(backStack) },
                        onNavigateToConfigureApps = {
                            navigateFromSettingsMaster(
                                backStack = backStack,
                                target = DryToastRoute.ConfigureApps(profileId = null),
                                isExpanded = isExpanded,
                            )
                        },
                        onNavigateToProfiles = {
                            navigateFromSettingsMaster(
                                backStack = backStack,
                                target = DryToastRoute.Profiles,
                                isExpanded = isExpanded,
                            )
                        },
                        onNavigateToHardenedMode = {
                            navigateFromSettingsMaster(
                                backStack = backStack,
                                target = DryToastRoute.HardenedMode,
                                isExpanded = isExpanded,
                            )
                        },
                        onNavigateToBreakGlass = {
                            navigateFromSettingsMaster(
                                backStack = backStack,
                                target = DryToastRoute.BreakGlass,
                                isExpanded = isExpanded,
                            )
                        },
                        onUpdateShowUnlocks = {},
                        onUpdateShowRest = {},
                        onUpdateRequireFullTyping = {},
                        onUpdateDefaultFriction = {},
                        onAddQuickAction = {},
                        onRemoveQuickAction = {},
                    )
                }
                entry<DryToastRoute.ConfigureApps>(
                    metadata = { route ->
                        SettingsTwoPaneSceneStrategy.detailMetadata(
                            section = SettingsDetailSection.CONFIGURE_APPS,
                            isSubDetail = route.profileId != null,
                        )
                    },
                ) { route ->
                    val profile = uiState.customProfiles.firstOrNull { it.id == route.profileId }
                    ConfigureAppsScreen(
                        availableApps = uiState.availableApps,
                        initialAssignments = profile?.appAssignments ?: uiState.baseProfile.appAssignments,
                        defaultFriction = FrictionChoice.TYPED_INTENTION,
                        onSave = {},
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        isCustomProfile = profile != null,
                        customProfileName = profile?.name,
                    )
                }
                entry<DryToastRoute.Profiles>(
                    metadata = SettingsTwoPaneSceneStrategy.detailMetadata(SettingsDetailSection.PROFILES),
                ) {
                    ProfilesScreen(
                        profiles = uiState.customProfiles,
                        savedTriggers = uiState.savedTriggers,
                        onSaveProfile = {},
                        onDeleteProfile = {},
                        onReorderProfiles = {},
                        onConfigureProfileApps = { id ->
                            backStack.add(DryToastRoute.ConfigureApps(profileId = id))
                        },
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    )
                }
                entry<DryToastRoute.HardenedMode>(
                    metadata = SettingsTwoPaneSceneStrategy.detailMetadata(SettingsDetailSection.HARDENED_MODE),
                ) {
                    HardenedModeScreen(
                        isHardenedModeActive = false,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    )
                }
                entry<DryToastRoute.BreakGlass>(
                    metadata = SettingsTwoPaneSceneStrategy.detailMetadata(SettingsDetailSection.BREAK_GLASS),
                ) {
                    BreakGlassScreen(
                        breakGlassTagUid = null,
                        breakGlassDurationMinutes = 15,
                        breakGlassUntilEpochMillis = null,
                        savedTriggers = emptyList(),
                        isSettingsLocked = false,
                        onSaveBreakGlassTagUid = {},
                        onUpdateDurationMinutes = {},
                        onEndBreakGlassEarly = {},
                        onSetNfcRegistrationListener = {},
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}

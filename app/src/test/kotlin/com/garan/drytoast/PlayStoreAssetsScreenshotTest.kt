package com.garan.drytoast

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.garan.drytoast.core.model.AppAccessState
import com.garan.drytoast.core.model.AppAssignment
import com.garan.drytoast.core.model.BaseProfileSettings
import com.garan.drytoast.core.model.ComponentKey
import com.garan.drytoast.core.model.CustomProfile
import com.garan.drytoast.core.model.DayOfWeek
import com.garan.drytoast.core.model.FrictionChoice
import com.garan.drytoast.core.model.FrictionPolicy
import com.garan.drytoast.core.model.GeneralSettings
import com.garan.drytoast.core.model.InstalledApp
import com.garan.drytoast.core.model.ProfileDefaultRule
import com.garan.drytoast.core.model.ProfileTrigger
import com.garan.drytoast.core.model.SavedTrigger
import com.garan.drytoast.core.model.ScheduleTrigger
import com.garan.drytoast.core.model.TimeOfDay
import com.garan.drytoast.core.model.TriggerRule
import com.garan.drytoast.core.search.MatchType
import com.garan.drytoast.core.search.SearchItem
import com.garan.drytoast.core.search.SearchResult
import com.garan.drytoast.ui.home.DryToastHomeCanvas
import com.garan.drytoast.ui.search.DryToastSearchSurface
import com.garan.drytoast.ui.search.QuickActionUiItem
import com.garan.drytoast.ui.settings.ConfigureAppsScreen
import com.garan.drytoast.ui.settings.DryToastSettingsScreen
import com.garan.drytoast.ui.settings.ProfilesScreen
import com.garan.drytoast.ui.settings.SettingsDetailSection
import com.garan.drytoast.ui.settings.SettingsTwoPaneSceneStrategy
import com.garan.drytoast.ui.settings.SettingsUiState
import com.garan.drytoast.ui.settings.rememberSettingsTwoPaneSceneStrategy
import com.garan.drytoast.ui.theme.DryToastTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Generates and verifies high-resolution Google Play Console store listing screenshots for:
 * - **7-inch Tablet:** `1920 × 1200` px (`w960dp-h600dp-xhdpi`)
 * - **10-inch Tablet:** `2560 × 1600` px (`w1280dp-h800dp-xhdpi`)
 * - **Unfolded Foldable:** `2016 × 2520` px (`w672dp-h840dp-xxhdpi`)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayStoreAssetsScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val cameraKey = ComponentKey("com.android.camera", "CameraActivity")
    private val chromeKey = ComponentKey("com.android.chrome", "Main")
    private val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
    private val gmailKey = ComponentKey("com.google.android.gm", "ConversationListActivityGmail")
    private val mapsKey = ComponentKey("com.google.android.apps.maps", "MapsActivity")
    private val socialKey = ComponentKey("com.instagram.android", "MainActivity")
    private val videoKey = ComponentKey("com.google.android.youtube", "HomeActivity")

    private val sampleApps = persistentListOf(
        InstalledApp(cameraKey, "Camera"),
        InstalledApp(chromeKey, "Chrome"),
        InstalledApp(clockKey, "Clock"),
        InstalledApp(gmailKey, "Gmail"),
        InstalledApp(socialKey, "Instagram"),
        InstalledApp(mapsKey, "Maps"),
        InstalledApp(videoKey, "YouTube"),
    )

    private val baseAssignments = mapOf(
        chromeKey to AppAssignment.Guarded(),
        gmailKey to AppAssignment.Guarded(),
        socialKey to AppAssignment.Blocked,
        videoKey to AppAssignment.Blocked,
    )

    private val quickActions = persistentListOf(
        QuickActionUiItem(1, cameraKey, "Camera", AppAccessState.Direct),
        QuickActionUiItem(2, gmailKey, "Gmail", AppAccessState.Guarded(FrictionPolicy.TypedIntention)),
        QuickActionUiItem(3, mapsKey, "Maps", AppAccessState.Direct),
    )

    private val workTrigger = SavedTrigger(
        id = "t-work",
        name = "Workday Focus",
        trigger = ProfileTrigger.Schedule(
            ScheduleTrigger(
                days = setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                ),
                startTime = TimeOfDay(9, 0),
                endTime = TimeOfDay(17, 0),
            ),
        ),
    )

    private val bedtimeTrigger = SavedTrigger(
        id = "t-bedtime",
        name = "Evening Wind-Down",
        trigger = ProfileTrigger.Schedule(
            ScheduleTrigger(
                days = DayOfWeek.entries.toSet(),
                startTime = TimeOfDay(22, 0),
                endTime = TimeOfDay(6, 30),
            ),
        ),
    )

    private val sampleProfiles = persistentListOf(
        CustomProfile(
            id = "work",
            name = "Deep Work",
            priority = 0,
            enabled = true,
            colorIndex = 0,
            activationRules = listOf(TriggerRule(listOf("t-work"))),
            appAssignments = mapOf(
                socialKey to AppAssignment.Blocked,
                videoKey to AppAssignment.Blocked,
                chromeKey to AppAssignment.Guarded(),
            ),
        ),
        CustomProfile(
            id = "bedtime",
            name = "Bedtime",
            priority = 1,
            enabled = true,
            colorIndex = 2,
            activationRules = listOf(TriggerRule(listOf("t-bedtime"))),
            defaultRule = ProfileDefaultRule.BLOCK_ALL,
            appAssignments = mapOf(clockKey to AppAssignment.Standard),
        ),
    )

    private fun capturePlayAsset(relativePath: String) {
        val assetFile = File("../docs/play-store/$relativePath")
        assetFile.parentFile?.mkdirs()
        composeTestRule.onRoot().captureRoboImage(assetFile)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h600dp-xhdpi")
    fun playStore_tablet7in_01_homeCanvas() {
        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "REST 48M  ·  2 UNLOCKS",
                    activeProfiles = persistentListOf(sampleProfiles[0]),
                    contentTopSpacing = 24.dp,
                ) {
                    DryToastSearchSurface(
                        query = "",
                        onQueryChange = {},
                        results = persistentListOf(),
                        onItemClick = {},
                        quickActions = quickActions,
                    )
                }
            }
        }
        capturePlayAsset("tablet-7in/01_home_canvas_1920x1200.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h600dp-xhdpi")
    fun playStore_tablet7in_02_settingsMasterDetail() {
        val uiState = sampleSettingsUiState()
        composeTestRule.setContent {
            val backStack = remember {
                mutableStateListOf<DryToastRoute>(DryToastRoute.Home, DryToastRoute.Settings)
            }
            val twoPaneStrategy = rememberSettingsTwoPaneSceneStrategy<DryToastRoute>(isExpanded = true) {
                ConfigureAppsScreen(
                    availableApps = sampleApps,
                    initialAssignments = baseAssignments,
                    defaultFriction = FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                )
            }
            DryToastTheme {
                NavDisplay(
                    backStack = backStack,
                    sceneStrategies = listOf(twoPaneStrategy, SinglePaneSceneStrategy()),
                    onBack = {},
                    entryProvider = entryProvider {
                        entry<DryToastRoute.Home> {}
                        entry<DryToastRoute.Settings>(metadata = SettingsTwoPaneSceneStrategy.masterMetadata()) {
                            DryToastSettingsScreen(
                                uiState = uiState,
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
                    },
                )
            }
        }
        capturePlayAsset("tablet-7in/02_settings_master_detail_1920x1200.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
    fun playStore_tablet10in_01_homeSearch() {
        val results = persistentListOf(
            SearchResult(
                item = SearchItem.App(
                    component = chromeKey,
                    label = "Chrome",
                    accessState = AppAccessState.Guarded(FrictionPolicy.TypedIntention),
                ),
                score = 100,
                matchType = MatchType.PREFIX,
            ),
            SearchResult(
                item = SearchItem.App(
                    component = clockKey,
                    label = "Clock",
                    accessState = AppAccessState.Direct,
                ),
                score = 60,
                matchType = MatchType.SUBSEQUENCE,
            ),
        )
        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "REST 48M  ·  2 UNLOCKS",
                    activeProfiles = persistentListOf(sampleProfiles[0]),
                    contentTopSpacing = 24.dp,
                ) {
                    DryToastSearchSurface(
                        query = "ch",
                        onQueryChange = {},
                        results = results,
                        onItemClick = {},
                    )
                }
            }
        }
        capturePlayAsset("tablet-10in/01_home_canvas_2560x1600.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
    fun playStore_tablet10in_02_settingsMasterDetailProfilesGrid() {
        val uiState = sampleSettingsUiState()
        composeTestRule.setContent {
            val backStack = remember {
                mutableStateListOf<DryToastRoute>(
                    DryToastRoute.Home,
                    DryToastRoute.Settings,
                    DryToastRoute.Profiles,
                )
            }
            val twoPaneStrategy = rememberSettingsTwoPaneSceneStrategy<DryToastRoute>(isExpanded = true) {
                ConfigureAppsScreen(
                    availableApps = sampleApps,
                    initialAssignments = baseAssignments,
                    defaultFriction = FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                )
            }
            DryToastTheme {
                NavDisplay(
                    backStack = backStack,
                    sceneStrategies = listOf(twoPaneStrategy, SinglePaneSceneStrategy()),
                    onBack = {},
                    entryProvider = entryProvider {
                        entry<DryToastRoute.Home> {}
                        entry<DryToastRoute.Settings>(metadata = SettingsTwoPaneSceneStrategy.masterMetadata()) {
                            DryToastSettingsScreen(
                                uiState = uiState,
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
                        entry<DryToastRoute.Profiles>(
                            metadata = SettingsTwoPaneSceneStrategy.detailMetadata(SettingsDetailSection.PROFILES),
                        ) {
                            ProfilesScreen(
                                profiles = uiState.customProfiles,
                                savedTriggers = uiState.savedTriggers,
                                onSaveProfile = {},
                                onDeleteProfile = {},
                                onReorderProfiles = {},
                                onConfigureProfileApps = {},
                                onBack = {},
                            )
                        }
                    },
                )
            }
        }
        capturePlayAsset("tablet-10in/02_settings_master_detail_2560x1600.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w672dp-h840dp-xxhdpi")
    fun playStore_foldable_01_homeCanvasUnfolded() {
        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "REST 48M  ·  2 UNLOCKS",
                    activeProfiles = persistentListOf(sampleProfiles[0]),
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    DryToastSearchSurface(
                        query = "",
                        onQueryChange = {},
                        results = persistentListOf(),
                        onItemClick = {},
                        quickActions = quickActions,
                    )
                }
            }
        }
        capturePlayAsset("foldable/01_home_canvas_unfolded_2016x2520.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w672dp-h840dp-xxhdpi")
    fun playStore_foldable_02_configureAppsUnfolded() {
        composeTestRule.setContent {
            DryToastTheme {
                ConfigureAppsScreen(
                    availableApps = sampleApps,
                    initialAssignments = baseAssignments,
                    defaultFriction = FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                )
            }
        }
        capturePlayAsset("foldable/02_configure_apps_unfolded_2016x2520.png")
    }

    private fun sampleSettingsUiState(): SettingsUiState = SettingsUiState(
        general = GeneralSettings(
            showUnlocks = true,
            showRest = true,
            requireFullTypingOfGuardedApps = false,
        ),
        baseProfile = BaseProfileSettings(
            defaultFriction = FrictionChoice.TYPED_INTENTION,
            quickActions = listOf(
                InstalledApp(chromeKey, "Chrome"),
                InstalledApp(clockKey, "Clock"),
            ),
            appAssignments = baseAssignments,
        ),
        customProfiles = sampleProfiles,
        savedTriggers = persistentListOf(workTrigger, bedtimeTrigger),
        availableApps = sampleApps,
        isLoading = false,
    )
}

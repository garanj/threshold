package com.garan.drytoast

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.garan.drytoast.core.model.AppAccessState
import com.garan.drytoast.core.model.ComponentKey
import com.garan.drytoast.core.model.FrictionPolicy
import com.garan.drytoast.core.search.MatchType
import com.garan.drytoast.core.search.SearchItem
import com.garan.drytoast.core.search.SearchResult
import com.garan.drytoast.ui.friction.TypedIntentionSurface
import com.garan.drytoast.ui.home.DryToastHomeCanvas
import com.garan.drytoast.ui.onboarding.OnboardingScreen
import com.garan.drytoast.ui.search.DryToastSearchSurface
import com.garan.drytoast.ui.theme.DryToastTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DryToastScreenshotTest {

    private val publisherRule = WindowLayoutInfoPublisherRule()
    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val testRule: RuleChain = RuleChain.outerRule(publisherRule).around(composeTestRule)

    private fun verifyAndArchiveSnapshot(fileName: String) {
        val goldenFile = File("src/test/snapshots/$fileName")
        composeTestRule.onRoot().captureRoboImage(goldenFile)
        val ciArtifactFile = File("build/outputs/roborazzi/$fileName")
        if (goldenFile.exists()) {
            ciArtifactFile.parentFile?.mkdirs()
            goldenFile.copyTo(ciArtifactFile, overwrite = true)
        }
    }

    @Test
    fun snapshotHomeCanvasDefault() {
        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Dry Toast",
                )
            }
        }
        verifyAndArchiveSnapshot("home_canvas.png")
    }

    @Test
    fun snapshotHomeCanvasWithZeros() {
        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "00:00",
                    dateText = "Sunday, October 31",
                    statusText = "Dry Toast · Night Rest",
                )
            }
        }
        verifyAndArchiveSnapshot("home_canvas_zeros.png")
    }

    @Test
    fun snapshotHomeCanvasWithProfileStatus() {
        val activeProfiles = persistentListOf(
            com.garan.drytoast.core.model.CustomProfile(
                id = "deep-work",
                name = "Deep Work",
                priority = 0,
                colorIndex = 0,
            ),
            com.garan.drytoast.core.model.CustomProfile(
                id = "evening",
                name = "Evening",
                priority = 1,
                colorIndex = 2,
            ),
        )
        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "14:32",
                    dateText = "Wednesday, November 4",
                    statusText = "REST 48M  ·  2 UNLOCKS",
                    activeProfiles = activeProfiles,
                )
            }
        }
        verifyAndArchiveSnapshot("home_canvas_status.png")
    }

    @Test
    fun snapshotSearchSurfaceWithResults() {
        val cameraKey = ComponentKey("com.android.camera", "com.android.camera.CameraActivity", 0L)
        val calcKey = ComponentKey("com.android.calculator", "com.android.calculator.CalcActivity", 0L)

        val results = persistentListOf(
            SearchResult(
                item = SearchItem.App(cameraKey, "Camera", AppAccessState.Direct),
                score = 100,
                matchType = MatchType.PREFIX,
            ),
            SearchResult(
                item = SearchItem.App(calcKey, "Calculator", AppAccessState.Direct),
                score = 80,
                matchType = MatchType.SUBSTRING,
            ),
        )

        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Dry Toast",
                    showDialerIcon = false,
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    DryToastSearchSurface(
                        query = "ca",
                        onQueryChange = {},
                        results = results,
                        onItemClick = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("search_results.png")
    }

    @Test
    fun snapshotSearchSurfaceGuardedFriction() {
        val socialKey = ComponentKey("com.example.social", "com.example.social.MainActivity", 0L)

        val results = persistentListOf(
            SearchResult(
                item = SearchItem.App(
                    component = socialKey,
                    label = "Social Feed",
                    accessState = AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
                ),
                score = 95,
                matchType = MatchType.PREFIX,
            ),
        )

        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Focus · Deep Work",
                    showDialerIcon = false,
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    DryToastSearchSurface(
                        query = "so",
                        onQueryChange = {},
                        results = results,
                        onItemClick = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("search_guarded.png")
    }

    @Test
    fun snapshotSearchSurfaceEmptyState() {
        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Dry Toast",
                    showDialerIcon = false,
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    DryToastSearchSurface(
                        query = "xyz",
                        onQueryChange = {},
                        results = persistentListOf(),
                        onItemClick = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("search_empty.png")
    }

    @Test
    fun snapshotSearchSurfaceShutterOpen() {
        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "Dry Toast",
                    showDialerIcon = false,
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    DryToastSearchSurface(
                        query = "",
                        isSearchActive = true,
                        onQueryChange = {},
                        results = persistentListOf(),
                        onItemClick = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("search_shutter_open.png")
    }

    @Test
    fun snapshotTypedIntentionCheckpoint() {
        composeTestRule.setContent {
            DryToastTheme {
                TypedIntentionSurface(
                    appLabel = "YouTube",
                    overridePhrase = "I need to use YouTube right now",
                    onConfirmOpen = {},
                    onDismiss = {},
                )
            }
        }
        verifyAndArchiveSnapshot("typed_intention_checkpoint.png")
    }

    @Test
    fun snapshotDragSortCheckpoint() {
        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.friction.DragSortSurface(
                    pairCount = 6,
                    seed = 42L,
                    onConfirmOpen = {},
                    onDismiss = {},
                )
            }
        }
        verifyAndArchiveSnapshot("drag_sort_checkpoint.png")
    }

    @Test
    fun snapshotDragSortCheckpointTabletExpanded() {
        composeTestRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(1280.dp, 800.dp)),
            ) {
                DryToastTheme {
                    com.garan.drytoast.ui.friction.DragSortSurface(
                        pairCount = 6,
                        seed = 42L,
                        onConfirmOpen = {},
                        onDismiss = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("drag_sort_tablet_expanded.png")
    }

    @Test
    fun snapshotCaretTracingCheckpoint() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.friction.CaretTracingSurface(
                    onConfirmOpen = {},
                    onDismiss = {},
                )
            }
        }
        verifyAndArchiveSnapshot("caret_tracing_checkpoint.png")
    }

    @Test
    fun snapshotCaretTracingPhoneLandscape() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(891.dp, 390.dp)),
            ) {
                DryToastTheme {
                    com.garan.drytoast.ui.friction.CaretTracingSurface(
                        onConfirmOpen = {},
                        onDismiss = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("caret_tracing_phone_landscape.png")
    }

    @Test
    fun snapshotHomeCanvasWithQuickActions() {
        val quickActions = persistentListOf(
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 1,
                component = ComponentKey("com.android.chrome", "Main"),
                label = "Chrome",
                accessState = AppAccessState.Direct,
            ),
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 2,
                component = ComponentKey("com.google.android.deskclock", "DeskClock"),
                label = "Clock",
                accessState = AppAccessState.Direct,
            ),
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 3,
                component = ComponentKey("com.google.android.gm", "ConversationListActivityGmail"),
                label = "Gmail",
                accessState = AppAccessState.Guarded(FrictionPolicy.TypedIntention),
            ),
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 4,
                component = ComponentKey("com.example.games", "MainActivity"),
                label = "Arcade Games",
                accessState = AppAccessState.Blocked,
            ),
        )

        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "REST 48M  ·  2 UNLOCKS",
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
        verifyAndArchiveSnapshot("home_canvas_quick_actions.png")
    }

    @Test
    fun snapshotSettingsScreen() {
        val chromeKey = ComponentKey("com.android.chrome", "Main")
        val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
        val socialKey = ComponentKey("com.instagram.android", "MainActivity")

        val uiState = com.garan.drytoast.ui.settings.SettingsUiState(
            general = com.garan.drytoast.core.model.GeneralSettings(
                showUnlocks = true,
                showRest = true,
                requireFullTypingOfGuardedApps = false,
            ),
            baseProfile = com.garan.drytoast.core.model.BaseProfileSettings(
                defaultFriction = com.garan.drytoast.core.model.FrictionChoice.TYPED_INTENTION,
                quickActions = listOf(
                    com.garan.drytoast.core.model.InstalledApp(chromeKey, "Chrome"),
                    com.garan.drytoast.core.model.InstalledApp(clockKey, "Clock"),
                ),
                appAssignments = mapOf(
                    chromeKey to com.garan.drytoast.core.model.AppAssignment.Guarded(),
                    socialKey to com.garan.drytoast.core.model.AppAssignment.Blocked,
                ),
            ),
            availableApps = persistentListOf(
                com.garan.drytoast.core.model.InstalledApp(chromeKey, "Chrome"),
                com.garan.drytoast.core.model.InstalledApp(clockKey, "Clock"),
                com.garan.drytoast.core.model.InstalledApp(socialKey, "Instagram"),
            ),
            isLoading = false,
        )

        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.DryToastSettingsScreen(
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
        }
        verifyAndArchiveSnapshot("settings_screen.png")
    }

    @Test
    fun snapshotConfigureAppsScreen() {
        val chromeKey = ComponentKey("com.android.chrome", "Main")
        val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
        val socialKey = ComponentKey("com.instagram.android", "MainActivity")

        val apps = persistentListOf(
            com.garan.drytoast.core.model.InstalledApp(chromeKey, "Chrome"),
            com.garan.drytoast.core.model.InstalledApp(clockKey, "Clock"),
            com.garan.drytoast.core.model.InstalledApp(socialKey, "Instagram"),
        )
        val assignments = mapOf(
            chromeKey to com.garan.drytoast.core.model.AppAssignment.Guarded(),
            socialKey to com.garan.drytoast.core.model.AppAssignment.Blocked,
        )

        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.ConfigureAppsScreen(
                    availableApps = apps,
                    initialAssignments = assignments,
                    defaultFriction = com.garan.drytoast.core.model.FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                )
            }
        }
        verifyAndArchiveSnapshot("configure_apps_screen.png")
    }

    @Test
    fun snapshotProfilesScreen() {
        val schedule = com.garan.drytoast.core.model.ScheduleTrigger(
            days = setOf(
                com.garan.drytoast.core.model.DayOfWeek.MONDAY,
                com.garan.drytoast.core.model.DayOfWeek.TUESDAY,
                com.garan.drytoast.core.model.DayOfWeek.WEDNESDAY,
                com.garan.drytoast.core.model.DayOfWeek.THURSDAY,
                com.garan.drytoast.core.model.DayOfWeek.FRIDAY,
            ),
            startTime = com.garan.drytoast.core.model.TimeOfDay(9, 0),
            endTime = com.garan.drytoast.core.model.TimeOfDay(17, 0),
        )
        val profile1 = com.garan.drytoast.core.model.CustomProfile(
            id = "work",
            name = "Deep Work",
            priority = 0,
            enabled = true,
            colorIndex = 0,
            trigger = com.garan.drytoast.core.model.ProfileTrigger.Schedule(schedule),
            appAssignments = mapOf(
                ComponentKey("com.instagram.android", "MainActivity") to
                    com.garan.drytoast.core.model.AppAssignment.Blocked,
            ),
        )
        val profile2 = com.garan.drytoast.core.model.CustomProfile(
            id = "bedtime",
            name = "Bedtime",
            priority = 1,
            enabled = false,
            colorIndex = 2,
            trigger = com.garan.drytoast.core.model.ProfileTrigger.Schedule(
                com.garan.drytoast.core.model.ScheduleTrigger(
                    dailyWindows = mapOf(
                        com.garan.drytoast.core.model.DayOfWeek.MONDAY to
                            com.garan.drytoast.core.model.TimeWindow(
                                com.garan.drytoast.core.model.TimeOfDay(16, 0),
                                com.garan.drytoast.core.model.TimeOfDay(18, 0),
                            ),
                        com.garan.drytoast.core.model.DayOfWeek.TUESDAY to
                            com.garan.drytoast.core.model.TimeWindow(
                                com.garan.drytoast.core.model.TimeOfDay(10, 0),
                                com.garan.drytoast.core.model.TimeOfDay(11, 0),
                            ),
                    ),
                    sameScheduleEveryDay = false,
                ),
            ),
        )

        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.ProfilesScreen(
                    profiles = listOf(profile1, profile2),
                    onSaveProfile = {},
                    onDeleteProfile = {},
                    onReorderProfiles = {},
                    onConfigureProfileApps = {},
                    onBack = {},
                )
            }
        }
        verifyAndArchiveSnapshot("profiles_screen.png")
    }

    @Test
    fun snapshotConfigureAppsScreenCustomProfile() {
        val chromeKey = ComponentKey("com.android.chrome", "Main")
        val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
        val socialKey = ComponentKey("com.instagram.android", "MainActivity")

        val apps = persistentListOf(
            com.garan.drytoast.core.model.InstalledApp(chromeKey, "Chrome"),
            com.garan.drytoast.core.model.InstalledApp(clockKey, "Clock"),
            com.garan.drytoast.core.model.InstalledApp(socialKey, "Instagram"),
        )
        val assignments = mapOf(
            chromeKey to com.garan.drytoast.core.model.AppAssignment.Standard,
            socialKey to com.garan.drytoast.core.model.AppAssignment.Blocked,
        )

        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.ConfigureAppsScreen(
                    availableApps = apps,
                    initialAssignments = assignments,
                    defaultFriction = com.garan.drytoast.core.model.FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                    isCustomProfile = true,
                    customProfileName = "Deep Work Overrides",
                )
            }
        }
        verifyAndArchiveSnapshot("configure_apps_custom_profile.png")
    }

    @Test
    fun snapshotOnboardingPage1DryToast() {
        composeTestRule.setContent {
            DryToastTheme {
                OnboardingScreen(
                    isDefaultLauncher = false,
                    onRequestDefaultLauncher = {},
                    onCompleteOnboarding = {},
                    initialPage = 0,
                )
            }
        }
        verifyAndArchiveSnapshot("onboarding_page1_drytoast.png")
    }

    @Test
    fun snapshotOnboardingPage3Friction() {
        composeTestRule.setContent {
            DryToastTheme {
                OnboardingScreen(
                    isDefaultLauncher = false,
                    onRequestDefaultLauncher = {},
                    onCompleteOnboarding = {},
                    initialPage = 2,
                    demoOverridePhrase = "I am opening Social Feed intentionally",
                )
            }
        }
        verifyAndArchiveSnapshot("onboarding_page3_friction.png")
    }

    @Test
    fun snapshotOnboardingPage6Activation() {
        composeTestRule.setContent {
            DryToastTheme {
                OnboardingScreen(
                    isDefaultLauncher = false,
                    onRequestDefaultLauncher = {},
                    onCompleteOnboarding = {},
                    showRoleRequiredWarning = true,
                    initialPage = 5,
                )
            }
        }
        verifyAndArchiveSnapshot("onboarding_page6_activation.png")
    }

    @Test
    fun snapshotHardenedModeScreen() {
        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.HardenedModeScreen(
                    isHardenedModeActive = false,
                    onBack = {},
                )
            }
        }
        verifyAndArchiveSnapshot("hardened_mode_screen.png")
    }

    @Test
    fun snapshotHomeCanvasRtl() {
        val quickActions = persistentListOf(
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 1,
                component = ComponentKey("com.android.chrome", "Main"),
                label = "Chrome",
                accessState = AppAccessState.Direct,
            ),
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 2,
                component = ComponentKey("com.google.android.gm", "ConversationListActivityGmail"),
                label = "Gmail",
                accessState = AppAccessState.Guarded(FrictionPolicy.TypedIntention),
            ),
        )
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DryToastTheme {
                    DryToastHomeCanvas(
                        timeText = "10:09",
                        dateText = "Monday, November 2",
                        statusText = "REST 48M  ·  2 UNLOCKS",
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
        }
        verifyAndArchiveSnapshot("home_canvas_rtl.png")
    }

    @Test
    fun snapshotSearchSurfaceRtl() {
        val cameraKey = ComponentKey("com.android.camera", "com.android.camera.CameraActivity", 0L)
        val socialKey = ComponentKey("com.example.social", "com.example.social.MainActivity", 0L)

        val results = persistentListOf(
            SearchResult(
                item = SearchItem.App(cameraKey, "Camera", AppAccessState.Direct),
                score = 100,
                matchType = MatchType.PREFIX,
            ),
            SearchResult(
                item = SearchItem.App(
                    component = socialKey,
                    label = "Social Feed",
                    accessState = AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
                ),
                score = 95,
                matchType = MatchType.PREFIX,
            ),
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DryToastTheme {
                    DryToastHomeCanvas(
                        timeText = "10:09",
                        dateText = "Monday, November 2",
                        statusText = "Dry Toast",
                        showDialerIcon = false,
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))
                        DryToastSearchSurface(
                            query = "ca",
                            onQueryChange = {},
                            results = results,
                            onItemClick = {},
                        )
                    }
                }
            }
        }
        verifyAndArchiveSnapshot("search_results_rtl.png")
    }

    @Test
    fun snapshotConfigureAppsScreenRtl() {
        val chromeKey = ComponentKey("com.android.chrome", "Main")
        val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
        val socialKey = ComponentKey("com.instagram.android", "MainActivity")

        val apps = persistentListOf(
            com.garan.drytoast.core.model.InstalledApp(chromeKey, "Chrome"),
            com.garan.drytoast.core.model.InstalledApp(clockKey, "Clock"),
            com.garan.drytoast.core.model.InstalledApp(socialKey, "Instagram"),
        )
        val assignments = mapOf(
            chromeKey to com.garan.drytoast.core.model.AppAssignment.Guarded(),
            socialKey to com.garan.drytoast.core.model.AppAssignment.Blocked,
        )

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DryToastTheme {
                    com.garan.drytoast.ui.settings.ConfigureAppsScreen(
                        availableApps = apps,
                        initialAssignments = assignments,
                        defaultFriction = com.garan.drytoast.core.model.FrictionChoice.TYPED_INTENTION,
                        onSave = {},
                        onBack = {},
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("configure_apps_rtl.png")
    }

    @Test
    fun snapshotDisableHardenedModeScreen() {
        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.DisableHardenedModeScreen(
                    isHardenedModeActive = true,
                    onConfirmDisable = {},
                    onCancel = {},
                    initialChallenge = "492",
                )
            }
        }
        verifyAndArchiveSnapshot("disable_hardened_mode_screen.png")
    }

    @Test
    fun snapshotSettingsLockScreen() {
        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.SettingsLockScreen(
                    isSettingsLocked = true,
                    onConfirmSetSettingsLocked = {},
                    onCancel = {},
                    initialChallenge = "815",
                )
            }
        }
        verifyAndArchiveSnapshot("settings_lock_screen.png")
    }

    @Test
    fun snapshotSettingsLockScreenMultiWindowBlocked() {
        composeTestRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(336.dp, 841.dp)),
            ) {
                DryToastTheme {
                    com.garan.drytoast.ui.settings.SettingsLockScreen(
                        isSettingsLocked = true,
                        onConfirmSetSettingsLocked = {},
                        onCancel = {},
                        initialChallenge = "815",
                        isMultiWindowOverride = true,
                    )
                }
            }
        }
        verifyAndArchiveSnapshot("settings_lock_split_screen_blocked.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w891dp-h390dp-land-mdpi")
    fun snapshotTriggerTimePickerPhoneLandscape() {
        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.DryToastTimeInputDialog(
                    title = "Start time",
                    initialHour = 8,
                    initialMinute = 30,
                    onConfirm = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        verifyAndArchiveSnapshot("trigger_time_picker_phone_landscape.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w891dp-h390dp-land-mdpi")
    fun snapshotHomeCanvasPhoneLandscape() {
        val activeProfiles = persistentListOf(
            com.garan.drytoast.core.model.CustomProfile(
                id = "deep-work",
                name = "Deep Work",
                priority = 0,
                colorIndex = 0,
            ),
        )
        val quickActions = persistentListOf(
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 1,
                component = ComponentKey("com.android.camera", "CameraActivity"),
                label = "Camera",
                accessState = AppAccessState.Direct,
            ),
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 2,
                component = ComponentKey("com.google.android.gm", "ConversationListActivityGmail"),
                label = "Gmail",
                accessState = AppAccessState.Guarded(FrictionPolicy.TypedIntention),
            ),
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 3,
                component = ComponentKey("com.google.android.apps.maps", "MapsActivity"),
                label = "Maps",
                accessState = AppAccessState.Direct,
            ),
        )

        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "REST 48M  ·  2 UNLOCKS",
                    activeProfiles = activeProfiles,
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
        verifyAndArchiveSnapshot("home_canvas_phone_landscape.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w680dp-h840dp-mdpi")
    fun snapshotHomeSearchFoldableTabletop() {
        val cameraKey = ComponentKey("com.android.camera", "com.android.camera.CameraActivity", 0L)
        val calcKey = ComponentKey("com.android.calculator", "com.android.calculator.CalcActivity", 0L)
        val socialKey = ComponentKey("com.example.social", "com.example.social.MainActivity", 0L)
        val activeProfiles = persistentListOf(
            com.garan.drytoast.core.model.CustomProfile(
                id = "deep-work",
                name = "Deep Work",
                priority = 0,
                colorIndex = 0,
            ),
        )
        val results = persistentListOf(
            SearchResult(
                item = SearchItem.App(cameraKey, "Camera", AppAccessState.Direct),
                score = 100,
                matchType = MatchType.PREFIX,
            ),
            SearchResult(
                item = SearchItem.App(calcKey, "Calculator", AppAccessState.Direct),
                score = 85,
                matchType = MatchType.PREFIX,
            ),
            SearchResult(
                item = SearchItem.App(
                    component = socialKey,
                    label = "Social Feed",
                    accessState = AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
                ),
                score = 70,
                matchType = MatchType.SUBSTRING,
            ),
        )

        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "REST 48M  ·  2 UNLOCKS",
                    activeProfiles = activeProfiles,
                    showDialerIcon = false,
                    contentTopSpacing = 24.dp,
                ) {
                    DryToastSearchSurface(
                        query = "ca",
                        onQueryChange = {},
                        results = results,
                        onItemClick = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val horizontalHinge = FoldingFeature(
            activity = composeTestRule.activity,
            center = 420,
            size = 20,
            state = FoldingFeature.State.HALF_OPENED,
            orientation = FoldingFeature.Orientation.HORIZONTAL,
        )
        publisherRule.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(horizontalHinge)))
        composeTestRule.waitForIdle()

        verifyAndArchiveSnapshot("home_search_foldable_tabletop.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
    fun snapshotHomeCanvasTabletExpanded() {
        val activeProfiles = persistentListOf(
            com.garan.drytoast.core.model.CustomProfile(
                id = "deep-work",
                name = "Deep Work",
                priority = 0,
                colorIndex = 0,
            ),
        )
        val quickActions = persistentListOf(
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 1,
                component = ComponentKey("com.android.camera", "CameraActivity"),
                label = "Camera",
                accessState = AppAccessState.Direct,
            ),
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 2,
                component = ComponentKey("com.google.android.gm", "ConversationListActivityGmail"),
                label = "Gmail",
                accessState = AppAccessState.Guarded(FrictionPolicy.TypedIntention),
            ),
            com.garan.drytoast.ui.search.QuickActionUiItem(
                slotNumber = 3,
                component = ComponentKey("com.google.android.apps.maps", "MapsActivity"),
                label = "Maps",
                accessState = AppAccessState.Direct,
            ),
        )

        composeTestRule.setContent {
            DryToastTheme {
                DryToastHomeCanvas(
                    timeText = "10:09",
                    dateText = "Monday, November 2",
                    statusText = "REST 48M  ·  2 UNLOCKS",
                    activeProfiles = activeProfiles,
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
        verifyAndArchiveSnapshot("home_canvas_tablet_expanded.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
    fun snapshotOnboardingTabletExpanded() {
        composeTestRule.setContent {
            DryToastTheme {
                OnboardingScreen(
                    isDefaultLauncher = false,
                    onRequestDefaultLauncher = {},
                    onCompleteOnboarding = {},
                    initialPage = 0,
                )
            }
        }
        verifyAndArchiveSnapshot("onboarding_tablet_expanded.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
    fun snapshotSettingsLockTabletExpanded() {
        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.SettingsLockScreen(
                    isSettingsLocked = true,
                    onConfirmSetSettingsLocked = {},
                    onCancel = {},
                    initialChallenge = "815",
                )
            }
        }
        verifyAndArchiveSnapshot("settings_lock_tablet_expanded.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w673dp-h841dp-mdpi")
    fun snapshotConfigureAppsFoldableUnfolded() {
        val cameraKey = ComponentKey("com.android.camera", "CameraActivity")
        val chromeKey = ComponentKey("com.android.chrome", "Main")
        val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
        val gmailKey = ComponentKey("com.google.android.gm", "ConversationListActivityGmail")
        val socialKey = ComponentKey("com.instagram.android", "MainActivity")
        val mapsKey = ComponentKey("com.google.android.apps.maps", "MapsActivity")
        val videoKey = ComponentKey("com.google.android.youtube", "HomeActivity")

        val apps = persistentListOf(
            com.garan.drytoast.core.model.InstalledApp(cameraKey, "Camera"),
            com.garan.drytoast.core.model.InstalledApp(chromeKey, "Chrome"),
            com.garan.drytoast.core.model.InstalledApp(clockKey, "Clock"),
            com.garan.drytoast.core.model.InstalledApp(gmailKey, "Gmail"),
            com.garan.drytoast.core.model.InstalledApp(socialKey, "Instagram"),
            com.garan.drytoast.core.model.InstalledApp(mapsKey, "Maps"),
            com.garan.drytoast.core.model.InstalledApp(videoKey, "YouTube"),
        )
        val assignments = mapOf(
            chromeKey to com.garan.drytoast.core.model.AppAssignment.Guarded(),
            gmailKey to com.garan.drytoast.core.model.AppAssignment.Guarded(),
            socialKey to com.garan.drytoast.core.model.AppAssignment.Blocked,
            videoKey to com.garan.drytoast.core.model.AppAssignment.Blocked,
        )

        composeTestRule.setContent {
            DryToastTheme {
                com.garan.drytoast.ui.settings.ConfigureAppsScreen(
                    availableApps = apps,
                    initialAssignments = assignments,
                    defaultFriction = com.garan.drytoast.core.model.FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                )
            }
        }
        verifyAndArchiveSnapshot("configure_apps_foldable_unfolded.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
    fun snapshotSettingsMasterDetailTabletExpanded() {
        val chromeKey = ComponentKey("com.android.chrome", "Main")
        val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
        val socialKey = ComponentKey("com.instagram.android", "MainActivity")
        val videoKey = ComponentKey("com.google.android.youtube", "HomeActivity")

        val workTrigger = com.garan.drytoast.core.model.SavedTrigger(
            id = "t-work",
            name = "Workday Focus",
            trigger = com.garan.drytoast.core.model.ProfileTrigger.Schedule(
                com.garan.drytoast.core.model.ScheduleTrigger(
                    days = setOf(
                        com.garan.drytoast.core.model.DayOfWeek.MONDAY,
                        com.garan.drytoast.core.model.DayOfWeek.TUESDAY,
                        com.garan.drytoast.core.model.DayOfWeek.WEDNESDAY,
                        com.garan.drytoast.core.model.DayOfWeek.THURSDAY,
                        com.garan.drytoast.core.model.DayOfWeek.FRIDAY,
                    ),
                    startTime = com.garan.drytoast.core.model.TimeOfDay(9, 0),
                    endTime = com.garan.drytoast.core.model.TimeOfDay(17, 0),
                ),
            ),
        )
        val bedtimeTrigger = com.garan.drytoast.core.model.SavedTrigger(
            id = "t-bedtime",
            name = "Evening Wind-Down",
            trigger = com.garan.drytoast.core.model.ProfileTrigger.Schedule(
                com.garan.drytoast.core.model.ScheduleTrigger(
                    days = com.garan.drytoast.core.model.DayOfWeek.entries.toSet(),
                    startTime = com.garan.drytoast.core.model.TimeOfDay(22, 0),
                    endTime = com.garan.drytoast.core.model.TimeOfDay(6, 30),
                ),
            ),
        )
        val profiles = persistentListOf(
            com.garan.drytoast.core.model.CustomProfile(
                id = "work",
                name = "Deep Work",
                priority = 0,
                enabled = true,
                colorIndex = 0,
                activationRules = listOf(com.garan.drytoast.core.model.TriggerRule(listOf("t-work"))),
                appAssignments = mapOf(
                    socialKey to com.garan.drytoast.core.model.AppAssignment.Blocked,
                    videoKey to com.garan.drytoast.core.model.AppAssignment.Blocked,
                    chromeKey to com.garan.drytoast.core.model.AppAssignment.Guarded(),
                ),
            ),
            com.garan.drytoast.core.model.CustomProfile(
                id = "bedtime",
                name = "Bedtime",
                priority = 1,
                enabled = true,
                colorIndex = 2,
                activationRules = listOf(com.garan.drytoast.core.model.TriggerRule(listOf("t-bedtime"))),
                defaultRule = com.garan.drytoast.core.model.ProfileDefaultRule.BLOCK_ALL,
                appAssignments = mapOf(
                    clockKey to com.garan.drytoast.core.model.AppAssignment.Standard,
                ),
            ),
        )
        val uiState = com.garan.drytoast.ui.settings.SettingsUiState(
            general = com.garan.drytoast.core.model.GeneralSettings(
                showUnlocks = true,
                showRest = true,
                requireFullTypingOfGuardedApps = false,
            ),
            baseProfile = com.garan.drytoast.core.model.BaseProfileSettings(
                defaultFriction = com.garan.drytoast.core.model.FrictionChoice.TYPED_INTENTION,
                quickActions = listOf(
                    com.garan.drytoast.core.model.InstalledApp(chromeKey, "Chrome"),
                    com.garan.drytoast.core.model.InstalledApp(clockKey, "Clock"),
                ),
                appAssignments = mapOf(
                    chromeKey to com.garan.drytoast.core.model.AppAssignment.Guarded(),
                    socialKey to com.garan.drytoast.core.model.AppAssignment.Blocked,
                ),
            ),
            customProfiles = profiles,
            savedTriggers = persistentListOf(workTrigger, bedtimeTrigger),
            availableApps = persistentListOf(
                com.garan.drytoast.core.model.InstalledApp(chromeKey, "Chrome"),
                com.garan.drytoast.core.model.InstalledApp(clockKey, "Clock"),
                com.garan.drytoast.core.model.InstalledApp(socialKey, "Instagram"),
                com.garan.drytoast.core.model.InstalledApp(videoKey, "YouTube"),
            ),
            isLoading = false,
        )

        composeTestRule.setContent {
            val backStack = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateListOf<DryToastRoute>(
                    DryToastRoute.Home,
                    DryToastRoute.Settings,
                    DryToastRoute.Profiles,
                )
            }
            val twoPaneStrategy = com.garan.drytoast.ui.settings.rememberSettingsTwoPaneSceneStrategy<DryToastRoute>(
                isExpanded = true,
            ) {
                com.garan.drytoast.ui.settings.ConfigureAppsScreen(
                    availableApps = uiState.availableApps,
                    initialAssignments = uiState.baseProfile.appAssignments,
                    defaultFriction = com.garan.drytoast.core.model.FrictionChoice.TYPED_INTENTION,
                    onSave = {},
                    onBack = {},
                )
            }
            DryToastTheme {
                androidx.navigation3.ui.NavDisplay(
                    backStack = backStack,
                    sceneStrategies = listOf(
                        twoPaneStrategy,
                        androidx.navigation3.scene.SinglePaneSceneStrategy(),
                    ),
                    onBack = {},
                    entryProvider = androidx.navigation3.runtime.entryProvider {
                        entry<DryToastRoute.Home> {}
                        entry<DryToastRoute.Settings>(
                            metadata = com.garan.drytoast.ui.settings.SettingsTwoPaneSceneStrategy.masterMetadata(),
                        ) {
                            com.garan.drytoast.ui.settings.DryToastSettingsScreen(
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
                            metadata = com.garan.drytoast.ui.settings.SettingsTwoPaneSceneStrategy.detailMetadata(
                                com.garan.drytoast.ui.settings.SettingsDetailSection.PROFILES,
                            ),
                        ) {
                            com.garan.drytoast.ui.settings.ProfilesScreen(
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
        verifyAndArchiveSnapshot("settings_master_detail_tablet_expanded.png")
    }
}

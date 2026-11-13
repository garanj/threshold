package com.garan.threshold.ui.home

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import com.garan.threshold.MainActivity
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.ui.launcher.AppLauncher
import com.garan.threshold.ui.search.SearchViewModel
import com.garan.threshold.ui.theme.ThresholdTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThresholdHomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val cameraComponent = ComponentKey("com.android.camera", "com.android.camera.CameraActivity", 0L)

    private val fakePackageIndexSource = FakePackageIndexSource(
        initialApps = listOf(
            InstalledApp(cameraComponent, "Camera"),
        ),
    )

    @Test
    fun ambientState_backPressed_consumedWithoutFinishingActivity() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()
        var imeDismissed = false

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    onDismissIme = { imeDismissed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("THRESHOLD").assertIsDisplayed()
        composeTestRule.onNodeWithText("Type to launch...").assertIsDisplayed()

        // Press back via activity back dispatcher
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Verify activity is NOT finishing (back was consumed cleanly as a no-op)
        composeTestRule.activityRule.scenario.onActivity { activity ->
            assertThat(activity.isFinishing).isFalse()
        }
        assertThat(imeDismissed).isFalse()
        composeTestRule.onNodeWithText("THRESHOLD").assertIsDisplayed()
    }

    @Test
    fun searchActive_backPressed_clearsQueryAndDismissesSearch() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()
        var imeDismissed = false

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    onDismissIme = { imeDismissed = true },
                )
            }
        }

        // Tap search placeholder to activate search
        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        // Type query into the editable text field
        composeTestRule.onNode(hasSetTextAction()).performTextInput("cam")
        composeTestRule.waitForIdle()

        assertThat(viewModel.uiState.value.query).isEqualTo("cam")
        composeTestRule.onNodeWithText("Camera").assertIsDisplayed()

        // Press back via activity back dispatcher
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // Verify query cleared, IME dismissed, and back to ambient placeholder
        assertThat(viewModel.uiState.value.query).isEmpty()
        assertThat(imeDismissed).isTrue()
        composeTestRule.onNodeWithText("Type to launch...").assertIsDisplayed()
        composeTestRule.onNodeWithText("THRESHOLD").assertIsDisplayed()
    }

    @Test
    fun appLaunched_resetsSearchState() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                )
            }
        }

        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("cam")
        composeTestRule.waitForIdle()

        // Click on the app item
        composeTestRule.onNodeWithText("Camera").performClick()
        composeTestRule.waitForIdle()

        assertThat(launcher.launchedComponents).containsExactly(cameraComponent)
        assertThat(viewModel.uiState.value.query).isEmpty()
        composeTestRule.onNodeWithText("Type to launch...").assertIsDisplayed()
    }

    @Test
    fun appLongPressed_opensAppInfoAndResetsSearch() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                )
            }
        }

        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("cam")
        composeTestRule.waitForIdle()

        // Long-press on the app item (standard 500ms press — child ViewConfiguration must not require 3s)
        composeTestRule.onNodeWithText("Camera").performTouchInput {
            longClick(durationMillis = 500L)
        }
        composeTestRule.waitForIdle()

        assertThat(launcher.appInfoComponents).containsExactly(cameraComponent)
        assertThat(viewModel.uiState.value.query).isEmpty()
        composeTestRule.onNodeWithText("Type to launch...").assertIsDisplayed()
    }

    @Test
    fun guardedApp_imeGoOnPartialPrefix_doesNotBypassGatekeeper_andTriggersResistanceDetent() {
        val socialComponent = ComponentKey("com.example.social", "com.example.social.MainActivity", 0L)
        val guardedSource = FakePackageIndexSource(
            initialApps = listOf(InstalledApp(socialComponent, "Social Feed")),
        )
        val focusProfile = Profile(
            id = "focus",
            name = "Focus",
            defaultAccess = AppAccessState.Direct,
            appRules = mapOf(
                socialComponent to AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
            ),
        )
        val viewModel = SearchViewModel(
            packageIndexSource = guardedSource,
            initialProfile = focusProfile,
        )
        val launcher = FakeAppLauncher()
        val haptic = TestHapticEngine()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    hapticEngine = haptic,
                )
            }
        }

        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        // Type partial prefix "soc" and press IME Go
        composeTestRule.onNode(hasSetTextAction()).performTextInput("soc")
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasSetTextAction()).performImeAction()
        composeTestRule.waitForIdle()

        // Guarded app must NOT launch without passing friction gatekeeper, and resistance detent fires
        assertThat(launcher.launchedComponents).isEmpty()
        assertThat(viewModel.uiState.value.query).isEqualTo("soc")
        assertThat(haptic.resistanceDetentCount).isEqualTo(1)
    }

    @Test
    fun guardedAppClicked_triggersResistanceDetentHaptic() {
        val socialComponent = ComponentKey("com.example.social", "com.example.social.MainActivity", 0L)
        val guardedSource = FakePackageIndexSource(
            initialApps = listOf(InstalledApp(socialComponent, "Social Feed")),
        )
        val focusProfile = Profile(
            id = "focus",
            name = "Focus",
            defaultAccess = AppAccessState.Direct,
            appRules = mapOf(
                socialComponent to AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
            ),
        )
        val viewModel = SearchViewModel(
            packageIndexSource = guardedSource,
            initialProfile = focusProfile,
        )
        val launcher = FakeAppLauncher()
        val haptic = TestHapticEngine()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    hapticEngine = haptic,
                )
            }
        }

        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("soc")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Social Feed").performClick()
        composeTestRule.waitForIdle()

        assertThat(haptic.resistanceDetentCount).isEqualTo(1)
        assertThat(launcher.launchedComponents).isEmpty()
    }

    @Test
    fun appLongPressed_triggersResistanceDetentHaptic() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()
        val haptic = TestHapticEngine()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    hapticEngine = haptic,
                )
            }
        }

        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("cam")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Camera").performTouchInput {
            longClick()
        }
        composeTestRule.waitForIdle()

        assertThat(haptic.resistanceDetentCount).isEqualTo(1)
        assertThat(launcher.appInfoComponents).containsExactly(cameraComponent)
    }

    @Test
    fun mainActivity_onNewIntentHome_collapsesActiveSearchAndClearsQuery() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val activity = controller.get()

            // Simulate user typing a search query on MainActivity
            activity.searchViewModel.activateSearch()
            activity.searchViewModel.onQueryChanged("cam")
            assertThat(activity.searchViewModel.uiState.value.isSearching).isTrue()
            assertThat(activity.searchViewModel.uiState.value.query).isEqualTo("cam")

            // Simulate pressing system Home button while already on MainActivity (singleTask)
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            controller.newIntent(homeIntent)

            // Search must be collapsed and query cleared
            assertThat(activity.searchViewModel.uiState.value.query).isEmpty()
            assertThat(activity.searchViewModel.uiState.value.isSearching).isFalse()
        }
    }

    @Test
    fun mainActivity_onCreate_doesNotEagerlyLoadPackageIndexInAmbientMode() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val activity = controller.get()

            // Memory ceiling invariant: package index must remain unloaded in ambient home mode
            // to prevent ResourcesManager from mmapping foreign APK resource tables (+25 MB PSS)
            assertThat(activity.searchViewModel.isIndexLoaded).isFalse()
        }
    }

    @Test
    fun mainActivity_onNewIntentHome_whileInFrictionCheckpoint_collapsesFrictionAndReturnsHome() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val activity = controller.get()
            val youtubeApp = SearchItem.App(
                component = ComponentKey("com.google.android.youtube", "HomeActivity", 0L),
                label = "YouTube",
                accessState = AppAccessState.Guarded(FrictionPolicy.DragSort(6)),
            )

            activity.searchViewModel.openFriction(youtubeApp, FrictionPolicy.DragSort(6))
            assertThat(activity.searchViewModel.uiState.value.activeFriction).isNotNull()

            // Press Home button while on friction checkpoint
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            controller.newIntent(homeIntent)

            // Active friction checkpoint must be cleared
            assertThat(activity.searchViewModel.uiState.value.activeFriction).isNull()
        }
    }

    @Test
    fun frictionCheckpoint_onActivityStop_dismissesActiveFrictionGate() {
        val youtubeComponent = ComponentKey("com.google.android.youtube", "com.google.android.youtube.HomeActivity", 0L)
        val guardedSource = FakePackageIndexSource(
            initialApps = listOf(InstalledApp(youtubeComponent, "YouTube")),
        )
        val profile = Profile(
            id = "ambient",
            name = "Ambient",
            defaultAccess = AppAccessState.Direct,
            appRules = mapOf(
                youtubeComponent to AppAccessState.Guarded(FrictionPolicy.DragSort(6)),
            ),
        )
        val viewModel = SearchViewModel(
            packageIndexSource = guardedSource,
            initialProfile = profile,
        )
        val launcher = FakeAppLauncher()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                )
            }
        }

        // Open friction screen
        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("You")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("YouTube").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drag_sort_surface").assertIsDisplayed()

        // Simulate locking screen / backgrounding activity (CREATED state triggers ON_STOP)
        composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        composeTestRule.waitForIdle()

        // Friction gate must have been dismissed so it cannot linger across lock sessions
        assertThat(viewModel.uiState.value.activeFriction).isNull()
    }

    @Test
    fun guardedApp_dismissingFrictionScreen_placesSearchCursorAtEndOfQuery() {
        val youtubeComponent = ComponentKey("com.google.android.youtube", "com.google.android.youtube.HomeActivity", 0L)
        val guardedSource = FakePackageIndexSource(
            initialApps = listOf(InstalledApp(youtubeComponent, "YouTube")),
        )
        val profile = Profile(
            id = "ambient",
            name = "Ambient",
            defaultAccess = AppAccessState.Direct,
            appRules = mapOf(
                youtubeComponent to AppAccessState.Guarded(FrictionPolicy.DragSort(6)),
            ),
        )
        val viewModel = SearchViewModel(
            packageIndexSource = guardedSource,
            initialProfile = profile,
        )
        val launcher = FakeAppLauncher()

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                )
            }
        }

        // 1. Activate search and type "You"
        composeTestRule.onNodeWithText("Type to launch...").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("You")
        composeTestRule.waitForIdle()

        // 2. Tap YouTube -> navigates to DragSortSurface
        composeTestRule.onNodeWithText("YouTube").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("drag_sort_surface").assertIsDisplayed()

        // 3. Swipe back (system back) to return to search
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // 4. Verify search field is restored with cursor at the END of "You" (index 3), not index 0
        val textFieldNode = composeTestRule.onNode(hasSetTextAction()).fetchSemanticsNode()
        val selection = textFieldNode.config[SemanticsProperties.TextSelectionRange]
        assertThat(selection).isEqualTo(TextRange(3, 3))

        // Typing "Tube" appends to "You" -> "YouTube" (instead of prepending "TubeYou")
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Tube")
        composeTestRule.waitForIdle()
        assertThat(viewModel.uiState.value.query).isEqualTo("YouTube")
    }

    @Test
    fun antiMetrics_whenRepositoryProvided_displaysFormattedStatus() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()
        val metricsFlow = kotlinx.coroutines.flow.MutableStateFlow(
            com.garan.threshold.core.model.AntiMetrics(
                restDurationMs = 48 * 60_000L,
                unlockCount = 6,
            ),
        )
        val fakeRepo = object : com.garan.threshold.core.port.AntiMetricsRepository {
            override val metrics: kotlinx.coroutines.flow.StateFlow<com.garan.threshold.core.model.AntiMetrics> =
                metricsFlow
            override fun onScreenOff(timestampMs: Long) {}
            override fun onUnlock(timestampMs: Long) {}
        }

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    antiMetricsRepository = fakeRepo,
                )
            }
        }

        composeTestRule.onNodeWithText("REST 48M  ·  6 UNLOCKS").assertIsDisplayed()
    }

    @Test
    fun homeCanvas_pressUnderThreeSeconds_doesNotOpenSettings() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()
        var settingsOpenedCount = 0

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    onOpenSettings = { settingsOpenedCount++ },
                )
            }
        }

        // Hold for 2,500ms (well above Android's default 400ms long-press, but below 3,000ms)
        composeTestRule.onNodeWithText("THRESHOLD").performTouchInput {
            down(center)
            advanceEventTime(2_500L)
            up()
        }
        composeTestRule.waitForIdle()

        assertThat(settingsOpenedCount).isEqualTo(0)
    }

    @Test
    fun homeCanvas_longPressThreeSeconds_opensSettingsAndMarksFirstRunHintSeen() {
        val viewModel = SearchViewModel(packageIndexSource = fakePackageIndexSource)
        val launcher = FakeAppLauncher()
        var settingsOpenedCount = 0

        composeTestRule.setContent {
            ThresholdTheme {
                ThresholdHomeScreen(
                    viewModel = viewModel,
                    appLauncher = launcher,
                    onOpenSettings = { settingsOpenedCount++ },
                )
            }
        }

        // Hold for >= 3,000ms
        composeTestRule.onNodeWithText("THRESHOLD").performTouchInput {
            longClick(durationMillis = 3_100L)
        }
        composeTestRule.waitForIdle()

        assertThat(settingsOpenedCount).isEqualTo(1)
    }

    @Test
    fun mainActivity_recreate_doesNotRetainActivityInViewModels() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val firstActivity = controller.get()

            controller.recreate()
            val secondActivity = controller.get()

            assertThat(secondActivity).isNotSameInstanceAs(firstActivity)
            assertThat(findReachableActivities(secondActivity.searchViewModel)).isEmpty()
            assertThat(findReachableActivities(secondActivity.settingsViewModel)).isEmpty()
        }
    }

    @Test
    fun mainActivity_recreate_sharesSameLocationSourceInstance() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val firstLocationSource = controller.get().locationSource

            controller.recreate()
            val secondLocationSource = controller.get().locationSource

            assertThat(secondLocationSource).isSameInstanceAs(firstLocationSource)
        }
    }

    @Test
    fun mainActivity_recreate_testHooksStillDriveSurvivingViewModel() {
        Robolectric.buildActivity(MainActivity::class.java).use { controller ->
            controller.setup()
            val firstActivity = controller.get()
            val workProfile = com.garan.threshold.core.model.CustomProfile(
                id = "work",
                name = "Work",
                priority = 0,
                enabled = true,
                colorIndex = 0,
                trigger = com.garan.threshold.core.model.ProfileTrigger.Schedule(
                    com.garan.threshold.core.model.ScheduleTrigger(
                        dailyWindows = mapOf(
                            com.garan.threshold.core.model.DayOfWeek.MONDAY to
                                com.garan.threshold.core.model.TimeWindow(
                                    com.garan.threshold.core.model.TimeOfDay(9, 0),
                                    com.garan.threshold.core.model.TimeOfDay(17, 0),
                                ),
                        ),
                        sameScheduleEveryDay = false,
                    ),
                ),
            )
            firstActivity.setClockAndTriggerAlarmForTesting(
                com.garan.threshold.core.model.DayOfWeek.MONDAY,
                com.garan.threshold.core.model.TimeOfDay(8, 0),
            )
            firstActivity.setExactAlarmPermissionForTesting(true)
            kotlinx.coroutines.runBlocking {
                firstActivity.configureScheduleProfilesForTesting(customProfiles = listOf(workProfile))
            }
            assertThat(firstActivity.searchViewModel.uiState.value.activeProfiles).isEmpty()
            assertThat(firstActivity.searchViewModel.uiState.value.capabilityIssues).isEmpty()

            // Recreate the Activity (simulating fontScale / density / locale change)
            controller.recreate()
            val secondActivity = controller.get()

            // Invoking test hooks on the NEW Activity must drive the surviving SearchViewModel
            secondActivity.setClockAndTriggerAlarmForTesting(
                com.garan.threshold.core.model.DayOfWeek.MONDAY,
                com.garan.threshold.core.model.TimeOfDay(10, 0),
            )
            secondActivity.setExactAlarmPermissionForTesting(false)

            assertThat(secondActivity.searchViewModel.uiState.value.activeProfiles.map { it.id })
                .containsExactly("work")
            assertThat(secondActivity.searchViewModel.uiState.value.capabilityIssues)
                .isNotEmpty()
        }
    }

    private fun findReachableActivities(root: Any): List<MainActivity> {
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        val found = mutableListOf<MainActivity>()
        val queue = ArrayDeque<Any>()
        queue.add(root)
        visited.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current is MainActivity) {
                found.add(current)
                continue
            }
            var clazz: Class<*>? = current.javaClass
            while (clazz != null && clazz != Any::class.java) {
                val pkg = clazz.packageName
                if (pkg.startsWith("java.") || pkg.startsWith("jdk.") || pkg.startsWith("android.")) {
                    break
                }
                for (field in clazz.declaredFields) {
                    if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                    if (field.type.isPrimitive) continue
                    runCatching {
                        field.isAccessible = true
                        field.get(current)
                    }.getOrNull()?.let { value ->
                        if (visited.add(value)) {
                            queue.add(value)
                        }
                    }
                }
                clazz = clazz.superclass
            }
        }
        return found
    }

    private class TestHapticEngine : HapticEngine {
        var rotaryDetentCount = 0
            private set
        var targetSnapCount = 0
            private set
        var resistanceDetentCount = 0
            private set
        var relaySnapCount = 0
            private set

        override fun rotaryDetent() {
            rotaryDetentCount++
        }

        override fun targetSnap() {
            targetSnapCount++
        }

        override fun resistanceDetent() {
            resistanceDetentCount++
        }

        override fun relaySnap() {
            relaySnapCount++
        }
    }

    private class FakeAppLauncher : AppLauncher {
        val launchedComponents = mutableListOf<ComponentKey>()
        val appInfoComponents = mutableListOf<ComponentKey>()

        override fun launchApp(component: ComponentKey): Boolean {
            launchedComponents.add(component)
            return true
        }

        override fun openAppInfo(component: ComponentKey): Boolean {
            appInfoComponents.add(component)
            return true
        }
    }

    private class FakePackageIndexSource(private val initialApps: List<InstalledApp> = emptyList()) :
        PackageIndexSource {
        override suspend fun getInstalledApps(): List<InstalledApp> = initialApps
        override fun observePackageChanges(): Flow<PackageChangeEvent> = MutableSharedFlow()
    }
}

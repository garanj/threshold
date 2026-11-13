package com.garan.threshold.ui.search

import com.garan.threshold.core.gatekeeper.IntentGatekeeper
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.LaunchResult
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
import com.garan.threshold.core.search.ActionCandidate
import com.garan.threshold.core.search.FuzzyMatcher
import com.garan.threshold.core.search.SearchEngine
import com.garan.threshold.core.search.SearchItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val dialerComponent = ComponentKey("com.android.dialer", "com.android.dialer.DialerActivity", 0L)
    private val cameraComponent = ComponentKey("com.android.camera", "com.android.camera.CameraActivity", 0L)
    private val calculatorComponent =
        ComponentKey("com.android.calculator", "com.android.calculator.CalculatorActivity", 0L)
    private val socialComponent = ComponentKey("com.example.social", "com.example.social.MainActivity", 0L)
    private val blockedComponent = ComponentKey("com.example.games", "com.example.games.MainActivity", 0L)

    private val fakePackageIndexSource = FakePackageIndexSource(
        initialApps = listOf(
            InstalledApp(dialerComponent, "Phone"),
            InstalledApp(cameraComponent, "Camera"),
            InstalledApp(calculatorComponent, "Calculator"),
            InstalledApp(socialComponent, "Social Feed"),
            InstalledApp(blockedComponent, "Arcade Games"),
        ),
    )

    private val profile = Profile(
        id = "focus",
        name = "Focus",
        defaultAccess = AppAccessState.Direct,
        appRules = mapOf(
            socialComponent to AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
            blockedComponent to AppAccessState.Blocked,
        ),
    )

    private val searchEngine = SearchEngine(
        gatekeeper = IntentGatekeeper(),
        matcher = FuzzyMatcher(),
    )

    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            searchEngine = searchEngine,
            defaultDialerPackage = "com.android.dialer",
            actions = listOf(
                ActionCandidate("settings", "Threshold Settings", ":settings"),
            ),
            initialProfile = profile,
            ioDispatcher = testDispatcher,
        ).apply {
            ensureIndexLoaded()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_queryIsEmpty_resultsAreEmpty() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertThat(state.query).isEmpty()
        assertThat(state.results).isEmpty()
    }

    @Test
    fun onQueryChanged_filtersInstalledApps() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onQueryChanged("cam")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertThat(results).hasSize(1)
        val first = results[0].item as SearchItem.App
        assertThat(first.label).isEqualTo("Camera")
        assertThat(first.accessState).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun onQueryChanged_guardedApp_preservesGuardedAccessState() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onQueryChanged("soc")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertThat(results).hasSize(1)
        val first = results[0].item as SearchItem.App
        assertThat(first.label).isEqualTo("Social Feed")
        assertThat(first.accessState).isInstanceOf(AppAccessState.Guarded::class.java)
    }

    @Test
    fun onQueryChanged_blockedApp_neverAppearsInResults() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onQueryChanged("arc")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertThat(results).isEmpty()
    }

    @Test
    fun onQueryChanged_commandPrefix_returnsActionCandidates() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onQueryChanged(":")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertThat(results).hasSize(1)
        val action = results[0].item as SearchItem.Action
        assertThat(action.label).isEqualTo("Threshold Settings")
    }

    @Test
    fun observePackageChanges_appAdded_appearsInSubsequentSearch() = runTest(testDispatcher) {
        advanceUntilIdle()
        val newComponent = ComponentKey("com.example.notes", "com.example.notes.MainActivity", 0L)
        fakePackageIndexSource.emitChange(
            PackageChangeEvent.Added(InstalledApp(newComponent, "Notes")),
        )
        advanceUntilIdle()

        viewModel.onQueryChanged("not")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertThat(results).hasSize(1)
        val first = results[0].item as SearchItem.App
        assertThat(first.label).isEqualTo("Notes")
    }

    @Test
    fun observePackageChanges_appRemoved_disappearsFromSearch() = runTest(testDispatcher) {
        advanceUntilIdle()
        fakePackageIndexSource.emitChange(
            PackageChangeEvent.Removed(cameraComponent),
        )
        advanceUntilIdle()

        viewModel.onQueryChanged("cam")
        advanceUntilIdle()

        val results = viewModel.uiState.value.results
        assertThat(results).isEmpty()
    }

    @Test
    fun coldStartRace_queryTypedBeforeGetInstalledAppsCompletes_updatesResultsWhenScanFinishes() =
        runTest(testDispatcher) {
            val deferredApps = CompletableDeferred<List<InstalledApp>>()
            val delayedSource = object : PackageIndexSource {
                override suspend fun getInstalledApps(): List<InstalledApp> = deferredApps.await()
                override fun observePackageChanges(): Flow<PackageChangeEvent> = MutableSharedFlow()
            }

            val coldViewModel = SearchViewModel(
                packageIndexSource = delayedSource,
                searchEngine = searchEngine,
                defaultDialerPackage = "com.google.android.dialer",
                initialProfile = profile,
                ioDispatcher = testDispatcher,
            )

            // User types "cam" on cold start before getInstalledApps() finishes
            coldViewModel.onQueryChanged("cam")
            assertThat(coldViewModel.uiState.value.results).isEmpty()

            // Background scan completes
            deferredApps.complete(
                listOf(
                    InstalledApp(cameraComponent, "Camera"),
                ),
            )
            advanceUntilIdle()

            // Results must automatically populate without requiring another keystroke
            val results = coldViewModel.uiState.value.results
            assertThat(results).hasSize(1)
            assertThat((results.first().item as SearchItem.App).label).isEqualTo("Camera")
        }

    @Test
    fun searchWithAutoLaunch_populatesAutoLaunchForDirectAndRequiresExactMatchForGuarded() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Direct app ("Camera") uniquely matched with >= 2 chars -> autoLaunchCandidate populated
        viewModel.onQueryChanged("cam")
        assertThat(viewModel.uiState.value.autoLaunchCandidate).isNotNull()
        assertThat((viewModel.uiState.value.autoLaunchCandidate as SearchItem.App).component)
            .isEqualTo(cameraComponent)

        // Guarded app ("Social Feed") uniquely matched on prefix "soc" -> autoLaunchCandidate is null
        viewModel.onQueryChanged("soc")
        assertThat(viewModel.uiState.value.results).hasSize(1)
        assertThat(viewModel.uiState.value.autoLaunchCandidate).isNull()

        // Guarded app matched with full exact label -> autoLaunchCandidate populated
        viewModel.onQueryChanged("Social Feed")
        assertThat(viewModel.uiState.value.autoLaunchCandidate).isNotNull()
        assertThat((viewModel.uiState.value.autoLaunchCandidate as SearchItem.App).component)
            .isEqualTo(socialComponent)
    }

    @Test
    fun evaluateLaunch_enforcesIntentGatekeeperAndUniversalDialerSafetyInvariant() = runTest(testDispatcher) {
        val pixelDialer = ComponentKey("com.google.android.dialer", "com.google.android.dialer.DialtactsActivity", 0L)
        val bedtimeLockoutProfile = Profile(
            id = "bedtime",
            name = "Bedtime",
            defaultAccess = AppAccessState.Blocked,
            appRules = mapOf(
                pixelDialer to AppAccessState.Blocked,
                socialComponent to AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
            ),
        )
        val pixelViewModel = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            searchEngine = searchEngine,
            defaultDialerPackage = "com.google.android.dialer",
            initialProfile = bedtimeLockoutProfile,
            ioDispatcher = testDispatcher,
        )

        // Dialer is always LaunchResult.Launched even when profile defaultAccess is Blocked
        assertThat(
            pixelViewModel.evaluateLaunch(SearchItem.App(pixelDialer, "Phone", AppAccessState.Direct)),
        ).isEqualTo(LaunchResult.Launched)

        // Guarded app requires friction
        assertThat(
            pixelViewModel.evaluateLaunch(
                SearchItem.App(
                    socialComponent,
                    "Social Feed",
                    AppAccessState.Guarded(FrictionPolicy.TimedDelay(10)),
                ),
            ),
        ).isInstanceOf(LaunchResult.FrictionRequired::class.java)

        // Blocked app is rejected
        assertThat(
            pixelViewModel.evaluateLaunch(SearchItem.App(blockedComponent, "Arcade Games", AppAccessState.Blocked)),
        ).isEqualTo(LaunchResult.Blocked)
    }

    @Test
    fun typingQuery_targetSnapHapticFiresOnlyOnDecisiveCandidateLockIn() = runTest(testDispatcher) {
        val haptic = TestHapticEngine()
        val viewModel = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            hapticEngine = haptic,
            searchEngine = searchEngine,
            ioDispatcher = testDispatcher,
        )

        viewModel.ensureIndexLoaded()
        advanceUntilIdle()

        // 1. Initial ambiguous letter 'C' matches Calculator, Camera, etc. -> NO snap
        viewModel.onQueryChanged("C")
        assertThat(viewModel.uiState.value.results.size).isGreaterThan(1)
        assertThat(viewModel.uiState.value.autoLaunchCandidate).isNull()
        assertThat(haptic.targetSnapCount).isEqualTo(0)

        // 2. Typing 'Ca' still matches multiple apps -> NO snap
        viewModel.onQueryChanged("Ca")
        assertThat(viewModel.uiState.value.results.size).isGreaterThan(1)
        assertThat(viewModel.uiState.value.autoLaunchCandidate).isNull()
        assertThat(haptic.targetSnapCount).isEqualTo(0)

        // 3. Typing 'Calc' isolates Calculator as single decisive match -> fires targetSnap once
        viewModel.onQueryChanged("Calc")
        assertThat(viewModel.uiState.value.results).hasSize(1)
        assertThat(viewModel.uiState.value.autoLaunchCandidate?.label).isEqualTo("Calculator")
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        // 4. Continuing to type 'Calcu' keeps Calculator as decisive candidate -> NO additional snap
        viewModel.onQueryChanged("Calcu")
        assertThat(viewModel.uiState.value.results).hasSize(1)
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        // 5. Backspacing to ambiguous 'Ca' disengages decisive lock -> NO new snap
        viewModel.onQueryChanged("Ca")
        assertThat(viewModel.uiState.value.results.size).isGreaterThan(1)
        assertThat(viewModel.uiState.value.autoLaunchCandidate).isNull()
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        // 6. Clearing query resets state
        viewModel.dismissSearch()
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        // 7. Single-character query 'P' (Phone) is unique but length 1 prefix -> NO premature snap
        viewModel.onQueryChanged("P")
        assertThat(viewModel.uiState.value.autoLaunchCandidate).isNull()
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        // 8. Query 'Ph' reaches decisive threshold for Phone -> fires targetSnap
        viewModel.onQueryChanged("Ph")
        assertThat(viewModel.uiState.value.autoLaunchCandidate?.label).isEqualTo("Phone")
        assertThat(haptic.targetSnapCount).isEqualTo(2)
    }

    @Test
    fun settingsRepository_propagatesQuickActionsAndGeneralSettings() = runTest(testDispatcher) {
        val fakeRepo = com.garan.threshold.ui.settings.FakeSettingsRepository(
            com.garan.threshold.core.model.ThresholdSettings(
                general = com.garan.threshold.core.model.GeneralSettings(
                    showUnlocks = false,
                    requireFullTypingOfGuardedApps = true,
                ),
                baseProfile = com.garan.threshold.core.model.BaseProfileSettings(
                    quickActions = listOf(cameraComponent, socialComponent),
                    appAssignments = mapOf(socialComponent to com.garan.threshold.core.model.AppAssignment.Blocked),
                ),
            ),
        )

        val testHaptic = TestHapticEngine()
        val vm = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            hapticEngine = testHaptic,
            settingsRepository = fakeRepo,
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.generalSettings.showUnlocks).isFalse()
        assertThat(state.generalSettings.requireFullTypingOfGuardedApps).isTrue()
        assertThat(state.quickActions).hasSize(2)
        assertThat(state.quickActions[0].component).isEqualTo(cameraComponent)
        assertThat(state.quickActions[0].accessState).isEqualTo(AppAccessState.Direct)
        assertThat(state.quickActions[1].component).isEqualTo(socialComponent)
        assertThat(state.quickActions[1].accessState).isEqualTo(AppAccessState.Blocked)
    }

    @Test
    fun requireFullTypingOfGuardedApps_blocksFrictionUntilFullNameTyped() = runTest(testDispatcher) {
        val testHaptic = TestHapticEngine()
        val fakeRepo = com.garan.threshold.ui.settings.FakeSettingsRepository(
            com.garan.threshold.core.model.ThresholdSettings(
                general = com.garan.threshold.core.model.GeneralSettings(requireFullTypingOfGuardedApps = true),
                baseProfile = com.garan.threshold.core.model.BaseProfileSettings(
                    appAssignments = mapOf(socialComponent to com.garan.threshold.core.model.AppAssignment.Guarded()),
                ),
            ),
        )

        val vm = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            hapticEngine = testHaptic,
            settingsRepository = fakeRepo,
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        // Partial query: "Soc" (not "Social Feed")
        vm.onQueryChanged("Soc")
        advanceUntilIdle()

        val item = SearchItem.App(socialComponent, "Social Feed", AppAccessState.Guarded(FrictionPolicy.TypedIntention))
        vm.openFriction(item, FrictionPolicy.TypedIntention)

        // Must NOT open friction, must trigger resistance detent
        assertThat(vm.uiState.value.activeFriction).isNull()
        assertThat(testHaptic.resistanceDetentCount).isEqualTo(1)

        // Now type exact full name: "Social Feed"
        vm.onQueryChanged("Social Feed")
        advanceUntilIdle()
        vm.openFriction(item, FrictionPolicy.TypedIntention)

        // Opens friction successfully
        assertThat(vm.uiState.value.activeFriction).isNotNull()
    }

    @Test
    fun launchQuickAction_triggersAppropriateResponse() = runTest(testDispatcher) {
        val testHaptic = TestHapticEngine()
        val vm = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            hapticEngine = testHaptic,
            ioDispatcher = testDispatcher,
        )

        var launched = false
        val directItem = QuickActionUiItem(1, cameraComponent, "Camera", AppAccessState.Direct)
        vm.launchQuickAction(directItem) { launched = true }
        assertThat(launched).isTrue()

        val blockedItem = QuickActionUiItem(2, socialComponent, "Social", AppAccessState.Blocked)
        vm.launchQuickAction(blockedItem) { launched = false }
        assertThat(testHaptic.resistanceDetentCount).isEqualTo(1)

        val guardedItem =
            QuickActionUiItem(
                3,
                calculatorComponent,
                "Calculator",
                AppAccessState.Guarded(FrictionPolicy.TypedIntention),
            )
        vm.launchQuickAction(guardedItem) {}
        assertThat(vm.uiState.value.activeFriction).isNotNull()
        assertThat(vm.uiState.value.activeFriction?.app?.component).isEqualTo(calculatorComponent)
    }

    @Test
    fun evaluateLaunch_withRandomFriction_resolvesDynamicFrictionPoliciesAcrossInvocations() = runTest(testDispatcher) {
        val fakeRepo = com.garan.threshold.ui.settings.FakeSettingsRepository(
            com.garan.threshold.core.model.ThresholdSettings(
                baseProfile = com.garan.threshold.core.model.BaseProfileSettings(
                    defaultFriction = com.garan.threshold.core.model.FrictionChoice.RANDOM,
                    appAssignments = mapOf(
                        socialComponent to com.garan.threshold.core.model.AppAssignment.Guarded(
                            frictionOverride = com.garan.threshold.core.model.FrictionChoice.RANDOM,
                        ),
                    ),
                ),
            ),
        )

        val vm = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            settingsRepository = fakeRepo,
            ioDispatcher = testDispatcher,
            random = kotlin.random.Random(0),
        )
        advanceUntilIdle()

        val item = SearchItem.App(socialComponent, "Social Feed", AppAccessState.Guarded(FrictionPolicy.TypedIntention))

        val policies = (0 until 10).map {
            val result = vm.evaluateLaunch(item)
            assertThat(result).isInstanceOf(LaunchResult.FrictionRequired::class.java)
            (result as LaunchResult.FrictionRequired).policy
        }

        val uniquePolicyTypes = policies.map { it::class }.toSet()
        assertThat(uniquePolicyTypes.size).isAtLeast(2)
    }

    @Test
    fun launchQuickAction_withRandomFriction_resolvesDynamicFrictionPoliciesAcrossInvocations() =
        runTest(testDispatcher) {
            val fakeRepo = com.garan.threshold.ui.settings.FakeSettingsRepository(
                com.garan.threshold.core.model.ThresholdSettings(
                    baseProfile = com.garan.threshold.core.model.BaseProfileSettings(
                        defaultFriction = com.garan.threshold.core.model.FrictionChoice.RANDOM,
                        appAssignments = mapOf(
                            socialComponent to com.garan.threshold.core.model.AppAssignment.Guarded(
                                frictionOverride = com.garan.threshold.core.model.FrictionChoice.RANDOM,
                            ),
                        ),
                    ),
                ),
            )

            val vm = SearchViewModel(
                packageIndexSource = fakePackageIndexSource,
                settingsRepository = fakeRepo,
                ioDispatcher = testDispatcher,
                random = kotlin.random.Random(0),
            )
            advanceUntilIdle()

            val quickItem = QuickActionUiItem(
                slotNumber = 1,
                component = socialComponent,
                label = "Social Feed",
                accessState = AppAccessState.Guarded(FrictionPolicy.TypedIntention),
            )

            val policies = (0 until 10).map {
                vm.launchQuickAction(quickItem) {}
                val active = vm.uiState.value.activeFriction
                assertThat(active).isNotNull()
                vm.dismissFriction()
                active!!.policy
            }

            val uniquePolicyTypes = policies.map { it::class }.toSet()
            assertThat(uniquePolicyTypes.size).isAtLeast(2)
        }

    @Test
    fun customScheduleProfile_reconcilesWorkingSetAcrossScheduleBoundariesAndSyncsAlarmScheduler() =
        runTest(testDispatcher) {
            var currentNow = com.garan.threshold.core.port.LocalDayTime(
                dayOfWeek = com.garan.threshold.core.model.DayOfWeek.MONDAY,
                timeOfDay = com.garan.threshold.core.model.TimeOfDay(8, 30),
            )
            var syncedProfilesCount = 0

            val schedule = com.garan.threshold.core.model.ScheduleTrigger(
                days = setOf(com.garan.threshold.core.model.DayOfWeek.MONDAY),
                startTime = com.garan.threshold.core.model.TimeOfDay(9, 0),
                endTime = com.garan.threshold.core.model.TimeOfDay(17, 0),
            )
            val deepWorkProfile = com.garan.threshold.core.model.CustomProfile(
                id = "deep-work",
                name = "Deep Work",
                priority = 0,
                enabled = true,
                trigger = com.garan.threshold.core.model.ProfileTrigger.Schedule(schedule),
                appAssignments = mapOf(
                    socialComponent to com.garan.threshold.core.model.AppAssignment.Blocked,
                ),
            )

            val fakeRepo = com.garan.threshold.ui.settings.FakeSettingsRepository(
                com.garan.threshold.core.model.ThresholdSettings(
                    baseProfile = com.garan.threshold.core.model.BaseProfileSettings(
                        quickActions = listOf(socialComponent),
                        appAssignments = mapOf(
                            socialComponent to com.garan.threshold.core.model.AppAssignment.Standard,
                        ),
                    ),
                    customProfiles = listOf(deepWorkProfile),
                ),
            )

            val fakeAlarmScheduler = object : com.garan.threshold.core.port.ScheduleAlarmScheduler {
                override fun canScheduleExactAlarms(): Boolean = true
                override fun syncNextScheduleAlarm(profiles: List<com.garan.threshold.core.model.CustomProfile>) {
                    syncedProfilesCount = profiles.size
                }
            }

            val vm = SearchViewModel(
                packageIndexSource = fakePackageIndexSource,
                settingsRepository = fakeRepo,
                clockSource = { currentNow },
                scheduleAlarmScheduler = fakeAlarmScheduler,
                ioDispatcher = testDispatcher,
            ).apply {
                ensureIndexLoaded()
            }
            advanceUntilIdle()

            // 1. At 08:30 Monday (before 09:00 window), Deep Work is inactive -> socialComponent is Direct
            assertThat(syncedProfilesCount).isEqualTo(1)
            assertThat(vm.uiState.value.activeProfiles).isEmpty()
            assertThat(vm.uiState.value.quickActions.first().accessState).isEqualTo(AppAccessState.Direct)

            // 2. Clock crosses into 09:00 Monday -> refreshScheduleEvaluation() activates Deep Work
            currentNow = com.garan.threshold.core.port.LocalDayTime(
                dayOfWeek = com.garan.threshold.core.model.DayOfWeek.MONDAY,
                timeOfDay = com.garan.threshold.core.model.TimeOfDay(9, 0),
            )
            vm.refreshScheduleEvaluation()
            advanceUntilIdle()

            assertThat(vm.uiState.value.activeProfiles).containsExactly(deepWorkProfile)
            assertThat(vm.uiState.value.quickActions.first().accessState).isEqualTo(AppAccessState.Blocked)

            // Searching for "Social" while blocked by active profile returns 0 results (Intent Gatekeeper)
            vm.onQueryChanged("Social")
            advanceUntilIdle()
            assertThat(vm.uiState.value.results).isEmpty()

            // 3. Clock crosses 17:00 Monday -> Deep Work deactivates -> socialComponent is Direct again
            currentNow = com.garan.threshold.core.port.LocalDayTime(
                dayOfWeek = com.garan.threshold.core.model.DayOfWeek.MONDAY,
                timeOfDay = com.garan.threshold.core.model.TimeOfDay(17, 0),
            )
            vm.refreshScheduleEvaluation()
            advanceUntilIdle()

            assertThat(vm.uiState.value.activeProfiles).isEmpty()
            assertThat(vm.uiState.value.quickActions.first().accessState).isEqualTo(AppAccessState.Direct)
            assertThat(vm.uiState.value.results).hasSize(1)
        }

    @Test
    fun capabilityIssues_reportsMissingCapabilitiesForEnabledProfilesAndClearsWhenGranted() = runTest(testDispatcher) {
        val enabledProfile = com.garan.threshold.core.model.CustomProfile(
            id = "work",
            name = "Work Focus",
            enabled = true,
            priority = 0,
        )
        val disabledProfile = com.garan.threshold.core.model.CustomProfile(
            id = "rest",
            name = "Evening Rest",
            enabled = false,
            priority = 1,
        )
        val fakeRepo = com.garan.threshold.ui.settings.FakeSettingsRepository(
            com.garan.threshold.core.model.ThresholdSettings(
                customProfiles = listOf(enabledProfile, disabledProfile),
            ),
        )

        var exactAlarmGranted = false
        val vm = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            settingsRepository = fakeRepo,
            capabilityChecker = { exactAlarmGranted },
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        // Initially exactAlarmGranted == false -> enabledProfile is flagged in capabilityIssues
        assertThat(vm.uiState.value.capabilityIssues).containsExactly(
            com.garan.threshold.core.model.ProfileCapabilityIssue(
                profile = enabledProfile,
                missingCapabilities = setOf(com.garan.threshold.core.model.ProfileCapability.EXACT_ALARM),
            ),
        )

        // User grants permission and returns to app -> refreshScheduleEvaluation() clears capabilityIssues
        exactAlarmGranted = true
        vm.refreshScheduleEvaluation()
        advanceUntilIdle()

        assertThat(vm.uiState.value.capabilityIssues).isEmpty()
    }

    @Test
    fun customGeoProfile_reconcilesAcrossLocationUpdatesAndPreservesBoundaryHysteresis() = runTest(testDispatcher) {
        val center = com.garan.threshold.core.model.LocationFix(51.5074, -0.1278)
        val officeProfile = com.garan.threshold.core.model.CustomProfile(
            id = "office",
            name = "Office",
            enabled = true,
            priority = 0,
            trigger = com.garan.threshold.core.model.ProfileTrigger.Geo(
                com.garan.threshold.core.model.GeoTrigger(
                    latitude = center.latitude,
                    longitude = center.longitude,
                    radiusMeters = 250,
                    mode = com.garan.threshold.core.model.GeoTriggerMode.INSIDE,
                ),
            ),
            appAssignments = mapOf(
                socialComponent to com.garan.threshold.core.model.AppAssignment.Blocked,
            ),
        )

        val fakeRepo = com.garan.threshold.ui.settings.FakeSettingsRepository(
            com.garan.threshold.core.model.ThresholdSettings(
                baseProfile = com.garan.threshold.core.model.BaseProfileSettings(
                    quickActions = listOf(socialComponent),
                    appAssignments = mapOf(
                        socialComponent to com.garan.threshold.core.model.AppAssignment.Standard,
                    ),
                ),
                customProfiles = listOf(officeProfile),
            ),
        )

        val locationFlow = kotlinx.coroutines.flow.MutableStateFlow<com.garan.threshold.core.model.LocationFix?>(null)
        val fakeLocationSource = object : com.garan.threshold.core.port.LocationSource {
            override val currentLocation = locationFlow
            override suspend fun refreshLocation() = locationFlow.value
        }

        val vm = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            settingsRepository = fakeRepo,
            locationSource = fakeLocationSource,
            ioDispatcher = testDispatcher,
        ).apply {
            ensureIndexLoaded()
        }
        advanceUntilIdle()

        // 1. Null location -> inactive
        assertThat(vm.uiState.value.activeProfiles).isEmpty()
        assertThat(vm.uiState.value.quickActions.first().accessState).isEqualTo(AppAccessState.Direct)

        // 2. Move inside zone (100m away) -> Office activates, social is Blocked
        locationFlow.value = com.garan.threshold.core.model.LocationFix(
            latitude = center.latitude + (100.0 / 111_194.9266),
            longitude = center.longitude,
        )
        advanceUntilIdle()
        assertThat(vm.uiState.value.activeProfiles).containsExactly(officeProfile)
        assertThat(vm.uiState.value.quickActions.first().accessState).isEqualTo(AppAccessState.Blocked)

        // 3. Drift to 265m (within 250m + 25m hysteresis buffer) -> stays active
        locationFlow.value = com.garan.threshold.core.model.LocationFix(
            latitude = center.latitude + (265.0 / 111_194.9266),
            longitude = center.longitude,
        )
        advanceUntilIdle()
        assertThat(vm.uiState.value.activeProfiles).containsExactly(officeProfile)

        // 4. Move to 300m (beyond 275m buffer) -> deactivates
        locationFlow.value = com.garan.threshold.core.model.LocationFix(
            latitude = center.latitude + (300.0 / 111_194.9266),
            longitude = center.longitude,
        )
        advanceUntilIdle()
        assertThat(vm.uiState.value.activeProfiles).isEmpty()
        assertThat(vm.uiState.value.quickActions.first().accessState).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun reusableTriggersWithAndOrRules_reconcilesAcrossClockLocationAndLiveTriggerDeletion() = runTest(testDispatcher) {
        val center = com.garan.threshold.core.model.LocationFix(51.5074, -0.1278)
        val insideOffice = com.garan.threshold.core.model.LocationFix(
            latitude = center.latitude + (50.0 / 111_194.9266),
            longitude = center.longitude,
        )
        val outsideOffice = com.garan.threshold.core.model.LocationFix(
            latitude = center.latitude + (500.0 / 111_194.9266),
            longitude = center.longitude,
        )

        val workHours = com.garan.threshold.core.model.SavedTrigger(
            id = "t-work",
            name = "Work Hours",
            trigger = com.garan.threshold.core.model.ProfileTrigger.Schedule(
                com.garan.threshold.core.model.ScheduleTrigger(
                    days = setOf(com.garan.threshold.core.model.DayOfWeek.MONDAY),
                    startTime = com.garan.threshold.core.model.TimeOfDay(9, 0),
                    endTime = com.garan.threshold.core.model.TimeOfDay(17, 0),
                ),
            ),
        )
        val atOffice = com.garan.threshold.core.model.SavedTrigger(
            id = "t-office",
            name = "At Office",
            trigger = com.garan.threshold.core.model.ProfileTrigger.Geo(
                com.garan.threshold.core.model.GeoTrigger(
                    latitude = center.latitude,
                    longitude = center.longitude,
                    radiusMeters = 250,
                    mode = com.garan.threshold.core.model.GeoTriggerMode.INSIDE,
                ),
            ),
        )
        val lateNight = com.garan.threshold.core.model.SavedTrigger(
            id = "t-night",
            name = "Late Night",
            trigger = com.garan.threshold.core.model.ProfileTrigger.Schedule(
                com.garan.threshold.core.model.ScheduleTrigger(
                    days = setOf(com.garan.threshold.core.model.DayOfWeek.MONDAY),
                    startTime = com.garan.threshold.core.model.TimeOfDay(22, 0),
                    endTime = com.garan.threshold.core.model.TimeOfDay(23, 30),
                ),
            ),
        )

        // Activates when (Work Hours AND At Office) OR Late Night
        val focusProfile = com.garan.threshold.core.model.CustomProfile(
            id = "focus",
            name = "Focus",
            enabled = true,
            priority = 0,
            trigger = null,
            activationRules = listOf(
                com.garan.threshold.core.model.TriggerRule(listOf("t-work", "t-office")),
                com.garan.threshold.core.model.TriggerRule(listOf("t-night")),
            ),
            appAssignments = mapOf(
                socialComponent to com.garan.threshold.core.model.AppAssignment.Blocked,
            ),
        )

        val fakeRepo = com.garan.threshold.ui.settings.FakeSettingsRepository(
            com.garan.threshold.core.model.ThresholdSettings(
                baseProfile = com.garan.threshold.core.model.BaseProfileSettings(
                    quickActions = listOf(socialComponent),
                    appAssignments = mapOf(
                        socialComponent to com.garan.threshold.core.model.AppAssignment.Standard,
                    ),
                ),
                customProfiles = listOf(focusProfile),
                savedTriggers = listOf(workHours, atOffice, lateNight),
            ),
        )

        var currentDayTime = com.garan.threshold.core.port.LocalDayTime(
            com.garan.threshold.core.model.DayOfWeek.MONDAY,
            com.garan.threshold.core.model.TimeOfDay(10, 0),
        )
        val locationFlow =
            kotlinx.coroutines.flow.MutableStateFlow<com.garan.threshold.core.model.LocationFix?>(outsideOffice)
        val fakeLocationSource = object : com.garan.threshold.core.port.LocationSource {
            override val currentLocation = locationFlow
            override suspend fun refreshLocation() = locationFlow.value
        }

        val vm = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            settingsRepository = fakeRepo,
            clockSource = { currentDayTime },
            locationSource = fakeLocationSource,
            ioDispatcher = testDispatcher,
        ).apply {
            ensureIndexLoaded()
        }
        advanceUntilIdle()

        // 1. Monday 10:00, outside office -> AND rule not satisfied -> inactive
        assertThat(vm.uiState.value.activeProfiles).isEmpty()
        assertThat(vm.uiState.value.quickActions.first().accessState).isEqualTo(AppAccessState.Direct)

        // 2. Monday 10:00, move inside office -> (Work Hours AND At Office) satisfied -> active
        locationFlow.value = insideOffice
        advanceUntilIdle()
        assertThat(vm.uiState.value.activeProfiles).containsExactly(focusProfile)
        assertThat(vm.uiState.value.quickActions.first().accessState).isEqualTo(AppAccessState.Blocked)

        // 3. Monday 18:00 (after Work Hours), still inside office -> AND rule broken -> inactive
        currentDayTime = com.garan.threshold.core.port.LocalDayTime(
            com.garan.threshold.core.model.DayOfWeek.MONDAY,
            com.garan.threshold.core.model.TimeOfDay(18, 0),
        )
        vm.refreshScheduleEvaluation()
        advanceUntilIdle()
        assertThat(vm.uiState.value.activeProfiles).isEmpty()

        // 4. Monday 22:30, outside office -> OR rule (Late Night) satisfied -> active
        locationFlow.value = outsideOffice
        currentDayTime = com.garan.threshold.core.port.LocalDayTime(
            com.garan.threshold.core.model.DayOfWeek.MONDAY,
            com.garan.threshold.core.model.TimeOfDay(22, 30),
        )
        vm.refreshScheduleEvaluation()
        advanceUntilIdle()
        assertThat(vm.uiState.value.activeProfiles).containsExactly(focusProfile)

        // 5. Delete "t-night" trigger while active at 22:30 -> strips OR rule and immediately deactivates
        fakeRepo.deleteTrigger("t-night")
        advanceUntilIdle()
        assertThat(vm.uiState.value.activeProfiles).isEmpty()
        assertThat(vm.uiState.value.quickActions.first().accessState).isEqualTo(AppAccessState.Direct)
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

    private class FakePackageIndexSource(initialApps: List<InstalledApp> = emptyList()) : PackageIndexSource {
        private val apps = initialApps.toMutableList()
        private val changesFlow = MutableSharedFlow<PackageChangeEvent>()

        override suspend fun getInstalledApps(): List<InstalledApp> = apps.toList()

        override fun observePackageChanges(): Flow<PackageChangeEvent> = changesFlow

        suspend fun emitChange(event: PackageChangeEvent) {
            when (event) {
                is PackageChangeEvent.Added -> apps.add(event.app)
                is PackageChangeEvent.Removed -> apps.removeAll { it.component == event.component }
                is PackageChangeEvent.Changed -> {
                    apps.removeAll { it.component == event.component }
                    apps.add(event.app)
                }
            }
            changesFlow.emit(event)
        }
    }
}

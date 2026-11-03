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
    private val socialComponent = ComponentKey("com.example.social", "com.example.social.MainActivity", 0L)
    private val blockedComponent = ComponentKey("com.example.games", "com.example.games.MainActivity", 0L)

    private val fakePackageIndexSource = FakePackageIndexSource(
        initialApps = listOf(
            InstalledApp(dialerComponent, "Phone"),
            InstalledApp(cameraComponent, "Camera"),
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
    fun typingQuery_targetSnapHapticFiresOnlyWhenTopCandidateChanges() = runTest(testDispatcher) {
        val haptic = TestHapticEngine()
        val viewModel = SearchViewModel(
            packageIndexSource = fakePackageIndexSource,
            hapticEngine = haptic,
            searchEngine = searchEngine,
            ioDispatcher = testDispatcher,
        )

        viewModel.ensureIndexLoaded()
        advanceUntilIdle()

        // 1. First letter 'C' snaps 'Camera' as top candidate -> targetSnap fires once
        viewModel.onQueryChanged("C")
        assertThat(viewModel.uiState.value.results.first().item.label).isEqualTo("Camera")
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        // 2. Typing 'Ca' still has 'Camera' as top candidate -> does NOT fire again (no double click)
        viewModel.onQueryChanged("Ca")
        assertThat(viewModel.uiState.value.results.first().item.label).isEqualTo("Camera")
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        // 3. Typing 'Cam' still has 'Camera' as top candidate -> still 1
        viewModel.onQueryChanged("Cam")
        assertThat(viewModel.uiState.value.results.first().item.label).isEqualTo("Camera")
        assertThat(haptic.targetSnapCount).isEqualTo(1)

        // 4. Changing query to 'P' snaps 'Phone' as new top candidate -> targetSnap fires
        viewModel.onQueryChanged("P")
        assertThat(viewModel.uiState.value.results.first().item.label).isEqualTo("Phone")
        assertThat(haptic.targetSnapCount).isEqualTo(2)

        // 5. Clearing query resets top candidate
        viewModel.dismissSearch()
        assertThat(haptic.targetSnapCount).isEqualTo(2)

        // 6. Typing 'P' again snaps 'Phone' from null -> fires targetSnap
        viewModel.onQueryChanged("P")
        assertThat(haptic.targetSnapCount).isEqualTo(3)
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

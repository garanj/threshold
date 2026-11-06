package com.garan.threshold.ui.settings

import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.InstalledApp
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
import com.google.common.truth.Truth.assertThat
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
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val dialerComponent = ComponentKey("com.android.dialer", "DialtactsActivity")
    private val chromeComponent = ComponentKey("com.android.chrome", "Main")
    private val clockComponent = ComponentKey("com.android.deskclock", "DeskClock")
    private val socialComponent = ComponentKey("com.instagram.android", "MainActivity")

    private val fakePackageIndexSource = object : PackageIndexSource {
        override suspend fun getInstalledApps(): List<InstalledApp> = listOf(
            InstalledApp(dialerComponent, "Phone"),
            InstalledApp(chromeComponent, "Chrome"),
            InstalledApp(clockComponent, "Clock"),
            InstalledApp(socialComponent, "Instagram"),
        )

        override fun observePackageChanges(): Flow<PackageChangeEvent> = MutableSharedFlow()
    }

    private lateinit var fakeRepository: FakeSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeSettingsRepository()
        viewModel = SettingsViewModel(
            packageIndexSource = fakePackageIndexSource,
            settingsRepository = fakeRepository,
            dialerPackageResolver = { "com.android.dialer" },
            ioDispatcher = testDispatcher,
        )
        viewModel.ensureLoaded()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsSettingsAndExcludesDialerFromAppList() = runTest(testDispatcher) {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.general.showUnlocks).isTrue()
        assertThat(state.general.showRest).isTrue()
        assertThat(state.general.requireFullTypingOfGuardedApps).isFalse()
        assertThat(state.baseProfile.defaultFriction).isEqualTo(FrictionChoice.TYPED_INTENTION)

        // Dialer MUST be filtered out of available configurable apps
        val availableAppKeys = state.availableApps.map { it.component }
        assertThat(availableAppKeys).doesNotContain(dialerComponent)
        assertThat(availableAppKeys).containsExactly(chromeComponent, clockComponent, socialComponent)
    }

    @Test
    fun toggleGeneralSettings_persistsUpdates() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.updateShowUnlocks(false)
        viewModel.updateShowRest(false)
        viewModel.updateRequireFullTyping(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.general.showUnlocks).isFalse()
        assertThat(state.general.showRest).isFalse()
        assertThat(state.general.requireFullTypingOfGuardedApps).isTrue()
    }

    @Test
    fun updateDefaultFriction_persistsChoice() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.updateDefaultFriction(FrictionChoice.RANDOM)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.baseProfile.defaultFriction).isEqualTo(FrictionChoice.RANDOM)
    }

    @Test
    fun quickActions_addAndRemove_enforcesMax7Slots() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.addQuickAction(chromeComponent)
        viewModel.addQuickAction(clockComponent)
        advanceUntilIdle()

        assertThat(
            viewModel.uiState.value.baseProfile.quickActions,
        ).containsExactly(chromeComponent, clockComponent).inOrder()

        viewModel.removeQuickAction(chromeComponent)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.baseProfile.quickActions).containsExactly(clockComponent)
    }

    @Test
    fun saveAppAssignments_updatesGuardedAndBlockedLists() = runTest(testDispatcher) {
        advanceUntilIdle()

        val newAssignments = mapOf(
            chromeComponent to AppAssignment.Guarded(FrictionChoice.CARET_TRACING),
            socialComponent to AppAssignment.Blocked,
        )

        viewModel.saveAppAssignments(newAssignments)
        advanceUntilIdle()

        val base = viewModel.uiState.value.baseProfile
        assertThat(base.appAssignments[chromeComponent]).isEqualTo(AppAssignment.Guarded(FrictionChoice.CARET_TRACING))
        assertThat(base.appAssignments[socialComponent]).isEqualTo(AppAssignment.Blocked)
        assertThat(base.appAssignments[clockComponent]).isNull() // unassigned = Standard
    }
}

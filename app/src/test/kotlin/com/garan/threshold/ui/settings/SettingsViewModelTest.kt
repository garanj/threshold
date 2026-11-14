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

    @Test
    fun customProfiles_saveDeleteAndReorder_updatesUiState() = runTest(testDispatcher) {
        advanceUntilIdle()

        val profile1 = com.garan.threshold.core.model.CustomProfile(id = "work", name = "Work", priority = 0)
        val profile2 = com.garan.threshold.core.model.CustomProfile(id = "bedtime", name = "Bedtime", priority = 1)

        viewModel.saveCustomProfile(profile1)
        viewModel.saveCustomProfile(profile2)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.customProfiles).hasSize(2)
        assertThat(viewModel.uiState.value.customProfiles[0].name).isEqualTo("Work")
        assertThat(viewModel.uiState.value.customProfiles[1].name).isEqualTo("Bedtime")

        // Reorder
        viewModel.reorderCustomProfiles(listOf(profile2, profile1))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.customProfiles[0].name).isEqualTo("Bedtime")
        assertThat(viewModel.uiState.value.customProfiles[0].priority).isEqualTo(0)
        assertThat(viewModel.uiState.value.customProfiles[1].name).isEqualTo("Work")
        assertThat(viewModel.uiState.value.customProfiles[1].priority).isEqualTo(1)

        // Delete
        viewModel.deleteCustomProfile("work")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.customProfiles.map { it.id }).containsExactly("bedtime")
    }

    @Test
    fun saveCustomProfileAppAssignments_updatesAssignmentsForTargetProfile() = runTest(testDispatcher) {
        advanceUntilIdle()

        val profile = com.garan.threshold.core.model.CustomProfile(id = "work", name = "Work", priority = 0)
        viewModel.saveCustomProfile(profile)
        advanceUntilIdle()

        val overrides = mapOf(socialComponent to AppAssignment.Blocked)
        viewModel.saveCustomProfileAppAssignments("work", overrides)
        advanceUntilIdle()

        val saved = viewModel.uiState.value.customProfiles.first { it.id == "work" }
        assertThat(saved.appAssignments[socialComponent]).isEqualTo(AppAssignment.Blocked)
    }

    @Test
    fun savedTriggers_saveAndDelete_updatesStateAndStripsFromProfiles() = runTest(testDispatcher) {
        advanceUntilIdle()

        val trigger = com.garan.threshold.core.model.SavedTrigger(
            id = "t-work",
            name = "Work Hours",
            trigger = com.garan.threshold.core.model.ProfileTrigger.Schedule(),
        )
        viewModel.saveTrigger(trigger)
        val profile = com.garan.threshold.core.model.CustomProfile(
            id = "work",
            name = "Work",
            enabled = true,
            priority = 0,
            activationRules = listOf(com.garan.threshold.core.model.TriggerRule(listOf("t-work"))),
            trigger = null,
        )
        viewModel.saveCustomProfile(profile)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.savedTriggers).containsExactly(trigger)
        assertThat(viewModel.uiState.value.customProfiles.single().enabled).isTrue()

        viewModel.deleteTrigger("t-work")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.savedTriggers).isEmpty()
        val updatedProfile = viewModel.uiState.value.customProfiles.single()
        assertThat(updatedProfile.activationRules).isEmpty()
        assertThat(updatedProfile.enabled).isFalse()
    }

    @Test
    fun releaseLoadedApps_clearsAvailableAppsAndReloadsOnSubsequentEnsureLoaded() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.availableApps).hasSize(3)
        assertThat(viewModel.uiState.value.isLoading).isFalse()

        viewModel.releaseLoadedApps()
        assertThat(viewModel.uiState.value.availableApps).isEmpty()
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        viewModel.ensureLoaded()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.availableApps).hasSize(3)
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }
}

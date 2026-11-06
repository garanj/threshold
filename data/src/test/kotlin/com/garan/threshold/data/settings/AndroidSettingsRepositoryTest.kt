package com.garan.threshold.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.GeneralSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: AndroidSettingsRepository

    private val chromeKey = ComponentKey("com.android.chrome", "com.google.android.apps.chrome.Main")
    private val clockKey = ComponentKey("com.google.android.deskclock", "DeskClock")
    private val socialKey = ComponentKey("com.instagram.android", "MainActivity")

    @Before
    fun setUp() {
        val testFile = tmpFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile },
        )
        repository = AndroidSettingsRepository(dataStore, ioDispatcher = testDispatcher)
    }

    @Test
    fun defaultSettings_matchesInitialSpecifications() = runTest(testDispatcher) {
        val settings = repository.getSettings()

        assertThat(settings.general.showUnlocks).isTrue()
        assertThat(settings.general.showRest).isTrue()
        assertThat(settings.general.requireFullTypingOfGuardedApps).isFalse()
        assertThat(settings.general.hasSeenFirstRunHint).isFalse()

        assertThat(settings.baseProfile.defaultFriction).isEqualTo(FrictionChoice.TYPED_INTENTION)
        assertThat(settings.baseProfile.quickActions).isEmpty()
        assertThat(settings.baseProfile.appAssignments).isEmpty()
    }

    @Test
    fun updateGeneralSettings_persistsAndEmitsNewValues() = runTest(testDispatcher) {
        val updatedGeneral = GeneralSettings(
            showUnlocks = false,
            showRest = false,
            requireFullTypingOfGuardedApps = true,
            hasSeenFirstRunHint = true,
        )

        repository.updateGeneralSettings(updatedGeneral)

        val latest = repository.observeSettings().first()
        assertThat(latest.general).isEqualTo(updatedGeneral)
    }

    @Test
    fun updateBaseProfile_persistsAssignmentsQuickActionsAndFriction() = runTest(testDispatcher) {
        val updatedBase = BaseProfileSettings(
            defaultFriction = FrictionChoice.DRAG_SORT,
            quickActions = listOf(chromeKey, clockKey),
            appAssignments = mapOf(
                chromeKey to AppAssignment.Guarded(FrictionChoice.CARET_TRACING),
                clockKey to AppAssignment.Standard,
                socialKey to AppAssignment.Blocked,
            ),
        )

        repository.updateBaseProfile(updatedBase)

        val latest = repository.getSettings()
        assertThat(latest.baseProfile.defaultFriction).isEqualTo(FrictionChoice.DRAG_SORT)
        assertThat(latest.baseProfile.quickActions).containsExactly(chromeKey, clockKey).inOrder()
        assertThat(latest.baseProfile.appAssignments[chromeKey])
            .isEqualTo(AppAssignment.Guarded(FrictionChoice.CARET_TRACING))
        assertThat(latest.baseProfile.appAssignments[clockKey]).isEqualTo(AppAssignment.Standard)
        assertThat(latest.baseProfile.appAssignments[socialKey]).isEqualTo(AppAssignment.Blocked)
    }

    @Test
    fun setHasSeenFirstRunHint_updatesOnlyHintFlag() = runTest(testDispatcher) {
        repository.setHasSeenFirstRunHint(true)
        val settings = repository.getSettings()
        assertThat(settings.general.hasSeenFirstRunHint).isTrue()
        assertThat(settings.general.showUnlocks).isTrue()
    }

    @Test
    fun removeApps_removesFromBothQuickActionsAndAssignments() = runTest(testDispatcher) {
        val updatedBase = BaseProfileSettings(
            quickActions = listOf(chromeKey, socialKey),
            appAssignments = mapOf(
                chromeKey to AppAssignment.Guarded(null),
                socialKey to AppAssignment.Blocked,
            ),
        )
        repository.updateBaseProfile(updatedBase)

        // Simulate uninstall of Instagram
        repository.removeApps(setOf(socialKey))

        val result = repository.getSettings()
        assertThat(result.baseProfile.quickActions).containsExactly(chromeKey)
        assertThat(result.baseProfile.appAssignments).doesNotContainKey(socialKey)
        assertThat(result.baseProfile.appAssignments).containsKey(chromeKey)
    }
}

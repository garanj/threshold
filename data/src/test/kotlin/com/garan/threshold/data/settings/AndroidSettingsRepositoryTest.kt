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

    @Test
    fun customProfiles_persistedAndRetrievedCorrectly() = runTest(testDispatcher) {
        val profile = com.garan.threshold.core.model.CustomProfile(
            id = "deep-work",
            name = "Deep Work",
            enabled = true,
            priority = 0,
            frictionOverride = FrictionChoice.DRAG_SORT,
            trigger = com.garan.threshold.core.model.ProfileTrigger.Schedule(
                com.garan.threshold.core.model.ScheduleTrigger(
                    days = setOf(
                        com.garan.threshold.core.model.DayOfWeek.MONDAY,
                        com.garan.threshold.core.model.DayOfWeek.FRIDAY,
                    ),
                    startTime = com.garan.threshold.core.model.TimeOfDay(8, 30),
                    endTime = com.garan.threshold.core.model.TimeOfDay(16, 45),
                ),
            ),
            appAssignments = mapOf(
                socialKey to AppAssignment.Blocked,
                chromeKey to AppAssignment.Guarded(FrictionChoice.CARET_TRACING),
            ),
        )

        repository.saveCustomProfile(profile)

        val latest = repository.getSettings()
        assertThat(latest.customProfiles).hasSize(1)
        val loaded = latest.customProfiles.first()
        assertThat(loaded.id).isEqualTo("deep-work")
        assertThat(loaded.name).isEqualTo("Deep Work")
        assertThat(loaded.enabled).isTrue()
        assertThat(loaded.priority).isEqualTo(0)
        assertThat(loaded.frictionOverride).isEqualTo(FrictionChoice.DRAG_SORT)
        assertThat(loaded.appAssignments[socialKey]).isEqualTo(AppAssignment.Blocked)
        assertThat(loaded.appAssignments[chromeKey]).isEqualTo(AppAssignment.Guarded(FrictionChoice.CARET_TRACING))

        val schedule = (loaded.trigger as com.garan.threshold.core.model.ProfileTrigger.Schedule).schedule
        assertThat(schedule.days).containsExactly(
            com.garan.threshold.core.model.DayOfWeek.MONDAY,
            com.garan.threshold.core.model.DayOfWeek.FRIDAY,
        )
        assertThat(schedule.startTime).isEqualTo(com.garan.threshold.core.model.TimeOfDay(8, 30))
        assertThat(schedule.endTime).isEqualTo(com.garan.threshold.core.model.TimeOfDay(16, 45))
        assertThat(schedule.sameScheduleEveryDay).isTrue()
    }

    @Test
    fun customProfiles_perDaySchedulePersistedAndRetrievedCorrectly() = runTest(testDispatcher) {
        val profile = com.garan.threshold.core.model.CustomProfile(
            id = "flexible-schedule",
            name = "Flexible Schedule",
            enabled = true,
            priority = 0,
            trigger = com.garan.threshold.core.model.ProfileTrigger.Schedule(
                com.garan.threshold.core.model.ScheduleTrigger(
                    dailyWindows = mapOf(
                        com.garan.threshold.core.model.DayOfWeek.MONDAY to
                            com.garan.threshold.core.model.TimeWindow(
                                com.garan.threshold.core.model.TimeOfDay(19, 0),
                                com.garan.threshold.core.model.TimeOfDay(6, 0),
                            ),
                        com.garan.threshold.core.model.DayOfWeek.TUESDAY to
                            com.garan.threshold.core.model.TimeWindow(
                                com.garan.threshold.core.model.TimeOfDay(10, 0),
                                com.garan.threshold.core.model.TimeOfDay(11, 0),
                            ),
                    ),
                    sameScheduleEveryDay = false,
                ),
            ),
        )

        repository.saveCustomProfile(profile)

        val latest = repository.getSettings()
        val loaded = latest.customProfiles.first()
        val schedule = (loaded.trigger as com.garan.threshold.core.model.ProfileTrigger.Schedule).schedule
        assertThat(schedule.sameScheduleEveryDay).isFalse()
        assertThat(schedule.dailyWindows[com.garan.threshold.core.model.DayOfWeek.MONDAY]).isEqualTo(
            com.garan.threshold.core.model.TimeWindow(
                com.garan.threshold.core.model.TimeOfDay(19, 0),
                com.garan.threshold.core.model.TimeOfDay(6, 0),
            ),
        )
        assertThat(schedule.dailyWindows[com.garan.threshold.core.model.DayOfWeek.TUESDAY]).isEqualTo(
            com.garan.threshold.core.model.TimeWindow(
                com.garan.threshold.core.model.TimeOfDay(10, 0),
                com.garan.threshold.core.model.TimeOfDay(11, 0),
            ),
        )
    }

    @Test
    fun deleteCustomProfile_removesSpecifiedProfile() = runTest(testDispatcher) {
        val p1 = com.garan.threshold.core.model.CustomProfile(id = "p1", name = "P1", priority = 0)
        val p2 = com.garan.threshold.core.model.CustomProfile(id = "p2", name = "P2", priority = 1)
        repository.updateCustomProfiles(listOf(p1, p2))

        repository.deleteCustomProfile("p1")

        val latest = repository.getSettings()
        assertThat(latest.customProfiles.map { it.id }).containsExactly("p2")
    }

    @Test
    fun removeApps_prunesCustomProfilesAndDisablesWhenEmpty() = runTest(testDispatcher) {
        val workProfile = com.garan.threshold.core.model.CustomProfile(
            id = "work",
            name = "Work",
            enabled = true,
            appAssignments = mapOf(
                socialKey to AppAssignment.Blocked,
            ),
        )
        repository.saveCustomProfile(workProfile)

        // Uninstall socialKey -> workProfile has no other apps -> should auto-disable
        repository.removeApps(setOf(socialKey))

        val latest = repository.getSettings()
        val prunedProfile = latest.customProfiles.first { it.id == "work" }
        assertThat(prunedProfile.appAssignments).isEmpty()
        assertThat(prunedProfile.enabled).isFalse() // Auto-disable invariant verified
    }

    @Test
    fun customProfiles_geoTriggerPersistedAndRetrievedCorrectly() = runTest(testDispatcher) {
        val geoProfile = com.garan.threshold.core.model.CustomProfile(
            id = "away-home",
            name = "Away from Home",
            enabled = true,
            priority = 0,
            colorIndex = 3,
            trigger = com.garan.threshold.core.model.ProfileTrigger.Geo(
                com.garan.threshold.core.model.GeoTrigger(
                    latitude = 51.5074,
                    longitude = -0.1278,
                    radiusMeters = 500,
                    mode = com.garan.threshold.core.model.GeoTriggerMode.OUTSIDE,
                ),
            ),
            appAssignments = mapOf(socialKey to AppAssignment.Blocked),
        )

        repository.saveCustomProfile(geoProfile)

        val latest = repository.getSettings()
        assertThat(latest.customProfiles).hasSize(1)
        val loaded = latest.customProfiles.first()
        assertThat(loaded.id).isEqualTo("away-home")
        assertThat(loaded.name).isEqualTo("Away from Home")
        assertThat(loaded.colorIndex).isEqualTo(3)
        val geo = (loaded.trigger as com.garan.threshold.core.model.ProfileTrigger.Geo).geo
        assertThat(geo.latitude).isWithin(0.00001).of(51.5074)
        assertThat(geo.longitude).isWithin(0.00001).of(-0.1278)
        assertThat(geo.radiusMeters).isEqualTo(500)
        assertThat(geo.mode).isEqualTo(com.garan.threshold.core.model.GeoTriggerMode.OUTSIDE)
    }

    @Test
    fun saveTrigger_persistsAndUpdatesScheduleAndGeoTriggers() = runTest(testDispatcher) {
        val workHours = com.garan.threshold.core.model.SavedTrigger(
            id = "t-work-hours",
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
                    latitude = 51.5,
                    longitude = -0.1,
                    radiusMeters = 250,
                    mode = com.garan.threshold.core.model.GeoTriggerMode.INSIDE,
                ),
            ),
        )

        repository.saveTrigger(workHours)
        repository.saveTrigger(atOffice)

        val settings = repository.getSettings()
        assertThat(settings.savedTriggers).containsExactly(workHours, atOffice).inOrder()

        val renamedOffice = atOffice.copy(name = "HQ Office")
        repository.saveTrigger(renamedOffice)
        assertThat(repository.getSettings().savedTriggers)
            .containsExactly(workHours, renamedOffice)
            .inOrder()
    }

    @Test
    fun customProfiles_activationRulesAndOrPersistedCorrectly() = runTest(testDispatcher) {
        val profile = com.garan.threshold.core.model.CustomProfile(
            id = "focus",
            name = "Focus",
            enabled = true,
            priority = 0,
            activationRules = listOf(
                com.garan.threshold.core.model.TriggerRule(listOf("t-work-hours", "t-office")),
                com.garan.threshold.core.model.TriggerRule(listOf("t-late-night")),
            ),
            trigger = null,
            appAssignments = mapOf(socialKey to AppAssignment.Blocked),
        )

        repository.saveCustomProfile(profile)

        val loaded = repository.getSettings().customProfiles.single()
        assertThat(loaded.trigger).isNull()
        assertThat(loaded.activationRules).containsExactly(
            com.garan.threshold.core.model.TriggerRule(listOf("t-work-hours", "t-office")),
            com.garan.threshold.core.model.TriggerRule(listOf("t-late-night")),
        ).inOrder()
    }

    @Test
    fun legacyInlineTrigger_autoMigratesToSavedTriggerAndActivationRule() = runTest(testDispatcher) {
        val legacyProfile = com.garan.threshold.core.model.CustomProfile(
            id = "legacy-work",
            name = "Legacy Work",
            enabled = true,
            priority = 0,
            trigger = com.garan.threshold.core.model.ProfileTrigger.Schedule(),
        )

        repository.saveCustomProfile(legacyProfile)

        val settings = repository.getSettings()
        val migratedProfile = settings.customProfiles.single()
        assertThat(migratedProfile.activationRules).containsExactly(
            com.garan.threshold.core.model.TriggerRule(listOf("trigger-legacy-work")),
        )
        assertThat(settings.savedTriggers.map { it.id }).containsExactly("trigger-legacy-work")
        assertThat(settings.savedTriggers.single().name).isEqualTo("Legacy Work")
    }

    @Test
    fun deleteTrigger_stripsFromProfileRulesAndAutoDisablesWhenNoTriggersRemain() = runTest(testDispatcher) {
        val t1 = com.garan.threshold.core.model.SavedTrigger(
            id = "t1",
            name = "Work Hours",
            trigger = com.garan.threshold.core.model.ProfileTrigger.Schedule(),
        )
        val t2 = com.garan.threshold.core.model.SavedTrigger(
            id = "t2",
            name = "At Office",
            trigger = com.garan.threshold.core.model.ProfileTrigger.Geo(
                com.garan.threshold.core.model.GeoTrigger(latitude = 51.5, longitude = -0.1),
            ),
        )
        repository.saveTrigger(t1)
        repository.saveTrigger(t2)

        val singleTriggerProfile = com.garan.threshold.core.model.CustomProfile(
            id = "p-single",
            name = "Single Trigger",
            enabled = true,
            priority = 0,
            activationRules = listOf(com.garan.threshold.core.model.TriggerRule(listOf("t1"))),
            trigger = null,
        )
        val multiTriggerProfile = com.garan.threshold.core.model.CustomProfile(
            id = "p-multi",
            name = "Multi Trigger",
            enabled = true,
            priority = 1,
            activationRules = listOf(com.garan.threshold.core.model.TriggerRule(listOf("t1", "t2"))),
            trigger = null,
        )
        repository.updateCustomProfiles(listOf(singleTriggerProfile, multiTriggerProfile))

        repository.deleteTrigger("t1")

        val settings = repository.getSettings()
        assertThat(settings.savedTriggers).containsExactly(t2)

        val updatedSingle = settings.customProfiles.first { it.id == "p-single" }
        assertThat(updatedSingle.activationRules).isEmpty()
        assertThat(updatedSingle.enabled).isFalse()

        val updatedMulti = settings.customProfiles.first { it.id == "p-multi" }
        assertThat(updatedMulti.activationRules).containsExactly(
            com.garan.threshold.core.model.TriggerRule(listOf("t2")),
        )
        assertThat(updatedMulti.enabled).isTrue()
    }
}

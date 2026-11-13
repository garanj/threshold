package com.garan.threshold.core.cascading

import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.model.GeoTrigger
import com.garan.threshold.core.model.GeoTriggerMode
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
import com.garan.threshold.core.model.TriggerRule
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CascadingResolutionEngineTest {

    private val dialerComponent = ComponentKey("com.android.dialer", "com.android.dialer.DialtactsActivity")
    private val socialComponent = ComponentKey("com.social.app", "com.social.app.MainActivity")
    private val workComponent = ComponentKey("com.work.chat", "com.work.chat.MainActivity")
    private val utilityComponent = ComponentKey("com.tool.calc", "com.tool.calc.MainActivity")

    private val weekdayDaySchedule = ScheduleTrigger(
        days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        startTime = TimeOfDay(9, 0),
        endTime = TimeOfDay(17, 0),
    )

    private val engine = CascadingResolutionEngine()

    @Test
    fun universalDialerInvariant_alwaysDirectEvenWhenBlocked() {
        val base = BaseProfileSettings(
            appAssignments = mapOf(dialerComponent to AppAssignment.Blocked),
        )
        val profile = CustomProfile(
            id = "work",
            name = "Work",
            priority = 0,
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
            appAssignments = mapOf(dialerComponent to AppAssignment.Blocked),
        )

        val resolved = engine.resolveAppAccess(
            component = dialerComponent,
            baseProfile = base,
            customProfiles = listOf(profile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = true,
        )

        assertThat(resolved).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun noActiveProfiles_fallsBackToBaseProfile() {
        val base = BaseProfileSettings(
            defaultFriction = FrictionChoice.TYPED_INTENTION,
            appAssignments = mapOf(
                socialComponent to AppAssignment.Blocked,
                workComponent to AppAssignment.Guarded(),
            ),
        )
        val inactiveProfile = CustomProfile(
            id = "work",
            name = "Work",
            priority = 0,
            enabled = false, // Disabled
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
            appAssignments = mapOf(socialComponent to AppAssignment.Standard),
        )

        val socialAccess = engine.resolveAppAccess(
            component = socialComponent,
            baseProfile = base,
            customProfiles = listOf(inactiveProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(socialAccess).isEqualTo(AppAccessState.Blocked)

        val workAccess = engine.resolveAppAccess(
            component = workComponent,
            baseProfile = base,
            customProfiles = listOf(inactiveProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(workAccess).isEqualTo(AppAccessState.Guarded(FrictionPolicy.TypedIntention))

        val utilityAccess = engine.resolveAppAccess(
            component = utilityComponent,
            baseProfile = base,
            customProfiles = listOf(inactiveProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(utilityAccess).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun singleActiveProfile_appliesExplicitOverridesAndCascadesDefault() {
        val base = BaseProfileSettings(
            defaultFriction = FrictionChoice.TYPED_INTENTION,
            appAssignments = mapOf(
                socialComponent to AppAssignment.Guarded(),
                workComponent to AppAssignment.Blocked,
                utilityComponent to AppAssignment.Standard,
            ),
        )
        val workProfile = CustomProfile(
            id = "work",
            name = "Work",
            priority = 0,
            enabled = true,
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
            appAssignments = mapOf(
                socialComponent to AppAssignment.Blocked, // Explicitly blocked during work
                workComponent to AppAssignment.Standard, // Explicitly standard (allowed) during work
                // utilityComponent is unmanaged (DEFAULT) -> should cascade to base (Standard)
            ),
        )

        val social = engine.resolveAppAccess(
            component = socialComponent,
            baseProfile = base,
            customProfiles = listOf(workProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(social).isEqualTo(AppAccessState.Blocked)

        val work = engine.resolveAppAccess(
            component = workComponent,
            baseProfile = base,
            customProfiles = listOf(workProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(work).isEqualTo(AppAccessState.Direct)

        val utility = engine.resolveAppAccess(
            component = utilityComponent,
            baseProfile = base,
            customProfiles = listOf(workProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(utility).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun multiProfilePriority_higherPriorityWinsWhenConflicting() {
        val base = BaseProfileSettings(
            appAssignments = mapOf(socialComponent to AppAssignment.Standard),
        )

        // Focus profile (priority 0 = highest)
        val focusProfile = CustomProfile(
            id = "focus",
            name = "Deep Focus",
            priority = 0,
            enabled = true,
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
            appAssignments = mapOf(socialComponent to AppAssignment.Blocked),
        )

        // General Work profile (priority 1 = lower)
        val workProfile = CustomProfile(
            id = "work",
            name = "Work",
            priority = 1,
            enabled = true,
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
            appAssignments = mapOf(socialComponent to AppAssignment.Guarded()),
        )

        // When focus is priority 0, social is Blocked
        val resolved1 = engine.resolveAppAccess(
            component = socialComponent,
            baseProfile = base,
            customProfiles = listOf(focusProfile, workProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(resolved1).isEqualTo(AppAccessState.Blocked)

        // If user reorders so work is priority 0 and focus is priority 1:
        val reorderedFocus = focusProfile.copy(priority = 1)
        val reorderedWork = workProfile.copy(priority = 0)
        val resolved2 = engine.resolveAppAccess(
            component = socialComponent,
            baseProfile = base,
            customProfiles = listOf(reorderedWork, reorderedFocus),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(resolved2).isInstanceOf(AppAccessState.Guarded::class.java)
    }

    @Test
    fun multiProfileCascading_unassignedInHigherCascadesToLowerActiveProfile() {
        val base = BaseProfileSettings(
            appAssignments = mapOf(workComponent to AppAssignment.Blocked),
        )

        // Priority 0: Focus profile does NOT mention workComponent (it's DEFAULT)
        val focusProfile = CustomProfile(
            id = "focus",
            name = "Deep Focus",
            priority = 0,
            enabled = true,
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
            appAssignments = mapOf(socialComponent to AppAssignment.Blocked),
        )

        // Priority 1: Work profile unblocks workComponent
        val workProfile = CustomProfile(
            id = "work",
            name = "Work",
            priority = 1,
            enabled = true,
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
            appAssignments = mapOf(workComponent to AppAssignment.Standard),
        )

        val resolved = engine.resolveAppAccess(
            component = workComponent,
            baseProfile = base,
            customProfiles = listOf(focusProfile, workProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        // Focus profile had no rule, so cascaded to Work profile which allowed it
        assertThat(resolved).isEqualTo(AppAccessState.Direct)
    }

    @Test
    fun frictionInheritance_appOverrideBeatsProfileOverrideBeatsBaseDefault() {
        val base = BaseProfileSettings(
            defaultFriction = FrictionChoice.TYPED_INTENTION,
        )

        val profileWithFriction = CustomProfile(
            id = "work",
            name = "Work",
            priority = 0,
            enabled = true,
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
            frictionOverride = FrictionChoice.CARET_TRACING,
            appAssignments = mapOf(
                socialComponent to AppAssignment.Guarded(), // Inherits profile override (Caret Tracing)
                workComponent to AppAssignment.Guarded(FrictionChoice.DRAG_SORT), // Has app override (Drag Sort)
            ),
        )

        val social = engine.resolveAppAccess(
            component = socialComponent,
            baseProfile = base,
            customProfiles = listOf(profileWithFriction),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(social).isEqualTo(
            AppAccessState.Guarded(FrictionPolicy.TimedDelay(seconds = FrictionChoice.CARET_DEFAULT_SECONDS)),
        )

        val work = engine.resolveAppAccess(
            component = workComponent,
            baseProfile = base,
            customProfiles = listOf(profileWithFriction),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            isDefaultDialer = false,
        )
        assertThat(work).isEqualTo(AppAccessState.Guarded(FrictionPolicy.DragSort(pairCount = 6)))
    }

    @Test
    fun resolveWorkingSet_mergesActiveProfilesAndBaseProfileRules() {
        val base = BaseProfileSettings(
            defaultFriction = FrictionChoice.TYPED_INTENTION,
            appAssignments = mapOf(
                utilityComponent to AppAssignment.Guarded(),
                socialComponent to AppAssignment.Standard,
            ),
        )
        val focusProfile = CustomProfile(
            id = "focus",
            name = "Deep Focus",
            priority = 0,
            enabled = true,
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
            appAssignments = mapOf(
                socialComponent to AppAssignment.Blocked,
                dialerComponent to AppAssignment.Blocked,
            ),
        )

        val workingSet = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(focusProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            defaultDialerPackage = dialerComponent.packageName,
        )

        assertThat(workingSet.activeProfiles).containsExactly(focusProfile)
        assertThat(workingSet.effectiveProfile.id).isEqualTo("focus")
        assertThat(workingSet.effectiveProfile.name).isEqualTo("Deep Focus")
        assertThat(workingSet.effectiveProfile.appRules[socialComponent]).isEqualTo(AppAccessState.Blocked)
        assertThat(workingSet.effectiveProfile.appRules[utilityComponent])
            .isEqualTo(AppAccessState.Guarded(FrictionPolicy.TypedIntention))
        assertThat(workingSet.effectiveProfile.appRules[dialerComponent]).isEqualTo(AppAccessState.Direct)
        assertThat(workingSet.blockedComponents).containsExactly(socialComponent)
    }

    @Test
    fun geoProfile_activatesWithLocationAndPreservesHysteresisOnBoundaryDrift() {
        val officeCenter = LocationFix(latitude = 51.5074, longitude = -0.1278)
        val officeGeoProfile = CustomProfile(
            id = "office",
            name = "Office",
            priority = 0,
            enabled = true,
            trigger = ProfileTrigger.Geo(
                GeoTrigger(
                    latitude = officeCenter.latitude,
                    longitude = officeCenter.longitude,
                    radiusMeters = 250,
                    mode = GeoTriggerMode.INSIDE,
                ),
            ),
            appAssignments = mapOf(socialComponent to AppAssignment.Blocked),
        )
        val base = BaseProfileSettings(
            appAssignments = mapOf(socialComponent to AppAssignment.Standard),
        )

        // 1. Inside 100m -> active
        val insideFix =
            LocationFix(latitude = officeCenter.latitude + (100.0 / 111_194.9266), longitude = officeCenter.longitude)
        val ws1 = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(officeGeoProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = insideFix,
            previouslyActiveProfileIds = emptySet(),
        )
        assertThat(ws1.activeProfiles).containsExactly(officeGeoProfile)
        assertThat(ws1.blockedComponents).containsExactly(socialComponent)

        // 2. Drifts to 265m (within +25m exit hysteresis buffer) -> stays active when previously active
        val boundaryFix =
            LocationFix(latitude = officeCenter.latitude + (265.0 / 111_194.9266), longitude = officeCenter.longitude)
        val ws2 = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(officeGeoProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 1),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = boundaryFix,
            previouslyActiveProfileIds = setOf("office"),
        )
        assertThat(ws2.activeProfiles).containsExactly(officeGeoProfile)

        // 3. Moves to 300m (beyond 275m buffer) -> deactivates
        val outsideFix =
            LocationFix(latitude = officeCenter.latitude + (300.0 / 111_194.9266), longitude = officeCenter.longitude)
        val ws3 = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(officeGeoProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 2),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = outsideFix,
            previouslyActiveProfileIds = setOf("office"),
        )
        assertThat(ws3.activeProfiles).isEmpty()

        // 4. Null location (Location Services off / permission missing) -> fails safe to inactive even for OUTSIDE mode
        val awayGeoProfile = officeGeoProfile.copy(
            id = "away",
            trigger = ProfileTrigger.Geo(
                GeoTrigger(
                    latitude = officeCenter.latitude,
                    longitude = officeCenter.longitude,
                    radiusMeters = 250,
                    mode = GeoTriggerMode.OUTSIDE,
                ),
            ),
        )
        val wsNull = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(officeGeoProfile, awayGeoProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 3),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = null,
            previouslyActiveProfileIds = setOf("office", "away"),
        )
        assertThat(wsNull.activeProfiles).isEmpty()
    }

    @Test
    fun reusableTriggers_evaluatesAndWithinRuleAndOrAcrossRulesWithTriggerHysteresis() {
        val officeCenter = LocationFix(latitude = 51.5074, longitude = -0.1278)
        val insideFix =
            LocationFix(latitude = officeCenter.latitude + (100.0 / 111_194.9266), longitude = officeCenter.longitude)
        val boundaryFix =
            LocationFix(latitude = officeCenter.latitude + (265.0 / 111_194.9266), longitude = officeCenter.longitude)
        val farFix =
            LocationFix(latitude = officeCenter.latitude + (500.0 / 111_194.9266), longitude = officeCenter.longitude)

        val workHoursTrigger = SavedTrigger(
            id = "t-work-hours",
            name = "Work Hours",
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
        )
        val officeGeoTrigger = SavedTrigger(
            id = "t-office-geo",
            name = "At Office",
            trigger = ProfileTrigger.Geo(
                GeoTrigger(
                    latitude = officeCenter.latitude,
                    longitude = officeCenter.longitude,
                    radiusMeters = 250,
                    mode = GeoTriggerMode.INSIDE,
                ),
            ),
        )
        val weekendEveningTrigger = SavedTrigger(
            id = "t-weekend",
            name = "Saturday Study",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.SATURDAY),
                    startTime = TimeOfDay(14, 0),
                    endTime = TimeOfDay(18, 0),
                ),
            ),
        )
        val savedTriggers = listOf(workHoursTrigger, officeGeoTrigger, weekendEveningTrigger)

        // Profile activates when: (Work Hours AND At Office) OR (Saturday Study)
        val focusProfile = CustomProfile(
            id = "focus",
            name = "Focus",
            priority = 0,
            enabled = true,
            trigger = null,
            activationRules = listOf(
                TriggerRule(listOf("t-work-hours", "t-office-geo")),
                TriggerRule(listOf("t-weekend")),
            ),
            appAssignments = mapOf(socialComponent to AppAssignment.Blocked),
        )
        val base = BaseProfileSettings(
            appAssignments = mapOf(socialComponent to AppAssignment.Standard),
        )

        // 1. Monday 10:00 (Work Hours = true), but far from Office (At Office = false) -> AND rule is FALSE
        val wsTimeOnly = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(focusProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = farFix,
            savedTriggers = savedTriggers,
        )
        assertThat(wsTimeOnly.activeProfiles).isEmpty()

        // 2. Sunday 10:00 (Work Hours = false), inside Office (At Office = true) -> AND rule is FALSE
        val wsGeoOnly = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(focusProfile),
            currentDay = DayOfWeek.SUNDAY,
            currentTime = TimeOfDay(10, 0),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = insideFix,
            savedTriggers = savedTriggers,
        )
        assertThat(wsGeoOnly.activeProfiles).isEmpty()

        // 3. Monday 10:00 (Work Hours = true) AND inside Office (At Office = true) -> AND rule is TRUE
        val wsBoth = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(focusProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = insideFix,
            savedTriggers = savedTriggers,
        )
        assertThat(wsBoth.activeProfiles).containsExactly(focusProfile)
        assertThat(wsBoth.activeTriggerIds).containsExactly("t-work-hours", "t-office-geo")

        // 4. Drifts to 265m on Monday 10:05 -> trigger hysteresis keeps t-office-geo active
        val wsDrift = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(focusProfile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 5),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = boundaryFix,
            savedTriggers = savedTriggers,
            previouslyActiveTriggerIds = wsBoth.activeTriggerIds,
        )
        assertThat(wsDrift.activeProfiles).containsExactly(focusProfile)

        // 5. Saturday 15:00 while far from Office -> second OR rule (t-weekend) activates profile
        val wsOrRule = engine.resolveWorkingSet(
            baseProfile = base,
            customProfiles = listOf(focusProfile),
            currentDay = DayOfWeek.SATURDAY,
            currentTime = TimeOfDay(15, 0),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = farFix,
            savedTriggers = savedTriggers,
        )
        assertThat(wsOrRule.activeProfiles).containsExactly(focusProfile)
    }

    @Test
    fun reusableTriggers_missingTriggerInAndRuleFailsSafeWhileValidOrRuleStillActivates() {
        val workHoursTrigger = SavedTrigger(
            id = "t-work-hours",
            name = "Work Hours",
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
        )
        val eveningTrigger = SavedTrigger(
            id = "t-evening",
            name = "Evening",
            trigger = ProfileTrigger.Schedule(
                ScheduleTrigger(
                    days = setOf(DayOfWeek.MONDAY),
                    startTime = TimeOfDay(19, 0),
                    endTime = TimeOfDay(22, 0),
                ),
            ),
        )

        // Rule 1 has [t-work-hours AND t-missing]; Rule 2 has [t-evening]
        val profile = CustomProfile(
            id = "resilient",
            name = "Resilient",
            priority = 0,
            enabled = true,
            trigger = null,
            activationRules = listOf(
                TriggerRule(listOf("t-work-hours", "t-missing")),
                TriggerRule(listOf("t-evening")),
            ),
            appAssignments = mapOf(socialComponent to AppAssignment.Blocked),
        )

        // Monday 10:00 -> t-work-hours is active, but t-missing is not -> Rule 1 is false -> inactive
        val wsMorning = engine.resolveWorkingSet(
            baseProfile = BaseProfileSettings(),
            customProfiles = listOf(profile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            defaultDialerPackage = dialerComponent.packageName,
            savedTriggers = listOf(workHoursTrigger, eveningTrigger),
        )
        assertThat(wsMorning.activeProfiles).isEmpty()

        // Monday 20:00 -> Rule 2 (t-evening) is satisfied -> profile activates
        val wsEvening = engine.resolveWorkingSet(
            baseProfile = BaseProfileSettings(),
            customProfiles = listOf(profile),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(20, 0),
            defaultDialerPackage = dialerComponent.packageName,
            savedTriggers = listOf(workHoursTrigger, eveningTrigger),
        )
        assertThat(wsEvening.activeProfiles).containsExactly(profile)
    }

    @Test
    fun reusableTriggers_sharedAcrossProfilesAndDualGeoAndRule_evaluatesPriorityAndSpatialExclusion() {
        val campusCenter = LocationFix(latitude = 51.5074, longitude = -0.1278)
        val breakRoomCenter = LocationFix(
            latitude = campusCenter.latitude + (100.0 / 111_194.9266),
            longitude = campusCenter.longitude,
        )

        val workHours = SavedTrigger(
            id = "t-work",
            name = "Work Hours",
            trigger = ProfileTrigger.Schedule(weekdayDaySchedule),
        )
        val insideCampus = SavedTrigger(
            id = "t-inside-campus",
            name = "Inside Campus",
            trigger = ProfileTrigger.Geo(
                GeoTrigger(
                    latitude = campusCenter.latitude,
                    longitude = campusCenter.longitude,
                    radiusMeters = 500,
                    mode = GeoTriggerMode.INSIDE,
                ),
            ),
        )
        val outsideBreakRoom = SavedTrigger(
            id = "t-outside-breakroom",
            name = "Outside Break Room",
            trigger = ProfileTrigger.Geo(
                GeoTrigger(
                    latitude = breakRoomCenter.latitude,
                    longitude = breakRoomCenter.longitude,
                    radiusMeters = 100,
                    mode = GeoTriggerMode.OUTSIDE,
                ),
            ),
        )

        // Priority 0: Strict Desk Focus = Work Hours AND Inside Campus AND Outside Break Room -> Blocks social
        val strictFocus = CustomProfile(
            id = "strict-desk",
            name = "Strict Desk",
            priority = 0,
            enabled = true,
            trigger = null,
            activationRules = listOf(
                TriggerRule(listOf("t-work", "t-inside-campus", "t-outside-breakroom")),
            ),
            appAssignments = mapOf(socialComponent to AppAssignment.Blocked),
        )

        // Priority 1: General Work = Work Hours AND Inside Campus -> Guards social
        val generalWork = CustomProfile(
            id = "general-work",
            name = "General Work",
            priority = 1,
            enabled = true,
            trigger = null,
            activationRules = listOf(
                TriggerRule(listOf("t-work", "t-inside-campus")),
            ),
            appAssignments = mapOf(socialComponent to AppAssignment.Guarded()),
        )

        val triggers = listOf(workHours, insideCampus, outsideBreakRoom)

        // 1. At campus center (100m from break room, wait: let's stand 300m south of campus center -> inside 500m campus, 400m from break room)
        val deskLocation = LocationFix(
            latitude = campusCenter.latitude - (200.0 / 111_194.9266),
            longitude = campusCenter.longitude,
        )
        val wsAtDesk = engine.resolveWorkingSet(
            baseProfile = BaseProfileSettings(),
            customProfiles = listOf(strictFocus, generalWork),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 0),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = deskLocation,
            savedTriggers = triggers,
        )
        // Both profiles active, Strict Desk (priority 0) wins and blocks socialComponent
        assertThat(wsAtDesk.activeProfiles).containsExactly(strictFocus, generalWork).inOrder()
        assertThat(wsAtDesk.blockedComponents).containsExactly(socialComponent)

        // 2. Step into Break Room -> Outside Break Room becomes false -> Strict Desk deactivates, General Work remains active (Guarded)
        val wsInBreakRoom = engine.resolveWorkingSet(
            baseProfile = BaseProfileSettings(),
            customProfiles = listOf(strictFocus, generalWork),
            currentDay = DayOfWeek.MONDAY,
            currentTime = TimeOfDay(10, 5),
            defaultDialerPackage = dialerComponent.packageName,
            currentLocation = breakRoomCenter,
            savedTriggers = triggers,
        )
        assertThat(wsInBreakRoom.activeProfiles).containsExactly(generalWork)
        assertThat(wsInBreakRoom.blockedComponents).isEmpty()
        assertThat(wsInBreakRoom.effectiveProfile.appRules[socialComponent]).isNotNull()
    }
}

package com.garan.threshold.core.cascading

import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.FrictionChoice
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.ScheduleTrigger
import com.garan.threshold.core.model.TimeOfDay
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
}

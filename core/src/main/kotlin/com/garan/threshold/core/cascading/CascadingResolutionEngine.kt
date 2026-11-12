package com.garan.threshold.core.cascading

import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.TimeOfDay
import kotlin.random.Random

/**
 * Snapshot of the currently active custom profiles, active trigger IDs, and the precomputed
 * effective access rules for all configured applications.
 */
data class ResolvedWorkingSet(
    val activeProfiles: List<CustomProfile>,
    val effectiveProfile: Profile,
    val activeTriggerIds: Set<String> = emptySet(),
) {
    val blockedComponents: Set<ComponentKey>
        get() = effectiveProfile.appRules.filterValues { it is AppAccessState.Blocked }.keys
}

/**
 * Conflict Resolution Engine evaluating application access states across user-defined profiles
 * and cascading fallback baselines.
 *
 * Evaluation hierarchy:
 * 1. **Universal Dialer Safety Invariant**: The default dialer (`isDefaultDialer = true`) is
 *    permanently and unconditionally resolved as [AppAccessState.Direct] across all tiers.
 * 2. **Active Custom Profiles**: Evaluated in user-defined priority order (top-to-bottom, lowest
 *    priority integer first). The first active profile providing an explicit assignment for the app
 *    determines its access state.
 * 3. **Base Profile Fallback**: If no active profile defines an explicit assignment for the app
 *    (or no profiles are currently active), evaluation cascades down to the base profile.
 */
class CascadingResolutionEngine {

    /**
     * Evaluates all [savedTriggers] and returns the IDs of triggers that are currently active.
     */
    fun evaluateActiveTriggerIds(
        savedTriggers: List<SavedTrigger>,
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        currentLocation: LocationFix? = null,
        previouslyActiveTriggerIds: Set<String> = emptySet(),
        customProfiles: List<CustomProfile> = emptyList(),
        previouslyActiveProfileIds: Set<String> = emptySet(),
    ): Set<String> {
        if (savedTriggers.isEmpty()) return emptySet()
        return savedTriggers.mapNotNullTo(linkedSetOf()) { saved ->
            val wasActive = saved.id in previouslyActiveTriggerIds ||
                customProfiles.any { profile ->
                    profile.id in previouslyActiveProfileIds && saved.id in profile.referencedTriggerIds
                }
            if (saved.isActive(
                    currentDay = currentDay,
                    currentTime = currentTime,
                    currentLocation = currentLocation,
                    wasPreviouslyActive = wasActive,
                )
            ) {
                saved.id
            } else {
                null
            }
        }
    }

    /**
     * Filters and returns active custom profiles sorted in priority order (0 = highest priority).
     */
    fun getActiveProfiles(
        customProfiles: List<CustomProfile>,
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        currentLocation: LocationFix? = null,
        previouslyActiveProfileIds: Set<String> = emptySet(),
        savedTriggers: List<SavedTrigger> = emptyList(),
        previouslyActiveTriggerIds: Set<String> = emptySet(),
    ): List<CustomProfile> {
        val activeTriggerIds = evaluateActiveTriggerIds(
            savedTriggers = savedTriggers,
            currentDay = currentDay,
            currentTime = currentTime,
            currentLocation = currentLocation,
            previouslyActiveTriggerIds = previouslyActiveTriggerIds,
            customProfiles = customProfiles,
            previouslyActiveProfileIds = previouslyActiveProfileIds,
        )
        return customProfiles
            .filter {
                it.isActive(
                    currentDay = currentDay,
                    currentTime = currentTime,
                    currentLocation = currentLocation,
                    wasPreviouslyActive = it.id in previouslyActiveProfileIds,
                    activeTriggerIds = activeTriggerIds,
                )
            }
            .sortedBy { it.priority }
    }

    /**
     * Resolves the effective [AppAccessState] for [component] against active profiles and base configuration.
     */
    fun resolveAppAccess(
        component: ComponentKey,
        baseProfile: BaseProfileSettings,
        customProfiles: List<CustomProfile>,
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        isDefaultDialer: Boolean,
        currentLocation: LocationFix? = null,
        previouslyActiveProfileIds: Set<String> = emptySet(),
        savedTriggers: List<SavedTrigger> = emptyList(),
        previouslyActiveTriggerIds: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): AppAccessState {
        // Prime Invariant 2: Universal Dialer Safety Invariant
        if (isDefaultDialer) {
            return AppAccessState.Direct
        }

        val activeProfiles = getActiveProfiles(
            customProfiles = customProfiles,
            currentDay = currentDay,
            currentTime = currentTime,
            currentLocation = currentLocation,
            previouslyActiveProfileIds = previouslyActiveProfileIds,
            savedTriggers = savedTriggers,
            previouslyActiveTriggerIds = previouslyActiveTriggerIds,
        )

        for (profile in activeProfiles) {
            val assignment = profile.appAssignments[component]
                ?: profile.appAssignments.entries.firstOrNull { it.key.packageName == component.packageName }?.value

            if (assignment != null) {
                return when (assignment) {
                    is AppAssignment.Standard -> AppAccessState.Direct
                    is AppAssignment.Blocked -> AppAccessState.Blocked
                    is AppAssignment.Guarded -> {
                        // Friction precedence: App override -> Profile override -> Base default
                        val frictionChoice = assignment.frictionOverride
                            ?: profile.frictionOverride
                            ?: baseProfile.defaultFriction
                        AppAccessState.Guarded(frictionChoice.resolvePolicy(random))
                    }
                }
            }
        }

        // Cascade down to Base Profile
        return baseProfile.resolveAccess(component, isDefaultDialer = false, random = random)
    }

    /**
     * Computes the complete [ResolvedWorkingSet] (active custom profiles, active trigger IDs, and
     * merged effective [Profile] across all configured components in [baseProfile] and [customProfiles]).
     */
    fun resolveWorkingSet(
        baseProfile: BaseProfileSettings,
        customProfiles: List<CustomProfile>,
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        defaultDialerPackage: String,
        additionalComponents: Collection<ComponentKey> = emptyList(),
        currentLocation: LocationFix? = null,
        previouslyActiveProfileIds: Set<String> = emptySet(),
        savedTriggers: List<SavedTrigger> = emptyList(),
        previouslyActiveTriggerIds: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): ResolvedWorkingSet {
        val activeTriggerIds = evaluateActiveTriggerIds(
            savedTriggers = savedTriggers,
            currentDay = currentDay,
            currentTime = currentTime,
            currentLocation = currentLocation,
            previouslyActiveTriggerIds = previouslyActiveTriggerIds,
            customProfiles = customProfiles,
            previouslyActiveProfileIds = previouslyActiveProfileIds,
        )

        val activeProfiles = customProfiles
            .filter {
                it.isActive(
                    currentDay = currentDay,
                    currentTime = currentTime,
                    currentLocation = currentLocation,
                    wasPreviouslyActive = it.id in previouslyActiveProfileIds,
                    activeTriggerIds = activeTriggerIds,
                )
            }
            .sortedBy { it.priority }

        val candidateComponents = buildSet {
            addAll(baseProfile.appAssignments.keys)
            for (profile in activeProfiles) {
                addAll(profile.appAssignments.keys)
            }
            addAll(additionalComponents)
        }

        val appRules = candidateComponents.associateWith { comp ->
            resolveWithActiveProfiles(
                component = comp,
                baseProfile = baseProfile,
                activeProfiles = activeProfiles,
                isDefaultDialer = (comp.packageName == defaultDialerPackage),
                random = random,
            )
        }

        val topProfile = activeProfiles.firstOrNull()
        val effectiveProfile = Profile(
            id = topProfile?.id ?: "base",
            name = topProfile?.name ?: "Base",
            defaultAccess = AppAccessState.Direct,
            appRules = appRules,
        )

        return ResolvedWorkingSet(
            activeProfiles = activeProfiles,
            effectiveProfile = effectiveProfile,
            activeTriggerIds = activeTriggerIds,
        )
    }

    private fun resolveWithActiveProfiles(
        component: ComponentKey,
        baseProfile: BaseProfileSettings,
        activeProfiles: List<CustomProfile>,
        isDefaultDialer: Boolean,
        random: Random,
    ): AppAccessState {
        if (isDefaultDialer) {
            return AppAccessState.Direct
        }

        for (profile in activeProfiles) {
            val assignment = profile.appAssignments[component]
                ?: profile.appAssignments.entries.firstOrNull { it.key.packageName == component.packageName }?.value

            if (assignment != null) {
                return when (assignment) {
                    is AppAssignment.Standard -> AppAccessState.Direct
                    is AppAssignment.Blocked -> AppAccessState.Blocked
                    is AppAssignment.Guarded -> {
                        val frictionChoice = assignment.frictionOverride
                            ?: profile.frictionOverride
                            ?: baseProfile.defaultFriction
                        AppAccessState.Guarded(frictionChoice.resolvePolicy(random))
                    }
                }
            }
        }

        return baseProfile.resolveAccess(component, isDefaultDialer = false, random = random)
    }
}

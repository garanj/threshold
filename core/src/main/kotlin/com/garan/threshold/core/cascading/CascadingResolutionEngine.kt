package com.garan.threshold.core.cascading

import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.AppAssignment
import com.garan.threshold.core.model.BaseProfileSettings
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.DayOfWeek
import com.garan.threshold.core.model.LocationFix
import com.garan.threshold.core.model.NfcLatchState
import com.garan.threshold.core.model.NfcTagEntry
import com.garan.threshold.core.model.NfcTriggerMode
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.model.TimeOfDay
import kotlin.random.Random

/**
 * Outcome of tapping a physical NFC tag against [CascadingResolutionEngine.processNfcTagTap].
 */
enum class NfcTapOutcome {
    /**
     * At least one matching [ProfileTrigger.Nfc] was latched ON (and its companion `AND` conditions were satisfied).
     */
    LATCHED_ON,

    /**
     * At least one matching [ProfileTrigger.Nfc] that was currently active was unlatched OFF.
     */
    UNLATCHED_OFF,

    /**
     * The tapped tag matched an [ProfileTrigger.Nfc], but no enabled profile rule referencing it had its
     * companion `AND` conditions (`Schedule` / `Geo`) satisfied, so early latching was rejected.
     */
    COMPANION_CONDITIONS_NOT_MET,

    /**
     * The tapped tag UID is not registered in any [SavedTrigger].
     */
    UNRECOGNIZED_TAG,
}

/**
 * Result of processing a physical NFC tag tap.
 */
data class NfcTapResult(
    val outcome: NfcTapOutcome,
    val updatedLatches: Map<String, NfcLatchState>,
    val affectedTriggerIds: Set<String> = emptySet(),
)

/**
 * Snapshot of the currently active custom profiles, active trigger IDs, active NFC latches,
 * and the precomputed effective access rules for all configured applications.
 */
data class ResolvedWorkingSet(
    val activeProfiles: List<CustomProfile>,
    val effectiveProfile: Profile,
    val activeTriggerIds: Set<String> = emptySet(),
    val activeNfcLatches: Map<String, NfcLatchState> = emptyMap(),
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
     *
     * For [ProfileTrigger.Nfc] triggers:
     * - An NFC trigger is only considered active if it is currently latched in [nfcLatchStates]
     *   (and not expired at [nowEpochMillis]) or present in [previouslyActiveTriggerIds] when
     *   [nfcLatchStates] is omitted.
     * - When [customProfiles] is non-empty, a latched NFC trigger automatically unlatches if no
     *   enabled profile's `AND` rule containing that NFC trigger has all of its companion conditions
     *   (`Schedule` / `Geo` / other required triggers) currently satisfied.
     */
    fun evaluateActiveTriggerIds(
        savedTriggers: List<SavedTrigger>,
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        currentLocation: LocationFix? = null,
        previouslyActiveTriggerIds: Set<String> = emptySet(),
        customProfiles: List<CustomProfile> = emptyList(),
        previouslyActiveProfileIds: Set<String> = emptySet(),
        nfcLatchStates: Map<String, NfcLatchState>? = null,
        nowEpochMillis: Long = 0L,
    ): Set<String> {
        if (savedTriggers.isEmpty()) return emptySet()

        // 1. Evaluate ambient (non-NFC) triggers first
        val ambientActiveIds = LinkedHashSet<String>()
        val candidateNfcIds = LinkedHashSet<String>()
        val validNfcTriggerIds = LinkedHashSet<String>()

        for (saved in savedTriggers) {
            when (val spec = saved.trigger) {
                is ProfileTrigger.Schedule, is ProfileTrigger.Geo -> {
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
                        ambientActiveIds.add(saved.id)
                    }
                }
                is ProfileTrigger.Nfc -> {
                    if (spec.nfc.tags.isNotEmpty()) {
                        validNfcTriggerIds.add(saved.id)
                        val isLatchedAndValid = if (nfcLatchStates != null) {
                            nfcLatchStates[saved.id]?.reconcileWith(spec.nfc, nowEpochMillis) != null
                        } else {
                            saved.id in previouslyActiveTriggerIds
                        }
                        if (isLatchedAndValid) {
                            candidateNfcIds.add(saved.id)
                        }
                    }
                }
            }
        }

        if (candidateNfcIds.isEmpty()) return ambientActiveIds

        // 2. If customProfiles are provided, verify each candidate NFC trigger still has at least one
        //    enabled profile rule whose ambient companion conditions (`Schedule` / `Geo`) are satisfied;
        //    otherwise auto-unlatch it.
        val survivingNfcIds = if (customProfiles.isEmpty()) {
            candidateNfcIds
        } else {
            val eligibleIdsForNfcRetention = ambientActiveIds + validNfcTriggerIds
            candidateNfcIds.filterTo(linkedSetOf()) { nfcId ->
                customProfiles.any { profile ->
                    profile.enabled &&
                        profile.activationRules.any { rule ->
                            nfcId in rule.triggerIds &&
                                rule.triggerIds.all { it in eligibleIdsForNfcRetention }
                        }
                }
            }
        }

        return ambientActiveIds + survivingNfcIds
    }

    /**
     * Reconciles [nfcLatchStates] against the current [savedTriggers] definitions and [activeTriggerIds],
     * pruning expired or unreferenced latches and updating expiry timestamps if trigger mode/duration changed.
     */
    fun reconcileNfcLatches(
        savedTriggers: List<SavedTrigger>,
        activeTriggerIds: Set<String>,
        nfcLatchStates: Map<String, NfcLatchState>?,
        nowEpochMillis: Long,
    ): Map<String, NfcLatchState> {
        if (nfcLatchStates.isNullOrEmpty() || activeTriggerIds.isEmpty()) return emptyMap()
        val triggersById = savedTriggers.associateBy { it.id }
        val result = LinkedHashMap<String, NfcLatchState>()
        for ((triggerId, latch) in nfcLatchStates) {
            if (triggerId !in activeTriggerIds) continue
            val nfcSpec = (triggersById[triggerId]?.trigger as? ProfileTrigger.Nfc)?.nfc ?: continue
            val reconciled = latch.reconcileWith(nfcSpec, nowEpochMillis) ?: continue
            result[triggerId] = reconciled
        }
        return result
    }

    /**
     * Processes a physical NFC tag tap for [rawTagUid] at [nowEpochMillis]:
     * - If any matching [ProfileTrigger.Nfc] is currently active, tapping unlatches it OFF.
     * - If matching [ProfileTrigger.Nfc] triggers are currently OFF, each trigger latches ON only if
     *   at least one enabled profile's `AND` rule referencing it has all of its ambient companion conditions
     *   currently satisfied ("no early taps").
     */
    fun processNfcTagTap(
        rawTagUid: String,
        savedTriggers: List<SavedTrigger>,
        customProfiles: List<CustomProfile>,
        currentDay: DayOfWeek,
        currentTime: TimeOfDay,
        currentLocation: LocationFix? = null,
        previouslyActiveTriggerIds: Set<String> = emptySet(),
        previouslyActiveProfileIds: Set<String> = emptySet(),
        currentNfcLatches: Map<String, NfcLatchState> = emptyMap(),
        nowEpochMillis: Long = 0L,
    ): NfcTapResult {
        val normalizedUid = NfcTagEntry.normalizeUid(rawTagUid)
        val matchingNfcTriggers = savedTriggers.filter { saved ->
            val nfcSpec = saved.trigger as? ProfileTrigger.Nfc ?: return@filter false
            nfcSpec.nfc.matchesTagUid(normalizedUid)
        }
        if (matchingNfcTriggers.isEmpty()) {
            return NfcTapResult(
                outcome = NfcTapOutcome.UNRECOGNIZED_TAG,
                updatedLatches = currentNfcLatches,
            )
        }

        // First prune any expired or companion-invalidated latches
        val currentlyActiveIds = evaluateActiveTriggerIds(
            savedTriggers = savedTriggers,
            currentDay = currentDay,
            currentTime = currentTime,
            currentLocation = currentLocation,
            previouslyActiveTriggerIds = previouslyActiveTriggerIds,
            customProfiles = customProfiles,
            previouslyActiveProfileIds = previouslyActiveProfileIds,
            nfcLatchStates = currentNfcLatches,
            nowEpochMillis = nowEpochMillis,
        )
        val validCurrentLatches = reconcileNfcLatches(
            savedTriggers = savedTriggers,
            activeTriggerIds = currentlyActiveIds,
            nfcLatchStates = currentNfcLatches,
            nowEpochMillis = nowEpochMillis,
        )

        // If any matching NFC trigger is currently active -> toggle it OFF
        val activeMatches = matchingNfcTriggers.filter { it.id in currentlyActiveIds }
        if (activeMatches.isNotEmpty()) {
            val idsToUnlatch = activeMatches.mapTo(linkedSetOf()) { it.id }
            return NfcTapResult(
                outcome = NfcTapOutcome.UNLATCHED_OFF,
                updatedLatches = validCurrentLatches - idsToUnlatch,
                affectedTriggerIds = idsToUnlatch,
            )
        }

        // Otherwise, matching NFC triggers are currently OFF -> check if ambient companion AND conditions allow latching
        val validNfcTriggerIds = savedTriggers.mapNotNullTo(linkedSetOf()) { saved ->
            val nfcSpec = saved.trigger as? ProfileTrigger.Nfc ?: return@mapNotNullTo null
            if (nfcSpec.nfc.tags.isNotEmpty()) saved.id else null
        }
        val eligibleIdsForLatching = currentlyActiveIds + validNfcTriggerIds

        val latchableTriggers = matchingNfcTriggers.filter { nfcTrigger ->
            customProfiles.any { profile ->
                profile.enabled &&
                    profile.activationRules.any { rule ->
                        nfcTrigger.id in rule.triggerIds &&
                            rule.triggerIds.all { it in eligibleIdsForLatching }
                    }
            }
        }

        if (latchableTriggers.isEmpty()) {
            return NfcTapResult(
                outcome = NfcTapOutcome.COMPANION_CONDITIONS_NOT_MET,
                updatedLatches = validCurrentLatches,
            )
        }

        val newLatches = LinkedHashMap(validCurrentLatches)
        val newlyLatchedIds = LinkedHashSet<String>()
        for (saved in latchableTriggers) {
            val nfc = (saved.trigger as ProfileTrigger.Nfc).nfc
            val expiresAt = when (nfc.mode) {
                NfcTriggerMode.TOGGLE_ON_TAP -> null
                NfcTriggerMode.TIMED_SESSION -> nowEpochMillis + nfc.durationMinutes * 60_000L
            }
            newLatches[saved.id] = NfcLatchState(
                triggerId = saved.id,
                latchedAtEpochMillis = nowEpochMillis,
                expiresAtEpochMillis = expiresAt,
                latchedByTagUid = normalizedUid,
            )
            newlyLatchedIds.add(saved.id)
        }

        return NfcTapResult(
            outcome = NfcTapOutcome.LATCHED_ON,
            updatedLatches = newLatches,
            affectedTriggerIds = newlyLatchedIds,
        )
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
        nfcLatchStates: Map<String, NfcLatchState>? = null,
        nowEpochMillis: Long = 0L,
    ): List<CustomProfile> {
        val activeTriggerIds = evaluateActiveTriggerIds(
            savedTriggers = savedTriggers,
            currentDay = currentDay,
            currentTime = currentTime,
            currentLocation = currentLocation,
            previouslyActiveTriggerIds = previouslyActiveTriggerIds,
            customProfiles = customProfiles,
            previouslyActiveProfileIds = previouslyActiveProfileIds,
            nfcLatchStates = nfcLatchStates,
            nowEpochMillis = nowEpochMillis,
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
        nfcLatchStates: Map<String, NfcLatchState>? = null,
        nowEpochMillis: Long = 0L,
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
            nfcLatchStates = nfcLatchStates,
            nowEpochMillis = nowEpochMillis,
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
        nfcLatchStates: Map<String, NfcLatchState>? = null,
        nowEpochMillis: Long = 0L,
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
            nfcLatchStates = nfcLatchStates,
            nowEpochMillis = nowEpochMillis,
        )
        val activeNfcLatches = reconcileNfcLatches(
            savedTriggers = savedTriggers,
            activeTriggerIds = activeTriggerIds,
            nfcLatchStates = nfcLatchStates,
            nowEpochMillis = nowEpochMillis,
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
            activeNfcLatches = activeNfcLatches,
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

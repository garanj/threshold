package com.garan.threshold.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garan.threshold.core.cascading.CascadingResolutionEngine
import com.garan.threshold.core.cascading.NfcTapOutcome
import com.garan.threshold.core.gatekeeper.IntentGatekeeper
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.model.GeneralSettings
import com.garan.threshold.core.model.NfcLatchState
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.model.ProfileCapabilityIssue
import com.garan.threshold.core.model.ProfileTrigger
import com.garan.threshold.core.model.ThresholdSettings
import com.garan.threshold.core.port.ClockSource
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.LaunchResult
import com.garan.threshold.core.port.LocationSource
import com.garan.threshold.core.port.NfcReaderSource
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.core.port.NoOpNfcReaderSource
import com.garan.threshold.core.port.NoOpScheduleAlarmScheduler
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
import com.garan.threshold.core.port.ProfileCapabilityChecker
import com.garan.threshold.core.port.ScheduleAlarmScheduler
import com.garan.threshold.core.port.SettingsRepository
import com.garan.threshold.core.port.SystemClockSource
import com.garan.threshold.core.search.ActionCandidate
import com.garan.threshold.core.search.AppSearchCandidate
import com.garan.threshold.core.search.FuzzyMatcher
import com.garan.threshold.core.search.SearchEngine
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.core.search.SearchResult
import com.garan.threshold.schedule.ScheduleTransitionNotifier
import com.garan.threshold.ui.launcher.DialerPackageResolver
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

data class ActiveFrictionGate(val app: SearchItem.App, val policy: FrictionPolicy)

data class QuickActionUiItem(
    val slotNumber: Int,
    val component: ComponentKey,
    val label: String,
    val accessState: AppAccessState,
)

data class SearchUiState(
    val query: String = "",
    val results: ImmutableList<SearchResult> = persistentListOf(),
    val isSearching: Boolean = false,
    val autoLaunchCandidate: SearchItem? = null,
    val activeFriction: ActiveFrictionGate? = null,
    val quickActions: ImmutableList<QuickActionUiItem> = persistentListOf(),
    val generalSettings: GeneralSettings = GeneralSettings(),
    val activeProfiles: ImmutableList<CustomProfile> = persistentListOf(),
    val capabilityIssues: ImmutableList<ProfileCapabilityIssue> = persistentListOf(),
)

class SearchViewModel(
    private val packageIndexSource: PackageIndexSource,
    private val hapticEngine: HapticEngine = NoOpHapticEngine,
    private val gatekeeper: IntentGatekeeper = IntentGatekeeper(),
    private val searchEngine: SearchEngine = SearchEngine(
        gatekeeper = gatekeeper,
        matcher = FuzzyMatcher(),
    ),
    defaultDialerPackage: String = DialerPackageResolver.FALLBACK_DIALER_PACKAGE,
    private val dialerPackageResolver: () -> String = { defaultDialerPackage },
    private val actions: List<ActionCandidate> = emptyList(),
    initialProfile: Profile = Profile(
        id = "ambient",
        name = "Ambient",
        defaultAccess = AppAccessState.Direct,
        appRules = emptyMap(),
    ),
    private val settingsRepository: SettingsRepository? = null,
    private val cascadingEngine: CascadingResolutionEngine = CascadingResolutionEngine(),
    private val clockSource: ClockSource = SystemClockSource,
    private val scheduleAlarmScheduler: ScheduleAlarmScheduler = NoOpScheduleAlarmScheduler,
    private val capabilityChecker: ProfileCapabilityChecker = ProfileCapabilityChecker { true },
    private val locationSource: LocationSource? = null,
    private val nfcReaderSource: NfcReaderSource = NoOpNfcReaderSource,
    private val nowEpochMillisProvider: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val resolvedDialerPackage: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dialerPackageResolver()
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val reconcileMutex = Mutex()

    @Volatile
    private var lastObservedSettings: ThresholdSettings? = null

    @Volatile
    private var previouslyActiveProfileIds: Set<String> = emptySet()

    @Volatile
    private var previouslyActiveTriggerIds: Set<String> = emptySet()

    @Volatile
    private var currentNfcLatches: Map<String, NfcLatchState> = emptyMap()

    init {
        if (settingsRepository != null) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.observeSettings().collect { settings ->
                    onSettingsChanged(settings)
                }
            }
            viewModelScope.launch(ioDispatcher) {
                ScheduleTransitionNotifier.events.collect {
                    if (hasEnabledGeoProfiles(lastObservedSettings)) {
                        locationSource?.refreshLocation()
                    }
                    reconcileMutex.withLock {
                        lastObservedSettings?.let { reconcileWorkingSetLocked(it) }
                    }
                }
            }
            if (locationSource != null) {
                viewModelScope.launch(ioDispatcher) {
                    locationSource.currentLocation.collect {
                        reconcileMutex.withLock {
                            lastObservedSettings?.let { reconcileWorkingSetLocked(it) }
                        }
                    }
                }
            }
        }
        viewModelScope.launch(ioDispatcher) {
            nfcReaderSource.tagScans.collect { rawUid ->
                processScannedNfcTag(rawUid)
            }
        }
    }

    @Volatile
    private var candidates: List<AppSearchCandidate> = emptyList()

    @Volatile
    private var activeProfile: Profile = initialProfile

    private val isIndexInitialized = AtomicBoolean(false)
    private var lastDecisiveCandidateKey: String? = null

    internal val isIndexLoaded: Boolean
        get() = isIndexInitialized.get()

    /**
     * Lazily loads the installed package index on first search interaction.
     * Must NOT be invoked eagerly during ambient home startup because [LauncherApps] label resolution
     * mmaps foreign APK resource tables into the process via Android's `ResourcesManager`.
     */
    fun ensureIndexLoaded() {
        if (!isIndexInitialized.compareAndSet(false, true)) return

        viewModelScope.launch(ioDispatcher) {
            val apps = packageIndexSource.getInstalledApps()
            reconcileMutex.withLock {
                candidates = apps.map { AppSearchCandidate(it.component, it.label) }
                val currentQuery = _uiState.value.query
                if (currentQuery.isNotBlank()) {
                    performSearch(currentQuery)
                }
            }

            packageIndexSource.observePackageChanges().collect { event ->
                if (event is PackageChangeEvent.Removed) {
                    settingsRepository?.removeApps(setOf(event.component))
                }
                reconcileMutex.withLock {
                    candidates = when (event) {
                        is PackageChangeEvent.Added -> {
                            candidates.filterNot { it.component == event.component } +
                                AppSearchCandidate(event.app.component, event.app.label)
                        }
                        is PackageChangeEvent.Removed -> {
                            candidates.filterNot { it.component == event.component }
                        }
                        is PackageChangeEvent.Changed -> {
                            candidates.filterNot { it.component == event.component } +
                                AppSearchCandidate(event.app.component, event.app.label)
                        }
                    }
                    lastObservedSettings?.let { reconcileWorkingSetLocked(it) }
                }
            }
        }
    }

    /**
     * Re-evaluates the working set of active custom profiles and effective app rules against
     * the current day, time, and location (called on minute ticks, activity resume, or alarm boundaries).
     */
    fun refreshScheduleEvaluation() {
        viewModelScope.launch(ioDispatcher) {
            if (hasEnabledGeoProfiles(lastObservedSettings)) {
                locationSource?.refreshLocation()
            }
            reconcileMutex.withLock {
                lastObservedSettings?.let { reconcileWorkingSetLocked(it) }
            }
        }
    }

    fun onNfcTagScanned(rawUid: String) {
        viewModelScope.launch(ioDispatcher) {
            processScannedNfcTag(rawUid)
        }
    }

    internal suspend fun processScannedNfcTag(rawUid: String) {
        val effective = lastObservedSettings ?: settingsRepository?.getSettings() ?: return
        if (hasEnabledGeoProfiles(effective) && locationSource?.currentLocation?.value == null) {
            locationSource?.refreshLocation()
        }
        reconcileMutex.withLock {
            lastObservedSettings = effective
            val now = clockSource.now()
            val nowMillis = nowEpochMillisProvider()
            val currentLocation = locationSource?.currentLocation?.value
            val tapResult = cascadingEngine.processNfcTagTap(
                rawTagUid = rawUid,
                customProfiles = effective.customProfiles,
                savedTriggers = effective.savedTriggers,
                currentDay = now.dayOfWeek,
                currentTime = now.timeOfDay,
                currentLocation = currentLocation,
                previouslyActiveTriggerIds = previouslyActiveTriggerIds,
                previouslyActiveProfileIds = previouslyActiveProfileIds,
                currentNfcLatches = currentNfcLatches,
                nowEpochMillis = nowMillis,
            )
            val latchesChanged = tapResult.updatedLatches != currentNfcLatches
            currentNfcLatches = tapResult.updatedLatches
            val updatedSettings = effective.copy(nfcLatches = currentNfcLatches)
            lastObservedSettings = updatedSettings
            if (latchesChanged) {
                settingsRepository?.updateNfcLatches(currentNfcLatches)
            }
            when (tapResult.outcome) {
                NfcTapOutcome.LATCHED_ON,
                NfcTapOutcome.UNLATCHED_OFF,
                -> hapticEngine.relaySnap()
                NfcTapOutcome.COMPANION_CONDITIONS_NOT_MET -> hapticEngine.resistanceDetent()
                NfcTapOutcome.UNRECOGNIZED_TAG -> Unit
            }
            scheduleAlarmScheduler.syncNextScheduleAlarm(
                profiles = updatedSettings.customProfiles,
                savedTriggers = updatedSettings.savedTriggers,
                nfcLatches = currentNfcLatches,
            )
            reconcileWorkingSetLocked(updatedSettings)
        }
    }

    internal suspend fun reconcileForTesting(settings: ThresholdSettings? = null) {
        if (candidates.isEmpty()) {
            val apps = packageIndexSource.getInstalledApps()
            isIndexInitialized.set(true)
            reconcileMutex.withLock {
                candidates = apps.map { AppSearchCandidate(it.component, it.label) }
            }
        }
        val effective = settings ?: settingsRepository?.getSettings() ?: lastObservedSettings ?: return
        if (hasEnabledGeoProfiles(effective)) {
            locationSource?.refreshLocation()
        }
        reconcileMutex.withLock {
            if (settings == null || settings.nfcLatches.isNotEmpty()) {
                currentNfcLatches = effective.nfcLatches
            }
            lastObservedSettings = effective
            reconcileWorkingSetLocked(effective)
            scheduleAlarmScheduler.syncNextScheduleAlarm(
                profiles = effective.customProfiles,
                savedTriggers = effective.savedTriggers,
                nfcLatches = currentNfcLatches,
            )
        }
    }

    private fun hasEnabledGeoProfiles(settings: ThresholdSettings?): Boolean {
        if (settings == null) return false
        val triggersById = settings.savedTriggersById
        return settings.customProfiles.any { profile ->
            profile.enabled && profile.resolvedTriggers(triggersById).any { it is ProfileTrigger.Geo }
        }
    }

    fun activateSearch() {
        ensureIndexLoaded()
        _uiState.update { it.copy(isSearching = true) }
    }

    fun dismissSearch() {
        lastDecisiveCandidateKey = null
        _uiState.update { current ->
            current.copy(
                query = "",
                results = persistentListOf(),
                isSearching = false,
                autoLaunchCandidate = null,
                activeFriction = null,
            )
        }
    }

    fun openFriction(app: SearchItem.App, policy: FrictionPolicy) {
        if (_uiState.value.generalSettings.requireFullTypingOfGuardedApps) {
            val query = _uiState.value.query.trim()
            if (!query.equals(app.label.trim(), ignoreCase = true)) {
                hapticEngine.resistanceDetent()
                return
            }
        }
        _uiState.update { it.copy(activeFriction = ActiveFrictionGate(app, policy)) }
    }

    fun dismissFriction() {
        _uiState.update { it.copy(activeFriction = null) }
    }

    fun markFirstRunHintSeen() {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository?.setHasSeenFirstRunHint(true)
            _uiState.update { it.copy(generalSettings = it.generalSettings.copy(hasSeenFirstRunHint = true)) }
        }
    }

    fun launchQuickAction(item: QuickActionUiItem, onDirectLaunch: () -> Unit) {
        val isDefaultDialer = (item.component.packageName == resolvedDialerPackage)
        val settings = lastObservedSettings
        val access = if (settings != null) {
            val now = clockSource.now()
            cascadingEngine.resolveAppAccess(
                component = item.component,
                baseProfile = settings.baseProfile,
                customProfiles = settings.customProfiles,
                currentDay = now.dayOfWeek,
                currentTime = now.timeOfDay,
                isDefaultDialer = isDefaultDialer,
                currentLocation = locationSource?.currentLocation?.value,
                previouslyActiveProfileIds = previouslyActiveProfileIds,
                savedTriggers = settings.savedTriggers,
                previouslyActiveTriggerIds = previouslyActiveTriggerIds,
                nfcLatchStates = currentNfcLatches,
                nowEpochMillis = nowEpochMillisProvider(),
                random = random,
            )
        } else {
            item.accessState
        }

        when (access) {
            is AppAccessState.Blocked -> {
                hapticEngine.resistanceDetent()
            }
            is AppAccessState.Guarded -> {
                _uiState.update {
                    it.copy(
                        activeFriction = ActiveFrictionGate(
                            SearchItem.App(item.component, item.label, access),
                            access.policy,
                        ),
                    )
                }
            }
            is AppAccessState.Direct -> {
                onDirectLaunch()
            }
        }
    }

    private suspend fun onSettingsChanged(settings: ThresholdSettings) {
        if (hasEnabledGeoProfiles(settings) && locationSource?.currentLocation?.value == null) {
            locationSource?.refreshLocation()
        }
        reconcileMutex.withLock {
            currentNfcLatches = settings.nfcLatches
            lastObservedSettings = settings
            reconcileWorkingSetLocked(settings)
            scheduleAlarmScheduler.syncNextScheduleAlarm(
                profiles = settings.customProfiles,
                savedTriggers = settings.savedTriggers,
                nfcLatches = currentNfcLatches,
            )
        }
    }

    private suspend fun reconcileWorkingSetLocked(settings: ThresholdSettings) {
        val now = clockSource.now()
        val nowMillis = nowEpochMillisProvider()
        val currentLocation = locationSource?.currentLocation?.value
        val previousLatches = currentNfcLatches
        val workingSet = cascadingEngine.resolveWorkingSet(
            baseProfile = settings.baseProfile,
            customProfiles = settings.customProfiles,
            currentDay = now.dayOfWeek,
            currentTime = now.timeOfDay,
            defaultDialerPackage = resolvedDialerPackage,
            additionalComponents = settings.baseProfile.quickActions,
            currentLocation = currentLocation,
            previouslyActiveProfileIds = previouslyActiveProfileIds,
            savedTriggers = settings.savedTriggers,
            previouslyActiveTriggerIds = previouslyActiveTriggerIds,
            nfcLatchStates = previousLatches,
            nowEpochMillis = nowMillis,
            random = random,
        )
        activeProfile = workingSet.effectiveProfile
        previouslyActiveProfileIds = workingSet.activeProfiles.map { it.id }.toSet()
        previouslyActiveTriggerIds = workingSet.activeTriggerIds
        currentNfcLatches = workingSet.activeNfcLatches
        if (currentNfcLatches != previousLatches) {
            lastObservedSettings = settings.copy(nfcLatches = currentNfcLatches)
            settingsRepository?.updateNfcLatches(currentNfcLatches)
            scheduleAlarmScheduler.syncNextScheduleAlarm(
                profiles = settings.customProfiles,
                savedTriggers = settings.savedTriggers,
                nfcLatches = currentNfcLatches,
            )
        }

        val quickKeys = settings.baseProfile.quickActions
        val quickItems = quickKeys.mapIndexedNotNull { index, comp ->
            val label = candidates.find { it.component == comp }?.label
                ?: packageIndexSource.getApp(comp)?.label
                ?: comp.packageName
            val access = workingSet.effectiveProfile.appRules[comp]
                ?: cascadingEngine.resolveAppAccess(
                    component = comp,
                    baseProfile = settings.baseProfile,
                    customProfiles = settings.customProfiles,
                    currentDay = now.dayOfWeek,
                    currentTime = now.timeOfDay,
                    isDefaultDialer = (comp.packageName == resolvedDialerPackage),
                    currentLocation = currentLocation,
                    previouslyActiveProfileIds = previouslyActiveProfileIds,
                    savedTriggers = settings.savedTriggers,
                    previouslyActiveTriggerIds = previouslyActiveTriggerIds,
                    nfcLatchStates = currentNfcLatches,
                    nowEpochMillis = nowMillis,
                    random = random,
                )
            QuickActionUiItem(
                slotNumber = index + 1,
                component = comp,
                label = label,
                accessState = access,
            )
        }.toImmutableList()

        val capabilityIssues = capabilityChecker
            .findCapabilityIssues(settings.customProfiles, settings.savedTriggers)
            .toImmutableList()

        val snapshotCandidates = candidates
        _uiState.update { current ->
            val currentQuery = current.query
            if (currentQuery.isNotBlank()) {
                val response = searchEngine.searchWithAutoLaunch(
                    query = currentQuery,
                    candidates = snapshotCandidates,
                    actions = actions,
                    profile = workingSet.effectiveProfile,
                    defaultDialerPackage = resolvedDialerPackage,
                )
                current.copy(
                    generalSettings = settings.general,
                    quickActions = quickItems,
                    activeProfiles = workingSet.activeProfiles.toImmutableList(),
                    capabilityIssues = capabilityIssues,
                    results = response.results.toImmutableList(),
                    autoLaunchCandidate = response.autoLaunchCandidate,
                )
            } else {
                current.copy(
                    generalSettings = settings.general,
                    quickActions = quickItems,
                    activeProfiles = workingSet.activeProfiles.toImmutableList(),
                    capabilityIssues = capabilityIssues,
                    results = persistentListOf(),
                    autoLaunchCandidate = null,
                )
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        ensureIndexLoaded()
        _uiState.update { state ->
            state.copy(
                query = newQuery,
                isSearching = state.isSearching || newQuery.isNotBlank(),
            )
        }
        performSearch(newQuery)
    }

    fun clearQuery() {
        dismissSearch()
    }

    fun setProfile(profile: Profile) {
        activeProfile = profile
        val currentQuery = _uiState.value.query
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery)
        }
    }

    fun evaluateLaunch(item: SearchItem): LaunchResult = when (item) {
        is SearchItem.App -> {
            val isDefaultDialer = (item.component.packageName == resolvedDialerPackage)
            val settings = lastObservedSettings
            val effectiveAccess = if (settings != null) {
                val now = clockSource.now()
                cascadingEngine.resolveAppAccess(
                    component = item.component,
                    baseProfile = settings.baseProfile,
                    customProfiles = settings.customProfiles,
                    currentDay = now.dayOfWeek,
                    currentTime = now.timeOfDay,
                    isDefaultDialer = isDefaultDialer,
                    currentLocation = locationSource?.currentLocation?.value,
                    previouslyActiveProfileIds = previouslyActiveProfileIds,
                    savedTriggers = settings.savedTriggers,
                    previouslyActiveTriggerIds = previouslyActiveTriggerIds,
                    nfcLatchStates = currentNfcLatches,
                    nowEpochMillis = nowEpochMillisProvider(),
                    random = random,
                )
            } else {
                gatekeeper.resolveAccess(item.component, activeProfile, resolvedDialerPackage)
            }

            val result = when (effectiveAccess) {
                is AppAccessState.Direct -> LaunchResult.Launched
                is AppAccessState.Guarded -> LaunchResult.FrictionRequired(effectiveAccess.policy)
                is AppAccessState.Blocked -> LaunchResult.Blocked
            }
            if (result is LaunchResult.FrictionRequired &&
                _uiState.value.generalSettings.requireFullTypingOfGuardedApps
            ) {
                val query = _uiState.value.query.trim()
                if (!query.equals(item.label.trim(), ignoreCase = true)) {
                    hapticEngine.resistanceDetent()
                    LaunchResult.Blocked
                } else {
                    result
                }
            } else {
                result
            }
        }
        is SearchItem.Action -> LaunchResult.Launched
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            lastDecisiveCandidateKey = null
            _uiState.update {
                it.copy(
                    results = persistentListOf(),
                    autoLaunchCandidate = null,
                )
            }
            return
        }

        val snapshotCandidates = candidates
        val response = searchEngine.searchWithAutoLaunch(
            query = query,
            candidates = snapshotCandidates,
            actions = actions,
            profile = activeProfile,
            defaultDialerPackage = resolvedDialerPackage,
        )

        val trimmed = query.trim()
        val decisiveCandidate = response.autoLaunchCandidate
            ?: if (response.results.size == 1 && trimmed.length >= 2) response.results.first().item else null
        val newDecisiveCandidateKey = decisiveCandidate?.key

        if (newDecisiveCandidateKey != null && newDecisiveCandidateKey != lastDecisiveCandidateKey) {
            hapticEngine.targetSnap()
        }
        lastDecisiveCandidateKey = newDecisiveCandidateKey

        _uiState.update {
            it.copy(
                results = response.results.toImmutableList(),
                autoLaunchCandidate = response.autoLaunchCandidate,
            )
        }
    }
}

package com.garan.threshold.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garan.threshold.core.gatekeeper.IntentGatekeeper
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionPolicy
import com.garan.threshold.core.model.GeneralSettings
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.model.ThresholdSettings
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.LaunchResult
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
import com.garan.threshold.core.port.SettingsRepository
import com.garan.threshold.core.search.ActionCandidate
import com.garan.threshold.core.search.AppSearchCandidate
import com.garan.threshold.core.search.FuzzyMatcher
import com.garan.threshold.core.search.SearchEngine
import com.garan.threshold.core.search.SearchItem
import com.garan.threshold.core.search.SearchResult
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val resolvedDialerPackage: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dialerPackageResolver()
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    @Volatile
    private var lastObservedSettings: ThresholdSettings? = null

    init {
        if (settingsRepository != null) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.observeSettings().collect { settings ->
                    onSettingsChanged(settings)
                }
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
            candidates = apps.map { AppSearchCandidate(it.component, it.label) }

            // Re-evaluate search if the user entered a query while the cold-start scan was in flight
            val currentQuery = _uiState.value.query
            if (currentQuery.isNotBlank()) {
                performSearch(currentQuery)
            }

            packageIndexSource.observePackageChanges().collect { event ->
                if (event is PackageChangeEvent.Removed) {
                    settingsRepository?.removeApps(setOf(event.component))
                }
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
                lastObservedSettings?.let { refreshQuickActions(it) }
                val activeQuery = _uiState.value.query
                if (activeQuery.isNotBlank()) {
                    performSearch(activeQuery)
                }
            }
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
        val access = lastObservedSettings?.baseProfile?.resolveAccess(
            component = item.component,
            isDefaultDialer = isDefaultDialer,
            random = random,
        ) ?: item.accessState

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
        lastObservedSettings = settings
        val appRules = settings.baseProfile.appAssignments.mapValues { (comp, _) ->
            settings.baseProfile.resolveAccess(
                component = comp,
                isDefaultDialer = (comp.packageName == resolvedDialerPackage),
            )
        }
        activeProfile = Profile(
            id = "base",
            name = "Base",
            defaultAccess = AppAccessState.Direct,
            appRules = appRules,
        )

        refreshQuickActions(settings)

        val currentQuery = _uiState.value.query
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery)
        }
    }

    private suspend fun refreshQuickActions(settings: ThresholdSettings) {
        val quickKeys = settings.baseProfile.quickActions
        val quickItems = quickKeys.mapIndexedNotNull { index, comp ->
            val label = candidates.find { it.component == comp }?.label
                ?: packageIndexSource.getApp(comp)?.label
                ?: comp.packageName
            val access = settings.baseProfile.resolveAccess(
                component = comp,
                isDefaultDialer = (comp.packageName == resolvedDialerPackage),
            )
            QuickActionUiItem(
                slotNumber = index + 1,
                component = comp,
                label = label,
                accessState = access,
            )
        }.toImmutableList()

        _uiState.update {
            it.copy(
                generalSettings = settings.general,
                quickActions = quickItems,
            )
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
            val effectiveAccess = lastObservedSettings?.baseProfile?.resolveAccess(
                component = item.component,
                isDefaultDialer = isDefaultDialer,
                random = random,
            ) ?: gatekeeper.resolveAccess(item.component, activeProfile, resolvedDialerPackage)

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

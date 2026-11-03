package com.garan.threshold.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garan.threshold.core.gatekeeper.IntentGatekeeper
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.LaunchResult
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
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

data class SearchUiState(
    val query: String = "",
    val results: ImmutableList<SearchResult> = persistentListOf(),
    val isSearching: Boolean = false,
    val autoLaunchCandidate: SearchItem? = null,
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val resolvedDialerPackage: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        dialerPackageResolver()
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    @Volatile
    private var candidates: List<AppSearchCandidate> = emptyList()

    @Volatile
    private var activeProfile: Profile = initialProfile

    private val isIndexInitialized = AtomicBoolean(false)
    private var lastTopCandidateKey: String? = null

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
        lastTopCandidateKey = null
        _uiState.update {
            SearchUiState(
                query = "",
                results = persistentListOf(),
                isSearching = false,
                autoLaunchCandidate = null,
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
        is SearchItem.App -> gatekeeper.evaluateLaunch(
            component = item.component,
            profile = activeProfile,
            defaultDialerPackage = resolvedDialerPackage,
        )
        is SearchItem.Action -> LaunchResult.Launched
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            lastTopCandidateKey = null
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

        val newTopCandidateKey = response.results.firstOrNull()?.item?.key
        if (newTopCandidateKey != null && newTopCandidateKey != lastTopCandidateKey) {
            hapticEngine.targetSnap()
        }
        lastTopCandidateKey = newTopCandidateKey

        _uiState.update {
            it.copy(
                results = response.results.toImmutableList(),
                autoLaunchCandidate = response.autoLaunchCandidate,
            )
        }
    }
}

package com.garan.threshold.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garan.threshold.core.gatekeeper.IntentGatekeeper
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.port.PackageChangeEvent
import com.garan.threshold.core.port.PackageIndexSource
import com.garan.threshold.core.search.ActionCandidate
import com.garan.threshold.core.search.AppSearchCandidate
import com.garan.threshold.core.search.FuzzyMatcher
import com.garan.threshold.core.search.SearchEngine
import com.garan.threshold.core.search.SearchResult
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

data class SearchUiState(
    val query: String = "",
    val results: ImmutableList<SearchResult> = persistentListOf(),
    val isSearching: Boolean = false,
)

class SearchViewModel(
    private val packageIndexSource: PackageIndexSource,
    private val searchEngine: SearchEngine = SearchEngine(
        gatekeeper = IntentGatekeeper(),
        matcher = FuzzyMatcher(),
    ),
    private val defaultDialerPackage: String = "com.android.dialer",
    private val actions: List<ActionCandidate> = emptyList(),
    initialProfile: Profile = Profile(
        id = "ambient",
        name = "Ambient",
        defaultAccess = AppAccessState.Direct,
        appRules = emptyMap(),
    ),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val candidates = mutableListOf<AppSearchCandidate>()
    private var activeProfile: Profile = initialProfile
    private var isIndexInitialized = false

    fun ensureIndexLoaded() {
        if (isIndexInitialized) return
        isIndexInitialized = true

        viewModelScope.launch(ioDispatcher) {
            val apps = packageIndexSource.getInstalledApps()
            synchronized(candidates) {
                candidates.clear()
                apps.mapTo(candidates) { AppSearchCandidate(it.component, it.label) }
            }

            packageIndexSource.observePackageChanges().collect { event ->
                synchronized(candidates) {
                    when (event) {
                        is PackageChangeEvent.Added -> {
                            candidates.removeAll { it.component == event.component }
                            candidates.add(AppSearchCandidate(event.app.component, event.app.label))
                        }
                        is PackageChangeEvent.Removed -> {
                            candidates.removeAll { it.component == event.component }
                        }
                        is PackageChangeEvent.Changed -> {
                            candidates.removeAll { it.component == event.component }
                            candidates.add(AppSearchCandidate(event.app.component, event.app.label))
                        }
                    }
                }
                val currentQuery = _uiState.value.query
                if (currentQuery.isNotBlank()) {
                    performSearch(currentQuery)
                }
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        ensureIndexLoaded()
        _uiState.update { it.copy(query = newQuery, isSearching = newQuery.isNotBlank()) }
        performSearch(newQuery)
    }

    fun clearQuery() {
        onQueryChanged("")
    }

    fun setProfile(profile: Profile) {
        activeProfile = profile
        val currentQuery = _uiState.value.query
        if (currentQuery.isNotBlank()) {
            performSearch(currentQuery)
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(results = persistentListOf()) }
            return
        }

        val snapshotCandidates = synchronized(candidates) { candidates.toList() }
        val searchResults = searchEngine.search(
            query = query,
            candidates = snapshotCandidates,
            actions = actions,
            profile = activeProfile,
            defaultDialerPackage = defaultDialerPackage,
        )

        _uiState.update { it.copy(results = searchResults.toImmutableList()) }
    }
}

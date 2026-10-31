package com.garan.threshold.core.search

import com.garan.threshold.core.gatekeeper.IntentGatekeeper
import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.Profile

/**
 * Core search engine orchestrating fuzzy matching, profile visibility filtering,
 * shortcut actions, and anti-impulse reverse search drag logic.
 */
class SearchEngine(private val gatekeeper: IntentGatekeeper, private val matcher: FuzzyMatcher) {

    /**
     * Searches across visible applications and launcher actions.
     */
    fun search(
        query: String,
        candidates: List<AppSearchCandidate>,
        actions: List<ActionCandidate>,
        profile: Profile,
        defaultDialerPackage: String,
    ): List<SearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val isActionFilter = trimmed.startsWith(":") || trimmed.startsWith(",")
        val cleanQuery = if (isActionFilter) trimmed.substring(1).trim() else trimmed
        if (cleanQuery.isEmpty() && isActionFilter) {
            // Return all actions when bare prefix ':' or ',' is entered
            return actions.map { action ->
                SearchResult(
                    item = SearchItem.Action(action.id, action.label),
                    matchResult = MatchResult(MatchType.PREFIX, 100),
                )
            }
        }

        val results = mutableListOf<SearchResult>()

        // 1. Actions
        actions.forEach { action ->
            val match = matcher.match(cleanQuery, action.shortcutKeyword)
                ?: matcher.match(cleanQuery, action.label)
            if (match != null) {
                results.add(SearchResult(SearchItem.Action(action.id, action.label), match))
            }
        }

        // 2. Apps (strictly excluded when query uses ':' or ',' action prefix)
        if (!isActionFilter) {
            val visibleComponents = gatekeeper.filterVisible(
                components = candidates.map { it.component },
                profile = profile,
                defaultDialerPackage = defaultDialerPackage,
            ).toSet()

            candidates.forEach { candidate ->
                if (candidate.component in visibleComponents) {
                    val match = matcher.match(cleanQuery, candidate.label)
                    if (match != null) {
                        val access = gatekeeper.resolveAccess(
                            component = candidate.component,
                            profile = profile,
                            defaultDialerPackage = defaultDialerPackage,
                        )
                        results.add(
                            SearchResult(
                                item = SearchItem.App(candidate.component, candidate.label, access),
                                matchResult = match,
                            ),
                        )
                    }
                }
            }
        }

        return results.sortedByDescending { it.matchResult.score }
    }

    /**
     * Executes [search] and evaluates whether a single unique match is eligible for auto-launch,
     * enforcing the **Reverse Search Drag** invariant for guarded apps.
     */
    fun searchWithAutoLaunch(
        query: String,
        candidates: List<AppSearchCandidate>,
        actions: List<ActionCandidate>,
        profile: Profile,
        defaultDialerPackage: String,
    ): SearchResponse {
        val results = search(query, candidates, actions, profile, defaultDialerPackage)
        var autoLaunch: SearchItem? = null

        if (results.size == 1) {
            val single = results.first()
            when (val item = single.item) {
                is SearchItem.App -> {
                    when (item.accessState) {
                        is AppAccessState.Direct -> {
                            // Direct apps auto-launch on unique match with query length >= 2 or exact match
                            if (query.trim().length >= 2 || single.matchResult.matchType == MatchType.EXACT) {
                                autoLaunch = item
                            }
                        }
                        is AppAccessState.Guarded -> {
                            // Reverse Search Drag: Guarded apps NEVER auto-launch on partial prefix.
                            // Requires deliberate full typed match.
                            if (single.matchResult.matchType == MatchType.EXACT) {
                                autoLaunch = item
                            }
                        }
                        is AppAccessState.Blocked -> {
                            // Invariant: Blocked apps never appear in search results
                            autoLaunch = null
                        }
                    }
                }
                is SearchItem.Action -> {
                    if (single.matchResult.matchType == MatchType.EXACT) {
                        autoLaunch = item
                    }
                }
            }
        }

        return SearchResponse(results = results, autoLaunchCandidate = autoLaunch)
    }
}

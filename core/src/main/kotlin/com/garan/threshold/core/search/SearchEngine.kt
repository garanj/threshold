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
     * Operates over pre-normalized candidate labels, avoiding per-candidate string allocations.
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
                    score = FuzzyMatcher.SCORE_PREFIX_BASE,
                    matchType = MatchType.PREFIX,
                )
            }
        }

        // Normalize query string ONCE per keystroke
        val normQuery = FuzzyMatcher.normalize(cleanQuery)
        val results = mutableListOf<SearchResult>()

        // 1. Actions
        for (i in actions.indices) {
            val action = actions[i]
            var score = matcher.matchNormalized(normQuery, action.normalizedKeyword)
            var matchType = matcher.getMatchType(score)

            if (score == FuzzyMatcher.NO_MATCH) {
                score = matcher.matchNormalized(normQuery, action.normalizedLabel)
                matchType = matcher.getMatchType(score)
            }

            if (score != FuzzyMatcher.NO_MATCH && matchType != null) {
                results.add(
                    SearchResult(
                        item = SearchItem.Action(action.id, action.label),
                        score = score,
                        matchType = matchType,
                    ),
                )
            }
        }

        // 2. Apps (strictly excluded when query uses ':' or ',' action prefix)
        if (!isActionFilter) {
            for (i in candidates.indices) {
                val candidate = candidates[i]
                val access = gatekeeper.resolveAccess(
                    component = candidate.component,
                    profile = profile,
                    defaultDialerPackage = defaultDialerPackage,
                )

                if (access != AppAccessState.Blocked) {
                    val score = matcher.matchNormalized(normQuery, candidate.normalizedLabel)
                    if (score != FuzzyMatcher.NO_MATCH) {
                        val matchType = matcher.getMatchType(score) ?: continue
                        results.add(
                            SearchResult(
                                item = SearchItem.App(candidate.component, candidate.label, access),
                                score = score,
                                matchType = matchType,
                            ),
                        )
                    }
                }
            }
        }

        return results.sortedByDescending { it.score }
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
                            if (query.trim().length >= 2 || single.matchType == MatchType.EXACT) {
                                autoLaunch = item
                            }
                        }
                        is AppAccessState.Guarded -> {
                            // Reverse Search Drag: Guarded apps NEVER auto-launch on partial prefix.
                            // Requires deliberate full typed match.
                            if (single.matchType == MatchType.EXACT) {
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
                    if (single.matchType == MatchType.EXACT) {
                        autoLaunch = item
                    }
                }
            }
        }

        return SearchResponse(results = results, autoLaunchCandidate = autoLaunch)
    }
}

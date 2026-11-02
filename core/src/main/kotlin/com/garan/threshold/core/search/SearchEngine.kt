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
    ): List<SearchResult> = searchTrimmed(query.trim(), candidates, actions, profile, defaultDialerPackage)

    private fun searchTrimmed(
        trimmed: String,
        candidates: List<AppSearchCandidate>,
        actions: List<ActionCandidate>,
        profile: Profile,
        defaultDialerPackage: String,
    ): List<SearchResult> {
        if (trimmed.isEmpty()) return emptyList()

        val firstChar = trimmed[0]
        val isActionFilter = firstChar == ':' || firstChar == ','
        val cleanQuery = if (isActionFilter) trimmed.substring(1).trim() else trimmed
        if (cleanQuery.isEmpty() && isActionFilter) {
            // Return all actions when bare prefix ':' or ',' is entered
            return actions.map { action ->
                SearchResult(
                    item = action.item,
                    score = FuzzyMatcher.SCORE_PREFIX_BASE,
                    matchType = MatchType.PREFIX,
                )
            }
        }

        // Normalize query string ONCE per keystroke (zero allocations for lowercase ASCII)
        val normQuery = FuzzyMatcher.normalize(cleanQuery)
        var results: ArrayList<SearchResult>? = null

        // 1. Actions
        for (i in actions.indices) {
            val action = actions[i]
            val keywordScore = matcher.matchNormalized(normQuery, action.normalizedKeyword)
            val labelScore = matcher.matchNormalized(normQuery, action.normalizedLabel)
            val score = maxOf(keywordScore, labelScore)

            if (score != FuzzyMatcher.NO_MATCH) {
                val matchType = matcher.getMatchType(score) ?: continue
                val list = results ?: ArrayList<SearchResult>(8).also { results = it }
                list.add(
                    SearchResult(
                        item = action.item,
                        score = score,
                        matchType = matchType,
                    ),
                )
            }
        }

        // 2. Apps (strictly excluded when query uses ':' or ',' action prefix)
        if (!isActionFilter) {
            if (profile.defaultAccess == AppAccessState.Blocked) {
                // Lockout / Bedtime fast-path: almost all apps are Blocked, so reject via Gatekeeper O(1) first
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
                            val list = results ?: ArrayList<SearchResult>(8).also { results = it }
                            list.add(
                                SearchResult(
                                    item = candidate.itemFor(access),
                                    score = score,
                                    matchType = matchType,
                                ),
                            )
                        }
                    }
                }
            } else {
                // Standard / Focus fast-path: fuzzy-match first, only lookup profile rules on matched candidates
                for (i in candidates.indices) {
                    val candidate = candidates[i]
                    val score = matcher.matchNormalized(normQuery, candidate.normalizedLabel)
                    if (score != FuzzyMatcher.NO_MATCH) {
                        val access = gatekeeper.resolveAccess(
                            component = candidate.component,
                            profile = profile,
                            defaultDialerPackage = defaultDialerPackage,
                        )
                        if (access != AppAccessState.Blocked) {
                            val matchType = matcher.getMatchType(score) ?: continue
                            val list = results ?: ArrayList<SearchResult>(8).also { results = it }
                            list.add(
                                SearchResult(
                                    item = candidate.itemFor(access),
                                    score = score,
                                    matchType = matchType,
                                ),
                            )
                        }
                    }
                }
            }
        }

        val finalResults = results ?: return emptyList()
        if (finalResults.size > 1) {
            finalResults.sortWith(SCORE_COMPARATOR)
        }
        return finalResults
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
        val trimmed = query.trim()
        val results = searchTrimmed(trimmed, candidates, actions, profile, defaultDialerPackage)
        var autoLaunch: SearchItem? = null

        if (results.size == 1) {
            val single = results[0]
            when (val item = single.item) {
                is SearchItem.App -> {
                    when (item.accessState) {
                        is AppAccessState.Direct -> {
                            // Direct apps auto-launch on unique match with query length >= 2 or exact match
                            if (trimmed.length >= 2 || single.matchType == MatchType.EXACT) {
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

    companion object {
        private val SCORE_COMPARATOR = Comparator<SearchResult> { a, b ->
            val scoreDiff = b.score.compareTo(a.score)
            if (scoreDiff != 0) {
                scoreDiff
            } else {
                val labelDiff = a.item.label.compareTo(b.item.label, ignoreCase = true)
                if (labelDiff != 0) {
                    labelDiff
                } else {
                    a.item.key.compareTo(b.item.key)
                }
            }
        }
    }
}

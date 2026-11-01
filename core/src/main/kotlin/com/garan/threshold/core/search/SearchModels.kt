package com.garan.threshold.core.search

import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey

data class AppSearchCandidate(
    val component: ComponentKey,
    val label: String,
    val normalizedLabel: String = FuzzyMatcher.normalize(label),
)

data class ActionCandidate(
    val id: String,
    val label: String,
    val shortcutKeyword: String,
    val normalizedKeyword: String = FuzzyMatcher.normalize(shortcutKeyword),
    val normalizedLabel: String = FuzzyMatcher.normalize(label),
)

sealed interface SearchItem {
    data class App(val component: ComponentKey, val label: String, val accessState: AppAccessState) : SearchItem

    data class Action(val id: String, val label: String) : SearchItem
}

data class SearchResult(val item: SearchItem, val score: Int, val matchType: MatchType)

data class SearchResponse(val results: List<SearchResult>, val autoLaunchCandidate: SearchItem?)

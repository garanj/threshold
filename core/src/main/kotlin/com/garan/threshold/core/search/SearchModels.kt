package com.garan.threshold.core.search

import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey

data class AppSearchCandidate(
    val component: ComponentKey,
    val label: String,
    val normalizedLabel: String = FuzzyMatcher.normalize(label),
) {
    val directItem: SearchItem.App = SearchItem.App(component, label, AppAccessState.Direct)

    fun itemFor(accessState: AppAccessState): SearchItem.App =
        if (accessState === AppAccessState.Direct) directItem else SearchItem.App(component, label, accessState)
}

data class ActionCandidate(
    val id: String,
    val label: String,
    val shortcutKeyword: String,
    val normalizedKeyword: String = FuzzyMatcher.normalize(shortcutKeyword),
    val normalizedLabel: String = FuzzyMatcher.normalize(label),
) {
    val item: SearchItem.Action = SearchItem.Action(id, label)
}

sealed interface SearchItem {
    val label: String
    val key: String

    data class App(val component: ComponentKey, override val label: String, val accessState: AppAccessState) :
        SearchItem {
        override val key: String
            get() = component.id
    }

    data class Action(val id: String, override val label: String) : SearchItem {
        override val key: String = "action:$id"
    }
}

data class SearchResult(val item: SearchItem, val score: Int, val matchType: MatchType)

data class SearchResponse(val results: List<SearchResult>, val autoLaunchCandidate: SearchItem?)

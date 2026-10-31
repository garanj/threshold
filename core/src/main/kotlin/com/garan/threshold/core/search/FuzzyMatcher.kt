package com.garan.threshold.core.search

import java.text.Normalizer

enum class MatchType {
    EXACT,
    PREFIX,
    WORD_BOUNDARY,
    SUBSTRING,
    SUBSEQUENCE,
}

data class MatchResult(val matchType: MatchType, val score: Int)

/**
 * High-performance, zero-allocation fuzzy matcher operating across normalized char sequences.
 */
class FuzzyMatcher {

    /**
     * Matches [query] against [target] and returns a scored [MatchResult], or null if no match.
     */
    fun match(query: CharSequence, target: CharSequence): MatchResult? {
        if (query.isEmpty()) return null

        val normQuery = normalize(query)
        val normTarget = normalize(target)

        if (normQuery.length > normTarget.length) return null

        // 1. Exact Match
        if (normQuery == normTarget) {
            return MatchResult(MatchType.EXACT, 100)
        }

        // 2. Prefix Match
        if (normTarget.startsWith(normQuery)) {
            val lengthPenalty = (normTarget.length - normQuery.length).coerceAtMost(19)
            return MatchResult(MatchType.PREFIX, 80 - lengthPenalty)
        }

        // 3. Word-Boundary / Acronym Match
        if (matchesWordBoundary(normQuery, normTarget)) {
            return MatchResult(MatchType.WORD_BOUNDARY, 60)
        }

        // 4. Contiguous Substring Match
        val subIndex = normTarget.indexOf(normQuery)
        if (subIndex >= 0) {
            val positionPenalty = subIndex.coerceAtMost(19)
            return MatchResult(MatchType.SUBSTRING, 40 - positionPenalty)
        }

        // 5. Fuzzy Subsequence Match
        if (matchesSubsequence(normQuery, normTarget)) {
            return MatchResult(MatchType.SUBSEQUENCE, 20)
        }

        return null
    }

    private fun matchesWordBoundary(query: String, target: String): Boolean {
        var queryIdx = 0
        var isWordStart = true

        for (i in target.indices) {
            val c = target[i]
            if (c == ' ' || c == '-' || c == '_' || c == '.') {
                isWordStart = true
                continue
            }

            if (isWordStart) {
                if (queryIdx < query.length && query[queryIdx] == c) {
                    queryIdx++
                    if (queryIdx == query.length) return true
                }
                isWordStart = false
            }
        }
        return false
    }

    private fun matchesSubsequence(query: String, target: String): Boolean {
        var queryIdx = 0
        for (i in target.indices) {
            if (target[i] == query[queryIdx]) {
                queryIdx++
                if (queryIdx == query.length) return true
            }
        }
        return false
    }

    companion object {
        /**
         * Normalizes a string by decomposing Unicode diacritics and converting to lowercase.
         * e.g. "Café" -> "cafe", "Über" -> "uber".
         */
        fun normalize(input: CharSequence): String {
            val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
            val sb = StringBuilder(decomposed.length)
            for (i in decomposed.indices) {
                val c = decomposed[i]
                if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
                    sb.append(c.lowercaseChar())
                }
            }
            return sb.toString()
        }
    }
}

package com.garan.threshold.core.search

import java.text.Normalizer

enum class MatchType {
    EXACT,
    PREFIX,
    WORD_BOUNDARY,
    SUBSTRING,
    SUBSEQUENCE,
}

/**
 * High-performance, zero-allocation fuzzy matcher operating across pre-normalized character sequences.
 */
class FuzzyMatcher {

    /**
     * Matches [normalizedQuery] against pre-normalized [normalizedTarget] with strictly zero heap allocations.
     *
     * @return A positive primitive score if matched (higher = better relevance), or [NO_MATCH] (-1) if no match.
     */
    fun matchNormalized(normalizedQuery: CharSequence, normalizedTarget: CharSequence): Int {
        val qLen = normalizedQuery.length
        val tLen = normalizedTarget.length

        if (qLen == 0 || qLen > tLen) return NO_MATCH

        // 1. Exact Match
        if (qLen == tLen && charSequenceEquals(normalizedQuery, normalizedTarget)) {
            return SCORE_EXACT
        }

        // 2. Prefix Match
        if (startsWith(normalizedTarget, normalizedQuery)) {
            val lengthPenalty = (tLen - qLen).coerceAtMost(9_999)
            return SCORE_PREFIX_BASE - lengthPenalty
        }

        // 3. Word-Boundary / Acronym Match
        if (matchesWordBoundary(normalizedQuery, normalizedTarget)) {
            return SCORE_WORD_BOUNDARY
        }

        // 4. Contiguous Substring Match
        val subIndex = indexOf(normalizedTarget, normalizedQuery)
        if (subIndex >= 0) {
            val positionPenalty = (subIndex * 10).coerceAtMost(9_999)
            return SCORE_SUBSTRING_BASE - positionPenalty
        }

        // 5. Fuzzy Subsequence Match
        if (matchesSubsequence(normalizedQuery, normalizedTarget)) {
            return SCORE_SUBSEQUENCE
        }

        return NO_MATCH
    }

    /**
     * Convenience method normalizing both query and target on the fly.
     * For zero-allocation inner loops, call [matchNormalized] with pre-normalized targets.
     */
    fun match(query: CharSequence, target: CharSequence): Int {
        if (query.isEmpty() || target.isEmpty()) return NO_MATCH
        val normQuery = normalize(query)
        val normTarget = normalize(target)
        return matchNormalized(normQuery, normTarget)
    }

    fun getMatchType(score: Int): MatchType? = when {
        score >= SCORE_EXACT -> MatchType.EXACT
        score >= SCORE_PREFIX_BASE - 10_000 -> MatchType.PREFIX
        score == SCORE_WORD_BOUNDARY -> MatchType.WORD_BOUNDARY
        score >= SCORE_SUBSTRING_BASE - 10_000 -> MatchType.SUBSTRING
        score == SCORE_SUBSEQUENCE -> MatchType.SUBSEQUENCE
        else -> null
    }

    private fun charSequenceEquals(a: CharSequence, b: CharSequence): Boolean {
        if (a.length != b.length) return false
        for (i in 0 until a.length) {
            if (a[i] != b[i]) return false
        }
        return true
    }

    private fun startsWith(target: CharSequence, prefix: CharSequence): Boolean {
        if (prefix.length > target.length) return false
        for (i in 0 until prefix.length) {
            if (target[i] != prefix[i]) return false
        }
        return true
    }

    private fun indexOf(target: CharSequence, query: CharSequence): Int {
        val qLen = query.length
        val maxIndex = target.length - qLen
        for (i in 0..maxIndex) {
            var matched = true
            for (j in 0 until qLen) {
                if (target[i + j] != query[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
        }
        return -1
    }

    private fun matchesWordBoundary(query: CharSequence, target: CharSequence): Boolean {
        var queryIdx = 0
        var isWordStart = true
        val qLen = query.length
        val tLen = target.length

        for (i in 0 until tLen) {
            val c = target[i]
            if (c == ' ' || c == '-' || c == '_' || c == '.') {
                isWordStart = true
                continue
            }

            if (isWordStart) {
                if (queryIdx < qLen && query[queryIdx] == c) {
                    queryIdx++
                    if (queryIdx == qLen) return true
                }
                isWordStart = false
            }
        }
        return false
    }

    private fun matchesSubsequence(query: CharSequence, target: CharSequence): Boolean {
        var queryIdx = 0
        val qLen = query.length
        val tLen = target.length

        for (i in 0 until tLen) {
            if (target[i] == query[queryIdx]) {
                queryIdx++
                if (queryIdx == qLen) return true
            }
        }
        return false
    }

    companion object {
        const val NO_MATCH: Int = -1
        const val SCORE_EXACT: Int = 1_000_000
        const val SCORE_PREFIX_BASE: Int = 800_000
        const val SCORE_WORD_BOUNDARY: Int = 600_000
        const val SCORE_SUBSTRING_BASE: Int = 400_000
        const val SCORE_SUBSEQUENCE: Int = 200_000

        /**
         * Normalizes a string by decomposing Unicode diacritics and converting to lowercase.
         * e.g. "Café" -> "cafe", "Über" -> "uber".
         */
        fun normalize(input: CharSequence): String {
            val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
            val sb = java.lang.StringBuilder(decomposed.length)
            for (i in 0 until decomposed.length) {
                val c = decomposed[i]
                if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) {
                    sb.append(c.lowercaseChar())
                }
            }
            return sb.toString()
        }
    }
}

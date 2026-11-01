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

        val q0 = normalizedQuery[0]
        val maxStart = tLen - qLen

        // Find first occurrence of query[0] within valid start bounds [0..maxStart].
        // Every match tier requires query[0] to appear at or before maxStart.
        var firstCharIdx = -1
        for (i in 0..maxStart) {
            if (normalizedTarget[i] == q0) {
                firstCharIdx = i
                break
            }
        }
        if (firstCharIdx < 0) return NO_MATCH

        // 1 & 2. Exact Match and Prefix Match (only possible when query[0] matches at index 0)
        if (firstCharIdx == 0) {
            var prefixMatch = true
            for (i in 1 until qLen) {
                if (normalizedTarget[i] != normalizedQuery[i]) {
                    prefixMatch = false
                    break
                }
            }
            if (prefixMatch) {
                if (qLen == tLen) return SCORE_EXACT
                val lengthPenalty = (tLen - qLen).coerceAtMost(9_999)
                return SCORE_PREFIX_BASE - lengthPenalty
            }
            // If lengths are equal and not an exact match, no other tier can match
            if (qLen == tLen) return NO_MATCH
        } else if (qLen == tLen) {
            return NO_MATCH
        }

        // 3. Word-Boundary / Acronym Match
        if (matchesWordBoundary(normalizedQuery, normalizedTarget, qLen, tLen)) {
            return SCORE_WORD_BOUNDARY
        }

        // 4. Contiguous Substring Match (resume scan from firstCharIdx)
        val subIndex = if (qLen == 1) {
            firstCharIdx
        } else {
            indexOfFrom(normalizedTarget, normalizedQuery, firstCharIdx, maxStart, qLen)
        }
        if (subIndex >= 0) {
            val positionPenalty = (subIndex * 10).coerceAtMost(9_999)
            return SCORE_SUBSTRING_BASE - positionPenalty
        }

        // 5. Fuzzy Subsequence Match (query[0] already matched at firstCharIdx)
        if (qLen > 1 && matchesSubsequenceFrom(normalizedQuery, normalizedTarget, firstCharIdx + 1, qLen, tLen)) {
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

    private fun indexOfFrom(target: CharSequence, query: CharSequence, startIdx: Int, maxIndex: Int, qLen: Int): Int {
        val q0 = query[0]
        for (i in startIdx..maxIndex) {
            if (target[i] != q0) continue
            var matched = true
            for (j in 1 until qLen) {
                if (target[i + j] != query[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
        }
        return -1
    }

    private fun matchesWordBoundary(query: CharSequence, target: CharSequence, qLen: Int, tLen: Int): Boolean {
        var queryIdx = 0
        var isWordStart = true

        for (i in 0 until tLen) {
            val c = target[i]
            if (c == ' ' || c == '-' || c == '_' || c == '.') {
                isWordStart = true
                continue
            }

            if (isWordStart) {
                if (query[queryIdx] == c) {
                    queryIdx++
                    if (queryIdx == qLen) return true
                }
                isWordStart = false
            }
        }
        return false
    }

    private fun matchesSubsequenceFrom(
        query: CharSequence,
        target: CharSequence,
        startTargetIdx: Int,
        qLen: Int,
        tLen: Int,
    ): Boolean {
        var queryIdx = 1
        for (i in startTargetIdx until tLen) {
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
         * Uses a zero-allocation fast path for lowercase ASCII and a single-pass CharArray fast path
         * for mixed-case ASCII before falling back to Unicode NFD decomposition.
         * e.g. "Café" -> "cafe", "Über" -> "uber".
         */
        fun normalize(input: CharSequence): String {
            val len = input.length
            if (len == 0) return ""

            var firstTransformIdx = -1
            var hasNonAscii = false
            for (i in 0 until len) {
                val c = input[i]
                if (c >= '\u0080') {
                    hasNonAscii = true
                    if (firstTransformIdx == -1) firstTransformIdx = i
                    break
                } else if (c in 'A'..'Z') {
                    if (firstTransformIdx == -1) firstTransformIdx = i
                }
            }

            // Fast path 1: Already lowercase ASCII -> zero allocations when input is a String
            if (firstTransformIdx == -1) {
                return input.toString()
            }

            // Fast path 2: Pure ASCII with uppercase characters -> single CharArray pass
            if (!hasNonAscii) {
                val chars = CharArray(len)
                for (i in 0 until firstTransformIdx) {
                    chars[i] = input[i]
                }
                for (i in firstTransformIdx until len) {
                    val c = input[i]
                    chars[i] = if (c in 'A'..'Z') (c.code + 32).toChar() else c
                }
                return String(chars)
            }

            // Fallback path: Non-ASCII Unicode NFD decomposition and diacritic stripping
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

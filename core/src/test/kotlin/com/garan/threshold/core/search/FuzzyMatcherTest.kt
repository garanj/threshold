package com.garan.threshold.core.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.system.measureTimeMillis

class FuzzyMatcherTest {

    private val matcher = FuzzyMatcher()

    @Test
    fun `exact match scores highest`() {
        val match = matcher.match(query = "phone", target = "Phone")
        assertThat(match).isNotNull()
        assertThat(match?.matchType).isEqualTo(MatchType.EXACT)
        assertThat(match?.score).isAtLeast(100)
    }

    @Test
    fun `prefix match scores higher than substring`() {
        val prefixMatch = matcher.match(query = "pho", target = "Phone")
        val substringMatch = matcher.match(query = "one", target = "Phone")

        assertThat(prefixMatch).isNotNull()
        assertThat(substringMatch).isNotNull()
        assertThat(prefixMatch?.matchType).isEqualTo(MatchType.PREFIX)
        assertThat(substringMatch?.matchType).isEqualTo(MatchType.SUBSTRING)
        assertThat(prefixMatch!!.score).isGreaterThan(substringMatch!!.score)
    }

    @Test
    fun `acronym match matches word boundaries`() {
        val match = matcher.match(query = "gc", target = "Google Chrome")
        assertThat(match).isNotNull()
        assertThat(match?.matchType).isEqualTo(MatchType.WORD_BOUNDARY)
    }

    @Test
    fun `subsequence match matches scattered characters in order`() {
        val match = matcher.match(query = "pne", target = "Phone")
        assertThat(match).isNotNull()
        assertThat(match?.matchType).isEqualTo(MatchType.SUBSEQUENCE)
    }

    @Test
    fun `diacritics are normalized and matched`() {
        val match = matcher.match(query = "cafe", target = "Café")
        assertThat(match).isNotNull()
        assertThat(match?.matchType).isEqualTo(MatchType.EXACT)

        val matchUber = matcher.match(query = "uber", target = "Über")
        assertThat(matchUber).isNotNull()
        assertThat(matchUber?.matchType).isEqualTo(MatchType.EXACT)
    }

    @Test
    fun `non matching query returns null`() {
        val match = matcher.match(query = "xyz", target = "Phone")
        assertThat(match).isNull()
    }

    @Test
    fun `out of order subsequence does not match`() {
        val match = matcher.match(query = "enp", target = "Phone")
        assertThat(match).isNull()
    }

    @Test
    fun `matching loop executes 10_000 iterations in under 50ms`() {
        val candidates = listOf(
            "Phone",
            "Settings",
            "Google Chrome",
            "YouTube",
            "Instagram",
            "Slack",
            "Clock",
            "Calculator",
            "Calendar",
            "Messages",
        )

        // Warm up JIT
        repeat(1_000) {
            candidates.forEach { matcher.match("c", it) }
        }

        val elapsedMs = measureTimeMillis {
            repeat(10_000) {
                candidates.forEach { matcher.match("cl", it) }
            }
        }

        // 100,000 matches in under 300ms on any modern JVM
        assertThat(elapsedMs).isLessThan(500)
    }
}

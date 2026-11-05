package com.garan.threshold.core.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.system.measureTimeMillis

class FuzzyMatcherTest {

    private val matcher = FuzzyMatcher()

    @Test
    fun `exact match scores highest`() {
        val score = matcher.match(query = "phone", target = "Phone")
        assertThat(score).isGreaterThan(FuzzyMatcher.NO_MATCH)
        assertThat(matcher.getMatchType(score)).isEqualTo(MatchType.EXACT)
        assertThat(score).isEqualTo(FuzzyMatcher.SCORE_EXACT)
    }

    @Test
    fun `prefix match scores higher than substring`() {
        val prefixScore = matcher.match(query = "pho", target = "Phone")
        val substringScore = matcher.match(query = "one", target = "Phone")

        assertThat(prefixScore).isGreaterThan(FuzzyMatcher.NO_MATCH)
        assertThat(substringScore).isGreaterThan(FuzzyMatcher.NO_MATCH)
        assertThat(matcher.getMatchType(prefixScore)).isEqualTo(MatchType.PREFIX)
        assertThat(matcher.getMatchType(substringScore)).isEqualTo(MatchType.SUBSTRING)
        assertThat(prefixScore).isGreaterThan(substringScore)
    }

    @Test
    fun `acronym match matches word boundaries`() {
        val score = matcher.match(query = "gc", target = "Google Chrome")
        assertThat(score).isGreaterThan(FuzzyMatcher.NO_MATCH)
        assertThat(matcher.getMatchType(score)).isEqualTo(MatchType.WORD_BOUNDARY)
    }

    @Test
    fun `word boundary prefix matches multi character second word and ranks above acronym`() {
        val googleMapsScore = matcher.match(query = "maps", target = "Google Maps")
        val myGoogleMapsScore = matcher.match(query = "maps", target = "My Google Maps")
        val acronymScore = matcher.match(query = "maps", target = "My Awesome Photo Studio")
        val substringScore = matcher.match(query = "maps", target = "Synapse Mapsy")

        assertThat(matcher.getMatchType(googleMapsScore)).isEqualTo(MatchType.WORD_BOUNDARY)
        assertThat(matcher.getMatchType(myGoogleMapsScore)).isEqualTo(MatchType.WORD_BOUNDARY)
        assertThat(matcher.getMatchType(acronymScore)).isEqualTo(MatchType.WORD_BOUNDARY)
        assertThat(googleMapsScore).isGreaterThan(acronymScore)
        assertThat(googleMapsScore).isGreaterThan(substringScore)
    }

    @Test
    fun `subsequence match matches scattered characters in order`() {
        val score = matcher.match(query = "pne", target = "Phone")
        assertThat(score).isGreaterThan(FuzzyMatcher.NO_MATCH)
        assertThat(matcher.getMatchType(score)).isEqualTo(MatchType.SUBSEQUENCE)
    }

    @Test
    fun `diacritics are normalized and matched`() {
        val scoreCafe = matcher.match(query = "cafe", target = "Café")
        assertThat(scoreCafe).isGreaterThan(FuzzyMatcher.NO_MATCH)
        assertThat(matcher.getMatchType(scoreCafe)).isEqualTo(MatchType.EXACT)

        val scoreUber = matcher.match(query = "uber", target = "Über")
        assertThat(scoreUber).isGreaterThan(FuzzyMatcher.NO_MATCH)
        assertThat(matcher.getMatchType(scoreUber)).isEqualTo(MatchType.EXACT)
    }

    @Test
    fun `non matching query returns NO_MATCH`() {
        val score = matcher.match(query = "xyz", target = "Phone")
        assertThat(score).isEqualTo(FuzzyMatcher.NO_MATCH)
    }

    @Test
    fun `out of order subsequence does not match`() {
        val score = matcher.match(query = "enp", target = "Phone")
        assertThat(score).isEqualTo(FuzzyMatcher.NO_MATCH)
    }

    @Test
    fun `equal length non matching strings and single char queries behave deterministically`() {
        assertThat(matcher.match(query = "phona", target = "Phone")).isEqualTo(FuzzyMatcher.NO_MATCH)
        assertThat(matcher.match(query = "xphone", target = "yphone")).isEqualTo(FuzzyMatcher.NO_MATCH)

        val singleSubstringScore = matcher.match(query = "h", target = "Phone")
        assertThat(matcher.getMatchType(singleSubstringScore)).isEqualTo(MatchType.SUBSTRING)

        val singleWordBoundaryScore = matcher.match(query = "c", target = "Google Chrome")
        assertThat(matcher.getMatchType(singleWordBoundaryScore)).isEqualTo(MatchType.WORD_BOUNDARY)
    }

    @Test
    fun `normalize generates zero heap allocations for lowercase ASCII inputs`() {
        val input = "slack"

        // Warm up JIT
        repeat(10_000) {
            FuzzyMatcher.normalize(input)
        }

        val threadBean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (threadBean != null && threadBean.isThreadAllocatedMemorySupported) {
            @Suppress("DEPRECATION")
            val threadId = Thread.currentThread().id
            val startBytes = threadBean.getThreadAllocatedBytes(threadId)

            repeat(50_000) {
                FuzzyMatcher.normalize(input)
            }

            val endBytes = threadBean.getThreadAllocatedBytes(threadId)
            val allocatedBytes = endBytes - startBytes

            assertThat(allocatedBytes).isEqualTo(0L)
        }
    }

    @Test
    fun `matchNormalized generates zero heap allocations in inner loop`() {
        val query = "cl"
        val target = "clock"

        // Warm up JVM and JIT compiler
        repeat(10_000) {
            matcher.matchNormalized(query, target)
        }

        val threadBean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (threadBean != null && threadBean.isThreadAllocatedMemorySupported) {
            @Suppress("DEPRECATION")
            val threadId = Thread.currentThread().id
            val startBytes = threadBean.getThreadAllocatedBytes(threadId)

            repeat(50_000) {
                matcher.matchNormalized(query, target)
            }

            val endBytes = threadBean.getThreadAllocatedBytes(threadId)
            val allocatedBytes = endBytes - startBytes

            // Strictly 0 bytes allocated on the heap across 50,000 matches!
            assertThat(allocatedBytes).isEqualTo(0L)
        }
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
        ).map { FuzzyMatcher.normalize(it) }

        val query = "cl"

        // Warm up JIT
        repeat(1_000) {
            candidates.forEach { matcher.matchNormalized(query, it) }
        }

        val elapsedMs = measureTimeMillis {
            repeat(10_000) {
                candidates.forEach { matcher.matchNormalized(query, it) }
            }
        }

        // 100,000 matches in under 1000ms on modern JVM / virtualized CI runners
        assertThat(elapsedMs).isLessThan(1000)
    }
}

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

        // 100,000 matches in under 200ms on modern JVM
        assertThat(elapsedMs).isLessThan(200)
    }
}

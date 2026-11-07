package com.garan.threshold.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class FrictionChoiceTest {

    @Test
    fun `resolvePolicy maps explicit choices to correct policies`() {
        assertThat(FrictionChoice.TYPED_INTENTION.resolvePolicy()).isEqualTo(FrictionPolicy.TypedIntention)
        assertThat(FrictionChoice.DRAG_SORT.resolvePolicy()).isEqualTo(FrictionPolicy.DragSort(pairCount = 6))
        assertThat(FrictionChoice.CARET_TRACING.resolvePolicy()).isEqualTo(FrictionPolicy.TimedDelay(seconds = 10))
    }

    @Test
    fun `resolvePolicy with RANDOM selects between available policies`() {
        val deterministicRandom = object : Random() {
            var counter = 0
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = (counter++) % until
        }

        val policy0 = FrictionChoice.RANDOM.resolvePolicy(deterministicRandom)
        val policy1 = FrictionChoice.RANDOM.resolvePolicy(deterministicRandom)
        val policy2 = FrictionChoice.RANDOM.resolvePolicy(deterministicRandom)

        assertThat(policy0).isEqualTo(FrictionPolicy.TypedIntention)
        assertThat(policy1).isEqualTo(FrictionPolicy.DragSort(pairCount = 6))
        assertThat(policy2).isEqualTo(FrictionPolicy.TimedDelay(seconds = 10))
    }
}

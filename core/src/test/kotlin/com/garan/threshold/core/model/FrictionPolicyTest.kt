package com.garan.threshold.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class FrictionPolicyTest {

    @Test
    fun `timed delay creates with valid positive seconds`() {
        val policy = FrictionPolicy.TimedDelay(seconds = 15)
        assertThat(policy.seconds).isEqualTo(15)
    }

    @Test
    fun `timed delay with non-positive seconds throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            FrictionPolicy.TimedDelay(seconds = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrictionPolicy.TimedDelay(seconds = -5)
        }
    }

    @Test
    fun `session budget creates with valid minute intervals`() {
        val policy = FrictionPolicy.SessionBudget(availableMinutes = listOf(5, 10, 20))
        assertThat(policy.availableMinutes).containsExactly(5, 10, 20).inOrder()
    }

    @Test
    fun `session budget with empty list throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            FrictionPolicy.SessionBudget(availableMinutes = emptyList())
        }
    }

    @Test
    fun `session budget with non-positive minutes throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            FrictionPolicy.SessionBudget(availableMinutes = listOf(5, 0, 15))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrictionPolicy.SessionBudget(availableMinutes = listOf(-1, 10))
        }
    }

    @Test
    fun `drag sort creates with valid pair count`() {
        val policy = FrictionPolicy.DragSort(pairCount = 6)
        assertThat(policy.pairCount).isEqualTo(6)
    }

    @Test
    fun `drag sort with invalid pair count throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            FrictionPolicy.DragSort(pairCount = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrictionPolicy.DragSort(pairCount = 7)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrictionPolicy.DragSort(pairCount = 11)
        }
    }
}

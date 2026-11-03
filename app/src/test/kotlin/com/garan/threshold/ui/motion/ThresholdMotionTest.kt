package com.garan.threshold.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ThresholdMotionTest {

    @Test
    fun opticalEasing_matchesStrictSensoryContractSpecs() {
        // Sensory Contract: cubic-bezier(0.16, 1.0, 0.3, 1.0)
        val easing = ThresholdMotion.OpticalEasing
        assertThat(easing).isInstanceOf(CubicBezierEasing::class.java)

        // Evaluate key interpolation points along the optical curve
        // Frame 0: 0.0
        assertThat(easing.transform(0.0f)).isEqualTo(0.0f)
        // Frame 1: 1.0
        assertThat(easing.transform(1.0f)).isEqualTo(1.0f)

        // High initial deceleration: by t = 0.5, transform should be heavily decelerated (> 0.85)
        assertThat(easing.transform(0.5f)).isGreaterThan(0.85f)
    }

    @Test
    fun motionDurations_strictlyEnforceSensoryBounds() {
        // Durations must strictly reside between 80ms and 140ms
        assertThat(ThresholdMotion.MIN_DURATION_MS).isEqualTo(80)
        assertThat(ThresholdMotion.MAX_DURATION_MS).isEqualTo(140)

        assertThat(ThresholdMotion.SHUTTER_OPEN_DURATION_MS)
            .isAtLeast(ThresholdMotion.MIN_DURATION_MS)
        assertThat(ThresholdMotion.SHUTTER_OPEN_DURATION_MS)
            .isAtMost(ThresholdMotion.MAX_DURATION_MS)

        assertThat(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS)
            .isAtLeast(ThresholdMotion.MIN_DURATION_MS)
        assertThat(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS)
            .isAtMost(ThresholdMotion.MAX_DURATION_MS)

        assertThat(ThresholdMotion.RESULT_REVEAL_DURATION_MS)
            .isAtLeast(ThresholdMotion.MIN_DURATION_MS)
        assertThat(ThresholdMotion.RESULT_REVEAL_DURATION_MS)
            .isAtMost(ThresholdMotion.MAX_DURATION_MS)
    }

    @Test
    fun opticalTween_enforcesDurationBoundaryGuardrails() {
        // Valid durations within [80ms..140ms] succeed
        val validSpec = ThresholdMotion.opticalTween<Float>(100)
        assertThat(validSpec).isNotNull()

        // Durations under 80ms or over 140ms violate the sensory contract
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdMotion.opticalTween<Float>(70)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThresholdMotion.opticalTween<Float>(150)
        }
    }
}

package com.garan.threshold.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.Alignment

/**
 * Optical motion contract for Threshold ("Optics, Not Cartoons").
 * Enforces strictly 120Hz-tuned decelerating optical curves `cubic-bezier(0.16, 1.0, 0.3, 1.0)`
 * and tight duration bounds strictly between 80ms and 140ms with zero spring overshoot.
 */
object ThresholdMotion {
    /**
     * Precision decelerating optical easing curve: `cubic-bezier(0.16, 1.0, 0.3, 1.0)`.
     */
    val OpticalEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)

    const val MIN_DURATION_MS = 80
    const val MAX_DURATION_MS = 140

    /**
     * Duration for expanding the Hairline Shutter into search mode.
     */
    const val SHUTTER_OPEN_DURATION_MS = 100

    /**
     * Duration for closing the Hairline Shutter back to ambient home mode.
     */
    const val SHUTTER_CLOSE_DURATION_MS = 80

    /**
     * Duration for candidate results to resolve/settle into view.
     */
    const val RESULT_REVEAL_DURATION_MS = 100

    /**
     * Creates an optical tween spec enforcing sensory contract bounds.
     */
    fun <T> opticalTween(durationMillis: Int = SHUTTER_OPEN_DURATION_MS): FiniteAnimationSpec<T> {
        require(durationMillis in MIN_DURATION_MS..MAX_DURATION_MS) {
            "Motion duration $durationMillis ms violates [$MIN_DURATION_MS ms..$MAX_DURATION_MS ms] sensory contract"
        }
        return tween(durationMillis = durationMillis, easing = OpticalEasing)
    }

    /**
     * Hairline Shutter Wipe enter transition (reveals downwards from 1dp hairline).
     */
    val ShutterWipeEnter: EnterTransition = expandVertically(
        animationSpec = opticalTween(SHUTTER_OPEN_DURATION_MS),
        expandFrom = Alignment.Top,
    ) + fadeIn(
        animationSpec = opticalTween(SHUTTER_OPEN_DURATION_MS),
    )

    /**
     * Hairline Shutter Wipe exit transition (collapses upwards into 1dp hairline).
     */
    val ShutterWipeExit: ExitTransition = shrinkVertically(
        animationSpec = opticalTween(SHUTTER_CLOSE_DURATION_MS),
        shrinkTowards = Alignment.Top,
    ) + fadeOut(
        animationSpec = opticalTween(SHUTTER_CLOSE_DURATION_MS),
    )

    /**
     * Result list candidate reveal transition.
     */
    val ResultRevealEnter: EnterTransition = expandVertically(
        animationSpec = opticalTween(RESULT_REVEAL_DURATION_MS),
        expandFrom = Alignment.Top,
    ) + fadeIn(
        animationSpec = opticalTween(RESULT_REVEAL_DURATION_MS),
    )

    /**
     * Result list candidate exit transition.
     */
    val ResultRevealExit: ExitTransition = shrinkVertically(
        animationSpec = opticalTween(SHUTTER_CLOSE_DURATION_MS),
        shrinkTowards = Alignment.Top,
    ) + fadeOut(
        animationSpec = opticalTween(SHUTTER_CLOSE_DURATION_MS),
    )
}

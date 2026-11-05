package com.garan.threshold.ui.friction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.garan.threshold.R
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import kotlinx.coroutines.withContext

private val CARET_DARK_GREY = Color(0xFF404040)
private const val CARET_ASPECT_RATIO = 1.515f

/**
 * Enforces a 1.0x time scale for intentional friction delays so that system-level
 * `animator_duration_scale = 0.0` (e.g. Battery Saver or Accessibility "Remove animations")
 * cannot bypass or fast-forward the 10-second checkpoint gate.
 */
private object UnscaledMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

/**
 * Full-screen meditative Caret Tracing friction checkpoint.
 *
 * Imposes an intentional ~10-second mechanical delay gate before launching guarded applications:
 * - Pass 1 (0 to ~3.33s): Forward trace (left to right) in dark grey.
 * - Pass 2 (~3.33s to ~6.66s): Reverse trace (right to left) in mid grey.
 * - Pass 3 (~6.66s to 10s): Forward trace (left to right) in pure white.
 *
 * Tactile micro-haptics fire at each apex notch ([HapticEngine.rotaryDetent]),
 * pass completion ([HapticEngine.targetSnap]), and full completion ([HapticEngine.relaySnap]).
 *
 * Back-swipe or tapping "Go back" immediately aborts the gate and resets progress.
 *
 * @param onConfirmOpen Dispatched when all 3 passes finish and user taps "Open".
 * @param onDismiss Dispatched when the user abandons the checkpoint.
 * @param hapticEngine Tactile feedback provider.
 * @param totalDurationMs Total time for the full 3-pass sequence (default 10,000ms).
 */
@Composable
fun CaretTracingSurface(
    onConfirmOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    totalDurationMs: Long = 10_000L,
) {
    BackHandler(onBack = onDismiss)

    val currentHapticEngine by rememberUpdatedState(hapticEngine)

    // Continuous progress from 0f to 3f across the 3 passes (0..1 = Pass 1, 1..2 = Pass 2, 2..3 = Pass 3)
    val totalProgress = remember { Animatable(0f) }

    var isComplete by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 14.dp.toPx() } }
    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val caretWidthPx = remember(density) { with(density) { 140.dp.toPx() } }
    val caretHeightPx = remember(caretWidthPx) { caretWidthPx / CARET_ASPECT_RATIO }
    val pass1Path = remember { Path() }
    val pass2Path = remember { Path() }
    val pass3Path = remember { Path() }

    LaunchedEffect(totalDurationMs) {
        withContext(UnscaledMotionDurationScale) {
            isComplete = false
            totalProgress.snapTo(0f)

            var firedPass1Apex = false
            var firedPass1End = false
            var firedPass2Apex = false
            var firedPass2End = false
            var firedPass3Apex = false

            totalProgress.animateTo(
                targetValue = 3f,
                animationSpec = tween(durationMillis = totalDurationMs.toInt(), easing = LinearEasing),
            ) {
                val v = value
                if (!firedPass1Apex && v >= 0.5f) {
                    firedPass1Apex = true
                    currentHapticEngine.rotaryDetent()
                }
                if (!firedPass1End && v >= 1.0f) {
                    firedPass1End = true
                    currentHapticEngine.targetSnap()
                }
                if (!firedPass2Apex && v >= 1.5f) {
                    firedPass2Apex = true
                    currentHapticEngine.rotaryDetent()
                }
                if (!firedPass2End && v >= 2.0f) {
                    firedPass2End = true
                    currentHapticEngine.targetSnap()
                }
                if (!firedPass3Apex && v >= 2.5f) {
                    firedPass3Apex = true
                    currentHapticEngine.rotaryDetent()
                }
            }

            currentHapticEngine.relaySnap()
            isComplete = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .testTag("caret_tracing_surface"),
    ) {
        // Centered Canvas Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .size(180.dp)
                    .testTag("caret_tracing_canvas"),
            ) {
                val progress = totalProgress.value
                val p1 = progress.coerceIn(0f, 1f)
                val p2 = (progress - 1f).coerceIn(0f, 1f)
                val p3 = (progress - 2f).coerceIn(0f, 1f)

                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val left = centerX - caretWidthPx / 2f
                val right = centerX + caretWidthPx / 2f
                val top = centerY - caretHeightPx / 2f
                val bottom = centerY + caretHeightPx / 2f

                fun getPoint(f: Float): Offset {
                    val clamped = f.coerceIn(0f, 1f)
                    return if (clamped <= 0.5f) {
                        val u = clamped / 0.5f
                        Offset(
                            x = left + u * (centerX - left),
                            y = bottom + u * (top - bottom),
                        )
                    } else {
                        val u = (clamped - 0.5f) / 0.5f
                        Offset(
                            x = centerX + u * (right - centerX),
                            y = top + u * (bottom - top),
                        )
                    }
                }

                fun drawSegment(targetPath: Path, startFrac: Float, endFrac: Float, color: Color) {
                    if (endFrac <= startFrac) return
                    targetPath.reset()
                    val pStart = getPoint(startFrac)
                    targetPath.moveTo(pStart.x, pStart.y)
                    if (startFrac < 0.5f && endFrac > 0.5f) {
                        targetPath.lineTo(centerX, top)
                    }
                    val pEnd = getPoint(endFrac)
                    targetPath.lineTo(pEnd.x, pEnd.y)
                    drawPath(targetPath, color = color, style = stroke)
                }

                // Pass 1: Dark Grey
                if (p1 > 0f) {
                    drawSegment(pass1Path, 0f, p1, CARET_DARK_GREY)
                }

                // Pass 2: Mid Grey (Additive reverse: right-to-left covering (1 - p2) to 1)
                if (p2 > 0f) {
                    drawSegment(pass2Path, 1f - p2, 1f, SubtleGray)
                }

                // Pass 3: Pure White (Additive forward: 0 to p3)
                if (p3 > 0f) {
                    drawSegment(pass3Path, 0f, p3, PureWhite)
                }
            }
        }

        // Bottom Action Button: "Go back" (SubtleGray) morphs into "Open" (PureWhite)
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            AnimatedContent(
                targetState = isComplete,
                transitionSpec = {
                    fadeIn(
                        animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_OPEN_DURATION_MS),
                    ) togetherWith
                        fadeOut(animationSpec = ThresholdMotion.opticalTween(ThresholdMotion.SHUTTER_CLOSE_DURATION_MS))
                },
                label = "CaretTracingActionButtonTransition",
            ) { complete ->
                Text(
                    text = stringResource(if (complete) R.string.action_open else R.string.action_go_back),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (complete) PureWhite else SubtleGray,
                    modifier = Modifier
                        .clickable {
                            if (complete) {
                                onConfirmOpen()
                            } else {
                                onDismiss()
                            }
                        }
                        .padding(vertical = 8.dp)
                        .testTag("caret_tracing_action_button"),
                )
            }
        }
    }
}

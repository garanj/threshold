package com.garan.threshold.ui.friction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.garan.threshold.R
import com.garan.threshold.core.friction.DragSortSession
import com.garan.threshold.core.friction.DropResult
import com.garan.threshold.core.friction.Vector2D
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.random.Random

private val GLYPH_DRAWABLES = listOf(
    R.drawable.ic_glyph_hexagon,
    R.drawable.ic_glyph_diamond,
    R.drawable.ic_glyph_triangle,
    R.drawable.ic_glyph_cross,
    R.drawable.ic_glyph_circle,
    R.drawable.ic_glyph_star,
)

private val TARGET_COLOR_FILTER = ColorFilter.tint(SubtleGray.copy(alpha = 0.45f))
private val DRAGGABLE_COLOR_FILTER = ColorFilter.tint(PureWhite)

/**
 * Full-screen sensory Drag-to-Match Icon Sorting friction checkpoint.
 *
 * Uses a single unified [Canvas] for both rendering and pointer input hit-testing so that:
 * 1. Target silhouettes and draggable glyphs share an identical pixel coordinate space.
 * 2. Drag gestures trigger draw-phase invalidation only (zero recomposition, zero relayout at 120Hz).
 * 3. Removing matched pairs never shifts Composable slot tables or corrupts gesture state.
 *
 * @param onConfirmOpen Dispatched when all pairs have been matched and user taps "Open".
 * @param onDismiss Dispatched when the user abandons the checkpoint (back key or "Go back").
 * @param hapticEngine Tactile feedback provider.
 * @param pairCount Number of glyph pairs to match (default 6).
 * @param seed Optional random seed for deterministic layout (e.g. screenshot tests).
 */
@Composable
fun DragSortSurface(
    onConfirmOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hapticEngine: HapticEngine = NoOpHapticEngine,
    pairCount: Int = 6,
    seed: Long? = null,
) {
    BackHandler(onBack = onDismiss)

    val currentHapticEngine by rememberUpdatedState(hapticEngine)
    val coroutineScope = rememberCoroutineScope()
    val glyphPainters = GLYPH_DRAWABLES.map { painterResource(id = it) }
    var isComplete by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .safeDrawingPadding()
            .testTag("drag_sort_surface"),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val density = LocalDensity.current
            val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(100f)
            val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(100f)

            val iconSizePx = with(density) { 40.dp.toPx() }
            val activeIconSizePx = with(density) { 46.dp.toPx() }
            val itemRadiusPx = with(density) { 28.dp.toPx() }
            val tolerancePx = with(density) { 28.dp.toPx() }
            val grabRadiusPx = with(density) { 36.dp.toPx() }

            val normalDrawSize = remember(iconSizePx) { Size(iconSizePx, iconSizePx) }
            val activeDrawSize = remember(activeIconSizePx) { Size(activeIconSizePx, activeIconSizePx) }

            val session = remember(widthPx, heightPx, pairCount, seed) {
                val random = if (seed != null) Random(seed) else Random.Default
                val pairs = DragSortSession.generateLayout(
                    pairCount = pairCount,
                    containerWidth = widthPx,
                    containerHeight = heightPx,
                    itemRadius = itemRadiusPx,
                    minPairSeparation = widthPx * 0.45f,
                    random = random,
                )
                DragSortSession(pairs)
            }

            LaunchedEffect(session) {
                isComplete = session.isComplete
            }

            // Synchronous center coordinates for each draggable glyph in Canvas pixel space.
            // Read only inside pointerInput and Canvas DrawScope to avoid recomposition during 120Hz drags.
            val currentPositions = remember(session) {
                mutableStateListOf<Offset>().apply {
                    for (pair in session.pairs) {
                        add(Offset(pair.startPosition.x, pair.startPosition.y))
                    }
                }
            }
            val snapBackJobs = remember(session) { arrayOfNulls<Job>(session.pairs.size) }
            var unmatchedIds by remember(session) { mutableStateOf(session.unmatchedIds.toSet()) }
            var activeDragId by remember(session) { mutableStateOf<Int?>(null) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("drag_sort_canvas")
                    .pointerInput(session, grabRadiusPx, tolerancePx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            // Find the closest unmatched draggable glyph within grabRadiusPx
                            var closestId: Int? = null
                            var closestDist = Float.MAX_VALUE
                            for (pair in session.pairs) {
                                val id = pair.id
                                if (id in unmatchedIds) {
                                    val pos = currentPositions[id]
                                    val dist = hypot(down.position.x - pos.x, down.position.y - pos.y)
                                    if (dist <= grabRadiusPx && dist < closestDist) {
                                        closestDist = dist
                                        closestId = id
                                    }
                                }
                            }

                            val draggedId = closestId ?: return@awaitEachGesture
                            snapBackJobs[draggedId]?.cancel()
                            activeDragId = draggedId
                            currentHapticEngine.rotaryDetent()

                            val completedNormally = drag(down.id) { change ->
                                val delta = change.positionChange()
                                change.consume()
                                val current = currentPositions[draggedId]
                                currentPositions[draggedId] = Offset(
                                    x = (current.x + delta.x).coerceIn(0f, widthPx),
                                    y = (current.y + delta.y).coerceIn(0f, heightPx),
                                )
                            }

                            activeDragId = null
                            val dropPos = currentPositions[draggedId]
                            val pair = session.pairs[draggedId]

                            if (completedNormally) {
                                val result = session.drop(
                                    id = draggedId,
                                    dropPosition = Vector2D(dropPos.x, dropPos.y),
                                    toleranceRadius = tolerancePx,
                                )
                                when (result) {
                                    is DropResult.Match -> {
                                        currentHapticEngine.relaySnap()
                                        unmatchedIds = session.unmatchedIds.toSet()
                                        if (session.isComplete) {
                                            isComplete = true
                                            currentHapticEngine.targetSnap()
                                        }
                                    }
                                    is DropResult.Mismatch -> {
                                        currentHapticEngine.resistanceDetent()
                                        val startOffset = dropPos
                                        val targetOffset = Offset(pair.startPosition.x, pair.startPosition.y)
                                        snapBackJobs[draggedId] = coroutineScope.launch {
                                            Animatable(0f).animateTo(
                                                targetValue = 1f,
                                                animationSpec = ThresholdMotion.opticalTween(120),
                                            ) {
                                                currentPositions[draggedId] = lerp(
                                                    start = startOffset,
                                                    stop = targetOffset,
                                                    fraction = value,
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Gesture cancelled: smoothly return to start position
                                val startOffset = dropPos
                                val targetOffset = Offset(pair.startPosition.x, pair.startPosition.y)
                                snapBackJobs[draggedId] = coroutineScope.launch {
                                    Animatable(0f).animateTo(
                                        targetValue = 1f,
                                        animationSpec = ThresholdMotion.opticalTween(120),
                                    ) {
                                        currentPositions[draggedId] = lerp(
                                            start = startOffset,
                                            stop = targetOffset,
                                            fraction = value,
                                        )
                                    }
                                }
                            }
                        }
                    },
            ) {
                val halfIconPx = iconSizePx * 0.5f
                val halfActiveIconPx = activeIconSizePx * 0.5f

                // 1. Fixed Target Silhouettes (Grey)
                for (pair in session.pairs) {
                    if (pair.id in unmatchedIds) {
                        val painter = glyphPainters[pair.id % glyphPainters.size]
                        translate(
                            left = pair.targetPosition.x - halfIconPx,
                            top = pair.targetPosition.y - halfIconPx,
                        ) {
                            with(painter) {
                                draw(size = normalDrawSize, colorFilter = TARGET_COLOR_FILTER)
                            }
                        }
                    }
                }

                // 2. Inactive Draggable Foreground Glyphs (White)
                for (pair in session.pairs) {
                    val id = pair.id
                    if (id in unmatchedIds && id != activeDragId) {
                        val pos = currentPositions[id]
                        val painter = glyphPainters[id % glyphPainters.size]
                        translate(
                            left = pos.x - halfIconPx,
                            top = pos.y - halfIconPx,
                        ) {
                            with(painter) {
                                draw(size = normalDrawSize, colorFilter = DRAGGABLE_COLOR_FILTER)
                            }
                        }
                    }
                }

                // 3. Actively Dragged Foreground Glyph (White, drawn on top in Z-order)
                val activeId = activeDragId
                if (activeId != null && activeId in unmatchedIds) {
                    val pos = currentPositions[activeId]
                    val painter = glyphPainters[activeId % glyphPainters.size]
                    translate(
                        left = pos.x - halfActiveIconPx,
                        top = pos.y - halfActiveIconPx,
                    ) {
                        with(painter) {
                            draw(size = activeDrawSize, colorFilter = DRAGGABLE_COLOR_FILTER)
                        }
                    }
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
                label = "ActionButtonTransition",
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
                        .testTag("drag_sort_action_button"),
                )
            }
        }
    }
}

package com.garan.threshold.ui.friction

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.garan.threshold.R
import com.garan.threshold.core.friction.DragSortSession
import com.garan.threshold.core.friction.DropResult
import com.garan.threshold.core.friction.GlyphPair
import com.garan.threshold.core.friction.Vector2D
import com.garan.threshold.core.port.HapticEngine
import com.garan.threshold.core.port.NoOpHapticEngine
import com.garan.threshold.ui.motion.ThresholdMotion
import com.garan.threshold.ui.theme.AmoledBlack
import com.garan.threshold.ui.theme.PureWhite
import com.garan.threshold.ui.theme.SubtleGray
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

private val GLYPH_DRAWABLES = listOf(
    R.drawable.ic_glyph_hexagon,
    R.drawable.ic_glyph_diamond,
    R.drawable.ic_glyph_triangle,
    R.drawable.ic_glyph_cross,
    R.drawable.ic_glyph_circle,
    R.drawable.ic_glyph_star,
)

/**
 * Full-screen sensory Drag-to-Match Icon Sorting friction checkpoint.
 *
 * @param onConfirmOpen Dispatched when all 6 pairs have been matched and user taps "Open".
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .safeDrawingPadding()
            .testTag("drag_sort_surface"),
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val bottomBarHeight = with(density) { 96.dp.toPx() }
        val heightPx = (constraints.maxHeight.toFloat() - bottomBarHeight).coerceAtLeast(100f)

        val itemRadiusPx = with(density) { 28.dp.toPx() }
        val tolerancePx = with(density) { 28.dp.toPx() }

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

        var unmatchedIds by remember(session) { mutableStateOf(session.unmatchedIds.toSet()) }
        var activeDragId by remember { mutableStateOf<Int?>(null) }
        val isComplete = unmatchedIds.isEmpty()

        // Canvas Area for glyphs
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
        ) {
            val halfTargetPx = with(density) { 20.dp.toPx() }

            // 1. Fixed Target Silhouettes (Grey)
            for (pair in session.pairs) {
                if (pair.id in unmatchedIds) {
                    val drawableRes = GLYPH_DRAWABLES[pair.id % GLYPH_DRAWABLES.size]
                    Icon(
                        painter = painterResource(id = drawableRes),
                        contentDescription = null,
                        tint = SubtleGray.copy(alpha = 0.45f),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("target_glyph_${pair.id}")
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    val x = (pair.targetPosition.x - halfTargetPx).roundToInt()
                                    val y = (pair.targetPosition.y - halfTargetPx).roundToInt()
                                    placeable.place(x, y)
                                }
                            },
                    )
                }
            }

            // 2. Draggable Foreground Glyphs (White)
            for (pair in session.pairs) {
                if (pair.id in unmatchedIds) {
                    DraggableGlyph(
                        pair = pair,
                        drawableRes = GLYPH_DRAWABLES[pair.id % GLYPH_DRAWABLES.size],
                        isActive = activeDragId == pair.id,
                        tolerancePx = tolerancePx,
                        hapticEngine = hapticEngine,
                        session = session,
                        onDragStarted = { activeDragId = pair.id },
                        onDropComplete = {
                            activeDragId = null
                            unmatchedIds = session.unmatchedIds.toSet()
                        },
                    )
                }
            }
        }

        // Bottom Action Button: "Go back" (SubtleGray) morphs into "Open" (PureWhite)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
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
                    text = if (complete) "Open" else "Go back",
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

@Composable
private fun DraggableGlyph(
    pair: GlyphPair,
    drawableRes: Int,
    isActive: Boolean,
    tolerancePx: Float,
    hapticEngine: HapticEngine,
    session: DragSortSession,
    onDragStarted: () -> Unit,
    onDropComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val halfItemPx = with(density) { 20.dp.toPx() }
    val fingerLiftPx = with(density) { 16.dp.toPx() }

    val position = remember(pair) {
        Animatable(
            initialValue = Offset(pair.startPosition.x, pair.startPosition.y),
            typeConverter = Offset.VectorConverter,
        )
    }

    Icon(
        painter = painterResource(id = drawableRes),
        contentDescription = null,
        tint = PureWhite,
        modifier = modifier
            .size(40.dp)
            .scale(if (isActive) 1.15f else 1.0f)
            .zIndex(if (isActive) 10f else 1f)
            .testTag("draggable_glyph_${pair.id}")
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val offsetY = if (isActive) -fingerLiftPx else 0f
                    val x = (position.value.x - halfItemPx).roundToInt()
                    val y = (position.value.y - halfItemPx + offsetY).roundToInt()
                    placeable.place(x, y)
                }
            }
            .pointerInput(pair.id) {
                detectDragGestures(
                    onDragStart = {
                        hapticEngine.rotaryDetent()
                        onDragStarted()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            position.snapTo(position.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        val visualY = position.value.y - fingerLiftPx
                        val dropCoord = Vector2D(position.value.x, visualY)
                        val result = session.drop(pair.id, dropCoord, tolerancePx)

                        when (result) {
                            is DropResult.Match -> {
                                hapticEngine.relaySnap()
                                onDropComplete()
                                if (session.isComplete) {
                                    hapticEngine.targetSnap()
                                }
                            }
                            is DropResult.Mismatch -> {
                                hapticEngine.resistanceDetent()
                                onDropComplete()
                                coroutineScope.launch {
                                    position.animateTo(
                                        targetValue = Offset(pair.startPosition.x, pair.startPosition.y),
                                        animationSpec = ThresholdMotion.opticalTween(120),
                                    )
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        onDropComplete()
                        coroutineScope.launch {
                            position.animateTo(
                                targetValue = Offset(pair.startPosition.x, pair.startPosition.y),
                                animationSpec = ThresholdMotion.opticalTween(120),
                            )
                        }
                    },
                )
            },
    )
}

package com.garan.threshold.core.friction

import kotlin.math.hypot
import kotlin.random.Random

/**
 * 2D vector for spatial coordinates on the friction canvas.
 */
data class Vector2D(val x: Float, val y: Float) {
    fun distanceTo(other: Vector2D): Float = hypot(x - other.x, y - other.y)
}

/**
 * Represents a pair consisting of a fixed target silhouette and a corresponding draggable glyph.
 */
data class GlyphPair(val id: Int, val targetPosition: Vector2D, val startPosition: Vector2D)

/**
 * Result of a drop interaction.
 */
sealed interface DropResult {
    data class Match(val id: Int) : DropResult
    data class Mismatch(val id: Int, val snapBackTo: Vector2D) : DropResult
}

/**
 * State machine managing the kinetic drag-to-match icon sorting friction checkpoint.
 */
class DragSortSession(val pairs: List<GlyphPair>) {
    private val _unmatchedIds = pairs.map { it.id }.toMutableSet()

    /**
     * Set of glyph IDs that have not yet been matched.
     */
    val unmatchedIds: Set<Int> get() = _unmatchedIds

    /**
     * Whether all glyph pairs have been matched.
     */
    val isComplete: Boolean get() = _unmatchedIds.isEmpty()

    /**
     * Evaluates a drop action at [dropPosition].
     *
     * @param id The ID of the dragged glyph.
     * @param dropPosition The coordinate where the drag was released.
     * @param toleranceRadius The maximum center-to-center distance to consider a match.
     */
    fun drop(id: Int, dropPosition: Vector2D, toleranceRadius: Float): DropResult {
        if (!_unmatchedIds.contains(id)) {
            val pair = pairs.firstOrNull { it.id == id }
            return DropResult.Mismatch(id, pair?.startPosition ?: Vector2D(0f, 0f))
        }

        val pair = pairs.first { it.id == id }
        val distance = dropPosition.distanceTo(pair.targetPosition)

        return if (distance <= toleranceRadius) {
            _unmatchedIds.remove(id)
            DropResult.Match(id)
        } else {
            DropResult.Mismatch(id, pair.startPosition)
        }
    }

    companion object {
        /**
         * Generates a randomized, non-overlapping spatial layout for [pairCount] glyph pairs.
         *
         * @param pairCount Number of pairs to generate.
         * @param containerWidth Available canvas width.
         * @param containerHeight Available canvas height.
         * @param itemRadius Bounding radius of each glyph to prevent collision.
         * @param minPairSeparation Minimum distance required between a pair's start and target points.
         * @param random Random instance.
         */
        fun generateLayout(
            pairCount: Int = 6,
            containerWidth: Float,
            containerHeight: Float,
            itemRadius: Float = 24f,
            minPairSeparation: Float = containerWidth * 0.5f,
            random: Random = Random.Default,
        ): List<GlyphPair> {
            val minPointDistance = itemRadius * 1.8f
            val minX = itemRadius
            val maxX = (containerWidth - itemRadius).coerceAtLeast(minX)
            val minY = itemRadius
            val maxY = (containerHeight - itemRadius).coerceAtLeast(minY)

            for (attempt in 0 until 50) {
                val placedPoints = mutableListOf<Vector2D>()
                val pairs = mutableListOf<GlyphPair>()
                var failed = false

                for (id in 0 until pairCount) {
                    // Find target point
                    var targetPoint: Vector2D? = null
                    for (tTry in 0 until 100) {
                        val candidate = Vector2D(
                            x = random.nextFloat() * (maxX - minX) + minX,
                            y = random.nextFloat() * (maxY - minY) + minY,
                        )
                        if (placedPoints.none { it.distanceTo(candidate) < minPointDistance }) {
                            targetPoint = candidate
                            break
                        }
                    }

                    if (targetPoint == null) {
                        failed = true
                        break
                    }
                    placedPoints.add(targetPoint)

                    // Find start point separated from target
                    var startPoint: Vector2D? = null
                    for (sTry in 0 until 100) {
                        val candidate = Vector2D(
                            x = random.nextFloat() * (maxX - minX) + minX,
                            y = random.nextFloat() * (maxY - minY) + minY,
                        )
                        if (candidate.distanceTo(targetPoint) >= minPairSeparation &&
                            placedPoints.none { it.distanceTo(candidate) < minPointDistance }
                        ) {
                            startPoint = candidate
                            break
                        }
                    }

                    if (startPoint == null) {
                        failed = true
                        break
                    }
                    placedPoints.add(startPoint)
                    pairs.add(GlyphPair(id = id, targetPosition = targetPoint, startPosition = startPoint))
                }

                if (!failed) {
                    return pairs
                }
            }

            // Fallback deterministic partitioned layout if random sampling fails under tight constraints
            val pairs = mutableListOf<GlyphPair>()
            val cols = 3
            val rows = (pairCount * 2 + cols - 1) / cols
            val cellW = (maxX - minX) / cols
            val cellH = (maxY - minY) / rows

            val gridPoints = mutableListOf<Vector2D>()
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    gridPoints.add(
                        Vector2D(
                            x = minX + c * cellW + cellW * 0.5f,
                            y = minY + r * cellH + cellH * 0.5f,
                        ),
                    )
                }
            }
            gridPoints.shuffle(random)

            for (id in 0 until pairCount) {
                val targetIndex = id
                val startIndex = gridPoints.size - 1 - id
                pairs.add(
                    GlyphPair(
                        id = id,
                        targetPosition = gridPoints[targetIndex],
                        startPosition = gridPoints[startIndex],
                    ),
                )
            }
            return pairs
        }
    }
}

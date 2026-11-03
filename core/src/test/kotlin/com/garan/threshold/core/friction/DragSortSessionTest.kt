package com.garan.threshold.core.friction

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class DragSortSessionTest {

    @Test
    fun `drop within tolerance matches and resolves pair`() {
        val pairs = listOf(
            GlyphPair(id = 0, targetPosition = Vector2D(100f, 100f), startPosition = Vector2D(300f, 500f)),
            GlyphPair(id = 1, targetPosition = Vector2D(200f, 100f), startPosition = Vector2D(100f, 500f)),
        )
        val session = DragSortSession(pairs)

        assertThat(session.isComplete).isFalse()
        assertThat(session.unmatchedIds).containsExactly(0, 1)

        val result = session.drop(id = 0, dropPosition = Vector2D(105f, 102f), toleranceRadius = 24f)

        assertThat(result).isEqualTo(DropResult.Match(id = 0))
        assertThat(session.unmatchedIds).containsExactly(1)
        assertThat(session.isComplete).isFalse()
    }

    @Test
    fun `drop outside tolerance returns Mismatch with original start position`() {
        val pair = GlyphPair(id = 0, targetPosition = Vector2D(100f, 100f), startPosition = Vector2D(300f, 500f))
        val session = DragSortSession(listOf(pair))

        val result = session.drop(id = 0, dropPosition = Vector2D(150f, 150f), toleranceRadius = 24f)

        assertThat(result).isEqualTo(DropResult.Mismatch(id = 0, snapBackTo = Vector2D(300f, 500f)))
        assertThat(session.unmatchedIds).containsExactly(0)
        assertThat(session.isComplete).isFalse()
    }

    @Test
    fun `drop on wrong target returns Mismatch`() {
        val pairs = listOf(
            GlyphPair(id = 0, targetPosition = Vector2D(100f, 100f), startPosition = Vector2D(300f, 500f)),
            GlyphPair(id = 1, targetPosition = Vector2D(200f, 100f), startPosition = Vector2D(100f, 500f)),
        )
        val session = DragSortSession(pairs)

        // Dropping icon 0 onto target 1's position
        val result = session.drop(id = 0, dropPosition = Vector2D(200f, 100f), toleranceRadius = 24f)

        assertThat(result).isEqualTo(DropResult.Mismatch(id = 0, snapBackTo = Vector2D(300f, 500f)))
        assertThat(session.unmatchedIds).containsExactly(0, 1)
    }

    @Test
    fun `session completes when all pairs are matched`() {
        val pairs = listOf(
            GlyphPair(id = 0, targetPosition = Vector2D(100f, 100f), startPosition = Vector2D(300f, 500f)),
            GlyphPair(id = 1, targetPosition = Vector2D(200f, 100f), startPosition = Vector2D(100f, 500f)),
        )
        val session = DragSortSession(pairs)

        session.drop(id = 0, dropPosition = Vector2D(100f, 100f), toleranceRadius = 24f)
        session.drop(id = 1, dropPosition = Vector2D(200f, 100f), toleranceRadius = 24f)

        assertThat(session.unmatchedIds).isEmpty()
        assertThat(session.isComplete).isTrue()
    }

    @Test
    fun `generateLayout creates requested count within bounds with separation and no overlap`() {
        val random = Random(42)
        val width = 380f
        val height = 700f
        val itemRadius = 24f
        val minSeparation = 200f

        val pairs = DragSortSession.generateLayout(
            pairCount = 6,
            containerWidth = width,
            containerHeight = height,
            itemRadius = itemRadius,
            minPairSeparation = minSeparation,
            random = random,
        )

        assertThat(pairs).hasSize(6)

        val allPoints = mutableListOf<Vector2D>()
        for (pair in pairs) {
            // Check boundary bounds
            assertThat(pair.targetPosition.x).isAtLeast(itemRadius)
            assertThat(pair.targetPosition.x).isAtMost(width - itemRadius)
            assertThat(pair.targetPosition.y).isAtLeast(itemRadius)
            assertThat(pair.targetPosition.y).isAtMost(height - itemRadius)

            assertThat(pair.startPosition.x).isAtLeast(itemRadius)
            assertThat(pair.startPosition.x).isAtMost(width - itemRadius)
            assertThat(pair.startPosition.y).isAtLeast(itemRadius)
            assertThat(pair.startPosition.y).isAtMost(height - itemRadius)

            // Check pairwise distance >= minSeparation
            val pairDistance = pair.startPosition.distanceTo(pair.targetPosition)
            assertThat(pairDistance).isAtLeast(minSeparation)

            allPoints.add(pair.targetPosition)
            allPoints.add(pair.startPosition)
        }

        // Check that no two points overlap (distance >= 2 * itemRadius)
        for (i in allPoints.indices) {
            for (j in (i + 1) until allPoints.size) {
                val distance = allPoints[i].distanceTo(allPoints[j])
                assertThat(distance).isAtLeast(itemRadius * 1.8f)
            }
        }
    }
}

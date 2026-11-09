package com.garan.threshold.core.model

import kotlin.random.Random

/**
 * User-configurable friction preference for guarded apps.
 */
enum class FrictionChoice {
    TYPED_INTENTION,
    DRAG_SORT,
    CARET_TRACING,
    RANDOM,
    ;

    /**
     * Resolves this choice to a concrete [FrictionPolicy].
     *
     * In the case of [RANDOM], selects nondeterministically among the 3 active friction mechanisms.
     */
    fun resolvePolicy(random: Random = Random.Default): FrictionPolicy = when (this) {
        TYPED_INTENTION -> FrictionPolicy.TypedIntention
        DRAG_SORT -> FrictionPolicy.DragSort(pairCount = 6)
        CARET_TRACING -> FrictionPolicy.TimedDelay(seconds = CARET_DEFAULT_SECONDS)
        RANDOM -> {
            val choices = listOf(
                FrictionPolicy.TypedIntention,
                FrictionPolicy.DragSort(pairCount = 6),
                FrictionPolicy.TimedDelay(seconds = CARET_DEFAULT_SECONDS),
            )
            choices[random.nextInt(choices.size)]
        }
    }

    companion object {
        const val CARET_DEFAULT_SECONDS = 17
    }
}

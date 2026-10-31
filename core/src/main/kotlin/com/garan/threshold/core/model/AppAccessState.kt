package com.garan.threshold.core.model

/**
 * The access tier governing how an application behaves when a profile is active.
 */
sealed interface AppAccessState {

    /**
     * The app launches immediately with zero friction delay.
     */
    data object Direct : AppAccessState

    /**
     * The app is accessible, but requires completing an intentional friction checkpoint before launch.
     *
     * @property policy The anti-impulse friction barrier to present.
     */
    data class Guarded(val policy: FrictionPolicy = FrictionPolicy.TypedIntention) : AppAccessState

    /**
     * The app is hidden from the launcher home canvas and search index, and cannot be launched.
     */
    data object Blocked : AppAccessState
}

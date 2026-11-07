package com.garan.threshold.core.model

/**
 * Configuration assignment for an application in a profile.
 */
sealed interface AppAssignment {

    /**
     * Standard/normal access with zero friction barrier.
     */
    data object Standard : AppAssignment

    /**
     * Guarded access presenting an anti-impulse friction barrier before launching.
     *
     * @property frictionOverride Optional app-level friction preference overriding the profile default.
     */
    data class Guarded(val frictionOverride: FrictionChoice? = null) : AppAssignment

    /**
     * Blocked from launching and hidden from launcher surfaces.
     */
    data object Blocked : AppAssignment
}

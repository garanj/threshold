package com.garan.threshold.core.model

/**
 * Defines the intentional friction barrier presented before launching a guarded application.
 */
sealed interface FrictionPolicy {

    /**
     * Requires the user to deliberately type a short intention or confirmation phrase before launch.
     */
    data object TypedIntention : FrictionPolicy

    /**
     * Requires waiting for a deliberate countdown delay before launch.
     *
     * @property seconds Countdown duration in seconds.
     */
    data class TimedDelay(val seconds: Int = 10) : FrictionPolicy {
        init {
            require(seconds > 0) { "Delay seconds must be positive, was $seconds" }
        }
    }

    /**
     * Prompts the user to commit to a pre-session time budget before launch.
     *
     * @property availableMinutes List of allowed session durations in minutes.
     */
    data class SessionBudget(val availableMinutes: List<Int> = listOf(5, 15, 30)) : FrictionPolicy {
        init {
            require(availableMinutes.isNotEmpty() && availableMinutes.all { it > 0 }) {
                "availableMinutes must contain positive integers"
            }
        }
    }
}

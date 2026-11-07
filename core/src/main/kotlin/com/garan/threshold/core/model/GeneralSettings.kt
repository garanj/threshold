package com.garan.threshold.core.model

/**
 * Global configuration settings for Threshold.
 *
 * @property showUnlocks Whether to display unlock count anti-metric on the home surface.
 * @property showRest Whether to display rest duration anti-metric on the home surface.
 * @property requireFullTypingOfGuardedApps When true, guarded apps in search results require typing their full name.
 * @property hasSeenFirstRunHint Whether the user has seen or dismissed the first-run onboarding hint.
 */
data class GeneralSettings(
    val showUnlocks: Boolean = true,
    val showRest: Boolean = true,
    val requireFullTypingOfGuardedApps: Boolean = false,
    val hasSeenFirstRunHint: Boolean = false,
)

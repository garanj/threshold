package com.garan.threshold.core.model

/**
 * Represents an installed launchable application component with its display label.
 *
 * @property component The unique component key (package, activity, user serial).
 * @property label The human-readable application label (e.g. "Phone", "Settings").
 */
data class InstalledApp(val component: ComponentKey, val label: String) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
    }
}

package com.garan.threshold.core.port

import com.garan.threshold.core.model.ComponentKey
import kotlinx.coroutines.flow.Flow

/**
 * Event representing lifecycle changes to installed application packages.
 */
sealed interface PackageChangeEvent {
    data class Added(val component: ComponentKey) : PackageChangeEvent
    data class Removed(val component: ComponentKey) : PackageChangeEvent
    data class Changed(val component: ComponentKey) : PackageChangeEvent
}

/**
 * Outbound port for querying and observing launchable application components installed on the system.
 * Implementations in `:data` or `:app` interface with Android's `LauncherApps` and `PackageManager`.
 */
interface PackageIndexSource {

    /**
     * Retrieves all launchable components currently installed across available user profiles.
     */
    suspend fun getInstalledComponents(): List<ComponentKey>

    /**
     * Observes real-time package install, uninstall, and update events.
     */
    fun observePackageChanges(): Flow<PackageChangeEvent>
}

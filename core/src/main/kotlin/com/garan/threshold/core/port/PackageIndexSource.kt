package com.garan.threshold.core.port

import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.InstalledApp
import kotlinx.coroutines.flow.Flow

/**
 * Event representing lifecycle changes to installed application packages.
 */
sealed interface PackageChangeEvent {
    data class Added(val app: InstalledApp) : PackageChangeEvent {
        val component: ComponentKey get() = app.component
    }

    data class Removed(val component: ComponentKey) : PackageChangeEvent

    data class Changed(val app: InstalledApp) : PackageChangeEvent {
        val component: ComponentKey get() = app.component
    }
}

/**
 * Outbound port for querying and observing launchable application components installed on the system.
 * Implementations in `:data` or `:app` interface with Android's `LauncherApps` and `PackageManager`.
 */
interface PackageIndexSource {

    /**
     * Retrieves all launchable applications currently installed across available user profiles.
     */
    suspend fun getInstalledApps(): List<InstalledApp>

    /**
     * Retrieves all launchable component keys currently installed across available user profiles.
     */
    suspend fun getInstalledComponents(): List<ComponentKey> = getInstalledApps().map { it.component }

    /**
     * Observes real-time package install, uninstall, and update events.
     */
    fun observePackageChanges(): Flow<PackageChangeEvent>
}

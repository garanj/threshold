package com.garan.threshold.core.port

import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.FrictionPolicy

/**
 * Result returned when attempting to launch an application component through Threshold's gatekeeper.
 */
sealed interface LaunchResult {
    /** The launch was approved and dispatched to the platform launcher. */
    data object Launched : LaunchResult

    /** The launch requires completing an intentional friction checkpoint. */
    data class FrictionRequired(val policy: FrictionPolicy) : LaunchResult

    /** The launch was rejected because the application is blocked under the active profile. */
    data object Blocked : LaunchResult
}

/**
 * Outbound port for mediating application launches, intentional friction gates, and
 * optional Device Policy Controller (DPC) kernel-level package suspension.
 */
interface AppAccessController {

    /**
     * Determines whether the specified [component] is permitted to launch under the active profile.
     */
    suspend fun canLaunch(component: ComponentKey): Boolean

    /**
     * Requests launching the specified [component], initiating friction checkpoints if guarded.
     */
    suspend fun requestLaunch(component: ComponentKey): LaunchResult

    /**
     * Determines whether Threshold is running with active Device Owner / DPC privileges.
     */
    suspend fun isDeviceOwner(): Boolean

    /**
     * Optional DPC-level package suspension (`setPackagesSuspended`).
     * Returns true if successfully applied; false if unsupported or not in DPC mode.
     */
    suspend fun setPackagesSuspended(components: Set<ComponentKey>, suspended: Boolean): Boolean
}

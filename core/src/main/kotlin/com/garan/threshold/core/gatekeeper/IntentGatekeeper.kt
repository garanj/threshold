package com.garan.threshold.core.gatekeeper

import com.garan.threshold.core.model.AppAccessState
import com.garan.threshold.core.model.ComponentKey
import com.garan.threshold.core.model.Profile
import com.garan.threshold.core.port.LaunchResult

/**
 * Gatekeeper engine that governs application visibility and launch intentions.
 *
 * Invariant: The system default dialer package is unconditionally evaluated as [AppAccessState.Direct]
 * and guaranteed to remain accessible across all profiles, focus states, and locks.
 */
class IntentGatekeeper {

    /**
     * Resolves the effective [AppAccessState] for the given [component] under [profile],
     * checking against the system's [defaultDialerPackage].
     */
    fun resolveAccess(component: ComponentKey, profile: Profile, defaultDialerPackage: String): AppAccessState {
        val isDefaultDialer = component.packageName.equals(defaultDialerPackage, ignoreCase = true)
        return profile.resolveAccess(component, isDefaultDialer = isDefaultDialer)
    }

    /**
     * Filters a list of installed [components], returning only those that are visible
     * under [profile] (`Direct` and `Guarded` tiers). Strictly excludes `Blocked` apps.
     */
    fun filterVisible(
        components: List<ComponentKey>,
        profile: Profile,
        defaultDialerPackage: String,
    ): List<ComponentKey> = components.filter { component ->
        resolveAccess(component, profile, defaultDialerPackage) != AppAccessState.Blocked
    }

    /**
     * Evaluates an explicit intention to launch [component] under [profile].
     *
     * @return [LaunchResult.Launched] if immediate launch is approved,
     *         [LaunchResult.FrictionRequired] if an anti-impulse checkpoint must be passed,
     *         [LaunchResult.Blocked] if the application is prohibited from launching.
     */
    fun evaluateLaunch(component: ComponentKey, profile: Profile, defaultDialerPackage: String): LaunchResult =
        when (val access = resolveAccess(component, profile, defaultDialerPackage)) {
            is AppAccessState.Direct -> LaunchResult.Launched
            is AppAccessState.Guarded -> LaunchResult.FrictionRequired(access.policy)
            is AppAccessState.Blocked -> LaunchResult.Blocked
        }
}

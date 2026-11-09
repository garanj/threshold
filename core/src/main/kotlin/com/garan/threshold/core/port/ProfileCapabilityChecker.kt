package com.garan.threshold.core.port

import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.ProfileCapability
import com.garan.threshold.core.model.ProfileCapabilityIssue

/**
 * Port for checking whether a required [ProfileCapability] is currently granted/enabled on the host OS.
 */
fun interface ProfileCapabilityChecker {
    fun isCapabilityGranted(capability: ProfileCapability): Boolean

    /**
     * Evaluates all enabled [profiles] in priority order and returns a [ProfileCapabilityIssue]
     * for each enabled profile that is missing one or more required capabilities.
     */
    fun findCapabilityIssues(profiles: List<CustomProfile>): List<ProfileCapabilityIssue> = profiles
        .filter { it.enabled }
        .sortedBy { it.priority }
        .mapNotNull { profile ->
            val missing = profile.requiredCapabilities
                .filterNot { isCapabilityGranted(it) }
                .toSet()
            if (missing.isEmpty()) null else ProfileCapabilityIssue(profile, missing)
        }
}

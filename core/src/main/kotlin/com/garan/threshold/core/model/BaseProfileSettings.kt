package com.garan.threshold.core.model

import kotlin.random.Random

/**
 * Base app configuration defining default friction, quick action slots, and app assignments.
 *
 * @property defaultFriction The default friction policy applied to guarded apps without an explicit override.
 * @property appAssignments Explicit per-app assignments (`Blocked`, `Guarded`, or `Standard`).
 * @property quickActions Pinned app component keys displayed on the home surface (up to 7 items).
 */
data class BaseProfileSettings(
    val defaultFriction: FrictionChoice = FrictionChoice.TYPED_INTENTION,
    val appAssignments: Map<ComponentKey, AppAssignment> = emptyMap(),
    val quickActions: List<ComponentKey> = emptyList(),
) {
    init {
        require(quickActions.size <= MAX_QUICK_ACTIONS) {
            "Quick actions cannot exceed $MAX_QUICK_ACTIONS slots, had ${quickActions.size}"
        }
        require(quickActions.distinct().size == quickActions.size) {
            "Quick actions cannot contain duplicates: $quickActions"
        }
    }

    /**
     * Resolves the effective [AppAccessState] for a given [component].
     *
     * Invariant: The system default dialer (`isDefaultDialer = true`) is permanently and
     * unconditionally evaluated as [AppAccessState.Direct].
     */
    fun resolveAccess(
        component: ComponentKey,
        isDefaultDialer: Boolean,
        random: Random = Random.Default,
    ): AppAccessState {
        if (isDefaultDialer) {
            return AppAccessState.Direct
        }
        val assignment = appAssignments[component]
            ?: appAssignments.entries.firstOrNull { it.key.packageName == component.packageName }?.value
            ?: AppAssignment.Standard

        return when (assignment) {
            is AppAssignment.Standard -> AppAccessState.Direct
            is AppAssignment.Blocked -> AppAccessState.Blocked
            is AppAssignment.Guarded -> {
                val choice = assignment.frictionOverride ?: defaultFriction
                AppAccessState.Guarded(choice.resolvePolicy(random))
            }
        }
    }

    companion object {
        const val MAX_QUICK_ACTIONS = 7
    }
}

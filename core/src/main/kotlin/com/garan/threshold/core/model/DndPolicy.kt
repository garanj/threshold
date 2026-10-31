package com.garan.threshold.core.model

/**
 * Domain policy representing Do-Not-Disturb and notification behavior for a profile.
 */
enum class DndPolicy {
    /** Notifications behave per normal system settings without interruption. */
    Off,

    /** Only priority contacts and alarms interrupt. */
    PriorityOnly,

    /** Only scheduled alarms interrupt. */
    AlarmsOnly,

    /** Total silence; zero interruptions. */
    TotalSilence,
}

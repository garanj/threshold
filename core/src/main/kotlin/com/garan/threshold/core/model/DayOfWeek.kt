package com.garan.threshold.core.model

/**
 * Days of the week in pure Kotlin, independent of platform date/time APIs.
 */
enum class DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
    ;

    /**
     * Returns the preceding day of the week (with cyclic wrap-around from Monday to Sunday).
     */
    fun previousDay(): DayOfWeek = when (this) {
        MONDAY -> SUNDAY
        TUESDAY -> MONDAY
        WEDNESDAY -> TUESDAY
        THURSDAY -> WEDNESDAY
        FRIDAY -> THURSDAY
        SATURDAY -> FRIDAY
        SUNDAY -> SATURDAY
    }

    /**
     * Returns the following day of the week (with cyclic wrap-around from Sunday to Monday).
     */
    fun nextDay(): DayOfWeek = when (this) {
        MONDAY -> TUESDAY
        TUESDAY -> WEDNESDAY
        WEDNESDAY -> THURSDAY
        THURSDAY -> FRIDAY
        FRIDAY -> SATURDAY
        SATURDAY -> SUNDAY
        SUNDAY -> MONDAY
    }
}

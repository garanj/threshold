package com.garan.threshold.core.model

/**
 * 24-hour time representation in pure Kotlin (hour: 0..23, minute: 0..59).
 */
data class TimeOfDay(val hour: Int, val minute: Int) : Comparable<TimeOfDay> {

    init {
        require(hour in 0..23) { "Hour must be 0..23, was $hour" }
        require(minute in 0..59) { "Minute must be 0..59, was $minute" }
    }

    /**
     * Minutes elapsed since start of day (00:00).
     */
    val totalMinutes: Int = hour * 60 + minute

    override fun compareTo(other: TimeOfDay): Int = totalMinutes.compareTo(other.totalMinutes)

    /**
     * Formats this time as standard 24-hour HH:mm string with leading zeros.
     */
    fun formatted(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

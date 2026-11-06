package com.garan.threshold.core.model

/**
 * Immutable state snapshot representing device resting and pickup metrics.
 * Designed for calming, non-judgmental awareness on the home canvas.
 *
 * @property restDurationMs Duration in milliseconds that the device was locked/sleeping prior to current unlock session.
 *                          Null if no prior sleep timestamp was recorded (e.g. cold launch).
 * @property unlockCount Number of device unlock events accumulated during the current calendar day.
 */
data class AntiMetrics(val restDurationMs: Long? = null, val unlockCount: Int = 0)

/**
 * Pure Kotlin formatter formatting anti-metrics into monospaced, mechanical tabular readout.
 * Pure port-and-adapter compliant: zero `android.*` imports.
 */
object AntiMetricsFormatter {

    fun formatRestDuration(durationMs: Long): String {
        val clamped = durationMs.coerceAtLeast(0L)
        if (clamped < 60_000L) {
            return "REST < 1M"
        }
        val totalMinutes = clamped / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        val days = hours / 24L
        val remainingHours = hours % 24L

        return when {
            days > 0 -> if (remainingHours > 0) "REST ${days}D ${remainingHours}H" else "REST ${days}D"
            hours > 0 -> if (minutes > 0) "REST ${hours}H ${minutes}M" else "REST ${hours}H"
            else -> "REST ${minutes}M"
        }
    }

    fun formatUnlocks(count: Int): String = when (count) {
        1 -> "1 UNLOCK"
        else -> "$count UNLOCKS"
    }

    fun formatStatus(metrics: AntiMetrics, showUnlocks: Boolean = true, showRest: Boolean = true): String {
        val restPart = if (showRest) metrics.restDurationMs?.let { formatRestDuration(it) } else null
        val unlockPart = if (showUnlocks) formatUnlocks(metrics.unlockCount) else null

        return when {
            restPart != null && unlockPart != null -> "$restPart  ·  $unlockPart"
            restPart != null -> restPart
            unlockPart != null -> unlockPart
            else -> ""
        }
    }
}

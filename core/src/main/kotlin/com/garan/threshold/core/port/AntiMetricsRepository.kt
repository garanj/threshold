package com.garan.threshold.core.port

import com.garan.threshold.core.model.AntiMetrics
import kotlinx.coroutines.flow.StateFlow

/**
 * Outbound port for recording and observing device sleep and unlock anti-metrics.
 * Driven strictly by lifecycle and broadcast events with zero background polling or timers.
 */
interface AntiMetricsRepository {

    /**
     * Flow of current anti-metrics snapshot, updated synchronously upon unlock/resume.
     */
    val metrics: StateFlow<AntiMetrics>

    /**
     * Records a screen-off event (`ACTION_SCREEN_OFF`).
     *
     * @param timestampMs Epoch millisecond when screen turned off.
     */
    fun onScreenOff(timestampMs: Long = System.currentTimeMillis())

    /**
     * Records a device unlock / launcher resume event (`ACTION_USER_PRESENT` / onResume).
     * Calculates rest duration since last screen-off, handles calendar day rollover,
     * and increments the daily unlock counter.
     *
     * @param timestampMs Epoch millisecond when user unlocked.
     */
    fun onUnlock(timestampMs: Long = System.currentTimeMillis())

    /**
     * Re-evaluates whether the calendar day has rolled over (e.g. across midnight while the
     * screen remained awake) and resets the daily unlock count to 0 if a new day has started.
     *
     * @param timestampMs Current epoch millisecond.
     */
    fun refreshForCurrentDate(timestampMs: Long = System.currentTimeMillis()) {}
}

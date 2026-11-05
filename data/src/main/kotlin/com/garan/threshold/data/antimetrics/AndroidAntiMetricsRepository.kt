package com.garan.threshold.data.antimetrics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import com.garan.threshold.core.model.AntiMetrics
import com.garan.threshold.core.port.AntiMetricsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Android implementation of [AntiMetricsRepository].
 * Backed by lightweight SharedPreferences for instantaneous Frame 0 read availability without coroutine lag.
 *
 * Power Efficiency Guarantee:
 * - Listens exclusively to OS display pipeline events (ACTION_SCREEN_OFF, ACTION_USER_PRESENT, ACTION_DATE_CHANGED).
 * - Zero background polling, zero WakeLocks, zero AlarmManager wakeups.
 * - While the device sleeps, CPU usage is 0%.
 */
class AndroidAntiMetricsRepository(
    context: Context,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
    registerSystemReceiver: Boolean = true,
) : AntiMetricsRepository {

    private val appContext = context.applicationContext ?: context
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _metrics = MutableStateFlow(loadInitialMetrics())
    override val metrics: StateFlow<AntiMetrics> = _metrics.asStateFlow()

    init {
        synchronized(Companion) {
            activeInstance = this
            if (registerSystemReceiver && activeReceiver == null) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }
                val newReceiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        when (intent?.action) {
                            Intent.ACTION_SCREEN_OFF -> activeInstance?.onScreenOff()
                            Intent.ACTION_USER_PRESENT -> activeInstance?.onUnlock()
                            Intent.ACTION_DATE_CHANGED,
                            Intent.ACTION_TIME_CHANGED,
                            Intent.ACTION_TIMEZONE_CHANGED,
                            -> activeInstance?.refreshForCurrentDate()
                        }
                    }
                }
                appContext.registerReceiver(newReceiver, filter)
                activeReceiver = newReceiver
            }
        }
    }

    /**
     * Unregisters the shared process-scoped [BroadcastReceiver] and clears static instance references.
     */
    fun unregister() {
        synchronized(Companion) {
            activeReceiver?.let {
                try {
                    appContext.unregisterReceiver(it)
                } catch (_: IllegalArgumentException) {
                    // Already unregistered
                }
                activeReceiver = null
            }
            if (activeInstance === this) {
                activeInstance = null
            }
        }
    }

    private fun loadInitialMetrics(): AntiMetrics {
        val storedDate = prefs.getString(KEY_LAST_UNLOCK_DATE, null)
        val todayDate = LocalDate.now(zoneIdProvider()).toString()
        val count = if (storedDate == todayDate) {
            prefs.getInt(KEY_UNLOCK_COUNT, 0)
        } else {
            0
        }
        val lastRest = if (prefs.contains(KEY_LAST_REST_DURATION)) {
            prefs.getLong(KEY_LAST_REST_DURATION, 0L)
        } else {
            null
        }
        return AntiMetrics(
            restDurationMs = lastRest,
            unlockCount = count,
        )
    }

    override fun onScreenOff(timestampMs: Long) {
        prefs.edit()
            .putLong(KEY_SCREEN_OFF_TIMESTAMP, timestampMs)
            .putLong(KEY_SCREEN_OFF_ELAPSED, elapsedRealtimeProvider())
            .apply()
    }

    override fun onUnlock(timestampMs: Long) {
        val lastScreenOff = prefs.getLong(KEY_SCREEN_OFF_TIMESTAMP, -1L)
        val lastElapsedOff = prefs.getLong(KEY_SCREEN_OFF_ELAPSED, -1L)
        val lastUnlockTimestamp = prefs.getLong(KEY_LAST_UNLOCK_TIMESTAMP, 0L)

        // If no intervening screen-off occurred and this broadcast arrived within the debounce window,
        // it is a duplicate broadcast (e.g. Pixel two-stage biometric dismiss pipeline).
        if (lastScreenOff == -1L &&
            lastUnlockTimestamp > 0L &&
            abs(timestampMs - lastUnlockTimestamp) < DEBOUNCE_WINDOW_MS
        ) {
            return
        }

        val currentElapsed = elapsedRealtimeProvider()
        val restDurationMs = when {
            lastScreenOff > 0L && timestampMs >= lastScreenOff -> timestampMs - lastScreenOff
            lastScreenOff > 0L && lastElapsedOff > 0L && currentElapsed >= lastElapsedOff ->
                currentElapsed - lastElapsedOff
            else -> _metrics.value.restDurationMs
        }

        val todayDate = LocalDate.ofInstant(Instant.ofEpochMilli(timestampMs), zoneIdProvider()).toString()
        val storedDate = prefs.getString(KEY_LAST_UNLOCK_DATE, null)
        val currentCount = prefs.getInt(KEY_UNLOCK_COUNT, 0)

        val newCount = if (storedDate == todayDate) {
            currentCount + 1
        } else {
            1
        }

        val editor = prefs.edit()
            .putString(KEY_LAST_UNLOCK_DATE, todayDate)
            .putLong(KEY_LAST_UNLOCK_TIMESTAMP, timestampMs)
            .putInt(KEY_UNLOCK_COUNT, newCount)
            .remove(KEY_SCREEN_OFF_TIMESTAMP)
            .remove(KEY_SCREEN_OFF_ELAPSED)

        if (restDurationMs != null) {
            editor.putLong(KEY_LAST_REST_DURATION, restDurationMs)
        }
        editor.apply()

        _metrics.value = AntiMetrics(
            restDurationMs = restDurationMs,
            unlockCount = newCount,
        )
    }

    override fun refreshForCurrentDate(timestampMs: Long) {
        val todayDate = LocalDate.ofInstant(Instant.ofEpochMilli(timestampMs), zoneIdProvider()).toString()
        val storedDate = prefs.getString(KEY_LAST_UNLOCK_DATE, null)
        if (storedDate != null && storedDate != todayDate) {
            prefs.edit()
                .putString(KEY_LAST_UNLOCK_DATE, todayDate)
                .putInt(KEY_UNLOCK_COUNT, 0)
                .apply()
            _metrics.value = _metrics.value.copy(unlockCount = 0)
        }
    }

    companion object {
        @Volatile
        private var activeReceiver: BroadcastReceiver? = null

        @Volatile
        private var activeInstance: AndroidAntiMetricsRepository? = null

        private const val PREFS_NAME = "threshold_anti_metrics"
        private const val KEY_SCREEN_OFF_TIMESTAMP = "key_screen_off_timestamp"
        private const val KEY_SCREEN_OFF_ELAPSED = "key_screen_off_elapsed"
        private const val KEY_LAST_UNLOCK_DATE = "key_last_unlock_date"
        private const val KEY_LAST_UNLOCK_TIMESTAMP = "key_last_unlock_timestamp"
        private const val KEY_UNLOCK_COUNT = "key_unlock_count"
        private const val KEY_LAST_REST_DURATION = "key_last_rest_duration"

        /**
         * Window in milliseconds within which duplicate [Intent.ACTION_USER_PRESENT] broadcasts
         * (such as Pixel's two-stage biometric dismiss pipeline) are ignored.
         */
        const val DEBOUNCE_WINDOW_MS = 2000L
    }
}

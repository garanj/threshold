package com.garan.threshold.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.garan.threshold.core.cascading.ScheduleHorizonCalculator
import com.garan.threshold.core.model.CustomProfile
import com.garan.threshold.core.model.NfcLatchState
import com.garan.threshold.core.model.SavedTrigger
import com.garan.threshold.core.port.ClockSource
import com.garan.threshold.core.port.ScheduleAlarmScheduler
import com.garan.threshold.core.port.SystemClockSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * In-process event bus notifying active UI / ViewModels when a schedule alarm boundary fires
 * or system time/timezone changes.
 */
object ScheduleTransitionNotifier {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyTransition() {
        _events.tryEmit(Unit)
    }
}

/**
 * Android [AlarmManager] implementation of [ScheduleAlarmScheduler].
 *
 * Computes the single next chronological schedule transition across all enabled custom profiles
 * and registers an exact wakeup alarm (`setExactAndAllowWhileIdle` when permitted, with a safe
 * fallback to `setAndAllowWhileIdle`).
 */
class AndroidScheduleAlarmScheduler(
    private val context: Context,
    private val clockSource: ClockSource = SystemClockSource,
    private val horizonCalculator: ScheduleHorizonCalculator = ScheduleHorizonCalculator(),
    private val nowEpochMillisProvider: () -> Long = {
        ZonedDateTime.now().truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli()
    },
) : ScheduleAlarmScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun syncNextScheduleAlarm(profiles: List<CustomProfile>) {
        syncNextScheduleAlarm(profiles = profiles, savedTriggers = emptyList(), nfcLatches = emptyMap())
    }

    override fun syncNextScheduleAlarm(
        profiles: List<CustomProfile>,
        savedTriggers: List<SavedTrigger>,
        nfcLatches: Map<String, NfcLatchState>,
    ) {
        val manager = alarmManager ?: return
        val pendingIntent = createAlarmPendingIntent(context)
        val now = clockSource.now()
        val nextTransition = horizonCalculator.findNextTransition(
            customProfiles = profiles,
            currentDay = now.dayOfWeek,
            currentTime = now.timeOfDay,
            savedTriggers = savedTriggers,
        )

        val currentMinuteFloorMillis = nowEpochMillisProvider()
        val scheduleTriggerAtMillis = nextTransition?.let {
            currentMinuteFloorMillis + it.minutesFromNow * 60_000L
        }
        val earliestNfcExpiryMillis = nfcLatches.values
            .mapNotNull { it.expiresAtEpochMillis }
            .filter { it > currentMinuteFloorMillis }
            .minOrNull()

        val triggerAtMillis = listOfNotNull(scheduleTriggerAtMillis, earliestNfcExpiryMillis).minOrNull()
        if (triggerAtMillis == null) {
            manager.cancel(pendingIntent)
            return
        }

        if (canScheduleExactAlarms()) {
            runCatching {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }.onFailure {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } else {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    companion object {
        const val ACTION_SCHEDULE_TRANSITION_ALARM =
            "com.garan.threshold.action.SCHEDULE_TRANSITION_ALARM"
        private const val ALARM_REQUEST_CODE = 5601

        fun createAlarmPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ACTION_SCHEDULE_TRANSITION_ALARM
                setPackage(context.packageName)
            }
            return PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

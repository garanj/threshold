package com.garan.threshold.schedule

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.nfc.NfcAdapter
import com.garan.threshold.ThresholdAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver handling custom profile schedule boundary alarms, system clock/timezone
 * changes, boot completion, and `SCHEDULE_EXACT_ALARM` permission state changes.
 *
 * On each trigger:
 * 1. Emits a transition signal on [ScheduleTransitionNotifier] so any live UI/ViewModel
 *    immediately re-evaluates the working set of apps and active profiles.
 * 2. Reads persisted custom profiles and schedules the next chronological boundary alarm.
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in HANDLED_ACTIONS) return

        ScheduleTransitionNotifier.notifyTransition()

        val pendingResult = goAsync()
        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = ThresholdAppContainer.from(appContext)
                val settings = container.settingsRepository.getSettings()
                container.scheduleAlarmScheduler.syncNextScheduleAlarm(
                    profiles = settings.customProfiles,
                    savedTriggers = settings.savedTriggers,
                    nfcLatches = settings.nfcLatches,
                )
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private val HANDLED_ACTIONS = setOf(
            AndroidScheduleAlarmScheduler.ACTION_SCHEDULE_TRANSITION_ALARM,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            LocationManager.PROVIDERS_CHANGED_ACTION,
            LocationManager.MODE_CHANGED_ACTION,
            NfcAdapter.ACTION_ADAPTER_STATE_CHANGED,
        )
    }
}

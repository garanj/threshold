package com.garan.threshold.schedule

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import androidx.core.content.ContextCompat
import com.garan.threshold.core.model.ProfileCapability
import com.garan.threshold.core.port.ProfileCapabilityChecker

/**
 * Android implementation of [ProfileCapabilityChecker] that inspects OS permissions
 * and hardware state using fast in-memory/Binder-cached system checks.
 */
class AndroidProfileCapabilityChecker(
    private val context: Context,
    private val exactAlarmOverride: (() -> Boolean?)? = null,
) : ProfileCapabilityChecker {

    override fun isCapabilityGranted(capability: ProfileCapability): Boolean = when (capability) {
        ProfileCapability.EXACT_ALARM -> {
            val overrideValue = exactAlarmOverride?.invoke()
            if (overrideValue != null) {
                overrideValue
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.canScheduleExactAlarms() ?: true
            } else {
                true
            }
        }
        ProfileCapability.BACKGROUND_LOCATION -> {
            val fineGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val bgGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            fineGranted && bgGranted
        }
        ProfileCapability.NFC_HARDWARE_ENABLED -> {
            val adapter = NfcAdapter.getDefaultAdapter(context)
            adapter != null && adapter.isEnabled
        }
    }
}

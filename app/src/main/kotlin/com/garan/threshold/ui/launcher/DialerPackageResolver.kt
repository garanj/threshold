package com.garan.threshold.ui.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager

/**
 * Resolves the active default dialer package on the device to uphold Prime Invariant #2
 * (Universal Dialer AllowList Safety Invariant).
 */
object DialerPackageResolver {
    const val FALLBACK_DIALER_PACKAGE = "com.android.dialer"

    fun resolveDefaultDialerPackage(
        context: Context,
        telecomManager: TelecomManager? = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager,
        packageManager: PackageManager = context.packageManager,
    ): String {
        val telecomDialer = runCatching { telecomManager?.defaultDialerPackage }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        if (telecomDialer != null) return telecomDialer

        val dialIntent = Intent(Intent.ACTION_DIAL)

        @Suppress("DEPRECATION")
        val resolvedDialer = runCatching {
            packageManager.resolveActivity(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        }.getOrNull()?.takeIf { it.isNotBlank() && it != "android" }
        if (resolvedDialer != null) return resolvedDialer

        return FALLBACK_DIALER_PACKAGE
    }
}

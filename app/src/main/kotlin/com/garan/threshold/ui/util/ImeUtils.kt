package com.garan.threshold.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Utility functions for keyboard dismissal and launch transitions.
 * Dismisses the soft keyboard before starting an activity to prevent IME jitter and animation lag.
 */
object ImeUtils {

    fun dismissIme(activity: Activity) {
        val window = activity.window ?: return
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }

    fun Context.findActivity(): Activity? {
        var currentContext = this
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }

    fun Context.launchActivityDismissingIme(intent: Intent, options: Bundle? = null) {
        findActivity()?.let { dismissIme(it) }
        startActivity(intent, options)
    }
}

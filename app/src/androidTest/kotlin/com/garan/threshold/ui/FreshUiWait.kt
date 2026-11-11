package com.garan.threshold.ui

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2

/**
 * UiAutomator's `device.wait(Until.findObject(...))` / `Until.gone(...)` poll the process-wide
 * accessibility node cache. When Compose removes or inserts nodes in quick succession (e.g. a
 * profile activating and filtering a search result), that cache can keep serving stale nodes for
 * longer than the wait timeout, so assertions fail even though the on-screen UI is already correct.
 *
 * These helpers clear the cache before every poll so each check reads the live hierarchy.
 */
private const val POLL_INTERVAL_MS = 100L

private fun pollFresh(timeoutMs: Long, condition: () -> Boolean): Boolean {
    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            uiAutomation.clearCache()
        }
        if (condition()) return true
        if (SystemClock.uptimeMillis() >= deadline) return false
        SystemClock.sleep(POLL_INTERVAL_MS)
    }
}

/** Waits until [selector] matches a node in the live hierarchy, returning it, or `null` on timeout. */
internal fun UiDevice.waitForFresh(selector: BySelector, timeoutMs: Long): UiObject2? {
    var found: UiObject2? = null
    pollFresh(timeoutMs) {
        found = findObject(selector)
        found != null
    }
    return found
}

/** Waits until [selector] no longer matches any node in the live hierarchy. */
internal fun UiDevice.waitUntilGoneFresh(selector: BySelector, timeoutMs: Long): Boolean =
    pollFresh(timeoutMs) { findObject(selector) == null }

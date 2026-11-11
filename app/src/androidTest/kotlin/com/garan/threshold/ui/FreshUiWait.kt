package com.garan.threshold.ui

import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2

/**
 * UiAutomator's `device.wait(Until.findObject(...))` / `Until.hasObject(...)` / `Until.gone(...)`
 * poll the process-wide `AccessibilityCache` (both cached window lists and node trees). When
 * Compose mutates semantics rapidly or an activity transition hops across windows (e.g. Threshold
 * launching Settings' `InstalledAppDetails` trampoline into `SpaActivity`), `AccessibilityCache`
 * can keep `mIsAllWindowsCached = true` and serve the stale window/node hierarchy for longer than
 * the wait timeout, causing assertions to fail even though the on-screen UI is already updated.
 *
 * These helpers call `UiAutomation.clearCache()` before every poll so each check reads the live
 * window and node hierarchy from `system_server`.
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

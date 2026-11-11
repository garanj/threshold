# Active Task: Issue #65 — AppInfo Instrumented Test Failure on Android 17 (API 37)

- **Issue:** [#65](https://github.com/garanj/threshold/issues/65)
- **Branch:** `fix/65-app-info-instrumented-test`
- **Status:** Completed

## Checklist
- [x] **1. Reproduce & Identify Root Cause on API 37:**
  - Confirmed Hypothesis 1 (`AccessibilityCache` stale window/node hierarchy across the `Threshold` -> `InstalledAppDetails` -> `SpaActivity` transition when `UiAutomation.clearCache()` is not called before each poll).
  - Identified locked-device keyguard unocclusion on physical hardware (`KeyguardManager.isKeyguardLocked`): `MainActivity` sets `setShowWhenLocked(true)` (occluding the lockscreen), whereas `com.android.settings/.spa.SpaActivity` does not set `showWhenLocked`, causing `WindowManager` to unocclude the lockscreen (`KeyguardService: setOccluded(false)`) and hide `SpaActivity`'s window (`VRI[SpaActivity]: visibilityChanged oldVisibility=true newVisibility=false`) behind `com.android.systemui` while `SpaActivity` is the top resumed activity in `ActivityTaskManager`.
  - Ruled out Hypothesis 2 (`uiautomator dump` on API 37 confirms `com.android.settings/.spa.SpaActivity` exposes its full `ComposeView` semantics tree with `package="com.android.settings"` when unlocked).
- [x] **2. Fix `ThresholdAppInfoInstrumentedTest` & `FreshUiWait`:**
  - Added `device.executeShellCommand("am force-stop com.android.settings")` in `setUp()` and `tearDown()` of `ThresholdAppInfoInstrumentedTest`.
  - Replaced `device.wait(Until.findObject(...))` and `device.wait(Until.hasObject(...))` with `device.waitForFresh(...)` / `pollFresh(...)` in `ThresholdAppInfoInstrumentedTest`, and handled keyguard unocclusion when `keyguardManager.isKeyguardLocked` is true.
- [x] **3. Verification:**
  - Verified test passes 2x consecutively on the physical Pixel 9a (Android 17 / API 37), 3x consecutively on API 37 (`ci37phone`), and on API 34 (`ci34phone`).
  - Verified `./gradlew test spotlessCheck` passes.

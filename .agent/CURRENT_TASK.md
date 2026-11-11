# Active Task: Issue #65 — AppInfo Instrumented Test Failure on Android 17 (API 37)

- **Issue:** [#65](https://github.com/garanj/threshold/issues/65)
- **Branch:** `fix/65-app-info-instrumented-test`
- **Status:** Completed

## Checklist
- [x] **1. Reproduce & Identify Root Cause on API 37:**
  - Confirmed Hypothesis 1 (`AccessibilityCache` stale window/node hierarchy across the `Threshold` -> `InstalledAppDetails` -> `SpaActivity` transition when `UiAutomation.clearCache()` is not called before each poll).
  - Ruled out Hypothesis 2 (`uiautomator dump` on API 37 confirms `com.android.settings/.spa.SpaActivity` exposes its full `ComposeView` semantics tree with `package="com.android.settings"`).
- [x] **2. Fix `ThresholdAppInfoInstrumentedTest` & `FreshUiWait`:**
  - Added `device.executeShellCommand("am force-stop com.android.settings")` in `setUp()` and `tearDown()` of `ThresholdAppInfoInstrumentedTest`.
  - Replaced `device.wait(Until.findObject(...))` and `device.wait(Until.hasObject(...))` with `device.waitForFresh(...)` in `ThresholdAppInfoInstrumentedTest` so `uiAutomation.clearCache()` runs before each poll.
- [x] **3. Verification:**
  - Verified test passes 3x consecutively on API 37 (`ci37phone`) and passes on API 34 (`ci34phone`).
  - Verified `./gradlew test spotlessCheck` passes.

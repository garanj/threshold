# Active Task: Issue #65 — AppInfo Instrumented Test Failure on Android 17 (API 37)

- **Issue:** [#65](https://github.com/garanj/threshold/issues/65)
- **Branch:** `fix/65-app-info-instrumented-test`
- **Status:** Completed

## Checklist
- [x] **1. Reproduce & Identify Root Cause on API 37:**
  - Confirmed Hypothesis 1 (`AccessibilityCache` stale window/node hierarchy across the `Threshold` -> `InstalledAppDetails` -> `SpaActivity` transition when `UiAutomation.clearCache()` is not called before each poll).
  - Identified locked-device keyguard unocclusion on physical hardware (`KeyguardManager.isKeyguardLocked`): `MainActivity` sets `setShowWhenLocked(true)` (occluding the lockscreen), whereas `com.android.settings/.spa.SpaActivity` does not set `showWhenLocked`, causing `WindowManager` to unocclude the lockscreen (`KeyguardService: setOccluded(false)`) and hide `SpaActivity`'s window (`VRI[SpaActivity]: visibilityChanged oldVisibility=true newVisibility=false`) behind `com.android.systemui` while `SpaActivity` is the top resumed activity in `ActivityTaskManager`.
  - Ruled out Hypothesis 2 (`uiautomator dump` on API 37 confirms `com.android.settings/.spa.SpaActivity` exposes its full `ComposeView` semantics tree with `package="com.android.settings"` when unlocked).
- [x] **2. Fix `ThresholdAppInfoInstrumentedTest`, `FreshUiWait`, & Cold-Boot CI Contention:**
  - Updated `pollFresh(timeoutMs)` in `FreshUiWait.kt` to evaluate an initial `checkFresh()` before starting the `deadline` clock (so an initial `UiDevice.waitForIdle()` during activity launch or IME animation does not consume the caller's timeout) and reduced `clearCache()` polling frequency to 250ms.
  - Updated `ThresholdAppInfoInstrumentedTest`, `ThresholdAntiMetricsInstrumentedTest`, and `ThresholdSearchScrollInstrumentedTest` to use `waitForFresh` / `pollFresh`, and ensured `ThresholdAppInfoInstrumentedTest` stops `com.android.settings` after pressing Back (handling both `ActivityEmbedding` `DeepLinkHomepageActivity` on `pixel_6` and keyguard-locked devices).
  - Updated `scripts/test-emulator-functional.sh` and `AndroidPackageIndexSourceLifecycleInstrumentedTest` to wait for User 0 `FallbackHome` completion and `am wait-for-broadcast-idle` before running tests, and pre-assembled APKs before booting the AVD in `.github/workflows/ci.yml`.
- [x] **3. Verification:**
  - Verified full cold-wiped 2-core (`-wipe-data -cores 2`) `ci34phone` functional + smoke suite (`6/6` instrumented tests + smoke/memory gate), physical Pixel 9a (Android 17 / API 37), and API 37 emulator.
  - Verified `./gradlew test spotlessCheck` passes.

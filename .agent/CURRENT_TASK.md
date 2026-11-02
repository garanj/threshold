# Active Task: Home Back Navigation & Flicker Prevention (`:app`)

- **Issue:** [#23 fix(app): handle back gesture on home canvas and search to prevent flicker](https://github.com/garanj/threshold/issues/23)
- **Milestone:** Phase 1 Hardening & Physical Device Testing
- **Status:** Complete / Ready for Review
- **Branch:** `fix/23-home-back-navigation`

---

## Objectives
1. [x] **Back Navigation Handling (`:app`)**:
   - Implemented `ThresholdHomeScreen` registering Compose `BackHandler(enabled = true)` and set `android:enableOnBackInvokedCallback="true"`.
   - Prevented activity finish by overriding `onBackPressed()` with a no-op so root launcher never calls `super.onBackPressed()`.
   - When search is active or query is typed: dismisses search mode, resets query to empty, clears focus, and hides the IME keyboard via `ImeUtils.dismissIme(activity)` and `SoftwareKeyboardController`.
   - When in ambient home state: cleanly consumes back navigation as a no-op so the OS never finishes or restarts the launcher activity.
2. [x] **Standard Home Manifest Configuration (`:app`)**:
   - Added `android:taskAffinity=""` to strip package affinity, isolating Threshold's home task so it never leaks into the Recents task switcher.
   - Added `android:clearTaskOnLaunch="true"`, `android:resumeWhilePausing="true"`, `android:windowSoftInputMode="adjustResize"`, and `android:configChanges` matching AOSP Launcher3 standards.
3. [x] **Test Coverage**:
   - Added `ThresholdHomeScreenTest` with unit/Compose tests asserting back gesture behavior for ambient-home state, search-active state, and app launch state.
4. [x] **Verification & Deployment**:
   - Verified `./gradlew test spotlessCheck`.
   - Built release APK (`./gradlew :app:assembleRelease`) and installed to connected Pixel 9a (`54241JEBF07233`).
   - Verified activity running top-most on device with `affinity=null` in `dumpsys activity recents`.

# Active Task: Long-Press App Info in Search (`:app`)

- **Issue:** [#27 feat(app): open App Info on long-press of app search result](https://github.com/garanj/threshold/issues/27)
- **Milestone:** Phase 1 Polish & Launcher Conventions
- **Status:** Complete / Ready for Review
- **Branch:** `feat/27-long-press-app-info`

---

## Objectives
1. [x] **`AppLauncher` Interface & Implementation (`:app`)**:
   - Added `fun openAppInfo(component: ComponentKey): Boolean` to `AppLauncher`.
   - Implemented in `AndroidAppLauncher` via `LauncherApps.startAppDetailsActivity(componentName, userHandle, null, options)` with multi-user / Work Profile support, and `ACTION_APPLICATION_DETAILS_SETTINGS` fallback for `userSerial == 0L` (and for test environments where `ShadowLauncherApps` does not support `startAppDetailsActivity`).
   - Dismissed IME before opening App Info.
2. [x] **Search Surface & Long-Click Interaction (`:app`)**:
   - Added `onLongClick: (() -> Unit)? = null` to `SearchResultRow` via `combinedClickable`.
   - Added `onItemLongClick: ((SearchItem) -> Unit)? = null` to `ThresholdSearchSurface`.
   - Wired `onItemLongClick` in `ThresholdHomeScreen` to invoke `appLauncher.openAppInfo(item.component)` and dismiss search / clear focus / hide IME.
3. [x] **Tests (`:app`)**:
   - Added `openAppInfo` tests in `AndroidAppLauncherTest`.
   - Added long-press Compose test in `ThresholdHomeScreenTest`.
4. [x] **Verification**:
   - Verified `./gradlew test spotlessCheck`.
   - Built minified release APK and deployed to connected Pixel 9a (`54241JEBF07233`).

# Active Task: Issue #67 — Increase Long Press Time for Settings to 3s

- **Issue:** [#67](https://github.com/garanj/threshold/issues/67)
- **Branch:** `feat/67-increase-settings-long-press`
- **Status:** Ready for Review

## Checklist
- [x] **1. TDD Red Phase:**
  - Added unit tests in `ThresholdHomeScreenTest` verifying that a press under 3s (`2,500ms`) on the home canvas does NOT open settings, while a >= 3,000ms press opens settings, and search result long-press for App Info remains at standard system timeout (`500ms`).
- [x] **2. TDD Green Phase:**
  - Configured a custom `ViewConfiguration` (`SETTINGS_LONG_PRESS_TIMEOUT_MS = 3_000L`) via `LocalViewConfiguration` for `ThresholdHomeCanvas` in `ThresholdHomeScreen.kt`, restoring the default `ViewConfiguration` inside `ThresholdSearchSurface` so child gestures (App Info long-press, text input) are unaffected.
- [x] **3. Verification:**
  - Ran `./gradlew spotlessApply test` and verified all unit & screenshot tests pass.

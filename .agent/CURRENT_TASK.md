# Active Task: Issue #64 & #68 — Prevent ViewModel Factories from Capturing MainActivity Across Recreation

- **Issues:** [#64](https://github.com/garanj/threshold/issues/64), [#68](https://github.com/garanj/threshold/issues/68)
- **Branch:** `fix/64-viewmodel-activity-leak`
- **Status:** Ready for Review

## Checklist
- [x] **1. TDD Red Phase:**
  - Added recreation tests in `ThresholdHomeScreenTest.kt` verifying:
    1. Zero `MainActivity` instances are reachable from `searchViewModel` or `settingsViewModel` object graphs across `recreate()`.
    2. `MainActivity.locationSource` is the same shared instance across `recreate()`.
    3. `setClockAndTriggerAlarmForTesting` and `setExactAlarmPermissionForTesting` work on the recreated `MainActivity` instance.
  - Confirmed all 3 failed on the pre-fix code.
- [x] **2. TDD Green Phase:**
  - Introduced process-scoped `ThresholdAppContainer` (and `ThresholdApplication : Application` registered in `AndroidManifest.xml`) holding `packageIndexSource`, `hapticEngine`, `settingsRepository`, `locationSource`, `antiMetricsRepository`, `scheduleAlarmScheduler`, `capabilityChecker`, `dialerPackageResolver`, and test overrides (`clockOverrideForTesting`, `exactAlarmOverrideForTesting`).
  - Refactored `MainActivity` and `ScheduleAlarmReceiver` to delegate to `ThresholdAppContainer` so `ViewModelProvider.Factory` lambdas never capture `MainActivity`.
- [x] **3. Verification:**
  - Verified all unit & screenshot tests pass (`./gradlew spotlessApply test`).
  - Added `ActivityScenario.recreate()` verification to `ThresholdScheduleProfileInstrumentedTest` and verified post-GC `Activities: 1` after 5x `font_scale` recreation and release PSS (`26,322 KB <= 35,000 KB`).

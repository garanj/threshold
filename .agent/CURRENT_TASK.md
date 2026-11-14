# Active Task: Cache Derived Trigger Sets, Memoize DataStore JSON Parsing, and Release Settings App List on Home Return

- **Issue:** [#88](https://github.com/garanj/threshold/issues/88)
- **Branch:** `refactor/88-perf-and-memory-polish`
- **Status:** Ready for Review

## Checklist
- [x] Convert `CustomProfile.referencedTriggerIds` and `ThresholdSettings.savedTriggersById` to stored `val` properties (`:core:test` passing)
- [x] Memoize raw string -> parsed collection deserialization in `AndroidSettingsRepository` (`:data:testDebugUnitTest` passing)
- [x] Add `SettingsViewModel.releaseLoadedApps()` + unit test in `SettingsViewModelTest` and invoke when returning to `ThresholdRoute.Home` in `MainActivity`
- [x] Verify `./gradlew spotlessApply spotlessCheck test` and open PR for [#88](https://github.com/garanj/threshold/issues/88)

# Current Task: Issue #78 — NFC Triggers, `AND`/`OR` Latch Semantics & Foreground NFC Reader

- **Issue:** [#78](https://github.com/garanj/threshold/issues/78)
- **Branch:** `feat/78-nfc-triggers-foreground-reader`
- **Status:** Ready for Review

## Checklist
- [x] `:core` (TDD): Implement `NfcTagEntry`, `NfcTriggerMode`, `NfcTrigger`, `ProfileTrigger.Nfc`, and `NfcLatchState` / `CascadingResolutionEngine` `AND`/`OR` latch & auto-unlatch semantics with unit tests (`NfcTriggerTest`, `CascadingResolutionEngineTest`)
- [x] `:data` (TDD): Extend `AndroidSettingsRepository` for `ProfileTrigger.Nfc` persistence with unit tests (`AndroidSettingsRepositoryTest`)
- [x] `:app` Hardware Adapter: Implement `AndroidNfcReaderSource` (`NfcAdapter.enableReaderMode` with `FLAG_READER_NO_PLATFORM_SOUNDS` + `HapticEngine.relaySnap()`) and timed-session alarm scheduling in `AndroidScheduleAlarmScheduler`
- [x] `:app` ViewModel & UI: Wire NFC tap handling in `SearchViewModel`, add `NFC` tab (`Schedule | Geo | NFC`) and tag scanner/list UI in `ScheduleEditorDialog.kt` & `ProfilesScreen.kt` with deterministic `ProfilesTestTags`
- [x] `:app` Tests: Add unit, Robolectric, screenshot, and end-to-end instrumented (`androidTest`) tests for NFC triggers and `AND`/`OR` latch rules

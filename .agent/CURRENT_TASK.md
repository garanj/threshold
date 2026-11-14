# Active Task: Harden NFC Latch Persistence, Multi-NFC AND Rules, Trigger Mutation Reconciliation, and Alarm Scheduling

- **Issue:** [#86](https://github.com/garanj/threshold/issues/86)
- **Branch:** `fix/86-nfc-and-trigger-state-machine-hardening`
- **Status:** Ready for Review

## Checklist
- [x] Write failing unit tests across `:core`, `:data`, and `:app` (Red phase)
- [x] Update `NfcLatchState` (`latchedByTagUid`), `CustomProfile` (`requiredCapabilities`), and `CascadingResolutionEngine` (reconcile trigger mutations + support multi-NFC `AND` sequential taps)
- [x] Persist `nfcLatches: Map<String, NfcLatchState>` in `SettingsRepository` / `AndroidSettingsRepository` and wire into `SearchViewModel` and `ScheduleAlarmReceiver`
- [x] Fix sub-minute NFC expiry alarm comparison in `AndroidScheduleAlarmScheduler` and wire `activeNowEpochMillisProvider` in `ThresholdAppContainer`
- [x] Marshal `AndroidNfcReaderSource` registration callbacks onto main thread and eliminate duplicate `relaySnap()` in `ScheduleEditorDialog.kt`
- [x] Require `selectedDays.isNotEmpty()` for `SCHEDULE` triggers in `ScheduleEditorDialog.kt`
- [x] Verify `./gradlew test` passes with zero `android.*` imports in `:core`

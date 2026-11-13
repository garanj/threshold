# Current Task: Issue #80 — Fix 2nd-Tap NFC Unlatch & Abstract `NfcAdapter.enableReaderMode`

- **Issue:** [#80](https://github.com/garanj/threshold/issues/80)
- **Branch:** `fix/80-nfc-toggle-off-and-reader-abstraction`
- **Status:** Ready for Review

## Checklist
- [x] `:core` (TDD): Write failing regression test for 2nd-tap toggle-off when `previouslyActiveTriggerIds` contains the unlatched NFC trigger ID, then fix `CascadingResolutionEngine` (`nfcLatchStates: Map<String, NfcLatchState>? = null`)
- [x] `:app` ViewModel (TDD): Write failing regression test in `SearchViewModelTest` for 1st tap ON -> 2nd tap OFF -> 3rd tap ON, and verify fix
- [x] `:app` Hardware Adapter (TDD): Abstract `NfcAdapter.enableReaderMode` / `disableReaderMode` behind `NfcAdapterGateway` in `AndroidNfcReaderSource` and add `AndroidNfcReaderSourceTest`
- [x] `:app` Instrumented Test: Update `ThresholdScheduleProfileInstrumentedTest` to assert 2nd-tap toggle-off before re-latching and testing schedule auto-unlatch

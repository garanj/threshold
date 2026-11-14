# Active Task: Configurable Max Daily Uses for Timed NFC Triggers & Single-NFC-per-AND-Rule

- **Issue:** [#89](https://github.com/garanj/threshold/issues/89)
- **Branch:** `feat/89-nfc-timed-max-daily-uses`
- **Status:** Ready for Review

## Checklist
- [x] Add `maxDailyUses` and `NfcDailyUsage` (`getTapsToday`, `hasRemainingTaps`, `markTapUsed`) to `NfcTrigger.kt`
- [x] Add `localDate` to `LocalDayTime` / `SystemClockSource`
- [x] Enforce single-NFC-per-`AND`-rule and daily usage limits (`NfcTapOutcome.DAILY_LIMIT_REACHED`) in `CascadingResolutionEngine`
- [x] Persist `nfcDailyUsage` in `ThresholdSettings`, `SettingsRepository`, `AndroidSettingsRepository`, and `SearchViewModel`
- [x] Add stepper UI for `maxDailyUses` in `NfcEditorFields` and single-NFC-per-AND-rule filtering in `ProfileRulesEditorSheet` / `TriggerEditorSheet`
- [x] Add unit and UI tests in `:core`, `:data`, and `:app`
- [x] Run `./gradlew spotlessApply spotlessCheck test` and open PR for [#89](https://github.com/garanj/threshold/issues/89)

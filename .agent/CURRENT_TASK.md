# Current Task: Issue #73 — Decouple Reusable Triggers from Profiles with AND/OR Activation Rules

- **Issue:** [#73](https://github.com/garanj/threshold/issues/73)
- **Branch:** `feat/73-reusable-triggers-and-or-rules`
- **Status:** Ready for Review

## Checklist
- [x] Phase 1 (`:core`): Add `SavedTrigger` and `TriggerRule` (`AND` within a rule, `OR` across rules), update `CustomProfile`, `ThresholdSettings`, `CascadingResolutionEngine`, `ScheduleHorizonCalculator`, `ProfileCapabilityChecker`, `ScheduleAlarmScheduler`, and `SettingsRepository`, and add unit tests.
- [x] Phase 2 (`:data`): Persist `savedTriggers` and `CustomProfile.activationRules` in `AndroidSettingsRepository`, auto-migrate legacy inline profile triggers, implement `saveTrigger` and `deleteTrigger` (stripping deleted trigger IDs from profiles and auto-disabling profiles left with zero triggers), and add unit tests.
- [x] Phase 3 (`:app`): Add `Profiles | Triggers` segmented view in `ProfilesScreen`, `TriggerCard` + `TriggerEditorSheet` with deletion warning dialog, `ProfileRulesEditorSheet` (`AND` rows + `OR` conditions + inline trigger creation/editing), update `SearchViewModel` / `SettingsViewModel` / `AndroidScheduleAlarmScheduler`, update Roborazzi golden, and add Compose UI tests.

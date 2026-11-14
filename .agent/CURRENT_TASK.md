# Active Task: Decompose Monolithic ProfilesScreen and ScheduleEditorDialog into Focused Compose Files

- **Issue:** [#91](https://github.com/garanj/threshold/issues/91)
- **Branch:** `refactor/91-decompose-profiles-and-trigger-ui`
- **Status:** Ready for Review

## Checklist
- [x] Extract `ProfilesTestTags.kt`
- [x] Extract `TriggerFormatting.kt`
- [x] Extract `ProfileRulesEditorSheet.kt`
- [x] Rename/split `ScheduleEditorDialog.kt` -> `TriggerEditorSheet.kt` and `TriggerTypeEditorFields.kt`
- [x] Trim `ProfilesScreen.kt` to top-level screen and card components
- [x] Run `./gradlew spotlessApply spotlessCheck test` and open PR for [#91](https://github.com/garanj/threshold/issues/91)

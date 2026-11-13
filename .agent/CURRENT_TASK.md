# Current Task: Issue #76 — UI Automation `testTag`s & Strengthened `AND`/`OR` Trigger Tests

- **Issue:** [#76](https://github.com/garanj/threshold/issues/76)
- **Branch:** `feat/76-test-tags-and-trigger-tests`
- **Status:** Ready for Review

## Checklist
- [x] Codify `Modifier.testTag` and `testTagsAsResourceId = true` best practices in `AGENTS.md`, `.agent/STANDARDS.md`, and `.agent/skills/compose-m3-ui/SKILL.md`
- [x] Enable `semantics { testTagsAsResourceId = true }` at the root of `ThresholdHomeScreen` and add structured `testTag`s to `SettingsScreen`, `ProfilesScreen`, and `ScheduleEditorDialog`
- [x] Strengthen `:core` unit tests (`ScheduleHorizonCalculatorTest`, `CascadingResolutionEngineTest`) for `savedTriggers` and `AND`/`OR` `activationRules`
- [x] Strengthen `:app` unit & Robolectric tests (`AndroidScheduleAlarmSchedulerTest`, `SearchViewModelTest`, `ProfilesScreenTest`) using deterministic `testTag`s
- [x] Add end-to-end instrumented test (`:app:connectedDebugAndroidTest`) for composite `(Schedule AND Geo) OR Schedule` trigger activation and UI automation

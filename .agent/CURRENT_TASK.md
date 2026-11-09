# Current Task: Custom Profile Models, Schedule Triggers, and Cascading Resolution Engine (#56)

## Active Issue
- **Issue**: `#56` - feat(core): custom profile models, schedule triggers, and cascading resolution engine
- **Branch**: `feat/56-custom-profiles-cascading-engine`

## Checklist
- [x] **`:core` Domain Models (Zero `android.*` imports)**:
  - [x] `DayOfWeek`, `TimeOfDay`, `TimeWindow`, and `ScheduleTrigger` (uniform daily or per-day `dailyWindows`, overnight spillover support).
  - [x] `ProfileTrigger.Schedule` sealed interface and `ProfileCapability` / `ProfileCapabilityChecker` port.
  - [x] `CustomProfile` domain model (`id`, `name`, `enabled`, `priority`, `trigger`, `appAssignments`, `frictionOverride`, `colorIndex`, `initialLetter`).
- [x] **`:core` Cascading Resolution Engine & Horizon Calculator**:
  - [x] `CascadingResolutionEngine` (`resolveAppAccess`, `resolveWorkingSet`) and `ScheduleHorizonCalculator` (`findNextTransition`).
  - [x] Unit tests (`ScheduleTriggerTest`, `CustomProfileTest`, `CascadingResolutionEngineTest`, `ScheduleHorizonCalculatorTest`).
- [x] **`:data` Persistence & Profile Repository (`:data`)**:
  - [x] Extend `SettingsRepository` / `AndroidSettingsRepository` to persist `List<CustomProfile>` (`dailyWindows`, `sameScheduleEveryDay`, `colorIndex`) in DataStore.
  - [x] Unit tests in `AndroidSettingsRepositoryTest`.
- [x] **`:app` End-to-End Scheduling, UI, and On-Screen Indicators**:
  - [x] Material 3 & Jetpack Navigation 3 refactor across all `:app` screens + `compose-m3-ui` skill.
  - [x] `AndroidScheduleAlarmScheduler`, `ScheduleAlarmReceiver`, and `AndroidProfileCapabilityChecker`.
  - [x] Square active profile `[X]` badges and `[!]` capability fault badge + bottom sheet on `ThresholdHomeCanvas` / `ThresholdHomeScreen`.
  - [x] Unit, Robolectric, Roborazzi screenshot, and on-device `ThresholdScheduleProfileInstrumentedTest` verification.
- [x] **Verification**:
  - [x] `./gradlew test` passes 100% across `:core`, `:data`, and `:app`.
  - [x] Zero `android.*` imports in `:core`.
  - [x] Spotless check passes (`./gradlew spotlessCheck`).

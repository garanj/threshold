# Current Task: Custom Profile Models, Schedule Triggers, and Cascading Resolution Engine (#56)

## Active Issue
- **Issue**: `#56` - feat(core): custom profile models, schedule triggers, and cascading resolution engine
- **Branch**: `feat/56-custom-profiles-cascading-engine`

## Checklist
- [ ] **`:core` Domain Models (Zero `android.*` imports)**:
  - [ ] `ProfileId` value class / identifier.
  - [ ] `DayOfWeek` representation compatible with pure host JVM / standard java.time / Kotlin.
  - [ ] `ScheduleTime` (hours/minutes representation) and `ScheduleTrigger` (days of week, startTime, endTime, overnight support).
  - [ ] `ProfileTrigger.Schedule` sealed interface.
  - [ ] `CustomProfile` domain model (`id: ProfileId`, `name: String`, `enabled: Boolean`, `priority: Int`, `trigger: ProfileTrigger`, `appOverrides: Map<ComponentKey, AppAssignment>`, `frictionOverride: FrictionChoice?`).
  - [ ] `EffectiveRule` / `ResolvedAppPolicy` domain model (`Blocked`, `Guarded`, `Allowed`, with origin tracking for debugging/UI).
- [ ] **`:core` Cascading Resolution Engine**:
  - [ ] `CascadingResolutionEngine`:
    - Evaluates active profiles by priority order (top-to-bottom).
    - Respects `enabled` flag and checks trigger active state against current time.
    - Resolves app assignment: highest-priority active profile's explicit rule (`ALLOW`, `GUARD`, `BLOCK`) wins; unassigned/default cascades to lower profiles and base profile.
    - Resolves friction: app friction override -> active profile friction override -> base profile default friction.
    - Permanent Universal Dialer invariant: `android.app.role.DIALER` is permanently `Allowed` regardless of profile state.
    - Profile auto-disable / rule pruning on app uninstallation.
  - [ ] Unit tests for `CascadingResolutionEngine`:
    - Top-to-bottom priority precedence.
    - Cascading fallback to base profile.
    - Overnight schedule evaluation (e.g. 22:00 to 06:00 spanning midnight).
    - Multi-day schedule evaluation (e.g. Mon–Fri vs Sat–Sun).
    - Universal dialer invariant.
    - App uninstallation pruning and empty profile behavior.
- [ ] **`:data` Persistence & Profile Repository (`:data`)**:
  - [ ] Extend `SettingsRepository` to observe, save, and manage `List<CustomProfile>` in DataStore.
  - [ ] Serialization & deserialization of `CustomProfile` and `ScheduleTrigger`.
  - [ ] Package uninstall reconciliation for custom profile app overrides.
  - [ ] Unit tests in `:data` for DataStore persistence and serialization.
- [ ] **Verification**:
  - [ ] `./gradlew test` passes 100% across `:core` and `:data`.
  - [ ] Zero `android.*` imports in `:core`.
  - [ ] Spotless check passes (`./gradlew spotlessCheck`).

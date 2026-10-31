# Active Task: Core Domain Models & Ports (`:core`)

- **Issue:** [#7 feat(core): implement core domain models and ports](https://github.com/garanj/threshold/issues/7)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** In Progress
- **Branch:** `feat/7-core-domain-models`

---

## Objectives
1. [x] Create pure Kotlin domain models in `com.garan.threshold.core.model`:
   - `ComponentKey` (`packageName`, `activityName`, `userSerial`)
   - `AppAccessState` (`Direct`, `Guarded(policy: FrictionPolicy)`, `Blocked`)
   - `FrictionPolicy` (`TypedIntention`, `TimedDelay`, `SessionBudget`)
   - `DndPolicy` (`Off`, `PriorityOnly`, `AlarmsOnly`, `TotalSilence`)
   - `Profile` (`id`, `name`, `dndPolicy`, `defaultAccess`, `appRules`) with `resolveAccess(component, isDefaultDialer)`
2. [x] Create core outbound ports in `com.garan.threshold.core.port`:
   - `PackageIndexSource`: Query and stream installed launchable components and package lifecycle changes.
   - `AppAccessController`: Mediate launch requests, friction checks, and DPC-level package suspension.
3. [x] Implement comprehensive unit tests in `core/src/test/kotlin/com/garan/threshold/core/`:
   - `ComponentKeyTest`: equality, hashing, validation, string flattening roundtrips.
   - `ProfileTest`: access resolution, default fallbacks, override precedence, and the Universal Dialer Whitelist safety invariant.
   - `FrictionPolicyTest`: validation and boundary tests.
   - `ArchitectureBoundaryTest`: strictly enforces 0 `android.*` imports in `:core`.
4. [x] Invariant Verifications:
   - Zero `android.*` imports in `:core` (verified via `ArchitectureBoundaryTest`).
   - Pass `./gradlew test spotlessCheck`.

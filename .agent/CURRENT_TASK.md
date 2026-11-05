# Active Task: Power-Efficient Anti-Metric Display (`:core`, `:data`, `:app`)

- **Issues:**
  - [#48 feat(ui): implement power-efficient anti-metric display (rest duration & unlock counter)](https://github.com/garanj/threshold/issues/48)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine (Task 2.5: Anti-Metric Display)
- **Status:** Complete (Awaiting PR Review)
- **Branch:** `feat/48-anti-metric-display`

---

## Objectives
1. [x] **Domain Model & Formatting (`:core`)**:
   - `AntiMetrics(restDurationMs: Long?, unlockCount: Int)` model.
   - `AntiMetricsFormatter` formatting rest intervals (`REST < 1M`, `REST 48M`, `REST 2H 15M`, `REST 1D 4H`) and unlock counts (`1 UNLOCK`, `N UNLOCKS`).
   - Zero `android.*` imports verified by `ArchitectureBoundaryTest`.
   - Comprehensive unit test suite in `AntiMetricsTest`.
2. [x] **Repository & Storage (`:data`)**:
   - `AndroidAntiMetricsRepository` backed by lightweight SharedPreferences.
   - Zero background execution: listens to `ACTION_SCREEN_OFF` and `ACTION_USER_PRESENT`.
   - Lazy calendar-day rollover without waking up the CPU at midnight.
   - Comprehensive unit test suite in `AndroidAntiMetricsRepositoryTest`.
3. [x] **UI Integration & Presentation (`:app`)**:
   - Wired `antiMetricsRepository` into `MainActivity` and `ThresholdHomeScreen`.
   - Renders formatted anti-metric string in `ThresholdHomeCanvas` below the hairline divider.
   - Compose unit test in `ThresholdHomeScreenTest` verifying formatted status display.
4. [x] **Acceptance Gates & Verification**:
   - `./gradlew test` passes (all 81 unit & screenshot tests).
   - Zero `android.*` imports in `:core`.
   - `./gradlew spotlessCheck` clean.

# Active Task: Scaffold Multi-Module Gradle Architecture & Shift-Left Test Harness

- **Issue:** [#1 Scaffold multi-module Gradle structure (:core, :data, :app, :benchmark) & Shift-Left test harness](https://github.com/garanj/threshold/issues/1)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** Complete (Ready for PR)
- **Branch:** `feat/1-scaffold-gradle-modules`

---

## Objectives
1. [x] Configure `settings.gradle.kts` and root `build.gradle.kts`.
2. [x] Establish `gradle/libs.versions.toml` with pinned, verified versions (AGP 9.2.1, Kotlin 2.4.0, Compose, Room, DataStore, Roborazzi, LeakCanary).
3. [x] Configure `:core` as pure Kotlin JVM module with zero Android SDK dependencies and sub-second test execution.
4. [x] Configure `:data` as Android library module with Room/DataStore.
5. [x] Configure `:app` as Android launcher application with Compose, host JVM screenshot testing (Roborazzi/Robolectric), and LeakCanary debug dependency.
6. [x] Configure `:benchmark` as Macrobenchmark module with Baseline Profile plugin.
7. [x] Setup script for < 30 MB PSS memory assertions (`scripts/verify-memory.sh`).
8. [x] Verify `./gradlew test` runs cleanly on host JVM.

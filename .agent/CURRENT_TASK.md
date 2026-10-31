# Active Task: Scaffold Multi-Module Gradle Architecture & Shift-Left Test Harness

- **Issue:** [#1 Scaffold multi-module Gradle structure (:core, :data, :app, :benchmark) & Shift-Left test harness](https://github.com/garanj/threshold/issues/1)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** In Progress
- **Branch:** `feat/1-scaffold-gradle-modules`

---

## Objectives
1. [ ] Configure `settings.gradle.kts` and root `build.gradle.kts`.
2. [ ] Establish `gradle/libs.versions.toml` with pinned, verified versions (AGP, Kotlin, Compose, Room, DataStore, Paparazzi, LeakCanary).
3. [ ] Configure `:core` as pure Kotlin JVM module with zero Android SDK dependencies.
4. [ ] Configure `:data` as Android library module with Room/DataStore.
5. [ ] Configure `:app` as Android launcher application with Compose, Paparazzi screenshot testing, and LeakCanary debug dependency.
6. [ ] Configure `:benchmark` as Macrobenchmark module with Baseline Profile plugin.
7. [ ] Setup script / task for < 30 MB PSS memory assertions.
8. [ ] Verify `./gradlew test` runs cleanly on host JVM.

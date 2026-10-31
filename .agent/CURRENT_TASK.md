# Active Task: Scaffold Multi-Module Gradle Architecture

- **Issue:** [#1 Scaffold multi-module Gradle structure (:core, :data, :app, :benchmark)](https://github.com/garanj/threshold/issues/1)
- **Milestone:** Phase 1: Core Shell & Safe System Integration
- **Status:** In Progress
- **Branch:** `feat/1-scaffold-gradle-modules`

---

## Objectives
1. [ ] Configure `settings.gradle.kts` and root `build.gradle.kts`.
2. [ ] Establish `gradle/libs.versions.toml` with pinned, verified versions.
3. [ ] Configure `:core` as pure Kotlin JVM module with zero Android SDK dependencies.
4. [ ] Configure `:data` as Android library module with Room/DataStore.
5. [ ] Configure `:app` as Android launcher application with Compose.
6. [ ] Configure `:benchmark` as Macrobenchmark module.
7. [ ] Verify `./gradlew test` runs cleanly on host JVM.

---

## Verification & Guardrails Check
- [x] "No Shenanigans" rules documented and prominent.
- [x] Universal dialer whitelist safety invariant defined.
- [x] Pure Kotlin `:core` port-and-adapter constraint documented.
- [x] Memory limit (<30MB PSS) and haptic/motion rules codified.

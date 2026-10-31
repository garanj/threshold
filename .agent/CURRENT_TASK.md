# Active Task: Establish Baseline Profile Generator & Initial Cold-Start Profile

- **Issue:** [#5 feat(perf): establish Baseline Profile generator and initial cold-start profile](https://github.com/garanj/threshold/issues/5)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** In Progress
- **Branch:** `feat/5-baseline-profile-generator`

---

## Objectives
1. [x] Configure `:benchmark` module with `androidx.baselineprofile` plugin and Macrobenchmark dependencies.
2. [x] Configure `:app` module with `androidx.baselineprofile` plugin to consume generated profiles.
3. [x] Implement `BaselineProfileGenerator` in `:benchmark` capturing cold startup of `MainActivity` to first drawn frame (`ThresholdHomeCanvas`).
4. [x] Generate initial `baseline-prof.txt` and package it into `app/src/main/baselineProfiles/baseline-prof.txt`.
5. [x] Verify that minified release builds (`:app:assembleRelease`) compile the ART profile cleanly.
6. [x] Ensure Spotless, tests, and CI gates pass.

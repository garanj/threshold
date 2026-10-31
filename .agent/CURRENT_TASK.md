# Active Task: CI Quality Pipeline, Spotless, Release Validation & Emulator Smoke Harness

- **Issue:** [#3 Establish CI Quality Pipeline, Spotless, Release ProGuard Validation, and Emulator Home Role Harness](https://github.com/garanj/threshold/issues/3)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** In Progress
- **Branch:** `feat/3-ci-quality-and-smoke-harness`

---

## Objectives
1. [ ] Configure Spotless with ktlint across all modules (`:core`, `:data`, `:app`, `:benchmark`).
2. [ ] Validate `./gradlew assembleRelease` and ensure R8 minification succeeds without stripping Compose/Room runtime classes.
3. [ ] Set up automated emulator smoke test script (`scripts/test-emulator-smoke.sh`):
   - Install APK
   - Grant `android.app.role.HOME` via ADB
   - Trigger `KEYCODE_HOME`
   - Assert foreground focus (`dumpsys window | grep mCurrentFocus`)
   - Assert memory budget (< 30 MB Total PSS)
4. [ ] Create GitHub Actions CI workflow (`.github/workflows/ci.yml`) covering host JVM checks and smoke gates.
5. [ ] Verify all checks pass cleanly locally.

# Testing Strategy & Quality Guardrails: Threshold

Threshold adheres to a strict test-driven development (TDD) discipline. No feature code should be written without prior failing unit tests.

---

## 1. The Test Pyramid

```
                ┌─────────────────────────────────┐
                │      Macrobenchmarks & PSS      │  Cold startup, frame drops,
                │         (:benchmark)            │  <30MB PSS memory assertion
                ├─────────────────────────────────┤
                │     Emulator Instrumentation    │  UI Automator, Home role contracts,
                │         (:app androidTest)      │  package update broadcast fakes
                ├─────────────────────────────────┤
                │     Screenshot Regression       │  Paparazzi / Roborazzi on host JVM:
                │         (:app test)             │  RTL, font scaling (1.0x-1.5x), insets
                ├─────────────────────────────────┤
                │      Pure Unit Tests (Fast)     │  Host JVM execution (~60s):
                │      (:core test, :data test)   │  Conflict engine, search, intent gate
                └─────────────────────────────────┘
```

---

## 2. Test Discipline & TDD Rules

### Pure Core JVM Tests (`:core`)
- Executed on the local JVM without Android SDK stubs, mocks, or Robolectric overhead.
- Execution speed target: **< 5 seconds** for the entire core test suite.
- Uses clean in-memory fakes for ports (`FakePackageIndexSource`, `FakeProfileRepository`).
- Verifies:
  - 4-Tier conflict engine hierarchy and tiebreakers.
  - Universal dialer package safety invariant.
  - Zero-allocation fuzzy search accuracy.
  - State transitions across profiles and expired token passes.

### Visual & Layout Regression (`:app`)
- Uses **Paparazzi** (or Roborazzi) running directly on the host JVM.
- Goldens recorded and version-controlled.
- Test variations:
  - Typography modes (System Default, JetBrains Mono, Editorial Serif, Geometric Sans).
  - Navigation styles (Gesture navigation vs 3-button navigation insets).
  - Font scaling (1.0x, 1.25x, 1.5x).
  - Right-to-Left (RTL) localization.

### Memory & Performance Guardrails
- **TOTAL PSS Budget:** Strictly capped at **30,000 KB (30 MB)**.
- Verified in CI and local emulator via:
  ```bash
  adb shell dumpsys meminfo com.threshold.launcher | grep "TOTAL PSS"
  ```
- **LeakCanary** embedded in local debug builds.
- Zero bitmap objects held in long-lived state.

---

## 3. Local Verification Commands

Run before every commit/push:

```bash
# 1. Run all host JVM unit tests
./gradlew test

# 2. Verify code formatting and linting
./gradlew lint spotlessCheck

# 3. Run screenshot verification
./gradlew verifyPaparazziDebug
```

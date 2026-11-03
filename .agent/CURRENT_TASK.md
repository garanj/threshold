# Active Task: Micro-Haptics Engine & Target Snap Detents (`:core`, `:data`, `:app`)

- **Issue:** [#29 feat(haptics): implement micro-haptics engine and result-snap detents](https://github.com/garanj/threshold/issues/29)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine
- **Status:** Complete / Ready for Review
- **Branch:** `feat/29-micro-haptics-engine`

---

## Objectives
1. [x] **Core Domain Port (`:core`)**:
   - Defined `HapticEngine` interface and `NoOpHapticEngine` with strictly zero `android.*` imports.
   - Defined tactile feedback primitives:
     - `rotaryDetent()`
     - `targetSnap()`
     - `resistanceDetent()`
     - `relaySnap()`
2. [x] **Android Adapter (`:data`)**:
   - Implemented `AndroidHapticEngine(context: Context)` implementing `HapticEngine`.
   - Used `Vibrator` / `VibratorManager` with `VibrationEffect.startComposition()`.
   - Hardware capability detection via `vibrator.areAllPrimitivesSupported(...)`.
   - Micro-pulse fallbacks (`createOneShot` / `createWaveform`) for devices without hardware composition primitives.
3. [x] **UI Integration (`:app`)**:
   - Precision tactile lock-in: Triggered `targetSnap()` when top search result candidate changes during typing.
   - Triggered `resistanceDetent()` on friction-guarded app evaluation and long-press App Info interaction.
   - Triggered `rotaryDetent()` on search result list scrolling.
4. [x] **Tests & Verification**:
   - Unit tests in `:core` (`HapticEngineTest`, `ArchitectureBoundaryTest`).
   - Robolectric tests in `:data` (`AndroidHapticEngineTest`).
   - Compose & unit tests in `:app` (`SearchViewModelTest`, `ThresholdHomeScreenTest`).
   - Spotless clean, all 98 host JVM tests pass.
   - Minified release build deployed to connected Pixel 9a (`54241JEBF07233`).

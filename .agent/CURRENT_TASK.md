# Current Task: Phase 2 Retrospective Hardening & Polish (#52)

## Active Issue
- **Issue**: `#52` - Phase 2 Retrospective: Fix friction bypasses, edge-to-edge insets, haptic IPC caching, and edge cases
- **Branch**: `fix/52-phase2-retrospective-hardening`

## Checklist
- [x] **`:core` Hardening & Tests**:
  - [x] Constrain `FrictionPolicy.DragSort` `pairCount` to `2..6` matching the 6 distinct monochrome glyphs (`FrictionPolicyTest`).
  - [x] Shuffle fallback grid points using `random` in `DragSortSession.generateLayout` (`DragSortSessionTest`).
  - [x] Add `refreshForCurrentDate(timestampMs: Long = System.currentTimeMillis())` to `AntiMetricsRepository` port.
- [x] **`:data` Hardening & Tests**:
  - [x] `AndroidAntiMetricsRepository`: Centralize static `BroadcastReceiver` in companion object so `unregister()` works across instances and clears references; implement `refreshForCurrentDate()`; use monotonic `elapsedRealtimeProvider` fallback when wall-clock shifts backward during sleep (`AndroidAntiMetricsRepositoryTest`).
  - [x] `AndroidHapticEngine`: Pre-compute and cache `VibrationEffect` instances and `hasVibrator` capability via `by lazy`; make `resistanceDetent()` fallback distinct from `targetSnap()`; store `context.applicationContext` (`AndroidHapticEngineTest`).
  - [x] `AndroidPackageIndexSource`: Use `context.applicationContext ?: context` to prevent Activity context retention.
- [x] **`:app` Hardening & Tests**:
  - [x] Move `activeFriction` (`ActiveFrictionGate`) into `SearchViewModel` / `SearchUiState` so `dismissSearch()` (and `MainActivity.onNewIntent`) clears active friction; clear active friction on `Lifecycle.Event.ON_STOP` in `ThresholdHomeScreen` (`SearchViewModelTest`, `ThresholdHomeScreenTest`).
  - [x] Pre-warm package index on `MainActivity.onCreate` (`searchViewModel.ensureIndexLoaded()`).
  - [x] Add `windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))` to `CaretTracingSurface` (`CaretTracingSurfaceTest`).
  - [x] Harden `TypedIntentionSurface`: `KeyboardType.Ascii`, multi-character backspace, 2-char IME commit handling (rejecting >2 char bulk paste with `resistanceDetent()`), vertical scroll support (`TypedIntentionSurfaceTest`).
  - [x] `DragSortSurface`: Remember painter lists across recompositions.
  - [x] `ThresholdSearchSurface`: Trigger `hapticEngine.resistanceDetent()` when pressing IME `Go` on a guarded partial match; deduplicate IME/search reset helper in `ThresholdHomeScreen` and fix `SessionBudget` IME flicker.
- [x] **Verification**:
  - [x] `./gradlew test` (100% pass across `:core`, `:data`, `:app`, including Roborazzi golden screenshots)
  - [x] `./gradlew spotlessApply spotlessCheck`

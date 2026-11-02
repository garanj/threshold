# Active Task: Typographic Surface & Insets Foundation (`:app`)

- **Issue:** [#17 feat(app): typographic surface and window insets foundation](https://github.com/garanj/threshold/issues/17)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** Complete / Ready for Review
- **Branch:** `feat/17-typographic-surface-insets`

---

## Objectives
1. [x] **Typographic Foundation (`:app`)**:
   - Defined `Type.kt` typography hierarchy with OpenType `tnum, zero` (`THRESHOLD_FONT_FEATURE_SETTINGS = "tnum, zero"`).
   - Configured `PlatformTextStyle(includeFontPadding = false)` across all typography styles to eliminate baseline drift.
   - Minimalist AMOLED palette: `#000000` (AmoledBlack), `#FFFFFF` (PureWhite), `#888888` (SubtleGray), `#262626` (HairlineBorder).
2. [x] **Home Display Surface (`ThresholdHomeCanvas`)**:
   - Typographic clock with hours, minutes, and ambient calendar date format.
   - Fixed-pitch tabular digits ensuring no horizontal jitter when time changes.
3. [x] **Edge-to-Edge & Navigation Insets**:
   - Edge-to-edge support with `enableEdgeToEdge()` and `Modifier.safeDrawingPadding()`.
   - Supports both gesture navigation bars and 3-button navigation.
4. [x] **IME Controller & Dismissal Utility**:
   - Clean keyboard dismissal utility via `WindowInsetsControllerCompat` (`ImeUtils.launchActivityDismissingIme`) before launching activities.
5. [x] **Roborazzi Golden Screenshot Tests**:
   - Verified typographic home display across phone configurations.
   - Golden screenshot tests in host JVM (`ThresholdScreenshotTest`): default, zero-padded, and profile status variants.
6. [x] **Quality Gates**:
   - `./gradlew test spotlessCheck` passes on host JVM.
   - Total PSS remains < 25 MB.

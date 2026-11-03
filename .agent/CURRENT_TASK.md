# Active Task: Drag-to-Match Icon Sorting Checkpoint (`:core`, `:app`)

- **Issue:** [#37 feat(friction): implement drag-to-match icon sorting checkpoint](https://github.com/garanj/threshold/issues/37)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine (Task 2.3)
- **Status:** Complete (Ready for Review)
- **Branch:** `feat/37-drag-sort-checkpoint`

---

## Objectives
1. [x] **Core Friction Domain (`:core`)**:
   - Add `FrictionPolicy.DragSort` to `com.garan.threshold.core.model.FrictionPolicy`.
   - `DragSortSession`:
     - Models 6 glyph pairs with spatial coordinates.
     - Generator / Layout algorithm ensuring:
       - Bounds within available container (width, height, icon size, padding).
       - No overlap between any of the 12 positions (pairwise distance >= min item spacing).
       - Pair separation: distance between a draggable icon and its corresponding target >= separation threshold (e.g. 0.45 * height).
     - Drag handling: pickup, drag offset tracking, drop validation with 24 dp tolerance.
     - Pair resolution: matched items removed; failed drops signal snap-back.
     - Pure Kotlin, zero `android.*` imports.
     - Unit tests in `DragSortSessionTest`.
2. [x] **Minimalist Vector Glyphs (`:app`)**:
   - 6 clean vector drawables in `app/src/main/res/drawable/`:
     - `ic_glyph_hexagon.xml`
     - `ic_glyph_diamond.xml`
     - `ic_glyph_triangle.xml`
     - `ic_glyph_cross.xml`
     - `ic_glyph_circle.xml`
     - `ic_glyph_star.xml`
3. [x] **UI Component (`DragSortSurface.kt` in `:app`)**:
   - Full-screen `AmoledBlack` with `safeDrawingPadding`.
   - Fixed grey targets in background.
   - Draggable white icons in foreground with elevated z-index on drag.
   - Zero-allocation 120Hz drag translation using `Modifier.offset { IntOffset(...) }`.
   - On drop match: `relaySnap()`, pair dissolves.
   - On drop mismatch: 120ms decelerating snap-back to start position + `resistanceDetent()`.
   - Bottom button: "Go back" morphs to "Open" with `targetSnap()` when all 6 pairs matched.
   - Swiping or pressing back dismisses the checkpoint.
4. [x] **Integration & Testing**:
   - Route `LaunchResult.FrictionRequired(FrictionPolicy.DragSort)` in `ThresholdHomeScreen.kt`.
   - Set YouTube (`com.google.android.youtube`) to `Guarded(FrictionPolicy.DragSort)` in `MainActivity.kt` for testing.
   - Compose unit tests and Roborazzi golden screenshot.
   - `./gradlew test spotlessCheck`.
   - Release build and install to connected Pixel 9a (`54241JEBF07233`).
   - Open PR and assign to `garanj`.

# Active Task: Fix VectorPainter DrawCache Clipping in Drag-to-Match Checkpoint

- **Issue:** [#39 fix(friction): isolate VectorPainter instances to prevent DrawCache scaling corruption in drag-sort](https://github.com/garanj/threshold/issues/39)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine (Task 2.3)
- **Status:** In Progress
- **Branch:** `fix/39-vector-painter-clipping`

---

## Objectives
1. [x] **Investigate Root Cause**:
   - `DragSortSurface` shared a single list of `VectorPainter` instances (`glyphPainters`) across target silhouettes (`normalDrawSize = 40.dp`) and active dragged glyphs (`activeDrawSize = 46.dp`).
   - Compose's `VectorPainter` wraps a mutable `VectorComponent` and `DrawCache` that caches root scaling and rasterized bitmap layers.
   - When a glyph is actively dragged, the painter scales to 46.dp. When drawing the target silhouette at `normalDrawSize` (40.dp), the painter draws the 46.dp-scaled circle into a 40.dp `DrawScope` boundary, cutting off the bottom 2.17dp horizontally.
2. [ ] **Isolate Painter Instances**:
   - Provide distinct `targetPainters`, `draggablePainters`, and `activeDragPainters` in `DragSortSurface.kt`.
   - Ensure target silhouettes are strictly isolated and never contaminated by active drag dimensions.
3. [ ] **Verification**:
   - Verify all unit and screenshot tests pass (`./gradlew test`).
   - Verify memory allocations remain within budget (< 35 MB).
   - Create PR assigned to `garanj`.

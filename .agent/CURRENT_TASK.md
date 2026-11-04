# Active Task: Caret Tracing & Friction UX Polish (`:app`)

- **Issues:**
  - [#43 feat(friction): implement caret tracing meditative delay checkpoint](https://github.com/garanj/threshold/issues/43)
  - [#45 fix(ui): prevent DragSortSurface IME resize flashing and preserve search cursor position on back navigation](https://github.com/garanj/threshold/issues/45)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine (Task 2.3 Checkpoint 3)
- **Status:** Complete (Awaiting PR Review)
- **Branch:** `feat/43-caret-tracing-checkpoint`

---

## Objectives
1. [x] **Caret Tracing Surface (`CaretTracingSurface.kt`)**:
   - Centered 140dp caret path with 14dp stroke width on 180dp canvas.
   - Single continuous `Animatable` ($0.0 \to 3.0$) inside `UnscaledMotionDurationScale`.
   - Micro-haptics at apexes (`rotaryDetent`), pass completions (`targetSnap`), and final completion (`relaySnap`).
2. [x] **Fix Bug 1 (`DragSortSurface` IME resize flashing)**:
   - Exclude `WindowInsets.ime` from `DragSortSurface` insets (use `WindowInsets.systemBars.union(WindowInsets.displayCutout)`).
   - Stabilize random seed per session (`val effectiveSeed = remember(seed) { seed ?: Random.Default.nextLong() }`).
3. [x] **Fix Bug 2 (`ThresholdSearchSurface` cursor position on back navigation)**:
   - Maintain `TextFieldValue` in `ThresholdSearchSurface` initialized with `selection = TextRange(query.length)`.

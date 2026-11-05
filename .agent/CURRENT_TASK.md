# Active Task: Reset Search Result Scroll Position on Query Change (`:app`)

- **Issues:**
  - [#46 fix(search): reset search result list scroll position to top on query change](https://github.com/garanj/threshold/issues/46)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine (Search Surface Polish)
- **Status:** Complete (Awaiting PR Review)
- **Branch:** `fix/46-search-scroll-reset`

---

## Objectives
1. [x] **Failing Tests (TDD Red)**:
   - Compose host test verifying `firstVisibleItemIndex` resets to 0 when query changes (specifically reproducing `chr` -> `c` -> `coo` -> `chr`).
   - Manual scroll test: user scrolls to index 2, types a character, list snaps back to index 0.
   - Guarded rotary haptics: programmatic scroll reset does not fire false rotary clicks.
   - Instrumented device test (`androidTest`) in `app` testing UI Automator live scrolling and query refinement on device (`ThresholdSearchScrollInstrumentedTest.kt`).
2. [x] **Implementation (TDD Green)**:
   - In `ThresholdSearchSurface.kt`, hoist `listState: LazyListState = rememberLazyListState()`.
   - Add `LaunchedEffect(query) { listState.scrollToItem(0) }` so every query change snaps the results list to the top.
   - Guard `hapticEngine.rotaryDetent()` in `LaunchedEffect(listState)` with `if (listState.isScrollInProgress)` so programmatic resets remain silent.
3. [x] **Verification & PR**:
   - Run `./gradlew test` (all 81 unit tests pass).
   - Run `./gradlew spotlessCheck` (clean).
   - Create PR assigned to `garanj` and provide review URL.

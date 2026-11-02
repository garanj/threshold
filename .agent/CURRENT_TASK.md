# Active Task: Search Interface & Golden Screenshot Verification (`:app`)

- **Issue:** [#21 feat(app): implement search interface and golden screenshot verification](https://github.com/garanj/threshold/issues/21)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** Complete / Ready for Review
- **Branch:** `feat/21-search-interface`

---

## Objectives
1. [x] **Search ViewModel / State Holder (`:app`)**:
   - Bound `PackageIndexSource` (`:data`) and `SearchEngine` (`:core`) via `SearchViewModel`.
   - Reactive updates on `observePackageChanges()` so package install/uninstall updates search candidates in real-time.
   - Preserves `AppAccessState` (Direct, Guarded, Blocked).
   - Unit tested with `SearchViewModelTest`.
2. [x] **App Launcher Helper (`:app`)**:
   - `AndroidAppLauncher` dismissing IME cleanly before triggering `LauncherApps.startMainActivity`.
   - Unit tested with `AndroidAppLauncherTest`.
3. [x] **Search Surface UI (`ThresholdSearchSurface.kt`)**:
   - Typographic search input field with `BasicTextField` and pure white cursor.
   - Results list displaying filtered apps with fixed-pitch alignment.
   - Visual indicator (`GUARDED`, `ACTION`) for friction-guarded applications and shortcuts.
   - Empty state (`NO MATCHING APPS`) when no candidates match.
4. [x] **Integration in `MainActivity`**:
   - Embedded `ThresholdSearchSurface` inside `ThresholdHomeCanvas`.
5. [x] **Roborazzi Golden Screenshot Tests**:
   - `snapshotSearchSurfaceWithResults`: App appears in search results when user types query.
   - `snapshotSearchSurfaceGuardedFriction`: Guarded friction visual indicator state.
   - `snapshotSearchSurfaceEmptyState`: Empty state when query has no matches.
6. [x] **Verification**:
   - JVM unit tests and Roborazzi golden screenshot tests passed (`./gradlew test`).
   - Spotless check passed (`./gradlew spotlessCheck`).
   - Release APK minified and built cleanly: 2.5 MB (`./gradlew :app:assembleRelease`).

# Active Task: Phase 1 Retrospective Hardening (`:core`, `:data`, `:app`)

- **Issue:** [#25 [Fix]: Phase 1 Retrospective — Performance, Concurrency, Launcher & Kotlin Hardening](https://github.com/garanj/threshold/issues/25)
- **Milestone:** Phase 1 Hardening & Physical Device Testing
- **Status:** Complete / Ready for Review
- **Branch:** `fix/25-phase-1-retrospective`

---

## Objectives

1. [x] **`:core` Fixes & Regression Tests (`B-5`, `B-6`, `P-2`, `P-4`, `K-2`)**:
   - Precomputed `id: String` on `ComponentKey` (`flattenToString()`) and cached `directItem: SearchItem.App` on `AppSearchCandidate` to avoid per-keystroke allocations.
   - Supported multi-character word-boundary prefix matching in `FuzzyMatcher.matchWordBoundary` (so `"maps"` matches `"Google Maps"` in `WORD_BOUNDARY` tier and ranks above acronyms).
   - Used `maxOf(keywordScore, labelScore)` for `ActionCandidate` matching in `SearchEngine`.
   - Added deterministic tie-breaking to `SearchEngine.SCORE_COMPARATOR` (by case-insensitive label, then item key).
   - Added unit tests in `FuzzyMatcherTest`, `SearchEngineTest`, and `ComponentKeyTest`.

2. [x] **`:data` Fixes & Regression Tests (`B-1`, `L-2`)**:
   - Removed blocking `Thread.sleep(200)` from `LauncherApps.Callback` on `mainHandler` in `AndroidPackageIndexSource`; dispatched retries asynchronously on `ioDispatcher` via coroutine `delay(200)`.
   - Filtered out Threshold's own package (`selfPackageName`) from `getInstalledApps()` and `observePackageChanges()`.
   - Added non-blocking callback execution timing test, async retry test, and self-package filtering test in `AndroidPackageIndexSourceTest`.

3. [x] **`:app` Fixes & Regression Tests (`B-2`, `B-3`, `B-4`, `B-7`, `P-1`, `P-2`, `P-3`, `P-4`, `L-1`, `L-3`, `L-4`, `K-1`)**:
   - Added `compose_stability.conf` in `:app` and wired `composeCompiler { stabilityConfigurationFiles.add(...) }` in `app/build.gradle.kts`, plus `ArchitectureAndConfigGuardTest` to guarantee `:core` domain classes remain configured as stable and `Thread.sleep` is never reintroduced in production sources.
   - Fixed `app/build.gradle.kts` and `ThresholdScreenshotTest` so `roborazzi.test.verify` is enabled by default against committed `app/src/test/snapshots/*.png` goldens unless `-Proborazzi.test.record=true` is explicitly provided.
   - Implemented `DialerPackageResolver` to query `TelecomManager.defaultDialerPackage` (with `ACTION_DIAL` fallback) so `com.google.android.dialer` and OEM dialers are properly AllowListed.
   - Refactored `SearchViewModel` to use lock-free `@Volatile` candidate snapshots, `AtomicBoolean` initialization, re-run `performSearch` when cold-start `getInstalledApps()` completes while a query is active, wire `searchWithAutoLaunch`, and evaluate launches via `IntentGatekeeper.evaluateLaunch`.
   - Eliminated per-keystroke allocations in `ThresholdSearchSurface` and `ThresholdHomeCanvas`, initialized clock/date synchronously on Frame 0, and scoped `ACTION_TIME_TICK` receiver registration to `Lifecycle.State.STARTED` (`ON_START`..`ON_STOP`).
   - Failed safely in `AndroidAppLauncher` when a Work Profile `UserHandle` is unresolved and passed `ActivityOptions.makeBasic().toBundle()`.
   - Handled `onNewIntent` in `MainActivity` to dismiss search/IME when pressing Home while already on Threshold.

# Active Task: Issue #69 — Eliminate Callback Registration Race & Broadcast Timeout in `:data` Package Index Test

- **Issue:** [#69](https://github.com/garanj/threshold/issues/69)
- **Branch:** `fix/69-data-package-lifecycle-test`
- **Status:** Ready for Review

## Checklist
- [x] **1. Diagnose `:data` failure on post-merge `main` run (`#43200480982`):**
  - Identified race between `testScope!!.launch { packageIndexSource.observePackageChanges().collect { ... } }` (dispatched asynchronously onto `Dispatchers.Default` -> `Dispatchers.IO`) and `installFixtureApk()` running immediately on the test thread once `BroadcastQueueModernImpl` was already idle.
  - Identified unsynchronized `mutableListOf<PackageChangeEvent>()` shared across threads.
  - Identified missing `am wait-for-broadcast-idle` after `installFixtureApk()` and `pm uninstall`.
- [x] **2. Fix `AndroidPackageIndexSourceLifecycleInstrumentedTest`:**
  - Inject `ioDispatcher = Dispatchers.Unconfined` and collect with `CoroutineStart.UNDISPATCHED` so `launcherApps.registerCallback(callback, mainHandler)` executes synchronously before `installFixtureApk()` runs.
  - Use `CopyOnWriteArrayList<PackageChangeEvent>()` for thread-safe event collection.
  - Run `am wait-for-broadcast-idle` after `installFixtureApk()` and `pm uninstall`, and extend callback wait timeout to 30s.
- [x] **3. Verification:**
  - Verified locally on `ci34phone` (`-wipe-data -cores 2`).
  - Verified `./gradlew spotlessCheck test` passes.

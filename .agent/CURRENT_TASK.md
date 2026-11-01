# Active Task: Android PackageIndexSource & Multi-User Support (`:data`)

- **Issue:** [#13 feat(data): implement PackageIndexSource with LauncherApps and multi-user support](https://github.com/garanj/threshold/issues/13)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** Complete / Ready for Review
- **Branch:** `feat/13-package-index-source`

---

## Objectives
1. [x] Implement `InstalledApp` domain model in `:core` and update `PackageIndexSource`:
   - `InstalledApp(val component: ComponentKey, val label: String)`
   - `PackageChangeEvent.Added(val app: InstalledApp)`
   - `PackageChangeEvent.Removed(val component: ComponentKey)`
   - `PackageChangeEvent.Changed(val app: InstalledApp)`
2. [x] Implement `AndroidPackageIndexSource` in `com.garan.threshold.data.packageindex`:
   - Query all launchable activities via `LauncherApps.getActivityList(null, user)`.
   - Iterate over `launcherApps.profiles` and map each `UserHandle` to `userSerial` via `UserManager.getSerialNumberForUser(user)`.
   - Map each activity to core domain `ComponentKey(packageName, className, userSerial)`.
   - Maintain cached known component mappings for reliable resolution of uninstalled activities upon `onPackageRemoved`.
   - Observe live package changes (`Added`, `Removed`, `Changed`, `onPackagesAvailable`, `onPackagesUnavailable`) via `callbackFlow` and `LauncherApps.Callback`.
   - Clean listener unregistration upon flow cancellation.
3. [x] Setup test fixture module `:fixtures:dummy-app`:
   - Minimal in-tree Android application module with a launcher activity for real OS `pm install` / `pm uninstall` testing on physical devices/emulators.
4. [x] Exhaustive unit tests in `data/src/test/kotlin/com/garan/threshold/data/packageindex/`:
   - `AndroidPackageIndexSourceTest` (Robolectric):
     - Multi-user / work profile query resolution across distinct `userSerial` handles.
     - Event emissions for `onPackageAdded`, `onPackageRemoved`, `onPackageChanged`, `onPackagesAvailable`, and `onPackagesUnavailable`.
     - Live flow emission when OS package callbacks fire.
     - Lifecycle cleanup upon flow collector cancellation.
5. [x] Quality gates:
   - Zero `android.*` imports in `:core`.
   - `./gradlew test spotlessCheck` passes.

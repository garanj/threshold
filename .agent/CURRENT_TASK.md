# Active Task: Real OS Package Lifecycle Instrumented Test (`:data:androidTest`)

- **Issue:** [#15 test(data): add real OS package lifecycle instrumented test with fixture APK](https://github.com/garanj/threshold/issues/15)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** Complete / Ready for Review
- **Branch:** `feat/15-package-lifecycle-instrumented-test`

---

## Objectives
1. [x] Wire `:fixtures:dummy-app` APK into `:data:androidTest` assets:
   - Configured custom `CopyDummyApkTask` wired via AGP Variant API `variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(...)`.
2. [x] Add `androidTest` dependencies to `data/build.gradle.kts`:
   - `androidx.test.uiautomator:uiautomator`, `androidx.test:runner`, `truth`, `kotlinx-coroutines-test`.
3. [x] Implement `AndroidPackageIndexSourceLifecycleInstrumentedTest` in `data/src/androidTest/kotlin/com/garan/threshold/data/packageindex/`:
   - Extracts fixture APK from androidTest assets to device cache directory.
   - Cleans up any pre-existing installation of `com.garan.threshold.fixtures.dummy`.
   - Collects from `AndroidPackageIndexSource.observePackageChanges()`.
   - Executes `pm install -r <apkPath>` via `UiDevice.executeShellCommand`.
   - Asserts `PackageChangeEvent.Added` is emitted with component `com.garan.threshold.fixtures.dummy` and valid label.
   - Asserts app appears in `packageIndexSource.getInstalledApps()`.
   - Executes `pm uninstall com.garan.threshold.fixtures.dummy` via `UiDevice.executeShellCommand`.
   - Asserts `PackageChangeEvent.Removed` is emitted for the component.
   - Asserts app no longer appears in `packageIndexSource.getInstalledApps()`.
4. [x] Quality gates:
   - `./gradlew :data:assembleDebugAndroidTest` compiles and packages successfully.
   - `./gradlew test spotlessCheck` passes.

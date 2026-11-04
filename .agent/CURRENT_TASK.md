# Active Task: Minimalist Caret Adaptive App Icon (`:app`)

- **Issue:** [#41 feat(app): implement minimalist caret adaptive app icon and monochrome layer](https://github.com/garanj/threshold/issues/41)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine (Task 2.3 Checkpoint 3 Preparation)
- **Status:** Complete (Awaiting PR Review)
- **Branch:** `feat/41-adaptive-app-icon`

---

## Objectives
1. [x] **Adaptive Icon Specifications**:
   - 108dp x 108dp total canvas size.
   - Visible safe area mask: 72dp diameter circle / squircle centered at (54dp, 54dp).
   - Inner safe zone: 66dp diameter circle (radius 33dp centered at (54dp, 54dp)). Caret fits inside radius <= 29.41dp.
   - Solid AMOLED black background (`#000000`).
   - Pure white caret (`#FFFFFF`).
   - Android 13+ (API 33+) monochrome layer for Material You themed icons.
2. [x] **Drawable & Mipmap Architecture**:
   - `app/src/main/res/drawable/ic_launcher_background.xml` (solid AMOLED black `#000000`)
   - `app/src/main/res/drawable/ic_launcher_foreground.xml` (108dp viewport, centered caret)
   - `app/src/main/res/drawable/ic_launcher_monochrome.xml` (white caret on transparent for themed icon)
   - `app/src/main/res/drawable/ic_threshold_caret.xml` (24dp standard viewport for friction screen reuse)
   - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
   - `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
   - Legacy raster mipmaps (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi) for square and round icons.
3. [x] **Manifest & App Wiring**:
   - Update `app/src/main/AndroidManifest.xml` with `android:icon="@mipmap/ic_launcher"`, `android:roundIcon="@mipmap/ic_launcher_round"`, and `android:label="@string/app_name"`.
4. [x] **Verification**:
   - Manifest and resource guard test added to `ArchitectureAndConfigGuardTest.kt`.
   - All unit tests and spotless checks pass.
   - Release APK installed and verified on Pixel 9a.

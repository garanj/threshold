# Threshold Delivery Roadmap

## Phase 1: Test Infrastructure, Core Architecture & Safe Shell (Milestone 1)
- [x] **Task 1.1: Multi-Module Gradle Scaffolding & Hygiene Baseline**
  - Setup `:core` (pure Kotlin JVM), `:data` (Android library), `:app` (Android application), `:benchmark` (Macrobenchmark).
  - Configure Spotless / ktlint, Java 17/21 compatibility, strict compiler flags (`-Werror`).
- [x] **Task 1.2: Shift-Left Quality & Testing Harness**
  - Host JVM screenshot testing harness setup (Roborazzi on `:app`).
  - LeakCanary embedded in debug builds for early memory leak detection.
  - Automated PSS memory ceiling check script/verification (< 30,000 KB PSS).
  - Pure Kotlin unit test suite infrastructure in `:core` (< 5s target execution time).
- [x] **Task 1.3: Core Domain Models & Ports (`:core`)**
  - Implement `ComponentKey`, `AppAccessState`, `FrictionPolicy`, `DndPolicy`, `Profile`.
  - Implement `PackageIndexSource` and `AppAccessController` interfaces.
- [x] **Task 1.4: Universal Dialer AllowList Invariant (`:core`)**
  - Test and verify permanent AllowListing of the system default dialer across all profile filters.
- [x] **Task 1.5: Fast Zero-Allocation Fuzzy Search (`:core`)**
  - Unicode-normalized, zero-allocation fuzzy matching with reverse-search friction logic.
- [x] **Task 1.6: Android Launcher Shell & Package Index (`:data` / `:app`)**
  - **Task 1.6a: `AndroidPackageIndexSource` Implementation & Robolectric Suite (`:data`)**
    - `LauncherApps.getActivityList` for launchable activities across personal and work profiles.
    - Multi-user mapping via `UserManager.getSerialNumberForUser`.
    - Reactive `observePackageChanges()` flow via `LauncherApps.Callback` (`Added`, `Removed`, `Changed`).
    - Fast host JVM Robolectric unit test coverage.
  - **Task 1.6b: Real OS Package Lifecycle Instrumented Test (`:data:androidTest`)**
    - Minimal in-tree test fixture APK (`:fixtures:dummy-app`) compiled directly by Gradle.
    - Device / emulator integration test using `pm install` / `pm uninstall` to verify real Android OS Binder callback delivery.
- [x] **Task 1.7: Typographic Surface & Insets Foundation (`:app`)**
  - Clean typographic home display with OpenType `tnum, zero` numerals and `PlatformTextStyle(includeFontPadding = false)`.
  - WindowInsets handling (3-button vs gesture navigation).
  - Programmatic IME dismissal prior to `startActivity()`.
  - Roborazzi golden screenshot tests for insets and typography modes.
- [x] **Task 1.8: Search Interface & Golden Screenshot Verification (`:app`)**
  - Search surface binding `SearchEngine` (`:core`) to reactive `PackageIndexSource` (`:data`).
  - Reverse-search friction UI and command prefixing (`:` / `,`).
  - Roborazzi host JVM screenshot tests:
    - App appears in search results when installed and user types query.
    - App does not appear when not installed or after uninstall event.
    - Guarded friction visual indicator state in search results.

---

## Phase 2: Sensory Mechanics & Friction Engine (Milestone 2)
- [x] **Task 2.1: Micro-Haptics Engine (`:data` / `:app`)**
  - `VibrationEffect.Composition` implementation: Rotary Detent (`PRIMITIVE_CLICK`), Relay Snap (`PRIMITIVE_HEAVY_CLICK` + 30ms tick), Resistance Detent.
- [x] **Task 2.2: Motion System (Optics, Not Cartoons)**
  - 120Hz decelerating optical curves (`cubic-bezier(0.16, 1, 0.3, 1)`), 80ms–140ms durations.
  - Hairline Shutter Wipe transition (1dp rule).
- [x] **Task 2.3: Intentional Friction Checkpoints**
  - [x] Typed intention check (30 curated phrases, case-insensitive illumination, lock-tumbler resistance) (#35).
  - [x] Drag-to-match icon sorting check (6 monochrome glyph pairs, unified Canvas, 120Hz zero-allocation translation, optical snap-back) (#37).
  - [x] Caret tracing meditative delay checkpoint (3-second continuous trace, optical ease-in/ease-out, hairline track, 14dp indicator) (#43).
  - *(Note: Pre-session budgeting deferred to DeviceOwner mode; Visual app decay discarded by design decision).*
- [x] **Task 2.4: Anti-Metric Display**
  - [x] Anti-metric readout below clock / hairline divider ("REST X · Y UNLOCKS") (#48, #50).
  - *(Note: Flip-to-Silence / Flip-to-DND discarded by design decision; Dual-flank fast capture moved/discarded; Launcher Action Registry moved to Task 8.1).*

---

## Phase 3: Configuration, Base Settings & Profile Cascading Engine (Milestone 3)
- [ ] **Task 3.1: Base Settings, Quick Actions & 3-Tier App Configuration**
  - **General Settings**: Show Unlocks, Show Rest, Require full typing of guarded apps.
  - **Quick Actions**: Up to 7 slots beneath empty search box; reflects calculated state (Blocked = greyed out, Guarded = badge, Standard = launchable); hidden when typing search queries.
  - **Base App Configuration**: Reusable 3-tier triage screen (Blocked, Guarded, Standard/Normal) with single-tap triage icons, alphabetical ordering, and friction override toggle + radio (Typed Intention, Drag Sort, Caret Tracing, Random).
  - **Base Friction Selection**: Friction radio group for base guarded apps (including Random).
  - **Entry Points**: First-run hint ("long-press to configure", 20s fade, permanent flag), home screen long-press gesture, and `:settings` command.
  - **Reconciliation**: Package uninstall listener automatically removes uninstalled apps from rules and quick actions.
  - **Universal Dialer Invariant**: Permanently Standard; omitted from configuration picker.
- [ ] **Task 3.2: Custom Profiles & Cascading Resolution Engine (`:core` / `:data`)**
  - Profile triggers: Schedules (start day/time to end day/time), NFC (duration timer and/or tap-to-relock), Geofence (list of lat/long/radii), Manual.
  - User-ordered cascading evaluation: Top-to-bottom active profile evaluation with base profile fallback.
  - Dynamic profile disable invariant when all app rules are removed due to uninstallation.
  - Zero-allocation cached effective ruleset computation.
- [ ] **Task 3.3: Custom Profile Management UI (`:app`)**
  - Profile list management (create, edit, delete, enable/disable, reorder).
  - Reusing the 3-tier app configuration screen for custom profile overrides.
  - Live active profile indicator below the clock.
- [ ] **Task 3.4: Wallpaper & Lock Screen Sync (`:data` / `:app`)**
  - Solid AMOLED black / profile color wallpaper application.
  - Opt-in `syncLockScreenWallpaper` toggle (`FLAG_LOCK`).

---

## Phase 4: Spatial Context & Geofencing (Milestone 4)
- [ ] **Task 4.1: Low-Power Geofencing API Integration (`:data`)**
  - Hardware-backed geofences (Home, Office, Gym) without continuous GPS polling.
- [ ] **Task 4.2: Temporal Schedule Transitions (`:data`)**
  - AlarmManager exact alarms for precise circadian schedule boundaries.
- [ ] **Task 4.3: Geofence State Reconciliation & Safety**
  - Handle location permission states, airplane mode, and reboot rescheduling via `BootReceiver`.

---

## Phase 5: Physical NFC Ecosystem (Milestone 5)
- [ ] **Task 5.1: Foreground NFC Reader Adapter (`:data`)**
  - `NfcAdapter.enableReaderMode` bound strictly to `onResume`/`onPause` with `FLAG_READER_NFC_A` and `FLAG_READER_SKIP_NDEF_CHECK`.
- [ ] **Task 5.2: Physical Tokens & Passes (`:core` / `:data`)**
  - The Downtime Token (30m countdown pass; quiet expiration with haptic click).
  - The Bedside Sleep Dock (activates Dumbphone Mode and pitch-black wallpaper).
  - The Departure Gate & Analog Companion profiles.
- [ ] **Task 5.3: Pair-to-Unlock Handshake (`:core` / `:app`)**
  - Anti-impulse challenge requiring tapping Tag A then Tag B across the house within 90 seconds.

---

## Phase 6: Device Owner & DPC Mode (Milestone 6)
- [ ] **Task 6.1: DPC Receiver & Dynamic Tier Detection (`:app`)**
  - `ThresholdDeviceAdminReceiver` implementation.
  - Runtime inspection via `DevicePolicyManager.isDeviceOwnerApp`.
- [ ] **Task 6.2: Kernel-Level Package Suspension & Lock Task**
  - `setPackagesSuspended` via `AppAccessController` port.
  - Native `setLockTaskPackages` multi-app AllowListing (stripping Overview/Recents from System UI).
- [ ] **Task 6.3: Anti-Tamper & Distraction Lockdown**
  - `setUninstallBlocked` and `setUserControlDisabledPackages` during active focus sessions.
  - `setStatusBarDisabled` notification shade lockdown.
- [ ] **Task 6.4: WebUSB Provisioning & Dignified Unenrollment**
  - WebUSB setup instructions and companion client web app (@yume-chan/adb).
  - In-app dignified unenrollment with optional delayed cool-down timer.
  - External ADB CLI revocation fallback.

---

## Phase 7: Hardening, Baseline Profiles & Release Readiness (Milestone 7)
- [ ] **Task 7.1: Automated Baseline Profile Generation (`:benchmark`)**
  - BaselineProfileGenerator capturing critical launch and scroll paths for AOT compilation.
- [ ] **Task 7.2: Macrobenchmark Startup & Frame Timing Suite (`:benchmark`)**
  - MacrobenchmarkRule measuring cold/warm startup and frame deadline misses (< 16ms/8ms at 120Hz).
- [ ] **Task 7.3: Full RTL & Typographic Archetype Audit**
  - Comprehensive Paparazzi visual regression pass across all 4 typographic archetypes in LTR & RTL.
- [ ] **Task 7.4: CI Hardening & Release Artifact Signing**
  - ProGuard/R8 rules optimization, reproducible release builds.

---

## Phase 8: Miscellaneous & Enhancements (Milestone 8)
- [ ] **Task 8.1: Launcher Action Registry & Dispatcher (`:`, `,` prefix)**
  - *(Moved from Phase 2 Task 2.4; dual-flank panels discarded).*
  - Action Registry in `:app`/`:data`: Register core utility actions (`:settings`, `:wallpaper`, `:dnd`, `:note`).
  - Wire `ThresholdHomeScreen` action execution: Handle `SearchItem.Action` in `onItemClick` with appropriate Android system intents.
  - Action quick-launch on IME Go action.

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
- [ ] **Task 2.3: Intentional Friction Checkpoints**
  - Typed intention check (4-word sentence confirmation).
  - Pre-session budgeting (5m | 15m | Unlimited).
  - Visual app decay (contrast fading after 7 days idle).
- [ ] **Task 2.4: Dual-Flank Fast Capture**
  - Left Swipe: Minimal Action Ledger (zero-latency checklist, haptic toggle, strikethrough at 30% luminance, horizontal swipe dismissal).
  - Right Swipe: Borderless Ephemeral Scratchpad (autofocus plain text writing surface, auto-persisted, haptic clear).
- [ ] **Task 2.5: Flip-to-Silence & Anti-Metric Display**
  - Accelerometer/orientation flip-to-DND.
  - Anti-metric readout below clock ("Unlocked X times · Last rest: Y mins ago").

---

## Phase 3: Configuration & Conflict Resolution Engine (Milestone 3)
- [ ] **Task 3.1: 4-Tier Conflict Resolution Engine (`:core`)**
  - Deterministic evaluation: Tier 1 (Ephemeral/NFC) > Tier 2 (Schedules) > Tier 3 (Geofences) > Tier 4 (Ambient Fallback).
  - Intra-tier schedule overlap resolution (Most Restrictive Rule wins).
- [ ] **Task 3.2: Room & DataStore Persistence (`:data`)**
  - Local database schema for profiles, schedules, and active tokens.
  - Reactive streams (`Flow`) for active profile emissions.
- [ ] **Task 3.3: In-UI Conflict Highlighting & Diagnostics**
  - Natural-language conflict explanations ("At Home after 21:00, Night Rest takes precedence").
  - Live home indicator string below the clock.
- [ ] **Task 3.4: Wallpaper & Lock Screen Sync**
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

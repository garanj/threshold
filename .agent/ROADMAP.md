# Threshold Delivery Roadmap

## Phase 1: Core Shell & Safe System Integration (Milestone 1)
- [ ] **Task 1.1: Multi-Module Gradle Scaffolding**
  - Setup `:core` (pure Kotlin JVM), `:data` (Android library), `:app` (Android application), `:benchmark` (Macrobenchmark).
  - Configure Spotless / ktlint, Java 17/21 compatibility, strict compiler flags (`-Werror`).
- [ ] **Task 1.2: Core Domain Models & Ports (`:core`)**
  - Implement `ConfigurationProfile`, `ComponentKey`, `DndPolicy`, `AppFilterRule`.
  - Implement `PackageIndexSource` and `AppAccessController` interfaces.
- [ ] **Task 1.3: Universal Dialer Whitelist Invariant (`:core`)**
  - Test and verify permanent whitelisting of the system default dialer across all profile filters.
- [ ] **Task 1.4: Fast Zero-Allocation Fuzzy Search (`:core`)**
  - Unicode-normalized, zero-allocation fuzzy matching with reverse-search friction logic.
- [ ] **Task 1.5: Android Launcher Shell & Home Role (`:app`)**
  - `MainActivity` configured with `CATEGORY_HOME` and `CATEGORY_DEFAULT`.
  - Android `LauncherApps.Callback` lifecycle integration with soft-delete support (`is_installed = false`).
  - Multi-user / work profile support (`UserManager.userProfiles`).
- [ ] **Task 1.6: Typographic Surface & Insets Foundation (`:app`)**
  - Clean typographic home display with OpenType `tnum, zero` numerals and `PlatformTextStyle(includeFontPadding = false)`.
  - WindowInsets handling (3-button vs gesture navigation).
  - Programmatic IME dismissal prior to `startActivity()`.

---

## Phase 2: Sensory Mechanics & Friction Engine (Milestone 2)
- [ ] **Task 2.1: Micro-Haptics Engine (`:data` / `:app`)**
  - `VibrationEffect.Composition` implementation: Rotary Detent (`PRIMITIVE_CLICK`), Relay Snap (`PRIMITIVE_HEAVY_CLICK` + 30ms tick), Resistance Detent.
- [ ] **Task 2.2: Motion System (Optics, Not Cartoons)**
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

## Phase 3: Configuration Engine, Location & NFC (Milestone 3)
- [ ] **Task 3.1: 4-Tier Conflict Resolution Engine (`:core`)**
  - Deterministic evaluation: Tier 1 (Ephemeral/NFC) > Tier 2 (Schedules) > Tier 3 (Geofences) > Tier 4 (Ambient Fallback).
  - Intra-tier schedule overlap resolution (Most Restrictive Rule wins).
- [ ] **Task 3.2: Room & DataStore Persistence (`:data`)**
  - Local database schema for profiles, schedules, and active tokens.
- [ ] **Task 3.3: Low-Power Geofencing & Schedules (`:data`)**
  - Android Geofencing API integration without continuous GPS polling.
  - AlarmManager exact alarms for schedule transitions.
- [ ] **Task 3.4: Physical NFC Reader Mode (`:data` / `:app`)**
  - `NfcAdapter.enableReaderMode` bound to `onResume`/`onPause` with `FLAG_READER_NFC_A` and `FLAG_READER_SKIP_NDEF_CHECK`.
  - Physical token handling (Downtime token, Bedside puck, Pair-to-Unlock 90s dual tag handshake).
- [ ] **Task 3.5: Wallpapers & Lock Screen Synchronization**
  - Solid AMOLED black / profile color wallpaper application.
  - Opt-in `syncLockScreenWallpaper` toggle (`FLAG_LOCK`).

---

## Phase 4: Polish, Localization & Pre-Launch (Milestone 4)
- [ ] **Task 4.1: Optional Managed Device Mode (DPC Tier)**
  - `ThresholdDeviceAdminReceiver` implementation.
  - `setPackagesSuspended`, `setLockTaskPackages`, `setUninstallBlocked`, `setStatusBarDisabled`.
  - WebUSB companion web app (@yume-chan/adb) setup instructions & unenrollment workflows.
- [ ] **Task 4.2: Typographic Archetypes & Localization**
  - System, JetBrains Mono, Serif, Sans typefaces.
  - Full RTL layout mirroring.
- [ ] **Task 4.3: Paparazzi Screenshot Golden Suite**
  - Comprehensive snapshot tests across all orientations, font scales, and navigation bars.
- [ ] **Task 4.4: Macrobenchmark & Memory Ceilings**
  - MacrobenchmarkRule & BaselineProfileGenerator.
  - Strict assertion of < 30 MB TOTAL PSS memory budget.

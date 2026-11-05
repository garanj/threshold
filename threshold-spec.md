# **Threshold — Product Requirements Document**

**Project Codename:** Threshold (Tactile Minimalist Launcher)  
Platform: Android (API Level 29+ / Android 10+) · Tech Stack: Kotlin, Jetpack Compose, Coroutines/Flow, Room/DataStore  
**Document Version:** 2.5 · **Status:** Approved for Implementation

# **1\. Executive Summary & Design Thesis**

# **1.1 The Core Problem**

Modern smartphones treat every interaction with identical affordance: opening an infinite algorithmic feed requires the exact same physical and mental effort as checking a calendar. Existing digital wellness tools fail because they are either:

* **Passive & Aesthetic Only (e.g., Olauncher):** Visually minimal, but friction-free. Addictive apps launch instantly upon search or tap.  
* **Adversarial & Bloated (e.g., ScreenZen, One Sec):** Rely on invasive, fragile AccessibilityService overlays, constant background polling, and guilt-inducing charts that treat the user like an adversary.

# **1.2 The Product Thesis**

*"A launcher that treats your phone like a precision mechanical instrument, not an entertainment console."*

Threshold bridges digital minimalism software with tactile physical objects (NFC) and contextual awareness (Location/Time). It acts as an Intent Gatekeeper at the home screen and search index, rather than an invasive system police officer. It inserts meaningful cognitive and physical friction before an impulse begins, while keeping the launcher ultra-fast, local, and private.

# **2\. Core Pillars & Architectural Boundaries**

# **2.1 The "No Shenanigans" Architectural Rule**

To ensure device stability, battery efficiency, and privacy, Threshold adheres to strict system boundaries:

* **Zero Accessibility Service for App Blocking:** The launcher does not monitor active window titles, does not kill background tasks, and does not draw screen overlays over third-party apps.  
* **Intent Gatekeeper Model:** The launcher controls app availability solely within its own index and home surface. If an app is hidden or restricted, it cannot be indexed in search or launched from the home screen.  
* **Local & Offline by Default:** Zero internet permissions required for core functionality. No analytics, tracking SDKs, or cloud accounts.  
* **Standard Permissions Only:** Relies exclusively on standard Android contracts: `SET_WALLPAPER`, `ACCESS_NOTIFICATION_POLICY` (DND), fine location/geofencing, and standard foreground `NfcAdapter`.  
* **Universal Dialer AllowList Safety Invariant:** Regardless of active configuration profile restrictions or focus locks, the package holding `android.app.role.DIALER` is permanently AllowListed and accessible in universal search and emergency dial scenarios to guarantee critical communication availability.  
* **OS-Owned Task Switcher (Recents) Boundary:** The Recents overview is owned strictly by `com.android.systemui` and the OS shell. Under the standard Android Home role, third-party launchers cannot intercept or strip tasks from the system overview without brittle, battery-draining `AccessibilityService` screen-scraping. Standard Threshold strictly governs launch surfaces and intent gatekeeping, leaving OS task switching unmolested.

# **2.2 Explicit Product Non-Goals**

To avoid the bloat trap that ruins minimalist launchers, Threshold explicitly rejects:

* **No Third-Party App Widgets:** No widget host implementations.  
* **No Custom Icon Packs:** Icons are either completely disabled (pure typographic mode) or rendered as monochrome system glyphs.  
* **No Nested Folders or App Categories:** Flat lists and high-speed search only.  
* **No Cloud Sync / Social Features:** All configuration resides in local Room/DataStore.  
* **No Mandatory Enterprise Provisioning or Accessibility Screen Scrapers:** The standard product will never require root, enterprise enrollment, or `AccessibilityService` hacks to function as a daily launcher.

# **3\. Configuration Profiles & Conflict Resolution Engine**

Users can create named configurations (e.g., Home Calm, Deep Work, Transit, Night Rest) and define declarative triggers to activate them.

# **3.1 Configuration Data Schema**

| Parameter | Type | Description / Specification |
| :---- | :---- | :---- |
| id | UUID | Unique identifier for the profile instance |
| name | String | Display title (e.g., "Deep Work", "Transit", "Home Calm") |
| pinnedAppIds | List\<ComponentKey\> | 3–6 priority items displayed on the active home surface |
| allowedPackageNames | Set\<String\> | AllowListed applications accessible via Search/Launch |
| highFrictionPackageNames | Set\<String\> | Applications requiring typed intent before launching |
| reverseSearchPackageNames | Set\<String\> | Applications where auto-launch on search match is disabled |
| notificationFilter | DndPolicy | System notification filter mode (ALL, PRIORITY, or TOTAL\_SILENCE) |
| homeWallpaperColor | Long | Solid background color value (e.g., 0xFF000000 AMOLED Black) |
| syncLockScreenWallpaper | Boolean | Opt-in toggle (default false). When true, applies profile solid color to lock screen via FLAG\_LOCK; when false, preserves personal lock screen photo untouched. |
| typographyArchetype | FontStyle | Active typeface selection (SYSTEM, MONO, SERIF, SANS) |

# **3.2 The 4-Tier Conflict Resolution Engine**

When multiple profiles match simultaneously (e.g., "At Home" geofence active while a "21:00 Sleep" schedule begins), the system resolves state deterministically using a non-negotiable 4-tier hierarchy:

* **Tier 1: Physical & Ephemeral Overrides (Highest Priority):** Physical NFC token scans (e.g., 30-minute Downtime token, Desk Dock tag) and manual timer passes. Operates with an explicit expiration countdown. While active, Tier 1 preempts all lower schedules and geofences.  
* **Tier 2: Temporal Schedules:** Scheduled time-windows (e.g., 21:30–06:30 Sleep Mode, Morning Focus). Circadian rhythm and sleep supersede physical location.  
* **Tier 3: Spatial / Location Geofences:** Low-power hardware geofences (e.g., Home, Office, Gym). Only active if no higher-tier Temporal Schedule or NFC Override is claiming the device.  
* **Tier 4: Default Ambient Fallback (Lowest Priority):** Applied when outside all geofences, schedules, and active token passes.

## **Edge Cases & Determinism**

* **Intra-Tier Overlaps:** If two schedules overlap in Tier 2, the **Most Restrictive Rule** wins (the profile allowing the fewest total applications).  
* **In-UI Conflict Highlighting:** When setting up triggers, the UI displays clear natural-language resolution notes (e.g., "At Home after 21:00, Night Rest takes precedence over Home Calm").  
* **Live Home Indicator:** The active profile and its driver are quietly rendered below the clock (e.g., "21:04 · Night Rest \[Scheduled\]").

# **4\. Sensory Contract: Micro-Haptics, Motion, & Restraint**

# **4.1 Bespoke Micro-Haptics**

Threshold utilizes `VibrationEffect.Composition` for precise physical feel:

* **The "Rotary Detent":** Scrolling through the alphabet index fires `PRIMITIVE_CLICK` / `PRIMITIVE_TICK` at fixed pixel intervals, mimicking the knurled metal dial of high-end audio equipment.  
* **The "Relay Snap" on NFC Tap:** Tag recognition triggers a sharp `PRIMITIVE_HEAVY_CLICK` paired with a low-amplitude `PRIMITIVE_TICK` (30ms later), simulating a mechanical relay snapping shut.  
* **The Threshold Resistance Click:** Tapping an app marked with intentional friction delivers a distinct, firm resistance detent.

# **4.2 Motion Principles: Optics, Not Cartoons**

* **Zero Bouncy Springs:** Eliminates damping overshoot, rubber-banding, and playful physics.  
* **Decelerating Optical Curves:** 120Hz-tuned, tight decelerating curves (`cubic-bezier(0.16, 1, 0.3, 1)`) with micro-durations strictly between 80ms and 140ms.  
* **The Hairline Shutter Wipe:** Profile switches and search drawer opens are anchored by a single 1dp hairline rule that slides smoothly across the plane.

# **4.3 Acoustic Silence & Zero-State Calm**

* **Zero Audio Clutter:** No synthesized digital beeps or alert chimes.  
* **The Zero-State Display:** If idle, the home screen renders only a clean typographic clock and hairline rule.  
* **No Onboarding Carousels:** First run displays a clean black screen: "This phone is a tool. Tap anywhere to begin."

# **5\. Intentional Friction & Wellbeing Mechanics**

* **Typed Intention Check:** Tapping a high-friction app requires typing a 4-word phrase (e.g., "I am consciously opening Reddit") before launch.  
* **Pre-Session Budgeting:** Opening recreational apps prompts a calm duration choice: 5m \| 15m \| Unlimited.  
* **Reverse Search Drag:** Utility apps auto-launch on unique match; high-friction apps require typing the complete name before launch is enabled.  
* **Visual App Decay:** Apps unlaunched for over 7 days visually fade in contrast and drop to an archived tier.  
* **The "Anti-Metric" Display:** Quiet line below clock: "Unlocked 14 times today · Last rest: 38 mins ago".  
* **Dual-Flank Fast Capture (Lists & Scratchpad):**  
  * **Swipe Left (Action Ledger / Checklist):** Opens a minimal, zero-latency checklist. Tap to toggle completion with a `PRIMITIVE_CLICK` haptic detent; completed tasks automatically strike through, dim to 30% luminance, and sink to the bottom. Swiping items horizontally dismisses/deletes them. Zero tags, folders, or due dates.  
  * **Swipe Right (Ephemeral Scratchpad / Free Text):** Opens a borderless plain-text writing surface with autofocus keyboard input for quick thoughts, measurements, and draft snippets. Persists automatically across keystrokes without explicit file saving. Includes clean 'Copy all' and haptic-guarded 'Clear' actions.  
* **Flip-to-Silence:** *(Discarded by design decision to eliminate unnecessary sensor polling and avoid launcher scope creep).*

# **6\. Physical NFC Ecosystem & Profile Modes**

Foreground reader mode (`NfcAdapter.enableReaderMode`) coupled with physical objects (turned wood pucks, brass coins):

* **The Downtime Token:** Tapping grants 30 minutes of open access; expires quietly with a haptic click.  
* **The Departure Gate:** Doorframe tag; trims phone down to Maps, Transit, Camera, Notes, and Audio.  
* **The Analog Companion:** Notebook/journal tag; reduces launcher to stopwatch and scratchpad with Total Silence DND.  
* **The Pair-to-Unlock Handshake:** High-friction anti-impulse challenge requiring tapping Tag A then Tag B across the house within 90 seconds.  
* **The Bedside Sleep Dock:** Nightstand tag; activates Dumbphone Mode (Alarm \+ Dialer only) and pitch-black wallpaper.

# **7\. UI, Typography, & System Surfaces**

# **7.1 System Integration Boundaries**

* **Lock Screen Boundary:** Lock screen UI and clock are owned strictly by Android System UI (zero direct modification).  
* **Synchronous Wallpaper Sync & Lock Screen Discretion (Opt-In):** Home screen background color is managed directly. Overriding the lock screen wallpaper (`WallpaperManager.FLAG_LOCK`) is strictly an opt-in per-profile preference (`syncLockScreenWallpaper: Boolean`, default false). If disabled, the user's custom photo or wallpaper is preserved without interference. If enabled (e.g. Bedside Dumbphone mode), the solid background synchronizes to both screens for distraction minimization.

# **7.2 Typography & Localization**

* **Typographic Rigor:** OpenType `fontFeatureSettings = "tnum, zero"` on numerals to eliminate digit width jitter. `PlatformTextStyle(includeFontPadding = false)` for exact vertical centering.  
* **Curated Archetypes:** System Default, JetBrains Mono, Editorial Serif, Geometric Sans.  
* **Localization & RTL:** Automatic layout mirroring using Jetpack Compose start/end semantics and Unicode normalization for search indexing.

# **7.3 Settings Access Specification**

* **Primary Canvas Gesture (Long-Press):** Touching and holding any empty space on the home screen for 400ms triggers a crisp mechanical `PRIMITIVE_CLICK` haptic detent, opening a modal surface with Threshold Settings, System Settings, and Visual Styling.  
* **Universal Search Indexing (Fallback & Power Users):** Typing `settings`, `threshold`, or `android` surfaces direct shortcuts. Entering a comma (`,`) or colon (`:`) immediately filters the index strictly to launcher tools and preferences.  
* **Active Context Label Touchpoint:** Tapping the subtle active profile metadata string below the clock displays active rule diagnostics and an edit shortcut.

# **8\. Technical Architecture & Edge-Case Safeguards**

* **Multi-User / Work Profiles:** Uses `LauncherApps.getActivityList()` iterating `UserManager.userProfiles` with badged icons.  
* **Safe Package Lifecycle:** Uses `LauncherApps.Callback` and soft-deletes (`is_installed = false`) to prevent Play Store updates from wiping user rules.  
* **IME Handling:** Programmatically dismisses keyboard via `WindowInsetsController` prior to `startActivity()` to eliminate redraw flicker.  
* **RAM Retention:** Stores no Bitmaps in long-lived state; pure typography mode maintains memory under 25MB indefinitely.  
* **Battery-Safe NFC:** Bound strictly to `onResume` / `onPause` with `FLAG_READER_NFC_A` and `FLAG_READER_SKIP_NDEF_CHECK`.

# **8.4 Optional Managed Device Mode (Hardware Calibrated DPC Tier)**

While Threshold operates out-of-the-box as a standard, privacy-first Android Home app (consuming under 25 MB RAM with zero accessibility services), it includes an optional Device Policy Controller (DPC) receiver:

* **Opt-In Provisioning (WebUSB Zero-Install Setup):**  
  * **WebUSB Provisioner Web App:** To eliminate terminal and command-line friction for non-technical users, Threshold provides a client-side web utility (hosted on GitHub Pages / custom domain) utilizing the browser WebUSB API via `@yume-chan/adb`. Users simply connect their phone via USB to a Chromium browser (Chrome/Edge/Brave), authorize the USB debugging prompt on device, and trigger a one-click automated setup. The web app executes pre-flight checks (verifying account status) and issues `dpm set-device-owner com.garan.threshold/.ThresholdDeviceAdminReceiver` directly without requiring Android SDK / platform-tools installation.  
  * **Raw ADB Shell CLI (Fallback):** Power users can alternatively provision directly from a terminal: `adb shell dpm set-device-owner com.garan.threshold/.ThresholdDeviceAdminReceiver`  
* **Dynamic Tier Detection:** At runtime, Threshold inspects `DevicePolicyManager.isDeviceOwnerApp(packageName)`. If inactive, the launcher runs its standard lightweight in-memory filtering.  
* **Kernel-Level Access Enforcement:** When provisioned as Device Owner, Threshold unlocks native, platform-enforced control:  
  * **Package Suspension (`setPackagesSuspended`):** Restricted packages are paused by the OS; tapping an existing thumbnail in Recents is intercepted directly by Android with a system pause dialog ('This app is paused by Threshold'), eliminating the Recents switcher escape hatch without background polling.  
  * **Native Lock Task Mode (`setLockTaskPackages` / `setLockTaskFeatures`):** Allows AllowListing a multi-task group (e.g., Notes, Signal, Calculator) while stripping the Overview/Recents gesture out of System UI at the platform level.  
  * **Zero Architectural Leaks:** Governed via an abstract `AppAccessController` interface in `core/`, preserving pure architectural separation and keeping CI testable without physical DPC provisioning.  
  * **Anti-Tamper & Uninstall Guardrail (`setUninstallBlocked` / `setUserControlDisabledPackages`):** Prevents impulsive uninstallation and bypass attempts during active focus sessions. When a scheduled profile or focus countdown is active, Threshold asserts `dpm.setUninstallBlocked(admin, packageName, true)` and `dpm.setUserControlDisabledPackages(admin, listOf(packageName))`. Settings and Play Store uninstallation and Force Stop options are locked down by the OS. Uninstallation is re-enabled during ambient idle states or via explicit cool-down unenrollment.  
  * **Notification Shade Lockdown (`setStatusBarDisabled`):** Calling `dpm.setStatusBarDisabled(admin, true)` physically disables the pull-down notification shade and quick settings toggles during deep focus blocks, closing the top-bezel distraction loop.

## **Deactivation, Unenrollment & Lifecycle Safety**

To ensure the user is never permanently locked out or trapped, Threshold provides three distinct deactivation paths:

* **In-App Dignified Unenrollment:** Accessible via Threshold Settings with configurable friction (e.g., typed intention check or 24-hour delayed cool-down timer). The app cleanly invokes `dpm.setUninstallBlocked(admin, packageName, false)`, clears package suspensions, and calls `dpm.clearDeviceOwnerApp(packageName)` to surrender admin authority gracefully.  
* **WebUSB One-Click Revocation:** The companion web utility provides an explicit 'Deactivate & Unenroll' action over WebUSB, executing admin revocation without requiring manual terminal interaction.  
* **External ADB CLI Revocation (Developer Fallback):** For builds compiled with `android:testOnly=true`, privileges can be stripped externally via: `adb shell dpm remove-active-admin com.garan.threshold/.ThresholdDeviceAdminReceiver` followed by standard `adb uninstall com.garan.threshold`.

# **9\. Testing Strategy, Benchmarking & Performance Guardrails**

* **Local Unit Tests (Local JVM):** App search indexing, priority conflict engine, and NFC payload parsing.  
* **Screenshot Tests (Paparazzi / Roborazzi on Host JVM):** WindowInsets (gesture vs 3-button navigation), font scaling (1.0x to 1.5x), and RTL layout rendering.  
* **OS Integration Tests (UI Automator on Local Emulator):** Home button contracts, package change broadcasts, and ADB-mocked NFC/Geofence broadcasts.  
* **Macrobenchmark & Baseline Profiles:** Implementation of `:benchmark` module utilizing `MacrobenchmarkRule` and Automated `BaselineProfileGenerator`.  
* **Memory Ceilings & CI Delta Checks:** TOTAL PSS memory budget strictly capped at 30,000 KB (30 MB) verified via `adb shell dumpsys meminfo`. LeakCanary embedded in local debug builds.  
* **Cloud Device Verification (Google Play Pre-Launch Report):** Automated Robo testing across real physical devices.

# **10\. MVP Delivery Roadmap**

* **Phase 1:** Core Shell & Safe System Integration.  
* **Phase 2:** Sensory Mechanics & Friction Engine.  
* **Phase 3:** Configuration Engine, Location & NFC.  
* **Phase 4:** Polish, Localization & Pre-Launch.

# **11\. Test-Driven Development (TDD) Strategy & CI Gate Pipeline**

* **Pure Kotlin Core (`core/` package):** Zero `android.*` SDK dependencies.  
* **System Wrappers / Port-and-Adapter Pattern:** All OS interfaces abstracted behind clean interfaces with in-memory test fakes.  
* **Visual & Layout Regression:** Paparazzi screenshot testing executed on host JVM.  
* **Automated Headless Emulator Instrumentation:** Zero-click role assignment via `adb shell cmd role add-role-holder android.app.role.HOME com.garan.threshold`.  
* **Multi-Stage GitHub Actions CI Pipeline:** Stage 1 Fast Host JVM Gate (\~60-90s) and Stage 2 Headless Emulator Integration (\~4-6m).

# **12\. Engineering Workflow: Issue-Driven Development & PR Gates**

* **1:1 Issue-to-PR Contract:** Branch naming, atomic scope, traceability, and merge gates.  
* **Commit & Branch Guardrails:** Local Git commit-msg hook regex matching and pre-push assertions.  
* **Automated PR Verification Gate:** Issue association check and mandatory test coverage delta checks.

# **13\. Best Practices for Issue-Driven Development & Quality Discipline**

* Vertical feature slicing (The "Thin Spike" pattern).  
* Enforced Squash-and-Merge Git hygiene.  
* Shift-left testing and local pre-push ergonomics.  
* Strict "Three-Strike Rule" for scope creep.  
* Milestone dependency chains and automated changelogs.

# **14\. Agentic Development Architecture & Token-Efficient Repository Structure**

Progressive disclosure file layout (`AGENTS.md`, `.agent/ROADMAP.md`, `.agent/CURRENT_TASK.md`, `.agent/issues/`).

# **15\. Memory & Performance Engineering Guardrails**

Zero-bitmap memory baseline, zero-allocation fuzzy search (core/), Compose layout stability (@Immutable, derivedStateOf), programmatic IME dismissal prior to `startActivity()`, and macrobenchmark AOT compilation.
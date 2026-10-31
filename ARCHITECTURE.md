# Architectural Specification: Threshold

## 1. High-Level Modular Architecture

Threshold is structured as a modular Android project prioritizing testability, separation of concerns, and host JVM execution speed:

```
                  ┌───────────────────────────────────────────────┐
                  │                    :app                       │
                  │  - Jetpack Compose UI (Theme, Surfaces)       │
                  │  - Home Role Activity & Services              │
                  │  - System Adapters (Wallpaper, Notification)  │
                  │  - DPC Receiver (DeviceAdminReceiver)         │
                  └───────────────────────┬───────────────────────┘
                                          │
                  ┌───────────────────────▼───────────────────────┐
                  │                    :data                      │
                  │  - Room Database (Profiles, Friction logs)    │
                  │  - DataStore Preferences                      │
                  │  - LauncherApps & PackageManager Adapters     │
                  │  - NFC Reader Adapter                         │
                  └───────────────────────┬───────────────────────┘
                                          │
                  ┌───────────────────────▼───────────────────────┐
                  │                    :core                      │
                  │  - PURE KOTLIN (Zero android.* dependencies)  │
                  │  - 4-Tier Conflict Resolution Engine          │
                  │  - Intent Gatekeeper & Filter State Machine   │
                  │  - Zero-Allocation Fuzzy Search Engine        │
                  │  - Domain Models & Repository Interfaces      │
                  └───────────────────────────────────────────────┘
                                          │
                  ┌───────────────────────▼───────────────────────┐
                  │                 :benchmark                    │
                  │  - MacrobenchmarkRule & Baseline Profiles     │
                  │  - Frame Timing & App Startup Metrics         │
                  └───────────────────────────────────────────────┘
```

---

## 2. The Port-and-Adapter Pattern (Hexagonal)

All platform services are abstracted behind clean interfaces in `:core`:

### Core Ports (`:core` module)
* `PackageIndexSource`: Emits installed activity list and package metadata.
* `AppAccessController`: Interface governing app availability (standard filtering vs DPC package suspension).
* `SensoryFeedbackPort`: Defines haptic primitives (`rotaryDetent`, `relaySnap`, `frictionResistance`).
* `ProfileRepository`: Reactive stream of configured profiles and active triggers.
* `SystemPolicyPort`: DND policies, solid wallpaper color application.

### Adapters (`:data` / `:app` modules)
* `AndroidLauncherAppsAdapter`: Wraps `LauncherApps` and `LauncherApps.Callback`.
* `AndroidSensoryAdapter`: Wraps `Vibrator` / `VibrationEffect.Composition`.
* `DpcAppAccessAdapter`: Wraps `DevicePolicyManager.setPackagesSuspended` and `setLockTaskPackages`.
* `RoomProfileRepository`: Implements persistence using Room with SQLCipher / standard local SQLite.

---

## 3. The 4-Tier Conflict Resolution Engine

When multiple profiles are eligible simultaneously, deterministic resolution occurs via `:core/domain/ConflictResolutionEngine.kt`:

1. **Tier 1: Physical & Ephemeral Overrides (Highest Priority):**
   - NFC token taps (e.g., 30-minute Downtime token, Bedside puck).
   - Manual timer countdown passes.
   - Operates with an absolute expiration timestamp (`Instant`). Preempts all lower tiers while active.
2. **Tier 2: Temporal Schedules:**
   - Time windows (e.g. 21:30–06:30 Night Rest). Circadian sleep rules override physical location.
   - *Intra-tier overlap tiebreaker:* **Most Restrictive Rule** wins (profile with the fewest total allowed apps).
3. **Tier 3: Spatial / Location Geofences:**
   - Hardware geofences (Home, Office, Gym). Active only if no Tier 1 or Tier 2 rule matches.
4. **Tier 4: Default Ambient Fallback (Lowest Priority):**
   - Active when no geofence, schedule, or token is claiming the device.

---

## 4. Hardware Calibrated DPC Tier (Optional Managed Device Mode)

Threshold includes an optional Device Policy Controller tier:
- Provisioned via WebUSB companion web app or `adb shell dpm set-device-owner`.
- Inspected at runtime via `DevicePolicyManager.isDeviceOwnerApp`.
- When active, leverages:
  - `setPackagesSuspended`: Suspends apps at the OS level so tapping Recents triggers the system pause dialog.
  - `setLockTaskPackages`: Native lock-task multi-app whitelisting.
  - `setUninstallBlocked` & `setUserControlDisabledPackages`: Anti-tamper during active focus sessions.
  - `setStatusBarDisabled`: Notification shade lockdown during deep focus.
- Dignified unenrollment available in-app, via WebUSB, or via ADB fallback.

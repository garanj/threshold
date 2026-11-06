# Current Task: Base Settings, Quick Actions, and 3-Tier App Configuration (#54)

## Active Issue
- **Issue**: `#54` - feat(settings): base settings, quick actions, and 3-tier app configuration
- **Branch**: `feat/54-base-settings-app-configuration`

## Checklist
- [x] **`:core` Models & Ports**:
  - [x] `GeneralSettings` model (`showUnlocks: Boolean`, `showRest: Boolean`, `requireFullTypingOfGuardedApps: Boolean`, `hasSeenFirstRunHint: Boolean`).
  - [x] `AppAssignment` and `BaseProfileSettings` models with app states (`Blocked`, `Guarded` with optional friction override, `Standard`) and base friction choice (supporting `Random`).
  - [x] `QuickActions` model (up to 7 items).
  - [x] `SettingsRepository` port interface.
  - [x] Universal dialer safety invariant: permanently `Standard` and filtered out of settings triage.
  - [x] Unit tests for all domain models and invariants in `:core` (`BaseProfileSettingsTest`, `FrictionChoiceTest`).
- [x] **`:data` Persistence & Package Reconciliation**:
  - [x] `AndroidSettingsRepository`: Local DataStore persistence implementation of `SettingsRepository`.
  - [x] Auto-reconciliation of package uninstalls: remove uninstalled package keys from base app rules and quick action slots.
  - [x] Unit tests for `AndroidSettingsRepository` and uninstall cleanup.
- [x] **`:app` UI & Home Screen Integration**:
  - [x] Settings entry points:
    - First-run hint (`"long-press to configure"`, 20s optical fade-out, permanent persistence via DataStore).
    - Long-press gesture on home screen background.
    - `:settings` command in search index.
  - [x] Main Settings screen (`ThresholdSettingsScreen`):
    - Adaptive layout with Material 3 / Threshold monochrome aesthetic.
    - General settings section toggles (`Show unlocks`, `Show rest`, `Require full typing`).
    - Quick actions section (up to 7 slots, add with dialog, remove).
    - Base app configuration cards (`Guarded`, `Blocked` counts, `"Configure Default Apps"` button).
    - Base friction policy radio selector (Typed Intention, Drag-to-Match, Caret Tracing, Random).
  - [x] Reusable 3-Tier App Configuration screen (`ConfigureAppsScreen`):
    - Alphabetical sections for *Blocked*, *Guarded*, and *Standard*.
    - Single-tap triage icons: `[Block]`, `[Guard]`, `[Reset]`.
    - Expandable rows in *Guarded* with friction override toggle & radio group.
    - Persistent Save button at the bottom.
  - [x] Home Screen integration:
    - Quick actions rendered below empty search box, hidden when search query is entered.
    - Quick action items honor calculated access state (`Blocked` greyed out with resistance detent, `Guarded` with friction gate, `Standard` direct launch).
    - Respect `showUnlocks` and `showRest` toggles in anti-metrics status bar.
    - Respect `requireFullTypingOfGuardedApps` and Random friction policy.
- [x] **Verification**:
  - [x] Host JVM unit tests (`./gradlew test` - 100% pass across `:core`, `:data`, and `:app`).
  - [x] Roborazzi golden screenshot tests for Settings, Quick Actions, and 3-tier configuration screens.
  - [x] Spotless formatting check (`./gradlew spotlessApply spotlessCheck`).

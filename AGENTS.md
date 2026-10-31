# Agent Operating Manual: Threshold

Welcome to **Threshold** (`com.garan.threshold`).
Threshold is a tactile, minimalist Android launcher designed to treat the smartphone like a precision mechanical instrument rather than an entertainment console.

This file establishes the **non-negotiable operating rules, boundaries, and standards** for any AI agent or human contributor working in this repository.

---

## 1. Prime Invariants & Architectural Boundaries ("No Shenanigans")

Every line of code committed to Threshold must adhere to these non-negotiable boundaries:

1. **Zero Accessibility Service for App Blocking:**
   - Threshold NEVER uses an `AccessibilityService` to inspect active window titles, scrape third-party screens, kill background tasks, or draw persistent blocking overlays.
   - Threshold is an **Intent Gatekeeper** at the home screen and search index. Restricted apps simply do not appear or cannot be launched from Threshold's surfaces.

2. **Universal Dialer Whitelist Safety Invariant:**
   - The package holding `android.app.role.DIALER` is **permanently whitelisted** regardless of active profiles, bedtime locks, or friction states. Emergency dialing and core phone communication must never be locked out.

3. **Pure Kotlin `core/` Module (Port-and-Adapter):**
   - The `:core` module contains all domain models, state machines, the 4-tier conflict resolution engine, intent gatekeeper logic, and fuzzy search.
   - **Zero `android.*` imports are permitted in `:core`** (except standard annotations if strictly necessary, but prefer pure Kotlin). All Android OS interactions (PackageManager, LauncherApps, WallpaperManager, NfcAdapter, NotificationManager) must be injected via interfaces defined in `:core` and implemented in `:data` or `:app`.

4. **Offline & Private by Default:**
   - Zero internet permissions (`android.permission.INTERNET`) in the core app.
   - No analytics SDKs, cloud sync, telemetry, or remote crash reporters. All state resides locally in Room / DataStore.

5. **OS-Owned Task Switcher (Recents) Boundary:**
   - In standard mode, Threshold does not tamper with the system Overview/Recents task switcher.
   - Kernel-level package suspension (`setPackagesSuspended`) and Lock Task mode are strictly isolated to the optional, opt-in **Device Policy Controller (DPC)** mode, mediated by an abstract `AppAccessController` interface.

6. **Memory Ceiling (< 30 MB Total PSS):**
   - Long-lived state must NEVER hold uncompressed `Bitmap` instances or large icon caches.
   - Typographic mode maintains memory under 25 MB. The hard ceiling in CI is 30,000 KB PSS.

---

## 2. Sensory Contract: Micro-Haptics & Motion

- **Micro-Haptics (`VibrationEffect.Composition`):**
  - Use `PRIMITIVE_CLICK` / `PRIMITIVE_TICK` for rotary index detents.
  - Use `PRIMITIVE_HEAVY_CLICK` + delayed `PRIMITIVE_TICK` (30ms) for the physical "Relay Snap" on NFC tap.
  - Firm resistance detent on friction-guarded apps.
- **Motion Restraint:**
  - **No bouncy springs.** No overshoot or playful physics.
  - 120Hz-tuned decelerating optical curves (`cubic-bezier(0.16, 1, 0.3, 1)`), durations strictly 80ms–140ms.
  - Hairline Shutter Wipe (1dp rule) anchors transitions.

---

## 3. Engineering Workflow & 1:1 Issue-to-PR Contract

We operate strictly via **Issue-Driven Development (IDD)**:

1. **Every change traces to a GitHub Issue:**
   - Never write code without an active issue.
   - Work is tracked in `.agent/CURRENT_TASK.md` while in progress.
2. **Branch Naming Convention:**
   - `feat/<issue-number>-<short-description>`
   - `fix/<issue-number>-<short-description>`
   - `refactor/<issue-number>-<short-description>`
3. **Commit Message Format:**
   - Format: `type(scope): imperative summary (#issue-number)`
   - Examples:
     - `feat(core): implement 4-tier conflict resolution engine (#12)`
     - `test(core): add edge-case unit tests for intra-tier schedule overlaps (#12)`
     - `fix(search): prevent memory allocation in fuzzy search loop (#15)`
4. **Test-Driven Development (TDD) Mandatory:**
   - **Red:** Write unit tests against the `:core` interface or fake.
   - **Green:** Implement the minimum code to pass.
   - **Refactor:** Clean up, verify memory allocations and linting.
5. **PR Acceptance Gates:**
   - All host JVM tests (`./gradlew test`) pass.
   - Zero `android.*` imports in `:core`.
   - Paparazzi screenshot tests match goldens.
   - PSS memory footprint verified within budget.

---

## 4. Progressive Disclosure Layout for Agents

To optimize token efficiency and agent context:

- **`AGENTS.md`** (this file): Invariants, core rules, DoD, architecture overview.
- **`.agent/ROADMAP.md`**: Breakdown of phases, epics, and planned tasks.
- **`.agent/CURRENT_TASK.md`**: Single source of truth for the active task being developed right now.
- **`.agent/STANDARDS.md`**: Compose stability guidelines, memory benchmarks, code style rules.
- **`.agent/issues/`**: Issue templates and ready-to-file specs.

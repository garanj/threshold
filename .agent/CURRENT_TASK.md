# Active Task: Universal Dialer AllowList Safety Invariant & Intent Gatekeeper (`:core`)

- **Issue:** [#9 feat(core): implement Universal Dialer AllowList safety invariant and intent gatekeeper](https://github.com/garanj/threshold/issues/9)
- **Milestone:** Phase 1: Test Infrastructure, Core Architecture & Safe Shell
- **Status:** In Progress
- **Branch:** `feat/9-dialer-allowlist-invariant`

---

## Objectives
1. [x] Purge any legacy terminology across repo to strictly adhere to `AllowList` and `DenyList`.
2. [x] Implement `IntentGatekeeper` in `com.garan.threshold.core.gatekeeper`:
   - `filterVisible(components: List<ComponentKey>, profile: Profile, defaultDialerPackage: String): List<ComponentKey>`
   - `resolveAccess(component: ComponentKey, profile: Profile, defaultDialerPackage: String): AppAccessState`
   - `evaluateLaunch(component: ComponentKey, profile: Profile, defaultDialerPackage: String): LaunchResult`
3. [x] Exhaustive unit tests in `core/src/test/kotlin/com/garan/threshold/core/gatekeeper/IntentGatekeeperTest.kt`:
   - Dialer is guaranteed `Direct` even under complete lockouts (`defaultAccess = Blocked`).
   - Explicit attempt to mark dialer `Blocked` in `appRules` is rejected and evaluated as `Direct`.
   - Invisible apps (`Blocked`) are strictly excluded from visible listing.
   - Guarded apps remain visible with appropriate `FrictionPolicy`.
4. [x] Invariant verifications:
   - Zero `android.*` imports in `:core` (verified via `ArchitectureBoundaryTest`).
   - Pass `./gradlew test spotlessCheck`.

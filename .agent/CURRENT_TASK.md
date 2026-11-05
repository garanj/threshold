# Active Task: Debounce Duplicate Unlock Broadcasts (`:data`, `:app`)

- **Issues:**
  - [#50 fix(data): debounce duplicate ACTION_USER_PRESENT unlock broadcasts](https://github.com/garanj/threshold/issues/50)
- **PR:**
  - [#51 fix(data): debounce duplicate ACTION_USER_PRESENT unlock broadcasts (#50)](https://github.com/garanj/threshold/pull/51)
- **Status:** Complete (Awaiting PR Review)
- **Branch:** `fix/50-unlock-broadcast-debounce`

---

## Objectives
1. [x] **Debounce Broadcast Pipeline (`:data`)**:
   - Add `DEBOUNCE_WINDOW_MS = 2000L` in `AndroidAntiMetricsRepository`.
   - Persist `KEY_LAST_UNLOCK_TIMESTAMP` in SharedPreferences.
   - Ignore rapid duplicate `ACTION_USER_PRESENT` broadcasts emitted by Pixel biometric unlock pipeline.
2. [x] **Lifecycle Cleanup (`:app`)**:
   - Provide `unregister()` method on `AndroidAntiMetricsRepository`.
   - Unregister receiver in `MainActivity.onDestroy()` to prevent leaked listeners across activity recreations.
3. [x] **Unit Testing (`:data`)**:
   - Added unit tests in `AndroidAntiMetricsRepositoryTest` for 608ms biometric duplicate broadcasts and debounce window boundaries.
4. [x] **Acceptance Gates & Deployment**:
   - `./gradlew test` passes.
   - `./gradlew spotlessCheck` passes.
   - Verified on physical Pixel 9a (`54241JEBF07233`).

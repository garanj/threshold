# Active Task: Tune Target Snap Detent to Decisive Auto-Launch Match (`:app`)

- **Issue:** [#31 fix(haptics): tune target snap to decisive auto-launch match](https://github.com/garanj/threshold/issues/31)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine
- **Status:** In Progress
- **Branch:** `fix/31-tune-target-snap-decisive-match`

---

## Objectives
1. [ ] **Haptic Trigger Refinement in `SearchViewModel`**:
   - Only trigger `targetSnap()` when a decisive match / auto-launch candidate engages (`autoLaunchCandidate != null`).
   - Suppress premature snaps on initial or ambiguous keystrokes (e.g. typing "c" when multiple results exist).
   - Prevent redundant snaps on consecutive keystrokes where the auto-launch target has already locked in.
2. [ ] **Unit Tests (`SearchViewModelTest`)**:
   - Verify typing an initial ambiguous letter ("c") does NOT fire `targetSnap()`.
   - Verify typing subsequent letters that narrow down to a decisive auto-launch candidate ("chr") DOES fire `targetSnap()`.
   - Verify typing further characters while the decisive candidate remains the same ("chro", "chrom") does NOT fire additional snaps.
   - Verify clearing or backspacing resets the state cleanly.
3. [ ] **Verification & Device Run**:
   - Run `./gradlew test spotlessCheck`.
   - Build and deploy release APK to connected Pixel 9a (`54241JEBF07233`).
   - Create PR assigned to `garanj`.

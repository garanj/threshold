# Active Task: Typed Intention Checkpoint for Guarded Apps (`:core`, `:app`)

- **Issue:** [#35 feat(friction): implement typed intention checkpoint for guarded apps](https://github.com/garanj/threshold/issues/35)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine (Task 2.3)
- **Status:** Complete (Ready for Review)
- **Branch:** `feat/35-typed-intention-checkpoint`

---

## Objectives
1. [x] **Phrase Bank Resources (`app/src/main/res/values/strings.xml`)**:
   - Define `<string-array name="typed_intention_phrases">` with 30 curated phrases.
   - Support `%s` formatting for dynamic target app name insertion.
2. [x] **Core Phrase Matching Engine (`:core`)**:
   - `TypedIntentionSession`:
     - Case-insensitive letter-by-letter matching.
     - Lock-tumbler mistyping: returns whether next char was matched, triggers resistance on mismatch.
     - Sanitizes double-tap space periods (`. `).
     - Tracks illuminated character count and completion state.
3. [x] **UI Component (`TypedIntentionSurface.kt` in `:app`)**:
   - Full-screen `AmoledBlack` with `safeDrawing` and `ime` insets padding.
   - Dual-tone text: Target phrase in `SubtleGray`, typed prefix in `PureWhite`.
   - Hidden/seamless `BasicTextField` with autofocus, predictive suggestions/autocorrect disabled (`autoCorrectEnabled = false`).
   - "Go back" button below phrase, morphs into "Open" with `hapticEngine.targetSnap()` when complete.
   - Back navigation handling (swiping back dismisses the checkpoint).
4. [x] **Home Screen Integration (`ThresholdHomeScreen.kt`)**:
   - When launching an app returns `LaunchResult.FrictionRequired(FrictionPolicy.TypedIntention)`, show the checkpoint.
   - Tapping "Open" dispatches `appLauncher.launchApp(component)` and dismisses checkpoint.
   - Temporary test rule for YouTube (`com.google.android.youtube`) in default profile marked as `Guarded(FrictionPolicy.TypedIntention)` with Phase-3 cleanup TODO.
5. [x] **Tests & Verification**:
   - Unit tests in `:core` (`TypedIntentionSessionTest`).
   - UI & Screenshot tests in `:app` (`TypedIntentionSurfaceTest`, Roborazzi snapshot).
   - `./gradlew test spotlessCheck`.
   - Release build and install to connected Pixel 9a (`54241JEBF07233`).
   - PR created and assigned to `garanj`.

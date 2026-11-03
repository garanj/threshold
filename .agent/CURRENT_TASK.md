# Active Task: Motion System: "Optics, Not Cartoons" (`:app`)

- **Issue:** [#33 feat(motion): implement 120Hz decelerating optical curves and hairline shutter wipe](https://github.com/garanj/threshold/issues/33)
- **Milestone:** Phase 2: Sensory Mechanics & Friction Engine (Task 2.2)
- **Status:** Complete / Ready for Review
- **Branch:** `feat/33-motion-system`

---

## Objectives
1. [x] **Motion Tokens & Specs (`ThresholdMotion.kt` in `:app`)**:
   - `OpticalEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)`.
   - Durations strictly bounded between `MIN_DURATION_MS = 80` and `MAX_DURATION_MS = 140`.
   - `SHUTTER_OPEN_DURATION_MS = 100`, `SHUTTER_CLOSE_DURATION_MS = 80`, `RESULT_REVEAL_DURATION_MS = 100`.
   - `opticalTween()` helper enforcing sensory contract bounds.
2. [x] **Hairline Shutter Wipe (`ThresholdSearchSurface.kt`)**:
   - 1dp Hairline divider expansion/reveal transition between ambient home state and active search state.
   - Decelerating optical reveal for the search text field and results list with `ThresholdMotion.ShutterWipeEnter` / `ShutterWipeExit`.
   - Rapid 80ms shutter close on back dismissal.
3. [x] **Result List Ingress & Optical Settling**:
   - Result list entries settle into view with optical easing using `Modifier.animateItem(...)`.
4. [x] **Tests & Verification**:
   - Unit tests in `ThresholdMotionTest` verifying easing parameters, duration boundaries, and guardrails.
   - Roborazzi host JVM screenshot tests capturing shutter wipe states (`search_shutter_open.png`).
   - `./gradlew test spotlessCheck` all passed.
   - Minified release build deployed to connected Pixel 9a (`54241JEBF07233`).
   - PR created and assigned to `garanj`.

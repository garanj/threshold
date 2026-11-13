# Current Task: Issue #82 — Prevent Status Row Vertical Layout Shift on Profile/Fault Badge Appearance

- **Issue:** [#82](https://github.com/garanj/threshold/issues/82)
- **Branch:** `fix/82-status-row-layout-shift-lock`
- **Status:** Ready for Review

## Checklist
- [x] `:app` (TDD Red): Add Compose layout test in `ThresholdHomeCanvasTest` verifying that `content()` vertical position does not shift when `activeProfiles` transitions from empty to non-empty (at 1.0x and 2.0x font scale)
- [x] `:app` (TDD Green): Implement `Layout` with unplaced reference badge measurement and `defaultMinSize` in `ThresholdHomeCanvas.kt`
- [x] `:app` Screenshots & Tests: Verify Roborazzi goldens and `./gradlew test` pass

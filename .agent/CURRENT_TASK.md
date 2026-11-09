# Current Task: Keyboard & Caret Tracing Refinements (#58)

## Active Issue
- **Issue**: `#58` - feat(ui): disable search IME suggestions, capitalize profile names, and expand caret tracing to 5 passes
- **Branch**: `feat/58-keyboard-and-caret-refinements`

## Checklist
- [x] **Disable IME Auto-Suggestions in Search (`ThresholdSearchSurface`, `ThresholdSettingsScreen`, `TypedIntentionSurface`)**:
  - [x] Set `autoCorrectEnabled = false`, `capitalization = KeyboardCapitalization.None`, `keyboardType = KeyboardType.Password` on search inputs.
- [x] **Capitalize Profile Name Input (`ScheduleEditorDialog.kt`)**:
  - [x] Set `KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done)` on profile name `OutlinedTextField`.
- [x] **5-Pass Caret Tracing (`FrictionChoice.kt`, `Color.kt`, `CaretTracingSurface.kt`)**:
  - [x] Update `FrictionChoice.CARET_TRACING` / `RANDOM` to `FrictionPolicy.TimedDelay(seconds = 17)`.
  - [x] Define 5-step monochrome luminance ramp (`CaretPass1Gray`..`CaretPass5White`) in `Color.kt`.
  - [x] Update `CaretTracingSurface` to animate 5 alternating passes (`0f..5f`, default `17_000L`) with 5 apex `rotaryDetent()`s, 4 pass-end `targetSnap()`s, and final `relaySnap()`.
  - [x] Update unit tests (`CaretTracingSurfaceTest`, `BaseProfileSettingsTest`, `CascadingResolutionEngineTest`).
- [x] **Verification**:
  - [x] `./gradlew spotlessApply test spotlessCheck` passes 100%.
  - [x] Install updated debug APK on connected Pixel 9a.

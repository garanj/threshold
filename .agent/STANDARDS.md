# Engineering Standards & Guardrails: Threshold

## 1. Jetpack Compose, Material 3 & Navigation 3 Guidelines

> **Mandatory Skill:** Whenever designing, building, refactoring, or auditing UI in `:app`, you MUST read and follow [`.agent/skills/compose-m3-ui/SKILL.md`](skills/compose-m3-ui/SKILL.md).

1. **State Immutability & Stability:**
   - Mark domain models exposed to Composables with `@Immutable` or `@Stable`.
   - Never pass unstable lambdas or mutable collections directly down the tree; prefer `ImmutableList<T>` (kotlinx.collections.immutable) or pass stable events.
2. **Recomposition Hygiene:**
   - Use `derivedStateOf` when deriving state from rapidly changing values (e.g. scroll offsets, search text lengths).
   - Never perform allocations, regex compilation, or object instantiations inside `@Composable` functions without `remember`.
3. **Motion Curves:**
   - Optical curve spec: `CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)`.
   - Durations strictly between 80ms and 140ms.
   - Zero spring physics or bouncy overshoot.
4. **Jetpack Navigation 3 (`androidx.navigation3`):**
   - All multi-screen navigation MUST use Navigation 3 (`NavDisplay`, `@Serializable` `NavKey` routes, `rememberNavBackStack()`, and `entryProvider` DSL).
   - Never use manual `enum class` screen switching (`when (currentScreen)`) or loose `mutableStateOf` route IDs in `MainActivity`.
5. **Material 3 Component & Token Fidelity (Zero "AI-Slop" UI):**
   - **No Hardcoded Colors or Font Sizes:** Never declare `Color(0x...)` literals or ad-hoc `fontSize = ...sp` in screen files. Always use `MaterialTheme.colorScheme` semantic roles and `MaterialTheme.typography`.
   - **Canonical M3 Components:** Always use `Scaffold` + `TopAppBar`, `ListItem`, `FilterChip`, `SingleChoiceSegmentedButtonRow`, `ModalBottomSheet`, and `TimePicker` / `TimeInput`. Do not hand-roll top bars, settings rows, or custom `"▲"`/`"▼"` steppers.
   - **No ASCII Icons:** Never use text characters (`"▲"`, `"▼"`, `"→"`, `"☾"`) as icons or buttons.
   - **Dialog Restraint:** `AlertDialog` is strictly reserved for binary destructive confirmations. Never put scrollable lists, app pickers, schedule editors, or multi-field forms inside a `Dialog`.
   - **Edge-to-Edge Insets:** On `Scaffold` screens, pass `innerPadding` to `LazyColumn(contentPadding = innerPadding)` and `Modifier.consumeWindowInsets(innerPadding)`. Never apply `.windowInsetsPadding(WindowInsets.safeDrawing)` on the root container of a `Scaffold` screen.
   - **48dp Minimum Touch Targets:** Every interactive control must meet the `48.dp × 48.dp` accessibility touch target minimum.
6. **UI Automation & Deterministic `testTag` Standards:**
   - **Root `testTagsAsResourceId = true`:** Always enable `@OptIn(ExperimentalComposeUiApi::class) Modifier.semantics { testTagsAsResourceId = true }` at the root `ThresholdHomeScreen` container so every `Modifier.testTag(...)` is automatically exposed as an Android `resource-id` (`By.res(...)`) for UiAutomator instrumented tests (`androidTest`), Macrobenchmark, and `android layout` inspection.
   - **When `Modifier.testTag(...)` Is Mandatory:**
     1. **Nested Sheets / Dialogs:** Any action button or text field inside a `ModalBottomSheet` or `AlertDialog` that shares a label with an underlying screen/sheet (e.g., `"Create"` / `"Save"` / `"Name"` in `ProfileRulesEditorSheet` vs `TriggerEditorSheet`).
     2. **Repeating Cards, Rows, and Rule Groups:** Controls inside a list of profiles, triggers, apps, or `AND`/`OR` rule groups MUST include the stable entity ID or rule index in the tag (e.g., `profile_toggle_<id>`, `profile_delete_<id>`, `profile_rule_<index>_add_and`, `profile_rule_<index>_remove_<triggerId>`). Never rely on `.onAllNodes(...)[index]` or `.onLast()` when a deterministic tag can be attached.
     3. **Icon-Only & Non-Text Controls:** Switches, sliders, segmented tabs, reorder buttons, and icon buttons.
   - **Shared `TestTags` Constants:** Define tag names and builder functions (`fun profileToggle(id: String) = "profile_toggle_$id"`) in `internal object` / `object` constants so production UI and tests share a single source of truth.

---

## 2. Memory & Performance Rules

1. **Zero-Bitmap Memory Baseline:**
   - Do NOT store decoded `android.graphics.Bitmap` instances in persistent state or long-lived caches.
   - If icons are rendered in glyph mode, use vector glyphs or monochromatic masks.
   - Total PSS must stay strictly **under 35 MB** in CI emulator (typographic baseline < 25 MB on device).
2. **Zero-Allocation Fuzzy Search (`:core`):**
   - The fuzzy search matcher must operate using in-place character index matching, avoiding object creation (e.g. intermediate substring lists or boxing) during keystroke search loops.
3. **IME Handling:**
   - Dismiss the software keyboard cleanly via `WindowInsetsController` prior to triggering `startActivity()`. This eliminates keyboard jitter and redraw delays during app transition.
4. **Zero `Activity` Retention in `ViewModel`s & Process-Scoped Dependencies:**
   - Never pass an `Activity` reference or a lambda that captures `this@MainActivity` (including lambdas reading `Activity` properties or calling `this@MainActivity.applicationContext`) into a `ViewModel` or `ViewModelProvider.Factory`.
   - Never instantiate shared stateful sources (such as `AndroidLocationSource`, `AndroidSettingsRepository`, or test override hooks) as per-`Activity` `by lazy` properties when a `ViewModel` also observes them—after configuration recreation (`fontScale`, `density`, `locale`), the new `Activity` UI and the surviving `ViewModel` will split onto separate instances and leak the old `Activity`.
   - Always wire Activity-independent dependencies through `ThresholdAppContainer` (owned by `ThresholdApplication` and constructed strictly with `applicationContext`).

---

## 3. Pure Kotlin Core Constraint

- **Module:** `:core`
- **Rule:** Never import anything from `android.*`.
- **Reason:** Guarantees sub-second host JVM execution for all domain tests and prevents coupling domain logic to OS release cycles.

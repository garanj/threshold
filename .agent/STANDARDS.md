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

---

## 3. Pure Kotlin Core Constraint

- **Module:** `:core`
- **Rule:** Never import anything from `android.*`.
- **Reason:** Guarantees sub-second host JVM execution for all domain tests and prevents coupling domain logic to OS release cycles.

# Engineering Standards & Guardrails: Threshold

## 1. Jetpack Compose Guidelines

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

---

## 2. Memory & Performance Rules

1. **Zero-Bitmap Memory Baseline:**
   - Do NOT store decoded `android.graphics.Bitmap` instances in persistent state or long-lived caches.
   - If icons are rendered in glyph mode, use vector glyphs or monochromatic masks.
   - Total PSS must stay strictly **under 30 MB** (typographic baseline < 25 MB).
2. **Zero-Allocation Fuzzy Search (`:core`):**
   - The fuzzy search matcher must operate using in-place character index matching, avoiding object creation (e.g. intermediate substring lists or boxing) during keystroke search loops.
3. **IME Handling:**
   - Dismiss the software keyboard cleanly via `WindowInsetsController` prior to triggering `startActivity()`. This eliminates keyboard jitter and redraw delays during app transition.

---

## 3. Pure Kotlin Core Constraint

- **Module:** `:core`
- **Rule:** Never import anything from `android.*`.
- **Reason:** Guarantees sub-second host JVM execution for all domain tests and prevents coupling domain logic to OS release cycles.

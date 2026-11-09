---
name: compose-m3-ui
description: >-
  Use this skill whenever designing, building, refactoring, or auditing Jetpack Compose UI
  screens, components, themes, or navigation flows in Threshold. Enforces Material 3 (M3)
  best practices, Jetpack Navigation 3 (NavDisplay/NavKey), edge-to-edge WindowInsets,
  semantic tokens, spatial hierarchy over pop-up dialogs, and anti-"AI-slop" layout standards.
---

# Threshold Compose UI & Material 3 Specialist (`compose-m3-ui`)

Threshold treats the smartphone like a **precision mechanical instrument** rather than an entertainment console. However, "minimalist and monochrome" does **not** mean hand-rolling raw `Box`/`Row`/`Column` primitives with arbitrary borders, ASCII symbols (`"▲"`, `"▼"`, `"→"`), or chaining modal `Dialog`s.

Every screen in Threshold must combine **Google's Material 3 (M3) & Navigation 3 architectural best practices** with **Threshold's monochrome sensory contract**.

---

## 1. Anti-"AI UI" Design Philosophy

AI-generated UIs share recognizable anti-patterns. **Strictly avoid all of the following:**

1. **No "Box-in-a-Box" Dashboard Syndrome:**
   - Do NOT wrap every group of settings or stats in a bordered card (`.background(Color(0xFF0F0F0F)).border(1.dp, ...)`).
   - Do NOT invent "KPI stat cards" (e.g., side-by-side "Guarded Apps: 3" / "Blocked Apps: 2" summary boxes) on settings screens when the information belongs inline in the navigation row's `supportingContent` (`"3 guarded · 2 blocked"`).
   - Prefer **whitespace, typography hierarchy, and standard M3 `ListItem` alignment** over nested bordered boxes.
2. **No Modal `Dialog` Abuse:**
   - `AlertDialog` is **strictly reserved** for binary destructive confirmations (e.g., *"Delete 'Deep Work' profile?"*) or critical system alerts.
   - **NEVER** put scrollable lists, app pickers, schedule editors, or multi-field creation/editing forms inside a `Dialog`.
   - Use **dedicated Navigation 3 screen destinations** (for primary entity creation/editing like a Profile) or **M3 `ModalBottomSheet`** (for focused contextual pickers like adding a Quick Action or picking a friction override).
3. **No ASCII Art or Text Characters as Icons:**
   - **NEVER** use Unicode characters (`"▲"`, `"▼"`, `"→"`, `"Manage →"`, `"☾"`, `"+"`) as interactive buttons or directional indicators.
   - Always use crisp vector `Icon`s (`ThresholdIcons` / Material Symbols) inside proper M3 icon slots (`leadingContent`, `trailingContent`, `IconButton`).
4. **No Pinned Full-Width "Save" Footer Buttons on Settings Screens:**
   - Settings, toggles, and app assignment changes should either **persist reactively** on toggle/selection (Single Source of Truth in DataStore/Room via ViewModel) or use a top-bar action in an explicit creation flow—never a giant 52dp white rectangle pinned to the bottom of a browse/list screen.
5. **No File-Private Hardcoded Hex Colors or Ad-Hoc Font Sizes:**
   - **NEVER** declare `private val CardBackground = Color(0xFF121212)` or inline `Color(0xFF888888)` / `fontSize = 11.sp` in screen files.
   - Every color must come from `MaterialTheme.colorScheme` semantic roles (`background`, `surface`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `onSurface`, `onSurfaceVariant`, `outline`, `outlineVariant`).
   - Every text style must come from `MaterialTheme.typography`.

---

## 2. Navigation Architecture: Jetpack Navigation 3

All multi-screen flows in `:app` MUST use **Jetpack Navigation 3 (`androidx.navigation3`)**.
Read [references/navigation-3.md](references/navigation-3.md) when adding routes, transitions, or sheet scenes.

1. **User-Owned Type-Safe Back Stack:**
   - Define routes as `@Serializable sealed interface ThresholdRoute : NavKey`.
   - Hold navigation state via `rememberNavBackStack(ThresholdRoute.Home)`.
   - Never use `enum class` screen switching (`when (currentScreen)`) or loose `var editingProfileId: String?` state variables in `MainActivity`. Pass IDs inside the type-safe `NavKey` data class (e.g., `ThresholdRoute.ProfileDetail(profileId: String?)`).
2. **`NavDisplay` & Entry Decorators:**
   - Render the back stack with `NavDisplay`, wired with `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`.
   - Use `entryProvider { entry<Route> { ... } }` DSL.
   - Apply Threshold's optical transitions (`ThresholdMotion.opticalTween`, 80ms–140ms, zero spring physics) via `transitionSpec`, `popTransitionSpec`, and `predictivePopTransitionSpec`.
3. **Home Launcher Root Back Contract:**
   - On `ThresholdRoute.Home`, back navigation dismisses active friction or active search; when at rest on the ambient home canvas, back is consumed as a no-op so the root launcher activity never finishes.
   - On all child routes (`backStack.size > 1`), `onBack = { if (backStack.size > 1) backStack.removeLastOrNull() }` pops the back stack with predictive back support.

---

## 3. Material 3 Component Selection Matrix

Always select the canonical Material 3 component before building a custom layout.
Read [references/m3-components-and-tokens.md](references/m3-components-and-tokens.md) for code templates.

| UI Pattern | Required M3 Component | Notes |
| :--- | :--- | :--- |
| **Screen Frame & App Bar** | `Scaffold` + `TopAppBar` / `LargeTopAppBar` | Connect `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` or `pinnedScrollBehavior()` via `Modifier.nestedScroll(...)`. |
| **Settings / Preference Row** | `ListItem` | Use `headlineContent`, `supportingContent`, `leadingContent`, and `trailingContent` (`Switch`, `Icon`, value label). |
| **Section Header** | `Text` (`labelMedium`, `onSurfaceVariant`) | Placed with `padding(start = 16.dp, top = 24.dp, bottom = 8.dp)`—no box around the section unless using M3 Expressive grouped containers (`surfaceContainerLow`). |
| **Horizontal Category Filter** | `LazyRow` + `FilterChip` | Use M3 `FilterChip` with `FilterChipDefaults` mapped to monochrome tokens. |
| **Single-Choice Inline Selector** | `SingleChoiceSegmentedButtonRow` or `ThresholdMultiStateToggle` | Use M3 `SegmentedButton` for standard 2–4 option choices; `ThresholdMultiStateToggle` is permitted for the tactile 3-state app gate (`ALLOW` / `GUARD` / `BLOCK`) when sized to `48.dp` min touch target height. |
| **Time Window Picker** | M3 `TimePicker` or `TimeInput` (in `TimePickerDialog` / `ModalBottomSheet`) | Never use custom `"▲"` / `"▼"` text steppers for hours/minutes. |
| **Day-of-Week Selector** | `MultiChoiceSegmentedButtonRow` or M3 `FilterChip` row | Minimum `48.dp` touch target height with proper `Role.Checkbox` semantics. |
| **Contextual Picker / Secondary Editor** | `ModalBottomSheet` | Use for picking Quick Action apps or configuring a single app's friction override. |
| **Destructive Confirmation** | `AlertDialog` | Only for confirming deletion or irreversible actions. |

---

## 4. Layout, Spacing, Borders & Edge-to-Edge Conventions

Read [references/edge-to-edge-and-insets.md](references/edge-to-edge-and-insets.md) for `WindowInsets` rules.

1. **Edge-to-Edge & Insets:**
   - Every screen with a top bar or scrollable list MUST use `Scaffold`.
   - Pass `Scaffold`'s `innerPadding` directly to `LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding))`.
   - **NEVER** apply `.windowInsetsPadding(WindowInsets.safeDrawing)` on the root `Column` of a screen—that prevents the `TopAppBar` and list content from drawing edge-to-edge behind system bars.
2. **Spatial Grid (4dp / 8dp):**
   - Horizontal screen keylines: `16.dp` on compact phones (Settings, Lists), `24.dp` on ambient canvas surfaces (`ThresholdHomeCanvas`, Friction checkpoints).
   - List item vertical rhythm: handled automatically by M3 `ListItem` (`56.dp` 1-line, `72.dp` 2-line, `88.dp` 3-line).
   - Minimum interactive touch target: **`48.dp × 48.dp`** on every tappable element (`IconButton`, day-of-week toggles, reorder handles, segmented controls).
3. **Borders & Dividers (The 1dp Rule):**
   - Use `HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)` sparingly: between major logical groups or as the mechanical shutter anchor on the home/search canvas.
   - Do not put `HorizontalDivider` between every single `ListItem` if whitespace already separates them cleanly.

---

## 5. Architecture & Verification Protocol

1. **State Hoisting & Stateless Screens:**
   - Split every destination into:
     - `XyzRoute(viewModel, onNavigate...)`: Collects `uiState` via `collectAsStateWithLifecycle()`.
     - `XyzScreen(uiState, onEvent..., modifier)`: Pure, stateless `@Composable` that takes immutable state and lambdas.
2. **Previews & Roborazzi Goldens:**
   - Every stateless `Screen` composable must be testable in [`ThresholdScreenshotTest.kt`](file:///home/garan/threshold/app/src/test/kotlin/com/garan/threshold/ThresholdScreenshotTest.kt).
   - After modifying any UI, run `./gradlew testDebugUnitTest` (and `./gradlew recordRoborazziDebug` when intentionally updating visual goldens) and inspect the generated PNGs in `app/src/test/snapshots/` using `read_file` to visually verify layout, alignment, contrast, and spacing.

---

## 6. Step-by-Step UI Audit Checklist

When asked to review or audit an existing or new screen, evaluate it against these 8 criteria and report findings by severity (**P0 Structural/UX Flow**, **P1 Component & Token Misuse**, **P2 Layout/Polish/Accessibility**):

1. **Navigation & Flow (`Nav3`):** Is the screen a proper `NavKey` destination in `NavDisplay`? Are sub-flows using proper destinations or `ModalBottomSheet`s instead of chained `Dialog`s?
2. **Dialog Discipline:** Are there any `Dialog` / `AlertDialog` composables being used for forms, pickers, or complex editors instead of binary confirmations?
3. **M3 Component Fidelity:** Does the screen use `Scaffold`, `TopAppBar`, `ListItem`, `FilterChip`, `SegmentedButton`, and `TimePicker` rather than hand-rolled `Row`/`Box` approximations?
4. **Zero ASCII Icons:** Are there any text glyphs (`"▲"`, `"▼"`, `"→"`, `"☾"`) masquerading as icons?
5. **Color & Typography Tokens:** Are there any hardcoded `Color(0x...)` values or inline `fontSize = ...sp` overrides in the screen file?
6. **Box-in-a-Box / Visual Clutter:** Are there unnecessary nested bordered boxes, redundant dividers, or "dashboard KPI cards" that should be collapsed into clean M3 `ListItem`s?
7. **Edge-to-Edge & Insets:** Does `Scaffold` pass `innerPadding` into `LazyColumn(contentPadding = innerPadding)` without clipping the screen via root `safeDrawingPadding`?
8. **Touch Targets & Accessibility:** Is every interactive element at least `48.dp × 48.dp` with proper `contentDescription` / semantics (`Role.Switch`, `Role.Tab`, `Role.Checkbox`)?

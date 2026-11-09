# Edge-to-Edge & `WindowInsets` Reference for Threshold

Synthesized from Google's official `android/skills` (`system/edge-to-edge`) for Android 15/16+ (SDK 35+).

---

## 1. Core Edge-to-Edge Rules

1. **Activity Setup:**
   - Call `enableEdgeToEdge()` before `setContent` in `MainActivity.onCreate()`.
   - Ensure `android:windowSoftInputMode="adjustResize"` is set on `MainActivity` in `AndroidManifest.xml`.

2. **Single Point of Inset Consumption (No Double Padding, No Root Clipping):**
   - **PREFERRED (`Scaffold` screens):** Let `Scaffold` and `TopAppBar` handle top/bottom system bar insets automatically. Pass `innerPadding` to the scrolling container (`LazyColumn` / `Column`):
     ```kotlin
     Scaffold(
         topBar = { TopAppBar(...) }
     ) { innerPadding ->
         LazyColumn(
             modifier = Modifier
                 .fillMaxSize()
                 .consumeWindowInsets(innerPadding),
             contentPadding = innerPadding,
         ) { ... }
     }
     ```
   - **FORBIDDEN on `Scaffold` screens:** Do NOT apply `.windowInsetsPadding(WindowInsets.safeDrawing)` or `.safeDrawingPadding()` on a root `Column` wrapping a top bar and list. Doing so clips the scrolling list before the navigation bar and prevents `TopAppBar` from drawing its container color behind the status bar.
   - **Permitted ONLY on Chrome-less Full-Screen Canvas Surfaces:** `ThresholdHomeCanvas`, `DragSortSurface`, `CaretTracingSurface`, and `TypedIntentionSurface` have no `TopAppBar` or `Scaffold`. On those full-screen ambient canvases, apply `.safeDrawingPadding()` (and `.imePadding()` where the keyboard is active) inside the `.background(MaterialTheme.colorScheme.background)` modifier so the black background fills the entire display edge-to-edge while keeping text/canvas elements inside the safe drawing area.

---

## 2. IME (Software Keyboard) Inset Handling

1. **Search Surface & Typed Intention Surface:**
   - Always place `.background(MaterialTheme.colorScheme.background)` **before** any `.safeDrawingPadding()` or `.imePadding()` modifier so the background never shrinks or exposes a raw window frame when the keyboard animates.
   - When using a `LazyColumn` above an open keyboard, ensure `contentPadding` or `imePadding()` allows the user to scroll to the bottom result while the keyboard is visible.
2. **Pre-Launch IME Dismissal Invariant:**
   - Per `.agent/STANDARDS.md`, always dismiss the software keyboard cleanly via `ImeUtils.dismissIme(activity)` / `WindowInsetsController` before invoking `startActivity()` to prevent window redraw jitter during app launch transitions.

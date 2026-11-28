# Current Task

- **Issue:** [#220](https://github.com/garanj/drytoast/issues/220) — `fix(accessibility): enforce 48dp touch targets on compact controls and capture tablet/foldable Play assets`
- **Branch:** `fix/220-touch-targets-snackbar-tablet-assets`
- **Status:** Ready for Verification & PR

## Checklist
- [x] TDD Red: Add `AccessibilityTouchTargetAndSnackbarTest` verifying `>= 48dp × 48dp` non-overlapping touch targets across `ProfilesScreen` and `ConfigureAppsScreen`, zero text overflow/overlap at `360x800dp` + `FontScale(1.5f)`, and pane-scoped `SnackbarHost` confirmations (`black_wallpaper_applied`, `link_copied`) with zero system `Toast`.
- [x] TDD Green: Enforce `>= 48dp × 48dp` touch bounds on `ProfileCard` reorder buttons (preserving `18dp` icons) and `DryToastMultiStateToggle`/`DryToastProfileOverrideToggle` (preserving `32dp` visual pill frame), ensure `360x800dp @ 1.5x` font-scale resilience, route in-Settings confirmations through `SnackbarHost`, update affected snapshots, and capture 7" tablet, 10" tablet, and unfolded foldable Play Store screenshots under `docs/play-store/`.
- [x] Verify `./gradlew spotlessCheck --no-daemon`, `./gradlew test`, `:app:compileReleaseKotlin`, zero `android.*` imports in `:core`, and green GitHub Actions CI.

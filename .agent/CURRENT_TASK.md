# Current Task

- **Issue:** [#219](https://github.com/garanj/drytoast/issues/219) — `feat(settings): add adaptive 2-pane Settings master-detail and 2-column profile grid on expanded screens`
- **Branch:** `feat/219-adaptive-two-pane-settings-grid`
- **Status:** Ready for Verification & PR

## Checklist
- [x] TDD Red: Add `AdaptiveSettingsLayoutTest` covering `COMPACT` (`411x891dp`), `MEDIUM` (`673x841dp` & `768x1024dp` centered `<= 720dp`), `EXPANDED` (`1280x800dp` 2-pane master–detail + non-duplicating detail switching + Back), and `>= 720dp` 2-column `Profiles`/`Triggers` staggered grid.
- [x] TDD Green: Implement `AdaptiveSettingsLayout.kt` (`SettingsTwoPaneSceneStrategy`, `SettingsContentFrame`, `navigateFromSettingsMaster`, `exitSettingsFromMaster`), extract `ProfileCards.kt` and add 2-column `LazyVerticalStaggeredGrid` to `ProfilesScreen`, wire `MainActivity` `NavDisplay`, record `configure_apps_foldable_unfolded.png` and `settings_master_detail_tablet_expanded.png` Roborazzi goldens, and add `DryToastMasterDetailInstrumentedTest` + `2560x1600` PSS verification in `scripts/test-emulator-smoke.sh`.
- [x] Verify `./gradlew spotlessCheck --no-daemon`, `./gradlew test`, `:app:compileReleaseKotlin`, zero `android.*` imports in `:core`, and green GitHub Actions CI.

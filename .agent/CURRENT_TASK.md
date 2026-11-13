# Current Task: Issue #84 — Increase Location Cache Freshness Window to 2m & Eliminate Double Registration

- **Issue:** [#84](https://github.com/garanj/threshold/issues/84)
- **Branch:** `fix/84-location-cache-and-single-registration`
- **Status:** Ready for Review

## Checklist
- [x] `:data` (TDD Red): Update `AndroidLocationSourceTest` to assert that a 60s-old fix (minute tick) is a cache hit and >120s-old fix triggers a single active refresh
- [x] `:data` (TDD Green): Update `STALE_FIX_THRESHOLD_MS = 120_000L` and use a single `requestLocationUpdates` registration in `AndroidLocationSource.kt`
- [x] Verify all unit tests and `spotlessCheck` pass

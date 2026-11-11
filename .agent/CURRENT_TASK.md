# Active Task: Issue #62 — Geo Profiles with Zero-Bloat Location Editor & Hysteresis

- **Issue:** [#62](https://github.com/garanj/threshold/issues/62)
- **Branch:** `feat/62-geo-profiles`
- **Status:** Ready for PR Review

## Checklist
- [x] **1. `:core` Domain Model, Math, Coordinate Parser & Unit Tests (TDD):**
  - `LocationFix(latitude, longitude)` with Haversine `distanceMetersTo`.
  - `GeoTrigger(latitude, longitude, radiusMeters, mode)` with `GeoTriggerMode.INSIDE` / `OUTSIDE`, preset radii (`100`, `250`, `500`, `1000`), and `+25m` hysteresis exit buffer (`HYSTERESIS_BUFFER_METERS = 25.0`).
  - `GeoCoordinateParser` supporting decimal `lat, lng`, `geo:lat,lng`, and DMS coordinates.
  - `ProfileTrigger.Geo(val geo: GeoTrigger)` and `LocationSource` port in `:core`.
  - Updated `CustomProfile.isActive` and `CascadingResolutionEngine` to accept `currentLocation: LocationFix? = null` and `wasPreviouslyActive: Boolean = false` / `previouslyActiveProfileIds: Set<String> = emptySet()`.
- [x] **2. `:data` Location Adapter, Capability Checker, Serialization & Robolectric Tests:**
  - `AndroidLocationSource` backed by platform `LocationManager` (`FUSED_PROVIDER`, `GPS_PROVIDER`, `NETWORK_PROVIDER`).
  - Updated `AndroidSettingsRepository` JSON serialization to persist `ProfileTrigger.Geo`.
  - Updated `AndroidProfileCapabilityChecker` for Geo location capability (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` + `isLocationEnabled`).
- [x] **3. `:app` UI, Editor Sheet, Capability Fault Sheet & Robolectric / Screenshot Tests:**
  - Added `ThresholdIcons.LocationOn`, `ThresholdIcons.MyLocation`, and `ThresholdIcons.OpenInNew` vector icons.
  - Updated `ScheduleEditorDialog.kt` (`ProfileEditorSheet`) with `[ Schedule | Geo ]` `SingleChoiceSegmentedButtonRow` on creation and dedicated `GeoEditorFields` when creating/editing Geo profiles.
  - Updated `ProfilesScreen.kt` to render `ThresholdIcons.LocationOn`, live distance/status summary, and `"Location"` action button on Geo profile cards.
  - Wired `LocationSource` into `SearchViewModel` and `MainActivity` (`onResume` refresh + inline permission request + capability fault action).
- [x] **4. Real-Device Instrumented Test (`ThresholdGeoProfileInstrumentedTest`):**
  - Mock GPS location via `LocationManager.addTestProvider` + `setTestProviderLocation` and verify inside, hysteresis buffer, and outside transitions.

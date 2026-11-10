# Current Task: Profile Card Type Icon & Delete Confirmation Dialog (#60)

## Active Issue
- **Issue**: `#60` - feat(profiles): add profile type icon on card and Material 3 delete confirmation dialog
- **Branch**: `feat/60-profile-card-icon-and-delete-confirmation`

## Checklist
- [x] **Profile Type Icon in `ProfileCard` (`ProfilesScreen.kt`)**:
  - [x] Display `ThresholdIcons.Schedule` clock icon in the leading area of `ProfileCard` to represent the profile's trigger type (and only show up/down reorder arrows when `totalProfiles > 1`).
- [x] **Delete Confirmation `AlertDialog` (`ProfilesScreen.kt`)**:
  - [x] Tapping the Delete icon sets `profilePendingDeletion`, presenting a Material 3 `AlertDialog` ("Delete profile?") with "Cancel" and "Delete" buttons.
- [x] **Tests & Snapshots (`ProfilesScreenTest.kt`, `profiles_screen.png`)**:
  - [x] Update `ProfilesScreenTest` to verify the delete confirmation dialog ("Cancel" aborts, "Delete" confirms) and schedule icon presence.
  - [x] Record updated Roborazzi screenshot for `profiles_screen.png` and verify all tests pass.

# Changelog

## 1.1.6

- Stabilized full-screen navigation lifecycle on iOS and Android.
- Hardened session conflict handling to prevent overlapping navigation sessions.
- Added consistent close/stop behavior for native end-navigation controls.
- Improved custom native bottom sheet gesture behavior and right-side safe-zone handling.
- Added configurable right exclusion width for reveal hot-zone:
  - `bottomSheet.revealGestureRightExclusionWidth`
- Added quick-action style customization fields:
  - `quickActionBackgroundColor`
  - `quickActionTextColor`
  - `quickActionSecondaryBackgroundColor`
  - `quickActionSecondaryTextColor`
  - `quickActionGhostTextColor`
  - `quickActionBorderColor`
  - `quickActionBorderWidth`
  - `quickActionCornerRadius`
- Added/standardized rounded-corner handling for action and quick-action buttons.
- Added extended full-screen custom-native typography and badge styling controls:
  - `primaryTextFontFamily`, `primaryTextFontWeight`
  - `secondaryTextFontFamily`, `secondaryTextFontWeight`
  - `actionButtonFontFamily`, `actionButtonFontWeight`
  - `quickActionFontFamily`, `quickActionFontWeight`
  - `headerTitleFontSize`, `headerTitleFontFamily`, `headerTitleFontWeight`
  - `headerSubtitleFontSize`, `headerSubtitleFontFamily`, `headerSubtitleFontWeight`
  - `headerBadgeFontSize`, `headerBadgeFontFamily`, `headerBadgeFontWeight`
  - `headerBadgeCornerRadius`, `headerBadgeBorderColor`, `headerBadgeBorderWidth`
- Updated example app to full-screen-only test flow.
- Re-enabled embedded runtime usage (`MapboxNavigationView`) with explicit opt-in gate (`enabled`) to reduce session-conflict loops.
- Updated documentation (`README`, `USAGE`, `QUICKSTART`, troubleshooting notes) to match `1.1.6` behavior and API surface.

## 1.1.0

- Added iOS current-location fallback for full-screen `startNavigation` when `startOrigin` is omitted.
- Improved release readiness with `npm run verify` script (`tsc`, Android module compile, package dry-run).
- Added lightweight GitHub Actions CI workflow for package checks.
- Expanded docs with richer usage and troubleshooting guides.
- Tightened package publish surface to keep tarballs clean and focused.

## 1.0.0

- Added Android/iOS navigation module API with route, camera, style, and simulation options.
- Added event listeners for location, route progress, banner instructions, arrival, cancel, and errors.
- Added Android config plugin automation for repository/token wiring and required permissions.
- Added iOS config plugin defaults for location usage strings and background modes.
- Added runtime JS validation for coordinates in `startNavigation`.
- Added updated package docs (`README.md`, `QUICKSTART.md`) for developer onboarding.

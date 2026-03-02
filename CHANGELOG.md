# Changelog

## 2.0.0

- Breaking: package is now embedded-only on both iOS and Android.
- Removed full-screen API surface: `startNavigation`, `isNavigating`.
- Added embedded-session `stopNavigation()` API to stop active embedded navigation programmatically.
- Removed Android full-screen activity implementation and manifest entry.
- Simplified iOS module bridge to embedded-only runtime + `MapboxNavigationView` props.
- Updated JS/types/docs/examples for embedded-only usage and overlay custom sheet flow.
- Android embedded: switched route preview startup to Drop-In route preview pipeline with route options interceptor.
- Android embedded: adjusted session-conflict messages to embedded-only wording.
- Overlay custom sheet: fixed state handling (`collapsed` height now distinct from `expanded`) and changed default right exclusion to `0` to avoid blocking native right-side controls.


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

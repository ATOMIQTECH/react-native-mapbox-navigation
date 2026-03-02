# Changelog

## 2.0.0

- Breaking: package is now embedded-only on both iOS and Android.
- Removed full-screen API surface: `startNavigation`, `stopNavigation`, `isNavigating`.
- Removed Android full-screen activity implementation and manifest entry.
- Simplified iOS module bridge to embedded-only runtime + `MapboxNavigationView` props.
- Updated JS/types/docs/examples for embedded-only usage and overlay custom sheet flow.
- Android embedded: switched route preview startup to Drop-In route preview pipeline with route options interceptor.
- Android embedded: adjusted session-conflict messages to embedded-only wording.
- Overlay custom sheet: fixed state handling (`collapsed` height now distinct from `expanded`) and changed default right exclusion to `0` to avoid blocking native right-side controls.

## 1.1.6

- Android embedded: switch to Mapbox’s official Drop-In `NavigationView` UI (route preview panel + Start button + turn-by-turn UI) to match the SDK look/feel (and iOS embedded behavior).
- Android embedded: explicitly attach `ViewTree` lifecycle / ViewModel / saved-state owners from the host Activity so Drop-In state and buttons work reliably inside React Native view hierarchies.

- Android embedded: render the route preview line on the embedded `MapView` and set an initial route overview camera when routes are ready (so embedded mode is no longer “just a plain map”).
- Android embedded: enable the Mapbox location component and feed it the enhanced navigation location via `NavigationLocationProvider` so the user location puck reliably appears/moves.
- Android embedded: add a modern native preview overlay (From/To + Overview + Start) and start trip session only when the user taps Start.
- Android embedded: implement route simulation using `MapboxReplayer` when `shouldSimulateRoute` is enabled, so turn-by-turn guidance progresses without physical movement.
- Android embedded: opt into Mapbox preview APIs used for route preview / replay wiring (`ExperimentalPreviewMapboxNavigationAPI`).

- Android embedded: schedule a few bounded “catch-up” measure/layout passes after adding `MapView` so the internal `TextureView` becomes available without reintroducing continuous refresh loops.

- Android embedded: stop continuous “refreshing” by avoiding redundant forced measure/layout passes and preventing repeated style reloads.

- Android embedded: explicitly measure the native map layer and `MapView` to fix `TextureView` staying at width `0` / `isAvailable=false` (black map with only UI overlays like the scale bar visible).

- Android embedded: prime the `TextureView` render surface (force opaque/alpha) and disable translucent texture surfaces when available to fix “scale bar visible but map blank / ghosted” reports.

- Android embedded: set Mapbox Maps access token via reflection (when available) and add style-load diagnostics to fix cases where routes load but the map stays blank.

- Android embedded: explicitly lay out the native map layer and `MapView` to match host bounds (fixes `MapView` width staying `0` even when host is measured).

- Android embedded: force measure/layout cycles (`requestLayout` workaround) and add host-size layout logs to fix `MapView` staying at `0x0` in some React Native/Fabric hierarchies.

- Android: fix compilation in apps whose dependency graph does not expose `MapboxOptions`, `NavigationOptions.Builder`, or `MapInitOptions.Builder` by using reflection for those optional APIs in embedded mode.

- Android embedded: keep a dedicated native map layer under React children (avoids removing React-managed overlays and improves mount stability).
- Android embedded: stabilize access-token usage for embedded map rendering (relies on `mapbox_access_token` Android string resource injected by the config plugin).
- Android embedded: improve `startIfReady` logging, create `MapboxNavigationProvider` with version-tolerant `NavigationOptions` reflection, and add TextureView/SurfaceView composition diagnostics.

- Android: emit `onCancelNavigation` for user-dismissed full-screen navigation sessions.
- Android: embedded view now emits `onCancelNavigation` when guidance returns to free drive.
- Android: `getNavigationSettings()` now returns live `mute`, `voiceVolume`, `language`, `distanceUnit`, and session state.
- Android embedded: improved mount reliability by waiting briefly for a host Activity, invoking best-effort NavigationView lifecycle hooks, and retrying destination preview until the view is ready.

- Android: fix Kotlin compilation issue by using explicit `this@MapboxNavigationModule` in AsyncFunction lambdas.

- Android embedded: invoke best-effort `NavigationView` lifecycle hooks (`onCreate/onResume/onPause`) to improve rendering/initialization when hosted inside a React Native view hierarchy.

- Android embedded: improve TextureView enforcement for Mapbox `MapView` injection and fall back to `SurfaceView` Z-order workarounds for React Native rendering issues.

- Android embedded: add stronger MapView TextureView creation heuristics, token diagnostics, and automatic MapView re-injection when SurfaceView is detected.

- Android embedded: brute-force MapInitOptions constructors (when needed) to obtain a TextureView-backed MapView for reliable rendering inside React Native.

- Android embedded: support older Mapbox Maps SDKs by enabling TextureView via `MapInitOptions(Context)` setter methods when `MapInitOptions.Builder` is unavailable, and expand constructor brute-force coverage.

- Android embedded: replace Drop-In `NavigationView` implementation with a direct `MapView` + `MapboxNavigation` integration to avoid SurfaceView rendering issues in React Native hierarchies.

- Android embedded: add Mapbox Maps SDK dependency and fix `NavigationRouterCallback` signatures for direct MapView-based embedded navigation.

- Android embedded: fix compilation by using `MapView.getMapboxMap()` (instead of Kotlin property access) and make MapView lifecycle calls best-effort via reflection.

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

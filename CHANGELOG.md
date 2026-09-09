# Changelog

## 2.1.1

### Fixed

- **Android Gradle Plugin 9 compatibility.** AGP 9 ships built-in Kotlin support
  and registers the `kotlin` extension itself, so the unconditional
  `apply plugin: 'kotlin-android'` failed configuration with "Cannot add
  extension with name 'kotlin'" and the library could not be built at all on an
  AGP 9 project. The plugin is now applied only when nothing has already
  registered that extension, which also covers AGP 10 where the
  `android.builtInKotlin` opt-out is removed.

  Thanks to [@gabrieldonadel](https://github.com/gabrieldonadel) for the fix
  ([#21](https://github.com/ATOMIQTECH/react-native-mapbox-navigation/pull/21)).

- **Android did not compile in 2.1.0.** Four regressions introduced by that
  release, all caught by the new Android CI job on its first real compile:
  - A mechanical rename turned the `NavigationViewListener` overrides
    `onDestinationChanged` / `onDestinationPreview` into non-overrides, so those
    events could never fire.
  - `Events()` takes a vararg, not a `List`.
  - The event wrappers accepted `Map<String, Any?>` while `EventDispatcher`
    requires `Map<String, Any>`; null entries are now dropped, so an optional
    field arrives as a missing key rather than an explicit null.
  - `MapView.getMapboxMap()` is a Kotlin function, so it cannot be reached as a
    synthesized `.mapboxMap` property.

  Anyone who installed 2.1.0 could not build for Android. Upgrade to 2.1.1.

- Followed that with the second AGP 9 blocker in the same file: AGP 9's built-in
  Kotlin does not provide `android { kotlinOptions { } }`, so the Kotlin target
  configuration failed once the plugin was no longer applied. Replaced with the
  Kotlin extension's `compilerOptions`, which resolves on both paths — KGP's
  extension on AGP 8 and AGP's built-in one on AGP 9.

## 2.1.0

### Added

- **Custom navigation pointer (`locationPuck`)** — replace the standard location
  puck with a glTF/GLB 3D model, custom 2D images, a recolored default puck, or
  hide it entirely. Model and image sources accept a React Native `require()`,
  a remote `https://` URL, or a native asset path; remote assets are downloaded
  once and cached on disk. Pointers can vary per navigation state
  (`freeDrive`, `destinationPreview`, `routePreview`, `activeNavigation`,
  `arrival`, `idle`), backed by Mapbox's native `LocationPuckOptions` on Android
  and by `NavigationMapView.userLocationStyle` on iOS.
- Expo config plugin now accepts options: `backgroundLocation`,
  `backgroundAudio`, and `locationWhenInUsePermission`.
- **Multi-stop waypoint arrivals** — new `onWaypointArrive` event and
  `addWaypointArriveListener`. `ArrivalEvent` now carries `index`,
  `isFinalDestination` and `remainingWaypoints`.
- **Off-route / rerouting events** — new `onOffRoute` event and
  `addOffRouteListener`, backed by `OffRouteObserver` on Android and
  `willRerouteFrom` on iOS. A failed reroute now surfaces as an `onError` with
  code `REROUTE_FAILED`.
- **`eventThrottleMs` prop** — throttles the continuous high-frequency events
  natively, before they are serialized or cross the bridge.
- A **SDK Coverage** section in the README documenting exactly what is wrapped
  and what is not.
- **`advanceToNextWaypoint()`** — advance a multi-stop route to the next leg
  from an app event (passenger picked up, parcel delivered) instead of physical
  arrival, without restarting the session. Backed by `navigateNextRouteLeg` on
  Android and `Router.advanceLegIndex` on iOS.
- **`RouteProgress.legIndex`** — the active leg on a multi-waypoint route, so
  leg transitions are observable from `onRouteProgressChange`.

  > `advanceToNextWaypoint()` and `legIndex` were contributed from a downstream
  > `pnpm` patch in the Tujyane mobile app and are now upstreamed.

### Fixed

- **Every `add*Listener` helper was dead on both platforms.** All twelve are
  exported, documented and re-exported from the default export, but nothing ever
  fed the module event emitter: Android had a `MapboxNavigationEventBridge` that
  no code called, and iOS sent no module events at all. The views only
  dispatched *view* events (the `onX` props). Both platforms now relay through
  the bridge, gated on real subscriptions via `OnStartObserving` /
  `OnStopObserving` so unsubscribed events cost nothing.
- **Intermediate waypoint arrivals were broken on both platforms.** Android's
  `onWaypointArrival` and `onNextRouteLegStart` were `= Unit`, so multi-stop
  arrivals were dropped entirely. iOS emitted a plain `onArrive` for *every*
  leg, so on a multi-stop route the end-of-route rating modal fired at the first
  stop instead of the destination.
- **iOS: `setMuted`, `setVoiceVolume` and `setDistanceUnit` were silent no-ops.**
  They only assigned a private field and never reached the SDK, so muting voice
  guidance from JS did nothing on iOS. They now apply through
  `NavigationSettings.shared`.
- **`getNavigationSettings().isNavigating` always returned `false`** on both
  platforms. It now reflects the real embedded session state.
- **iOS: `showsContinuousAlternatives`, `usesNightStyleWhileInTunnel`,
  `routeLineTracksTraversal` and `annotatesIntersectionsAlongRoute` were dropped
  with an "unsupported" warning.** All four are first-class
  `NavigationViewController` properties in the v2 SDK and are now applied.
- **iOS: `cameraMode: 'overview'` never entered overview.** The overview branch
  mutated the *following* viewport and then called `follow()`. It now calls
  `NavigationCamera.moveToOverview()`.
- **Android: `cameraPitch` and `cameraZoom` were no-op stubs** (`= Unit`). They
  are now applied to the map camera. Note that the Drop-In camera may override
  them while actively following.
- **iOS: navigation never started when `startOrigin` was omitted**, despite the
  prop being optional and Android already falling back to the device location.
  iOS now resolves the device location, requesting a fix if needed.
- **iOS: `showsReportFeedback: false` was overridden** by the floating-button
  pass, which unconditionally re-enabled the feedback control.
- **iOS: `uiTheme` did not theme the map itself.**
  `overrideUserInterfaceStyle` only themes the UIKit chrome; the map's day/night
  tiles are driven by `StyleManager`, which auto-switches by time of day — so a
  dark app showed a light map in daylight. `uiTheme` now drives `StyleManager`
  directly. (Also from the downstream Tujyane patch.)
- Overlay sheet distance ignored `distanceUnit` and always rendered raw metres
  (e.g. `12480 m`). It now formats as m/km or ft/mi.
- Importing the package no longer throws when the native module is missing
  (web, Jest, Expo Go). The module is resolved lazily with an actionable error.
- The published npm tarball listed iOS sources file-by-file, so newly added
  Swift files were silently omitted. It now globs `ios/*.swift`.

### Changed

- **`ACCESS_BACKGROUND_LOCATION` is no longer added automatically** on Android.
  It triggers a Google Play policy review and prominent-disclosure requirement.
  Opt in with the plugin's `backgroundLocation: true` option.
- `androidActionButtons` is marked `@deprecated` — it is ignored on both
  platforms and always has been. Type docs across the props now state actual
  per-platform support instead of implying parity.
- The config plugin reports its real package version, and warns instead of
  silently doing nothing when it cannot patch `build.gradle`.

### Performance

- Continuous events (`onLocationChange`, `onRouteProgressChange`,
  `onJourneyDataChange`) can now be throttled natively with `eventThrottleMs`,
  and module-level events are skipped entirely when nothing is subscribed.
- Android: the off-route observer is unregistered on teardown along with the
  others, rather than leaking across sessions.
- iOS: the banner/UI visibility pass walked the entire `UIView` tree on **every**
  location update during guidance. It is now throttled.
- iOS: an `ISO8601DateFormatter` was allocated per location update for the
  journey payload; it is now shared.
- JS: `PanResponder.create()` ran on every render of the overlay sheet,
  allocating two new gesture recognizers each time. The responders are now
  created once.

## 2.0.2

- Added camera-follow APIs on both platforms:
  - `resumeCameraFollowing()` function.
  - `onCameraFollowingStateChange` event/callback.
  - `getNavigationSettings()` now returns `isCameraFollowing` and `isCameraNotFollowing`.
- Updated embedded example to show:
  - camera-follow state handling + resume action

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

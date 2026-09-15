# Changelog

## 3.0.0

Migrates both platforms to **Mapbox Navigation SDK v3** (iOS 3.30.1, Android
3.30.1) and Maps SDK 10.19.0 → 11.30.1.

The headline reason is not the new features: pinning Maps 10.x forced every app
that also uses `@rnmapbox/maps` onto a deprecated map stack, because that package
needs Maps v11. Sharing a major with it removes the conflict.

### Breaking

- **Maps SDK 11 is now required** (was 10.19.0). If you pinned Mapbox Maps
  versions to work around the old conflict, remove those pins.
- **iOS is now integrated via Swift Package Manager.** Mapbox publishes no
  CocoaPods artifacts for Navigation v3, so the config plugin injects the SDK as
  Swift packages from a `post_install` hook. Run `pod install` after upgrading.
  Nothing changes in your Podfile — the plugin edits it.
- **`onDestinationPreview` no longer fires on either platform.** It mirrored a
  route-preview phase that only ever existed inside Android's Drop-In UI, which
  v3 removed; it never fired on iOS at all. `addDestinationPreviewListener` is
  kept and marked `@deprecated`, so existing subscriptions keep compiling and
  simply never fire. `onDestinationChanged` is **unaffected** — it comes from the
  `setDestination` prop setter, not from Drop-In.
- **`showsTripProgress` and `showsSpeedLimits` have no effect on Android.** v3
  deleted the Drop-In UI, and the standalone `MapboxTripProgressView` /
  `MapboxSpeedInfoView` widgets that replace it render as an unstyled light bar
  pinned to the screen edge, with no theming hooks to blend into a host app.
  Trip progress remains available — and themeable — through this package's own
  `bottomSheet` overlay. Both props are logged once at runtime rather than
  silently ignored. Unchanged on iOS.
- **`showsWayNameLabel`, `showsReportFeedback` and `showsEndOfRouteFeedback`
  have no effect on Android.** v3 ships no widget to host them. Unchanged on
  iOS.
- **Per-state location pucks collapse to one on Android.** `locationPuck` still
  accepts all five states and the prop shape is unchanged, but v3's `locationPuck`
  component takes a single puck, so only the `activeNavigation` / `default` entry
  has a visible effect. Unchanged on iOS.

### Added

- **`colors` — theme the navigation UI.** 24 optional colour overrides covering
  the route line and its casing, traversed and alternative lines, the five
  congestion bands, restricted roads, the on-map turn arrow, the maneuver banner
  (background, sub-background, primary/secondary/distance text, turn icon), the
  floating map buttons, and the iOS trip progress bar. All of them apply live,
  including changes made after mount, and every key works on **both** platforms
  except the three `tripProgress*` ones, which are iOS-only (see below).

  ```tsx
  <MapboxNavigationView
    destination={destination}
    colors={{
      routeLine: '#1E9E5A',
      routeLineCasing: '#14532D',
      maneuverBackground: '#14532D',
      maneuverText: '#FFFFFF',
      maneuverTurnIcon: '#FFFFFF',
      congestionHeavy: '#DC2626',
    }}
  />
  ```

  Fully additive: omitting it keeps every Mapbox default, so no existing app
  changes appearance. Three things worth knowing:

  - Use `#RGB` or `#RRGGBB`. **Eight-digit hex is not portable** — iOS reads it
    as `#RRGGBBAA` and Android as `#AARRGGBB`, so the same string gives
    different colours. Pass an opaque colour instead.
  - `routeLine` also sets the `low` and `unknown` congestion colours, because
    congestion shading is painted over the base line and those two bands cover
    most of a typical route — without it a recoloured route still looks blue.
    Set `congestionLow` / `congestionUnknown` to override them individually.
  - `tripProgressBackground`, `tripProgressText` and `tripProgressIcon` are
    **iOS only**. Android has no trip progress bar to colour: v3's standalone
    widget only lays out correctly inside the Drop-In info panel that v3
    removed. Use the `bottomSheet` overlay there — it is JS, so it takes your
    own styles directly.
  - Treat it as a theme you set rather than a value you toggle. On iOS the
    chrome colours go through `UIAppearance`, which is process-global and has no
    "unset", so *removing* a key leaves its last colour rather than restoring
    the Mapbox default until the app relaunches. Changing a key's value always
    works — pass the full palette and vary values within it.
  - `maneuverTurnIcon` is the one key whose result differs by platform. The turn
    icon is drawn in two tones, and iOS can colour them separately, so only the
    emphasised strokes change and a fork still reads correctly; Android only
    permits a flat tint at runtime, so the whole icon takes the colour.

- **The iOS trip progress bar can now be hidden on its own.** The cancel button
  lives in that bar, so the bar was previously kept up by `showsActionButtons`
  — which meant hiding it also meant giving up every other action button. It is
  now gated on `showCancelButton`, so `showsTripProgress={false}` together with
  `showCancelButton={false}` hides just that bar. If you were passing
  `showCancelButton={false}` *and* `showsTripProgress={false}` and relying on the
  bar staying visible, it will now be hidden.

- **The package no longer crashes Expo Go.** The native view is resolved
  defensively and falls back to a labelled placeholder when the native module is
  absent, so a JS-only runtime renders a message instead of throwing.
- **A missing access token now fails safe.** v3's `ApiConfiguration.default`
  calls `assertionFailure`, which traps in Debug builds. A missing or blank
  token is now reported through `onError` as `MISSING_ACCESS_TOKEN` instead.
- **`npm run verify:parity`** — checks that props, async functions and events
  still agree across iOS, Android and the TypeScript surface. Neither native
  platform can be compile-checked from this repo, so silent one-platform drift
  was the most likely way to break a consumer.

- **`routeProfile` — choose the Directions profile.** `driving-traffic`
  (default, and the only one reachable before 3.0.0), `driving`, `walking` or
  `cycling`. Changing it re-requests the route. Note that only
  `driving-traffic` carries live traffic, so the `congestion*` colours have
  nothing to shade on the other three.

- **`routeExclusions` — route around road classes and specific points.**
  `roadClasses` takes `toll`, `motorway`, `ferry`, `tunnel`, `restricted`,
  `unpaved` and `cashOnlyTolls`; `locations` takes up to 50 coordinates to avoid.

  ```tsx
  <MapboxNavigationView
    destination={destination}
    routeProfile='driving'
    routeExclusions={{ roadClasses: ['toll', 'ferry'] }}
    vehicle={{ maxHeight: 4.2, maxWeight: 18 }}
  />
  ```

- **`vehicle` — route for a vehicle's dimensions.** `maxHeight` and `maxWidth`
  in metres, `maxWeight` in metric tons, so oversized vehicles avoid roads they
  cannot use.

  Both of these are driving-only at the API, and the API *rejects* rather than
  ignores them elsewhere: `max_height` on `walking` or `cycling` answers
  `422 Invalid query param`, and those profiles accept only `ferry` and
  `cashOnlyTolls` for exclusions. Sending them would fail the whole route
  request, so the wrapper drops them on non-driving profiles and tells you
  through `onError` (`VEHICLE_NOT_SUPPORTED_BY_PROFILE`,
  `ROAD_CLASS_NOT_SUPPORTED_BY_PROFILE`,
  `EXCLUDED_LOCATIONS_NOT_SUPPORTED_BY_PROFILE`) instead of letting it break.

- **`mapStyleConfig` — configure a Standard style's basemap.** `lightPreset`
  (`day`/`dusk`/`dawn`/`night`), `show3dObjects`, `showRoadLabels`,
  `showPlaceLabels`, `showPointOfInterestLabels` and `showTransitLabels`.
  Applied live. Styles with no configurable import report
  `MAP_STYLE_CONFIG_UNSUPPORTED` once through `onError` rather than silently
  doing nothing.

  ```tsx
  <MapboxNavigationView
    destination={destination}
    mapStyleUriDay='mapbox://styles/mapbox/standard'
    mapStyleConfig={{ lightPreset: 'dusk', show3dObjects: true }}
  />
  ```

- **Standard / 3D map styles are supported.** Pass
  `mapbox://styles/mapbox/standard` or `standard-satellite` through
  `mapStyleUri`, `mapStyleUriDay` or `mapStyleUriNight` and you get 3D
  buildings, landmarks and lighting with the route line composing correctly:
  both SDKs place the navigation layers in the Standard `middle` slot, so the
  route draws above the basemap but below labels, POIs and extrusions. Verified
  on device.

### Fixed

- **`cameraPitch` and `cameraZoom` now take effect in following mode on
  Android.** They previously wrote to `mapboxMap.setCamera(...)`, which the
  navigation camera overwrote on every frame — the old code logged a warning
  admitting as much. v3's `*PropertyOverride` API is the supported mechanism, so
  they now hold. A fix, but a behaviour change if you were passing them.
- **The Android voice button no longer overlaps the maneuver banner.** It was
  anchored under a fixed top margin; the banner's height varies with the
  instruction. Both are now anchored to the bottom of the view.
- **The Android location puck is visible again.** The navigation camera was
  never told the route had changed, so the viewport had no geometry to frame and
  the puck sat off-screen. The routes observer now drives
  `MapboxNavigationViewportDataSource.onRouteChanged`, which also silences a
  continuous `you didn't call #onRouteChanged` warning.
- **`distanceUnit` now works on Android.** It was recorded on both the view and
  the module and applied nowhere, so Android used the device locale: the same
  app showed "200 m" on iOS and "100 ft" on Android. It now feeds
  `DistanceFormatterOptions` on both the process-wide `NavigationOptions` and
  the maneuver component, which carries its own formatter options and does not
  read the former. Changing it mid-trip is deferred to the next session — the
  formatter is baked into the process-wide options — and logged rather than
  silently dropped.
- **`routeLineTracksTraversal` now works on Android** via
  `MapboxRouteLineApiOptions.vanishingRouteLineEnabled`. Previously accepted and
  ignored; iOS was unaffected.
- **`showsEndOfRouteFeedback` now works on iOS.** It was accepted as a prop and
  never applied, so the end-of-route feedback card showed regardless.
- **The default Android map style is a navigation style again.** v2 inherited
  Mapbox's navigation styles from Drop-In; the first cut of this migration fell
  back to `Style.MAPBOX_STREETS`, quietly changing how every Android consumer's
  map looked. It now defaults to `navigation-day-v1` / `navigation-night-v1`.
- **`mapStyleUri`, `mapStyleUriDay`, `mapStyleUriNight` and `uiTheme` now apply
  on Android after mount, and `uiTheme` now selects between the day and night
  styles.** All four setters previously only refreshed widget visibility, so a
  style change after mount was dropped and `uiTheme` never influenced the map at
  all. Automatic sunrise/sunset switching remains iOS-only.
- **`colors` applied at mount now reaches the iOS route line.** The apply ran
  before the navigation controller was assigned, so it silently returned and
  only post-mount changes worked. Route-line colours also survive a day/night
  style change now, and repaint immediately instead of waiting for the next
  route refresh.
- **Props that are unsupported on Android are logged instead of ignored.**
  `annotatesIntersectionsAlongRoute`, `usesNightStyleWhileInTunnel`,
  `showsContinuousAlternatives` and the `nativeFloatingButtons` flags other than
  the audio-guidance button have no v3 Android counterpart; they now say so once
  at runtime.

## 2.2.0

Supersedes the unpublished 2.1.1. **2.1.0 does not build for Android** and
should not be used.

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

- **React Native 0.86 compatibility.** RN 0.86 removed the `absoluteFillObject`
  export from StyleSheet's types, and `src/index.tsx` used it five times. Since
  `main`/`types` point at raw TSX, consumers compile this source, so on RN 0.86
  that surfaced as an error in *their* build. Replaced with a locally declared
  constant rather than RN's `absoluteFill`, because the peer range is
  `react-native: *` and a local object is correct on every version. Verified the
  library typechecks against RN 0.84 and RN 0.86 with `skipLibCheck` off.

- **iOS: two Swift type errors**, found by the first build that ever
  type-checked against the Mapbox frameworks:
  - `Expression` was ambiguous once the iOS 26 SDK added
    `Foundation.Expression`; qualified to `MapboxMaps.Expression`.
  - The per-event observer registrations used a `for` loop, which
    `ModuleDefinitionBuilder` rejects as a result builder. Unrolled, keeping
    the per-event gating that Android has.

- The security scanner's temp-file template was BSD-only, so the scan job
  failed on Linux while passing on macOS.

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
  > app's `pnpm` patch and are now upstreamed.

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
  directly. (Also from that downstream patch.)
- Overlay sheet distance ignored `distanceUnit` and always rendered raw metres
  (e.g. `12480 m`). It now formats as m/km or ft/mi.
- Importing the package no longer throws when the native module is missing
  (web, Jest, Expo Go). The module is resolved lazily with an actionable error.
- The published npm tarball listed iOS sources file-by-file, so newly added
  Swift files were silently omitted. It now globs `ios/*.swift`.

### Changed

- **`scripts/` is no longer published.** It holds only maintainer tooling (the
  release verifier and the security scanner); no lifecycle script runs it and
  nothing in the published surface references it.

- Android and iOS builds are now gated in CI. Both platforms are compiled on
  every change that can affect them, which is what caught the faults above.

- Dependency upgrades to clear security advisories: `expo-module-scripts`
  3.5.4 → 56.0.3, and in the example app `expo` 54 → 57, `react-native`
  0.81 → 0.86. Root advisories 45 → 15 and example 37 → 17, with no critical
  or high-critical findings left. None of these ever reached consumers: the
  package declares no runtime dependencies and neither lockfiles nor the
  example app are published.


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

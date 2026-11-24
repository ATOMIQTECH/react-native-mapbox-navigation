# Changelog

## 4.0.0

Mostly fixes and features from a downstream app's production patch against
3.1.0, upstreamed so no consumer has to carry one, plus guidance diagnostics.

### Breaking

- **Expo SDK 56 or newer is required** (`expo >= 56`, was `>= 50`). The config
  plugin is now `app.plugin.ts`, and Expo resolves a TypeScript plugin file from
  SDK 56 on. Nothing changes in how it is used: keep
  `"@atomiqlab/react-native-mapbox-navigation"` in your `plugins`. Its options
  are typed as `MapboxNavigationPluginOptions` for an `app.config.ts`.
- **iOS builds need Xcode 26.5 or newer** (Mapbox Maps 11.35).
- **`NavigationError.code` is a `NavigationErrorCode` union** instead of
  `string`, and every error carries `recoverable`.

**Behaviour changes worth checking before you upgrade:** `onBannerInstruction`
is now change-driven rather than per tick, `onLocationChange` follows the map
matcher, Mapbox's native end-of-route card is never shown, and a mid-trip
`destination`/`waypoints` change re-routes in place. Multi-stop apps that
advance legs themselves should set `waypointAdvance="manual"`. See *Changed*.

### Added

- **`NO_LOCATION_FIX` through `onError` when guidance starts but no position
  arrives.** Both platforms start guidance as soon as a route resolves — iOS
  through `NavigationViewController`, which puts the trip session into active
  guidance itself, and Android through `startTripSession`, which returns as soon
  as the session exists. Neither checks that the device is actually producing
  location fixes, so a simulator or emulator with no location set would compute
  a route and then sit forever: empty banner, no progress, no voice, and a
  camera that never positions. Mapbox logs "The navigator is paused" at warning
  level and nothing reached `onError`, so the only symptom an app saw was a
  blank navigation view.

  A watchdog now arms when guidance attaches and is cancelled by the first route
  progress update. If 20s pass with no progress, `onError` fires — and the code
  distinguishes the two causes, because their remedies are opposite:

  - `NO_LOCATION_FIX` when the device has no position at all (give it one, or
    pass `shouldSimulateRoute`).
  - `GUIDANCE_NOT_PROGRESSING` when fixes are arriving and the navigator still
    produces nothing, which is a session problem rather than a location one —
    typically a second `MapboxNavigationView` mounted over the first, stopping
    its billing session ("billing session is not running" in the Mapbox log).

  Android reads this from the raw-location stream feeding the navigator; iOS
  from the last known `CLLocation`. The timeout is deliberately long enough that
  a cold GPS fix on a real device does not trip it.

  The same code is also reported, once, when no `startOrigin` is given and the
  pre-route wait for a device fix has seen none after 20s. That wait used to be
  silent on both platforms; it still carries on.

The rest of this release upstreams a downstream app's production patch against
3.1.0, with the parts that encoded one app's choices made opt-in or generic.

- **`waypointAdvance: 'automatic' | 'manual'`.** `manual` holds a multi-stop
  route's leg after arrival until `advanceToNextWaypoint()`, so a stop completed
  by an app event cannot race the SDK's own advance. Default `automatic`
  (unchanged behaviour).
- **`Waypoint.approach: 'curb' | 'unrestricted'`** — arrive with the stop on the
  driving side of the road.
- **`NavigationError.recoverable` and a typed `NavigationErrorCode`.** `true`
  when navigation carries on or the package retries by itself (`NO_LOCATION_FIX`,
  `REROUTE_FAILED`, an ignored routing option, …), `false` when nothing happens
  until the app acts. Treat `false` as fatal and `true` as a notice; an app that
  treated every unknown code as fatal no longer has to guess. Every code is
  listed on `NavigationErrorCode`.
- **`cameraPadding: EdgeInsets`** — frame the following/overview camera inside
  the part of the map the host's own UI leaves uncovered.
- **`keepAliveWhenDetached`** — keep the navigator, route and trip session
  running while a screen is pushed over the view, so coming back only redraws.
  The session still ends on unmount, on `enabled={false}`, and after 10 minutes
  detached.
- **`navigationLines`** — display-only polylines on the navigation map.
- **`showsWaypoints`** — hide the SDK's start/stop/destination circles.
- **`ornamentBottomMargin`** — lift the Mapbox logo and attribution above a host
  panel, whichever banners are shown.
- **`NavigationMarker.image` / `imageScale`** — draw a picture instead of the
  bubble, from `require()`, a URL or a `data:` URI. Loaded off the main thread
  and cached by URI in a bounded cache.
- **`onJourneyDataChange` only crosses the bridge when something listens** — a
  handler on the view or an `addJourneyDataChangeListener` subscription.
- **Richer events.** `onBannerInstruction` gains `maneuverType`,
  `maneuverModifier`, `maneuverDegrees`, `drivingSide`, `exitNumber`, `lanes`
  and `nextManeuver` — typed as `ManeuverType` / `ManeuverModifier` — enough to
  draw a complete custom banner. `onLocationChange`
  gains `timestamp`, `speedLimit` (`{ value, unit: 'km/h' | 'mph' }`, the
  posted sign), `roadName` and `matched`. `onRouteProgressChange`
  gains `legDistanceRemaining` / `legDurationRemaining`. `onRouteChange` gains
  `routeId` and fires only for a new route geometry. `onOffRoute` gains
  `rerouteFailed` and `reason` (`deviation`, or `destinationChange` when a new
  `destination` could not be routed yet and the old route still guides), and
  both platforms now send `offRoute: false` when a new route is in place.

- **`NavigationMarker.animatesMovement`** — glide a marker to its new position
  instead of jumping. Off by default.

### Changed

- **Mapbox SDKs: Navigation 3.35.2, Maps 11.35.2, Turf 4.1.0**, pinned
  **exactly** on both platforms. iOS used to ask Swift Package Manager for
  "up to next major", so a fresh install could already resolve a newer
  Navigation SDK on iOS than Android's, and an existing Xcode project kept its
  first requirement forever. The upgrade brings, among others: the iOS
  predictive cache now actually downloading map tiles, an Android navigator
  thread leak on every re-setup fixed, iOS route-decoding crashes fixed, the
  Android camera framing a fresh map correctly, view annotations no longer
  crashing with several maps on screen, and fewer flickering lines at
  navigation pitch. **iOS builds now need Xcode 26.5 or newer.** Maps iOS
  11.35 reports crash data through MetricKit and declares
  `NSPrivacyCollectedDataTypeCrashData`, which shows up in your app's App Store
  privacy report.
- **Changing `destination` or `waypoints` mid-trip re-routes in place** from the
  car's current matched position, keeping the session, instead of being ignored
  (iOS) or restarting guidance (Android). Changes under 15 m are ignored, legs
  already completed are dropped, and props set in the same render share one
  request. A failed re-route keeps the running route, reports `REROUTE_FAILED`
  once plus `onOffRoute({ offRoute: true, rerouteFailed: true, reason:
  'destinationChange' })`, and retries
  with backoff — unless the router says no route exists, which only a new target
  retries.
- **The first route waits briefly for a good origin.** A cached fix is used only
  if it is under 15 s old and 50 m accurate; otherwise the best fix within 4 s
  is taken, with the heading when moving, so routes no longer start behind the
  car or on a parallel road.
- **`onBannerInstruction` is change-driven:** it fires when the instruction
  changes or the distance moves by 10 m, not on every progress tick.
- **`onLocationChange` follows the map matcher** (including after arrival)
  instead of route progress, falling back to the raw fix when the two disagree
  by more than max(30 m, 2× accuracy).
- **Predictive caching uses the same radii on both platforms** (navigation
  tiles 3 km around the car and destination, 1 km along the route; map tiles
  2 km / 500 m). They differed per platform.
- **Mapbox's native end-of-route rating card is never shown.**
  `showsEndOfRouteFeedback` has only ever been documented as the package's
  React modal; iOS defaulted the native card on.

### Fixed

- **Android: with `keepAliveWhenDetached`, the map came back black** after a
  screen was pushed over it and popped. The session kept running, but the
  `MapView` had bound to the covered screen fragment's lifecycle and was
  destroyed with it. It now follows the activity, so it only stops and restarts.
- **Clearing a prop left the old value in place**, and on Android threw
  "Cannot set prop". Going from a value to `undefined` — `mute={x || undefined}`,
  or turning the overlay bottom sheet off, which hands back the caller's own
  `showsTripProgress`/`showsManeuverView`/`showsActionButtons`/`showCancelButton`
  — now resets the prop to its default on both platforms.
- **iOS: `showsTripProgress={false}` and `showsManeuverView={false}` were
  ignored** whenever the other banner stayed up: the time, distance and arrival
  labels stayed beside the cancel button, and the maneuver banner could not be
  hidden. Both banners were looked up by names Navigation SDK v3 no longer uses.
- **`onRouteChange` fired again for the same route** when a kept-alive view
  came back, contradicting "only a new route geometry emits an event".
- **Android: `navigationMarkers` without an `image` were never drawn.** Maps
  SDK 11.35 rejects a view annotation whose view has no layout params, and the
  built-in pin and dot views were created without any, so every such marker
  failed with only a log warning.
- **iOS: opening a navigation screen made up to three billed Directions
  requests** and fired `onRouteChange` up to three times. A navigation stack's
  push animation takes the view out of the window for a moment while the
  first route is being embedded, and every detach tore the session down at once,
  so the route was requested again and then re-routed in place. A detach now
  waits half a second before tearing down, and a view that returns in time keeps
  its route; leaving for good still ends the session, and unmounting or
  `enabled={false}` still ends it immediately.
- **iOS: toggling `shouldSimulateRoute` crashed** with "MapboxNavigationProvider
  was instantiated twice".
- **iOS: the JS banner showed the maneuver already driven**, paired with the
  distance to the next one.
- **Android: the banner's `stepDistanceRemaining` never changed** — it sent the
  step's constant trigger distance. Journey data had the same bug.
- **iOS: panning the map snapped straight back** to following.
- **iOS: `cameraMode="overview"` on a running session did nothing.**
- **iOS: mute and voice volume were lost from the second session on**, and
  `mute`, `voiceVolume` and `language` were ignored mid-session.
- **iOS: map style props were ignored after mount.** Swapping the style no
  longer jumps the camera to the style's default position.
- **iOS: a route that finished for an earlier start released the live session**,
  letting a second view start one and stop the first's billing session.
- **iOS: arriving tore the map down**, leaving it black, and the SDK's
  self-dismiss on arrival could close the modal hosting the React Native screen.
- **iOS: the way-name pill reappeared** with `showsWayNameLabel={false}`, and
  the native Resume button overlapped host controls.
- **iOS: the 3D puck model was re-cached on every launch** (the cache key used a
  per-launch hash seed).
- **iOS: guidance location** now uses navigation accuracy, an activity type
  matching the route profile, and no automatic pauses.
- **Android: "This instance of MapboxNavigation is destroyed"** when a second
  view mounted, and views stuck on "Waiting for your location…" after the
  navigator was replaced or a stop/start cycle.
- **Android: every session leaked its MapView** (renderer, style and tile
  cache).
- **Android: the screen slept during guidance.**
- **Android: the documented `require()`'d 3D puck model never drew.**
- **Android: route language and spoken units ignored `language` and
  `distanceUnit`**, and walking/cycling requests carried driving-only options.
- **Android: voice mute, volume and language set before the first route were
  lost**, and the voice player was driven off the main thread.
- **Android: `showsContinuousAlternatives` is honoured** at runtime.
- **Android: the route line was drawn over the car.** The route goes into the
  Standard style's `middle` slot, which every other style lacks — including
  this package's Android default, `navigation-day-v1` — and the SDK then puts
  it at the top of the layer stack, above the location puck added earlier.
  With a 3D car model the road visibly ran across the vehicle. The puck is now
  kept above the route line, its maneuver arrows and `navigationLines` after
  every route, style load and line update. iOS already ordered these itself.
- **The config plugin required `@expo/config-plugins` without declaring it.**
  It only resolved where the package manager happened to hoist it; it now goes
  through `expo/config-plugins` (`expo` is a peer dependency).
- **Both: markers vanished inside the camera padding or next to the puck.**
- **Both: `onWaypointArrive.index` drifted after a re-route** dropped completed
  stops — it counted legs of the new route. It is now the stop's index in your
  `waypoints` array.
- **iOS: turning `showsManeuverView` / `showsTripProgress` back on after
  mounting with both off showed nothing.** Guidance is rebuilt with the stock
  banners.
- **iOS: `showsContinuousAlternatives` only took effect for the first session**,
  and turning it off also switched off alternative detection. It now only
  decides whether a faster route is switched to automatically, at any time;
  Android restores the SDK default when a session ends.
- **Keep-alive:** returning to a kept-alive view on Android no longer fails with
  `NAVIGATION_SESSION_CONFLICT` while the covering screen releases the session,
  and the 10-minute detached limit now fires `onCancelNavigation` on both
  platforms instead of ending the session silently.
- **`navigationLines` are updated in place** instead of removed and re-added on
  every change, and drawn below the puck.
- **iOS: `distanceUnit` reaches the route request**, so banners and voice use
  it with Navigation SDK 3.35, which reads the unit from the request and
  otherwise falls back to the device locale (an en_US simulator showed miles
  under `distanceUnit="metric"`).
- **`onCameraFollowingStateChange` reports the initial state** when guidance
  starts, on both platforms; it used to fire only on a change, so an app never
  learned the camera was following until the user panned.
- **Android: a view that does not own the session no longer overrides the
  running session's mute, volume or voice language.**

### Development

These change nothing a consumer installs; they change how the package is built
and checked.

- **pnpm** replaces npm (`packageManager` pins the version) for the package and
  the example app. The example links the package with `link:..`, so library
  edits reach it without a reinstall.
- **oxlint and oxfmt replace Biome and the broken ESLint setup.** Type-aware,
  with `correctness`, `suspicious` and `pedantic` as errors; pre-existing
  problems are counted in `lint-baseline.json`, which can only shrink.
- **knip** fails on unused code and undeclared dependencies.
- **No comments in source**, and **500 lines per file** with exact allowances
  that only shrink, enforced for TypeScript by a local oxlint plugin
  (`lint-rules/`) and for Swift, Kotlin, Gradle, Ruby and shell by
  `scripts/check-source.mjs`. Rationale lives in commit messages.
- **commitlint** enforces Conventional Commits.
- **`pnpm check`** runs everything; the git hooks and the new `check` workflow
  run it too. See `CONVENTIONS.md`.
- **Docs: `startOrigin` was documented as required on iOS.** It has not been
  since 2.1.0, which added the iOS device-location fallback; *Platform
  Behavior* still told you otherwise.

## 3.1.0

Two unrelated things, both about not making consumers work around this package.

`@rnmapbox/maps` alongside this package now needs no setup at all, where before
every consuming app hand-wrote an Expo config plugin and a Ruby `post_install`
shim. And eight-digit hex colours now mean the same thing on both platforms.

**The colour change reinterprets an input you may already be passing.** If any
colour string you pass is eight digits long, read the first entry under
*Changed* before upgrading. Everything else here is additive.

### Added

- **`@rnmapbox/maps` is reconciled automatically on both platforms.** When that
  package is resolvable from your project, the config plugin:

  - **iOS** — writes `$RNMapboxMapsSwiftPackageManager = 'manual'` into the
    Podfile, which stops `@rnmapbox/maps` declaring `MapboxMaps` and `Turf` as
    pods, and `ios/spm.rb` then wires its pod target to the same Swift package
    this package already resolves. A correct install now has **no Mapbox pods at
    all**.
  - **Android** — redirects `com.mapbox.maps:android` to
    `com.mapbox.maps:android-ndk27` at the Maps version Navigation v3 is built
    against, so the app cannot end up with both artifact variants.

  Nothing to configure, and the order of the two packages in your `plugins`
  array does not matter. Opt out with `rnmapboxMapsCompat: false` if you would
  rather wire them together yourself. See
  [Using with `@rnmapbox/maps`](README.md#using-with-rnmapboxmaps).

- The Mapbox SDK versions are published under a `mapbox` key in `package.json`,
  so you can read the Maps version this package is built against:
  `require('@atomiqlab/react-native-mapbox-navigation/package.json').mapbox.maps`.

- `example/` gained a **With `@rnmapbox/maps`** scenario that renders an
  `@rnmapbox/maps` `MapView` beside the navigation view, so the combination is
  built on every CI run instead of being discovered by consumers. The example
  lists `@rnmapbox/maps` *after* this package on purpose — Expo runs mods
  last-registered-first, so that is the ordering which puts
  `$ExpoMapboxNavigation.post_install` ahead of `$RNMapboxMaps.post_install` in
  the Podfile.

### Changed

- **Eight-digit hex is now `#RRGGBBAA` on both platforms, and its alpha is
  applied.** It was `#RRGGBBAA` on iOS and `#AARRGGBB` on Android, so one
  palette produced two themes; and on iOS the marker colours truncated the
  string with `prefix(6)` and dropped the alpha byte entirely. Every colour
  string now goes through one parser per platform, and the two agree.

  Alpha last is the CSS/web convention, which is what a React Native developer
  writing `'#14532D80'` means by it. Three- and six-digit hex are unaffected.

  What this changes in practice, per prop:

  | Prop | Before | Now |
  | --- | --- | --- |
  | `colors` (iOS) | `#RRGGBBAA`, alpha applied | unchanged |
  | `colors` (Android) | `#AARRGGBB` | `#RRGGBBAA` |
  | `locationPuck` `appearance` (iOS) | `#RRGGBBAA`, alpha applied | unchanged |
  | `locationPuck` `appearance` (Android) | `#AARRGGBB` | `#RRGGBBAA` |
  | `navigationMarkers` `color` / `badgeColor` (iOS) | 8 digits accepted, alpha **dropped** | alpha applied |
  | `navigationMarkers` `color` / `badgeColor` (Android) | `#AARRGGBB` | `#RRGGBBAA` |

  To migrate, reverse the byte order of any eight-digit string you were passing
  to Android — `'#8014532D'` becomes `'#14532D80'` — and check any eight-digit
  string you were passing to iOS *markers*, which will now be translucent where
  it used to be opaque. The simplest fix in both cases is to drop to six digits,
  which has always meant the same thing everywhere.

- **Three-digit hex now works on Android.** Every doc listed `#RGB` as one of
  the two portable forms, but Android never parsed it: `Color.parseColor`
  accepts only the 7- and 9-character forms and threw on `'#1AF'`, which this
  package caught and treated as "no colour set", silently leaving the Mapbox
  default. It now expands by doubling each nibble (`'#1AF'` → `'#11AAFF'`) as
  CSS and iOS do. If you passed three-digit hex to Android and saw no effect,
  you will now see the colour you asked for.

- **A malformed `#` string is now rejected rather than guessed at on Android.**
  `Color.parseColor` parses the digits with `Long.parseLong`, which accepts a
  sign, so `'#+fffff'` used to yield a colour. Anything starting with `#` is now
  parsed as hex or rejected outright. Colour *names* without a `#` ("red",
  "magenta") still work on Android, as they always have — they are not portable,
  iOS has no equivalent, and they remain undocumented, but nothing is gained by
  breaking an app that relies on one.
- `ios/spm.rb`, `android/build.gradle` and `app.plugin.js` all read the native
  SDK versions from `package.json` instead of each holding their own copy. The
  Maps version is now shared with `@rnmapbox/maps`, so a stale copy would put
  the two packages on different Maps SDKs without anything failing loudly.
- CI asserts the shared-Maps invariant on both platforms rather than hoping a
  build breaks. The iOS job checks that `Podfile.lock` contains no Mapbox pods
  and that the Podfile hands `@rnmapbox/maps` to SPM. The Android job resolves
  the *app's* classpath — not just this module's — and fails if both Maps
  artifact variants appear, forcing `targetSdkVersion` to 34 via
  `scripts/ci/check-one-mapbox-maps-variant.init.gradle` because that is the
  only value at which the bug reproduces (Expo's defaults are 35+, where
  `@rnmapbox/maps` already picks `-ndk27` and the build would pass either way).

### Fixed

- **Android: `checkDebugDuplicateClasses` on `com.mapbox.maps` classes when
  `@rnmapbox/maps` was installed and `targetSdkVersion` was below 35.** Mapbox
  publishes every Android artifact twice — a default build and an `-ndk27` build
  with 16 KB page-size support — and the two carry the same classes. This
  package is always on `-ndk27`, while `@rnmapbox/maps` picks its variant from
  the app's `targetSdkVersion` and only uses `-ndk27` at 35 and above. Any app
  below that got one of each and could not build, with an error naming neither
  package. Apps on Expo SDK 54+ defaults (targetSdk 35/36) were never affected.

- **iOS: `pod install` no longer only *warns* about MapboxMaps being resolved by
  both CocoaPods and SPM.** That warning told consumers to edit their Podfile
  and said the package could not fix it; it now fixes it, and the remaining
  warning fires only for the cases that genuinely cannot be resolved from here
  (another pod declaring MapboxMaps, or a `$RNMapboxMapsSwiftPackageManager`
  Hash set by hand) and says which one it saw.

- **Translucent chrome no longer needs a workaround on iOS.** `colors` could
  already carry alpha there, but the documentation told you not to use it
  because Android disagreed, so the honest advice was "pass an opaque colour and
  use the map style for translucency instead". Both platforms now honour it, so
  that advice is retired.

  One caveat is unchanged and worth repeating: iOS reaches the chrome colours
  through `UIAppearance`, which is process-global and has no "unset". That
  applies to alpha as much as to hue, so `'#00000000'` permanently paints a
  piece of chrome transparent for the life of the process rather than hiding it.
  Use the `shows*` props to turn UI off.


## 3.0.1

Fixes a cold-build failure on iOS introduced with the v3 Swift Package Manager
integration in 3.0.0. Android is unaffected.

### Fixed

- **iOS: `no such module 'MapboxMaps'` on a clean build.** The Mapbox Swift
  packages were not guaranteed to be built before this package's pod target
  compiled, so on a machine with no warm `DerivedData` the compile could start
  before the `.swiftmodule` files existed.

  The pod target deliberately takes no SPM *product dependency* — a static
  library archives those into its own `.a`, and the app then links them a second
  time, failing with `duplicate symbol` errors. But withholding it also removed
  the only thing ordering the two builds. `ios/spm.rb` now adds a non-linking
  `PBXTargetDependency` (one carrying a `productRef` rather than a `target`),
  which orders the build without reintroducing the duplicate symbols.

  This reproduced only on clean machines: an incremental local build almost
  always found the modules left behind by an earlier build. If you hit it on
  3.0.0, upgrading and re-running `pod install` is enough — no Podfile or app
  code changes are needed.

### Changed

- `android/build.gradle` now reads its `version` from `package.json` instead of
  a hand-maintained literal, which had drifted to `2.2.0`. The coordinate is not
  resolved by consumers (the module is consumed as a project dependency), so
  this is cosmetic, but it can no longer fall out of step with a release.
- CI's iOS job now builds for `arm64` explicitly. `generic/platform=iOS
  Simulator` carries no architecture, so Xcode had been resolving it to x86_64
  and cross-compiling on an arm64 runner. This was not related to the bug above
  — both architectures build correctly — it simply spent runner time
  cross-compiling an architecture no contributor or consumer builds.

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

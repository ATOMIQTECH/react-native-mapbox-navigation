# Mapbox Navigation SDK v3 migration

Working document for the `2.x` → `3.0.0` migration. It exists to make the
migration verifiable: the parity contract below is the definition of done, and
the API mapping records what was checked against SDK source rather than guessed.

All v3 facts here were verified against SDK sources at iOS `3.30.1` and Android
`v3.30.1`. `docs.mapbox.com` returns HTTP 403 to automated fetches, so the
official migration guides were **not** readable — the source tree is the
authority for everything below.

## Why we are doing this

Not primarily for v3 features. `@rnmapbox/maps@10.2.10` defaults to Mapbox Maps
SDK `11.16.2`, but an app that uses both it and this package has to pin it **back
down** to `10.19.0` in three places to match us:

| Where | Pin |
| --- | --- |
| `app.config.ts` (~230) | `RNMapboxMapsVersion: '10.19.0'` |
| `android/gradle.properties` (65) | `expoRNMapboxMapsVersion=10.19.0` |
| `ios/Podfile` (31) | `$RNMapboxMapsVersion = '10.19.0'` |

We are the reason their whole map stack sits on Maps SDK 10.x, a line rnmapbox
warns is deprecated and sponsor-only-supported. Those three pins disappear when
this migration lands.

## Parity contract

Nothing in this section may change behaviour. Any deviation is a breaking change
and must be called out explicitly in `CHANGELOG.md`, not absorbed silently.

### Module async functions (8)

`setMuted`, `setVoiceVolume`, `setDistanceUnit`, `setLanguage`,
`getNavigationSettings`, `stopNavigation`, `resumeCameraFollowing`,
`advanceToNextWaypoint`

(`advanceToNextWaypoint` was upstreamed from a downstream pnpm patch in 2.1.0 —
it is load-bearing for multi-stop trips.)

### Events (14 declared on Android, 12 on iOS — see the gap note below)

`onLocationChange`, `onRouteProgressChange`, `onJourneyDataChange`,
`onRouteChange`, `onCameraFollowingStateChange`, `onBannerInstruction`,
`onArrive`, `onWaypointArrive`, `onOffRoute`, `onCancelNavigation`, `onError`,
`onBottomSheetActionPress`, plus `onDestinationPreview` and
`onDestinationChanged` on Android only.

**Pre-existing bug found while writing this contract, and how it was
resolved.** `addDestinationPreviewListener` and
`addDestinationChangedListener` are exported and documented in JS and were
emitted on Android, but `onDestinationPreview` / `onDestinationChanged` appear
**nowhere** in `ios/` — they had never fired on iOS.

Investigating during the Android rewrite showed the two are not alike:

- `onDestinationChanged` is emitted from the `setDestination` **prop setter**,
  independent of Drop-In. It survives the migration untouched and still only
  fires on Android — a genuine remaining gap, tracked in `KNOWN_GAPS`.
- `onDestinationPreview` came from `NavigationViewListener`, i.e. Drop-In's
  preview phase. v3 removes that phase, so it now fires on **neither**
  platform. The JS helper is kept and marked `@deprecated` rather than removed,
  so existing subscriptions keep working.

Two invariants that are easy to lose in a rewrite:

1. Every event is delivered **both** as a view prop (`onX`) and through the
   module emitter backing `addXListener()`. `MapboxNavigationEventBridge` exists
   solely to relay view → module; on iOS the `add*Listener` helpers were once
   silently dead, and that regression must not return.
2. Emission is **gated on subscription count** via `startObserving` /
   `stopObserving`. Ungated emission during active guidance fires on every
   location update and costs real bridge time. Keep the gate.

### View props (34 — 33 shared + 1 Android-only)

`startOrigin`, `enabled`, `destination`, `waypoints`, `navigationMarkers`,
`shouldSimulateRoute`, `showCancelButton`, `mute`, `voiceVolume`, `cameraPitch`,
`cameraZoom`, `cameraMode`, `mapStyleUri`, `mapStyleUriDay`, `mapStyleUriNight`,
`uiTheme`, `routeAlternatives`, `showsSpeedLimits`, `showsWayNameLabel`,
`showsTripProgress`, `showsManeuverView`, `showsActionButtons`,
`showsReportFeedback`, `showsEndOfRouteFeedback`, `showsContinuousAlternatives`,
`usesNightStyleWhileInTunnel`, `routeLineTracksTraversal`,
`annotatesIntersectionsAlongRoute`, `distanceUnit`, `nativeFloatingButtons`,
`language`, `locationPuck`, `eventThrottleMs`, plus `androidActionButtons`
(Android only).

The migration adds one prop on top of this baseline — `colors`, shared by both
platforms — so `verify:parity` now reports 34 iOS / 35 Android. Additive, so the
contract above is unaffected.

### Exported components

`MapboxNavigationView`, `MapboxNavigationFloatingButton`,
`MapboxNavigationFloatingButtonsStack`

### Non-negotiable runtime behaviour

- **Importing the package must never throw.** `getNativeModule()` resolves the
  native module lazily inside a `try/catch` and raises a descriptive error only
  when an API is actually *called*. This is what keeps `import` safe on web, in
  Jest, and in Expo Go. Do not hoist the lookup to module scope.
  - `getNativeView()` had the same problem and was **fixed** in this migration:
    it previously had no `try/catch`, so rendering `<MapboxNavigationView>` in
    Expo Go threw raw instead of degrading. It now falls back to a placeholder
    that renders nothing and logs once. Throwing from a view resolver takes the
    whole app down at render time, which is exactly the Expo Go crash this
    migration must not introduce.
- `uiTheme` must theme the map tiles, not only the UIKit chrome (the iOS
  `StyleManager` fix upstreamed in 2.1.0).
- Android teardown must call `nav.stopTripSession()`; skipping it leaks the trip
  session across unmounts.

## Verified v2 → v3 API mapping

### iOS

Packaging is the blocking change, not the APIs.

| v2 | v3 | Note |
| --- | --- | --- |
| `MapboxNavigation` pod | `MapboxNavigationCore` + `MapboxNavigationUIKit` (SPM) | **No CocoaPods for v3 at all.** Pod tops out at `2.22.0`; the v3 pods return 404 on trunk. |
| `MapboxNavigationService` | `MapboxNavigationProvider` → `MapboxNavigation` | Only real removal. |
| `IndexedRouteResponse` + `Directions.shared.calculate` | `NavigationRoutes` via `mapboxNavigation.routingProvider()` | |
| delegates / `NotificationCenter` | Combine publishers | Broadest mechanical change. |
| `NavigationViewController` | unchanged, moved to `MapboxNavigationUIKit` | New init: `init(navigationRoutes: NavigationRoutes, navigationOptions: NavigationOptions)` |
| `NavigationOptions`, `StyleManager` | unchanged (`MapboxNavigationUIKit`) | |
| `NavigationMapView` | moved to `MapboxNavigationCore` | |

Requirements: iOS 14+, Swift 5.9, Xcode 16+. Nav `3.30.1` pins MapboxMaps
**exactly** `11.30.1` and NavigationNative `324.30.1`.

#### `NavigationSettings` is gone — this one affects the parity contract

There is no `NavigationSettings` class and no `NavigationSettings.shared`
anywhere in v3. We use it today for `distanceUnit`, `voiceMuted` and
`voiceVolume`, which back three contract functions (`setDistanceUnit`,
`setMuted`, `setVoiceVolume`). v3 replaces the global with per-provider config:

| v2 global | v3 |
| --- | --- |
| `NavigationSettings.shared.distanceUnit` | `CoreConfig.unitOfMeasurement` — `.auto` / `.imperial` / `.metric` |
| `NavigationSettings.shared.voiceMuted` | `SpeechSynthesizing.muted: Bool`, via `provider.routeVoiceController` |
| `NavigationSettings.shared.voiceVolume` (`Float`) | `SpeechSynthesizing.volume: VolumeMode` — `.system` or `.override(Float)` |

Note the volume type change: our JS API takes a `number` in `0...1`, so it maps
to `.override(Float(volume))`, and the old "set volume to system default"
behaviour is now `.system`. Keep the JS signature identical.

#### Verified v3 architecture

`MapboxNavigationProvider(coreConfig: CoreConfig)` is the root object, replacing
the v2 `MapboxNavigationService`:

- `provider.mapboxNavigation` → `MapboxNavigation`
- `mapboxNavigation.routingProvider()` → `RoutingProvider` (replaces `Directions.shared.calculate`)
- `mapboxNavigation.tripSession()` → `SessionController` (`startActiveGuidance` / `startFreeDrive`)
- `provider.routeVoiceController` → `RouteVoiceController`
- `provider.coreConfig` / `provider.apply(coreConfig:)`

`CoreConfig` carries what used to be scattered across globals and init
arguments — the fields that matter to us: `unitOfMeasurement`, `locale`,
`locationSource`, `ttsConfig`, `routingConfig`, `routeRequestConfig`,
`multilegAdvancing`, `predictiveCacheConfig`, `electronicHorizonConfig`.

`shouldSimulateRoute` maps to `CoreConfig.locationSource`:
`LocationSource` is `.simulation(initialLocation:)` / `.live` / `.custom(_)`,
replacing v2's `simulating: .always`.

#### NavigationViewController properties — all five survive

Confirmed present on `NavigationViewController` in v3:
`showsSpeedLimits` (line 536, proxies `ornamentsController`),
`routeLineTracksTraversal` (86, proxies `navigationMapView`),
`showsContinuousAlternatives` (114), `usesNightStyleWhileInTunnel` (150),
`annotatesIntersectionsAlongRoute` (101), `showsReportFeedback` (511),
`showsEndOfRouteFeedback` (522). No parity loss here.

#### Additional iOS removals found during implementation

These were not obvious from the type names and each broke something real:

| v2 | v3 | Impact |
| --- | --- | --- |
| `UserLocationStyle` | removed — `NavigationMapView.puckType: PuckType?` | `locationPuck` prop |
| `UserPuckCourseView` | removed, no replacement | the `locationPuck` tint option |
| `NavigationViewportDataSource` | `MobileViewportDataSource` | `cameraMode` / `cameraPitch` / `cameraZoom` |
| `followingMobileCamera` / `overviewMobileCamera` | one `currentNavigationCameraOptions` (`followingCamera` / `overviewCamera`) | same |
| `navigationCamera.follow()` / `.moveToOverview()` | `navigationCamera.update(cameraState:)` | same |
| `navigationService.router.advanceLegIndex(completionHandler:)` | `mapboxNavigation.navigation().switchLeg(newLegIndex:)` | `advanceToNextWaypoint()` |
| `navigationService.routeProgress` | `mapboxNavigation.navigation().currentRouteProgress?.routeProgress` | arrival events |
| `Directions.shared.calculate(_:completion:)` | `routingProvider().calculateRoutes(options:)` returning `Task<NavigationRoutes, Error>` | route requests |

Delegate signature changes, both silent breakages if missed:

- `navigationViewController(_:didArriveAt:)` changed from **`-> Bool` to `Void`**. The
  `Bool` used to control leg auto-advance; that moved to
  `CoreConfig.multilegAdvancing`, whose default `.automatically` matches the old
  `return true`, so behaviour is preserved by leaving it alone.
- `navigationViewController(_:didRerouteAlong:)` dropped `at location:` and
  `proactive:`. Neither was used here.

Survives unchanged, verified: `NavigationViewController.styleManager` and
`StyleManager.applyStyle(type:)` — so the 2.1.0 `uiTheme` map-tile fix is intact.
`DayStyle` / `NightStyle` / `Style.mapStyleURL`, and all seven
`NavigationViewController` display properties.

#### Maps SDK 10 → 11 breakage (independent of the Navigation SDK)

- `ViewAnnotationOptions` is deprecated, and `.geometry`, `.anchor`, `.offsetX`,
  `.offsetY`, `.associatedFeatureId` are **`@available(*, unavailable)` and trap
  with `fatalError()`**. The `navigationMarkers` prop is migrated to
  `ViewAnnotation` objects: `annotatedFeature` replaces `geometry`,
  `variableAnchors` replaces the anchor/offset pair, and `priority` replaces the
  deprecated `selected`. Annotations are now retained and mutated in place, so
  marker storage changed from `[String: UIView]` to `[String: ViewAnnotation]`.
- `PuckType.puck2D` / `.puck3D` associated values are **unlabelled** in v11; our
  calls passed `configuration:` and would not compile.
- `Puck2DConfiguration` and `Puck3DConfiguration` are otherwise **unchanged**,
  including the two-overload ambiguity that requires passing `pulsing: nil`.

**One thing that still needs eyes on a device:** v11's
`ViewAnnotationAnchorConfig.offsetY` documents positive as "moves toward the
top". v10's `ViewAnnotationOptions.offsetY` was a binary CoreMaps type with no
readable source, so its sign convention could not be verified. The value and
anchor are carried across unchanged; if v10 meant the opposite, markers will sit
`2 × offsetY` off vertically — obvious on first run, and a one-line fix.

#### Pre-existing bug preserved deliberately

In `applyCameraConfiguration`, the following branch sets `cameraPitch` /
`cameraZoom` while leaving `pitchUpdatesAllowed` / `zoomUpdatesAllowed` at
`true`. Mapbox documents that a manually set value "will be overriden" unless
the gate is disabled first, so those two props have almost certainly never taken
effect in following mode (the overview branch disables its gate and is correct).
This was carried across as-is: fixing it would start honouring a pitch/zoom that
consumers currently set to no effect, which is a behaviour change that deserves
its own release rather than being buried in a migration.

### Android

APIs are mostly a clean lift; the UI layer is the rewrite.

| v2 | v3 |
| --- | --- |
| `com.mapbox.navigation:ui-dropin-ndk27` | **Module deleted.** No drop-in UI exists in v3. |
| `dropin.NavigationView` | Gone — assemble from `ui-components` |
| `dropin.map.MapViewBinder` / `MapViewObserver` | Gone |
| `dropin.navigationview.NavigationViewListener` | Gone |
| `dropin.RouteOptionsInterceptor` | Gone |
| `view.api.startRoutePreview()` / `startActiveGuidance()` | Gone — reimplement the preview → guidance state machine |
| `ui.voice.api.MapboxAudioGuidance` | `voice.api.MapboxAudioGuidance` (package move only) |
| Maps `10.19.0` | Maps `11.30.x` (own breaking jump) |

Verified **surviving unchanged**: `MapboxNavigationApp`, `MapboxNavigation`,
`NavigationRoute`, `RouteProgress`, `NavigationOptions`, `ArrivalObserver`,
`RoutesObserver`, `OffRouteObserver`, `BannerInstructionsObserver`,
`LocationObserver`, `RouteProgressObserver`, `LegIndexUpdatedCallback`,
`ui.maps.puck.LocationPuckOptions`.

v3 `ui-components` provides only discrete widgets: `maneuver`, `maps`,
`speedlimit`, `status`, `tripprogress`, `voice`, `MapboxExtendableButton`.

## The iOS integration hazard — test this first

Consuming MapboxMaps from **both** CocoaPods and SPM in one project is broken.
rnmapbox ships a `_check_no_mapbox_spm` guard that warns *"Duplicate Mapbox
dependency found, it's consumed by both SwiftPackageManager and CocoaPods"*.

After this migration that is exactly where a consumer lands:

- us → Nav v3 via **SPM** → MapboxMaps pinned `exact: 11.30.1`
- rnmapbox → via **CocoaPods** → `~> 11.16.2`

Resolution: move rnmapbox to SPM too (it supports
`$RNMapboxMapsSwiftPackageManager`). The versions do reconcile — `11.30.1`
satisfies `~> 11.16.2` — so a single-resolver graph is consistent.

Good news for the implementation: rnmapbox already ships the SPM-injection
machinery we need, and we should copy it rather than invent one —
`$RNMapboxMaps._add_spm_to_target(project, target, url, requirement,
product_name)` creates `XCRemoteSwiftPackageReference` entries from a CocoaPods
`post_install` hook.

Android has no equivalent problem: Gradle resolves a single Maps 11.x for both
packages, so Android gets *simpler* after the migration.

> **Resolved in 3.1.0, and the Android note above was wrong.**
>
> 3.0.x shipped the hazard as a `pod install` *warning* telling consumers to
> edit their Podfile, which meant every app using both packages hand-wrote a
> config plugin and a Ruby `post_install` shim. The config plugin now does it:
> it writes `$RNMapboxMapsSwiftPackageManager = 'manual'`, which stops rnmapbox
> declaring any Mapbox pod, and `ios/spm.rb` wires the `rnmapbox-maps` pod
> target to the same Swift package. Note the choice of `'manual'` over the Hash
> form: rnmapbox then writes nothing to the Xcode project, so the two
> `post_install` hooks can run in either order — and their order follows the
> order the packages sit in the app's `plugins` array, which no consumer should
> have to reason about.
>
> Android did have an equivalent problem. Gradle resolves a single Maps
> *version*, but not a single *artifact variant*: rnmapbox picks
> `com.mapbox.maps:android-ndk27` only at `targetSdkVersion` 35+ and the plain
> build below that, while this package is always on `-ndk27`. The two carry the
> same classes, so any app below targetSdk 35 failed
> `checkDebugDuplicateClasses`. The plugin now substitutes the plain coordinate.
>
> See [Using with `@rnmapbox/maps`](../README.md#using-with-rnmapboxmaps).

## New v3 capability to wire into the wrapper

Per the goal, v3-only features should be surfaced through the wrapper rather
than just inherited. Candidates confirmed present in v3 and absent in v2:

- `RouteOptions.excludedLocations: [LocationCoordinate2D]` — verified at
  `Sources/MapboxDirections/RouteOptions.swift:367`. Exclude routing by
  coordinate (driving / driving-traffic profiles, ≤50 points). Natural new view
  prop; shares the API's `exclude` key with `roadClassesToAvoid`.
- EV charging-station routing, incl. `changeUserChargingStationsRetainState`.
- Road cameras (`MapboxNavigationCppRoadCameras`) and pole-style route
  annotations (traffic signals, yield/stop signs, lane restrictions).
- `PredictiveCacheMapsOptions.tilesets` +
  `PredictiveCacheController.createTilesetsMapController`.
- `NavigationOptions.timeFormatter` (custom `TimeFormatter`; `timeFormatType` is
  deprecated).

These are additive and must default to off/unset so existing consumers see no
behaviour change.

## Theming: the `colors` prop

New in this migration, and the one feature here that is not a port. Mapbox's
default palette is its own brand blue — the route line on iOS, the maneuver
banner (`colorSecondary` = `#37516F`) on Android — which rarely matches a host
app. `colors` is a flat, string-keyed map of 24 colour overrides. Anything
omitted keeps the SDK default.

The keys are deliberately platform-neutral names rather than a mirror of either
SDK, because the two SDKs model the same surface differently:

| `colors` key | iOS | Android |
| --- | --- | --- |
| `routeLine` | `NavigationMapView.routeColor` | `routeDefaultColor` + low/unknown |
| `routeLineCasing` | `routeCasingColor` | `routeCasingColor` |
| `routeLineTraversed` | `traversedRouteColor` | `routeLineTraveledColor` |
| `routeLineAlternative` | `routeAlternateColor` | `alternativeRouteDefaultColor` + low/unknown |
| `routeLineAlternativeCasing` | `routeAlternateCasingColor` | `alternativeRouteCasingColor` |
| `congestion*` (5) | `congestionConfiguration.colors.mainRouteColors` | `route*CongestionColor` |
| `restrictedRoad` | `routeRestrictedAreaColor` | `restrictedRoadColor` |
| `maneuverArrow` | `maneuverArrowColor` | `RouteArrowOptions.withArrowColor` |
| `maneuverArrowStroke` | `maneuverArrowStrokeColor` | `withArrowCasingColor` |
| `maneuverBackground` | `InstructionsBannerView` / `TopBannerView` appearance | `ManeuverViewOptions.maneuverBackgroundColor` |
| `maneuverSubBackground` | `NextBannerView` / `LanesView` appearance | `subManeuverBackgroundColor` + `upcomingManeuverBackgroundColor` |
| `maneuverText` | `PrimaryLabel.normalTextColor` | `MapboxPrimaryManeuver.setTextColor` |
| `maneuverSecondaryText` | `SecondaryLabel` + `NextInstructionLabel` | `MapboxSecondaryManeuver` + `MapboxSubManeuver` |
| `maneuverDistanceText` | `DistanceLabel.value/unitTextColor` | `MapboxStepDistance.setTextColor` |
| `maneuverTurnIcon` | `ManeuverView.primaryColor` | `imageTintList` on the icon views |
| `floatingButtonBackground` | `FloatingButton.appearance().backgroundColor` | tint on the button *and* its `containerView` |
| `floatingButtonIcon` | `FloatingButton.appearance().tintColor` | `iconImage` tint + `textView` colour |
| `tripProgressBackground` | `BottomBannerView` + `BottomPaddingView` + the bottom `BannerContainerView` | *(iOS only)* |
| `tripProgressText` | `TimeRemainingLabel` (incl. all five `traffic*Color`), `DistanceRemainingLabel`, `ArrivalTimeLabel` | *(iOS only)* |
| `tripProgressIcon` | `CancelButton.tintColor` | *(iOS only)* |

### Why `routeLine` also writes the low/unknown congestion colours

Both SDKs paint congestion shading *over* the base line. Setting only the base
colour leaves a "green" route still rendering Mapbox blue wherever traffic data
is absent or free-flowing — which is most of a typical route. iOS's `routeColor`
setter does this itself (`NavigationMapView.swift:558`); Android's does not, so
we do it there. Explicit `congestion*` values are applied afterwards so they win.

### Making it apply live on both platforms

The prop is documented as applying live, including after mount. Each of the
three groups reaches the SDK by a different path, and only the first was free:

- **Route line.** iOS: plain properties, assign and done. Android: the route
  line component reads its options once at install time, so the first version
  needed a remount. v3 has `RouteLineConfig.viewOptionsUpdates`, a
  `MutableStateFlow` the component watches — wiring that removed the asymmetry
  rather than documenting it as a wart.
- **On-map turn arrow.** `RouteArrowConfig` has *no* equivalent flow, so the
  only way to recolour it after mount is to install that one component again.
  Its `Installation` handle is therefore kept apart from the others, and it is
  only reinstalled when an arrow colour actually changed.
- **Chrome.** iOS reaches every chrome colour through `UIAppearance` proxies,
  which `Style.apply()` populates — hence `ThemedDayStyle` / `ThemedNightStyle`
  in `ios/MapboxNavigationTheme.swift`. Two consequences:
  - Proxies are consulted at view *creation*, and `NavigationViewController`
    builds its banner in `loadView()` — before `styleManager` runs `apply()`.
    The SDK's own answer is `StyleManager.forceRefreshAppearance()`, which
    detaches and re-adds **every window subview**; that is not safe to rely on
    with a React Native root in that window, so we walk the hierarchy we own
    and recolour it directly (`MapboxNavigationTheme.refresh(in:)`).
  - `Style.traitCollection` is `internal`, so a subclass outside the SDK cannot
    read it. `DayStyle.apply()` registers separately under
    `UITraitCollection(userInterfaceIdiom: .phone)` and `.pad`, so
    reconstructing those two keys is equivalent — registering under a different
    trait collection would sit *beside* Mapbox's defaults instead of on top.

  Android's chrome splits differently: backgrounds are plain colour ints on
  `ManeuverViewOptions` and `updateManeuverViewOptions` accepts them any time,
  but **text and icon colours are only reachable through `@StyleRes` text
  appearances and a themed `Context`**, which cannot be synthesised from a
  runtime colour. Those are set straight onto the views instead — they are
  `AppCompatTextView`s and `AppCompatImageView`s, all public API, and the SDK's
  render path never rewrites a text colour or image tint, so the values hold
  across instruction changes. The banner inflates its secondary rows (lane
  guidance, the "then" step, the expandable upcoming-maneuver list) only when an
  instruction needs them, so the recolour is reapplied on each layout pass of
  the banner — the Android equivalent of what `UIAppearance` does for free.

### Four things that only showed up on device

Each of these compiled, logged nothing, and simply did not apply. Worth
recording because the pattern repeats: in both SDKs an options object is often
consumed at construction, and its `update…` method does not repaint what already
exists.

1. **Android's maneuver background.** `ManeuverViewOptions.maneuverBackgroundColor`
   through `updateManeuverViewOptions` left the banner Mapbox navy, with no
   error. The background lives on the `mainManeuverLayout` view inside the
   banner, which that call does not touch. Fixed by also painting
   `mainManeuverLayout`, `subManeuverLayout` and the two recyclers directly,
   looked up by the resource names in `ui-components`' `R.txt`. The options
   object is still passed, because the sub-layouts the banner inflates later are
   built from it.

2. **iOS route-line colours reverting.** Assigning `NavigationMapView.routeColor`
   and friends works — until a style is applied. `DayStyle.apply()` writes those
   same `UIAppearance` proxies, and `StyleManager.forceRefreshAppearance()`
   detaches and re-adds every window view, which makes UIKit re-apply appearance
   to the live map and overwrite the instance values. Observed as a themed route
   line snapping back to blue when the map switched to its night style. Fixed by
   writing the proxies too, from `ThemedDayStyle.apply()` *after* `super.apply()`
   so ours win. The per-congestion colours have no proxy — they are a plain
   struct — so those are re-asserted after every style application instead.

3. **iOS `colors` at mount never reaching the route line.** `applyRouteLineColors`
   reads `navigationViewController?.navigationMapView`, but it was being called
   from `embedNavigation` *before* that property was assigned, so it silently
   returned. Only a post-mount `colors` change worked. Now called again once the
   controller is in place.

4. **The Android voice button turning invisible.** `floatingButtonBackground` was
   tinting `containerView` only; the SDK's layout puts
   `audioGuidanceButtonBackground` on the button itself, so the background stayed
   white while `floatingButtonIcon` made the glyph white. Both are tinted now.

### Reverted: reinstating Android's trip progress bar

Tried, because iOS themes and hides its bottom bar and Android having no
equivalent is an asymmetry. It does not work: standing alone over the map,
`tripProgressContainer` measures itself at `wrap_content`, so the bar collapsed
to a ~125dp strip in the bottom-left with its readouts clipped and the distance
and arrival text empty. That is the same unstyled strip this migration removed in
the first place, so the widget stays uninstalled and `tripProgressBackground`,
`tripProgressText` and `tripProgressIcon` are documented on the public type as
iOS-only. Trip progress on Android remains the JS `bottomSheet` overlay, which
takes the host app's own styles directly.

### Hiding the iOS bottom bar

`showsTripProgress={false}` used to leave the bar up whenever `showsActionButtons`
was true, because the cancel button lives in it — so hiding the bar meant giving
up every other action button. The gate is now `showCancelButton` instead, so
`showsTripProgress={false}` plus `showCancelButton={false}` hides just that bar.
The container is also hidden through the public
`NavigationView.bottomBannerContainerView` rather than the class-name heuristics
that path used to rely on; those remain only as a fallback for SDK builds that do
not expose the banner controllers through KVC.

### `colors` is set-once-per-process on iOS, not toggleable

Observed while verifying: with `colors` switched back off, the route line stayed
green. The chrome and the route line's plain colours are written to
`UIAppearance` proxies, which are process-global and have no "unset". Removing a
key therefore leaves the last value rather than restoring the SDK default.

Restoring the default would mean knowing it, and the values `DayStyle` uses
(`UIColor.defaultRouteLayer` and friends) are internal to the SDK. Reading them
off a live `NavigationMapView` does not work either: by the time one exists, it
was already *created* with our proxy values. Capturing them would mean building
the first controller unthemed, snapshotting, then theming — a visible flash on
first mount to serve a case that barely occurs.

Note that `DayStyle.apply()` happens to rewrite most of these proxies, so most
keys do reset on the next mount; `routeColor`, `traversedRouteColor` and
`routeRestrictedAreaColor` are the ones the SDK never writes, so those persist.
Relying on that distinction would be fragile, so the public type documents the
simple rule instead: pass the palette you want and change values within it.

### The one documented platform difference

`maneuverTurnIcon`. The icon is two-tone: the turn being taken, plus
de-emphasised strokes for the roads that are not. iOS exposes those as
`ManeuverView.primaryColor` / `secondaryColor`, so we set only the first and a
fork still reads correctly. Android's equivalents are the theme attributes
`maneuverTurnIconColor` / `maneuverTurnIconShadowColor`, which cannot be set at
runtime — only a flat `imageTintList` can — so the whole icon takes the colour
there. Documented on the public type rather than papered over by flattening iOS
to match.

### Hex format is not symmetric — only 3- and 6-digit are portable

Caught before shipping, and the reason the public type warns about it: iOS's
parser reads 8-digit hex as `#RRGGBBAA`, while Android's `Color.parseColor`
reads it as `#AARRGGBB`. The same string produces different colours. `#RGB` and
`#RRGGBB` parse identically on both, and those are what the contract promises.

> **Resolved in 3.1.0.** Both platforms now read 8-digit hex as `#RRGGBBAA` and
> honour the alpha, so all three forms are portable. The write-up above is kept
> as the record of what v3 shipped with. Two things it did not catch, found
> while fixing this: `Color.parseColor` rejects 3-digit hex outright, so the
> `#RGB` form the contract promised never worked on Android at all; and iOS had
> a *third* parser for marker colours that truncated 8-digit input with
> `prefix(6)` and dropped the alpha. Each platform now has exactly one parser.

## iOS SPM integration — the part that took 9 builds

Verified end to end on 2026-09-14: `expo prebuild` → `pod install` → `xcodebuild`
→ launch on an iPhone 17 Pro simulator, `** BUILD SUCCEEDED **`, app runs.

Getting there produced seven defects. **Six were in this SPM wiring; exactly one
was in the API migration** (`Waypoint`, below). The wiring is genuinely
non-obvious, so the working shape is recorded here rather than left in
`ios/spm.rb` alone.

### Linking an SPM product needs three objects, not two

1. `XCRemoteSwiftPackageReference` on the project — *which package*
2. `XCSwiftPackageProductDependency` on the target — *which product*
3. `PBXBuildFile` with that `product_ref` in the Frameworks phase — *link it*

Xcode's UI creates all three. @rnmapbox/maps' helper — which this was modelled
on — creates only (1) and (2).

### …and the split across targets is asymmetric

| | Package ref | Product dependency | Link phase | Include path |
| --- | --- | --- | --- | --- |
| Pod target (`ExpoMapboxNavigationNative`) | yes | **no** | **no** | yes |
| Every user target | yes | yes | yes | — |

Both "no"s are load-bearing:

- **Product dependency on a static pod target archives the dependency into the
  `.a`.** With it attached, `libExpoMapboxNavigationNative.a` contained
  `MapboxMaps.o`, `MapboxNavigationCore.o`, `MapboxNavigationUIKit.o`,
  `MapboxDirections.o` and the wrappers — and the app linked the standalone
  objects too, producing hundreds of `duplicate symbol` errors. Removing only
  the link phase is **not** sufficient; the dependency itself pulls them in.
- The pod target needs the module only to *compile*, which the include path
  provides. The app does the single link.

### Every directly-imported product must be linked explicitly

SPM only lets a target import modules whose products it links. `MapboxMaps` and
`Turf` arrive transitively through `MapboxNavigationCore` — they resolve and
download, and are still not importable. Five products across three packages:
`MapboxNavigationCore`, `MapboxNavigationUIKit`, `MapboxDirections`,
`MapboxMaps`, `Turf`. Resolution succeeding is not linkage.

### `$(BUILT_PRODUCTS_DIR)` is the wrong variable

CocoaPods sets `CONFIGURATION_BUILD_DIR` per pod target, so
`BUILT_PRODUCTS_DIR` resolves to the pod's *own* subdirectory, not the shared
root where SPM writes `MapboxMaps.swiftmodule`. Use
`$(SYMROOT)/$(CONFIGURATION)$(EFFECTIVE_PLATFORM_NAME)`. The wrong value fails
silently — the setting is present and correct-looking and points nowhere useful.

### Static vs dynamic linkage is the variable that explains most of this

A source-only SPM product builds to `<root>/X.o` + `X.swiftmodule` for a static
pod, but to a framework in `<target>/PackageFrameworks/` under
`use_frameworks!`. Xcode only adds `-F …/PackageFrameworks` for the product
dependency, which is empty in the static case.

**Consumers sit on both sides of this line**, so the package must handle both:
an app that also pulls in @rnmapbox/maps gets dynamic frameworks (that package
forces them), while a default Expo app — including this repo's `example/` — gets
static pods. The config above is what works for static; it is inert under
dynamic linkage.

### Still unproven

- **Dynamic linkage untested.** Only static was exercised. The CocoaPods+SPM
  `MapboxMaps` collision (see the hazard section above) can only appear in a
  project that also has @rnmapbox/maps as a pod.
- **Build ordering.** With no product dependency on the pod target, nothing
  explicitly orders the SPM package builds before our pod compiles. It worked
  here; a failure would look like intermittent "no such module". If that
  surfaces, requiring `use_frameworks!` is the documented fallback.
- **Clean-build cost.** v3 ships as *source* over SPM, so a cold build compiles
  all of MapboxMaps and the Navigation SDK. Materially slower than v2's
  prebuilt pods — CI will notice.
- **Nothing past provider construction.** No access token was available, so
  routing, markers, puck, camera and leg advance have never actually run.

### Environment notes

- CocoaPods needs a UTF-8 locale; it aborts with an `Encoding::CompatibilityError`
  otherwise. `LANG=en_US.UTF-8`.
- No `~/.netrc` was needed — the Mapbox SPM repositories resolved without a
  `DOWNLOADS:READ` credential. (Android's Maven repo still needs
  `MAPBOX_DOWNLOADS_TOKEN`.)
- Expo raises the pod's deployment target to match `ExpoModulesCore` (16.4 here),
  above the podspec's 14.0.

## Missing access token now fails safe instead of crashing

`ApiConfiguration.default` calls `assertionFailure` when no token is found,
which **traps in Debug** and in Release silently yields an *empty* token so
every request fails with an opaque auth error. Neither is catchable, and v3
needs the token when the provider is *constructed* — earlier than v2, which
needed it at the first route request.

`MapboxNavigationSession.isConfigured` resolves the token exactly as the SDK
does (`MBXAccessToken`, legacy `MGLMapboxAccessToken`, then `UserDefaults`), and
`startNavigationIfReady` refuses to start without one, emitting
`MISSING_ACCESS_TOKEN` through `onError`. Confirmed on device: the app stays
alive on the screen that previously killed it. This is better than v2 behaviour,
not merely parity.

## Android: the Drop-In rewrite

`MapboxNavigationView.kt` went from 97 errors to a clean build by deleting
~340 lines of Drop-In code and assembling the UI ourselves.

### Dependency coordinates

v3 moved to the **`com.mapbox.navigationcore`** group. The old
`com.mapbox.navigation` group has no 3.x artifacts, so a version bump alone
fails to resolve.

| v2 | v3 |
| --- | --- |
| `com.mapbox.navigation:ui-dropin-ndk27` | **gone** — no Drop-In in v3 |
| `com.mapbox.navigation:ui-maps-ndk27` | `com.mapbox.navigationcore:ui-maps-ndk27` |
| — | `navigation-ndk27`, `ui-components-ndk27`, `ui-base-ndk27`, `voice-ndk27` |
| `com.mapbox.maps:android-ndk27:10.19.0` | `com.mapbox.maps:android-ndk27:11.30.1` |

**Every Mapbox coordinate must stay on the same variant line.** The plain and
`-ndk27` artifacts ship the *same classes*, so declaring `ui-base` (plain)
alongside `navigation-ndk27` put `base` and `base-ndk27` — and
`common`/`common-ndk27` — on the classpath together and the build died in
`:app:checkDebugDuplicateClasses` with hundreds of duplicate-class errors. Note
this passes `compileDebugKotlin`: duplicate classes are a packaging failure, so
only a full `assembleDebug` catches it.

`androidx.constraintlayout` must also be declared explicitly. `MapboxManeuverView`
extends `ConstraintLayout` and the SDK declares constraintlayout as
`implementation`, so it never reaches our *compile* classpath. Without it Kotlin
cannot resolve the widget's supertype and stops treating it as a `View`,
reporting "none of the addView candidates is applicable" — an error that points
nowhere near the cause.

### The UI is now ours to build

There is no Drop-In replacement. The UI is assembled from `ui-components`
widgets and bound to the navigator by the `ui-base` installer:

```kotlin
nav.installComponents(activity) {
  routeLine(mapView); routeArrow(mapView); navigationCamera(mapView) { … }
  locationPuck(mapView)
  maneuver(maneuverView); audioGuidanceButton(soundButton)
}
```

**`tripProgress` and `speedInfo` are deliberately not installed.** Under Drop-In
they sat inside Mapbox's own styled container; standing alone over the map,
`MapboxTripProgressView` and `MapboxSpeedInfoView` render as an unstyled light
bar pinned to the screen edge, and neither exposes enough theming to blend into
a host app (`MapboxStyleTripProgressView` only takes `@StyleRes` text
appearances). Trip progress is already available — and themeable — through this
package's own JS `bottomSheet` overlay, which is what consumers actually ship.
`showsTripProgress` / `showsSpeedLimits` therefore have no effect on Android and
are logged once rather than silently ignored.

The chrome layout is ours too, and the first attempt got it wrong: the sound
button was pinned under a fixed 96dp *top* margin, which collided with the
maneuver banner because the banner's height varies with the instruction. Both
small controls are now anchored to the **bottom**, which needs no guess about
the banner's height.

Every installer is `@ExperimentalPreviewMapboxNavigationAPI`, so this UI layer
rests on a preview API and may shift between v3 releases. Consumers should know.

Two lifecycle details that the SDK's activity-oriented examples do not hit:

- `installComponents` returns **Unit** and ties teardown to the lifecycle
  owner's `ON_DESTROY`. A React Native view unmounts while its host activity
  lives on, so the components would outlive it — the individual installers
  return `Installation`, so we keep those and detach at unmount.
- The navigator must exist **before** the UI is built, because
  `installComponents` binds to a live `MapboxNavigation`.

### Deleted, not ported

Much of the old file existed only to drive or fight Drop-In:

| Deleted | Why |
| --- | --- |
| `mapViewObserver` | no SDK-chosen MapView to observe |
| `TextureMapViewBinder` | `MapInitOptions(textureView = true)` instead |
| SurfaceView z-order + texture priming | no SurfaceView to fight |
| `requestDropInCameraMode` | reflected over six guessed method names on Drop-In's opaque `api`; v3 gives a real `NavigationCamera` |
| `scheduleLayoutNudges`, `hideNativeBottomPanelIfRequested`, `hideViewsByClassNameHints` | suppressed Drop-In chrome we no longer have |

### The route flow collapses

v2 ran `api.startDestinationPreview` → `api.startRoutePreview` →
`api.startActiveGuidance`, plus a fallback for an empty preview. None exist in
v3, and the preview was never user-facing here — the old code walked straight
through it. So:

```kotlin
nav.requestRoutes(routeOptions, callback)   // unchanged from v2
nav.setNavigationRoutes(routes)
nav.startTripSession()                      // or startReplayTripSession()
```

`shouldSimulateRoute` is no longer a toggle (`api.routeReplayEnabled`): it
selects the session entry point, with events pushed onto `mapboxReplayer` via
`ReplayRouteMapper`.

`startOrigin` stays optional, but Drop-In used to source the device location
itself. v3 leaves that to the host, so an omitted origin now runs free drive
until the first fix arrives and then routes.

### Verified API changes

| v2 | v3 |
| --- | --- |
| `RouterOrigin` | plain `String` |
| `android.location.Location` | `com.mapbox.common.location.Location` — `bearing`/`speed`/`altitude` nullable, `accuracy` → `horizontalAccuracy` |
| `LocationPuck2D(topImage: Drawable)` | `ImageHolder`; `ImageHolder.from` takes a resource id, `Bitmap` or `Image` — **not** a `Drawable` |
| `ui.voice.api.MapboxAudioGuidance` | `voice.api.MapboxAudioGuidance` (package move only) |
| `viewAnnotationOptions { geometry(p); offsetY(y); selected(b) }` | `annotatedFeature(AnnotatedFeature.valueOf(p))`, `annotationAnchor { }`, `priority(n)` |

Unchanged and reusable: `MapboxNavigationApp.current()`, `requestRoutes`,
`NavigationRouterCallback`, and every observer.

## Map styles, including Standard / 3D

**Yes, Standard and Standard-Satellite work** — pass them through `mapStyleUri`,
`mapStyleUriDay` or `mapStyleUriNight`:

```tsx
<MapboxNavigationView mapStyleUriDay='mapbox://styles/mapbox/standard' … />
```

Verified on the Android emulator with `mapbox://styles/mapbox/standard`: 3D
building extrusions, landmarks, POI icons, and the route line composing
correctly with them.

Nothing had to be wired for it, and the reason is worth recording. Standard
styles use *slots* to decide layer order, and both SDKs already put the
navigation layers in the `middle` slot — so the route line and turn arrow draw
above the basemap but below labels, POIs and 3D extrusions:

- iOS: `RouteLineStyleContent` applies `.slot(.middle)`
  (`MapboxNavigationCore/Map/Style/RouteLineMapFeatures.swift`).
- Android: `MapboxRouteLineViewOptions.Builder` defaults `slotName` to
  `"middle"` — confirmed by disassembling the builder's constructor.

Two wrinkles this uncovered, both now fixed:

### The default Android style had regressed

v2 never set a style: Drop-In did, and it used Mapbox's navigation styles. The
first cut of this migration fell back to `Style.MAPBOX_STREETS`, silently
changing the default look of every Android consumer to plain streets. It now
defaults to `navigation-day-v1` / `navigation-night-v1`, matching v2 and coming
closer to iOS, whose SDK default is `mapbox-dash/standard-navigation`.

### `uiTheme` and the style props did nothing on Android after mount

`setMapStyleUri`, `setMapStyleUriDay`, `setMapStyleUriNight` and `setUiTheme`
all called `applyUiVisibility()`, which only touches widget visibility — so a
style change after mount was dropped, and `uiTheme` never selected between the
day and night props at all. `resolveStyleUri()` now resolves against the theme
(`dark`/`night`, `light`/`day`, or the Android night-mode configuration for
`system`), and `applyMapStyle()` loads it onto a live map. Automatic
sunrise/sunset switching remains iOS-only — v3 Android ships no `StyleManager`
equivalent.

## Props that were accepted and ignored

A prop that is stored and never read is the failure mode this migration was
most likely to produce, and greping for declarations is not enough to catch it.
Counting *reads* of each private field found ten on Android and one on iOS. All
are now either wired or reported at runtime; the check is part of the
verification steps below and currently reports none on either platform.

| Prop | Was | Now |
| --- | --- | --- |
| `distanceUnit` | stored on the Android view **and** module, applied nowhere — the same app showed "200 m" on iOS and "100 ft" on Android | applied through `DistanceFormatterOptions` |
| `routeLineTracksTraversal` | ignored on Android | `MapboxRouteLineApiOptions.vanishingRouteLineEnabled` |
| `showsEndOfRouteFeedback` | ignored on **iOS** — the feedback card showed regardless | assigned on `NavigationViewController` |
| `annotatesIntersectionsAlongRoute`, `usesNightStyleWhileInTunnel`, `showsContinuousAlternatives`, `nativeFloatingButtons.*` | silently ignored on Android | logged once as unsupported |
| `warnedDropInCameraOverride` | dead flag from the Drop-In era | removed |

### `distanceUnit` needed two fixes, not one

Setting `NavigationOptions.distanceFormatterOptions` was not enough: the
maneuver banner still read "50 ft". `ManeuverConfig` carries its *own*
`distanceFormatterOptions` and builds them from the device locale rather than
reading the ones on `NavigationOptions`, so the installer block has to be given
them too. Both are set now — `NavigationOptions` for voice instructions and
anything else formatting from it, `ManeuverConfig` for the banner.

Changing `distanceUnit` mid-trip is the one case that cannot be honoured
immediately: Mapbox bakes the formatter into the process-wide
`NavigationOptions`, and replacing those means calling
`MapboxNavigationApp.setup` again, which recreates `MapboxNavigation` and would
drop the session. It is re-applied when no session is running and logged as
deferred when one is.

## Routing options and Standard-style configuration (3.0.0)

Four props added, symmetric on both platforms, all verified on device.

| Prop | iOS | Android |
| --- | --- | --- |
| `routeProfile` | `NavigationRouteOptions(waypoints:profileIdentifier:)` | `RouteOptions.Builder.profile(DirectionsCriteria.PROFILE_*)` |
| `routeExclusions.roadClasses` | `RouteOptions.roadClassesToAvoid` (`RoadClasses` option set) | `Exclude.builder().criteria(...)` |
| `routeExclusions.locations` | `RouteOptions.excludedLocations` | `Exclude.builder().points(...)` |
| `vehicle` | `maximumHeight` / `maximumWidth` / `maximumWeight` (`Measurement`) | `maxHeight` / `maxWidth` / `maxWeight` (`Double`) |
| `mapStyleConfig` | `MapboxMap.setStyleImportConfigProperty(for:config:value:)` | `Style.setStyleImportConfigProperty(...)` |

The three routing props change the *request*, so their setters clear the
"already requested" latch and re-request — which is what `routeAlternatives`
already did on Android. iOS needed a new `restartRouteRequestIfNeeded`, because
`startNavigationIfReady` deliberately refuses to act once a controller exists
and so could not express "the profile changed, the current route is stale".

### `excludedLocations` is behind an SPI on iOS

`RouteOptions.excludedLocations` is `@_spi(ExperimentalMapboxAPI)` — Mapbox
marks it a beta Directions feature "subject to change". Reaching it needs
`@_spi(ExperimentalMapboxAPI) import MapboxDirections`, which is the one place
this package depends on a non-public SDK symbol. The trade is deliberate: a
future SDK release that renames or drops it breaks this build at compile time,
loudly, rather than failing silently at runtime. Everything else used from that
module is ordinary public API. The earlier claim in this document that
`excludedLocations` was plain public API was wrong.

### The Directions API rejects driving-only parameters, it does not ignore them

The most important thing device testing caught. Combining `routeProfile:
'walking'` with `vehicle` or most `routeExclusions.roadClasses` failed the whole
route request — the app showed `ROUTE_ERROR: the server returned an empty
response` and no map at all.

Probed directly against the API to pin it down:

| Request | Result |
| --- | --- |
| `walking` (clean) | `200 Ok` |
| `walking` + `max_height` | **`422 InvalidInput`** — "Invalid query param" |
| `cycling` + `max_height` | **`422 InvalidInput`** |
| `walking` + `exclude=toll` | **`422 InvalidInput`** — "exclude value must be one of: ferry, cash_only_tolls, border_crossing, country_border, state_border" |

So the wrapper now filters per profile rather than passing everything through:
`vehicle` is dropped on non-driving profiles, and so is any road class other
than `ferry` / `cashOnlyTolls`, plus `routeExclusions.locations`. Each drop is
reported through `onError` (`VEHICLE_NOT_SUPPORTED_BY_PROFILE`,
`ROAD_CLASS_NOT_SUPPORTED_BY_PROFILE`,
`EXCLUDED_LOCATIONS_NOT_SUPPORTED_BY_PROFILE`) so the consumer learns why,
rather than the request quietly failing. Verified on both platforms: walking +
truck + toll/ferry/motorway now returns a route *and* reports the drop.

### `mapStyleConfig` had a subscription-ordering bug

Standard styles expose their basemap as a configurable style *import*, and
config writes are ignored while a style is still loading. The first cut
subscribed to `onStyleLoaded` *after* calling `applyMapStyle`, which triggers
the load — and `onStyleLoaded` does not replay for a style that has already
loaded, so the subscription often missed it. The only attempt that ran was the
eager one, before any style existed, where the map reports no imports at all.
That produced a false `MAP_STYLE_CONFIG_UNSUPPORTED`, which then latched and hid
the later success. Three fixes:

- subscribe before triggering the load;
- only judge support *after* a style has loaded, never from the eager attempt;
- clear the one-shot latch when `mapStyleConfig` changes, since a new config may
  be supported where the last was not.

Also, the import id is not reliably `"basemap"` — that is a convention used by
styles that import Standard, not a guarantee — so the ids are discovered from
`styleImports` and each config value is written to whichever import accepts it.

Verified the honest way: with `lightPreset: 'day'` forced at 19:04, after
sunset, the map renders day-lit. The SDK's own time-of-day switching would have
kept it night, so the preset is demonstrably ours. Confirmed with Standard both
as the root style and as an import.

## v3 capabilities still not wired

Deliberately out of scope for 3.0.0, listed so the decision is explicit rather
than an oversight. None of them is a regression — v2 had none of them either:

`excludedLocations`, routing profile selection and Standard-style config are all
wired now — see the section above. What remains:

| Capability | Why not in 3.0.0 |
| --- | --- |
| EV routing + charging stations | Large surface (`EvStateOfCharge`, charging-station retention, an EV-specific request shape), and not verifiable here: it needs EV-enabled routing data, which neither a simulator nor an emulator can exercise. Shipping it unverified would be worse than not shipping it. |
| Road cameras, pole-style route annotations | Traffic signals, yield/stop signs, lane restrictions. iOS keeps these in a separate `MapboxNavigationCppRoadCameras` SPM product that this package does not link, so wiring them means adding a product to the SPM plumbing; the Android counterpart was not located. |
| Predictive-cache *tilesets* | `PredictiveCacheMapsOptions.tilesets` + `createTilesetsMapController`. The map/nav predictive cache itself **is** carried through on iOS; only the tileset-descriptor variant is missing, and it has no visible behaviour to verify against. |
| Route callouts | iOS only, and behind `@_spi(ExperimentalMapboxAPI)` — the same SPI hazard accepted once for `excludedLocations`, but here for a whole view-provider protocol, with no Android counterpart. |
| `NavigationOptions.timeFormatter` | **Does not exist.** An earlier revision of this document listed it from release notes; grepping iOS 3.30.1 finds no such property. Nothing to wire. |

## Behaviour changes owed to CHANGELOG (3.0.0)

Five, all deliberate:

1. **`onDestinationPreview` no longer fires on either platform.** It mirrored a
   preview phase that only existed inside Android's Drop-In, and never fired on
   iOS at all. The JS helper is kept and marked `@deprecated` so existing
   subscriptions do not break.
   *(`onDestinationChanged` is unaffected — it comes from the `setDestination`
   prop setter, not Drop-In.)*
2. **Per-state location pucks collapse to one on Android.**
   `LocationPuckOptions` still carries five states, but v3's `locationPuck`
   component takes a single `LocationPuck`. The prop shape is unchanged, so
   callers keep working, but only the `activeNavigation`/`default` entry has a
   visible effect.
3. **`cameraPitch` / `cameraZoom` now take effect in following mode on
   Android.** They previously wrote to `mapboxMap.setCamera(...)`, which the
   navigation camera overwrote every frame — the old code logged a warning
   admitting it. v3's `*PropertyOverride` API is the supported mechanism, so
   they now hold. A fix, but a behaviour change.
4. **`showsWayNameLabel`, `showsReportFeedback`, `showsEndOfRouteFeedback`,
   `showsTripProgress` and `showsSpeedLimits` have no effect on Android.** v3
   ships no widget to host the first three; the last two have widgets that
   cannot be themed to fit a host app (see "The UI is now ours to build").
   Logged once at runtime rather than silently ignored.
5. **The route line, turn arrow, maneuver banner and floating buttons are
   themeable via the new `colors` prop.** Additive — omitting it keeps every
   Mapbox default, so no existing consumer changes appearance. See
   "Theming: the `colors` prop".

## Sequence

1. **iOS packaging spike** — port rnmapbox's `_add_spm_to_target` approach;
   prove the CocoaPods + SPM duplication is resolvable in an app that also has
   @rnmapbox/maps as a pod. This is the go/no-go gate for the whole migration.
2. iOS API migration (`MapboxNavigationView.swift`, 1,589 lines — edited, not
   rewritten).
3. Android: nav `2.21.0` → `3.30.x`, Maps `11.30.x`, and rebuild the Drop-In
   layer in `MapboxNavigationView.kt` (2,027 lines). Largest single item.
4. Wire the new v3 capability above.
5. Release `3.0.0`; downstream apps can then drop the three `10.19.0` pins
   described under "Why we are doing this".

## Verification plan

Neither platform is compile-checkable from this repo (no `android/gradlew`, no
Pods installed), so per `mapbox-v2-api-verification-method`:

- iOS: `swiftc -parse` for syntax; grep the tagged SDK source for API shape.
- Android: `javap` against the AARs cached in
  `~/.gradle/caches/modules-2/files-2.1/com.mapbox.*`; Kotlin parameter names
  come from grepping `@Metadata` strings, not `javap`.
- Real verification is a device build in a consuming app, which is also the
  only way to test the Expo Go / prebuild / EAS paths in the goal.

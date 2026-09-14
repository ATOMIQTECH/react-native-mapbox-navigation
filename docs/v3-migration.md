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
SDK `11.16.2`, but a consuming app pins it **back down** to
`10.19.0` in three places to match this package:

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

**Pre-existing bug found while writing this contract:**
`addDestinationPreviewListener` and `addDestinationChangedListener` are
exported and documented in JS and are emitted on Android, but
`onDestinationPreview` / `onDestinationChanged` appear **nowhere** in `ios/`.
They have never fired on iOS. Both come from the Android Drop-In preview flow
(`view.api.startDestinationPreview` / `startRoutePreview`) — the exact layer
being rebuilt for v3 — so the iOS side should be implemented as part of that
work. Tracked as `KNOWN_GAPS` in `scripts/verify-parity.mjs`.

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
a consuming app gets dynamic frameworks (@rnmapbox/maps forces them), while a
default Expo app — including this repo's `example/` — gets static pods. The
config above is what works for static; it is inert under dynamic linkage.

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

## Sequence

1. **iOS packaging spike** — port rnmapbox's `_add_spm_to_target` approach;
   prove the CocoaPods + SPM duplication is resolvable in a consuming app.
   This is the go/no-go gate for the whole migration.
2. iOS API migration (`MapboxNavigationView.swift`, 1,589 lines — edited, not
   rewritten).
3. Android: nav `2.21.0` → `3.30.x`, Maps `11.30.x`, and rebuild the Drop-In
   layer in `MapboxNavigationView.kt` (2,027 lines). Largest single item.
4. Wire the new v3 capability above.
5. Release `3.0.0`; delete a consuming app's three `10.19.0` pins in the same change.

## Verification plan

Neither platform is compile-checkable from this repo (no `android/gradlew`, no
Pods installed), so per `mapbox-v2-api-verification-method`:

- iOS: `swiftc -parse` for syntax; grep the tagged SDK source for API shape.
- Android: `javap` against the AARs cached in
  `~/.gradle/caches/modules-2/files-2.1/com.mapbox.*`; Kotlin parameter names
  come from grepping `@Metadata` strings, not `javap`.
- Real verification is a device build in a consuming app, which is also the
  only way to test the Expo Go / prebuild / EAS paths in the goal.

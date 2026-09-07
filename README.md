# @atomiqlab/react-native-mapbox-navigation

Embedded Mapbox turn-by-turn navigation for Expo and React Native on iOS and Android.

This package is `2.x` and embedded-only. Full-screen `startNavigation(...)` flows were removed. The main entry point is `MapboxNavigationView`.

## What You Get

- Native Mapbox navigation UI embedded in a React Native view
- Expo config plugin for Mapbox token wiring and required native permissions
- Optional React overlay bottom sheet
- Optional React overlay floating buttons
- Per-button control over the built-in native floating buttons
- Package-managed end-of-route rating modal, or a custom replacement
- Runtime helpers (`setMuted`, `stopNavigation`, etc.) and event listeners

## Installation

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

This is a native module. After installing or changing config, rebuild the native app (`npx expo prebuild`, `npx expo run:ios`, `npx expo run:android`, or your normal native build flow).

## Required Mapbox Tokens

The config plugin validates tokens during prebuild.

- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN`
  A Mapbox public token starting with `pk.`
- `MAPBOX_DOWNLOADS_TOKEN`
  A Mapbox secret token starting with `sk.` and including `DOWNLOADS:READ`

The plugin also accepts these fallbacks:

- `MAPBOX_PUBLIC_TOKEN`
- `expo.extra.mapboxPublicToken`
- `expo.extra.expoPublicMapboxAccessToken`
- `expo.extra.mapboxAccessToken`
- `expo.extra.mapboxDownloadsToken`

Example `.env`:

```bash
EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN=pk.your_public_token
MAPBOX_DOWNLOADS_TOKEN=sk.your_secret_token
```

## Expo Config Plugin

The package ships with an Expo config plugin that:

- injects the Mapbox Maven repository on Android
- writes `mapbox_access_token` into Android resources
- sets `MBXAccessToken` in `Info.plist`
- adds required Android location/foreground-service permissions
- adds iOS location usage strings and `location` / `audio` background modes

If you manage plugins explicitly, add the package to your app config:

```json
{
  "expo": {
    "plugins": ["@atomiqlab/react-native-mapbox-navigation"]
  }
}
```

### Plugin options

```json
{
  "expo": {
    "plugins": [
      [
        "@atomiqlab/react-native-mapbox-navigation",
        {
          "backgroundLocation": false,
          "backgroundAudio": true,
          "locationWhenInUsePermission": "We use your location for turn-by-turn navigation."
        }
      ]
    ]
  }
}
```

| Option | Default | Effect |
| --- | --- | --- |
| `backgroundLocation` | `false` | Adds `ACCESS_BACKGROUND_LOCATION` on Android. **Opt-in**: it triggers a Google Play policy review and a prominent-disclosure requirement, so it is no longer added automatically. |
| `backgroundAudio` | `true` | Keeps the iOS `audio` background mode so spoken guidance continues while backgrounded. Set `false` if you don't need it at App Store review. |
| `locationWhenInUsePermission` | package default | Overrides the iOS location usage description. |

## Minimal Usage

Request location permission in your app before enabling navigation. The view will emit `LOCATION_PERMISSION_REQUIRED` if mounted without permission.

```tsx
import * as Location from "expo-location";
import { useEffect, useState } from "react";
import {
  MapboxNavigationView,
  type Waypoint,
} from "@atomiqlab/react-native-mapbox-navigation";

const DESTINATION: Waypoint = {
  latitude: 37.7847,
  longitude: -122.4073,
  name: "Union Square",
};

export function EmbeddedNavigation() {
  const [granted, setGranted] = useState(false);
  const [origin, setOrigin] = useState<Waypoint | undefined>(undefined);

  useEffect(() => {
    void (async () => {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (!permission.granted) {
        return;
      }

      const position = await Location.getCurrentPositionAsync({});
      setOrigin({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        name: "Current Location",
      });
      setGranted(true);
    })();
  }, []);

  return (
    <MapboxNavigationView
      enabled={granted}
      style={{ flex: 1 }}
      startOrigin={origin}
      destination={DESTINATION}
      shouldSimulateRoute
    />
  );
}
```

## Platform Behavior

- Android can start without `startOrigin`; it falls back to the device location.
- iOS currently requires `startOrigin` to begin routing.
- Only one embedded navigation session should be active at a time.
- The package uses native UI for the main map/navigation chrome and React overlays for custom controls.

## Display-Only Navigation Markers

`navigationMarkers` renders lightweight native pin annotations directly on the embedded navigation map.
These are **display-only** — they do not affect routing. Use `waypoints` for intermediate route stops.

```tsx
<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  startOrigin={origin}
  destination={destination}
  navigationMarkers={[
    {
      id: "pickup-1",
      latitude: 37.7858,
      longitude: -122.4064,
      label: "Pickup – Alice",
      glyph: "P",
      badge: "2",
      variant: "primary",
      size: "large",
      selected: true,
    },
    {
      id: "dropoff-1",
      latitude: 37.7901,
      longitude: -122.4019,
      label: "Dropoff – Alice",
      glyph: "D",
      variant: "success",
    },
    {
      id: "custom-stop",
      latitude: 37.788,
      longitude: -122.408,
      label: "Custom Stop",
      glyph: "★",
      // Fully custom color — overrides `variant`
      color: "#7C3AED",
      badgeColor: "#5B21B6",
      opacity: 0.9,
      markerStyle: "dot",   // simple circle, no tail
      size: "small",
    },
  ]}
/>
```

### Marker fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `id` | `string` | **required** | Stable key — used to update/remove markers in place |
| `latitude` | `number` | **required** | WGS84 latitude |
| `longitude` | `number` | **required** | WGS84 longitude |
| `label` | `string` | — | Accessibility label and debug description |
| `glyph` | `string` | `"•"` | Short text rendered inside the bubble (max 2 chars) |
| `badge` | `string` | — | Badge text in the upper-right corner (max 3 chars) |
| `variant` | `NavigationMarkerVariant` | `"default"` | Semantic color preset |
| `color` | `string` | — | Custom fill hex (e.g. `"#7C3AED"`). Overrides `variant` |
| `badgeColor` | `string` | — | Custom badge hex. Falls back to a darker shade of `color`/`variant` |
| `opacity` | `number` | auto | Marker opacity 0..1. Overrides the `variant`/`selected` default |
| `size` | `NavigationMarkerSize` | `"medium"` | Size preset |
| `markerStyle` | `NavigationMarkerStyle` | `"pin"` | `"pin"` = bubble + tail, `"dot"` = circle only |
| `showTail` | `boolean` | `true` | Show the pointer tail (only applies to `"pin"` style) |
| `selected` | `boolean` | auto | Forwarded to the native annotation `selected` state |
| `allowOverlap` | `boolean` | `true` | Allow overlap with other annotations |
| `anchorOffsetY` | `number` | auto | Custom Y-axis pixel offset from the anchor point |

### Variant presets

| Variant | Fill color | Badge color | Default opacity |
|---------|-----------|-------------|-----------------|
| `"default"` | `#1F2937` | `#111827` | 0.92 (unselected) / 1 |
| `"primary"` | `#2563EB` | `#1D4ED8` | 1 |
| `"success"` | `#15803D` | `#166534` | 1 |
| `"warning"` | `#C2410C` | `#9A3412` | 1 |
| `"danger"` | `#B91C1C` | `#991B1B` | 1 |
| `"muted"` | `#475569` | `#334155` | 0.72 |

Use `color` + `badgeColor` + `opacity` together for fully custom branding without touching `variant`.

## Custom Navigation Pointer (Location Puck)

Replace the standard blue location puck with a 3D model, your own 2D images, a
recolored default puck, or nothing at all — via the `locationPuck` prop.

### 3D model

```tsx
<MapboxNavigationView
  enabled
  destination={{ latitude: 37.7749, longitude: -122.4194 }}
  locationPuck={{
    type: '3d',
    modelUri: require('./assets/car.glb'),
    scale: 12,
    rotation: [0, 0, 180],
  }}
/>
```

To `require()` a `.glb`/`.gltf` file, register the extension with Metro:

```js
// metro.config.js
const config = getDefaultConfig(__dirname)
config.resolver.assetExts.push('glb', 'gltf')
module.exports = config
```

A remote URL or a native asset path works too, and needs no Metro change:

```tsx
locationPuck={{ type: '3d', modelUri: 'https://cdn.example.com/car.glb' }}
locationPuck={{ type: '3d', modelUri: { uri: 'asset://car.glb' } }}
```

Remote models are downloaded once and cached on disk on both platforms.

> **On `require()` and release builds.** A `require()`'d model resolves through
> React Native's asset registry, which returns a Metro packager URL in
> development and a bundled asset reference in release builds. The dev path
> works out of the box; for production builds the most predictable options are a
> remote `https://` URL or a model placed in the native bundle and referenced
> explicitly (`asset://car.glb` on Android, a bundle resource name on iOS).
> Verify your release build before shipping a `require()`'d model.

### 2D images

```tsx
locationPuck={{
  type: '2d',
  bearingImage: require('./assets/arrow.png'),
  shadowImage: require('./assets/shadow.png'),
  scale: 1.2,
}}
```

`bearingImage` rotates to follow the course; `topImage` stays unrotated and
`shadowImage` draws underneath.

### Tinted default puck

Keeps Mapbox's puck shape and just recolors it — no assets required.

```tsx
locationPuck={{ type: 'tinted', color: '#2563EB', haloColor: '#FFFFFF' }}
```

### Hiding the pointer

For apps that draw their own vehicle marker:

```tsx
locationPuck={{ type: 'none' }}
```

Location updates keep flowing; only the pointer is hidden.

### Per-navigation-state pointers

Pass an object keyed by state instead of a single appearance. `default` covers
every state you don't override.

```tsx
locationPuck={{
  default: { type: 'tinted', color: '#2563EB' },
  activeNavigation: {
    type: '3d',
    modelUri: require('./assets/car.glb'),
    scale: 12,
  },
  arrival: { type: '2d', bearingImage: require('./assets/pin.png') },
}}
```

States: `default`, `freeDrive`, `destinationPreview`, `routePreview`,
`activeNavigation`, `arrival`, `idle`.

Android maps these onto Mapbox's native `LocationPuckOptions`. iOS has no
per-state puck API, so the pointer is swapped as the session transitions —
in practice `default`, `activeNavigation`, and `arrival` are the ones that
matter there.

### Appearance fields

| Field | Types | Notes |
| --- | --- | --- |
| `modelUri` | `3d` | `require()`, `https://` URL, or `{ uri }`. Required. |
| `topImage` / `bearingImage` / `shadowImage` | `2d` | At least one required. |
| `color` / `haloColor` / `bearingColor` | `tinted` | Hex strings (`#RGB`, `#RRGGBB`, `#RRGGBBAA`). |
| `scale` | `3d`, `2d`, `tinted` | Number, or `[x, y, z]` for `3d`. |
| `scaleExpression` | `3d`, `2d` | Mapbox style expression as a JSON string. Overrides `scale`. |
| `rotation` | `3d` | `[x, y, z]` degrees. Corrects a model's authored axis. |
| `translation` | `3d` | `[x, y, z]` metres. **Android only** — the iOS puck API has no equivalent. |
| `opacity` | all | `0`–`1`. |

Invalid values degrade to the SDK default rather than throwing — a 3D model that
fails to download, or a `2d` puck whose images all fail to load, leaves the
standard puck in place and logs a warning natively.

### Sizing a 3D model

Model scale is interpreted in the glTF's own units, and differs between
platforms and between models, so expect to tune `scale` per model. For
zoom-dependent sizing use `scaleExpression`, which behaves consistently on both
platforms:

```tsx
locationPuck={{
  type: '3d',
  modelUri: require('./assets/car.glb'),
  scaleExpression: JSON.stringify([
    'interpolate', ['linear'], ['zoom'],
    14, [8, 8, 8],
    18, [24, 24, 24],
  ]),
}}
```

## Overlay Bottom Sheet

`bottomSheet` is overlay-only. The package renders a React layer above the native navigation UI.

```tsx
<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  startOrigin={origin}
  destination={destination}
  bottomSheet={{
    enabled: true,
    mode: "overlay",
    initialState: "hidden",
    collapsedHeight: 120,
    expandedHeight: 320,
    collapsedBottomOffset: 24,
    showHandle: true,
    colorMode: "dark",
    builtInQuickActions: ["overview", "recenter", "toggleMute", "stop"],
  }}
  bottomSheetComponent={YourBottomSheet}
/>
```

Supported bottom-sheet entry points:

- `bottomSheetContent`
- `renderBottomSheet(context)`
- `bottomSheetComponent`

`BottomSheetRenderContext` includes:

- `state`
- `hidden`
- `expanded`
- `show(state?)`
- `hide()`
- `expand()`
- `collapse()`
- `toggle()`
- `bannerInstruction`
- `routeProgress`
- `location`
- `stopNavigation()`
- `emitAction(actionId)`

State behavior:

- Android uses `collapsed` and `expanded`
- iOS maps collapsed behavior to `hidden` / `expanded`

## Custom Floating Buttons

Custom floating buttons are independent from the bottom sheet. You can render them with or without `bottomSheet`.

```tsx
import {
  MapboxNavigationFloatingButton,
  MapboxNavigationFloatingButtonsStack,
  type FloatingButtonsRenderContext,
} from "@atomiqlab/react-native-mapbox-navigation";

function ActionRail({
  stopNavigation,
  emitAction,
}: FloatingButtonsRenderContext) {
  return (
    <MapboxNavigationFloatingButtonsStack>
      <MapboxNavigationFloatingButton
        accessibilityLabel="Open chat"
        onPress={() => emitAction("chat")}
      >
        CHAT
      </MapboxNavigationFloatingButton>
      <MapboxNavigationFloatingButton
        accessibilityLabel="Stop navigation"
        onPress={() => {
          void stopNavigation();
        }}
      >
        END
      </MapboxNavigationFloatingButton>
    </MapboxNavigationFloatingButtonsStack>
  );
}

<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  startOrigin={origin}
  destination={destination}
  floatingButtonsComponent={ActionRail}
/>
```

Supported floating-button entry points:

- `floatingButtons`
- `renderFloatingButtons(context)`
- `floatingButtonsComponent`

`FloatingButtonsRenderContext` includes:

- `show(state?)`
- `hide()`
- `expand()`
- `collapse()`
- `toggle()`
- `bannerInstruction`
- `routeProgress`
- `location`
- `stopNavigation()`
- `emitAction(actionId)`

By default, custom floating buttons automatically hide after arrival. Set `hideFloatingButtonsOnArrival={false}` if you need them to remain visible.

The default package helpers:

- `MapboxNavigationFloatingButton`
- `MapboxNavigationFloatingButtonsStack`

apply the same rounded dark rail styling used by the package examples.

## Native Floating Buttons

Use `nativeFloatingButtons` to control built-in native map buttons without removing your custom React buttons.

```tsx
<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  startOrigin={origin}
  destination={destination}
  nativeFloatingButtons={{
    showCameraModeButton: false,
    showCompassButton: false,
  }}
  floatingButtonsComponent={ActionRail}
/>
```

Supported keys:

- `showOverviewButton` (iOS)
- `showAudioGuidanceButton` (iOS + Android)
- `showFeedbackButton` (iOS)
- `showCameraModeButton` (Android)
- `showRecenterButton` (Android)
- `showCompassButton` (Android action button)

## End-of-Route Feedback

The library can show a package-managed rating modal when the trip finishes.

```tsx
<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  startOrigin={origin}
  destination={destination}
  showsEndOfRouteFeedback
  onEndOfRouteFeedbackSubmit={({ rating, arrival }) => {
    console.log("Trip rating:", rating, arrival?.name);
  }}
/>
```

You can also replace the default modal with your own UI:

- `renderEndOfRouteFeedback(context)`
- `endOfRouteFeedbackComponent`

`EndOfRouteFeedbackRenderContext` includes:

- `arrival`
- `dismiss()`
- `submitRating(rating)`
- `stopNavigation()`

Important:

- `showsEndOfRouteFeedback` controls the package React modal, not a native Mapbox rating flow
- a custom end-of-route renderer is automatically treated as enabled unless you explicitly set `showsEndOfRouteFeedback={false}`

## Runtime Functions

```ts
import {
  advanceToNextWaypoint,
  getNavigationSettings,
  resumeCameraFollowing,
  setDistanceUnit,
  setLanguage,
  setMuted,
  setVoiceVolume,
  stopNavigation,
} from "@atomiqlab/react-native-mapbox-navigation";
```

Available functions:

- `setMuted(muted: boolean): Promise<void>`
- `setVoiceVolume(volume: number): Promise<void>`
- `setDistanceUnit(unit: "metric" | "imperial"): Promise<void>`
- `setLanguage(language: string): Promise<void>`
- `getNavigationSettings(): Promise<NavigationSettings>`
- `stopNavigation(): Promise<boolean>`
- `resumeCameraFollowing(): Promise<boolean>`
- `advanceToNextWaypoint(): Promise<boolean>`

`getNavigationSettings().isNavigating` now reflects real embedded session state.
`setLanguage()` affects routes requested *after* the call on iOS, since spoken
instruction language is fixed per route request there.

### Advancing a waypoint on a business event

On a multi-stop route the SDK normally advances a leg when the driver physically
arrives. When a stop is instead completed by an app event — a passenger marked
picked up, a parcel handed over — advance it explicitly:

```tsx
await advanceToNextWaypoint()
```

The native SDK removes the completed leg, recomputes the ETA and re-focuses the
next waypoint, all without tearing down the session. Resolves `false` when there
is no active session or the route is already on its final leg.

`RouteProgress.legIndex` tells you which leg is active, so leg transitions are
observable from `onRouteProgressChange` without extra events.

## Component Callbacks

`MapboxNavigationView` supports these callbacks:

- `onLocationChange(location)`
- `onRouteProgressChange(progress)`
- `onRouteChange(event)`
- `onJourneyDataChange(data)`
- `onBannerInstruction(instruction)`
- `onArrive(event)` — final destination only
- `onWaypointArrive(event)` — an intermediate `waypoints` stop
- `onOffRoute(event)` — user left or rejoined the route
- `onCameraFollowingStateChange(state)`
- `onCancelNavigation()`
- `onError(error)`
- `onOverlayBottomSheetActionPress(event)`
- `onEndOfRouteFeedbackSubmit(event)`
- `onDestinationPreview(event)` Android-only
- `onDestinationChanged(event)` Android-only

### Multi-stop routes

`onArrive` fires **only** at the final destination; each intermediate
`waypoints` stop fires `onWaypointArrive`. Both payloads carry `index`,
`isFinalDestination` and `remainingWaypoints`.

```tsx
<MapboxNavigationView
  waypoints={[stopA, stopB]}
  destination={finalStop}
  onWaypointArrive={({ index, remainingWaypoints }) => {
    console.log(`Reached stop ${index}, ${remainingWaypoints} to go`)
  }}
  onArrive={() => console.log('Trip complete')}
/>
```

### Event throughput

Mapbox reports progress on every location fix, so `onLocationChange`,
`onRouteProgressChange` and `onJourneyDataChange` each cross the native bridge
several times a second. If your UI only needs periodic updates, throttle them
natively — the payload is then never serialized or sent at all:

```tsx
<MapboxNavigationView eventThrottleMs={500} ... />
```

Discrete events (`onArrive`, `onWaypointArrive`, `onBannerInstruction`,
`onError`) are never throttled. Default is `0` (every update), clamped to
`0`–`10000`.

## Listener Helpers

You can also subscribe outside the component tree:

```ts
import {
  addArriveListener,
  addBannerInstructionListener,
  addBottomSheetActionPressListener,
  addCameraFollowingStateChangeListener,
  addCancelNavigationListener,
  addDestinationChangedListener,
  addDestinationPreviewListener,
  addErrorListener,
  addJourneyDataChangeListener,
  addLocationChangeListener,
  addOffRouteListener,
  addRouteChangeListener,
  addRouteProgressChangeListener,
  addWaypointArriveListener,
} from "@atomiqlab/react-native-mapbox-navigation";
```

Each returns a subscription — call `.remove()` when done. Subscriptions are
reference-counted natively, so an event nobody subscribes to is never sent
across the bridge.

## SDK Coverage

What the package wraps today, and what it deliberately does not.

### Wrapped

| Area | Exposed as |
| --- | --- |
| Route request (origin, waypoints, alternatives, language, units) | `startOrigin`, `waypoints`, `destination`, `routeAlternatives`, `language`, `distanceUnit` |
| Turn-by-turn guidance + banner instructions | `onBannerInstruction`, `onRouteProgressChange`, `onJourneyDataChange` |
| Camera (follow / overview, pitch, zoom) | `cameraMode`, `cameraPitch`, `cameraZoom`, `resumeCameraFollowing()`, `onCameraFollowingStateChange` |
| Voice guidance (mute, volume) | `mute`, `voiceVolume`, `setMuted()`, `setVoiceVolume()` |
| Map styling (day/night, theme) | `mapStyleUri`, `mapStyleUriDay`, `mapStyleUriNight`, `uiTheme` |
| Location puck (2D / 3D / tinted / hidden / per-state) | `locationPuck` |
| Display-only map markers | `navigationMarkers` |
| Native UI visibility | `showsTripProgress`, `showsManeuverView`, `showsSpeedLimits`, `showsWayNameLabel`, `nativeFloatingButtons` |
| Route line traversal, tunnel styling, intersection annotations, continuous alternatives | `routeLineTracksTraversal`, `usesNightStyleWhileInTunnel`, `annotatesIntersectionsAlongRoute`, `showsContinuousAlternatives` (iOS) |
| Arrival, multi-stop waypoints, off-route | `onArrive`, `onWaypointArrive`, `onOffRoute` |
| Programmatic leg advance on multi-stop routes | `advanceToNextWaypoint()`, `RouteProgress.legIndex` |
| Map day/night tiles forced to match the app theme | `uiTheme` (drives `StyleManager` on iOS) |
| Route simulation | `shouldSimulateRoute` |
| Session control | `stopNavigation()`, `getNavigationSettings()` |

### Not wrapped yet

Deliberate gaps, roughly in order of how often they get asked for. Each is
additive — none require breaking changes.

| Capability | Platform support | Note |
| --- | --- | --- |
| Route line styling (color, width, traffic gradient) | Android `routeLineOptions`; iOS via `DayStyle`/`NightStyle` subclass | Most-requested customization. The two platforms model it very differently, so it needs a shared abstraction. |
| Maneuver arrow styling | Android `routeArrowOptions` | Android-only surface. |
| Destination marker customization | Android `destinationMarkerAnnotationOptions` | iOS equivalent is the `didAdd finalDestinationAnnotation` delegate hook. |
| Building highlight on arrival | Android `enableBuildingHighlightOnArrival`, `buildingHighlightOptions` | Cheap to add; Android-only. |
| Free-drive mode (navigate with no destination) | Android `NavigationViewApi.startFreeDrive()` | Would need a `mode` prop; `destination` is currently required. |
| Alternative-route selection | iOS `didUpdateAlternatives` / `didSelect continuousAlternative` | Currently alternatives can be shown but not chosen programmatically. |
| Manual reroute / route refresh triggers | Both | `onOffRoute` reports it; forcing one is not exposed. |
| Road objects / upcoming alerts (tunnels, tolls, rest stops) | `RouteProgress.upcomingRoadObjects` on both | Rich data, needs a serialization design. |
| Speed limit value as data | Both (`LocationMatcherResult.speedLimit`) | The native badge is exposed; the raw number is not. |
| Voice instruction events | Both | Only mute/volume are wrapped, not per-instruction callbacks. |
| EV routing / charging stations | Both | Large, specialized surface. |
| CarPlay / Android Auto | Both | Out of scope for an embedded RN view. |
| Full-screen navigation UI | Both | Removed in `2.0.0`; embedded-only by design. |


## Current Limitations

These are important review findings from the current codebase and the docs below reflect them intentionally:

- `androidActionButtons` is ignored on both platforms and is marked `@deprecated`. Mapbox's Drop-In UI does not expose those buttons individually — use `nativeFloatingButtons`, or render your own with `floatingButtons`.
- `showsReportFeedback` is iOS-only. Android's Drop-In UI does not expose it.
- `showsContinuousAlternatives`, `usesNightStyleWhileInTunnel`, `routeLineTracksTraversal` and `annotatesIntersectionsAlongRoute` are iOS-only.
- `cameraPitch` / `cameraZoom` apply on both platforms, but Android's Drop-In camera recomputes the viewport while following the user, so they hold reliably only while idle or in overview.
- `locationPuck.translation` is Android-only; the iOS puck API has no model-translation equivalent.
- `MapboxNavigationView` is embedded-only. There is no full-screen activity/controller API in `2.x`.
- The package targets the Mapbox Navigation **v2** SDKs (iOS `~> 2.19`, Android `2.21`). v3 is not supported yet.

## Supporting Docs

- [QUICKSTART.md](./QUICKSTART.md)
- [docs/USAGE.md](./docs/USAGE.md)
- [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md)
- [CHANGELOG.md](./CHANGELOG.md)

## Contributors

- 🧑‍💻 **Remy Tresor**  
  [![GitHub](https://img.shields.io/badge/GitHub-Remy--Tresor250-181717?style=for-the-badge&logo=github)](https://github.com/Remy-Tresor250)

- 🧑‍💻 **Irere Emmanuel**  
  [![GitHub](https://img.shields.io/badge/GitHub-Irere123-181717?style=for-the-badge&logo=github)](https://github.com/Irere123)
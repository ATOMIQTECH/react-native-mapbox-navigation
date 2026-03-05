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
  getNavigationSettings,
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

`getNavigationSettings()` currently exposes module-level settings state. Do not treat `isNavigating` as authoritative session state yet.

## Component Callbacks

`MapboxNavigationView` supports these callbacks:

- `onLocationChange(location)`
- `onRouteProgressChange(progress)`
- `onRouteChange(event)`
- `onJourneyDataChange(data)`
- `onBannerInstruction(instruction)`
- `onArrive(event)`
- `onCancelNavigation()`
- `onError(error)`
- `onOverlayBottomSheetActionPress(event)`
- `onEndOfRouteFeedbackSubmit(event)`
- `onDestinationPreview(event)` Android-only
- `onDestinationChanged(event)` Android-only

## Listener Helpers

You can also subscribe with exported listeners:

```ts
import {
  addArriveListener,
  addBannerInstructionListener,
  addBottomSheetActionPressListener,
  addCancelNavigationListener,
  addDestinationChangedListener,
  addDestinationPreviewListener,
  addErrorListener,
  addJourneyDataChangeListener,
  addLocationChangeListener,
  addRouteChangeListener,
  addRouteProgressChangeListener,
} from "@atomiqlab/react-native-mapbox-navigation";
```

## Current Limitations

These are important review findings from the current codebase and the docs below reflect them intentionally:

- `androidActionButtons` is still in the public types for compatibility, but it is effectively ignored in embedded mode today.
- `showsReportFeedback` is not a reliable cross-platform toggle in embedded mode. Use `nativeFloatingButtons` for built-in floating-button visibility instead.
- iOS currently requires `startOrigin`; Android can fall back to device location.
- `MapboxNavigationView` is embedded-only. There is no full-screen activity/controller API in `2.x`.

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
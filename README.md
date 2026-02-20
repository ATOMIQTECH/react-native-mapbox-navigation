# @atomiqlab/react-native-mapbox-navigation

Native Mapbox turn-by-turn navigation for Expo apps on iOS and Android.

## Features

- Full-screen native navigation via `startNavigation`.
- Embedded native navigation UI via `MapboxNavigationView`.
- Real-time events: location, route progress, banner instruction, arrival, cancel, and error.
- Runtime controls for mute, voice volume, distance unit, and language.
- Navigation customization: camera mode/pitch/zoom, theme, map style, and UI visibility toggles.
- Expo config plugin that applies required Android and iOS native setup.

## Requirements

- Expo SDK `>=50`
- iOS `14+`
- Mapbox access credentials:
  - Public token (`pk...`)
  - Downloads token (`sk...`) with `DOWNLOADS:READ`

## Installation

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

## Expo Setup

Add the plugin in your app config (`app.json` or `app.config.js`):

```json
{
  "expo": {
    "plugins": [
      "@atomiqlab/react-native-mapbox-navigation"
    ]
  }
}
```

Set these environment variables:

- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN` (Mapbox public token)
- `MAPBOX_DOWNLOADS_TOKEN` (Mapbox downloads token)

Regenerate native projects:

```bash
npx expo prebuild --clean
```

## Quick Start

```ts
import {
  startNavigation,
  stopNavigation,
  addLocationChangeListener,
  addRouteProgressChangeListener,
  addBannerInstructionListener,
  addArriveListener,
  addCancelNavigationListener,
  addErrorListener,
} from "@atomiqlab/react-native-mapbox-navigation";

await startNavigation({
  destination: { latitude: 37.7847, longitude: -122.4073, name: "Downtown" },
  startOrigin: { latitude: 37.7749, longitude: -122.4194 },
  shouldSimulateRoute: true,
  routeAlternatives: true,
  cameraMode: "following",
  uiTheme: "system",
  distanceUnit: "metric",
  language: "en",
});

const subscriptions = [
  addLocationChangeListener((location) => console.log(location)),
  addRouteProgressChangeListener((progress) => console.log(progress)),
  addBannerInstructionListener((instruction) => console.log(instruction.primaryText)),
  addArriveListener((arrival) => console.log(arrival)),
  addCancelNavigationListener(() => console.log("cancelled")),
  addErrorListener((error) => console.warn(error)),
];

// Cleanup
subscriptions.forEach((sub) => sub.remove());
await stopNavigation();
```

## Embedded Navigation View

```tsx
import { MapboxNavigationView } from "@atomiqlab/react-native-mapbox-navigation";

<MapboxNavigationView
  style={{ flex: 1 }}
  destination={{ latitude: 37.7847, longitude: -122.4073, name: "Downtown" }}
  startOrigin={{ latitude: 37.7749, longitude: -122.4194 }}
  shouldSimulateRoute
  cameraMode="following"
  showsTripProgress
  onBannerInstruction={(instruction) => console.log(instruction.primaryText)}
  onRouteProgressChange={(progress) => console.log(progress.fractionTraveled)}
  onError={(error) => console.warn(error.message)}
/>;
```

## API Overview

Core functions:

- `startNavigation(options)`
- `stopNavigation()`
- `isNavigating()`
- `getNavigationSettings()`
- `setMuted(muted)`
- `setVoiceVolume(volume)`
- `setDistanceUnit(unit)`
- `setLanguage(language)`

Event listeners:

- `addLocationChangeListener(listener)`
- `addRouteProgressChangeListener(listener)`
- `addBannerInstructionListener(listener)`
- `addArriveListener(listener)`
- `addCancelNavigationListener(listener)`
- `addErrorListener(listener)`

Main `NavigationOptions` fields:

- Route: `destination`, `startOrigin`, `waypoints`, `routeAlternatives`, `shouldSimulateRoute`
- Camera: `cameraMode`, `cameraPitch`, `cameraZoom`
- Theme/style: `uiTheme`, `mapStyleUri`, `mapStyleUriDay`, `mapStyleUriNight`
- Guidance: `distanceUnit`, `language`, `mute`, `voiceVolume`
- UI toggles: `showsSpeedLimits`, `showsWayNameLabel`, `showsTripProgress`, `showsManeuverView`, `showsActionButtons`

Full types: `src/MapboxNavigation.types.ts`

## Platform Notes

- Android: `startOrigin` is optional (current location is supported).
- iOS: `startOrigin` is optional (current location is resolved at runtime with location permission).

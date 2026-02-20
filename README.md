# react-native-mapbox-navigation

Native Mapbox turn-by-turn navigation bridge for Expo / React Native (iOS + Android).

## Highlights

- Full-screen native navigation (`startNavigation`).
- Embedded native navigation (`MapboxNavigationView`).
- Event stream: location, route progress, banner instruction, arrival, cancel, error.
- Flexible camera/theme/style controls.
- Expo config plugin for Android + iOS setup defaults.

## Installation

```bash
npm install react-native-mapbox-navigation
npx expo install expo-build-properties
```

## Expo Config

Add plugin in app config:

```json
{
  "expo": {
    "plugins": [
      "react-native-mapbox-navigation"
    ]
  }
}
```

### Required tokens

- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN`: Mapbox public token (`pk...`)
- `MAPBOX_DOWNLOADS_TOKEN`: Mapbox downloads token (`sk...`) with `DOWNLOADS:READ`

Then regenerate native projects:

```bash
npx expo prebuild --clean
```

## Quick Usage

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
} from "react-native-mapbox-navigation";

await startNavigation({
  destination: { latitude: 37.7847, longitude: -122.4073, name: "Downtown" },
  startOrigin: { latitude: 37.7749, longitude: -122.4194 },
  shouldSimulateRoute: true,
  cameraMode: "following",
  uiTheme: "system",
});

const subs = [
  addLocationChangeListener((location) => console.log(location)),
  addRouteProgressChangeListener((progress) => console.log(progress)),
  addBannerInstructionListener((instruction) => console.log(instruction.primaryText)),
  addArriveListener((arrival) => console.log(arrival)),
  addCancelNavigationListener(() => console.log("cancelled")),
  addErrorListener((error) => console.warn(error)),
];

// Cleanup
subs.forEach((sub) => sub.remove());
await stopNavigation();
```

## Embedded View

```tsx
import { MapboxNavigationView } from "react-native-mapbox-navigation";

<MapboxNavigationView
  style={{ flex: 1 }}
  destination={{ latitude: 37.7847, longitude: -122.4073, name: "Downtown" }}
  startOrigin={{ latitude: 37.7749, longitude: -122.4194 }}
  shouldSimulateRoute
  cameraMode="following"
  onBannerInstruction={(instruction) => console.log(instruction.primaryText)}
/>;
```

## API Surface

### Core functions

- `startNavigation(options)`
- `stopNavigation()`
- `isNavigating()`
- `getNavigationSettings()`
- `setMuted(muted)`
- `setVoiceVolume(volume)`
- `setDistanceUnit(unit)`
- `setLanguage(language)`

### Listener helpers

- `addLocationChangeListener(listener)`
- `addRouteProgressChangeListener(listener)`
- `addBannerInstructionListener(listener)`
- `addArriveListener(listener)`
- `addCancelNavigationListener(listener)`
- `addErrorListener(listener)`

### Key navigation options

- Routing: `destination`, `startOrigin`, `waypoints`, `routeAlternatives`, `shouldSimulateRoute`
- Camera: `cameraMode`, `cameraPitch`, `cameraZoom`
- Theme/style: `uiTheme`, `mapStyleUri`, `mapStyleUriDay`, `mapStyleUriNight`
- Guidance/UI: `distanceUnit`, `language`, `mute`, `voiceVolume`
- Visibility toggles: `showsSpeedLimits`, `showsWayNameLabel`, `showsTripProgress`, `showsManeuverView`, `showsActionButtons`

Full type reference: `src/MapboxNavigation.types.ts`

## Platform behavior

- Android: `startOrigin` optional; current location start is supported.
- iOS: `startOrigin` optional; when omitted, the module resolves current device location (requires location permission).
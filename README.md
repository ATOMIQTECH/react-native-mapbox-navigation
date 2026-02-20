# @atomiqlab/react-native-mapbox-navigation

Native Mapbox turn-by-turn navigation bridge for Expo / React Native (iOS + Android).

## Highlights

- Full-screen native navigation (`startNavigation`).
- Embedded native navigation (`MapboxNavigationView`).
- Event stream: location, route progress, banner instruction, arrival, cancel, error.
- Flexible camera/theme/style controls.
- Expo config plugin for Android + iOS setup defaults.

## Installation

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

## Expo Config

Add plugin in app config:

```json
{
  "expo": {
    "plugins": [
      "@atomiqlab/react-native-mapbox-navigation"
    ]
  }
}
```

### Required tokens(place in .env)

- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN`: Mapbox public token (`pk...`)
- `MAPBOX_DOWNLOADS_TOKEN`: Mapbox downloads token (`sk...`) with `DOWNLOADS:READ`

Then regenerate native projects:

```bash
npx expo prebuild --clean
```

## React Native CLI (Without Expo)

This package also works in bare React Native apps, but you must configure native files manually (the Expo config plugin is not used).

### 1. Install dependencies

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

### 2. Android manual setup

In `android/build.gradle`, add the Mapbox Maven repo in `allprojects.repositories`:

```gradle
def mapboxDownloadsToken = (findProperty("MAPBOX_DOWNLOADS_TOKEN") ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN") ?: "")
  .toString()
  .replace('"', '')
  .trim()

allprojects {
  repositories {
    google()
    mavenCentral()
    maven {
      url 'https://api.mapbox.com/downloads/v2/releases/maven'
      authentication {
        basic(BasicAuthentication)
      }
      credentials {
        username = "mapbox"
        password = mapboxDownloadsToken
      }
    }
  }
}
```

In `android/app/build.gradle`, add:

```gradle
def mapboxPublicToken = project.findProperty("MAPBOX_PUBLIC_TOKEN") ?: System.getenv("EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN") ?: ""
resValue "string", "mapbox_access_token", mapboxPublicToken
```

In `android/app/src/main/AndroidManifest.xml`, ensure these permissions exist:

- `android.permission.ACCESS_COARSE_LOCATION`
- `android.permission.ACCESS_FINE_LOCATION`
- `android.permission.ACCESS_BACKGROUND_LOCATION`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_LOCATION`
- `android.permission.POST_NOTIFICATIONS`

### 3. iOS manual setup

In `ios/<YourApp>/Info.plist`, add:

- `MBXAccessToken` = your Mapbox public token (`pk...`)
- `NSLocationWhenInUseUsageDescription`
- `NSLocationAlwaysAndWhenInUseUsageDescription`
- `UIBackgroundModes` includes `location` and `audio`

Then run:

```bash
cd ios && pod install
```

### 4. Required tokens (place in .env)

- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN`: Mapbox public token (`pk...`)
- `MAPBOX_DOWNLOADS_TOKEN`: Mapbox downloads token (`sk...`) with `DOWNLOADS:READ`

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
} from "@atomiqlab/react-native-mapbox-navigation";

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
import { MapboxNavigationView } from "@atomiqlab/react-native-mapbox-navigation";

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

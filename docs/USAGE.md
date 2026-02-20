# Usage Guide

## Start Full-Screen Navigation

```ts
import { startNavigation } from "@atomiqtech/react-native-mapbox-navigation";

await startNavigation({
  destination: { latitude: 37.7847, longitude: -122.4073, name: "Downtown" },
  startOrigin: { latitude: 37.7749, longitude: -122.4194 },
  shouldSimulateRoute: true,
  routeAlternatives: true,
  distanceUnit: "metric",
  language: "en",
  mute: false,
  voiceVolume: 1,
  cameraMode: "following",
  cameraPitch: 45,
  cameraZoom: 15,
  uiTheme: "system",
  mapStyleUriDay: "mapbox://styles/mapbox/navigation-day-v1",
  mapStyleUriNight: "mapbox://styles/mapbox/navigation-night-v1",
  showsSpeedLimits: true,
  showsWayNameLabel: true,
  showsTripProgress: true,
  showsManeuverView: true,
  showsActionButtons: true,
});
```

## Stop Navigation

```ts
import { stopNavigation } from "@atomiqtech/react-native-mapbox-navigation";

await stopNavigation();
```

## Listen to Events

```ts
import {
  addLocationChangeListener,
  addRouteProgressChangeListener,
  addBannerInstructionListener,
  addArriveListener,
  addCancelNavigationListener,
  addErrorListener,
} from "@atomiqtech/react-native-mapbox-navigation";

const subscriptions = [
  addLocationChangeListener((location) => {
    console.log("location", location.latitude, location.longitude);
  }),
  addRouteProgressChangeListener((progress) => {
    console.log("progress", progress.fractionTraveled);
  }),
  addBannerInstructionListener((instruction) => {
    console.log("banner", instruction.primaryText);
  }),
  addArriveListener((arrival) => {
    console.log("arrive", arrival.name);
  }),
  addCancelNavigationListener(() => {
    console.log("cancel");
  }),
  addErrorListener((error) => {
    console.warn("navigation error", error.code, error.message);
  }),
];

// cleanup
subscriptions.forEach((sub) => sub.remove());
```

## Embedded Navigation View

```tsx
import { MapboxNavigationView } from "@atomiqtech/react-native-mapbox-navigation";

export function EmbeddedNavigationScreen() {
  return (
    <MapboxNavigationView
      style={{ flex: 1 }}
      destination={{ latitude: 37.7847, longitude: -122.4073, name: "Downtown" }}
      startOrigin={{ latitude: 37.7749, longitude: -122.4194 }}
      shouldSimulateRoute
      cameraMode="following"
      onBannerInstruction={(instruction) => console.log(instruction.primaryText)}
      onError={(error) => console.warn(error.message)}
    />
  );
}
```

## Platform Notes

- Android supports omitting `startOrigin` to start from current location.
- iOS supports omitting `startOrigin`; current location is resolved at runtime (with permission).
- Both platforms support embedded navigation view and camera/style customization.

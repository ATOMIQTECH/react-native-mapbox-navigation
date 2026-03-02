# Usage

## Embedded Navigation

```tsx
import { MapboxNavigationView, type Waypoint } from "@atomiqlab/react-native-mapbox-navigation";

const START: Waypoint = { latitude: 37.7749, longitude: -122.4194 };
const DEST: Waypoint = { latitude: 37.7847, longitude: -122.4073 };

<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  startOrigin={START}
  destination={DEST}
  shouldSimulateRoute
/>
```

## Overlay Bottom Sheet (Custom)

```tsx
<MapboxNavigationView
  enabled
  destination={DEST}
  bottomSheet={{
    enabled: true,
    mode: "overlay",
    initialState: "hidden",
    revealGestureHotzoneHeight: 120,
    revealGestureRightExclusionWidth: 0,
    collapsedHeight: 124,
    expandedHeight: 340,
    showHandle: true,
    builtInQuickActions: ["overview", "recenter", "toggleMute", "stop"],
  }}
  renderBottomSheet={({ state, show, hide, bannerInstruction, routeProgress }) => (
    <YourBottomSheet
      state={state}
      bannerInstruction={bannerInstruction}
      routeProgress={routeProgress}
      onShow={show}
      onHide={hide}
    />
  )}
/>
```

## Runtime Controls

```ts
import {
  setMuted,
  setVoiceVolume,
  setDistanceUnit,
  setLanguage,
  getNavigationSettings,
} from "@atomiqlab/react-native-mapbox-navigation";

await setMuted(true);
await setVoiceVolume(0.8);
await setDistanceUnit("imperial");
await setLanguage("en");
const settings = await getNavigationSettings();
```

## Notes

- `bottomSheet.mode` is overlay-only in v2.0.0.
- Full-screen APIs are removed in v2.0.0.
- Android embedded mode uses Mapbox Drop-In UI.

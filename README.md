# @atomiqlab/react-native-mapbox-navigation

Embedded Mapbox turn-by-turn navigation for Expo and React Native (iOS + Android).

## 2.0.0 Breaking Change

This package is now **embedded-only**.

Removed:
- `startNavigation(...)`
- `stopNavigation()`
- `isNavigating()`
- full-screen navigation activity/controller flow

Use `MapboxNavigationView` for both platforms.

## Install

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

## Minimal Usage

```tsx
import { MapboxNavigationView, type Waypoint } from "@atomiqlab/react-native-mapbox-navigation";

const start: Waypoint = { latitude: 37.7749, longitude: -122.4194 };
const destination: Waypoint = { latitude: 37.7847, longitude: -122.4073 };

<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  startOrigin={start}
  destination={destination}
  shouldSimulateRoute
/>
```

## Overlay Custom Bottom Sheet

`bottomSheet.mode` is overlay-only. The package renders your custom sheet above the native map/navigation UI.

```tsx
<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  destination={destination}
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
  renderBottomSheet={({ state, bannerInstruction, routeProgress, show, hide }) => (
    <YourCustomSheet
      state={state}
      bannerInstruction={bannerInstruction}
      routeProgress={routeProgress}
      onShow={show}
      onHide={hide}
    />
  )}
/>
```

## Runtime APIs

- `setMuted(muted: boolean)`
- `setVoiceVolume(volume: number)`
- `setDistanceUnit("metric" | "imperial")`
- `setLanguage(language: string)`
- `getNavigationSettings()`

## Events

Use listeners or component callbacks:
- `onLocationChange`
- `onRouteProgressChange`
- `onJourneyDataChange`
- `onBannerInstruction`
- `onArrive`
- `onDestinationPreview` (Android)
- `onDestinationChanged` (Android)
- `onCancelNavigation`
- `onError`
- `onOverlayBottomSheetActionPress`

## Platform Notes

- Android uses Mapbox Drop-In `NavigationView` in embedded mode.
- Android full-screen activity has been removed.
- iOS full-screen module flow has been removed; embedded view is the only mode.

## Docs

- [QUICKSTART.md](./QUICKSTART.md)
- [docs/USAGE.md](./docs/USAGE.md)
- [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md)
- [CHANGELOG.md](./CHANGELOG.md)

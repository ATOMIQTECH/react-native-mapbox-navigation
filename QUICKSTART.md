# Quickstart

## 1. Install

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

## 2. Configure Mapbox token

Set `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN` and run prebuild so native token resources are generated.

## 3. Render embedded navigation

```tsx
import { MapboxNavigationView } from "@atomiqlab/react-native-mapbox-navigation";

<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  destination={{ latitude: 37.7847, longitude: -122.4073 }}
  shouldSimulateRoute
/>
```

## 4. Optional custom overlay sheet

```tsx
<MapboxNavigationView
  enabled
  destination={{ latitude: 37.7847, longitude: -122.4073 }}
  bottomSheet={{
    enabled: true,
    mode: "overlay",
    initialState: "hidden",
    revealGestureHotzoneHeight: 120,
    revealGestureRightExclusionWidth: 0,
  }}
  renderBottomSheet={({ bannerInstruction, routeProgress }) => (
    <YourSheet bannerInstruction={bannerInstruction} routeProgress={routeProgress} />
  )}
/>
```

## 2.0.0 Note

This package is embedded-only. Full-screen APIs were removed.

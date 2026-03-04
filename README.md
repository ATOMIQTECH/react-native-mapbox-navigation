# @atomiqlab/react-native-mapbox-navigation

Embedded Mapbox turn-by-turn navigation for Expo and React Native (iOS + Android).

## 2.0.0 Breaking Change

This package is now **embedded-only**.

Removed from v1 full-screen API:
- `startNavigation(...)`
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
    collapsedHeight: 124,
    expandedHeight: 340,
    collapsedBottomOffset: 24,
    showHandle: true,
    colorMode: "dark",
    builtInQuickActions: ["overview", "recenter", "toggleMute", "stop"],
  }}
  bottomSheetComponent={YourCustomSheet}
/>
```

## Custom Floating Buttons

Use the JS overlay layer for app-owned floating actions. This avoids any per-app native work and can drive either your own UI or the package bottom sheet.

```tsx
function YourFloatingButtons({ expand }: FloatingButtonsRenderContext) {
  return (
    <MapboxNavigationFloatingButtonsStack>
      <MapboxNavigationFloatingButton onPress={expand}>
        <Text>UI</Text>
      </MapboxNavigationFloatingButton>
    </MapboxNavigationFloatingButtonsStack>
  );
}

<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  destination={destination}
  nativeFloatingButtons={{
    showOverviewButton: false,
    showAudioGuidanceButton: false,
    showFeedbackButton: false,
    showCameraModeButton: false,
    showRecenterButton: false,
    showCompassButton: false,
  }}
  floatingButtonsComponent={YourFloatingButtons}
/>
```

Custom floating buttons are independent from the custom bottom sheet and work when `bottomSheet` is omitted. Use `nativeFloatingButtons` to hide built-in native buttons individually, then render your own React buttons with `floatingButtonsComponent`. `MapboxNavigationFloatingButtonsStack` and `MapboxNavigationFloatingButton` keep custom controls aligned and styled like the default right-side Mapbox buttons. `YourFloatingButtons` receives `expand`, `collapse`, `toggle`, `stopNavigation`, route progress, banner instructions, and location. `YourCustomSheet` receives the same controls plus the current sheet state.

## Runtime APIs

- `setMuted(muted: boolean)`
- `setVoiceVolume(volume: number)`
- `setDistanceUnit("metric" | "imperial")`
- `setLanguage(language: string)`
- `getNavigationSettings()`
- `stopNavigation()` stop active embedded session

## Events

Use listeners or component callbacks:
- `onLocationChange`
- `onRouteProgressChange`
- `onRouteChange`
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
- Android overlay mode keeps custom sheet in `collapsed/expanded`.
- iOS overlay mode uses `hidden/expanded`.
- Android full-screen activity has been removed.
- iOS full-screen module flow has been removed; embedded view is the only mode.

## Docs

- [QUICKSTART.md](./QUICKSTART.md)
- [docs/USAGE.md](./docs/USAGE.md)
- [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md)
- [CHANGELOG.md](./CHANGELOG.md)

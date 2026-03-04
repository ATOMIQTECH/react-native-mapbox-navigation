# @atomiqlab/react-native-mapbox-navigation

Embedded Mapbox turn-by-turn navigation for Expo / React Native (iOS + Android).

## v2 Breaking Change

This package is now embedded-only.

Removed from v1:
- `startNavigation(...)`
- `isNavigating()`
- full-screen navigation activity/controller flow

Use `MapboxNavigationView` on both platforms.

## Requirements

- Expo SDK 50+
- React Native app with native folders (prebuild workflow)
- iOS: CocoaPods available
- Mapbox tokens:
  - public token (`pk...`) for runtime map/navigation
  - downloads token (`sk...` with `DOWNLOADS:READ`) for Android Maven artifacts

## More Docs

- [QUICKSTART.md](./QUICKSTART.md)
- [USAGE.md](./docs/USAGE.md)
- [TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md)
- [CHANGELOG.md](./CHANGELOG.md)

## Install

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

## Before First Run (Important)

1. Set required env vars:

```bash
EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN=pk.your_public_token
MAPBOX_DOWNLOADS_TOKEN=sk.your_downloads_token_with_DOWNLOADS_READ
```

2. Ensure plugin is enabled (usually auto from package):

```json
{
  "expo": {
    "plugins": ["@atomiqlab/react-native-mapbox-navigation"]
  }
}
```

3. Generate/update native projects:

```bash
npx expo prebuild --clean
```

4. Install iOS pods:

```bash
npx pod-install
```

5. Run:

```bash
npx expo run:ios
npx expo run:android
```

Notes:
- If your first run shows a black screen, usually token/prebuild sync is missing.
- Android requires `MAPBOX_DOWNLOADS_TOKEN` at build time (Gradle resolves Mapbox Maven deps).

## Minimal Usage

```tsx
import { MapboxNavigationView, type Waypoint } from "@atomiqlab/react-native-mapbox-navigation";

const START: Waypoint = { latitude: 37.7749, longitude: -122.4194 };
const DEST: Waypoint = { latitude: 37.7847, longitude: -122.4073 };

export function Screen() {
  return (
    <MapboxNavigationView
      enabled
      style={{ flex: 1 }}
      startOrigin={START}
      destination={DEST}
      shouldSimulateRoute
    />
  );
}
```

## Custom Bottom Sheet (Overlay)

`bottomSheet.mode` is overlay-only.

```tsx
<MapboxNavigationView
  enabled
  style={{ flex: 1 }}
  destination={DEST}
  bottomSheet={{
    enabled: true,
    mode: "overlay",
    initialState: Platform.OS === "ios" ? "hidden" : "collapsed",
    collapsedHeight: 124,
    expandedHeight: 340,
    collapsedBottomOffset: Platform.OS === "android" ? 26 : 0,
    colorMode: "dark",
  }}
  renderBottomSheet={({ state, bannerInstruction, routeProgress, stopNavigation }) => (
    <YourCustomSheet
      state={state}
      bannerInstruction={bannerInstruction}
      routeProgress={routeProgress}
      onStop={stopNavigation}
    />
  )}
/>
```

Platform behavior:
- Android: `collapsed` and `expanded`
- iOS: `hidden` and `expanded` (collapsed maps to hidden behavior)

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
- `stopNavigation()`
- `resumeCameraFollowing()`

## Events

Use either component callbacks or exported listeners:
- `onLocationChange`
- `onRouteProgressChange`
- `onCameraFollowingStateChange`
- `onRouteChange`
- `onJourneyDataChange`
- `onBannerInstruction`
- `onArrive`
- `onDestinationPreview` (Android)
- `onDestinationChanged` (Android)
- `onCancelNavigation`
- `onError`
- `onOverlayBottomSheetActionPress`

## Contributors

- Remy Tresor ([Remy-Tresor250](https://github.com/Remy-Tresor250))

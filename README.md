# @atomiqlab/react-native-mapbox-navigation

Native Mapbox turn-by-turn navigation for Expo + React Native on iOS and Android.

A production-focused wrapper around Mapbox Navigation SDK with:
- full-screen native navigation
- embedded native navigation view
- rich bottom-sheet customization
- cross-platform event stream + runtime controls

---

## Why This Package

- Expo-friendly setup through config plugin
- Works in full-screen flow and embedded flow
- Strong customization surface for app-specific UX
- Error-first behavior with normalized codes and events

---

## Feature Highlights

- `startNavigation(options)` for full-screen native experience
- `MapboxNavigationView` for embedded navigation in your own screens
- Runtime controls:
  - `setMuted`
  - `setVoiceVolume`
  - `setDistanceUnit`
  - `setLanguage`
- Event listeners:
  - location/progress/banner/arrival/cancel/error
  - Android destination preview/changed
  - iOS full-screen bottom-sheet action events
- Bottom-sheet flexibility:
  - built-in defaults
  - custom React overlay content
  - style/typography/action behavior controls

---

## Requirements

- Expo SDK `>= 50`
- iOS `14+`
- Mapbox tokens:
  - public token: `pk...`
  - downloads token: `sk...` with `DOWNLOADS:READ`

---

## Installation

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

---

## Expo Setup

Add plugin in `app.json` or `app.config.js`:

```json
{
  "expo": {
    "plugins": ["@atomiqlab/react-native-mapbox-navigation"]
  }
}
```

Set env vars:
- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN`
- `MAPBOX_DOWNLOADS_TOKEN`

Regenerate native projects:

```bash
npx expo prebuild --clean
```

### Token Validation (Fail Fast)

Prebuild/build fails early when tokens are missing or malformed:
- invalid/missing public token (`pk...`)
- invalid/missing downloads token (`sk...`)

---

## Quick Start (Full-Screen)

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
  addBottomSheetActionPressListener,
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
  bottomSheet: {
    enabled: true,
    showsTripProgress: true,
    showsManeuverView: true,
    showsActionButtons: true,
    initialState: "collapsed",
    collapsedHeight: 120,
    expandedHeight: 280,
    showHandle: true,
    enableTapToToggle: true,
    contentHorizontalPadding: 16,
    contentBottomPadding: 14,
    contentTopSpacing: 4,
    backgroundColor: "#0f172a",
    handleColor: "#93c5fd",
    primaryTextColor: "#ffffff",
    secondaryTextColor: "#bfdbfe",
    primaryTextFontSize: 16,
    secondaryTextFontSize: 13,
    actionButtonBackgroundColor: "#2563eb",
    actionButtonTextColor: "#ffffff",
    secondaryActionButtonBackgroundColor: "#1e293b",
    secondaryActionButtonTextColor: "#bfdbfe",
    actionButtonTitle: "End Navigation",
    secondaryActionButtonTitle: "More",
    primaryActionButtonBehavior: "stopNavigation",
    secondaryActionButtonBehavior: "emitEvent",
    actionButtonBorderColor: "#60a5fa",
    actionButtonBorderWidth: 1,
    actionButtonCornerRadius: 10,
    actionButtonFontSize: 14,
    actionButtonHeight: 42,
    quickActions: [
      { id: "support", label: "Support", variant: "secondary" },
      { id: "share_eta", label: "Share ETA", variant: "ghost" },
    ],
    builtInQuickActions: ["overview", "recenter", "toggleMute", "stop"],
    cornerRadius: 18,
  },
});

const subscriptions = [
  addLocationChangeListener((e) => console.log(e)),
  addRouteProgressChangeListener((e) => console.log(e)),
  addBannerInstructionListener((e) => console.log(e.primaryText)),
  addArriveListener((e) => console.log(e)),
  addBottomSheetActionPressListener((e) => console.log(e.actionId)),
  addCancelNavigationListener(() => console.log("cancelled")),
  addErrorListener((e) => console.warn(e)),
];

// cleanup
subscriptions.forEach((s) => s.remove());
await stopNavigation();
```

---

## Embedded Navigation View

```tsx
import { MapboxNavigationView } from "@atomiqlab/react-native-mapbox-navigation";

<MapboxNavigationView
  style={{ flex: 1 }}
  destination={{ latitude: 37.7847, longitude: -122.4073, name: "Downtown" }}
  startOrigin={{ latitude: 37.7749, longitude: -122.4194 }}
  shouldSimulateRoute
  cameraMode="following"
  bottomSheet={{
    enabled: true,
    mode: "overlay",
    builtInQuickActions: ["overview", "toggleMute", "stop"],
  }}
  onOverlayBottomSheetActionPress={(e) => {
    console.log("overlay action", e.actionId, e.source);
  }}
/>
```

### Custom Overlay Content (iOS + Android)

```tsx
<MapboxNavigationView
  style={{ flex: 1 }}
  destination={{ latitude: 37.7847, longitude: -122.4073 }}
  startOrigin={{ latitude: 37.7749, longitude: -122.4194 }}
  bottomSheet={{ enabled: true, mode: "overlay" }}
  renderBottomSheet={({ expanded, toggle, routeProgress, emitAction }) => (
    <Pressable
      onPress={() => {
        emitAction("custom_toggle");
        toggle();
      }}
    >
      <Text style={{ color: "white" }}>
        {expanded ? "Collapse" : "Expand"} · {Math.round(routeProgress?.distanceRemaining ?? 0)}m left
      </Text>
    </Pressable>
  )}
/>
```

---

## API Overview

### Core

- `startNavigation(options)`
- `stopNavigation()`
- `isNavigating()`
- `getNavigationSettings()`
- `setMuted(muted)`
- `setVoiceVolume(volume)`
- `setDistanceUnit(unit)`
- `setLanguage(language)`

### Event Listeners

- `addLocationChangeListener(listener)`
- `addRouteProgressChangeListener(listener)`
- `addBannerInstructionListener(listener)`
- `addArriveListener(listener)`
- `addDestinationPreviewListener(listener)` (Android)
- `addDestinationChangedListener(listener)` (Android)
- `addCancelNavigationListener(listener)`
- `addErrorListener(listener)`
- `addBottomSheetActionPressListener(listener)` (iOS full-screen)

---

## Bottom Sheet Customization Surface

`bottomSheet` supports:
- structure:
  - `enabled`
  - `showsTripProgress`
  - `showsManeuverView`
  - `showsActionButtons`
  - `mode`
  - `initialState`
  - `collapsedHeight`
  - `expandedHeight`
- overlay behavior:
  - `showHandle`
  - `enableTapToToggle`
  - `showDefaultContent`
  - `defaultManeuverTitle`
  - `defaultTripProgressTitle`
  - `quickActions`
  - `builtInQuickActions`
- layout + typography:
  - `contentHorizontalPadding`
  - `contentBottomPadding`
  - `contentTopSpacing`
  - `primaryTextFontSize`
  - `secondaryTextFontSize`
  - `actionButtonFontSize`
  - `actionButtonHeight`
- color + button style:
  - `backgroundColor`
  - `handleColor`
  - `primaryTextColor`
  - `secondaryTextColor`
  - `actionButtonBackgroundColor`
  - `actionButtonTextColor`
  - `secondaryActionButtonBackgroundColor`
  - `secondaryActionButtonTextColor`
  - `actionButtonBorderColor`
  - `actionButtonBorderWidth`
  - `actionButtonCornerRadius`
  - `cornerRadius`
- action behavior:
  - `actionButtonTitle`
  - `secondaryActionButtonTitle`
  - `primaryActionButtonBehavior`
  - `secondaryActionButtonBehavior`

Built-in quick actions:
- `overview`
- `recenter`
- `mute`
- `unmute`
- `toggleMute`
- `stop`

---

## Platform Notes

- Android: `startOrigin` optional for full-screen flow
- iOS: `startOrigin` optional for full-screen flow (resolved via location permission)
- Embedded flow currently expects explicit `startOrigin`
- iOS full-screen uses package-native overlay sheet
- Embedded flow supports full custom React sheet content

---

## Common Error Codes

- `MAPBOX_TOKEN_INVALID`
- `MAPBOX_TOKEN_FORBIDDEN`
- `MAPBOX_RATE_LIMITED`
- `ROUTE_FETCH_FAILED`
- `CURRENT_LOCATION_UNAVAILABLE`
- `INVALID_COORDINATES`

Use `addErrorListener` / `onError` to surface and log these.

---

## Screenshots

<p align="center">
  <img src="docs/screenshots/ios.png" alt="iOS" width="300" />
  <img src="docs/screenshots/android.png" alt="Android" width="300" />
  <img src="docs/screenshots/navigation_error.png" alt="Error Screen" width="300" />
</p>

---

## Support

<p align="center">
  <a href="https://ko-fi.com/atomiqlabs" target="_blank">
    <img src="docs/imgs/support_me_on_kofi_badge_red.png" alt="Support on Ko-fi" width="260" />
  </a>
</p>

# @atomiqlab/react-native-mapbox-navigation

Native Mapbox turn-by-turn navigation for Expo + React Native on iOS and Android.

Current package version: `1.1.6`.

This package now focuses on **full-screen native navigation** (`startNavigation`) for stability and production UX consistency.

---

## Why This Package

- Expo-friendly setup via config plugin
- Full-screen native UX on iOS + Android
- Strong bottom banner customization (`native` and `customNative`)
- Runtime controls and event listeners
- Session-guarded flow to prevent overlapping navigation sessions

---

## Feature Highlights

- `startNavigation(options)` full-screen navigation
- `stopNavigation()` and `isNavigating()` session controls
- Runtime updates:
  - `setMuted`
  - `setVoiceVolume`
  - `setDistanceUnit`
  - `setLanguage`
- Event listeners:
  - location/progress/journey/banner/arrival/cancel/error
  - Android destination preview/changed
  - bottom-sheet action press
- Bottom sheet modes:
  - `native` (Mapbox SDK bottom banner)
  - `customNative` (package expandable native sheet)

---

## Support Us

<p align="center">
  <a href="https://ko-fi.com/atomiqlabs" target="_blank">
    <img src="docs/imgs/support_me_on_kofi_badge_red.png" alt="Support on Ko-fi" width="260" />
  </a>
</p>

---

## Embedded View Status

`MapboxNavigationView` (embedded mode) has been removed from supported runtime usage due to session-conflict instability.

Use full-screen flow with `startNavigation()`.

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

---

## Quick Start

```ts
import {
  startNavigation,
  stopNavigation,
  addLocationChangeListener,
  addRouteProgressChangeListener,
  addJourneyDataChangeListener,
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
  bottomSheet: {
    enabled: true,
    mode: "customNative",
    initialState: "hidden",
    revealOnNativeBannerGesture: true,
    revealGestureHotzoneHeight: 100,
    revealGestureRightExclusionWidth: 80,
    collapsedHeight: 120,
    expandedHeight: 340,
    showsTripProgress: true,
    showsManeuverView: true,
    showsActionButtons: true,
    showCurrentStreet: true,
    showRemainingDistance: true,
    showRemainingDuration: true,
    showETA: true,
    showCompletionPercent: true,
    actionButtonTitle: "End Navigation",
    secondaryActionButtonTitle: "Support",
    primaryActionButtonBehavior: "stopNavigation",
    secondaryActionButtonBehavior: "emitEvent",
    quickActions: [
      { id: "overview", label: "Overview", variant: "secondary" },
      { id: "recenter", label: "Recenter", variant: "ghost" },
    ],
    builtInQuickActions: ["overview", "recenter", "toggleMute", "stop"],
  },
});

const subscriptions = [
  addLocationChangeListener((e) => console.log(e)),
  addRouteProgressChangeListener((e) => console.log(e)),
  addJourneyDataChangeListener((e) => console.log("journey", e)),
  addBannerInstructionListener((e) => console.log(e.primaryText)),
  addArriveListener((e) => console.log(e)),
  addCancelNavigationListener(() => console.log("cancelled")),
  addErrorListener((e) => console.warn(e)),
];

// cleanup
subscriptions.forEach((s) => s.remove());
await stopNavigation();
```

---

## API Overview

### Core Methods

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
- `addJourneyDataChangeListener(listener)`
- `addBannerInstructionListener(listener)`
- `addArriveListener(listener)`
- `addCancelNavigationListener(listener)`
- `addErrorListener(listener)`
- `addBottomSheetActionPressListener(listener)`
- `addDestinationPreviewListener(listener)` (Android)
- `addDestinationChangedListener(listener)` (Android)

---

## NavigationOptions

Top-level options:

- `destination` (required)
- `startOrigin` (optional)
- `waypoints`
- `shouldSimulateRoute`
- `mute`
- `voiceVolume`
- `distanceUnit`
- `language`
- `cameraMode`
- `cameraPitch`
- `cameraZoom`
- `mapStyleUri`
- `mapStyleUriDay`
- `mapStyleUriNight`
- `uiTheme`
- `routeAlternatives`
- `showsSpeedLimits`
- `showsWayNameLabel`
- `showsTripProgress`
- `showsManeuverView`
- `showsActionButtons`
- `showsReportFeedback`
- `showsEndOfRouteFeedback`
- `showsContinuousAlternatives`
- `usesNightStyleWhileInTunnel`
- `routeLineTracksTraversal`
- `annotatesIntersectionsAlongRoute`
- `androidActionButtons`
- `bottomSheet`

---

## Bottom Sheet Options (`bottomSheet`)

Behavior and structure:

- `enabled`
- `mode`: `"native" | "customNative"`
- `showsTripProgress`
- `showsManeuverView`
- `showsActionButtons`
- `initialState`: `"hidden" | "collapsed" | "expanded"`
- `collapsedHeight`
- `expandedHeight`
- `contentHorizontalPadding`
- `contentBottomPadding`
- `contentTopSpacing`
- `showHandle`
- `enableTapToToggle`
- `revealOnNativeBannerGesture`
- `revealGestureHotzoneHeight`
- `revealGestureRightExclusionWidth`

Visibility content flags:

- `showCurrentStreet`
- `showRemainingDistance`
- `showRemainingDuration`
- `showETA`
- `showCompletionPercent`

Main style fields:

- `backgroundColor`
- `handleColor`
- `primaryTextColor`
- `secondaryTextColor`
- `primaryTextFontSize`
- `secondaryTextFontSize`
- `cornerRadius`

Action button fields:

- `actionButtonTitle`
- `secondaryActionButtonTitle`
- `primaryActionButtonBehavior`: `"stopNavigation" | "emitEvent"`
- `secondaryActionButtonBehavior`: `"none" | "stopNavigation" | "emitEvent"`
- `actionButtonBackgroundColor`
- `actionButtonTextColor`
- `secondaryActionButtonBackgroundColor`
- `secondaryActionButtonTextColor`
- `actionButtonBorderColor`
- `actionButtonBorderWidth`
- `actionButtonCornerRadius`
- `actionButtonFontSize`
- `actionButtonHeight`
- `actionButtonsBottomPadding`

Quick action fields:

- `quickActions`
- `builtInQuickActions`
- `quickActionBackgroundColor`
- `quickActionTextColor`
- `quickActionSecondaryBackgroundColor`
- `quickActionSecondaryTextColor`
- `quickActionGhostTextColor`
- `quickActionBorderColor`
- `quickActionBorderWidth`
- `quickActionCornerRadius`

Custom native additions:

- `customRows`
- `headerTitle`
- `headerSubtitle`
- `headerBadgeText`
- `headerBadgeBackgroundColor`
- `headerBadgeTextColor`

Built-in quick action IDs:

- `overview`
- `recenter`
- `mute`
- `unmute`
- `toggleMute`
- `stop`

---

## Session Lifecycle Rules

- Keep only one active navigation session at a time.
- Guard rapid repeated starts.
- Always call `stopNavigation()` on screen teardown.

```ts
useEffect(() => {
  return () => {
    stopNavigation().catch(() => {});
  };
}, []);
```

Safe start guard:

```ts
let starting = false;

async function safeStartNavigation(options: Parameters<typeof startNavigation>[0]) {
  if (starting || (await isNavigating())) return;
  starting = true;
  try {
    await startNavigation(options);
  } finally {
    starting = false;
  }
}
```

---

## Platform Notes

- Full-screen `startNavigation`: iOS + Android
- Bottom sheet `mode = "native"`: iOS + Android
- Bottom sheet `mode = "customNative"`: iOS + Android
- `startOrigin` optional in full-screen (both platforms)
- `onDestinationPreview` / `onDestinationChanged`: Android only
- iOS custom row icon supports `iconSystemName` (SF Symbols)

---

## Common Error Codes

- `NAVIGATION_SESSION_CONFLICT`
- `MAPBOX_TOKEN_INVALID`
- `MAPBOX_TOKEN_FORBIDDEN`
- `MAPBOX_RATE_LIMITED`
- `ROUTE_FETCH_FAILED`
- `ROUTE_FETCH_CANCELED`
- `CURRENT_LOCATION_UNAVAILABLE`
- `INVALID_COORDINATES`

Use `addErrorListener` to surface and log these.

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

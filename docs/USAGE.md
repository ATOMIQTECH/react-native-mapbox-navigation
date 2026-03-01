# Usage Guide

Applies to package version: `1.1.6`.

## Scope

This package wraps Mapbox Navigation “drop-in” UI for turn-by-turn guidance. It is **not** a full Mapbox Maps SDK wrapper (layers, annotations, 3D lights, offline tile management, etc.). For those map-rendering examples/features, use a dedicated Mapbox Maps SDK React Native library alongside this package.

## Full-Screen Navigation

Use full-screen `startNavigation()` when you want the simplest integration (native UI, quick start).

```ts
import { startNavigation } from "@atomiqlab/react-native-mapbox-navigation";

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
  bottomSheet: {
    enabled: true,
    mode: "customNative",
    initialState: "hidden",
    revealOnNativeBannerGesture: true,
    revealGestureHotzoneHeight: 100,
    collapsedHeight: 118,
    expandedHeight: 340,

    showsTripProgress: true,
    showsManeuverView: true,
    showsActionButtons: true,
    showCurrentStreet: true,
    showRemainingDistance: true,
    showRemainingDuration: true,
    showETA: true,
    showCompletionPercent: true,

    showHandle: true,
    enableTapToToggle: true,

    backgroundColor: "#0f172a",
    handleColor: "#93c5fd",
    primaryTextColor: "#ffffff",
    secondaryTextColor: "#bfdbfe",
    primaryTextFontSize: 16,
    primaryTextFontWeight: "700",
    secondaryTextFontSize: 13,
    secondaryTextFontWeight: "500",
    cornerRadius: 16,

    actionButtonTitle: "End Navigation",
    secondaryActionButtonTitle: "Support",
    primaryActionButtonBehavior: "stopNavigation",
    secondaryActionButtonBehavior: "emitEvent",
    actionButtonBackgroundColor: "#2563eb",
    actionButtonTextColor: "#ffffff",
    secondaryActionButtonBackgroundColor: "#1e293b",
    secondaryActionButtonTextColor: "#bfdbfe",
    actionButtonBorderColor: "#1d4ed8",
    actionButtonBorderWidth: 1,
    actionButtonCornerRadius: 12,
    actionButtonFontSize: 14,
    actionButtonFontWeight: "700",
    actionButtonHeight: 42,
    actionButtonsBottomPadding: 2,

    quickActions: [
      { id: "overview", label: "Overview", variant: "secondary" },
      { id: "recenter", label: "Recenter", variant: "ghost" },
    ],
    builtInQuickActions: ["overview", "recenter", "toggleMute", "stop"],
    quickActionBackgroundColor: "#1d4ed8",
    quickActionTextColor: "#ffffff",
    quickActionSecondaryBackgroundColor: "#0f172a",
    quickActionSecondaryTextColor: "#bfdbfe",
    quickActionGhostTextColor: "#93c5fd",
    quickActionBorderColor: "#334155",
    quickActionBorderWidth: 1,
    quickActionCornerRadius: 12,
    quickActionFontWeight: "700",

    headerTitle: "Trip",
    headerSubtitle: "Swipe up from bottom zone",
    headerTitleFontSize: 16,
    headerTitleFontWeight: "700",
    headerSubtitleFontSize: 12,
    headerSubtitleFontWeight: "500",
    headerBadgeText: "PRO",
    headerBadgeFontSize: 11,
    headerBadgeFontWeight: "700",
    headerBadgeBackgroundColor: "#2563eb",
    headerBadgeTextColor: "#ffffff",
    headerBadgeCornerRadius: 10,
    headerBadgeBorderColor: "#1d4ed8",
    headerBadgeBorderWidth: 1,

    customRows: [
      {
        id: "driver",
        iconSystemName: "person.crop.circle.fill",
        title: "Driver",
        value: "Alex",
        subtitle: "Toyota Prius",
        emphasis: true,
      },
      { id: "ride", iconText: "ID", title: "Ride ID", value: "RX-2048" },
    ],
  },

  showsReportFeedback: true,
  showsEndOfRouteFeedback: true,
  showsContinuousAlternatives: true,
  usesNightStyleWhileInTunnel: true,
  routeLineTracksTraversal: false,
  annotatesIntersectionsAlongRoute: false,
  androidActionButtons: {
    showToggleAudioButton: true,
    showReportFeedbackButton: true,
    showStartNavigationButton: true,
    showEndNavigationButton: true,
  },
});
```

## Notes on Accepted No-op Options

These options are currently accepted for API compatibility but no-op in this version:

- `showsReportFeedback`
- `showsEndOfRouteFeedback`
- `usesNightStyleWhileInTunnel`
- `routeLineTracksTraversal`
- `annotatesIntersectionsAlongRoute`
- `androidActionButtons.*`

## Stop Navigation

```ts
import { stopNavigation } from "@atomiqlab/react-native-mapbox-navigation";

await stopNavigation();
```

## Session Lifecycle Safety

Use one active session at a time.

```ts
import { isNavigating, startNavigation, stopNavigation } from "@atomiqlab/react-native-mapbox-navigation";

let starting = false;

async function safeStart(options: Parameters<typeof startNavigation>[0]) {
  if (starting || (await isNavigating())) return;
  starting = true;
  try {
    await startNavigation(options);
  } finally {
    starting = false;
  }
}

useEffect(() => {
  return () => {
    stopNavigation().catch(() => {});
  };
}, []);
```

## Event Listeners

```ts
import {
  addLocationChangeListener,
  addRouteProgressChangeListener,
  addJourneyDataChangeListener,
  addBannerInstructionListener,
  addBottomSheetActionPressListener,
  addArriveListener,
  addCancelNavigationListener,
  addErrorListener,
} from "@atomiqlab/react-native-mapbox-navigation";

const subscriptions = [
  addLocationChangeListener((location) => {
    console.log("location", location.latitude, location.longitude);
  }),
  addRouteProgressChangeListener((progress) => {
    console.log("progress", progress.fractionTraveled);
  }),
  addJourneyDataChangeListener((data) => {
    console.log("journey", data);
  }),
  addBannerInstructionListener((instruction) => {
    console.log("banner", instruction.primaryText);
  }),
  addArriveListener((arrival) => {
    console.log("arrive", arrival.name);
  }),
  addBottomSheetActionPressListener((event) => {
    console.log("sheet action", event.actionId);
  }),
  addCancelNavigationListener(() => {
    console.log("cancelled");
  }),
  addErrorListener((error) => {
    console.warn("navigation error", error.code, error.message);
  }),
];

// cleanup
subscriptions.forEach((s) => s.remove());
```

## Platform Notes

- Full-screen navigation is supported on iOS + Android.
- `bottomSheet.mode` full-screen support:
  - `native`
  - `customNative`
- If `bottomSheet.enabled !== false` and `bottomSheet.mode` is omitted, full-screen defaults to `customNative` (recommended).
- `onDestinationPreview` and `onDestinationChanged` are Android-only.
- iOS `customRows.iconSystemName` supports SF Symbols.

## Embedded View (Opt-in)

Use embedded `MapboxNavigationView` to embed Mapbox’s official Drop-In UI (route preview panel + Start button + turn-by-turn UI) inside your own screens.

Embedded mode is available with explicit opt-in to avoid accidental session conflicts.

```tsx
import { MapboxNavigationView } from "@atomiqlab/react-native-mapbox-navigation";

export function EmbeddedScreen() {
  return (
    <MapboxNavigationView
      enabled
      style={{ flex: 1 }}
      startOrigin={{ latitude: 37.7749, longitude: -122.4194 }}
      destination={{ latitude: 37.7847, longitude: -122.4073, name: "Downtown" }}
      shouldSimulateRoute
    />
  );
}
```

Important:

- `enabled` defaults to `false`.
- keep one mode active at a time (full-screen or embedded).
- call `stopNavigation()` before switching modes.
- embedded mode is designed to be re-render safe; it should only (re)start when coordinates/options change.
- embedded iOS + Android support hiding the SDK top/bottom banner sections via:
  - `showsManeuverView`, `showsTripProgress`, `showsActionButtons`, `showCancelButton`
- Android: you must request runtime location permission before mounting with `enabled={true}`.
- If you wrap the embedded view in `SafeAreaView`, consider disabling the bottom edge (for example `edges={["top","left","right"]}`) if you want the bottom sheet to sit flush to the screen like full-screen navigation.
 - If you render React children inside `MapboxNavigationView`, they will appear above the native UI and can cover the Mapbox bottom sheet/buttons.

## React Node in `startNavigation`

Direct React Node injection into native full-screen bottom banner is not currently possible.
Use your own React UI outside the full-screen native session (or consider embedding the Drop-In UI with `MapboxNavigationView` and overlaying your own controls carefully).

For full-screen `customNative`, the package now exposes native style controls for:

- colors (container/text/buttons/badge/borders)
- typography (`fontSize`, `fontFamily`, `fontWeight`) for primary/secondary/action/quick/header text
- radii and borders for container, action buttons, quick actions, and header badge

## Mapbox SDK Banner Customization (iOS)

The Mapbox Navigation iOS SDK supports custom `topBanner` / `bottomBanner` view controllers in `NavigationOptions` (as in the SDK examples).
This package does not currently expose that level of iOS-only banner composition through `startNavigation()` (the bridge is cross-platform and uses a consistent option surface).

If you need a fully custom “pro” UI:

- Prefer embedding the Drop-In UI with `MapboxNavigationView` and overlaying your own controls (keeping in mind overlays can cover the Mapbox bottom sheet/buttons).
- Or fork the package and add an iOS-only option that wires custom banner controllers into `NavigationOptions` natively.

# Usage Guide

Applies to package version: `1.1.6`.

## Full-Screen Navigation

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
    revealGestureRightExclusionWidth: 80,
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

    headerTitle: "Trip",
    headerSubtitle: "Swipe up from bottom zone",
    headerBadgeText: "PRO",
    headerBadgeBackgroundColor: "#2563eb",
    headerBadgeTextColor: "#ffffff",

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
- `onDestinationPreview` and `onDestinationChanged` are Android-only.
- iOS `customRows.iconSystemName` supports SF Symbols.

## Embedded View

`MapboxNavigationView` embedded runtime usage is removed in this version due to session-conflict instability. Use full-screen `startNavigation()`.

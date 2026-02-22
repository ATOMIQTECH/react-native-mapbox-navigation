# Usage Guide

## Start Full-Screen Navigation

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
      { id: "overview", label: "Overview", variant: "secondary" },
      { id: "mute", label: "Mute", variant: "ghost" },
    ],
    builtInQuickActions: ["overview", "recenter", "toggleMute", "stop"],
    cornerRadius: 18,
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

Note:
- `showsReportFeedback`, `showsEndOfRouteFeedback`, `usesNightStyleWhileInTunnel`, `routeLineTracksTraversal`, and `annotatesIntersectionsAlongRoute` are currently accepted but no-op in this package version.
- `androidActionButtons.*` is currently accepted but no-op in this package version.
- Unsupported options are ignored and logged as native warnings.
- `bottomSheet` in `startNavigation` enables package-managed native overlay controls on iOS full-screen and Drop-In controls on Android.
- iOS full-screen supports bottom sheet style fields: `backgroundColor`, `handleColor`, `primaryTextColor`, `secondaryTextColor`, `actionButtonBackgroundColor`, `actionButtonTextColor`, `actionButtonTitle`, `secondaryActionButtonTitle`, `actionButtonBorderColor`, `actionButtonBorderWidth`, `actionButtonCornerRadius`, `cornerRadius`.
- iOS full-screen supports action behavior fields: `primaryActionButtonBehavior`, `secondaryActionButtonBehavior`.
- Typography/layout fields: `primaryTextFontSize`, `secondaryTextFontSize`, `actionButtonFontSize`, `actionButtonHeight`, `contentHorizontalPadding`, `contentBottomPadding`, `contentTopSpacing`.
- Overlay behavior fields: `showHandle`, `enableTapToToggle`, `showDefaultContent`, `defaultManeuverTitle`, `defaultTripProgressTitle`, `quickActions`, `builtInQuickActions`.

## Stop Navigation

```ts
import { stopNavigation } from "@atomiqlab/react-native-mapbox-navigation";

await stopNavigation();
```

## Listen to Events

```ts
import {
  addLocationChangeListener,
  addRouteProgressChangeListener,
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
  addBannerInstructionListener((instruction) => {
    console.log("banner", instruction.primaryText);
  }),
  addArriveListener((arrival) => {
    console.log("arrive", arrival.name);
  }),
  addBottomSheetActionPressListener((event) => {
    console.log("bottom sheet action", event.actionId);
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
import { MapboxNavigationView } from "@atomiqlab/react-native-mapbox-navigation";

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

Tip:
- For fully custom bottom sheet UI (custom React components/styles), use `MapboxNavigationView` with `bottomSheet={{ mode: "overlay" }}` and `renderBottomSheet` / `bottomSheetContent`.
- Use `onOverlayBottomSheetActionPress` to react to `quickActions`/`builtInQuickActions` taps from the package default overlay sheet.

## Platform Notes

- Android supports omitting `startOrigin` to start from current location.
- iOS supports omitting `startOrigin`; current location is resolved at runtime (with permission).
- Both platforms support embedded navigation view and camera/style customization.
- Android embedded now uses native Drop-In `NavigationView` (not a placeholder).
- Cross-platform no-op options in current package version: `showsReportFeedback`, `showsEndOfRouteFeedback`, `usesNightStyleWhileInTunnel`, `routeLineTracksTraversal`, `annotatesIntersectionsAlongRoute`, `androidActionButtons.*`.

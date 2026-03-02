# Usage

## Embedded Only

This package is embedded-only. Full-screen APIs were removed in `2.0.0`.

## Basic

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

## Overlay Sheet Behavior

- Android: custom sheet uses `collapsed` and `expanded` states.
- iOS: custom sheet uses `hidden` and `expanded` behavior (collapsed maps to hidden).

## Runtime Functions

```ts
import {
  setMuted,
  setVoiceVolume,
  setDistanceUnit,
  setLanguage,
  getNavigationSettings,
  stopNavigation,
} from "@atomiqlab/react-native-mapbox-navigation";
```

- `setMuted(muted: boolean): Promise<void>`
- `setVoiceVolume(volume: number): Promise<void>`
- `setDistanceUnit(unit: "metric" | "imperial"): Promise<void>`
- `setLanguage(language: string): Promise<void>`
- `getNavigationSettings(): Promise<NavigationSettings>`
- `stopNavigation(): Promise<boolean>`

## Listeners

```ts
import {
  addLocationChangeListener,
  addRouteChangeListener,
  addRouteProgressChangeListener,
  addJourneyDataChangeListener,
  addBannerInstructionListener,
  addArriveListener,
  addDestinationPreviewListener,
  addDestinationChangedListener,
  addCancelNavigationListener,
  addErrorListener,
  addBottomSheetActionPressListener,
} from "@atomiqlab/react-native-mapbox-navigation";
```

- `addLocationChangeListener(listener)`
- `addRouteChangeListener(listener)`
- `addRouteProgressChangeListener(listener)`
- `addJourneyDataChangeListener(listener)`
- `addBannerInstructionListener(listener)`
- `addArriveListener(listener)`
- `addDestinationPreviewListener(listener)` Android-only
- `addDestinationChangedListener(listener)` Android-only
- `addCancelNavigationListener(listener)`
- `addErrorListener(listener)`
- `addBottomSheetActionPressListener(listener)`

## `MapboxNavigationView` Props

### Core
- `enabled?: boolean`
- `style?: any`
- `startOrigin?: Coordinate`
- `destination: Waypoint`
- `waypoints?: Waypoint[]`
- `shouldSimulateRoute?: boolean`

### Voice / Locale
- `mute?: boolean`
- `voiceVolume?: number`
- `distanceUnit?: "metric" | "imperial"`
- `language?: string`

### Camera / Theme / Style
- `cameraMode?: "following" | "overview"`
- `cameraPitch?: number`
- `cameraZoom?: number`
- `uiTheme?: "system" | "light" | "dark" | "day" | "night"`
- `mapStyleUri?: string`
- `mapStyleUriDay?: string`
- `mapStyleUriNight?: string`

### Navigation UI Toggles
- `showCancelButton?: boolean`
- `routeAlternatives?: boolean`
- `showsSpeedLimits?: boolean`
- `showsWayNameLabel?: boolean`
- `showsTripProgress?: boolean`
- `showsManeuverView?: boolean`
- `showsActionButtons?: boolean`
- `showsReportFeedback?: boolean`
- `showsEndOfRouteFeedback?: boolean`
- `showsContinuousAlternatives?: boolean`
- `usesNightStyleWhileInTunnel?: boolean`
- `routeLineTracksTraversal?: boolean`
- `annotatesIntersectionsAlongRoute?: boolean`
- `androidActionButtons?: AndroidActionButtonsOptions`

### Custom Bottom Sheet
- `bottomSheet?: BottomSheetOptions`
- `bottomSheetContent?: ReactNode`
- `renderBottomSheet?: (context) => ReactNode`

`renderBottomSheet` context:
- `state: "hidden" | "collapsed" | "expanded"`
- `hidden: boolean`
- `expanded: boolean`
- `show(state?)`
- `hide()`
- `expand()`
- `collapse()`
- `toggle()`
- `bannerInstruction?`
- `routeProgress?`
- `location?`
- `stopNavigation()`
- `emitAction(actionId)`

### Component Callbacks
- `onLocationChange?(location)`
- `onRouteChange?(event)` includes route `coordinates`
- `onRouteProgressChange?(progress)`
- `onJourneyDataChange?(data)`
- `onArrive?(event)`
- `onDestinationPreview?(event)` Android-only
- `onDestinationChanged?(event)` Android-only
- `onCancelNavigation?()`
- `onError?(error)`
- `onBannerInstruction?(instruction)`
- `onOverlayBottomSheetActionPress?(event)`

## `BottomSheetOptions` (active)

- `enabled?: boolean`
- `mode?: "overlay"`
- `initialState?: "hidden" | "collapsed" | "expanded"`
- `collapsedHeight?: number`
- `collapsedBottomOffset?: number`
- `expandedHeight?: number`
- `showHandle?: boolean`
- `enableTapToToggle?: boolean`
- `overlayLocationUpdateIntervalMs?: number`
- `overlayProgressUpdateIntervalMs?: number`
- `showDefaultContent?: boolean`
- `defaultManeuverTitle?: string`
- `defaultTripProgressTitle?: string`
- `showCurrentStreet?: boolean`
- `showRemainingDistance?: boolean`
- `showRemainingDuration?: boolean`
- `showETA?: boolean`
- `showCompletionPercent?: boolean`
- `builtInQuickActions?: BottomSheetBuiltInQuickAction[]`
- `colorMode?: "light" | "dark"`: only sheet background mode (`light => #fff`, `dark => #202020`)

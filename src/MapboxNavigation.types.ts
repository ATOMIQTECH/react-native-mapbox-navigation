import type { ReactNode } from "react";

/** Geographic coordinate in WGS84 format. */
export type Coordinate = {
  /** Latitude in range -90..90 */
  latitude: number;
  /** Longitude in range -180..180 */
  longitude: number;
};

/** A route point that can optionally include a display name. */
export type Waypoint = Coordinate & {
  /** Optional label for UI/debug output. */
  name?: string;
};

/** Bottom sheet visibility/customization controls. */
export type BottomSheetOptions = {
  /** Master switch for bottom sheet sections. */
  enabled?: boolean;
  /** Show trip progress summary in the bottom area. */
  showsTripProgress?: boolean;
  /** Show maneuver/instruction content in the bottom area. */
  showsManeuverView?: boolean;
  /** Show default bottom action buttons. */
  showsActionButtons?: boolean;
  /** Rendering mode. iOS defaults to `overlay` for customizability. */
  mode?: "native" | "overlay";
  /** Initial overlay state. */
  initialState?: "collapsed" | "expanded";
  /** Overlay collapsed height in points. */
  collapsedHeight?: number;
  /** Overlay expanded height in points. */
  expandedHeight?: number;
  /** Overlay content horizontal padding. */
  contentHorizontalPadding?: number;
  /** Overlay content bottom padding. */
  contentBottomPadding?: number;
  /** Overlay content top spacing below handle. */
  contentTopSpacing?: number;
  /** Show drag/toggle handle in overlay mode. */
  showHandle?: boolean;
  /** Allow tap on sheet container/handle to toggle expanded state. */
  enableTapToToggle?: boolean;
  /** Show built-in default content cards when custom content is not provided. */
  showDefaultContent?: boolean;
  /** Default title for maneuver card in package-built overlay. */
  defaultManeuverTitle?: string;
  /** Default title for progress card in package-built overlay. */
  defaultTripProgressTitle?: string;
  /** Style override for overlay container. */
  containerStyle?: any;
  /** Style override for overlay content container. */
  contentContainerStyle?: any;
  /** Style override for overlay drag/toggle handle. */
  handleStyle?: any;
  /** iOS full-screen: background color (hex/rgb string), e.g. `#0f172a`. */
  backgroundColor?: string;
  /** iOS full-screen: handle color (hex/rgb string). */
  handleColor?: string;
  /** iOS full-screen: primary text color (hex/rgb string). */
  primaryTextColor?: string;
  /** iOS full-screen: secondary text color (hex/rgb string). */
  secondaryTextColor?: string;
  /** iOS full-screen: action button background color (hex/rgb string). */
  actionButtonBackgroundColor?: string;
  /** iOS full-screen: action button text color (hex/rgb string). */
  actionButtonTextColor?: string;
  /** iOS full-screen: action button label. */
  actionButtonTitle?: string;
  /** iOS full-screen: optional secondary action button label. */
  secondaryActionButtonTitle?: string;
  /** iOS full-screen: primary action behavior. Defaults to `stopNavigation`. */
  primaryActionButtonBehavior?: "stopNavigation" | "emitEvent";
  /** iOS full-screen: secondary action behavior. Defaults to `emitEvent`. */
  secondaryActionButtonBehavior?: "none" | "stopNavigation" | "emitEvent";
  /** iOS full-screen: action button border color. */
  actionButtonBorderColor?: string;
  /** iOS full-screen: action button border width. */
  actionButtonBorderWidth?: number;
  /** iOS full-screen: action button corner radius. */
  actionButtonCornerRadius?: number;
  /** iOS full-screen: secondary action button background color (hex/rgb string). */
  secondaryActionButtonBackgroundColor?: string;
  /** iOS full-screen: secondary action button text color (hex/rgb string). */
  secondaryActionButtonTextColor?: string;
  /** iOS full-screen: maneuver title font size. */
  primaryTextFontSize?: number;
  /** iOS full-screen: progress subtitle font size. */
  secondaryTextFontSize?: number;
  /** iOS full-screen: action button title font size. */
  actionButtonFontSize?: number;
  /** iOS full-screen: action button height. */
  actionButtonHeight?: number;
  /** iOS full-screen: corner radius (points). */
  cornerRadius?: number;
  /** Overlay quick actions rendered by package default sheet. */
  quickActions?: BottomSheetQuickAction[];
  /** Overlay prebuilt quick actions with package-managed behavior. */
  builtInQuickActions?: BottomSheetBuiltInQuickAction[];
};

/** Overlay quick action button config. */
export type BottomSheetQuickAction = {
  id: string;
  label: string;
  variant?: "primary" | "secondary" | "ghost";
};

/** Prebuilt overlay actions with package-managed behavior. */
export type BottomSheetBuiltInQuickAction =
  | "overview"
  | "recenter"
  | "mute"
  | "unmute"
  | "toggleMute"
  | "stop";

/** Android Drop-In action button controls. */
export type AndroidActionButtonsOptions = {
  showEmergencyCallButton?: boolean;
  showCancelRouteButton?: boolean;
  showRefreshRouteButton?: boolean;
  showReportFeedbackButton?: boolean;
  showToggleAudioButton?: boolean;
  showSearchAlongRouteButton?: boolean;
  showStartNavigationButton?: boolean;
  showEndNavigationButton?: boolean;
  showAlternativeRoutesButton?: boolean;
  showStartNavigationFeedbackButton?: boolean;
  showEndNavigationFeedbackButton?: boolean;
};

/**
 * Options passed to `startNavigation`.
 */
export type NavigationOptions = {
  /** Optional route origin. If omitted, native layer may resolve current location. */
  startOrigin?: Coordinate;
  /** Final destination waypoint (required). */
  destination: Waypoint;
  /** Optional intermediate waypoints. */
  waypoints?: Waypoint[];
  /** Enable route simulation for testing without physical movement. */
  shouldSimulateRoute?: boolean;
  /** UI theme selection for native UI. */
  uiTheme?: 'system' | 'light' | 'dark' | 'day' | 'night';
  /** Request alternative routes when available. */
  routeAlternatives?: boolean;
  /** Distance unit for guidance. */
  distanceUnit?: 'metric' | 'imperial';
  /** Guidance language (for example `en`, `fr`). */
  language?: string;
  /** Start muted. */
  mute?: boolean;
  /** Voice volume range `0..1`. */
  voiceVolume?: number;
  /** Camera pitch range `0..85`. */
  cameraPitch?: number;
  /** Camera zoom range `1..22`. */
  cameraZoom?: number;
  /** Camera behavior mode. */
  cameraMode?: 'following' | 'overview';
  /** One style URI used for both day/night fallback. */
  mapStyleUri?: string;
  /** Day style URI override. */
  mapStyleUriDay?: string;
  /** Night style URI override. */
  mapStyleUriNight?: string;
  /** Show speed limit panel when supported. */
  showsSpeedLimits?: boolean;
  /** Show current road name label. */
  showsWayNameLabel?: boolean;
  /** Show trip progress summary. */
  showsTripProgress?: boolean;
  /** Show maneuver/instruction view. */
  showsManeuverView?: boolean;
  /** Show default action buttons in native UI. */
  showsActionButtons?: boolean;
  /** Show report feedback action when supported by native SDK. */
  showsReportFeedback?: boolean;
  /** Show end-of-route feedback action when supported by native SDK. */
  showsEndOfRouteFeedback?: boolean;
  /** Show continuous alternatives in native UI when supported. */
  showsContinuousAlternatives?: boolean;
  /** Keep night style when entering tunnels when supported. */
  usesNightStyleWhileInTunnel?: boolean;
  /** Draw traversed route line differently when supported. */
  routeLineTracksTraversal?: boolean;
  /** Annotate intersections along route when supported. */
  annotatesIntersectionsAlongRoute?: boolean;
  /** Fine-grained Android Drop-In action button controls. */
  androidActionButtons?: AndroidActionButtonsOptions;
  /** Bottom sheet controls (expanded into section visibility toggles). */
  bottomSheet?: BottomSheetOptions;
};

/** Runtime settings/state returned by `getNavigationSettings()`. */
export type NavigationSettings = {
  isNavigating: boolean;
  mute: boolean;
  voiceVolume: number;
  distanceUnit: 'metric' | 'imperial';
  language: string;
};

/** Location update payload emitted by native layer. */
export type LocationUpdate = {
  latitude: number;
  longitude: number;
  bearing?: number;
  speed?: number;
  altitude?: number;
  accuracy?: number;
};

/** Route progress payload emitted by native layer. */
export type RouteProgress = {
  distanceTraveled: number;
  distanceRemaining: number;
  durationRemaining: number;
  fractionTraveled: number;
};

/** Arrival event payload. */
export type ArrivalEvent = {
  index?: number;
  name?: string;
};

/** Destination preview event payload. */
export type DestinationPreviewEvent = {
  active: true;
};

/** Destination changed event payload. */
export type DestinationChangedEvent = {
  latitude: number;
  longitude: number;
};

/** Error payload emitted by native layer. */
export type NavigationError = {
  /** Machine-readable error code. */
  code: string;
  /** Developer-readable error details. */
  message: string;
};

/** Banner instruction payload emitted during guidance. */
export type BannerInstruction = {
  primaryText: string;
  secondaryText?: string;
  stepDistanceRemaining?: number;
};

/** Full-screen bottom-sheet action event payload. */
export type BottomSheetActionEvent = {
  actionId: "primary" | "secondary";
};

/** Event subscription handle. */
export type Subscription = {
  remove: () => void;
};

/** Native module interface bridged from iOS/Android. */
export interface MapboxNavigationModule {
  startNavigation(options: NavigationOptions): Promise<void>;
  stopNavigation(): Promise<void>;
  setMuted(muted: boolean): Promise<void>;
  setVoiceVolume(volume: number): Promise<void>;
  setDistanceUnit(unit: 'metric' | 'imperial'): Promise<void>;
  setLanguage(language: string): Promise<void>;
  isNavigating(): Promise<boolean>;
  getNavigationSettings(): Promise<NavigationSettings>;
}

/** Props for embedded `MapboxNavigationView`. */
export interface MapboxNavigationViewProps {
  style?: any;
  startOrigin?: Coordinate;
  destination: Waypoint;
  waypoints?: Waypoint[];
  shouldSimulateRoute?: boolean;
  showCancelButton?: boolean;
  uiTheme?: 'system' | 'light' | 'dark' | 'day' | 'night';
  distanceUnit?: 'metric' | 'imperial';
  language?: string;
  mute?: boolean;
  voiceVolume?: number;
  cameraPitch?: number;
  cameraZoom?: number;
  cameraMode?: 'following' | 'overview';
  mapStyleUri?: string;
  mapStyleUriDay?: string;
  mapStyleUriNight?: string;
  routeAlternatives?: boolean;
  showsSpeedLimits?: boolean;
  showsWayNameLabel?: boolean;
  showsTripProgress?: boolean;
  showsManeuverView?: boolean;
  showsActionButtons?: boolean;
  showsReportFeedback?: boolean;
  showsEndOfRouteFeedback?: boolean;
  showsContinuousAlternatives?: boolean;
  usesNightStyleWhileInTunnel?: boolean;
  routeLineTracksTraversal?: boolean;
  annotatesIntersectionsAlongRoute?: boolean;
  androidActionButtons?: AndroidActionButtonsOptions;
  /** Bottom sheet controls (expanded into section visibility toggles). */
  bottomSheet?: BottomSheetOptions;
  /** Static custom content rendered inside overlay bottom sheet. */
  bottomSheetContent?: ReactNode;
  /** Advanced custom sheet renderer. */
  renderBottomSheet?: (context: {
    expanded: boolean;
    expand: () => void;
    collapse: () => void;
    toggle: () => void;
    bannerInstruction?: BannerInstruction;
    routeProgress?: RouteProgress;
    location?: LocationUpdate;
    stopNavigation: () => Promise<void>;
    emitAction: (actionId: string) => void;
  }) => ReactNode;
  /** Optional children overlayed above native navigation view. */
  children?: ReactNode;

  /** Callback for location changes. */
  onLocationChange?: (location: LocationUpdate) => void;
  /** Callback for route progress changes. */
  onRouteProgressChange?: (progress: RouteProgress) => void;
  /** Callback when arrival is detected. */
  onArrive?: (point: ArrivalEvent) => void;
  /** Android: callback when destination preview is shown. */
  onDestinationPreview?: (event: DestinationPreviewEvent) => void;
  /** Android: callback when destination changes. */
  onDestinationChanged?: (event: DestinationChangedEvent) => void;
  /** Callback when navigation is canceled by user. */
  onCancelNavigation?: () => void;
  /** Callback for native errors. */
  onError?: (error: NavigationError) => void;
  /** Callback for banner instruction updates. */
  onBannerInstruction?: (instruction: BannerInstruction) => void;
  /** Embedded overlay-only callback for quick/custom sheet actions. */
  onOverlayBottomSheetActionPress?: (event: {
    actionId: string;
    source: "builtin" | "custom";
  }) => void;
}

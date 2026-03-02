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
  /** Rendering mode.
   * - `overlay`: embedded React overlay mode.
   *
   * This package uses custom-sheet overlay mode for embedded navigation.
   */
  mode?: "overlay";
  /** Initial overlay/custom-native state. */
  initialState?: "hidden" | "collapsed" | "expanded";
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
  /** Embedded overlay mode: minimum interval for location-driven overlay rerenders (ms). */
  overlayLocationUpdateIntervalMs?: number;
  /** Embedded overlay mode: minimum interval for progress-driven overlay rerenders (ms). */
  overlayProgressUpdateIntervalMs?: number;
  /** Overlay mode: reveal custom sheet when user swipes up from the bottom hot-zone. */
  revealOnNativeBannerGesture?: boolean;
  /** Hidden gesture-reveal zone height in points/dp (default `100`). */
  revealGestureHotzoneHeight?: number;
  /** Right-side exclusion width (points/dp, default `80`) to avoid blocking native buttons. */
  revealGestureRightExclusionWidth?: number;
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
  /** Overlay sheet: background color (hex/rgb string), e.g. `#0f172a`. */
  backgroundColor?: string;
  /** Overlay sheet: handle color (hex/rgb string). */
  handleColor?: string;
  /** Overlay sheet: primary text color (hex/rgb string). */
  primaryTextColor?: string;
  /** Overlay sheet: secondary text color (hex/rgb string). */
  secondaryTextColor?: string;
  /** Overlay sheet: action button background color (hex/rgb string). */
  actionButtonBackgroundColor?: string;
  /** Overlay sheet: action button text color (hex/rgb string). */
  actionButtonTextColor?: string;
  /** Overlay sheet: action button label. */
  actionButtonTitle?: string;
  /** Overlay sheet: optional secondary action button label. */
  secondaryActionButtonTitle?: string;
  /** Overlay sheet: primary action behavior. */
  primaryActionButtonBehavior?: "emitEvent";
  /** Overlay sheet: secondary action behavior. */
  secondaryActionButtonBehavior?: "none" | "emitEvent";
  /** Overlay sheet: action button border color. */
  actionButtonBorderColor?: string;
  /** Overlay sheet: action button border width. */
  actionButtonBorderWidth?: number;
  /** Overlay sheet: action button corner radius. */
  actionButtonCornerRadius?: number;
  /** Overlay sheet: secondary action button background color (hex/rgb string). */
  secondaryActionButtonBackgroundColor?: string;
  /** Overlay sheet: secondary action button text color (hex/rgb string). */
  secondaryActionButtonTextColor?: string;
  /** Overlay sheet: maneuver title font size. */
  primaryTextFontSize?: number;
  /** Bottom sheet: primary text font family name (native platform font). */
  primaryTextFontFamily?: string;
  /** Bottom sheet: primary text font weight (`normal`, `medium`, `semibold`, `bold`, `100..900`). */
  primaryTextFontWeight?: string;
  /** Overlay sheet: progress subtitle font size. */
  secondaryTextFontSize?: number;
  /** Bottom sheet: secondary text font family name (native platform font). */
  secondaryTextFontFamily?: string;
  /** Bottom sheet: secondary text font weight (`normal`, `medium`, `semibold`, `bold`, `100..900`). */
  secondaryTextFontWeight?: string;
  /** Overlay sheet: action button title font size. */
  actionButtonFontSize?: number;
  /** Bottom sheet: action button font family name (native platform font). */
  actionButtonFontFamily?: string;
  /** Bottom sheet: action button font weight (`normal`, `medium`, `semibold`, `bold`, `100..900`). */
  actionButtonFontWeight?: string;
  /** Overlay sheet: action button height. */
  actionButtonHeight?: number;
  /** Bottom spacing after action buttons in custom-native/overlay sheets. */
  actionButtonsBottomPadding?: number;
  /** Quick action (primary variant) background color. */
  quickActionBackgroundColor?: string;
  /** Quick action (primary variant) text color. */
  quickActionTextColor?: string;
  /** Quick action (secondary variant) background color. */
  quickActionSecondaryBackgroundColor?: string;
  /** Quick action (secondary variant) text color. */
  quickActionSecondaryTextColor?: string;
  /** Quick action (ghost variant) text color. */
  quickActionGhostTextColor?: string;
  /** Quick action border color. */
  quickActionBorderColor?: string;
  /** Quick action border width. */
  quickActionBorderWidth?: number;
  /** Quick action corner radius. */
  quickActionCornerRadius?: number;
  /** Quick action font family name (native platform font). */
  quickActionFontFamily?: string;
  /** Quick action font weight (`normal`, `medium`, `semibold`, `bold`, `100..900`). */
  quickActionFontWeight?: string;
  /** Overlay sheet: corner radius (points). */
  cornerRadius?: number;
  /** Show current street/road label in package-rendered sheet content. */
  showCurrentStreet?: boolean;
  /** Show remaining distance in package-rendered sheet content. */
  showRemainingDistance?: boolean;
  /** Show remaining duration in package-rendered sheet content. */
  showRemainingDuration?: boolean;
  /** Show ETA in package-rendered sheet content. */
  showETA?: boolean;
  /** Show completion percentage in package-rendered sheet content. */
  showCompletionPercent?: boolean;
  /** Overlay quick actions rendered by package default sheet. */
  quickActions?: BottomSheetQuickAction[];
  /** Overlay prebuilt quick actions with package-managed behavior. */
  builtInQuickActions?: BottomSheetBuiltInQuickAction[];
  /** Extra content rows for package-rendered overlay sheet. */
  customRows?: BottomSheetCustomRow[];
  /** Optional top header title. */
  headerTitle?: string;
  /** Header title font size. */
  headerTitleFontSize?: number;
  /** Header title font family name. */
  headerTitleFontFamily?: string;
  /** Header title font weight. */
  headerTitleFontWeight?: string;
  /** Optional top header subtitle. */
  headerSubtitle?: string;
  /** Header subtitle font size. */
  headerSubtitleFontSize?: number;
  /** Header subtitle font family name. */
  headerSubtitleFontFamily?: string;
  /** Header subtitle font weight. */
  headerSubtitleFontWeight?: string;
  /** Optional header badge text on trailing side. */
  headerBadgeText?: string;
  /** Header badge font size. */
  headerBadgeFontSize?: number;
  /** Header badge font family name. */
  headerBadgeFontFamily?: string;
  /** Header badge font weight. */
  headerBadgeFontWeight?: string;
  /** Optional header badge background color. */
  headerBadgeBackgroundColor?: string;
  /** Optional header badge text color. */
  headerBadgeTextColor?: string;
  /** Header badge corner radius. */
  headerBadgeCornerRadius?: number;
  /** Header badge border color. */
  headerBadgeBorderColor?: string;
  /** Header badge border width. */
  headerBadgeBorderWidth?: number;
};

/** Overlay quick action button config. */
export type BottomSheetQuickAction = {
  id: string;
  label: string;
  variant?: "primary" | "secondary" | "ghost";
};

/** Extra row item for package overlay sheet. */
export type BottomSheetCustomRow = {
  id: string;
  /** Optional SF Symbol name for native icon (for example: `car.fill`). */
  iconSystemName?: string;
  /** Optional fallback icon text/emoji. */
  iconText?: string;
  title: string;
  value?: string;
  subtitle?: string;
  emphasis?: boolean;
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

/** Runtime settings/state returned by `getNavigationSettings()`. */
export type NavigationSettings = {
  isNavigating: boolean;
  mute: boolean;
  voiceVolume: number;
  distanceUnit: "metric" | "imperial";
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

/** Structured journey data suitable for custom bottom banner UIs. */
export type JourneyData = {
  latitude?: number;
  longitude?: number;
  bearing?: number;
  speed?: number;
  altitude?: number;
  accuracy?: number;
  primaryInstruction?: string;
  secondaryInstruction?: string;
  currentStreet?: string;
  stepDistanceRemaining?: number;
  distanceRemaining?: number;
  durationRemaining?: number;
  fractionTraveled?: number;
  completionPercent?: number;
  etaIso8601?: string;
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

/** Overlay bottom-sheet action event payload. */
export type BottomSheetActionEvent = {
  actionId: "primary" | "secondary" | "cancel" | string;
};

/** Event subscription handle. */
export type Subscription = {
  remove: () => void;
};

/** Native module interface bridged from iOS/Android. */
export interface MapboxNavigationModule {
  setMuted(muted: boolean): Promise<void>;
  setVoiceVolume(volume: number): Promise<void>;
  setDistanceUnit(unit: "metric" | "imperial"): Promise<void>;
  setLanguage(language: string): Promise<void>;
  getNavigationSettings(): Promise<NavigationSettings>;
}

export interface MapboxNavigationViewProps {
  /** Explicitly opt in to embedded navigation startup. Defaults to `false`. */
  enabled?: boolean;
  style?: any;
  startOrigin?: Coordinate;
  destination: Waypoint;
  waypoints?: Waypoint[];
  shouldSimulateRoute?: boolean;
  showCancelButton?: boolean;
  uiTheme?: "system" | "light" | "dark" | "day" | "night";
  distanceUnit?: "metric" | "imperial";
  language?: string;
  mute?: boolean;
  voiceVolume?: number;
  cameraPitch?: number;
  cameraZoom?: number;
  cameraMode?: "following" | "overview";
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
    state: "hidden" | "collapsed" | "expanded";
    hidden: boolean;
    expanded: boolean;
    show: (state?: "collapsed" | "expanded") => void;
    hide: () => void;
    expand: () => void;
    collapse: () => void;
    toggle: () => void;
    bannerInstruction?: BannerInstruction;
    routeProgress?: RouteProgress;
    location?: LocationUpdate;
    emitAction: (actionId: string) => void;
  }) => ReactNode;
  /** Optional children overlayed above native navigation view. */
  children?: ReactNode;

  /** Callback for location changes. */
  onLocationChange?: (location: LocationUpdate) => void;
  /** Callback for route progress changes. */
  onRouteProgressChange?: (progress: RouteProgress) => void;
  /** Callback with aggregated journey data for custom UI rendering. */
  onJourneyDataChange?: (data: JourneyData) => void;
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

import type { ComponentType, ReactNode } from 'react'
import type { StyleProp, ViewStyle } from 'react-native'

/** Geographic coordinate in WGS84 format. */
export type Coordinate = {
  /** Latitude in range -90..90 */
  latitude: number
  /** Longitude in range -180..180 */
  longitude: number
}

/** A route point that can optionally include a display name. */
export type Waypoint = Coordinate & {
  /** Optional label for UI/debug output. */
  name?: string
}

/** Bottom sheet visibility/customization controls. */
export type BottomSheetOptions = {
  /** Master switch for bottom sheet sections. */
  enabled?: boolean
  /** Show trip progress summary in the bottom area. */
  showsTripProgress?: boolean
  /** Show maneuver/instruction content in the bottom area. */
  showsManeuverView?: boolean
  /** Show default bottom action buttons. */
  showsActionButtons?: boolean
  /** Rendering mode.
   * - `overlay`: embedded React overlay mode.
   *
   * This package uses custom-sheet overlay mode for embedded navigation.
   */
  mode?: 'overlay'
  /** Initial overlay/custom-native state. */
  initialState?: 'hidden' | 'collapsed' | 'expanded'
  /** Overlay collapsed height in points. */
  collapsedHeight?: number
  /** Overlay collapsed state vertical offset (positive moves sheet lower). */
  collapsedBottomOffset?: number
  /** Overlay expanded height in points. */
  expandedHeight?: number
  /** Overlay sheet color mode. `light` => `#fff`, `dark` => `#202020`. */
  colorMode?: 'light' | 'dark'
  /** Show drag/toggle handle in overlay mode. */
  showHandle?: boolean
  /** Allow tap on sheet container/handle to toggle expanded state. */
  enableTapToToggle?: boolean
  /** Embedded overlay mode: minimum interval for location-driven overlay rerenders (ms). */
  overlayLocationUpdateIntervalMs?: number
  /** Embedded overlay mode: minimum interval for progress-driven overlay rerenders (ms). */
  overlayProgressUpdateIntervalMs?: number
  /** Overlay mode: reveal custom sheet when user swipes up from the bottom hot-zone. */
  revealOnNativeBannerGesture?: boolean
  /** Hidden gesture-reveal zone height in points/dp (default `100`). */
  revealGestureHotzoneHeight?: number
  /** Right-side exclusion width (points/dp, default `80`) to avoid blocking native buttons. */
  revealGestureRightExclusionWidth?: number
  /** Show built-in default content cards when custom content is not provided. */
  showDefaultContent?: boolean
  /** Default title for maneuver card in package-built overlay. */
  defaultManeuverTitle?: string
  /** Default title for progress card in package-built overlay. */
  defaultTripProgressTitle?: string
  /** Overlay sheet: primary action behavior. */
  primaryActionButtonBehavior?: 'emitEvent'
  /** Overlay sheet: secondary action behavior. */
  secondaryActionButtonBehavior?: 'none' | 'emitEvent'
  /** Show current street/road label in package-rendered sheet content. */
  showCurrentStreet?: boolean
  /** Show remaining distance in package-rendered sheet content. */
  showRemainingDistance?: boolean
  /** Show remaining duration in package-rendered sheet content. */
  showRemainingDuration?: boolean
  /** Show ETA in package-rendered sheet content. */
  showETA?: boolean
  /** Show completion percentage in package-rendered sheet content. */
  showCompletionPercent?: boolean
  /** Overlay prebuilt quick actions with package-managed behavior. */
  builtInQuickActions?: BottomSheetBuiltInQuickAction[]
}

/** Prebuilt overlay actions with package-managed behavior. */
export type BottomSheetBuiltInQuickAction =
  | 'overview'
  | 'recenter'
  | 'mute'
  | 'unmute'
  | 'toggleMute'
  | 'stop'

/** Android Drop-In action button controls. */
export type AndroidActionButtonsOptions = {
  showEmergencyCallButton?: boolean
  showCancelRouteButton?: boolean
  showRefreshRouteButton?: boolean
  showReportFeedbackButton?: boolean
  showToggleAudioButton?: boolean
  showSearchAlongRouteButton?: boolean
  showStartNavigationButton?: boolean
  showEndNavigationButton?: boolean
  showAlternativeRoutesButton?: boolean
  showStartNavigationFeedbackButton?: boolean
  showEndNavigationFeedbackButton?: boolean
}

/** Built-in native floating/map controls visibility. */
export type NativeFloatingButtonsOptions = {
  /** iOS: overview button. */
  showOverviewButton?: boolean
  /** iOS + Android: mute/audio-guidance button. */
  showAudioGuidanceButton?: boolean
  /** iOS: feedback/report button. */
  showFeedbackButton?: boolean
  /** Android: camera-mode button. */
  showCameraModeButton?: boolean
  /** Android: recenter button. */
  showRecenterButton?: boolean
  /** Android: compass button. */
  showCompassButton?: boolean
}

/** Runtime settings/state returned by `getNavigationSettings()`. */
export type NavigationSettings = {
  isNavigating: boolean
  isCameraFollowing: boolean
  isCameraNotFollowing: boolean
  mute: boolean
  voiceVolume: number
  distanceUnit: 'metric' | 'imperial'
  language: string
}

export type CameraFollowingState = {
  isCameraFollowing: boolean
  isCameraNotFollowing: boolean
  reason?: string
}

/** Location update payload emitted by native layer. */
export type LocationUpdate = {
  latitude: number
  longitude: number
  bearing?: number
  speed?: number
  altitude?: number
  accuracy?: number
}

/** Route progress payload emitted by native layer. */
export type RouteProgress = {
  distanceTraveled: number
  distanceRemaining: number
  durationRemaining: number
  fractionTraveled: number
}

/** Route geometry payload emitted when a new route is selected/used. */
export type RouteChangeEvent = {
  coordinates: Coordinate[]
}

/** Structured journey data suitable for custom bottom banner UIs. */
export type JourneyData = {
  latitude?: number
  longitude?: number
  bearing?: number
  speed?: number
  altitude?: number
  accuracy?: number
  primaryInstruction?: string
  secondaryInstruction?: string
  currentStreet?: string
  stepDistanceRemaining?: number
  distanceRemaining?: number
  durationRemaining?: number
  fractionTraveled?: number
  completionPercent?: number
  etaIso8601?: string
}

/** Arrival event payload. */
export type ArrivalEvent = {
  index?: number
  name?: string
}

/** Destination preview event payload. */
export type DestinationPreviewEvent = {
  active: true
}

/** Destination changed event payload. */
export type DestinationChangedEvent = {
  latitude: number
  longitude: number
}

/** Error payload emitted by native layer. */
export type NavigationError = {
  /** Machine-readable error code. */
  code: string
  /** Developer-readable error details. */
  message: string
}

/** Banner instruction payload emitted during guidance. */
export type BannerInstruction = {
  primaryText: string
  secondaryText?: string
  stepDistanceRemaining?: number
}

/** Overlay bottom-sheet action event payload. */
export type BottomSheetActionEvent = {
  actionId: 'primary' | 'secondary' | 'cancel' | string
}

/** Overlay sheet visibility state used by the JS renderer layer. */
export type OverlayBottomSheetState = 'hidden' | 'collapsed' | 'expanded'

/** Shared overlay controls exposed to JS render callbacks. */
export type OverlaySheetController = {
  show: (state?: 'collapsed' | 'expanded') => void
  hide: () => void
  expand: () => void
  collapse: () => void
  toggle: () => void
}

/** Context exposed to floating-button renderers. */
export type FloatingButtonsRenderContext = OverlaySheetController & {
  bannerInstruction?: BannerInstruction
  routeProgress?: RouteProgress
  location?: LocationUpdate
  stopNavigation: () => Promise<boolean>
  emitAction: (actionId: string) => void
}

/** Context exposed to custom bottom-sheet renderers. */
export type BottomSheetRenderContext = FloatingButtonsRenderContext & {
  state: OverlayBottomSheetState
  hidden: boolean
  expanded: boolean
}

/** End-of-route feedback submission payload from the package modal. */
export type EndOfRouteFeedbackEvent = {
  rating: number
  arrival?: ArrivalEvent
}

/** Context exposed to end-of-route feedback renderers. */
export type EndOfRouteFeedbackRenderContext = {
  arrival?: ArrivalEvent
  dismiss: () => void
  submitRating: (rating: number) => void
  stopNavigation: () => Promise<boolean>
}

/** Default-styled floating button props matching the package overlay controls. */
export type MapboxNavigationFloatingButtonProps = {
  children?: ReactNode
  onPress?: () => void
  disabled?: boolean
  accessibilityLabel?: string
  style?: StyleProp<ViewStyle>
  testID?: string
}

/** Default stack wrapper for floating buttons aligned to the native control rail. */
export type MapboxNavigationFloatingButtonsStackProps = {
  children?: ReactNode
  style?: StyleProp<ViewStyle>
}

/** Event subscription handle. */
export type Subscription = {
  remove: () => void
}

/** Native module interface bridged from iOS/Android. */
export interface MapboxNavigationModule {
  setMuted(muted: boolean): Promise<void>
  setVoiceVolume(volume: number): Promise<void>
  setDistanceUnit(unit: 'metric' | 'imperial'): Promise<void>
  setLanguage(language: string): Promise<void>
  getNavigationSettings(): Promise<NavigationSettings>
  stopNavigation(): Promise<boolean>
  resumeCameraFollowing(): Promise<boolean>
}

export interface MapboxNavigationViewProps {
  /** Explicitly opt in to embedded navigation startup. Defaults to `false`. */
  enabled?: boolean
  style?: any
  startOrigin?: Coordinate
  destination: Waypoint
  waypoints?: Waypoint[]
  shouldSimulateRoute?: boolean
  showCancelButton?: boolean
  uiTheme?: 'system' | 'light' | 'dark' | 'day' | 'night'
  distanceUnit?: 'metric' | 'imperial'
  language?: string
  mute?: boolean
  voiceVolume?: number
  cameraPitch?: number
  cameraZoom?: number
  cameraMode?: 'following' | 'overview'
  mapStyleUri?: string
  mapStyleUriDay?: string
  mapStyleUriNight?: string
  routeAlternatives?: boolean
  showsSpeedLimits?: boolean
  showsWayNameLabel?: boolean
  showsTripProgress?: boolean
  showsManeuverView?: boolean
  showsActionButtons?: boolean
  showsReportFeedback?: boolean
  /** Opt in to the package-managed end-of-route rating modal in embedded mode. */
  showsEndOfRouteFeedback?: boolean
  showsContinuousAlternatives?: boolean
  usesNightStyleWhileInTunnel?: boolean
  routeLineTracksTraversal?: boolean
  annotatesIntersectionsAlongRoute?: boolean
  androidActionButtons?: AndroidActionButtonsOptions
  /** Visibility for built-in native floating/map buttons. */
  nativeFloatingButtons?: NativeFloatingButtonsOptions
  /** Bottom sheet controls (expanded into section visibility toggles). */
  bottomSheet?: BottomSheetOptions
  /** Static custom content rendered inside overlay bottom sheet. */
  bottomSheetContent?: ReactNode
  /** Advanced custom sheet renderer. */
  renderBottomSheet?: (context: BottomSheetRenderContext) => ReactNode
  /** React component type rendered inside the overlay bottom sheet. */
  bottomSheetComponent?: ComponentType<BottomSheetRenderContext>
  /** Static floating action content rendered above the native navigation UI. */
  floatingButtons?: ReactNode
  /** Advanced floating action renderer. */
  renderFloatingButtons?: (context: FloatingButtonsRenderContext) => ReactNode
  /** React component type rendered for floating buttons. */
  floatingButtonsComponent?: ComponentType<FloatingButtonsRenderContext>
  /** Hide custom floating buttons once the destination is reached. Defaults to `true`. */
  hideFloatingButtonsOnArrival?: boolean
  /** Override the default floating button anchor container position. */
  floatingButtonsContainerStyle?: StyleProp<ViewStyle>
  /** Advanced custom renderer for the end-of-route feedback modal. */
  renderEndOfRouteFeedback?: (context: EndOfRouteFeedbackRenderContext) => ReactNode
  /** React component type rendered for the end-of-route feedback modal. */
  endOfRouteFeedbackComponent?: ComponentType<EndOfRouteFeedbackRenderContext>
  /** Optional children overlayed above native navigation view. */
  children?: ReactNode

  /** Callback for location changes. */
  onLocationChange?: (location: LocationUpdate) => void
  /** Callback for route progress changes. */
  onRouteProgressChange?: (progress: RouteProgress) => void
  /** Callback when camera-following state changes (for example after map pan gesture). */
  onCameraFollowingStateChange?: (state: CameraFollowingState) => void
  /** Callback when active route geometry changes. */
  onRouteChange?: (event: RouteChangeEvent) => void
  /** Callback with aggregated journey data for custom UI rendering. */
  onJourneyDataChange?: (data: JourneyData) => void
  /** Callback when arrival is detected. */
  onArrive?: (point: ArrivalEvent) => void
  /** Android: callback when destination preview is shown. */
  onDestinationPreview?: (event: DestinationPreviewEvent) => void
  /** Android: callback when destination changes. */
  onDestinationChanged?: (event: DestinationChangedEvent) => void
  /** Callback when navigation is canceled by user. */
  onCancelNavigation?: () => void
  /** Callback for native errors. */
  onError?: (error: NavigationError) => void
  /** Callback for banner instruction updates. */
  onBannerInstruction?: (instruction: BannerInstruction) => void
  /** Callback when the package-managed end-of-route rating modal submits a score. */
  onEndOfRouteFeedbackSubmit?: (event: EndOfRouteFeedbackEvent) => void
  /** Embedded overlay-only callback for quick/custom sheet actions. */
  onOverlayBottomSheetActionPress?: (event: {
    actionId: string
    source: 'builtin' | 'custom'
  }) => void
}

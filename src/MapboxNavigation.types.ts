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

export type NavigationMarkerVariant =
  | 'default'
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'muted'

export type NavigationMarkerSize = 'small' | 'medium' | 'large'

/**
 * Visual style of the marker bubble.
 * - `pin` (default): round bubble with a downward pointer tail.
 * - `dot`: simple circle with no tail.
 */
export type NavigationMarkerStyle = 'pin' | 'dot'

/**
 * A display-only annotation rendered directly on the embedded navigation map.
 *
 * Markers are purely visual — they do not affect routing. Use `waypoints` for
 * intermediate route stops.
 *
 * @example
 * ```tsx
 * navigationMarkers={[
 *   {
 *     id: 'pickup-1',
 *     latitude: 37.7858,
 *     longitude: -122.4064,
 *     label: 'Pickup – Alice',
 *     glyph: 'P',
 *     badge: '2',
 *     variant: 'primary',
 *     size: 'large',
 *   },
 *   {
 *     id: 'dropoff-1',
 *     latitude: 37.7901,
 *     longitude: -122.4019,
 *     label: 'Dropoff – Alice',
 *     glyph: 'D',
 *     color: '#7C3AED',
 *     markerStyle: 'dot',
 *   },
 * ]}
 * ```
 */
export type NavigationMarker = Coordinate & {
  /** Stable identifier — used to update or remove an existing marker in place. */
  id: string
  /** Accessibility label and debug description. */
  label?: string
  /** Short glyph rendered inside the bubble (max 2 characters). */
  glyph?: string
  /** Badge text rendered in the upper-right corner of the bubble (max 3 characters). */
  badge?: string
  /**
   * Semantic color variant. Use `color` for a fully custom fill.
   * Defaults to `'default'` (dark charcoal).
   */
  variant?: NavigationMarkerVariant
  /**
   * Custom fill color as a hex string — `#RGB`, `#RRGGBB` or `#RRGGBBAA`
   * (e.g. `"#2563EB"`), read the same way on both platforms.
   * Takes priority over `variant` when provided.
   *
   * Note that an eight-digit value's alpha is applied to the marker's fill,
   * and then `opacity` multiplies the marker as a whole.
   */
  color?: string
  /**
   * Custom badge background color (hex string, same forms as `color`).
   * Defaults to a darkened shade of `color` or the `variant` badge color.
   */
  badgeColor?: string
  /**
   * Marker opacity in the range 0..1.
   * Overrides the automatic opacity derived from `variant` and `selected`.
   */
  opacity?: number
  /** Size preset. Defaults to `'medium'`. */
  size?: NavigationMarkerSize
  /**
   * Visual style of the marker bubble.
   * - `'pin'` (default): bubble with a downward pointer tail.
   * - `'dot'`: simple circle, no tail.
   */
  markerStyle?: NavigationMarkerStyle
  /**
   * Whether to render the pointer tail. Only applies to `markerStyle: 'pin'`.
   * Defaults to `true`.
   */
  showTail?: boolean
  /** Forwarded to the native view-annotation `selected` state. */
  selected?: boolean
  /** Allow the marker to overlap other annotations. Defaults to `true`. */
  allowOverlap?: boolean
  /**
   * Custom Y-axis pixel offset from the map anchor point.
   * Positive values move the marker upward. Overrides the size-preset default.
   */
  anchorOffsetY?: number
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

/**
 * Android Drop-In action button controls.
 *
 * @deprecated Not supported. The Android embedded view uses Mapbox's Drop-In
 * UI, which does not expose these buttons individually, and this object is
 * dropped before it reaches the native layer. Use {@link NativeFloatingButtonsOptions}
 * to control the Drop-In action buttons that *are* configurable, or render your
 * own controls with `floatingButtons` / `renderFloatingButtons`.
 */
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
  /**
   * Index of the active route leg on a multi-waypoint route. `0` is the leg
   * from the origin to the first waypoint. Advances as waypoints are reached
   * (or via `advanceToNextWaypoint`). Undefined on single-leg routes/platforms
   * that do not report it.
   */
  legIndex?: number
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
  /** Zero-based index of the completed route leg, when the SDK reports one. */
  index?: number
  name?: string
  /**
   * `true` for the final destination, `false` for an intermediate stop.
   *
   * Previously every waypoint arrival looked identical, so on a multi-stop
   * route the end-of-route flow fired at the first stop.
   */
  isFinalDestination?: boolean
  /** Number of stops still ahead. */
  remainingWaypoints?: number
}

/** Emitted when the user leaves or rejoins the active route. */
export type OffRouteEvent = {
  /** `true` when off-route (rerouting), `false` when back on the route. */
  offRoute?: boolean
  /** iOS: the location the reroute was triggered from, when available. */
  latitude?: number
  longitude?: number
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
  advanceToNextWaypoint(): Promise<boolean>
}

/**
 * Which Mapbox Directions profile to route with.
 *
 * Defaults to `driving-traffic`, which is what this package has always used and
 * the only profile it could reach before 3.0.0.
 *
 * `driving-traffic` is the only profile with live traffic, so the congestion
 * colours in {@link MapboxNavigationColors} have nothing to shade on the other
 * three — a route requested as `walking` renders in the plain `routeLine`
 * colour throughout.
 */
export type RouteProfile = 'driving-traffic' | 'driving' | 'walking' | 'cycling'

/**
 * Road classes the route should avoid.
 *
 * These are the classes both platforms support. The Directions API treats them
 * as hints, not guarantees: if avoiding one would make the trip impossible it
 * routes through anyway.
 *
 * On the `walking` and `cycling` profiles only `ferry` and `cashOnlyTolls` are
 * accepted — the API rejects the whole request for the others rather than
 * ignoring them, so the wrapper drops them and reports
 * `ROAD_CLASS_NOT_SUPPORTED_BY_PROFILE` through `onError`.
 */
export type ExcludedRoadClass =
  | 'toll'
  | 'motorway'
  | 'ferry'
  | 'tunnel'
  | 'restricted'
  | 'unpaved'
  | 'cashOnlyTolls'

/**
 * Things to keep the route away from.
 *
 * ```tsx
 * <MapboxNavigationView
 *   destination={destination}
 *   routeExclusions={{
 *     roadClasses: ['toll', 'ferry'],
 *     locations: [{ latitude: 37.79, longitude: -122.41 }],
 *   }}
 * />
 * ```
 */
export interface RouteExclusions {
  /** Road classes to route around. */
  roadClasses?: ExcludedRoadClass[]
  /**
   * Specific points to route around — a closed road, a blocked yard entrance.
   *
   * Supported only on the `driving` and `driving-traffic` profiles; on the
   * others the wrapper drops it and reports
   * `EXCLUDED_LOCATIONS_NOT_SUPPORTED_BY_PROFILE`. The API caps it at 50
   * points, and anything beyond that is dropped with a `TOO_MANY_EXCLUDED_LOCATIONS`
   * report rather than failing the request.
   *
   * This is a beta Directions API feature. Whether excluding a given point
   * actually changes the route is up to the router — a point it can detour
   * around at no cost may produce the same geometry.
   */
  locations?: Coordinate[]
}

/**
 * Vehicle dimensions, for routing that avoids roads the vehicle cannot use.
 *
 * Only supported on the `driving` and `driving-traffic` profiles. The API
 * answers `422 Invalid query param` for these on `walking` and `cycling`, which
 * would fail the whole route request — so on those profiles the wrapper drops
 * them and reports `VEHICLE_NOT_SUPPORTED_BY_PROFILE` through `onError` instead.
 * Omitted dimensions are not constrained.
 *
 * ```tsx
 * <MapboxNavigationView
 *   destination={destination}
 *   vehicle={{ maxHeight: 4.2, maxWeight: 18 }}
 * />
 * ```
 */
export interface VehicleConstraints {
  /** Vehicle height in **metres**. */
  maxHeight?: number
  /** Vehicle width in **metres**. */
  maxWidth?: number
  /** Vehicle weight in **metric tons**. */
  maxWeight?: number
}

/**
 * Configuration for Mapbox's Standard styles.
 *
 * Standard styles (`mapbox://styles/mapbox/standard`, `standard-satellite`, and
 * the navigation style both platforms default to) expose their basemap as a
 * configurable style *import* rather than a fixed set of layers. These are the
 * knobs that import accepts.
 *
 * They have no effect on classic styles like `streets-v12`, which have no
 * configurable import — that case is reported once as
 * `MAP_STYLE_CONFIG_UNSUPPORTED` through `onError` rather than failing.
 *
 * ```tsx
 * <MapboxNavigationView
 *   destination={destination}
 *   mapStyleUriDay='mapbox://styles/mapbox/standard'
 *   mapStyleConfig={{ lightPreset: 'dusk', show3dObjects: true }}
 * />
 * ```
 */
export interface MapStyleConfig {
  /**
   * The time-of-day lighting: shadow direction and length, sky colour, and how
   * lit building faces are.
   *
   * Note that this is independent of `uiTheme`, which picks the day or night
   * *style*. Setting `lightPreset: 'night'` on a day style gives you a
   * night-lit day style.
   */
  lightPreset?: 'day' | 'dusk' | 'dawn' | 'night'
  /** 3D buildings, landmarks and trees. */
  show3dObjects?: boolean
  /** Road name labels. */
  showRoadLabels?: boolean
  /** Place labels — cities, neighbourhoods, parks. */
  showPlaceLabels?: boolean
  /** Point-of-interest labels and icons. */
  showPointOfInterestLabels?: boolean
  /** Transit station labels and icons. */
  showTransitLabels?: boolean
}

/**
 * Colour overrides for the navigation UI.
 *
 * Mapbox's default palette is its own brand blue — the route line on iOS, the
 * maneuver banner on Android — which rarely matches a host app. Anything
 * omitted keeps the Mapbox default, so you can override just the route line and
 * leave the chrome alone, or vice versa.
 *
 * Use `#RGB`, `#RRGGBB` or `#RRGGBBAA`. All three are parsed identically on
 * iOS and Android, and eight digits are read the CSS way — alpha last — so
 * `'#14532D80'` is that green at 50%. The alpha channel is honoured rather
 * than dropped.
 *
 * Before 3.1.0 the eight-digit form was not portable: iOS read it as
 * `#RRGGBBAA` while Android read it as `#AARRGGBB`, so one string gave two
 * colours. If you were working around that with an opaque colour, nothing
 * changes; if you were passing eight digits to one platform only, see the
 * 3.1.0 changelog entry.
 *
 * Translucency composites over whatever is behind the element — the map for
 * the route line and the maneuver banner. It is honoured, but read the
 * `UIAppearance` note below before using it on the chrome keys: a translucent
 * chrome colour is as permanent as an opaque one for the life of the process.
 *
 * Every key below applies live, including changes made after mount, and every
 * key works on **both** platforms except the three `tripProgress*` ones, which
 * are iOS-only and say so — Android has no trip progress bar to colour, because
 * v3's standalone widget does not lay out usably outside the Drop-In container
 * it was designed for.
 *
 * Treat this as a theme you set, not a value you toggle off. On iOS the chrome
 * colours reach the SDK through `UIAppearance`, which is process-global: once a
 * key has been set, *removing* it (or removing the whole prop) leaves the last
 * colour in place rather than restoring the Mapbox default, until the app is
 * relaunched. Setting a key to a different colour always works. So pass the
 * full palette you want and change values within it, rather than adding and
 * removing keys at runtime.
 *
 * That applies to alpha too, so `'#00000000'` is not a way to hide a piece of
 * chrome: it paints it fully transparent for the rest of the process, and
 * there is no value you can pass afterwards to get the Mapbox default back.
 * Use the `shows*` props to turn UI off — they are designed for it.
 *
 * ```tsx
 * <MapboxNavigationView
 *   colors={{
 *     routeLine: '#1E9E5A',
 *     routeLineCasing: '#14532D',
 *     maneuverBackground: '#14532D',
 *     maneuverTurnIcon: '#FFFFFF',
 *     tripProgressBackground: '#14532D',
 *     tripProgressText: '#FFFFFF',
 *   }}
 *   destination={destination}
 * />
 * ```
 */
export interface MapboxNavigationColors {
  // --- Route line ----------------------------------------------------------

  /**
   * The main route line.
   *
   * This also becomes the `low` and `unknown` traffic colours, because that is
   * how both SDKs model a plain single-colour route: congestion shading is
   * drawn over the base line, and the unknown/low bands are the ones that would
   * otherwise still show through as Mapbox blue. Set `congestionLow` /
   * `congestionUnknown` after this to override them individually.
   */
  routeLine?: string
  /** The outline drawn under the main route line. */
  routeLineCasing?: string
  /**
   * The portion of the route already driven.
   *
   * Only visible when `routeLineTracksTraversal` is enabled.
   */
  routeLineTraversed?: string
  /** Alternative route lines. */
  routeLineAlternative?: string
  /** The outline drawn under alternative route lines. */
  routeLineAlternativeCasing?: string
  /** Free-flowing traffic. */
  congestionLow?: string
  /** Moderate traffic. */
  congestionModerate?: string
  /** Heavy traffic. */
  congestionHeavy?: string
  /** Severe traffic or a standstill. */
  congestionSevere?: string
  /** Sections with no traffic data. */
  congestionUnknown?: string
  /** Restricted sections, such as private or permit-only roads. */
  restrictedRoad?: string

  // --- Turn arrow drawn on the map -----------------------------------------

  /**
   * The arrow drawn on the map over the upcoming turn.
   *
   * This is the shape on the map itself, not the icon in the maneuver banner —
   * that one is {@link maneuverTurnIcon}.
   */
  maneuverArrow?: string
  /** The outline drawn around {@link maneuverArrow}. */
  maneuverArrowStroke?: string

  // --- Maneuver banner -----------------------------------------------------

  /**
   * The background of the maneuver banner at the top of the screen.
   *
   * This is the Mapbox navy (`#37516F`) on Android and white on iOS, so a theme
   * that sets this will usually want to set {@link maneuverText},
   * {@link maneuverSecondaryText}, {@link maneuverDistanceText} and
   * {@link maneuverTurnIcon} to something that reads against it — the SDK
   * defaults for those differ per platform (white on Android, near-black on
   * iOS) precisely because the default backgrounds differ.
   */
  maneuverBackground?: string
  /**
   * The background of the secondary rows below the main instruction: the lane
   * guidance strip and the "then" step.
   *
   * Defaults to a slightly darker shade of {@link maneuverBackground} on
   * Android and a slightly greyer one on iOS.
   */
  maneuverSubBackground?: string
  /** The primary instruction text ("Turn left onto Main Street"). */
  maneuverText?: string
  /**
   * The secondary and sub instruction text — the smaller lines under the
   * primary one, including the "then" step.
   */
  maneuverSecondaryText?: string
  /** The distance-to-maneuver readout in the banner ("400 m"). */
  maneuverDistanceText?: string
  /**
   * The turn icon in the maneuver banner.
   *
   * One caveat, and it is the only place these two platforms differ: the icon
   * is drawn in two tones — the turn you are taking, plus de-emphasised strokes
   * for the roads you are not taking. iOS lets those be coloured separately, so
   * this sets only the emphasised strokes and the de-emphasised ones stay grey.
   * Android only allows a flat tint at runtime, so the whole icon takes this
   * colour. Pick a colour that reads on {@link maneuverBackground} either way.
   */
  maneuverTurnIcon?: string

  // --- Trip progress bar (iOS only) ----------------------------------------
  //
  // Android ships no trip progress bar: v3's `MapboxTripProgressView` only
  // lays out correctly inside Drop-In's info panel, which v3 removed. Use this
  // package's `bottomSheet` overlay there — it is JS, so it takes your own
  // styles directly. These three keys are ignored on Android.

  /**
   * **iOS only.** The background of the trip progress bar along the bottom of
   * the screen — the one showing time remaining, distance and arrival time.
   *
   * To hide the bar instead of recolouring it, pass `showsTripProgress={false}`
   * together with `showCancelButton={false}` — the cancel button lives in that
   * bar, so the bar stays up while the button is wanted.
   */
  tripProgressBackground?: string
  /**
   * **iOS only.** The text in the trip progress bar: time remaining, distance
   * remaining and estimated arrival time.
   *
   * iOS normally tints the time remaining by traffic level — green for
   * free-flowing, red for severe — which is why the ETA reads green by
   * default. Setting this replaces all of those with one flat colour. Leave it
   * unset to keep the traffic-tinted ETA.
   */
  tripProgressText?: string
  /** **iOS only.** The cancel button in the trip progress bar. */
  tripProgressIcon?: string

  // --- Floating map buttons ------------------------------------------------

  /**
   * The background of the round buttons floating over the map — the voice
   * toggle, and on iOS also the camera, overview and feedback buttons.
   */
  floatingButtonBackground?: string
  /** The icon inside those buttons. */
  floatingButtonIcon?: string
}

export interface MapboxNavigationViewProps {
  /** Explicitly opt in to embedded navigation startup. Defaults to `false`. */
  enabled?: boolean
  style?: any
  /**
   * Route starting point. Defaults to the device's current location on both
   * platforms when omitted.
   */
  startOrigin?: Coordinate
  destination: Waypoint
  /** Display-only markers rendered directly on the native navigation map. */
  navigationMarkers?: NavigationMarker[]
  /**
   * Replace the standard navigation pointer (location puck) with a 3D model,
   * custom 2D images, a recolored default puck, or nothing at all.
   *
   * Accepts a single appearance applied to every navigation state, or an
   * object keyed by state for per-state pointers.
   *
   * @see {@link LocationPuckConfig}
   */
  locationPuck?: LocationPuckConfig
  /**
   * Theme the navigation UI: the route line, the on-map turn arrow, the
   * maneuver banner and the floating map buttons.
   *
   * Applied live on both platforms, including changes made after mount.
   *
   * @see {@link MapboxNavigationColors}
   */
  colors?: MapboxNavigationColors
  /**
   * Which Directions profile to route with. Defaults to `driving-traffic`.
   *
   * Changing this re-requests the route.
   *
   * @see {@link RouteProfile}
   */
  routeProfile?: RouteProfile
  /**
   * Road classes and specific points the route should avoid.
   *
   * Changing this re-requests the route.
   *
   * @see {@link RouteExclusions}
   */
  routeExclusions?: RouteExclusions
  /**
   * Vehicle dimensions, so the route avoids roads it cannot fit through.
   *
   * Changing this re-requests the route.
   *
   * @see {@link VehicleConstraints}
   */
  vehicle?: VehicleConstraints
  /**
   * Configure a Standard style's basemap: lighting, 3D objects, label
   * categories. Applied live, and ignored by classic styles.
   *
   * @see {@link MapStyleConfig}
   */
  mapStyleConfig?: MapStyleConfig
  /**
   * Minimum interval, in milliseconds, between the continuous high-frequency
   * events: `onLocationChange`, `onRouteProgressChange` and
   * `onJourneyDataChange`.
   *
   * Mapbox reports these on every location fix, and each one crosses the native
   * bridge with a payload. Raising this reduces CPU, garbage and battery use on
   * long trips. Discrete events (`onArrive`, `onBannerInstruction`, `onError`)
   * are never throttled.
   *
   * Defaults to `0` (emit every update). `250`–`1000` is a good range for UI
   * that only displays progress. Clamped to `0`–`10000`.
   */
  eventThrottleMs?: number
  waypoints?: Waypoint[]
  shouldSimulateRoute?: boolean
  showCancelButton?: boolean
  uiTheme?: 'system' | 'light' | 'dark' | 'day' | 'night'
  distanceUnit?: 'metric' | 'imperial'
  language?: string
  mute?: boolean
  voiceVolume?: number
  /**
   * Camera pitch in degrees (`0`–`85`).
   *
   * On Android the Drop-In navigation camera recomputes the viewport while
   * following the user, so this reliably applies only while the camera is idle
   * or in overview.
   */
  cameraPitch?: number
  /**
   * Camera zoom level (`1`–`22`).
   *
   * Same Android caveat as {@link MapboxNavigationViewProps.cameraPitch}.
   */
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
  /**
   * Show the Mapbox feedback/report control.
   *
   * iOS only — the Android Drop-In UI does not expose it, and setting `false`
   * there logs a warning and is otherwise ignored.
   */
  showsReportFeedback?: boolean
  /** Opt in to the package-managed end-of-route rating modal in embedded mode. */
  showsEndOfRouteFeedback?: boolean
  /**
   * Keep showing alternative routes while navigating.
   *
   * iOS only. On Android this triggers a fresh route request but the Drop-In UI
   * controls alternative-route display itself.
   */
  showsContinuousAlternatives?: boolean
  /** Switch to the night style inside tunnels. iOS only. */
  usesNightStyleWhileInTunnel?: boolean
  /** Fade the traversed portion of the route line. iOS only. */
  routeLineTracksTraversal?: boolean
  /** Annotate upcoming intersections along the route. iOS only. */
  annotatesIntersectionsAlongRoute?: boolean
  /**
   * @deprecated Not supported on either platform — see
   * {@link AndroidActionButtonsOptions}. This prop is ignored.
   */
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
  /** Callback when the final destination is reached. */
  onArrive?: (point: ArrivalEvent) => void
  /**
   * Callback when an intermediate `waypoints` stop is reached.
   *
   * Multi-stop routes previously had no way to observe this: Android dropped
   * the event entirely and iOS reported it as a normal arrival.
   */
  onWaypointArrive?: (point: ArrivalEvent) => void
  /** Callback when the user leaves or rejoins the route. */
  onOffRoute?: (event: OffRouteEvent) => void
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

// ---------------------------------------------------------------------------
// Custom location puck (navigation pointer)
// ---------------------------------------------------------------------------

/**
 * A source for a puck image or 3D model asset.
 *
 * Accepts three forms:
 * - A React Native `require()` of a local file (returns an opaque number).
 *   Requires the file extension to be registered in your Metro `assetExts`.
 * - An object with a `uri` — a remote `https://` URL, or a platform-native
 *   asset reference (`asset://model.glb` on Android, a bundle resource name
 *   on iOS).
 * - A bare string, treated exactly like `{ uri }`.
 *
 * @example
 * ```ts
 * modelUri: require('./assets/car.glb')
 * modelUri: 'https://cdn.example.com/car.glb'
 * modelUri: { uri: 'asset://car.glb' }
 * ```
 */
export type LocationPuckAssetSource = string | number | { uri: string }

/**
 * Replace the pointer with a glTF/GLB 3D model.
 *
 * The model's forward axis should point along +Y to align with the course
 * bearing; use `rotation` to correct models authored on a different axis.
 */
export type LocationPuck3D = {
  type: '3d'
  /** glTF (`.gltf`) or binary glTF (`.glb`) model source. */
  modelUri: LocationPuckAssetSource
  /**
   * Uniform scale, or a per-axis `[x, y, z]` scale.
   * Defaults to `1`. Note that Mapbox model scale is in metres, so real-world
   * vehicle models typically need a much larger value (10–40) to stay visible
   * at navigation zoom levels.
   */
  scale?: number | [number, number, number]
  /** Per-axis rotation in degrees, `[x, y, z]`. Defaults to `[0, 0, 0]`. */
  rotation?: [number, number, number]
  /** Per-axis translation in metres, `[x, y, z]`. Defaults to `[0, 0, 0]`. */
  translation?: [number, number, number]
  /** Model opacity in range `0..1`. Defaults to `1`. */
  opacity?: number
  /**
   * A Mapbox style expression (as a JSON string) driving scale from zoom.
   * Takes priority over `scale` when provided.
   *
   * @example `'["interpolate",["linear"],["zoom"],14,[10,10,10],18,[30,30,30]]'`
   */
  scaleExpression?: string
}

/** Replace the pointer with flat 2D images. */
export type LocationPuck2D = {
  type: '2d'
  /** Image drawn flat, not rotated by course. Typically a dot or halo. */
  topImage?: LocationPuckAssetSource
  /** Image rotated to match the course bearing. Typically an arrow or vehicle. */
  bearingImage?: LocationPuckAssetSource
  /** Image drawn beneath the puck. Typically a soft shadow. */
  shadowImage?: LocationPuckAssetSource
  /** Uniform scale multiplier. Defaults to `1`. */
  scale?: number
  /** A Mapbox style expression (JSON string) driving scale from zoom. Overrides `scale`. */
  scaleExpression?: string
  /** Puck opacity in range `0..1`. Defaults to `1`. */
  opacity?: number
}

/**
 * Keep Mapbox's built-in puck geometry but recolor it to match app branding.
 *
 * No image or model assets are required — the puck is generated natively from
 * the supplied colors.
 */
export type LocationPuckTinted = {
  type: 'tinted'
  /**
   * Fill color of the puck body, as a hex string — `#RGB`, `#RRGGBB` or
   * `#RRGGBBAA` (e.g. `"#2563EB"`), read the same way on both platforms.
   */
  color?: string
  /** Color of the surrounding halo/accuracy ring. Same forms as `color`. */
  haloColor?: string
  /**
   * Color of the directional arrow. Same forms as `color`.
   * Defaults to a contrast of `color`.
   */
  bearingColor?: string
  /** Uniform scale multiplier. Defaults to `1`. */
  scale?: number
  /** Puck opacity in range `0..1`. Defaults to `1`. */
  opacity?: number
}

/** Hide the pointer entirely — for apps drawing their own vehicle marker. */
export type LocationPuckNone = {
  type: 'none'
}

/** Use the Mapbox SDK default pointer for the current navigation state. */
export type LocationPuckDefault = {
  type: 'default'
}

/** A single pointer appearance. */
export type LocationPuckAppearance =
  | LocationPuck3D
  | LocationPuck2D
  | LocationPuckTinted
  | LocationPuckNone
  | LocationPuckDefault

/**
 * Per-navigation-state pointer appearances.
 *
 * `default` applies to every state that is not given its own override.
 * State-specific pucks are applied natively on Android via
 * `LocationPuckOptions`; on iOS the pointer is swapped as the session
 * transitions between states.
 */
export type LocationPuckStates = {
  /** Fallback for any state without an explicit override. */
  default?: LocationPuckAppearance
  /** Tracking location with no route loaded. */
  freeDrive?: LocationPuckAppearance
  /** A destination is set but no route has been previewed yet. */
  destinationPreview?: LocationPuckAppearance
  /** A route is being previewed before guidance starts. */
  routePreview?: LocationPuckAppearance
  /** Turn-by-turn guidance is active. */
  activeNavigation?: LocationPuckAppearance
  /** The final destination has been reached. */
  arrival?: LocationPuckAppearance
  /** The session is idle. */
  idle?: LocationPuckAppearance
}

/**
 * Custom navigation pointer configuration.
 *
 * Pass a single appearance to use it for every navigation state, or a
 * {@link LocationPuckStates} object to vary the pointer per state.
 *
 * @example Single 3D model for the whole session
 * ```tsx
 * locationPuck={{
 *   type: '3d',
 *   modelUri: require('./assets/car.glb'),
 *   scale: 18,
 *   rotation: [0, 0, 180],
 * }}
 * ```
 *
 * @example A different pointer while actively navigating
 * ```tsx
 * locationPuck={{
 *   default: { type: 'tinted', color: '#2563EB' },
 *   activeNavigation: { type: '3d', modelUri: require('./assets/car.glb'), scale: 18 },
 *   arrival: { type: '2d', bearingImage: require('./assets/pin.png') },
 * }}
 * ```
 */
export type LocationPuckConfig = LocationPuckAppearance | LocationPuckStates

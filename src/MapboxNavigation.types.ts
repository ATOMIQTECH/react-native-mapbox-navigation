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

  /** Callback for location changes. */
  onLocationChange?: (location: LocationUpdate) => void;
  /** Callback for route progress changes. */
  onRouteProgressChange?: (progress: RouteProgress) => void;
  /** Callback when arrival is detected. */
  onArrive?: (point: ArrivalEvent) => void;
  /** Callback when navigation is canceled by user. */
  onCancelNavigation?: () => void;
  /** Callback for native errors. */
  onError?: (error: NavigationError) => void;
  /** Callback for banner instruction updates. */
  onBannerInstruction?: (instruction: BannerInstruction) => void;
}

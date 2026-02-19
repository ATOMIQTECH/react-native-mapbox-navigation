export type Coordinate = {
  latitude: number;
  longitude: number;
};

export type Waypoint = Coordinate & {
  name?: string;
};

export type NavigationOptions = {
  startOrigin?: Coordinate;
  destination: Waypoint;
  waypoints?: Waypoint[];
  shouldSimulateRoute?: boolean;
  uiTheme?: 'system' | 'light' | 'dark' | 'day' | 'night';
  routeAlternatives?: boolean;
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
  showsSpeedLimits?: boolean;
  showsWayNameLabel?: boolean;
  showsTripProgress?: boolean;
  showsManeuverView?: boolean;
  showsActionButtons?: boolean;
};

export type NavigationSettings = {
  isNavigating: boolean;
  mute: boolean;
  voiceVolume: number;
  distanceUnit: 'metric' | 'imperial';
  language: string;
};

export type LocationUpdate = {
  latitude: number;
  longitude: number;
  bearing?: number;
  speed?: number;
  altitude?: number;
  accuracy?: number;
};

export type RouteProgress = {
  distanceTraveled: number;
  distanceRemaining: number;
  durationRemaining: number;
  fractionTraveled: number;
};

export type ArrivalEvent = {
  index?: number;
  name?: string;
};

export type NavigationError = {
  code: string;
  message: string;
};

export type BannerInstruction = {
  primaryText: string;
  secondaryText?: string;
  stepDistanceRemaining?: number;
};

export type Subscription = {
  remove: () => void;
};

export interface MapboxNavigationModule {
  // Start navigation with options
  startNavigation(options: NavigationOptions): Promise<void>;
  
  // Stop/cancel navigation
  stopNavigation(): Promise<void>;
  
  // Mute/unmute voice guidance
  setMuted(muted: boolean): Promise<void>;

  // Set voice volume (0.0 - 1.0)
  setVoiceVolume(volume: number): Promise<void>;

  // Set distance unit used by spoken and visual instructions
  setDistanceUnit(unit: 'metric' | 'imperial'): Promise<void>;

  // Set route instruction language (BCP-47, e.g. 'en', 'fr')
  setLanguage(language: string): Promise<void>;
  
  // Check if navigation is active
  isNavigating(): Promise<boolean>;

  // Get current native navigation settings
  getNavigationSettings(): Promise<NavigationSettings>;
}

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
  
  // Event callbacks
  onLocationChange?: (location: LocationUpdate) => void;
  onRouteProgressChange?: (progress: RouteProgress) => void;
  onArrive?: (point: ArrivalEvent) => void;
  onCancelNavigation?: () => void;
  onError?: (error: NavigationError) => void;
  onBannerInstruction?: (instruction: BannerInstruction) => void;
}

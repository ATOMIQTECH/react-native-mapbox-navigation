import { requireNativeModule, requireNativeViewManager } from "expo-modules-core";
import { ViewProps } from "react-native";

import type {
  ArrivalEvent,
  BannerInstruction,
  LocationUpdate,
  MapboxNavigationModule as MapboxNavigationModuleType,
  MapboxNavigationViewProps,
  NavigationSettings,
  NavigationError,
  NavigationOptions,
  RouteProgress,
  Subscription,
} from "./MapboxNavigation.types";

const MapboxNavigationModule = requireNativeModule<MapboxNavigationModuleType>(
  "MapboxNavigationModule",
);

const emitter = MapboxNavigationModule as unknown as {
  addListener: (
    eventName: string,
    listener: (...args: any[]) => void,
  ) => Subscription;
};

const VALID_DISTANCE_UNITS = new Set(["metric", "imperial"]);
const VALID_CAMERA_MODES = new Set(["following", "overview"]);
const VALID_UI_THEMES = new Set(["system", "light", "dark", "day", "night"]);

function assertCoordinate(
  coordinate: { latitude: number; longitude: number },
  fieldName: string,
) {
  if (!Number.isFinite(coordinate.latitude) || !Number.isFinite(coordinate.longitude)) {
    throw new Error(`${fieldName} must contain finite latitude/longitude numbers.`);
  }
  if (coordinate.latitude < -90 || coordinate.latitude > 90) {
    throw new Error(`${fieldName}.latitude must be between -90 and 90.`);
  }
  if (coordinate.longitude < -180 || coordinate.longitude > 180) {
    throw new Error(`${fieldName}.longitude must be between -180 and 180.`);
  }
}

function normalizeNavigationOptions(options: NavigationOptions): NavigationOptions {
  if (!options || typeof options !== "object") {
    throw new Error("startNavigation options must be an object.");
  }

  assertCoordinate(options.destination, "destination");
  if (options.startOrigin) {
    assertCoordinate(options.startOrigin, "startOrigin");
  }

  if (options.waypoints?.length) {
    options.waypoints.forEach((waypoint, index) => {
      assertCoordinate(waypoint, `waypoints[${index}]`);
    });
  }

  if (options.distanceUnit && !VALID_DISTANCE_UNITS.has(options.distanceUnit)) {
    throw new Error("distanceUnit must be 'metric' or 'imperial'.");
  }

  if (options.cameraMode && !VALID_CAMERA_MODES.has(options.cameraMode)) {
    throw new Error("cameraMode must be 'following' or 'overview'.");
  }

  if (options.uiTheme && !VALID_UI_THEMES.has(options.uiTheme)) {
    throw new Error("uiTheme must be one of: system, light, dark, day, night.");
  }

  if (options.voiceVolume != null) {
    if (!Number.isFinite(options.voiceVolume) || options.voiceVolume < 0 || options.voiceVolume > 1) {
      throw new Error("voiceVolume must be a finite number between 0 and 1.");
    }
  }

  if (options.cameraPitch != null) {
    if (!Number.isFinite(options.cameraPitch) || options.cameraPitch < 0 || options.cameraPitch > 85) {
      throw new Error("cameraPitch must be a finite number between 0 and 85.");
    }
  }

  if (options.cameraZoom != null) {
    if (!Number.isFinite(options.cameraZoom) || options.cameraZoom < 1 || options.cameraZoom > 22) {
      throw new Error("cameraZoom must be a finite number between 1 and 22.");
    }
  }

  const trimmedLanguage = options.language?.trim();
  if (trimmedLanguage != null && trimmedLanguage.length === 0) {
    throw new Error("language cannot be an empty string.");
  }

  return {
    ...options,
    language: trimmedLanguage || options.language,
    mapStyleUri: options.mapStyleUri?.trim() || options.mapStyleUri,
    mapStyleUriDay: options.mapStyleUriDay?.trim() || options.mapStyleUriDay,
    mapStyleUriNight: options.mapStyleUriNight?.trim() || options.mapStyleUriNight,
  };
}

function normalizeNativeError(error: unknown, fallbackCode = "NATIVE_ERROR"): Error {
  if (error instanceof Error) {
    return error;
  }

  const candidate = error as { code?: string; message?: string } | undefined;
  const code = candidate?.code ?? fallbackCode;
  const message = candidate?.message ?? "Unknown native error";
  return new Error(`[${code}] ${message}`);
}

/**
 * Start full-screen native turn-by-turn navigation.
 *
 * @param options Navigation settings such as destination, camera mode, simulation, and map UI options.
 * @throws Error if options are invalid or native route/token setup fails.
 */
export async function startNavigation(
  options: NavigationOptions,
): Promise<void> {
  const normalizedOptions = normalizeNavigationOptions(options);
  try {
    await MapboxNavigationModule.startNavigation(normalizedOptions);
  } catch (error) {
    throw normalizeNativeError(error, "START_NAVIGATION_FAILED");
  }
}

/**
 * Stop/dismiss native navigation if active.
 */
export async function stopNavigation(): Promise<void> {
  try {
    await MapboxNavigationModule.stopNavigation();
  } catch (error) {
    throw normalizeNativeError(error, "STOP_NAVIGATION_FAILED");
  }
}

/**
 * Enable or disable voice guidance.
 *
 * @param muted `true` to mute voice instructions.
 */
export async function setMuted(muted: boolean): Promise<void> {
  try {
    await MapboxNavigationModule.setMuted(muted);
  } catch (error) {
    throw normalizeNativeError(error, "SET_MUTED_FAILED");
  }
}

/**
 * Set voice instruction volume in range `0..1`.
 */
export async function setVoiceVolume(volume: number): Promise<void> {
  try {
    await MapboxNavigationModule.setVoiceVolume(volume);
  } catch (error) {
    throw normalizeNativeError(error, "SET_VOICE_VOLUME_FAILED");
  }
}

/**
 * Set spoken and displayed distance units.
 */
export async function setDistanceUnit(unit: "metric" | "imperial"): Promise<void> {
  try {
    await MapboxNavigationModule.setDistanceUnit(unit);
  } catch (error) {
    throw normalizeNativeError(error, "SET_DISTANCE_UNIT_FAILED");
  }
}

/**
 * Set instruction language (BCP-47-like code, for example `en`, `fr`).
 */
export async function setLanguage(language: string): Promise<void> {
  try {
    await MapboxNavigationModule.setLanguage(language);
  } catch (error) {
    throw normalizeNativeError(error, "SET_LANGUAGE_FAILED");
  }
}

/**
 * Check whether full-screen native navigation is currently active.
 */
export async function isNavigating(): Promise<boolean> {
  try {
    return await MapboxNavigationModule.isNavigating();
  } catch (error) {
    throw normalizeNativeError(error, "IS_NAVIGATING_FAILED");
  }
}

/**
 * Read current native navigation runtime settings.
 */
export async function getNavigationSettings(): Promise<NavigationSettings> {
  try {
    return await MapboxNavigationModule.getNavigationSettings();
  } catch (error) {
    throw normalizeNativeError(error, "GET_NAVIGATION_SETTINGS_FAILED");
  }
}

/**
 * Subscribe to location updates from native navigation.
 */
export function addLocationChangeListener(
  listener: (location: LocationUpdate) => void,
): Subscription {
  return emitter.addListener("onLocationChange", listener);
}

/**
 * Subscribe to route progress updates.
 */
export function addRouteProgressChangeListener(
  listener: (progress: RouteProgress) => void,
): Subscription {
  return emitter.addListener("onRouteProgressChange", listener);
}

/**
 * Subscribe to arrival events.
 */
export function addArriveListener(listener: (point: ArrivalEvent) => void): Subscription {
  return emitter.addListener("onArrive", listener);
}

/**
 * Subscribe to cancellation events.
 */
export function addCancelNavigationListener(listener: () => void): Subscription {
  return emitter.addListener("onCancelNavigation", listener);
}

/**
 * Subscribe to native errors (token issues, route fetch failures, permission failures, etc.).
 */
export function addErrorListener(listener: (error: NavigationError) => void): Subscription {
  return emitter.addListener("onError", listener);
}

/**
 * Subscribe to banner instruction updates.
 */
export function addBannerInstructionListener(
  listener: (instruction: BannerInstruction) => void,
): Subscription {
  return emitter.addListener("onBannerInstruction", listener);
}

/**
 * Embedded native navigation component.
 *
 * Use this when you need navigation inside your own screen layout instead of full-screen modal navigation.
 */
export function MapboxNavigationView(
  props: MapboxNavigationViewProps & ViewProps,
) {
  const NativeView = requireNativeViewManager("MapboxNavigationView");
  return <NativeView {...props} />;
}

export * from "./MapboxNavigation.types";

export default {
  startNavigation,
  stopNavigation,
  setMuted,
  setVoiceVolume,
  setDistanceUnit,
  setLanguage,
  isNavigating,
  getNavigationSettings,
  addLocationChangeListener,
  addRouteProgressChangeListener,
  addArriveListener,
  addCancelNavigationListener,
  addErrorListener,
  addBannerInstructionListener,
  MapboxNavigationView,
};

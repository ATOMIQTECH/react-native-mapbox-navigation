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

// Get the native module (use requireNativeModule instead of deprecated NativeModulesProxy)
const MapboxNavigationModule = requireNativeModule<MapboxNavigationModuleType>(
  "MapboxNavigationModule",
);

// The native module already acts as an EventEmitter; don't wrap with `new EventEmitter`.
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

// Module API
export async function startNavigation(
  options: NavigationOptions,
): Promise<void> {
  const normalizedOptions = normalizeNavigationOptions(options);
  return await MapboxNavigationModule.startNavigation(normalizedOptions);
}

export async function stopNavigation(): Promise<void> {
  return await MapboxNavigationModule.stopNavigation();
}

export async function setMuted(muted: boolean): Promise<void> {
  return await MapboxNavigationModule.setMuted(muted);
}

export async function setVoiceVolume(volume: number): Promise<void> {
  return await MapboxNavigationModule.setVoiceVolume(volume);
}

export async function setDistanceUnit(unit: "metric" | "imperial"): Promise<void> {
  return await MapboxNavigationModule.setDistanceUnit(unit);
}

export async function setLanguage(language: string): Promise<void> {
  return await MapboxNavigationModule.setLanguage(language);
}

export async function isNavigating(): Promise<boolean> {
  return await MapboxNavigationModule.isNavigating();
}

export async function getNavigationSettings(): Promise<NavigationSettings> {
  return await MapboxNavigationModule.getNavigationSettings();
}

// Event listeners
export function addLocationChangeListener(
  listener: (location: LocationUpdate) => void,
): Subscription {
  return emitter.addListener("onLocationChange", listener);
}

export function addRouteProgressChangeListener(
  listener: (progress: RouteProgress) => void,
): Subscription {
  return emitter.addListener("onRouteProgressChange", listener);
}

export function addArriveListener(listener: (point: ArrivalEvent) => void): Subscription {
  return emitter.addListener("onArrive", listener);
}

export function addCancelNavigationListener(listener: () => void): Subscription {
  return emitter.addListener("onCancelNavigation", listener);
}

export function addErrorListener(listener: (error: NavigationError) => void): Subscription {
  return emitter.addListener("onError", listener);
}

export function addBannerInstructionListener(
  listener: (instruction: BannerInstruction) => void,
): Subscription {
  return emitter.addListener("onBannerInstruction", listener);
}

// React component wrapper
export function MapboxNavigationView(
  props: MapboxNavigationViewProps & ViewProps,
) {
  const NativeView = requireNativeViewManager("MapboxNavigationView");
  return <NativeView {...props} />;
}

// Export types
export * from "./MapboxNavigation.types";

// Default export
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

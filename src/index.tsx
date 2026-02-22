import { requireNativeModule, requireNativeViewManager } from "expo-modules-core";
import { useEffect, useMemo, useState } from "react";
import { Platform, Pressable, StyleSheet, Text, View, ViewProps } from "react-native";

import type {
  ArrivalEvent,
  BannerInstruction,
  BottomSheetActionEvent,
  DestinationChangedEvent,
  DestinationPreviewEvent,
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

function unwrapNativeEventPayload<T>(payload: unknown): T | undefined {
  if (payload == null) {
    return undefined;
  }
  if (typeof payload === "object") {
    const value = payload as {
      nativeEvent?: unknown;
      payload?: unknown;
      data?: unknown;
    };
    const first = value.nativeEvent ?? value.payload ?? value.data;
    if (first != null) {
      return unwrapNativeEventPayload<T>(first);
    }
  }
  return payload as T;
}

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

  let showsTripProgress = options.showsTripProgress;
  let showsManeuverView = options.showsManeuverView;
  let showsActionButtons = options.showsActionButtons;
  if (options.bottomSheet) {
    const enabled = options.bottomSheet.enabled;
    if (enabled === false) {
      showsTripProgress = false;
      showsManeuverView = false;
      showsActionButtons = false;
    } else {
      if (showsTripProgress == null) {
        showsTripProgress = options.bottomSheet.showsTripProgress;
      }
      if (showsManeuverView == null) {
        showsManeuverView = options.bottomSheet.showsManeuverView;
      }
      if (showsActionButtons == null) {
        showsActionButtons = options.bottomSheet.showsActionButtons;
      }
    }
  }

  return {
    ...options,
    startOrigin: options.startOrigin
      ? {
          latitude: options.startOrigin.latitude,
          longitude: options.startOrigin.longitude,
        }
      : undefined,
    routeAlternatives: options.routeAlternatives ?? options.showsContinuousAlternatives,
    showsTripProgress,
    showsManeuverView,
    showsActionButtons,
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

function formatDuration(seconds: number): string {
  const totalMinutes = Math.max(0, Math.round(seconds / 60));
  if (totalMinutes < 60) {
    return `${totalMinutes} min`;
  }
  const hours = Math.floor(totalMinutes / 60);
  const mins = totalMinutes % 60;
  return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`;
}

function formatEta(durationRemainingSeconds?: number): string | undefined {
  if (!Number.isFinite(durationRemainingSeconds ?? NaN)) {
    return undefined;
  }
  const etaDate = new Date(Date.now() + (durationRemainingSeconds as number) * 1000);
  const time = etaDate.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  return `Arrive ${time}`;
}

function normalizeViewProps(
  props: MapboxNavigationViewProps & ViewProps,
): MapboxNavigationViewProps & ViewProps {
  let showsTripProgress = props.showsTripProgress;
  let showsManeuverView = props.showsManeuverView;
  let showsActionButtons = props.showsActionButtons;
  if (props.bottomSheet) {
    const enabled = props.bottomSheet.enabled;
    if (enabled === false) {
      showsTripProgress = false;
      showsManeuverView = false;
      showsActionButtons = false;
    } else {
      if (showsTripProgress == null) {
        showsTripProgress = props.bottomSheet.showsTripProgress;
      }
      if (showsManeuverView == null) {
        showsManeuverView = props.bottomSheet.showsManeuverView;
      }
      if (showsActionButtons == null) {
        showsActionButtons = props.bottomSheet.showsActionButtons;
      }
    }
  }

  const wrappedOnLocationChange = props.onLocationChange
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<LocationUpdate>(event);
        if (payload) {
          props.onLocationChange?.(payload);
        }
      }
    : undefined;
  const wrappedOnRouteProgressChange = props.onRouteProgressChange
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<RouteProgress>(event);
        if (payload) {
          props.onRouteProgressChange?.(payload);
        }
      }
    : undefined;
  const wrappedOnBannerInstruction = props.onBannerInstruction
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<BannerInstruction>(event);
        if (payload) {
          props.onBannerInstruction?.(payload);
        }
      }
    : undefined;
  const wrappedOnArrive = props.onArrive
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<ArrivalEvent>(event);
        if (payload) {
          props.onArrive?.(payload);
        }
      }
    : undefined;
  const wrappedOnDestinationPreview = props.onDestinationPreview
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<DestinationPreviewEvent>(event);
        if (payload) {
          props.onDestinationPreview?.(payload);
        }
      }
    : undefined;
  const wrappedOnDestinationChanged = props.onDestinationChanged
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<DestinationChangedEvent>(event);
        if (payload) {
          props.onDestinationChanged?.(payload);
        }
      }
    : undefined;
  const wrappedOnError = props.onError
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<NavigationError>(event);
        if (payload) {
          props.onError?.(payload);
        }
      }
    : undefined;
  const wrappedOnCancelNavigation = props.onCancelNavigation
    ? () => {
        props.onCancelNavigation?.();
      }
    : undefined;

  const sanitizedStartOrigin = props.startOrigin
    ? {
        latitude: props.startOrigin.latitude,
        longitude: props.startOrigin.longitude,
      }
    : undefined;

  return {
    ...props,
    startOrigin: sanitizedStartOrigin,
    routeAlternatives: props.routeAlternatives ?? props.showsContinuousAlternatives,
    showsTripProgress,
    showsManeuverView,
    showsActionButtons,
    androidActionButtons: undefined,
    bottomSheet: undefined,
    onLocationChange: wrappedOnLocationChange,
    onRouteProgressChange: wrappedOnRouteProgressChange,
    onBannerInstruction: wrappedOnBannerInstruction,
    onArrive: wrappedOnArrive,
    onDestinationPreview: wrappedOnDestinationPreview,
    onDestinationChanged: wrappedOnDestinationChanged,
    onCancelNavigation: wrappedOnCancelNavigation,
    onError: wrappedOnError,
  };
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
  return emitter.addListener("onLocationChange", (event: unknown) => {
    const payload = unwrapNativeEventPayload<LocationUpdate>(event);
    if (payload) {
      listener(payload);
    }
  });
}

/**
 * Subscribe to route progress updates.
 */
export function addRouteProgressChangeListener(
  listener: (progress: RouteProgress) => void,
): Subscription {
  return emitter.addListener("onRouteProgressChange", (event: unknown) => {
    const payload = unwrapNativeEventPayload<RouteProgress>(event);
    if (payload) {
      listener(payload);
    }
  });
}

/**
 * Subscribe to arrival events.
 */
export function addArriveListener(listener: (point: ArrivalEvent) => void): Subscription {
  return emitter.addListener("onArrive", (event: unknown) => {
    const payload = unwrapNativeEventPayload<ArrivalEvent>(event);
    if (payload) {
      listener(payload);
    }
  });
}

/**
 * Subscribe to destination preview events.
 */
export function addDestinationPreviewListener(
  listener: (event: DestinationPreviewEvent) => void,
): Subscription {
  return emitter.addListener("onDestinationPreview", (event: unknown) => {
    const payload = unwrapNativeEventPayload<DestinationPreviewEvent>(event);
    if (payload) {
      listener(payload);
    }
  });
}

/**
 * Subscribe to destination changed events.
 */
export function addDestinationChangedListener(
  listener: (event: DestinationChangedEvent) => void,
): Subscription {
  return emitter.addListener("onDestinationChanged", (event: unknown) => {
    const payload = unwrapNativeEventPayload<DestinationChangedEvent>(event);
    if (payload) {
      listener(payload);
    }
  });
}

/**
 * Subscribe to cancellation events.
 */
export function addCancelNavigationListener(listener: () => void): Subscription {
  return emitter.addListener("onCancelNavigation", () => {
    listener();
  });
}

/**
 * Subscribe to native errors (token issues, route fetch failures, permission failures, etc.).
 */
export function addErrorListener(listener: (error: NavigationError) => void): Subscription {
  return emitter.addListener("onError", (event: unknown) => {
    const payload = unwrapNativeEventPayload<NavigationError>(event);
    if (payload) {
      listener(payload);
    }
  });
}

/**
 * Subscribe to banner instruction updates.
 */
export function addBannerInstructionListener(
  listener: (instruction: BannerInstruction) => void,
): Subscription {
  return emitter.addListener("onBannerInstruction", (event: unknown) => {
    const payload = unwrapNativeEventPayload<BannerInstruction>(event);
    if (payload) {
      listener(payload);
    }
  });
}

/**
 * Subscribe to full-screen bottom-sheet action button presses.
 */
export function addBottomSheetActionPressListener(
  listener: (event: BottomSheetActionEvent) => void,
): Subscription {
  return emitter.addListener("onBottomSheetActionPress", (event: unknown) => {
    const payload = unwrapNativeEventPayload<BottomSheetActionEvent>(event);
    if (payload) {
      listener(payload);
    }
  });
}

/**
 * Embedded native navigation component.
 *
 * Use this when you need navigation inside your own screen layout instead of full-screen modal navigation.
 */
export function MapboxNavigationView(
  props: MapboxNavigationViewProps & ViewProps,
) {
  const bottomSheet = props.bottomSheet;
  const useOverlayBottomSheet = !!bottomSheet?.enabled &&
    (bottomSheet.mode === "overlay" || Platform.OS === "ios");
  const collapsedHeight = Math.max(56, Math.min(bottomSheet?.collapsedHeight ?? 112, 400));
  const expandedHeight = Math.max(collapsedHeight, Math.min(bottomSheet?.expandedHeight ?? 280, 700));
  const initialExpanded = bottomSheet?.initialState === "expanded";
  const [expanded, setExpanded] = useState(initialExpanded);
  const [overlayBanner, setOverlayBanner] = useState<BannerInstruction | undefined>(undefined);
  const [overlayProgress, setOverlayProgress] = useState<RouteProgress | undefined>(undefined);
  const [overlayLocation, setOverlayLocation] = useState<LocationUpdate | undefined>(undefined);
  const [overlayMuted, setOverlayMuted] = useState(!!props.mute);
  const [overlayCameraMode, setOverlayCameraMode] = useState<"following" | "overview" | undefined>(undefined);

  useEffect(() => {
    setExpanded(bottomSheet?.initialState === "expanded");
  }, [bottomSheet?.initialState]);

  useEffect(() => {
    setOverlayMuted(!!props.mute);
  }, [props.mute]);

  useEffect(() => {
    setOverlayCameraMode(undefined);
  }, [props.cameraMode]);

  const nativeProps = useMemo(() => normalizeViewProps(props), [props]);
  const nativePropsWithOverlay = useMemo(() => {
    const onLocationChange = (event: unknown) => {
      const payload = unwrapNativeEventPayload<LocationUpdate>(event);
      if (payload) {
        setOverlayLocation(payload);
      }
      nativeProps.onLocationChange?.(event as any);
    };
    const onRouteProgressChange = (event: unknown) => {
      const payload = unwrapNativeEventPayload<RouteProgress>(event);
      if (payload) {
        setOverlayProgress(payload);
      }
      nativeProps.onRouteProgressChange?.(event as any);
    };
    const onBannerInstruction = (event: unknown) => {
      const payload = unwrapNativeEventPayload<BannerInstruction>(event);
      if (payload) {
        setOverlayBanner(payload);
      }
      nativeProps.onBannerInstruction?.(event as any);
    };
    return {
      ...nativeProps,
      cameraMode: overlayCameraMode ?? nativeProps.cameraMode,
      onLocationChange,
      onRouteProgressChange,
      onBannerInstruction,
    };
  }, [nativeProps, overlayCameraMode]);
  const NativeView = (() => {
    if (Platform.OS !== "android") {
      return requireNativeViewManager("MapboxNavigationView");
    }

    // Expo Modules on Android Fabric may register the view under the module name.
    try {
      return requireNativeViewManager("MapboxNavigationModule");
    } catch {
      try {
        return requireNativeViewManager("MapboxNavigationModule_MapboxNavigationView");
      } catch {
        return requireNativeViewManager("MapboxNavigationView");
      }
    }
  })();
  const renderOverlaySheet = () => {
    if (!useOverlayBottomSheet) {
      return null;
    }

    const emitAction = (actionId: string, source: "builtin" | "custom" = "custom") => {
      props.onOverlayBottomSheetActionPress?.({ actionId, source });
    };

    const runBuiltInQuickAction = async (actionId: string) => {
      switch (actionId) {
        case "overview":
          setOverlayCameraMode("overview");
          emitAction(actionId, "builtin");
          break;
        case "recenter":
          setOverlayCameraMode("following");
          emitAction(actionId, "builtin");
          break;
        case "mute":
          await setMuted(true);
          setOverlayMuted(true);
          emitAction(actionId, "builtin");
          break;
        case "unmute":
          await setMuted(false);
          setOverlayMuted(false);
          emitAction(actionId, "builtin");
          break;
        case "toggleMute": {
          const nextMuted = !overlayMuted;
          await setMuted(nextMuted);
          setOverlayMuted(nextMuted);
          emitAction(actionId, "builtin");
          break;
        }
        case "stop":
          await stopNavigation();
          emitAction(actionId, "builtin");
          break;
        default:
          break;
      }
    };

    const context = {
      expanded,
      expand: () => setExpanded(true),
      collapse: () => setExpanded(false),
      toggle: () => setExpanded((v) => !v),
      bannerInstruction: overlayBanner,
      routeProgress: overlayProgress,
      location: overlayLocation,
      stopNavigation,
      emitAction: (actionId: string) => emitAction(actionId, "custom"),
    };

    const customSheet = props.renderBottomSheet?.(context);
    const staticSheet = props.bottomSheetContent;
    const builtInQuickActions: Array<{
      id: string;
      actionId: string;
      label: string;
      variant: "primary" | "secondary" | "ghost";
    }> = [];
    (bottomSheet?.builtInQuickActions ?? []).forEach((actionId) => {
      if (actionId === "overview") {
        builtInQuickActions.push({
          id: "__builtin_overview",
          actionId,
          label: "Overview",
          variant: "secondary",
        });
      } else if (actionId === "recenter") {
        builtInQuickActions.push({
          id: "__builtin_recenter",
          actionId,
          label: "Recenter",
          variant: "secondary",
        });
      } else if (actionId === "mute") {
        builtInQuickActions.push({
          id: "__builtin_mute",
          actionId,
          label: "Mute",
          variant: "ghost",
        });
      } else if (actionId === "unmute") {
        builtInQuickActions.push({
          id: "__builtin_unmute",
          actionId,
          label: "Unmute",
          variant: "ghost",
        });
      } else if (actionId === "toggleMute") {
        builtInQuickActions.push({
          id: "__builtin_toggle_mute",
          actionId,
          label: overlayMuted ? "Unmute" : "Mute",
          variant: "ghost",
        });
      } else if (actionId === "stop") {
        builtInQuickActions.push({
          id: "__builtin_stop",
          actionId,
          label: "Stop",
          variant: "primary",
        });
      }
    });
    const allQuickActions: Array<{
      id: string;
      actionId: string;
      label: string;
      variant: "primary" | "secondary" | "ghost";
    }> = [
      ...builtInQuickActions,
      ...(bottomSheet?.quickActions ?? []).map((action) => ({
        id: action?.id,
        actionId: action?.id,
        label: action?.label,
        variant: action?.variant ?? "primary",
      })),
    ];
    const etaText = formatEta(overlayProgress?.durationRemaining);
    const durationText = overlayProgress?.durationRemaining != null
      ? formatDuration(overlayProgress.durationRemaining)
      : undefined;
    const defaultSheet = (
      <View style={styles.defaultSheet}>
        {nativeProps.showsManeuverView !== false ? (
          <View style={styles.defaultCard}>
            <Text style={styles.defaultLabel}>{bottomSheet?.defaultManeuverTitle ?? "Maneuver"}</Text>
            <Text style={styles.defaultPrimary} numberOfLines={2}>
              {overlayBanner?.primaryText ?? "Waiting for route instructions..."}
            </Text>
            {overlayBanner?.secondaryText ? (
              <Text style={styles.defaultSecondary} numberOfLines={1}>
                {overlayBanner.secondaryText}
              </Text>
            ) : null}
          </View>
        ) : null}
        {nativeProps.showsTripProgress !== false ? (
          <View style={styles.defaultCard}>
            <Text style={styles.defaultLabel}>{bottomSheet?.defaultTripProgressTitle ?? "Trip Progress"}</Text>
            <Text style={styles.defaultPrimary}>
              {overlayProgress
                ? `${Math.round(overlayProgress.distanceRemaining)} m remaining`
                : "Waiting for progress..."}
            </Text>
            <Text style={styles.defaultSecondary}>
              {overlayProgress
                ? `${etaText ? `${etaText} • ` : ""}${durationText ? `${durationText} left • ` : ""}${Math.round((overlayProgress.fractionTraveled || 0) * 100)}% completed`
                : (overlayLocation
                  ? `${overlayLocation.latitude.toFixed(5)}, ${overlayLocation.longitude.toFixed(5)}`
                  : "Location not available")}
            </Text>
          </View>
        ) : null}
        {(allQuickActions.length ?? 0) > 0 ? (
          <View style={styles.quickActionsRow}>
            {allQuickActions.map((action) => (
              <Pressable
                key={action?.id}
                onPress={() => {
                  if (action?.id.startsWith("__builtin_")) {
                    runBuiltInQuickAction(action?.actionId).catch(() => {
                      emitAction(`error:${action?.actionId}`, "builtin");
                    });
                  } else {
                    emitAction(action?.actionId, "custom");
                  }
                }}
                style={[
                  styles.quickActionButton,
                  action?.variant === "secondary" && styles.quickActionButtonSecondary,
                  action?.variant === "ghost" && styles.quickActionButtonGhost,
                ]}
              >
                <Text
                  style={[
                    styles.quickActionLabel,
                    action?.variant === "ghost" && styles.quickActionLabelGhost,
                  ]}
                  numberOfLines={1}
                >
                  {action?.label}
                </Text>
              </Pressable>
            ))}
          </View>
        ) : null}
      </View>
    );
    const content = customSheet ?? staticSheet ?? (bottomSheet?.showDefaultContent === false ? null : defaultSheet);
    const currentHeight = expanded ? expandedHeight : collapsedHeight;
    const canToggle = bottomSheet?.enableTapToToggle !== false;
    const showHandle = bottomSheet?.showHandle !== false;

    const contentHorizontalPadding = Math.max(
      0,
      Math.min(bottomSheet?.contentHorizontalPadding ?? 14, 48),
    );
    const contentBottomPadding = Math.max(
      0,
      Math.min(bottomSheet?.contentBottomPadding ?? 14, 60),
    );
    const contentTopSpacing = Math.max(
      0,
      Math.min(bottomSheet?.contentTopSpacing ?? 0, 24),
    );

    return (
      <View pointerEvents="box-none" style={styles.overlayRoot}>
        <View
          style={[
            styles.sheetContainer,
            { height: currentHeight },
            bottomSheet?.containerStyle,
          ]}
        >
          {showHandle ? (
            <Pressable
              accessibilityRole="button"
              onPress={canToggle ? context.toggle : undefined}
              style={[styles.sheetHandle, bottomSheet?.handleStyle]}
            />
          ) : null}
          <View
            style={[
              styles.sheetContent,
              {
                paddingHorizontal: contentHorizontalPadding,
                paddingBottom: contentBottomPadding,
                paddingTop: contentTopSpacing,
              },
              bottomSheet?.contentContainerStyle,
            ]}
          >
            {content}
          </View>
        </View>
      </View>
    );
  };

  if (!props.children && !useOverlayBottomSheet) {
    return <NativeView {...nativePropsWithOverlay} />;
  }

  return (
    <View style={props.style}>
      <NativeView
        {...nativePropsWithOverlay}
        style={StyleSheet.absoluteFill}
      />
      {props.children ? (
        <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
          {props.children}
        </View>
      ) : null}
      {renderOverlaySheet()}
    </View>
  );
}

const styles = StyleSheet.create({
  overlayRoot: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: "flex-end",
  },
  sheetContainer: {
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    backgroundColor: "rgba(12, 18, 32, 0.94)",
    overflow: "hidden",
  },
  sheetHandle: {
    alignSelf: "center",
    width: 42,
    height: 5,
    borderRadius: 999,
    backgroundColor: "rgba(255,255,255,0.35)",
    marginTop: 10,
    marginBottom: 8,
  },
  sheetContent: {
    flex: 1,
  },
  defaultSheet: {
    flex: 1,
    gap: 8,
  },
  defaultCard: {
    borderRadius: 10,
    backgroundColor: "rgba(255,255,255,0.08)",
    paddingHorizontal: 10,
    paddingVertical: 8,
    gap: 2,
  },
  defaultLabel: {
    color: "rgba(255,255,255,0.72)",
    fontSize: 11,
    fontWeight: "600",
  },
  defaultPrimary: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "700",
  },
  defaultSecondary: {
    color: "rgba(255,255,255,0.8)",
    fontSize: 12,
    fontWeight: "500",
  },
  quickActionsRow: {
    flexDirection: "row",
    gap: 8,
    flexWrap: "wrap",
  },
  quickActionButton: {
    backgroundColor: "#2563eb",
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  quickActionButtonSecondary: {
    backgroundColor: "#1d4ed8",
  },
  quickActionButtonGhost: {
    backgroundColor: "transparent",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.35)",
  },
  quickActionLabel: {
    color: "#ffffff",
    fontSize: 12,
    fontWeight: "700",
  },
  quickActionLabelGhost: {
    color: "rgba(255,255,255,0.92)",
  },
});

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
  addDestinationPreviewListener,
  addDestinationChangedListener,
  addCancelNavigationListener,
  addErrorListener,
  addBannerInstructionListener,
  addBottomSheetActionPressListener,
  MapboxNavigationView,
};

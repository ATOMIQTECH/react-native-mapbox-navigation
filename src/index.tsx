import {
  requireNativeModule,
  requireNativeViewManager,
} from "expo-modules-core";
import {
  Fragment,
  isValidElement,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  PanResponder,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
  ViewProps,
} from "react-native";

import type {
  ArrivalEvent,
  BannerInstruction,
  BottomSheetActionEvent,
  DestinationChangedEvent,
  DestinationPreviewEvent,
  JourneyData,
  LocationUpdate,
  MapboxNavigationModule as MapboxNavigationModuleType,
  MapboxNavigationViewProps,
  NavigationSettings,
  NavigationError,
  RouteChangeEvent,
  RouteProgress,
  Subscription,
} from "./MapboxNavigation.types";

const MapboxNavigationModule = requireNativeModule<MapboxNavigationModuleType>(
  "MapboxNavigationModule",
);

const MapboxNavigationNativeView = requireNativeViewManager(
  "MapboxNavigationModule",
);

const emitter = MapboxNavigationModule as unknown as {
  addListener: (
    eventName: string,
    listener: (...args: any[]) => void,
  ) => Subscription;
};

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

function normalizeNativeError(
  error: unknown,
  fallbackCode = "NATIVE_ERROR",
): Error {
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
  const etaDate = new Date(
    Date.now() + (durationRemainingSeconds as number) * 1000,
  );
  const time = etaDate.toLocaleTimeString([], {
    hour: "numeric",
    minute: "2-digit",
  });
  return `Arrive ${time}`;
}

function normalizeViewProps(
  props: MapboxNavigationViewProps & ViewProps,
): MapboxNavigationViewProps & ViewProps {
  const overlayModeActive =
    props.bottomSheet?.enabled !== false &&
    props.bottomSheet?.mode === "overlay";
  let showsTripProgress = props.showsTripProgress;
  let showsManeuverView = props.showsManeuverView;
  let showsActionButtons = props.showsActionButtons;
  let showCancelButton = props.showCancelButton;
  if (props.bottomSheet && !overlayModeActive) {
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

  if (overlayModeActive) {
    // In overlay mode we keep native top maneuver banner, but hide native bottom panel/actions
    // so custom sheet is the only bottom UI.
    showsTripProgress = false;
    showsManeuverView = true;
    showsActionButtons = false;
    showCancelButton = false;
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
  const wrappedOnJourneyDataChange = props.onJourneyDataChange
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<JourneyData>(event);
        if (payload) {
          props.onJourneyDataChange?.(payload);
        }
      }
    : undefined;
  const wrappedOnRouteChange = props.onRouteChange
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<RouteChangeEvent>(event);
        if (payload) {
          props.onRouteChange?.(payload);
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
        const payload =
          unwrapNativeEventPayload<DestinationPreviewEvent>(event);
        if (payload) {
          props.onDestinationPreview?.(payload);
        }
      }
    : undefined;
  const wrappedOnDestinationChanged = props.onDestinationChanged
    ? (event: unknown) => {
        const payload =
          unwrapNativeEventPayload<DestinationChangedEvent>(event);
        if (payload) {
          props.onDestinationChanged?.(payload);
        }
      }
    : undefined;
  const wrappedOnError = (event: unknown) => {
    const payload = unwrapNativeEventPayload<NavigationError>(event);
    if (!payload) {
      return;
    }
    if (props.onError) {
      props.onError(payload);
      return;
    }
    console.warn(
      `[react-native-mapbox-navigation] embedded onError: ${payload.code}: ${payload.message}`,
    );
  };
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
    enabled: props.enabled === true,
    startOrigin: sanitizedStartOrigin,
    routeAlternatives:
      props.routeAlternatives ?? props.showsContinuousAlternatives,
    showsTripProgress,
    showsManeuverView,
    showsActionButtons,
    showCancelButton,
    androidActionButtons: undefined,
    bottomSheet: undefined,
    onLocationChange: wrappedOnLocationChange,
    onRouteProgressChange: wrappedOnRouteProgressChange,
    onJourneyDataChange: wrappedOnJourneyDataChange,
    onRouteChange: wrappedOnRouteChange,
    onBannerInstruction: wrappedOnBannerInstruction,
    onArrive: wrappedOnArrive,
    onDestinationPreview: wrappedOnDestinationPreview,
    onDestinationChanged: wrappedOnDestinationChanged,
    onCancelNavigation: wrappedOnCancelNavigation,
    onError: wrappedOnError,
  };
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
export async function setDistanceUnit(
  unit: "metric" | "imperial",
): Promise<void> {
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
 * Read current native navigation runtime settings.
 */
export async function getNavigationSettings(): Promise<NavigationSettings> {
  try {
    return await MapboxNavigationModule.getNavigationSettings();
  } catch (error) {
    throw normalizeNativeError(error, "GET_NAVIGATION_SETTINGS_FAILED");
  }
}

export async function stopNavigation(): Promise<boolean> {
  try {
    return await MapboxNavigationModule.stopNavigation();
  } catch (error) {
    throw normalizeNativeError(error, "STOP_NAVIGATION_FAILED");
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
 * Subscribe to aggregated journey data (location + progress + instruction) for custom UI.
 */
export function addJourneyDataChangeListener(
  listener: (data: JourneyData) => void,
): Subscription {
  return emitter.addListener("onJourneyDataChange", (event: unknown) => {
    const payload = unwrapNativeEventPayload<JourneyData>(event);
    if (payload) {
      listener(payload);
    }
  });
}

export function addRouteChangeListener(
  listener: (event: RouteChangeEvent) => void,
): Subscription {
  return emitter.addListener("onRouteChange", (event: unknown) => {
    const payload = unwrapNativeEventPayload<RouteChangeEvent>(event);
    if (payload) {
      listener(payload);
    }
  });
}

/**
 * Subscribe to arrival events.
 */
export function addArriveListener(
  listener: (point: ArrivalEvent) => void,
): Subscription {
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
export function addCancelNavigationListener(
  listener: () => void,
): Subscription {
  return emitter.addListener("onCancelNavigation", () => {
    listener();
  });
}

/**
 * Subscribe to native errors (token issues, route fetch failures, permission failures, etc.).
 */
export function addErrorListener(
  listener: (error: NavigationError) => void,
): Subscription {
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
 * Subscribe to native bottom-sheet action button presses.
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
 * Set `enabled={true}` to start embedded navigation. Default is disabled to avoid accidental session conflicts.
 */
export function MapboxNavigationView(
  props: MapboxNavigationViewProps & ViewProps,
) {
  const warnedCustomSheetOnlyRef = useRef(false);
  const bottomSheet =
    props.bottomSheet && props.bottomSheet.enabled !== false
      ? {
          ...props.bottomSheet,
          mode: "overlay" as const,
        }
      : props.bottomSheet;
  if (
    props.bottomSheet?.enabled !== false &&
    props.bottomSheet &&
    props.bottomSheet.mode !== "overlay" &&
    !warnedCustomSheetOnlyRef.current
  ) {
    warnedCustomSheetOnlyRef.current = true;
    console.warn(
      "[react-native-mapbox-navigation] Embedded mode is custom-sheet-only. Forcing bottomSheet.mode='overlay'.",
    );
  }
  const propsWithBottomSheet =
    bottomSheet === props.bottomSheet ? props : { ...props, bottomSheet };
  const useOverlayBottomSheet =
    !!bottomSheet?.enabled && bottomSheet?.mode === "overlay";
  const overlayLocationMinIntervalMs = Math.max(
    0,
    Math.min(
      bottomSheet?.overlayLocationUpdateIntervalMs ??
        (Platform.OS === "android" ? 900 : 300),
      3000,
    ),
  );
  const overlayProgressMinIntervalMs = Math.max(
    0,
    Math.min(
      bottomSheet?.overlayProgressUpdateIntervalMs ??
        (Platform.OS === "android" ? 700 : 300),
      3000,
    ),
  );
  const iosHiddenMode = Platform.OS === "ios";
  const collapsedHeight = Math.max(
    56,
    Math.min(bottomSheet?.collapsedHeight ?? 112, 400),
  );
  const expandedHeight = Math.max(
    collapsedHeight,
    Math.min(bottomSheet?.expandedHeight ?? 280, 700),
  );
  const requestedInitialState =
    bottomSheet?.initialState === "expanded" ||
    bottomSheet?.initialState === "collapsed" ||
    bottomSheet?.initialState === "hidden"
      ? bottomSheet.initialState
      : "collapsed";
  const initialState =
    requestedInitialState === "expanded"
      ? "expanded"
      : iosHiddenMode
        ? "hidden"
        : "collapsed";
  const [sheetState, setSheetState] = useState<
    "hidden" | "collapsed" | "expanded"
  >(initialState);
  const [overlayBanner, setOverlayBanner] = useState<
    BannerInstruction | undefined
  >(undefined);
  const [overlayProgress, setOverlayProgress] = useState<
    RouteProgress | undefined
  >(undefined);
  const [overlayLocation, setOverlayLocation] = useState<
    LocationUpdate | undefined
  >(undefined);
  const [overlayMuted, setOverlayMuted] = useState(!!props.mute);
  const [overlayCameraMode, setOverlayCameraMode] = useState<
    "following" | "overview" | undefined
  >(undefined);
  const overlayThrottleRef = useRef({
    locationAtMs: 0,
    progressAtMs: 0,
    bannerKey: "",
  });
  useEffect(() => {
    if (!useOverlayBottomSheet) {
      return;
    }
    const next =
      bottomSheet?.initialState === "expanded"
        ? "expanded"
        : iosHiddenMode
          ? "hidden"
          : "collapsed";
    setSheetState(next);
  }, [bottomSheet?.initialState, iosHiddenMode]);

  useEffect(() => {
    setOverlayMuted(!!props.mute);
  }, [props.mute]);

  useEffect(() => {
    setOverlayCameraMode(undefined);
  }, [props.cameraMode]);

  const nativeProps = useMemo(
    () => normalizeViewProps(propsWithBottomSheet),
    [propsWithBottomSheet],
  );
  const nativePropsWithOverlay = useMemo(() => {
    if (!useOverlayBottomSheet) {
      return nativeProps;
    }

    const onLocationChange = (event: unknown) => {
      const payload = unwrapNativeEventPayload<LocationUpdate>(event);
      if (payload) {
        const now = Date.now();
        if (
          now - overlayThrottleRef.current.locationAtMs >=
          overlayLocationMinIntervalMs
        ) {
          overlayThrottleRef.current.locationAtMs = now;
          setOverlayLocation(payload);
        }
      }
      nativeProps.onLocationChange?.(event as any);
    };
    const onRouteProgressChange = (event: unknown) => {
      const payload = unwrapNativeEventPayload<RouteProgress>(event);
      if (payload) {
        const now = Date.now();
        if (
          now - overlayThrottleRef.current.progressAtMs >=
          overlayProgressMinIntervalMs
        ) {
          overlayThrottleRef.current.progressAtMs = now;
          setOverlayProgress(payload);
        }
      }
      nativeProps.onRouteProgressChange?.(event as any);
    };
    const onBannerInstruction = (event: unknown) => {
      const payload = unwrapNativeEventPayload<BannerInstruction>(event);
      if (payload) {
        const bannerKey = [
          payload.primaryText ?? "",
          payload.secondaryText ?? "",
          Math.round(payload.stepDistanceRemaining ?? -1),
        ].join("|");
        if (bannerKey !== overlayThrottleRef.current.bannerKey) {
          overlayThrottleRef.current.bannerKey = bannerKey;
          setOverlayBanner(payload);
        }
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
  }, [
    nativeProps,
    overlayCameraMode,
    useOverlayBottomSheet,
    overlayLocationMinIntervalMs,
    overlayProgressMinIntervalMs,
  ]);
  const renderOverlaySheet = () => {
    if (!useOverlayBottomSheet) {
      return null;
    }

    const emitAction = (
      actionId: string,
      source: "builtin" | "custom" = "custom",
    ) => {
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
      state: sheetState,
      hidden: sheetState === "hidden",
      expanded: sheetState === "expanded",
      show: (next: "collapsed" | "expanded" = "collapsed") =>
        setSheetState(
          next === "expanded"
            ? "expanded"
            : iosHiddenMode
              ? "hidden"
              : "collapsed",
        ),
      hide: () => setSheetState(iosHiddenMode ? "hidden" : "collapsed"),
      expand: () => setSheetState("expanded"),
      collapse: () => setSheetState(iosHiddenMode ? "hidden" : "collapsed"),
      toggle: () =>
        setSheetState((v) =>
          v === "expanded"
            ? iosHiddenMode
              ? "hidden"
              : "collapsed"
            : "expanded",
        ),
      bannerInstruction: overlayBanner,
      routeProgress: overlayProgress,
      location: overlayLocation,
      stopNavigation,
      emitAction: (actionId: string) => emitAction(actionId, "custom"),
    };

    let customSheet = props.renderBottomSheet?.(context);
    if (
      isValidElement(customSheet) &&
      customSheet.type === Fragment &&
      (customSheet.props as any)?.children == null
    ) {
      customSheet = null;
    }
    const staticSheet = props.bottomSheetContent;
    const builtInQuickActions: {
      id: string;
      actionId: string;
      label: string;
      variant: "primary" | "secondary" | "ghost";
    }[] = [];
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
    const allQuickActions: {
      id: string;
      actionId: string;
      label: string;
      variant: "primary" | "secondary" | "ghost";
    }[] = [...builtInQuickActions];
    const etaText = formatEta(overlayProgress?.durationRemaining);
    const durationText =
      overlayProgress?.durationRemaining != null
        ? formatDuration(overlayProgress.durationRemaining)
        : undefined;
    const showCurrentStreet = bottomSheet?.showCurrentStreet !== false;
    const showRemainingDistance = bottomSheet?.showRemainingDistance !== false;
    const showRemainingDuration = bottomSheet?.showRemainingDuration !== false;
    const showETA = bottomSheet?.showETA !== false;
    const showCompletionPercent = bottomSheet?.showCompletionPercent !== false;
    const tripPrimaryParts: string[] = [];
    if (overlayProgress && showRemainingDistance) {
      tripPrimaryParts.push(
        `${Math.round(overlayProgress.distanceRemaining)} m`,
      );
    }
    if (overlayProgress && showRemainingDuration && durationText) {
      tripPrimaryParts.push(durationText);
    }
    if (overlayProgress && showETA && etaText) {
      tripPrimaryParts.push(etaText);
    }
    const tripSecondaryParts: string[] = [];
    if (showCurrentStreet && overlayBanner?.secondaryText) {
      tripSecondaryParts.push(overlayBanner.secondaryText);
    }
    if (overlayProgress && showCompletionPercent) {
      tripSecondaryParts.push(
        `${Math.round((overlayProgress.fractionTraveled || 0) * 100)}% completed`,
      );
    }
    const resolvedColorMode =
      bottomSheet?.colorMode ??
      (props.uiTheme === "dark" || props.uiTheme === "night"
        ? "dark"
        : "light");
    const isDark = resolvedColorMode === "dark";
    const sheetBackgroundColor = isDark ? "#202020" : "#ffffff";
    const sheetCornerRadius = 16;
    const handleColor = isDark ? "rgba(255,255,255,0.35)" : "rgba(0,0,0,0.3)";
    const primaryTextColor = isDark ? "#ffffff" : "#0f172a";
    const secondaryTextColor = isDark
      ? "rgba(255,255,255,0.8)"
      : "rgba(15,23,42,0.8)";
    const labelTextColor = isDark
      ? "rgba(255,255,255,0.72)"
      : "rgba(15,23,42,0.65)";
    const quickActionBorderColor = isDark
      ? "rgba(255,255,255,0.35)"
      : "rgba(15,23,42,0.25)";
    const quickPrimaryBackground = "#2563eb";
    const quickSecondaryBackground = isDark ? "#1d4ed8" : "#1e40af";
    const quickGhostText = isDark ? "rgba(255,255,255,0.92)" : "#0f172a";
    const quickPrimaryText = "#ffffff";
    const quickSecondaryText = "#ffffff";
    const defaultCardColor = isDark
      ? "rgba(255,255,255,0.08)"
      : "rgba(15,23,42,0.08)";
    const defaultSheet = (
      <View style={styles.defaultSheet}>
        {nativeProps.showsManeuverView !== false ? (
          <View
            style={[styles.defaultCard, { backgroundColor: defaultCardColor }]}
          >
            <Text
              style={[
                styles.defaultLabel,
                {
                  color: labelTextColor,
                  fontSize: 11,
                  fontWeight: "500",
                },
              ]}
            >
              {bottomSheet?.defaultManeuverTitle ?? "Maneuver"}
            </Text>
            <Text
              style={[
                styles.defaultPrimary,
                {
                  color: primaryTextColor,
                  fontSize: 14,
                  fontWeight: "700",
                },
              ]}
              numberOfLines={2}
            >
              {overlayBanner?.primaryText ??
                "Waiting for route instructions..."}
            </Text>
            {overlayBanner?.secondaryText ? (
              <Text
                style={[
                  styles.defaultSecondary,
                  {
                    color: secondaryTextColor,
                    fontSize: 12,
                    fontWeight: "500",
                  },
                ]}
                numberOfLines={1}
              >
                {overlayBanner.secondaryText}
              </Text>
            ) : null}
          </View>
        ) : null}
        {nativeProps.showsTripProgress !== false ? (
          <View
            style={[styles.defaultCard, { backgroundColor: defaultCardColor }]}
          >
            <Text
              style={[
                styles.defaultLabel,
                {
                  color: labelTextColor,
                  fontSize: 11,
                  fontWeight: "500",
                },
              ]}
            >
              {bottomSheet?.defaultTripProgressTitle ?? "Trip Progress"}
            </Text>
            <Text
              style={[
                styles.defaultPrimary,
                {
                  color: primaryTextColor,
                  fontSize: 14,
                  fontWeight: "700",
                },
              ]}
            >
              {overlayProgress
                ? tripPrimaryParts.length > 0
                  ? tripPrimaryParts.join(" • ")
                  : "Progress available"
                : "Waiting for progress..."}
            </Text>
            <Text
              style={[
                styles.defaultSecondary,
                {
                  color: secondaryTextColor,
                  fontSize: 12,
                  fontWeight: "500",
                },
              ]}
            >
              {overlayProgress
                ? tripSecondaryParts.length > 0
                  ? tripSecondaryParts.join(" • ")
                  : "On route"
                : overlayLocation
                  ? `${overlayLocation.latitude.toFixed(5)}, ${overlayLocation.longitude.toFixed(5)}`
                  : "Location not available"}
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
                  {
                    borderRadius: 999,
                    borderWidth: 1,
                    borderColor: quickActionBorderColor,
                    minHeight: 34,
                    backgroundColor: quickPrimaryBackground,
                  },
                  action?.variant === "secondary" && [
                    styles.quickActionButtonSecondary,
                    { backgroundColor: quickSecondaryBackground },
                  ],
                  action?.variant === "ghost" && [
                    styles.quickActionButtonGhost,
                    { backgroundColor: "transparent" },
                  ],
                ]}
              >
                <Text
                  style={[
                    styles.quickActionLabel,
                    {
                      fontSize: 12,
                      fontWeight: "700",
                      color: quickPrimaryText,
                    },
                    action?.variant === "secondary" && {
                      color: quickSecondaryText,
                    },
                    action?.variant === "ghost" && [
                      styles.quickActionLabelGhost,
                      { color: quickGhostText },
                    ],
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
    const content =
      customSheet ??
      staticSheet ??
      (bottomSheet?.showDefaultContent === false ? null : defaultSheet);
    const currentHeight =
      sheetState === "hidden"
        ? 0
        : sheetState === "collapsed"
          ? collapsedHeight
          : expandedHeight;
    const collapsedBottomOffset = Math.max(
      0,
      Math.min(bottomSheet?.collapsedBottomOffset ?? 24, 80),
    );
    const canToggle = bottomSheet?.enableTapToToggle !== false;
    const showHandle = bottomSheet?.showHandle !== false;

    const contentHorizontalPadding = 14;
    const contentBottomPadding = 14;
    const contentTopSpacing = 0;

    const backdropPress = () => {
      setSheetState(iosHiddenMode ? "hidden" : "collapsed");
    };

    const backdropVisible = sheetState === "expanded";
    const hiddenGrabberResponder = PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onStartShouldSetPanResponderCapture: () => true,
      onMoveShouldSetPanResponder: (_evt, gesture) =>
        Math.abs(gesture.dy) > Math.abs(gesture.dx) && Math.abs(gesture.dy) > 4,
      onMoveShouldSetPanResponderCapture: (_evt, gesture) =>
        Math.abs(gesture.dy) > Math.abs(gesture.dx) && Math.abs(gesture.dy) > 2,
      onPanResponderTerminationRequest: () => false,
      onPanResponderRelease: (_evt, gesture) => {
        if (gesture.dy < -8 || gesture.vy < -0.3) {
          setSheetState("expanded");
        }
      },
    });
    const sheetPanResponder = PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: (_evt, gesture) =>
        Math.abs(gesture.dy) > Math.abs(gesture.dx) && Math.abs(gesture.dy) > 4,
      onPanResponderRelease: (_evt, gesture) => {
        if (gesture.dy < -10 || gesture.vy < -0.35) {
          setSheetState("expanded");
          return;
        }
        if (gesture.dy > 10 || gesture.vy > 0.35) {
          setSheetState(iosHiddenMode ? "hidden" : "collapsed");
        }
      },
    });

    return (
      <View pointerEvents="box-none" style={styles.overlayRoot}>
        {iosHiddenMode && sheetState === "hidden" ? (
          <View
            pointerEvents="auto"
            style={styles.iosHiddenRevealZone}
            {...hiddenGrabberResponder.panHandlers}
          />
        ) : null}
        {sheetState === "hidden" ? (
          <View pointerEvents="box-none" style={styles.hiddenGrabberWrap}>
            <View
              pointerEvents="auto"
              style={styles.hiddenGrabberTouchArea}
              {...hiddenGrabberResponder.panHandlers}
            >
              <Pressable
                accessibilityRole="button"
                onPress={() => setSheetState("expanded")}
                style={[styles.hiddenGrabber, { backgroundColor: handleColor }]}
              />
            </View>
          </View>
        ) : null}
        {backdropVisible ? (
          <Pressable onPress={backdropPress} style={styles.overlayBackdrop} />
        ) : null}
        <View
          style={[
            styles.sheetContainer,
            {
              height: currentHeight,
              bottom:
                sheetState === "collapsed"
                  ? -collapsedBottomOffset
                  : sheetState === "hidden"
                    ? -(collapsedHeight + 80)
                    : 0,
              backgroundColor: sheetBackgroundColor,
              borderTopLeftRadius: sheetCornerRadius,
              borderTopRightRadius: sheetCornerRadius,
            },
          ]}
          {...sheetPanResponder.panHandlers}
          pointerEvents="auto"
        >
          {showHandle ? (
            <Pressable
              accessibilityRole="button"
              onPress={canToggle ? context.toggle : undefined}
              style={[styles.sheetHandle, { backgroundColor: handleColor }]}
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
            ]}
          >
            {content}
          </View>
        </View>
      </View>
    );
  };

  if (!props.children && !useOverlayBottomSheet) {
    return <MapboxNavigationNativeView {...nativePropsWithOverlay} />;
  }

  return (
    <View style={props.style}>
      <MapboxNavigationNativeView
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
  overlayBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(0,0,0,0.35)",
  },
  hiddenGrabberWrap: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 10,
    alignItems: "center",
    zIndex: 2,
  },
  hiddenGrabberTouchArea: {
    width: 160,
    height: 28,
    alignItems: "center",
    justifyContent: "center",
  },
  hiddenGrabber: {
    width: 84,
    height: 8,
    borderRadius: 999,
    backgroundColor: "rgba(255,255,255,0.35)",
  },
  iosHiddenRevealZone: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    height: 130,
    backgroundColor: "transparent",
    zIndex: 1,
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
  setMuted,
  setVoiceVolume,
  setDistanceUnit,
  setLanguage,
  getNavigationSettings,
  stopNavigation,
  addLocationChangeListener,
  addRouteProgressChangeListener,
  addJourneyDataChangeListener,
  addRouteChangeListener,
  addArriveListener,
  addDestinationPreviewListener,
  addDestinationChangedListener,
  addCancelNavigationListener,
  addErrorListener,
  addBannerInstructionListener,
  addBottomSheetActionPressListener,
  MapboxNavigationView,
};

import { requireNativeModule, requireNativeViewManager } from 'expo-modules-core'
import { Fragment, isValidElement, useEffect, useMemo, useRef, useState } from 'react'
import {
  PanResponder,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
  type ViewProps,
} from 'react-native'

import type {
  ArrivalEvent,
  BannerInstruction,
  BottomSheetActionEvent,
  BottomSheetRenderContext,
  CameraFollowingState,
  DestinationChangedEvent,
  DestinationPreviewEvent,
  EndOfRouteFeedbackEvent,
  EndOfRouteFeedbackRenderContext,
  FloatingButtonsRenderContext,
  JourneyData,
  LocationUpdate,
  MapboxNavigationFloatingButtonProps,
  MapboxNavigationFloatingButtonsStackProps,
  MapboxNavigationModule as MapboxNavigationModuleType,
  MapboxNavigationViewProps,
  NavigationError,
  NavigationSettings,
  RouteChangeEvent,
  RouteProgress,
  Subscription,
} from './MapboxNavigation.types'

const MapboxNavigationModule =
  requireNativeModule<MapboxNavigationModuleType>('MapboxNavigationModule')

const MapboxNavigationNativeView = requireNativeViewManager('MapboxNavigationModule')

const emitter = MapboxNavigationModule as unknown as {
  addListener: (eventName: string, listener: (...args: any[]) => void) => Subscription
}

function unwrapNativeEventPayload<T>(payload: unknown): T | undefined {
  if (payload == null) {
    return undefined
  }
  if (typeof payload === 'object') {
    const value = payload as {
      nativeEvent?: unknown
      payload?: unknown
      data?: unknown
    }
    const first = value.nativeEvent ?? value.payload ?? value.data
    if (first != null) {
      return unwrapNativeEventPayload<T>(first)
    }
  }
  return payload as T
}

function normalizeNativeError(error: unknown, fallbackCode = 'NATIVE_ERROR'): Error {
  if (error instanceof Error) {
    return error
  }

  const candidate = error as { code?: string; message?: string } | undefined
  const code = candidate?.code ?? fallbackCode
  const message = candidate?.message ?? 'Unknown native error'
  return new Error(`[${code}] ${message}`)
}

function normalizeOverlayNode<T>(node: T): T | null {
  if (isValidElement(node) && node.type === Fragment && (node.props as any)?.children == null) {
    return null
  }
  return node
}

function formatDuration(seconds: number): string {
  const totalMinutes = Math.max(0, Math.round(seconds / 60))
  if (totalMinutes < 60) {
    return `${totalMinutes} min`
  }
  const hours = Math.floor(totalMinutes / 60)
  const mins = totalMinutes % 60
  return mins > 0 ? `${hours}h ${mins}m` : `${hours}h`
}

function formatEta(durationRemainingSeconds?: number): string | undefined {
  if (!Number.isFinite(durationRemainingSeconds ?? Number.NaN)) {
    return undefined
  }
  const etaDate = new Date(Date.now() + (durationRemainingSeconds as number) * 1000)
  const time = etaDate.toLocaleTimeString([], {
    hour: 'numeric',
    minute: '2-digit',
  })
  return `Arrive ${time}`
}

function normalizeViewProps(
  props: MapboxNavigationViewProps & ViewProps
): MapboxNavigationViewProps & ViewProps {
  const overlayModeActive =
    props.bottomSheet?.enabled !== false && props.bottomSheet?.mode === 'overlay'
  let showsTripProgress = props.showsTripProgress
  let showsManeuverView = props.showsManeuverView
  let showsActionButtons = props.showsActionButtons
  let showCancelButton = props.showCancelButton
  if (props.bottomSheet && !overlayModeActive) {
    const enabled = props.bottomSheet.enabled
    if (enabled === false) {
      showsTripProgress = false
      showsManeuverView = false
      showsActionButtons = false
    } else {
      if (showsTripProgress == null) {
        showsTripProgress = props.bottomSheet.showsTripProgress
      }
      if (showsManeuverView == null) {
        showsManeuverView = props.bottomSheet.showsManeuverView
      }
      if (showsActionButtons == null) {
        showsActionButtons = props.bottomSheet.showsActionButtons
      }
    }
  }

  if (overlayModeActive) {
    if (Platform.OS === 'android') {
      // Android embedded mode keeps custom sheet as the only bottom UI.
      showsTripProgress = false
      showsActionButtons = false
      showCancelButton = false
    }
  }

  const wrappedOnLocationChange = props.onLocationChange
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<LocationUpdate>(event)
        if (payload) {
          props.onLocationChange?.(payload)
        }
      }
    : undefined
  const wrappedOnRouteProgressChange = props.onRouteProgressChange
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<RouteProgress>(event)
        if (payload) {
          props.onRouteProgressChange?.(payload)
        }
      }
    : undefined
  const wrappedOnCameraFollowingStateChange = props.onCameraFollowingStateChange
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<CameraFollowingState>(event)
        if (payload) {
          props.onCameraFollowingStateChange?.(payload)
        }
      }
    : undefined
  const wrappedOnJourneyDataChange = props.onJourneyDataChange
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<JourneyData>(event)
        if (payload) {
          props.onJourneyDataChange?.(payload)
        }
      }
    : undefined
  const wrappedOnRouteChange = props.onRouteChange
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<RouteChangeEvent>(event)
        if (payload) {
          props.onRouteChange?.(payload)
        }
      }
    : undefined
  const wrappedOnBannerInstruction = props.onBannerInstruction
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<BannerInstruction>(event)
        if (payload) {
          props.onBannerInstruction?.(payload)
        }
      }
    : undefined
  const wrappedOnArrive = props.onArrive
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<ArrivalEvent>(event)
        if (payload) {
          props.onArrive?.(payload)
        }
      }
    : undefined
  const wrappedOnDestinationPreview = props.onDestinationPreview
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<DestinationPreviewEvent>(event)
        if (payload) {
          props.onDestinationPreview?.(payload)
        }
      }
    : undefined
  const wrappedOnDestinationChanged = props.onDestinationChanged
    ? (event: unknown) => {
        const payload = unwrapNativeEventPayload<DestinationChangedEvent>(event)
        if (payload) {
          props.onDestinationChanged?.(payload)
        }
      }
    : undefined
  const wrappedOnError = (event: unknown) => {
    const payload = unwrapNativeEventPayload<NavigationError>(event)
    if (!payload) {
      return
    }
    if (props.onError) {
      props.onError(payload)
      return
    }
    console.warn(
      `[react-native-mapbox-navigation] embedded onError: ${payload.code}: ${payload.message}`
    )
  }
  const wrappedOnCancelNavigation = props.onCancelNavigation
    ? () => {
        props.onCancelNavigation?.()
      }
    : undefined

  const sanitizedStartOrigin = props.startOrigin
    ? {
        latitude: props.startOrigin.latitude,
        longitude: props.startOrigin.longitude,
      }
    : undefined

  return {
    ...props,
    enabled: props.enabled === true,
    startOrigin: sanitizedStartOrigin,
    routeAlternatives: props.routeAlternatives ?? props.showsContinuousAlternatives,
    showsTripProgress,
    showsManeuverView,
    showsActionButtons,
    showCancelButton,
    androidActionButtons: undefined,
    bottomSheet: undefined,
    bottomSheetContent: undefined,
    renderBottomSheet: undefined,
    bottomSheetComponent: undefined,
    floatingButtons: undefined,
    renderFloatingButtons: undefined,
    floatingButtonsComponent: undefined,
    hideFloatingButtonsOnArrival: undefined,
    floatingButtonsContainerStyle: undefined,
    renderEndOfRouteFeedback: undefined,
    endOfRouteFeedbackComponent: undefined,
    children: undefined,
    showsEndOfRouteFeedback: undefined,
    onEndOfRouteFeedbackSubmit: undefined,
    onOverlayBottomSheetActionPress: undefined,
    onLocationChange: wrappedOnLocationChange,
    onRouteProgressChange: wrappedOnRouteProgressChange,
    onCameraFollowingStateChange: wrappedOnCameraFollowingStateChange,
    onJourneyDataChange: wrappedOnJourneyDataChange,
    onRouteChange: wrappedOnRouteChange,
    onBannerInstruction: wrappedOnBannerInstruction,
    onArrive: wrappedOnArrive,
    onDestinationPreview: wrappedOnDestinationPreview,
    onDestinationChanged: wrappedOnDestinationChanged,
    onCancelNavigation: wrappedOnCancelNavigation,
    onError: wrappedOnError,
  }
}

/**
 * Enable or disable voice guidance.
 *
 * @param muted `true` to mute voice instructions.
 */
export async function setMuted(muted: boolean): Promise<void> {
  try {
    await MapboxNavigationModule.setMuted(muted)
  } catch (error) {
    throw normalizeNativeError(error, 'SET_MUTED_FAILED')
  }
}

/**
 * Set voice instruction volume in range `0..1`.
 */
export async function setVoiceVolume(volume: number): Promise<void> {
  try {
    await MapboxNavigationModule.setVoiceVolume(volume)
  } catch (error) {
    throw normalizeNativeError(error, 'SET_VOICE_VOLUME_FAILED')
  }
}

/**
 * Set spoken and displayed distance units.
 */
export async function setDistanceUnit(unit: 'metric' | 'imperial'): Promise<void> {
  try {
    await MapboxNavigationModule.setDistanceUnit(unit)
  } catch (error) {
    throw normalizeNativeError(error, 'SET_DISTANCE_UNIT_FAILED')
  }
}

/**
 * Set instruction language (BCP-47-like code, for example `en`, `fr`).
 */
export async function setLanguage(language: string): Promise<void> {
  try {
    await MapboxNavigationModule.setLanguage(language)
  } catch (error) {
    throw normalizeNativeError(error, 'SET_LANGUAGE_FAILED')
  }
}

/**
 * Read current native navigation runtime settings.
 */
export async function getNavigationSettings(): Promise<NavigationSettings> {
  try {
    return await MapboxNavigationModule.getNavigationSettings()
  } catch (error) {
    throw normalizeNativeError(error, 'GET_NAVIGATION_SETTINGS_FAILED')
  }
}

export async function stopNavigation(): Promise<boolean> {
  try {
    return await MapboxNavigationModule.stopNavigation()
  } catch (error) {
    throw normalizeNativeError(error, 'STOP_NAVIGATION_FAILED')
  }
}

export async function resumeCameraFollowing(): Promise<boolean> {
  try {
    return await MapboxNavigationModule.resumeCameraFollowing()
  } catch (error) {
    throw normalizeNativeError(error, 'RESUME_CAMERA_FOLLOWING_FAILED')
  }
}

/**
 * Subscribe to location updates from native navigation.
 */
export function addLocationChangeListener(
  listener: (location: LocationUpdate) => void
): Subscription {
  return emitter.addListener('onLocationChange', (event: unknown) => {
    const payload = unwrapNativeEventPayload<LocationUpdate>(event)
    if (payload) {
      listener(payload)
    }
  })
}

/**
 * Subscribe to route progress updates.
 */
export function addRouteProgressChangeListener(
  listener: (progress: RouteProgress) => void
): Subscription {
  return emitter.addListener('onRouteProgressChange', (event: unknown) => {
    const payload = unwrapNativeEventPayload<RouteProgress>(event)
    if (payload) {
      listener(payload)
    }
  })
}

export function addCameraFollowingStateChangeListener(
  listener: (state: CameraFollowingState) => void
): Subscription {
  return emitter.addListener('onCameraFollowingStateChange', (event: unknown) => {
    const payload = unwrapNativeEventPayload<CameraFollowingState>(event)
    if (payload) {
      listener(payload)
    }
  })
}

/**
 * Subscribe to aggregated journey data (location + progress + instruction) for custom UI.
 */
export function addJourneyDataChangeListener(listener: (data: JourneyData) => void): Subscription {
  return emitter.addListener('onJourneyDataChange', (event: unknown) => {
    const payload = unwrapNativeEventPayload<JourneyData>(event)
    if (payload) {
      listener(payload)
    }
  })
}

export function addRouteChangeListener(listener: (event: RouteChangeEvent) => void): Subscription {
  return emitter.addListener('onRouteChange', (event: unknown) => {
    const payload = unwrapNativeEventPayload<RouteChangeEvent>(event)
    if (payload) {
      listener(payload)
    }
  })
}

/**
 * Subscribe to arrival events.
 */
export function addArriveListener(listener: (point: ArrivalEvent) => void): Subscription {
  return emitter.addListener('onArrive', (event: unknown) => {
    const payload = unwrapNativeEventPayload<ArrivalEvent>(event)
    if (payload) {
      listener(payload)
    }
  })
}

/**
 * Subscribe to destination preview events.
 */
export function addDestinationPreviewListener(
  listener: (event: DestinationPreviewEvent) => void
): Subscription {
  return emitter.addListener('onDestinationPreview', (event: unknown) => {
    const payload = unwrapNativeEventPayload<DestinationPreviewEvent>(event)
    if (payload) {
      listener(payload)
    }
  })
}

/**
 * Subscribe to destination changed events.
 */
export function addDestinationChangedListener(
  listener: (event: DestinationChangedEvent) => void
): Subscription {
  return emitter.addListener('onDestinationChanged', (event: unknown) => {
    const payload = unwrapNativeEventPayload<DestinationChangedEvent>(event)
    if (payload) {
      listener(payload)
    }
  })
}

/**
 * Subscribe to cancellation events.
 */
export function addCancelNavigationListener(listener: () => void): Subscription {
  return emitter.addListener('onCancelNavigation', () => {
    listener()
  })
}

/**
 * Subscribe to native errors (token issues, route fetch failures, permission failures, etc.).
 */
export function addErrorListener(listener: (error: NavigationError) => void): Subscription {
  return emitter.addListener('onError', (event: unknown) => {
    const payload = unwrapNativeEventPayload<NavigationError>(event)
    if (payload) {
      listener(payload)
    }
  })
}

/**
 * Subscribe to banner instruction updates.
 */
export function addBannerInstructionListener(
  listener: (instruction: BannerInstruction) => void
): Subscription {
  return emitter.addListener('onBannerInstruction', (event: unknown) => {
    const payload = unwrapNativeEventPayload<BannerInstruction>(event)
    if (payload) {
      listener(payload)
    }
  })
}

/**
 * Subscribe to native bottom-sheet action button presses.
 */
export function addBottomSheetActionPressListener(
  listener: (event: BottomSheetActionEvent) => void
): Subscription {
  return emitter.addListener('onBottomSheetActionPress', (event: unknown) => {
    const payload = unwrapNativeEventPayload<BottomSheetActionEvent>(event)
    if (payload) {
      listener(payload)
    }
  })
}

export function MapboxNavigationFloatingButton({
  children,
  onPress,
  disabled,
  accessibilityLabel,
  style,
  testID,
}: MapboxNavigationFloatingButtonProps) {
  const content =
    typeof children === 'string' || typeof children === 'number' ? (
      <Text style={styles.defaultFloatingButtonLabel}>{children}</Text>
    ) : (
      children
    )

  return (
    <Pressable
      accessibilityRole='button'
      accessibilityLabel={accessibilityLabel}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.defaultFloatingButton,
        pressed && !disabled ? styles.defaultFloatingButtonPressed : null,
        disabled ? styles.defaultFloatingButtonDisabled : null,
        style,
      ]}
      testID={testID}
    >
      {content}
    </Pressable>
  )
}

export function MapboxNavigationFloatingButtonsStack({
  children,
  style,
}: MapboxNavigationFloatingButtonsStackProps) {
  return <View style={[styles.defaultFloatingButtonsStack, style]}>{children}</View>
}

/**
 * Embedded native navigation component.
 *
 * Set `enabled={true}` to start embedded navigation. Default is disabled to avoid accidental session conflicts.
 */
export function MapboxNavigationView(props: MapboxNavigationViewProps & ViewProps) {
  const warnedCustomSheetOnlyRef = useRef(false)
  const bottomSheet =
    props.bottomSheet && props.bottomSheet.enabled !== false
      ? {
          ...props.bottomSheet,
          mode: 'overlay' as const,
        }
      : props.bottomSheet
  if (
    props.bottomSheet?.enabled !== false &&
    props.bottomSheet &&
    props.bottomSheet.mode !== 'overlay' &&
    !warnedCustomSheetOnlyRef.current
  ) {
    warnedCustomSheetOnlyRef.current = true
    console.warn(
      "[react-native-mapbox-navigation] Embedded mode is custom-sheet-only. Forcing bottomSheet.mode='overlay'."
    )
  }
  const propsWithBottomSheet = bottomSheet === props.bottomSheet ? props : { ...props, bottomSheet }
  const useOverlayBottomSheet = !!bottomSheet?.enabled && bottomSheet?.mode === 'overlay'
  const hasCustomEndOfRouteFeedbackRenderer =
    typeof props.renderEndOfRouteFeedback === 'function' ||
    typeof props.endOfRouteFeedbackComponent === 'function'
  const useEndOfRouteFeedback =
    props.showsEndOfRouteFeedback === true ||
    (props.showsEndOfRouteFeedback !== false && hasCustomEndOfRouteFeedbackRenderer)
  const hideCustomFloatingButtonsOnArrival = props.hideFloatingButtonsOnArrival !== false
  const overlayLocationMinIntervalMs = Math.max(
    0,
    Math.min(
      bottomSheet?.overlayLocationUpdateIntervalMs ?? (Platform.OS === 'android' ? 350 : 300),
      3000
    )
  )
  const overlayProgressMinIntervalMs = Math.max(
    0,
    Math.min(
      bottomSheet?.overlayProgressUpdateIntervalMs ?? (Platform.OS === 'android' ? 300 : 300),
      3000
    )
  )
  const iosHiddenMode = Platform.OS === 'ios'
  const collapsedHeight = Math.max(56, Math.min(bottomSheet?.collapsedHeight ?? 112, 400))
  const expandedHeight = Math.max(
    collapsedHeight,
    Math.min(bottomSheet?.expandedHeight ?? 280, 700)
  )
  const collapsedBottomOffset = Math.max(0, Math.min(bottomSheet?.collapsedBottomOffset ?? 24, 80))
  const requestedInitialState =
    bottomSheet?.initialState === 'expanded' ||
    bottomSheet?.initialState === 'collapsed' ||
    bottomSheet?.initialState === 'hidden'
      ? bottomSheet.initialState
      : 'collapsed'
  const initialState =
    requestedInitialState === 'expanded' ? 'expanded' : iosHiddenMode ? 'hidden' : 'collapsed'
  const [sheetState, setSheetState] = useState<BottomSheetRenderContext['state']>(initialState)
  const [overlayBanner, setOverlayBanner] = useState<BannerInstruction | undefined>(undefined)
  const [overlayProgress, setOverlayProgress] = useState<RouteProgress | undefined>(undefined)
  const [overlayLocation, setOverlayLocation] = useState<LocationUpdate | undefined>(undefined)
  const [overlayMuted, setOverlayMuted] = useState(!!props.mute)
  const [overlayCameraMode, setOverlayCameraMode] = useState<'following' | 'overview' | undefined>(
    undefined
  )
  const [overlayArrival, setOverlayArrival] = useState<ArrivalEvent | undefined>(undefined)
  const [endOfRouteFeedbackVisible, setEndOfRouteFeedbackVisible] = useState(false)
  const overlayThrottleRef = useRef({
    locationAtMs: 0,
    progressAtMs: 0,
    bannerKey: '',
  })
  const navigationResetKey = [
    props.enabled === true ? 'enabled' : 'disabled',
    props.destination.latitude,
    props.destination.longitude,
    props.destination.name ?? '',
  ].join('|')
  useEffect(() => {
    if (!useOverlayBottomSheet) {
      return
    }
    const next =
      bottomSheet?.initialState === 'expanded' ? 'expanded' : iosHiddenMode ? 'hidden' : 'collapsed'
    setSheetState(next)
  }, [bottomSheet?.initialState, iosHiddenMode])

  useEffect(() => {
    setOverlayMuted(!!props.mute)
  }, [props.mute])

  useEffect(() => {
    setOverlayCameraMode(undefined)
  }, [props.cameraMode])

  useEffect(() => {
    setOverlayArrival(undefined)
    setEndOfRouteFeedbackVisible(false)
  }, [navigationResetKey])

  const nativeProps = useMemo(
    () => normalizeViewProps(propsWithBottomSheet),
    [propsWithBottomSheet]
  )
  const useOverlayTelemetry =
    useOverlayBottomSheet ||
    typeof props.renderFloatingButtons === 'function' ||
    typeof props.floatingButtonsComponent === 'function'
  const nativePropsWithOverlay = useMemo(() => {
    const onArrive = (event: unknown) => {
      const payload = unwrapNativeEventPayload<ArrivalEvent>(event)
      if (payload) {
        setOverlayArrival(payload)
        if (useEndOfRouteFeedback) {
          setEndOfRouteFeedbackVisible(true)
        }
      }
      nativeProps.onArrive?.(event as any)
    }
    const onCancelNavigation = () => {
      setOverlayArrival(undefined)
      setEndOfRouteFeedbackVisible(false)
      nativeProps.onCancelNavigation?.()
    }

    if (!useOverlayTelemetry) {
      return {
        ...nativeProps,
        onArrive,
        onCancelNavigation,
      }
    }

    const onLocationChange = (event: unknown) => {
      const payload = unwrapNativeEventPayload<LocationUpdate>(event)
      if (payload) {
        const now = Date.now()
        if (now - overlayThrottleRef.current.locationAtMs >= overlayLocationMinIntervalMs) {
          overlayThrottleRef.current.locationAtMs = now
          setOverlayLocation(payload)
        }
      }
      nativeProps.onLocationChange?.(event as any)
    }
    const onRouteProgressChange = (event: unknown) => {
      const payload = unwrapNativeEventPayload<RouteProgress>(event)
      if (payload) {
        const now = Date.now()
        if (now - overlayThrottleRef.current.progressAtMs >= overlayProgressMinIntervalMs) {
          overlayThrottleRef.current.progressAtMs = now
          setOverlayProgress(payload)
        }
      }
      nativeProps.onRouteProgressChange?.(event as any)
    }
    const onBannerInstruction = (event: unknown) => {
      const payload = unwrapNativeEventPayload<BannerInstruction>(event)
      if (payload) {
        const bannerKey = [
          payload.primaryText ?? '',
          payload.secondaryText ?? '',
          Math.round(payload.stepDistanceRemaining ?? -1),
        ].join('|')
        if (bannerKey !== overlayThrottleRef.current.bannerKey) {
          overlayThrottleRef.current.bannerKey = bannerKey
          setOverlayBanner(payload)
        }
      }
      nativeProps.onBannerInstruction?.(event as any)
    }
    return {
      ...nativeProps,
      cameraMode: overlayCameraMode ?? nativeProps.cameraMode,
      onLocationChange,
      onRouteProgressChange,
      onBannerInstruction,
      onArrive,
      onCancelNavigation,
    }
  }, [
    nativeProps,
    overlayCameraMode,
    useOverlayTelemetry,
    overlayLocationMinIntervalMs,
    overlayProgressMinIntervalMs,
    useEndOfRouteFeedback,
  ])

  const emitOverlayAction = (actionId: string, source: 'builtin' | 'custom' = 'custom') => {
    props.onOverlayBottomSheetActionPress?.({ actionId, source })
  }

  const showOverlayBottomSheet = (next: 'collapsed' | 'expanded' = 'collapsed') => {
    if (!useOverlayBottomSheet) {
      return
    }
    setSheetState(next === 'expanded' ? 'expanded' : iosHiddenMode ? 'expanded' : 'collapsed')
  }

  const hideOverlayBottomSheet = () => {
    if (!useOverlayBottomSheet) {
      return
    }
    setSheetState(iosHiddenMode ? 'hidden' : 'collapsed')
  }

  const expandOverlayBottomSheet = () => {
    if (!useOverlayBottomSheet) {
      return
    }
    setSheetState('expanded')
  }

  const collapseOverlayBottomSheet = () => {
    if (!useOverlayBottomSheet) {
      return
    }
    setSheetState(iosHiddenMode ? 'hidden' : 'collapsed')
  }

  const toggleOverlayBottomSheet = () => {
    if (!useOverlayBottomSheet) {
      return
    }
    setSheetState((value) =>
      value === 'expanded' ? (iosHiddenMode ? 'hidden' : 'collapsed') : 'expanded'
    )
  }

  const floatingButtonsContext: FloatingButtonsRenderContext = {
    show: showOverlayBottomSheet,
    hide: hideOverlayBottomSheet,
    expand: expandOverlayBottomSheet,
    collapse: collapseOverlayBottomSheet,
    toggle: toggleOverlayBottomSheet,
    bannerInstruction: overlayBanner,
    routeProgress: overlayProgress,
    location: overlayLocation,
    stopNavigation,
    emitAction: (actionId: string) => emitOverlayAction(actionId, 'custom'),
  }
  const endOfRouteFeedbackContext: EndOfRouteFeedbackRenderContext = {
    arrival: overlayArrival,
    dismiss: () => {
      setEndOfRouteFeedbackVisible(false)
    },
    submitRating: (rating: number) => {
      if (!Number.isFinite(rating)) {
        return
      }
      const normalizedRating = Math.max(1, Math.min(5, Math.round(rating)))
      const payload: EndOfRouteFeedbackEvent = {
        rating: normalizedRating,
        arrival: overlayArrival,
      }
      props.onEndOfRouteFeedbackSubmit?.(payload)
      setEndOfRouteFeedbackVisible(false)
    },
    stopNavigation,
  }

  const renderEndOfRouteFeedback = () => {
    if (!useEndOfRouteFeedback || !endOfRouteFeedbackVisible) {
      return null
    }

    const FeedbackComponent = props.endOfRouteFeedbackComponent
    const componentContent = FeedbackComponent ? (
      <FeedbackComponent {...endOfRouteFeedbackContext} />
    ) : null
    const renderedContent = normalizeOverlayNode(
      props.renderEndOfRouteFeedback?.(endOfRouteFeedbackContext)
    )
    const content = renderedContent ?? normalizeOverlayNode(componentContent) ?? (
      <View style={styles.endOfRouteFeedbackCard}>
        <Text style={styles.endOfRouteFeedbackTitle}>Rate This Trip</Text>
        <Text style={styles.endOfRouteFeedbackSubtitle}>
          {overlayArrival?.name
            ? `You arrived at ${overlayArrival.name}.`
            : 'You reached your destination.'}
        </Text>
        <View style={styles.endOfRouteFeedbackRatingRow}>
          {[1, 2, 3, 4, 5].map((rating) => (
            <Pressable
              accessibilityRole='button'
              accessibilityLabel={`Rate trip ${rating} out of 5`}
              key={`end-of-route-rating-${rating}`}
              onPress={() => {
                endOfRouteFeedbackContext.submitRating(rating)
              }}
              style={styles.endOfRouteFeedbackRatingButton}
            >
              <Text style={styles.endOfRouteFeedbackRatingLabel}>{rating}</Text>
            </Pressable>
          ))}
        </View>
        <Pressable
          accessibilityRole='button'
          onPress={endOfRouteFeedbackContext.dismiss}
          style={styles.endOfRouteFeedbackDismissButton}
        >
          <Text style={styles.endOfRouteFeedbackDismissLabel}>Not Now</Text>
        </Pressable>
      </View>
    )

    return (
      <View pointerEvents='box-none' style={styles.endOfRouteFeedbackRoot}>
        <Pressable
          accessibilityRole='button'
          onPress={endOfRouteFeedbackContext.dismiss}
          style={styles.endOfRouteFeedbackBackdrop}
        />
        <View pointerEvents='box-none' style={styles.endOfRouteFeedbackWrap}>
          {content}
        </View>
      </View>
    )
  }

  const renderFloatingButtons = () => {
    if (hideCustomFloatingButtonsOnArrival && overlayArrival) {
      return null
    }

    const FloatingButtonsComponent = props.floatingButtonsComponent
    const componentButtonsNode = FloatingButtonsComponent ? (
      <FloatingButtonsComponent {...floatingButtonsContext} />
    ) : null
    const renderedButtonsNode = normalizeOverlayNode(
      props.renderFloatingButtons?.(floatingButtonsContext)
    )
    const componentButtons = normalizeOverlayNode(componentButtonsNode)
    const staticButtons = normalizeOverlayNode(props.floatingButtons)
    const contentParts = [renderedButtonsNode, componentButtons, staticButtons].filter(Boolean)
    if (contentParts.length === 0) {
      return null
    }
    const content =
      contentParts.length === 1 ? (
        contentParts[0]
      ) : (
        <View style={styles.defaultFloatingButtonsStack}>
          {contentParts.map((node, index) => (
            <Fragment key={`floating-buttons-part-${index}`}>{node}</Fragment>
          ))}
        </View>
      )

    const nativeBottomUiVisible =
      !useOverlayBottomSheet &&
      (Platform.OS === 'android'
        ? nativeProps.showsWayNameLabel !== false
        : nativeProps.showsWayNameLabel !== false ||
          nativeProps.showsTripProgress !== false ||
          nativeProps.showsActionButtons !== false)
    const defaultBottomInset = nativeBottomUiVisible ? (Platform.OS === 'ios' ? 136 : 104) : 24
    const floatingButtonsBottomInset = useOverlayBottomSheet
      ? sheetState === 'expanded'
        ? expandedHeight + 16
        : sheetState === 'collapsed'
          ? Math.max(defaultBottomInset, collapsedHeight - collapsedBottomOffset + 16)
          : defaultBottomInset
      : defaultBottomInset
    const floatingButtonsAnchorStyle = {
      bottom: floatingButtonsBottomInset,
    }

    return (
      <View pointerEvents='box-none' style={styles.floatingButtonsRoot}>
        <View
          pointerEvents='box-none'
          style={[
            styles.floatingButtonsContainer,
            floatingButtonsAnchorStyle,
            props.floatingButtonsContainerStyle,
          ]}
        >
          {content}
        </View>
      </View>
    )
  }

  const renderOverlaySheet = () => {
    if (!useOverlayBottomSheet) {
      return null
    }

    const runBuiltInQuickAction = async (actionId: string) => {
      switch (actionId) {
        case 'overview':
          setOverlayCameraMode('overview')
          emitOverlayAction(actionId, 'builtin')
          break
        case 'recenter':
          setOverlayCameraMode('following')
          emitOverlayAction(actionId, 'builtin')
          break
        case 'mute':
          await setMuted(true)
          setOverlayMuted(true)
          emitOverlayAction(actionId, 'builtin')
          break
        case 'unmute':
          await setMuted(false)
          setOverlayMuted(false)
          emitOverlayAction(actionId, 'builtin')
          break
        case 'toggleMute': {
          const nextMuted = !overlayMuted
          await setMuted(nextMuted)
          setOverlayMuted(nextMuted)
          emitOverlayAction(actionId, 'builtin')
          break
        }
        case 'stop':
          await stopNavigation()
          emitOverlayAction(actionId, 'builtin')
          break
        default:
          break
      }
    }

    const context: BottomSheetRenderContext = {
      state: sheetState,
      hidden: sheetState === 'hidden',
      expanded: sheetState === 'expanded',
      ...floatingButtonsContext,
    }

    const BottomSheetComponent = props.bottomSheetComponent
    const componentSheet = BottomSheetComponent ? <BottomSheetComponent {...context} /> : null
    const customSheet = normalizeOverlayNode(props.renderBottomSheet?.(context) ?? componentSheet)
    const staticSheet = normalizeOverlayNode(props.bottomSheetContent)
    const builtInQuickActions: {
      id: string
      actionId: string
      label: string
      variant: 'primary' | 'secondary' | 'ghost'
    }[] = []
    ;(bottomSheet?.builtInQuickActions ?? []).forEach((actionId) => {
      if (actionId === 'overview') {
        builtInQuickActions.push({
          id: '__builtin_overview',
          actionId,
          label: 'Overview',
          variant: 'secondary',
        })
      } else if (actionId === 'recenter') {
        builtInQuickActions.push({
          id: '__builtin_recenter',
          actionId,
          label: 'Recenter',
          variant: 'secondary',
        })
      } else if (actionId === 'mute') {
        builtInQuickActions.push({
          id: '__builtin_mute',
          actionId,
          label: 'Mute',
          variant: 'ghost',
        })
      } else if (actionId === 'unmute') {
        builtInQuickActions.push({
          id: '__builtin_unmute',
          actionId,
          label: 'Unmute',
          variant: 'ghost',
        })
      } else if (actionId === 'toggleMute') {
        builtInQuickActions.push({
          id: '__builtin_toggle_mute',
          actionId,
          label: overlayMuted ? 'Unmute' : 'Mute',
          variant: 'ghost',
        })
      } else if (actionId === 'stop') {
        builtInQuickActions.push({
          id: '__builtin_stop',
          actionId,
          label: 'Stop',
          variant: 'primary',
        })
      }
    })
    const allQuickActions: {
      id: string
      actionId: string
      label: string
      variant: 'primary' | 'secondary' | 'ghost'
    }[] = [...builtInQuickActions]
    const etaText = formatEta(overlayProgress?.durationRemaining)
    const durationText =
      overlayProgress?.durationRemaining != null
        ? formatDuration(overlayProgress.durationRemaining)
        : undefined
    const showCurrentStreet = bottomSheet?.showCurrentStreet !== false
    const showRemainingDistance = bottomSheet?.showRemainingDistance !== false
    const showRemainingDuration = bottomSheet?.showRemainingDuration !== false
    const showETA = bottomSheet?.showETA !== false
    const showCompletionPercent = bottomSheet?.showCompletionPercent !== false
    const tripPrimaryParts: string[] = []
    if (overlayProgress && showRemainingDistance) {
      tripPrimaryParts.push(`${Math.round(overlayProgress.distanceRemaining)} m`)
    }
    if (overlayProgress && showRemainingDuration && durationText) {
      tripPrimaryParts.push(durationText)
    }
    if (overlayProgress && showETA && etaText) {
      tripPrimaryParts.push(etaText)
    }
    const tripSecondaryParts: string[] = []
    if (showCurrentStreet && overlayBanner?.secondaryText) {
      tripSecondaryParts.push(overlayBanner.secondaryText)
    }
    if (overlayProgress && showCompletionPercent) {
      tripSecondaryParts.push(
        `${Math.round((overlayProgress.fractionTraveled || 0) * 100)}% completed`
      )
    }
    const resolvedColorMode =
      bottomSheet?.colorMode ??
      (props.uiTheme === 'dark' || props.uiTheme === 'night' ? 'dark' : 'light')
    const isDark = resolvedColorMode === 'dark'
    const sheetBackgroundColor = isDark ? '#202020' : '#ffffff'
    const sheetCornerRadius = 16
    const handleColor = isDark ? 'rgba(255,255,255,0.35)' : 'rgba(0,0,0,0.3)'
    const primaryTextColor = isDark ? '#ffffff' : '#0f172a'
    const secondaryTextColor = isDark ? 'rgba(255,255,255,0.8)' : 'rgba(15,23,42,0.8)'
    const labelTextColor = isDark ? 'rgba(255,255,255,0.72)' : 'rgba(15,23,42,0.65)'
    const quickActionBorderColor = isDark ? 'rgba(255,255,255,0.35)' : 'rgba(15,23,42,0.25)'
    const quickPrimaryBackground = '#2563eb'
    const quickSecondaryBackground = isDark ? '#1d4ed8' : '#1e40af'
    const quickGhostText = isDark ? 'rgba(255,255,255,0.92)' : '#0f172a'
    const quickPrimaryText = '#ffffff'
    const quickSecondaryText = '#ffffff'
    const defaultCardColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(15,23,42,0.08)'
    const defaultSheet = (
      <View style={styles.defaultSheet}>
        {nativeProps.showsManeuverView !== false ? (
          <View style={[styles.defaultCard, { backgroundColor: defaultCardColor }]}>
            <Text
              style={[
                styles.defaultLabel,
                {
                  color: labelTextColor,
                  fontSize: 11,
                  fontWeight: '500',
                },
              ]}
            >
              {bottomSheet?.defaultManeuverTitle ?? 'Maneuver'}
            </Text>
            <Text
              style={[
                styles.defaultPrimary,
                {
                  color: primaryTextColor,
                  fontSize: 14,
                  fontWeight: '700',
                },
              ]}
              numberOfLines={2}
            >
              {overlayBanner?.primaryText ?? 'Waiting for route instructions...'}
            </Text>
            {overlayBanner?.secondaryText ? (
              <Text
                style={[
                  styles.defaultSecondary,
                  {
                    color: secondaryTextColor,
                    fontSize: 12,
                    fontWeight: '500',
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
          <View style={[styles.defaultCard, { backgroundColor: defaultCardColor }]}>
            <Text
              style={[
                styles.defaultLabel,
                {
                  color: labelTextColor,
                  fontSize: 11,
                  fontWeight: '500',
                },
              ]}
            >
              {bottomSheet?.defaultTripProgressTitle ?? 'Trip Progress'}
            </Text>
            <Text
              style={[
                styles.defaultPrimary,
                {
                  color: primaryTextColor,
                  fontSize: 14,
                  fontWeight: '700',
                },
              ]}
            >
              {overlayProgress
                ? tripPrimaryParts.length > 0
                  ? tripPrimaryParts.join(' • ')
                  : 'Progress available'
                : 'Waiting for progress...'}
            </Text>
            <Text
              style={[
                styles.defaultSecondary,
                {
                  color: secondaryTextColor,
                  fontSize: 12,
                  fontWeight: '500',
                },
              ]}
            >
              {overlayProgress
                ? tripSecondaryParts.length > 0
                  ? tripSecondaryParts.join(' • ')
                  : 'On route'
                : overlayLocation
                  ? `${overlayLocation.latitude.toFixed(5)}, ${overlayLocation.longitude.toFixed(5)}`
                  : 'Location not available'}
            </Text>
          </View>
        ) : null}
        {(allQuickActions.length ?? 0) > 0 ? (
          <View style={styles.quickActionsRow}>
            {allQuickActions.map((action) => (
              <Pressable
                key={action?.id}
                onPress={() => {
                  if (action?.id.startsWith('__builtin_')) {
                    runBuiltInQuickAction(action?.actionId).catch(() => {
                      emitOverlayAction(`error:${action?.actionId}`, 'builtin')
                    })
                  } else {
                    emitOverlayAction(action?.actionId, 'custom')
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
                  action?.variant === 'secondary' && [
                    styles.quickActionButtonSecondary,
                    { backgroundColor: quickSecondaryBackground },
                  ],
                  action?.variant === 'ghost' && [
                    styles.quickActionButtonGhost,
                    { backgroundColor: 'transparent' },
                  ],
                ]}
              >
                <Text
                  style={[
                    styles.quickActionLabel,
                    {
                      fontSize: 12,
                      fontWeight: '700',
                      color: quickPrimaryText,
                    },
                    action?.variant === 'secondary' && {
                      color: quickSecondaryText,
                    },
                    action?.variant === 'ghost' && [
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
    )
    const content =
      customSheet ??
      staticSheet ??
      (bottomSheet?.showDefaultContent === false ? null : defaultSheet)
    const currentHeight =
      sheetState === 'hidden' ? 0 : sheetState === 'collapsed' ? collapsedHeight : expandedHeight
    const canToggle = bottomSheet?.enableTapToToggle !== false
    const showHandle = bottomSheet?.showHandle !== false

    const contentHorizontalPadding = 14
    const contentBottomPadding = 14
    const contentTopSpacing = 0

    const iosHiddenTouchHeight = Math.max(
      40,
      Math.min(bottomSheet?.revealGestureHotzoneHeight ?? 120, 220)
    )
    const iosHiddenHotzoneBottom = 36

    const backdropPress = () => {
      hideOverlayBottomSheet()
    }

    const backdropVisible = sheetState === 'expanded'
    const hiddenGrabberResponder = PanResponder.create({
      onStartShouldSetPanResponder: () => false,
      onStartShouldSetPanResponderCapture: () => false,
      onMoveShouldSetPanResponder: (_evt, gesture) =>
        Math.abs(gesture.dy) > Math.abs(gesture.dx) && Math.abs(gesture.dy) > 6,
      onMoveShouldSetPanResponderCapture: (_evt, gesture) =>
        Math.abs(gesture.dy) > Math.abs(gesture.dx) && Math.abs(gesture.dy) > 6,
      onPanResponderTerminationRequest: () => false,
      onPanResponderRelease: (_evt, gesture) => {
        if (gesture.dy < -8 || gesture.vy < -0.3) {
          expandOverlayBottomSheet()
        }
      },
    })
    const sheetPanResponder = PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: (_evt, gesture) =>
        Math.abs(gesture.dy) > Math.abs(gesture.dx) && Math.abs(gesture.dy) > 4,
      onPanResponderRelease: (_evt, gesture) => {
        if (gesture.dy < -10 || gesture.vy < -0.35) {
          expandOverlayBottomSheet()
          return
        }
        if (gesture.dy > 10 || gesture.vy > 0.35) {
          collapseOverlayBottomSheet()
        }
      },
    })

    return (
      <View pointerEvents='box-none' style={styles.overlayRoot}>
        {iosHiddenMode && sheetState === 'hidden' ? (
          <View
            pointerEvents='auto'
            style={[
              styles.iosHiddenHotzone,
              { height: iosHiddenTouchHeight, bottom: iosHiddenHotzoneBottom },
            ]}
            {...hiddenGrabberResponder.panHandlers}
          />
        ) : null}
        {sheetState === 'hidden' ? (
          <View pointerEvents='box-none' style={styles.hiddenGrabberWrap}>
            <View
              pointerEvents='auto'
              style={styles.hiddenGrabberTouchArea}
              {...hiddenGrabberResponder.panHandlers}
            >
              <Pressable
                accessibilityRole='button'
                onPress={expandOverlayBottomSheet}
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
                sheetState === 'collapsed'
                  ? -collapsedBottomOffset
                  : sheetState === 'hidden'
                    ? -(collapsedHeight + 80)
                    : 0,
              backgroundColor: sheetBackgroundColor,
              borderTopLeftRadius: sheetCornerRadius,
              borderTopRightRadius: sheetCornerRadius,
            },
          ]}
          {...sheetPanResponder.panHandlers}
          pointerEvents='auto'
        >
          {showHandle ? (
            <Pressable
              accessibilityRole='button'
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
    )
  }

  if (
    !props.children &&
    !useOverlayBottomSheet &&
    !props.floatingButtons &&
    !props.renderFloatingButtons &&
    !props.floatingButtonsComponent &&
    !useEndOfRouteFeedback
  ) {
    return <MapboxNavigationNativeView {...nativePropsWithOverlay} />
  }

  return (
    <View style={props.style}>
      <MapboxNavigationNativeView {...nativePropsWithOverlay} style={StyleSheet.absoluteFill} />
      {props.children ? (
        <View pointerEvents='box-none' style={StyleSheet.absoluteFill}>
          {props.children}
        </View>
      ) : null}
      {renderFloatingButtons()}
      {renderOverlaySheet()}
      {renderEndOfRouteFeedback()}
    </View>
  )
}

const styles = StyleSheet.create({
  floatingButtonsRoot: {
    ...StyleSheet.absoluteFillObject,
  },
  floatingButtonsContainer: {
    position: 'absolute',
    bottom: 24,
    right: 12,
    maxWidth: '32%',
    alignItems: 'flex-end',
  },
  defaultFloatingButtonsStack: {
    gap: 10,
    alignItems: 'flex-end',
  },
  defaultFloatingButton: {
    width: 56,
    height: 56,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(15,23,42,0.96)',
    shadowColor: '#020617',
    shadowOpacity: 0.26,
    shadowRadius: 10,
    shadowOffset: {
      width: 0,
      height: 6,
    },
    elevation: 5,
  },
  defaultFloatingButtonPressed: {
    opacity: 0.9,
  },
  defaultFloatingButtonDisabled: {
    opacity: 0.45,
  },
  defaultFloatingButtonLabel: {
    color: '#f8fafc',
    fontSize: 12,
    fontWeight: '800',
    textAlign: 'center',
  },
  endOfRouteFeedbackRoot: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    paddingHorizontal: 20,
    zIndex: 3,
  },
  endOfRouteFeedbackBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(2,6,23,0.62)',
  },
  endOfRouteFeedbackWrap: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  endOfRouteFeedbackCard: {
    width: '100%',
    maxWidth: 360,
    borderRadius: 22,
    backgroundColor: 'rgba(15,23,42,0.96)',
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.2)',
    paddingHorizontal: 18,
    paddingVertical: 18,
    gap: 14,
  },
  endOfRouteFeedbackTitle: {
    color: '#f8fafc',
    fontSize: 18,
    fontWeight: '800',
    textAlign: 'center',
  },
  endOfRouteFeedbackSubtitle: {
    color: 'rgba(226,232,240,0.84)',
    fontSize: 13,
    lineHeight: 19,
    textAlign: 'center',
  },
  endOfRouteFeedbackRatingRow: {
    flexDirection: 'row',
    gap: 8,
    justifyContent: 'center',
  },
  endOfRouteFeedbackRatingButton: {
    minWidth: 44,
    borderRadius: 14,
    backgroundColor: '#1d4ed8',
    paddingHorizontal: 10,
    paddingVertical: 12,
    alignItems: 'center',
  },
  endOfRouteFeedbackRatingLabel: {
    color: '#ffffff',
    fontSize: 14,
    fontWeight: '800',
  },
  endOfRouteFeedbackDismissButton: {
    alignSelf: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  endOfRouteFeedbackDismissLabel: {
    color: 'rgba(191,219,254,0.95)',
    fontSize: 13,
    fontWeight: '700',
  },
  overlayRoot: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-end',
  },
  overlayBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
  iosHiddenHotzone: {
    position: 'absolute',
    left: 0,
    right: 0,
    zIndex: 1,
    backgroundColor: 'transparent',
  },
  hiddenGrabberWrap: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 10,
    alignItems: 'center',
    zIndex: 2,
  },
  hiddenGrabberTouchArea: {
    width: 160,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  hiddenGrabber: {
    width: 84,
    height: 8,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.35)',
  },
  sheetContainer: {
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    backgroundColor: 'rgba(12, 18, 32, 0.94)',
    overflow: 'hidden',
  },
  sheetHandle: {
    alignSelf: 'center',
    width: 42,
    height: 5,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.35)',
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
    backgroundColor: 'rgba(255,255,255,0.08)',
    paddingHorizontal: 10,
    paddingVertical: 8,
    gap: 2,
  },
  defaultLabel: {
    color: 'rgba(255,255,255,0.72)',
    fontSize: 11,
    fontWeight: '600',
  },
  defaultPrimary: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '700',
  },
  defaultSecondary: {
    color: 'rgba(255,255,255,0.8)',
    fontSize: 12,
    fontWeight: '500',
  },
  quickActionsRow: {
    flexDirection: 'row',
    gap: 8,
    flexWrap: 'wrap',
  },
  quickActionButton: {
    backgroundColor: '#2563eb',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  quickActionButtonSecondary: {
    backgroundColor: '#1d4ed8',
  },
  quickActionButtonGhost: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.35)',
  },
  quickActionLabel: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
  quickActionLabelGhost: {
    color: 'rgba(255,255,255,0.92)',
  },
})

export * from './MapboxNavigation.types'

export default {
  setMuted,
  setVoiceVolume,
  setDistanceUnit,
  setLanguage,
  getNavigationSettings,
  stopNavigation,
  resumeCameraFollowing,
  addLocationChangeListener,
  addRouteProgressChangeListener,
  addCameraFollowingStateChangeListener,
  addJourneyDataChangeListener,
  addRouteChangeListener,
  addArriveListener,
  addDestinationPreviewListener,
  addDestinationChangedListener,
  addCancelNavigationListener,
  addErrorListener,
  addBannerInstructionListener,
  addBottomSheetActionPressListener,
  MapboxNavigationFloatingButton,
  MapboxNavigationFloatingButtonsStack,
  MapboxNavigationView,
}

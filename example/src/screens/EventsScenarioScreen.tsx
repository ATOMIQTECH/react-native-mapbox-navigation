import {
  addArriveListener,
  addBannerInstructionListener,
  addBottomSheetActionPressListener,
  addCameraFollowingStateChangeListener,
  addCancelNavigationListener,
  addDestinationChangedListener,
  addDestinationPreviewListener,
  addErrorListener,
  addJourneyDataChangeListener,
  addLocationChangeListener,
  addRouteChangeListener,
  addRouteProgressChangeListener,
  MapboxNavigationView,
} from '@atomiqlab/react-native-mapbox-navigation'
import { useEffect, useMemo, useState } from 'react'
import { Pressable, ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { ALT_DESTINATION } from '../constants/navigation'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

const EVENT_KEYS = [
  'location',
  'progress',
  'journey',
  'route',
  'camera',
  'banner',
  'arrive',
  'preview',
  'destination',
  'cancel',
  'error',
  'moduleBottomSheetAction',
  'overlayBottomSheetAction',
] as const

type EventKey = (typeof EVENT_KEYS)[number]

const initialCounts: Record<EventKey, number> = {
  location: 0,
  progress: 0,
  journey: 0,
  route: 0,
  camera: 0,
  banner: 0,
  arrive: 0,
  preview: 0,
  destination: 0,
  cancel: 0,
  error: 0,
  moduleBottomSheetAction: 0,
  overlayBottomSheetAction: 0,
}

function payloadToText(payload: unknown): string {
  if (payload == null) return 'null'
  try {
    return JSON.stringify(payload)
  } catch {
    return String(payload)
  }
}

export default function EventsScenarioScreen() {
  const {
    hasLocationPermission,
    permissionStatus,
    resolvedStartOrigin,
    requestLocationAccess,
  } = useNavigationLocation(true)
  const [counts, setCounts] = useState<Record<EventKey, number>>(initialCounts)
  const [lastPayloads, setLastPayloads] = useState<Record<EventKey, string>>({
    location: '-',
    progress: '-',
    journey: '-',
    route: '-',
    camera: '-',
    banner: '-',
    arrive: '-',
    preview: '-',
    destination: '-',
    cancel: '-',
    error: '-',
    moduleBottomSheetAction: '-',
    overlayBottomSheetAction: '-',
  })

  const totalCount = useMemo(() => {
    return Object.values(counts).reduce((acc, value) => acc + value, 0)
  }, [counts])

  const captureEvent = (key: EventKey, payload?: unknown) => {
    setCounts((current) => ({
      ...current,
      [key]: current[key] + 1,
    }))
    if (payload !== undefined) {
      setLastPayloads((current) => ({
        ...current,
        [key]: payloadToText(payload),
      }))
    }
  }

  useEffect(() => {
    const subscriptions = [
      addLocationChangeListener((payload) => captureEvent('location', payload)),
      addRouteProgressChangeListener((payload) => captureEvent('progress', payload)),
      addJourneyDataChangeListener((payload) => captureEvent('journey', payload)),
      addRouteChangeListener((payload) => captureEvent('route', payload)),
      addCameraFollowingStateChangeListener((payload) => captureEvent('camera', payload)),
      addBannerInstructionListener((payload) => captureEvent('banner', payload)),
      addArriveListener((payload) => captureEvent('arrive', payload)),
      addDestinationPreviewListener((payload) => captureEvent('preview', payload)),
      addDestinationChangedListener((payload) => captureEvent('destination', payload)),
      addCancelNavigationListener(() => captureEvent('cancel')),
      addErrorListener((payload) => captureEvent('error', payload)),
      addBottomSheetActionPressListener((payload) => captureEvent('moduleBottomSheetAction', payload)),
    ]
    return () => {
      subscriptions.forEach((subscription) => {
        subscription.remove()
      })
    }
  }, [])

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <StatusBar barStyle='light-content' />
      <MapboxNavigationView
        enabled={hasLocationPermission}
        style={StyleSheet.absoluteFill}
        startOrigin={resolvedStartOrigin}
        destination={ALT_DESTINATION}
        shouldSimulateRoute
        bottomSheet={{
          enabled: true,
          mode: 'overlay',
          initialState: 'collapsed',
          builtInQuickActions: ['overview', 'recenter', 'toggleMute', 'stop'],
        }}
        onOverlayBottomSheetActionPress={(payload) => {
          captureEvent('overlayBottomSheetAction', payload)
        }}
      />

      <View pointerEvents='box-none' style={styles.overlayRoot}>
        <ScrollView contentContainerStyle={styles.panel}>
          <Text style={styles.panelTitle}>Listener Scenario</Text>
          <Text style={styles.panelText}>Total events: {totalCount}</Text>
          {EVENT_KEYS.map((key) => (
            <View key={key} style={styles.eventRow}>
              <Text style={styles.eventKey}>
                {key}: {counts[key]}
              </Text>
              <Text style={styles.eventPayload} numberOfLines={2}>
                {lastPayloads[key]}
              </Text>
            </View>
          ))}
          <Pressable
            onPress={() => {
              setCounts(initialCounts)
              setLastPayloads({
                location: '-',
                progress: '-',
                journey: '-',
                route: '-',
                camera: '-',
                banner: '-',
                arrive: '-',
                preview: '-',
                destination: '-',
                cancel: '-',
                error: '-',
                moduleBottomSheetAction: '-',
                overlayBottomSheetAction: '-',
              })
            }}
            style={styles.clearButton}
          >
            <Text style={styles.clearButtonLabel}>Clear Counters</Text>
          </Pressable>
        </ScrollView>
      </View>

      <LocationPermissionOverlay
        hasLocationPermission={hasLocationPermission}
        permissionStatus={permissionStatus}
        requestLocationAccess={requestLocationAccess}
      />
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#020617',
  },
  overlayRoot: {
    ...StyleSheet.absoluteFillObject,
    paddingHorizontal: 10,
    paddingTop: 10,
    paddingBottom: 12,
  },
  panel: {
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.24)',
    backgroundColor: 'rgba(2,6,23,0.86)',
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 7,
  },
  panelTitle: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '800',
  },
  panelText: {
    color: '#cbd5e1',
    fontSize: 12,
  },
  eventRow: {
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.2)',
    backgroundColor: 'rgba(15,23,42,0.8)',
    paddingHorizontal: 10,
    paddingVertical: 8,
    gap: 2,
  },
  eventKey: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
  eventPayload: {
    color: '#bfdbfe',
    fontSize: 11,
    lineHeight: 15,
  },
  clearButton: {
    alignSelf: 'flex-start',
    borderRadius: 999,
    backgroundColor: '#2563eb',
    paddingHorizontal: 14,
    paddingVertical: 8,
    marginTop: 4,
  },
  clearButtonLabel: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
})

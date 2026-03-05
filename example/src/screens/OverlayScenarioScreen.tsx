import {
  type BottomSheetRenderContext,
  type EndOfRouteFeedbackEvent,
  type FloatingButtonsRenderContext,
  type LocationUpdate,
  MapboxNavigationFloatingButton,
  MapboxNavigationFloatingButtonsStack,
  MapboxNavigationView,
} from '@atomiqlab/react-native-mapbox-navigation'
import { useEffect, useState } from 'react'
import { Pressable, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { PRIMARY_DESTINATION } from '../constants/navigation'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

export default function OverlayScenarioScreen() {
  const {
    hasLocationPermission,
    permissionStatus,
    location,
    resolvedStartOrigin,
    requestLocationAccess,
  } = useNavigationLocation(true)
  const [lastAction, setLastAction] = useState('none')
  const [tripRating, setTripRating] = useState<number | null>(null)
  const [lastError, setLastError] = useState<string | null>(null)
  const [supportSheetOpen, setSupportSheetOpen] = useState(false)
  const [capturedLocation, setCapturedLocation] = useState<LocationUpdate | undefined>(location)

  useEffect(() => {
    if (location) {
      setCapturedLocation(location)
    }
  }, [location])

  const FloatingButtons = ({ stopNavigation, emitAction }: FloatingButtonsRenderContext) => (
    <MapboxNavigationFloatingButtonsStack>
      <MapboxNavigationFloatingButton
        accessibilityLabel='Open support sheet'
        onPress={() => {
          setSupportSheetOpen(true)
          emitAction('open_support_sheet')
        }}
      >
        CHAT
      </MapboxNavigationFloatingButton>
      <MapboxNavigationFloatingButton
        accessibilityLabel='Expand overlay sheet'
        onPress={() => {
          emitAction('expand_overlay_sheet')
        }}
      >
        HELP
      </MapboxNavigationFloatingButton>
      <MapboxNavigationFloatingButton
        accessibilityLabel='Stop navigation'
        onPress={() => {
          void stopNavigation()
        }}
      >
        END
      </MapboxNavigationFloatingButton>
    </MapboxNavigationFloatingButtonsStack>
  )

  const BottomSheet = ({
    state,
    routeProgress,
    bannerInstruction,
    location: liveLocation,
  }: BottomSheetRenderContext) => (
    <View style={styles.sheetCard}>
      <Text style={styles.sheetTitle}>Overlay Sheet ({state})</Text>
      <Text style={styles.sheetText}>
        Maneuver: {bannerInstruction?.primaryText ?? 'Waiting for instruction...'}
      </Text>
      <Text style={styles.sheetText}>
        Distance left: {routeProgress ? `${Math.round(routeProgress.distanceRemaining)} m` : 'N/A'}
      </Text>
      <Text style={styles.sheetText}>
        Position:{' '}
        {liveLocation
          ? `${liveLocation.latitude.toFixed(5)}, ${liveLocation.longitude.toFixed(5)}`
          : 'N/A'}
      </Text>
    </View>
  )

  return (
    <SafeAreaView edges={['top']} style={styles.screen}>
      <StatusBar barStyle='light-content' />
      <MapboxNavigationView
        enabled={hasLocationPermission}
        style={StyleSheet.absoluteFill}
        startOrigin={resolvedStartOrigin}
        destination={PRIMARY_DESTINATION}
        shouldSimulateRoute
        mute
        showsEndOfRouteFeedback
        bottomSheet={{
          enabled: true,
          mode: 'overlay',
          initialState: 'collapsed',
          builtInQuickActions: ['overview', 'recenter', 'toggleMute', 'stop'],
          showDefaultContent: true,
          showCurrentStreet: true,
          showRemainingDistance: true,
          showRemainingDuration: true,
          showETA: true,
          showCompletionPercent: true,
        }}
        bottomSheetComponent={BottomSheet}
        nativeFloatingButtons={{
          showCameraModeButton: true,
          showCompassButton: false,
        }}
        floatingButtonsComponent={FloatingButtons}
        onLocationChange={(payload) => {
          setCapturedLocation(payload)
        }}
        onError={(error) => {
          setLastError(`${error.code}: ${error.message}`)
        }}
        onOverlayBottomSheetActionPress={(event) => {
          setLastAction(`${event.source}:${event.actionId}`)
        }}
        onEndOfRouteFeedbackSubmit={(event: EndOfRouteFeedbackEvent) => {
          setTripRating(event.rating)
          setLastAction(`feedback:${event.rating}`)
        }}
      />

      <View pointerEvents='none' style={styles.topStatus}>
        <Text style={styles.topStatusText}>Last action: {lastAction}</Text>
        <Text style={styles.topStatusText}>
          Last location:{' '}
          {capturedLocation
            ? `${capturedLocation.latitude.toFixed(4)}, ${capturedLocation.longitude.toFixed(4)}`
            : 'N/A'}
        </Text>
        {lastError ? <Text style={styles.errorText}>{lastError}</Text> : null}
      </View>

      {supportSheetOpen ? (
        <View pointerEvents='box-none' style={styles.supportOverlay}>
          <Pressable style={styles.supportBackdrop} onPress={() => setSupportSheetOpen(false)} />
          <View style={styles.supportSheet}>
            <Text style={styles.supportTitle}>App-Owned Support Sheet</Text>
            <Text style={styles.supportText}>
              This overlay proves package UI and app UI can coexist.
            </Text>
            <Pressable onPress={() => setSupportSheetOpen(false)} style={styles.supportButton}>
              <Text style={styles.supportButtonLabel}>Close</Text>
            </Pressable>
          </View>
        </View>
      ) : null}

      {tripRating != null ? (
        <View pointerEvents='box-none' style={styles.ratingToastWrap}>
          <View style={styles.ratingToast}>
            <Text style={styles.ratingToastLabel}>Rated trip {tripRating}/5</Text>
          </View>
        </View>
      ) : null}

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
  topStatus: {
    position: 'absolute',
    top: 10,
    left: 10,
    right: 10,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.24)',
    backgroundColor: 'rgba(2,6,23,0.86)',
    paddingHorizontal: 12,
    paddingVertical: 8,
    gap: 3,
  },
  topStatusText: {
    color: '#cbd5e1',
    fontSize: 12,
  },
  errorText: {
    color: '#fecaca',
    fontSize: 12,
    fontWeight: '700',
  },
  sheetCard: {
    borderRadius: 14,
    backgroundColor: 'rgba(15,23,42,0.94)',
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.2)',
    padding: 12,
    gap: 4,
  },
  sheetTitle: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '800',
  },
  sheetText: {
    color: '#dbeafe',
    fontSize: 12,
    lineHeight: 16,
  },
  supportOverlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-end',
  },
  supportBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(2,6,23,0.45)',
  },
  supportSheet: {
    margin: 14,
    borderRadius: 24,
    backgroundColor: '#ffffff',
    padding: 18,
    gap: 10,
  },
  supportTitle: {
    color: '#0f172a',
    fontSize: 18,
    fontWeight: '800',
  },
  supportText: {
    color: '#334155',
    fontSize: 14,
    lineHeight: 20,
  },
  supportButton: {
    alignSelf: 'flex-start',
    borderRadius: 999,
    backgroundColor: '#0f172a',
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  supportButtonLabel: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '700',
  },
  ratingToastWrap: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 26,
    alignItems: 'center',
  },
  ratingToast: {
    borderRadius: 999,
    backgroundColor: 'rgba(15,23,42,0.92)',
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.22)',
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  ratingToastLabel: {
    color: '#f8fafc',
    fontSize: 12,
    fontWeight: '700',
  },
})

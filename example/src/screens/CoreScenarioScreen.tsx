import {
  type BannerInstruction,
  type CameraFollowingState,
  type JourneyData,
  type LocationUpdate,
  MapboxNavigationView,
  type RouteProgress,
  resumeCameraFollowing,
  stopNavigation,
} from '@atomiqlab/react-native-mapbox-navigation'
import { useEffect, useState } from 'react'
import { Pressable, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { PRIMARY_DESTINATION, TEST_WAYPOINTS } from '../constants/navigation'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

export default function CoreScenarioScreen() {
  const {
    hasLocationPermission,
    permissionStatus,
    location,
    resolvedStartOrigin,
    locationError,
    requestLocationAccess,
  } = useNavigationLocation(true)
  const [navigationEnabled, setNavigationEnabled] = useState(true)
  const [lastLocation, setLastLocation] = useState<LocationUpdate | undefined>(location)
  const [lastProgress, setLastProgress] = useState<RouteProgress | undefined>(undefined)
  const [lastBanner, setLastBanner] = useState<BannerInstruction | undefined>(undefined)
  const [lastJourney, setLastJourney] = useState<JourneyData | undefined>(undefined)
  const [lastCameraState, setLastCameraState] = useState<CameraFollowingState | undefined>(
    undefined
  )
  const [lastRoutePoints, setLastRoutePoints] = useState(0)
  const [lastError, setLastError] = useState<string | null>(locationError)
  const [arrivalName, setArrivalName] = useState<string | null>(null)

  useEffect(() => {
    if (location) {
      setLastLocation(location)
    }
  }, [location])

  useEffect(() => {
    if (locationError) {
      setLastError(locationError)
    }
  }, [locationError])

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <StatusBar barStyle='light-content' />
      <MapboxNavigationView
        enabled={hasLocationPermission && navigationEnabled}
        style={StyleSheet.absoluteFill}
        startOrigin={resolvedStartOrigin}
        destination={PRIMARY_DESTINATION}
        waypoints={TEST_WAYPOINTS}
        shouldSimulateRoute
        showsTripProgress
        showsManeuverView
        showsActionButtons
        showsSpeedLimits
        showsWayNameLabel
        routeAlternatives
        onLocationChange={(payload) => {
          setLastLocation(payload)
        }}
        onRouteProgressChange={(payload) => {
          setLastProgress(payload)
        }}
        onBannerInstruction={(payload) => {
          setLastBanner(payload)
        }}
        onJourneyDataChange={(payload) => {
          setLastJourney(payload)
        }}
        onRouteChange={(event) => {
          setLastRoutePoints(event.coordinates.length)
        }}
        onCameraFollowingStateChange={(payload) => {
          setLastCameraState(payload)
        }}
        onDestinationPreview={() => {
          setLastError(null)
        }}
        onError={(error) => {
          setLastError(`${error.code}: ${error.message}`)
        }}
        onArrive={(payload) => {
          setArrivalName(payload.name ?? 'Destination')
        }}
        onCancelNavigation={() => {
          setArrivalName(null)
        }}
      />

      <View pointerEvents='box-none' style={styles.overlayRoot}>
        <View style={styles.panel}>
          <Text style={styles.panelTitle}>Core Scenario</Text>
          <Text style={styles.panelText}>
            Route pts: {lastRoutePoints} | Camera:{' '}
            {lastCameraState?.isCameraFollowing ? 'Following' : 'Overview'}
          </Text>
          <Text style={styles.panelText}>
            Banner: {lastBanner?.primaryText ?? 'Waiting for banner...'}
          </Text>
          <Text style={styles.panelText}>
            Progress:{' '}
            {lastProgress
              ? `${Math.round(lastProgress.distanceRemaining)}m • ${Math.round(
                  lastProgress.durationRemaining
                )}s`
              : 'Waiting...'}
          </Text>
          <Text style={styles.panelText}>
            Last lat/lng:{' '}
            {lastLocation
              ? `${lastLocation.latitude.toFixed(5)}, ${lastLocation.longitude.toFixed(5)}`
              : 'N/A'}
          </Text>
          <Text style={styles.panelText}>
            Journey completion: {lastJourney?.completionPercent ?? 0}% | Arrived:{' '}
            {arrivalName ?? 'No'}
          </Text>
          {lastError ? <Text style={styles.errorText}>{lastError}</Text> : null}
          <View style={styles.row}>
            <Pressable
              onPress={() => {
                setNavigationEnabled((value) => !value)
              }}
              style={styles.button}
            >
              <Text style={styles.buttonLabel}>{navigationEnabled ? 'Disable' : 'Enable'}</Text>
            </Pressable>
            <Pressable
              onPress={() => {
                void stopNavigation()
              }}
              style={styles.buttonSecondary}
            >
              <Text style={styles.buttonLabel}>Stop API</Text>
            </Pressable>
            <Pressable
              onPress={() => {
                void resumeCameraFollowing()
              }}
              style={styles.buttonSecondary}
            >
              <Text style={styles.buttonLabel}>Recenter API</Text>
            </Pressable>
          </View>
        </View>
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
    justifyContent: 'flex-start',
    paddingHorizontal: 12,
    paddingTop: 10,
  },
  panel: {
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.24)',
    backgroundColor: 'rgba(2,6,23,0.86)',
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 4,
  },
  panelTitle: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '800',
  },
  panelText: {
    color: '#cbd5e1',
    fontSize: 12,
    lineHeight: 17,
  },
  errorText: {
    color: '#fecaca',
    fontSize: 12,
    fontWeight: '700',
  },
  row: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 8,
  },
  button: {
    borderRadius: 999,
    backgroundColor: '#2563eb',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  buttonSecondary: {
    borderRadius: 999,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.36)',
    backgroundColor: 'rgba(15,23,42,0.9)',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  buttonLabel: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
})

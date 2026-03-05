import type { LocationUpdate, Waypoint } from '@atomiqlab/react-native-mapbox-navigation'
import * as Location from 'expo-location'
import { useCallback, useEffect, useMemo, useState } from 'react'

import { FALLBACK_START_ORIGIN } from '../constants/navigation'

export type LocationPermissionStatus = 'checking' | 'requesting' | 'granted' | 'denied' | 'blocked'

type UseNavigationLocationResult = {
  hasLocationPermission: boolean
  permissionStatus: LocationPermissionStatus
  location: LocationUpdate | undefined
  resolvedStartOrigin: Waypoint
  locationError: string | null
  requestLocationAccess: () => Promise<void>
  refreshLocation: () => Promise<void>
}

export function useNavigationLocation(autoRequest = true): UseNavigationLocationResult {
  const [hasLocationPermission, setHasLocationPermission] = useState(false)
  const [permissionStatus, setPermissionStatus] = useState<LocationPermissionStatus>('checking')
  const [location, setLocation] = useState<LocationUpdate | undefined>(undefined)
  const [locationError, setLocationError] = useState<string | null>(null)

  const refreshLocation = useCallback(async () => {
    try {
      const position = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      })
      setLocation({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracy: position.coords.accuracy ?? undefined,
        altitude: position.coords.altitude ?? undefined,
        bearing: position.coords.heading ?? undefined,
        speed: position.coords.speed ?? undefined,
      })
      setLocationError(null)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to capture location.'
      setLocationError(`LOCATION_CAPTURE_FAILED: ${message}`)
    }
  }, [])

  const requestLocationAccess = useCallback(async () => {
    const existingPermission = await Location.getForegroundPermissionsAsync()
    if (existingPermission.granted) {
      setHasLocationPermission(true)
      setPermissionStatus('granted')
      await refreshLocation()
      return
    }

    setPermissionStatus('requesting')
    const requestedPermission = await Location.requestForegroundPermissionsAsync()
    const granted = requestedPermission.granted
    const blocked = !granted && requestedPermission.canAskAgain === false

    setHasLocationPermission(granted)
    setPermissionStatus(granted ? 'granted' : blocked ? 'blocked' : 'denied')
    if (granted) {
      await refreshLocation()
    }
  }, [refreshLocation])

  useEffect(() => {
    if (!autoRequest) return
    void requestLocationAccess()
  }, [autoRequest, requestLocationAccess])

  const resolvedStartOrigin = useMemo<Waypoint>(() => {
    if (!location) {
      return FALLBACK_START_ORIGIN
    }
    return {
      latitude: location.latitude,
      longitude: location.longitude,
      name: 'Current Location',
    }
  }, [location])

  return {
    hasLocationPermission,
    permissionStatus,
    location,
    resolvedStartOrigin,
    locationError,
    requestLocationAccess,
    refreshLocation,
  }
}

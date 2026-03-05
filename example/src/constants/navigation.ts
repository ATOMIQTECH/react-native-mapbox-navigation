import type { Waypoint } from '@atomiqlab/react-native-mapbox-navigation'

export const FALLBACK_START_ORIGIN: Waypoint = {
  latitude: 37.7749,
  longitude: -122.4194,
  name: 'San Francisco',
}

export const PRIMARY_DESTINATION: Waypoint = {
  latitude: 37.7847,
  longitude: -122.4073,
  name: 'Union Square',
}

export const ALT_DESTINATION: Waypoint = {
  latitude: 37.7955,
  longitude: -122.3937,
  name: 'Ferry Building',
}

export const TEST_WAYPOINTS: Waypoint[] = [
  {
    latitude: 37.7812,
    longitude: -122.4121,
    name: 'Mid-Market',
  },
  {
    latitude: 37.7892,
    longitude: -122.4018,
    name: 'Downtown',
  },
]

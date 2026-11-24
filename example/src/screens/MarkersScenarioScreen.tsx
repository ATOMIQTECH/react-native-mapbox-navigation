import {
  MapboxNavigationView,
  type NavigationMarker,
  type Waypoint,
} from '@atomiqlab/react-native-mapbox-navigation'
import { StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

const START: Waypoint = { latitude: 37.7739, longitude: -122.46173, name: 'Start' }
const DESTINATION: Waypoint = { latitude: 37.77326, longitude: -122.46912, name: 'Finish' }

const outOfContract = (options: Record<string, string>) => options

const MARKERS: NavigationMarker[] = [
  { id: 'defaults', latitude: 37.7742, longitude: -122.4625, label: 'A' },
  {
    id: 'untrimmed',
    latitude: 37.774,
    longitude: -122.4645,
    label: 'B',
    ...outOfContract({ variant: ' SUCCESS ', size: ' Large ', markerStyle: 'DOT' }),
  },
  {
    id: 'explicit',
    latitude: 37.7738,
    longitude: -122.4665,
    label: 'C',
    variant: 'danger',
    size: 'small',
    markerStyle: 'pin',
  },
  {
    id: 'unknown',
    latitude: 37.7737,
    longitude: -122.4685,
    label: 'D',
    ...outOfContract({ variant: 'bogus', size: 'huge', markerStyle: 'square' }),
  },
]

export default function MarkersScenarioScreen() {
  const { hasLocationPermission, permissionStatus, requestLocationAccess } =
    useNavigationLocation(true)

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <StatusBar barStyle='light-content' />
      <MapboxNavigationView
        enabled={hasLocationPermission}
        style={StyleSheet.absoluteFill}
        startOrigin={START}
        destination={DESTINATION}
        navigationMarkers={MARKERS}
        cameraMode='overview'
        showsTripProgress={false}
        showsManeuverView={false}
      />
      <View pointerEvents='none' style={styles.legend}>
        <Text style={styles.legendText} testID='markers-legend'>
          A defaults · B untrimmed large green dot · C small red pin · D unknown values = A
        </Text>
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
  screen: { flex: 1, backgroundColor: '#020617' },
  legend: {
    position: 'absolute',
    top: 10,
    left: 12,
    right: 12,
    borderRadius: 12,
    backgroundColor: 'rgba(2,6,23,0.86)',
    padding: 10,
  },
  legendText: { color: '#cbd5e1', fontSize: 12, lineHeight: 17 },
})

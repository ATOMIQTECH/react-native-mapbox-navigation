import {
  MapboxNavigationView,
  type MapStyleConfig,
  type RouteExclusions,
  type RouteProfile,
  type VehicleConstraints,
} from '@atomiqlab/react-native-mapbox-navigation'
import { useState } from 'react'
import { Pressable, ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { PRIMARY_DESTINATION } from '../constants/navigation'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

const PROFILES: RouteProfile[] = ['driving-traffic', 'driving', 'walking', 'cycling']

type ExclusionPreset = 'none' | 'toll' | 'tollFerryMotorway' | 'points'
const EXCLUSION_PRESETS: ExclusionPreset[] = ['none', 'toll', 'tollFerryMotorway', 'points']

type VehiclePreset = 'none' | 'van' | 'truck'
const VEHICLE_PRESETS: VehiclePreset[] = ['none', 'van', 'truck']

type LightPreset = 'default' | 'day' | 'dusk' | 'dawn' | 'night'
const LIGHT_PRESETS: LightPreset[] = ['default', 'day', 'dusk', 'dawn', 'night']

/**
 * Exercises the routing and style-configuration props added in 3.0.0.
 *
 * Deliberately a Standard style, because `mapStyleConfig` only means anything
 * on a style whose basemap is a configurable import — on `navigation-day-v1`
 * the wrapper reports `MAP_STYLE_CONFIG_UNSUPPORTED` instead, which the error
 * line below will show.
 */
export default function RoutingScenarioScreen() {
  const { hasLocationPermission, permissionStatus, resolvedStartOrigin, requestLocationAccess } =
    useNavigationLocation(true)

  const [profile, setProfile] = useState<RouteProfile>('driving-traffic')
  const [exclusionPreset, setExclusionPreset] = useState<ExclusionPreset>('none')
  const [vehiclePreset, setVehiclePreset] = useState<VehiclePreset>('none')
  const [lightPreset, setLightPreset] = useState<LightPreset>('default')
  const [show3dObjects, setShow3dObjects] = useState(true)
  const [showPoiLabels, setShowPoiLabels] = useState(true)
  const [panelOpen, setPanelOpen] = useState(true)
  const [lastError, setLastError] = useState<string | null>(null)
  const [routeSummary, setRouteSummary] = useState<string | null>(null)

  const routeExclusions: RouteExclusions | undefined =
    exclusionPreset === 'none'
      ? undefined
      : exclusionPreset === 'toll'
        ? { roadClasses: ['toll'] }
        : exclusionPreset === 'tollFerryMotorway'
          ? { roadClasses: ['toll', 'ferry', 'motorway'] }
          : {
              // A point on Market St, between the start and the destination, so
              // excluding it should visibly push the route onto another street.
              locations: [{ latitude: 37.7793, longitude: -122.4098 }],
            }

  const vehicle: VehicleConstraints | undefined =
    vehiclePreset === 'none'
      ? undefined
      : vehiclePreset === 'van'
        ? { maxHeight: 2.6, maxWeight: 3.5 }
        : { maxHeight: 4.2, maxWidth: 2.6, maxWeight: 18 }

  const mapStyleConfig: MapStyleConfig = {
    show3dObjects,
    showPointOfInterestLabels: showPoiLabels,
    ...(lightPreset === 'default' ? {} : { lightPreset }),
  }

  const cycle = <T,>(list: T[], current: T): T => list[(list.indexOf(current) + 1) % list.length]

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <StatusBar barStyle='light-content' />
      <MapboxNavigationView
        enabled={hasLocationPermission}
        style={StyleSheet.absoluteFill}
        startOrigin={resolvedStartOrigin}
        destination={PRIMARY_DESTINATION}
        shouldSimulateRoute
        mapStyleUriDay='mapbox://styles/mapbox/standard'
        mapStyleUriNight='mapbox://styles/mapbox/standard'
        routeProfile={profile}
        routeExclusions={routeExclusions}
        vehicle={vehicle}
        mapStyleConfig={mapStyleConfig}
        onError={(event) => {
          setLastError(`${event.code}: ${event.message}`)
        }}
        onRouteChange={(event) => {
          setRouteSummary(`${event.coordinates.length} coords`)
        }}
      />

      <View pointerEvents='box-none' style={styles.overlayRoot}>
        {!panelOpen ? (
          <View style={styles.collapsedRow}>
            <ChipButton label='Panel: show' onPress={() => setPanelOpen(true)} />
          </View>
        ) : (
          <ScrollView contentContainerStyle={styles.panel}>
            <Text style={styles.panelTitle}>Routing + Style Config</Text>
            <View style={styles.row}>
              <ChipButton label='Panel: hide' onPress={() => setPanelOpen(false)} />
            </View>
            <View style={styles.row}>
              <ChipButton
                label={`Profile: ${profile}`}
                onPress={() => setProfile((value) => cycle(PROFILES, value))}
              />
              <ChipButton
                label={`Exclude: ${exclusionPreset}`}
                onPress={() => setExclusionPreset((value) => cycle(EXCLUSION_PRESETS, value))}
              />
            </View>
            <View style={styles.row}>
              <ChipButton
                label={`Vehicle: ${vehiclePreset}`}
                onPress={() => setVehiclePreset((value) => cycle(VEHICLE_PRESETS, value))}
              />
              <ChipButton
                label={`Light: ${lightPreset}`}
                onPress={() => setLightPreset((value) => cycle(LIGHT_PRESETS, value))}
              />
            </View>
            <View style={styles.row}>
              <ChipButton
                label={`3D: ${show3dObjects ? 'on' : 'off'}`}
                onPress={() => setShow3dObjects((value) => !value)}
              />
              <ChipButton
                label={`POI: ${showPoiLabels ? 'on' : 'off'}`}
                onPress={() => setShowPoiLabels((value) => !value)}
              />
            </View>
            <Text style={styles.status}>route: {routeSummary ?? '—'}</Text>
            <Text style={styles.status}>error: {lastError ?? 'none'}</Text>
          </ScrollView>
        )}
      </View>

      <LocationPermissionOverlay
        hasLocationPermission={hasLocationPermission}
        permissionStatus={permissionStatus}
        requestLocationAccess={requestLocationAccess}
      />
    </SafeAreaView>
  )
}

function ChipButton({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={styles.chip}>
      <Text style={styles.chipLabel}>{label}</Text>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#020617',
  },
  overlayRoot: {
    ...StyleSheet.absoluteFill,
    paddingHorizontal: 10,
    paddingTop: 10,
    paddingBottom: 14,
  },
  collapsedRow: {
    flexDirection: 'row',
  },
  panel: {
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.24)',
    backgroundColor: 'rgba(2,6,23,0.86)',
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 10,
  },
  panelTitle: {
    color: '#f8fafc',
    fontSize: 18,
    fontWeight: '700',
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    borderRadius: 999,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.35)',
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  chipLabel: {
    color: '#e2e8f0',
    fontSize: 14,
    fontWeight: '600',
  },
  status: {
    color: '#94a3b8',
    fontSize: 13,
  },
})

import { MapboxNavigationView } from '@atomiqlab/react-native-mapbox-navigation'
import { useState } from 'react'
import { Pressable, ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { ALT_DESTINATION, PRIMARY_DESTINATION, TEST_WAYPOINTS } from '../constants/navigation'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

type ThemeOption = 'system' | 'light' | 'dark' | 'day' | 'night'
type StylePreset = 'navigation' | 'streets' | 'satellite'

export default function AppearanceScenarioScreen() {
  const {
    hasLocationPermission,
    permissionStatus,
    resolvedStartOrigin,
    requestLocationAccess,
  } = useNavigationLocation(true)
  const [destinationMode, setDestinationMode] = useState<'primary' | 'alternate'>('primary')
  const [theme, setTheme] = useState<ThemeOption>('system')
  const [stylePreset, setStylePreset] = useState<StylePreset>('navigation')
  const [showsTripProgress, setShowsTripProgress] = useState(true)
  const [showsManeuverView, setShowsManeuverView] = useState(true)
  const [showsActionButtons, setShowsActionButtons] = useState(true)
  const [showsSpeedLimits, setShowsSpeedLimits] = useState(true)
  const [showsWayNameLabel, setShowsWayNameLabel] = useState(true)
  const [routeAlternatives, setRouteAlternatives] = useState(true)
  const [showsContinuousAlternatives, setShowsContinuousAlternatives] = useState(true)

  const destination = destinationMode === 'primary' ? PRIMARY_DESTINATION : ALT_DESTINATION
  const styleUris: { day?: string; night?: string } =
    stylePreset === 'streets'
      ? {
          day: 'mapbox://styles/mapbox/streets-v12',
          night: 'mapbox://styles/mapbox/navigation-night-v1',
        }
      : stylePreset === 'satellite'
        ? {
            day: 'mapbox://styles/mapbox/satellite-streets-v12',
            night: 'mapbox://styles/mapbox/satellite-streets-v12',
          }
        : {
            day: 'mapbox://styles/mapbox/navigation-day-v1',
            night: 'mapbox://styles/mapbox/navigation-night-v1',
          }

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <StatusBar barStyle='light-content' />
      <MapboxNavigationView
        enabled={hasLocationPermission}
        style={StyleSheet.absoluteFill}
        startOrigin={resolvedStartOrigin}
        destination={destination}
        waypoints={TEST_WAYPOINTS}
        shouldSimulateRoute
        uiTheme={theme}
        mapStyleUriDay={styleUris.day}
        mapStyleUriNight={styleUris.night}
        routeAlternatives={routeAlternatives}
        showsContinuousAlternatives={showsContinuousAlternatives}
        showsTripProgress={showsTripProgress}
        showsManeuverView={showsManeuverView}
        showsActionButtons={showsActionButtons}
        showsSpeedLimits={showsSpeedLimits}
        showsWayNameLabel={showsWayNameLabel}
        nativeFloatingButtons={{
          showAudioGuidanceButton: true,
          showCameraModeButton: true,
          showRecenterButton: true,
          showCompassButton: true,
        }}
      />

      <View pointerEvents='box-none' style={styles.overlayRoot}>
        <ScrollView contentContainerStyle={styles.panel}>
          <Text style={styles.panelTitle}>Appearance + Route Scenario</Text>
          <View style={styles.row}>
            <ChipButton
              label={`Destination: ${destinationMode}`}
              onPress={() => {
                setDestinationMode((value) => (value === 'primary' ? 'alternate' : 'primary'))
              }}
            />
            <ChipButton
              label={`Theme: ${theme}`}
              onPress={() => {
                const order: ThemeOption[] = ['system', 'light', 'dark', 'day', 'night']
                const current = order.indexOf(theme)
                setTheme(order[(current + 1) % order.length])
              }}
            />
            <ChipButton
              label={`Style: ${stylePreset}`}
              onPress={() => {
                const order: StylePreset[] = ['navigation', 'streets', 'satellite']
                const current = order.indexOf(stylePreset)
                setStylePreset(order[(current + 1) % order.length])
              }}
            />
          </View>
          <View style={styles.row}>
            <ChipButton
              label={`TripProgress: ${showsTripProgress ? 'on' : 'off'}`}
              onPress={() => {
                setShowsTripProgress((value) => !value)
              }}
            />
            <ChipButton
              label={`Maneuver: ${showsManeuverView ? 'on' : 'off'}`}
              onPress={() => {
                setShowsManeuverView((value) => !value)
              }}
            />
            <ChipButton
              label={`Actions: ${showsActionButtons ? 'on' : 'off'}`}
              onPress={() => {
                setShowsActionButtons((value) => !value)
              }}
            />
          </View>
          <View style={styles.row}>
            <ChipButton
              label={`SpeedLimits: ${showsSpeedLimits ? 'on' : 'off'}`}
              onPress={() => {
                setShowsSpeedLimits((value) => !value)
              }}
            />
            <ChipButton
              label={`WayName: ${showsWayNameLabel ? 'on' : 'off'}`}
              onPress={() => {
                setShowsWayNameLabel((value) => !value)
              }}
            />
            <ChipButton
              label={`Alternatives: ${routeAlternatives ? 'on' : 'off'}`}
              onPress={() => {
                setRouteAlternatives((value) => !value)
              }}
            />
            <ChipButton
              label={`ContinuousAlt: ${showsContinuousAlternatives ? 'on' : 'off'}`}
              onPress={() => {
                setShowsContinuousAlternatives((value) => !value)
              }}
            />
          </View>
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
    ...StyleSheet.absoluteFillObject,
    paddingHorizontal: 10,
    paddingTop: 10,
    paddingBottom: 14,
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
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '800',
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    borderRadius: 999,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.3)',
    backgroundColor: 'rgba(15,23,42,0.9)',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  chipLabel: {
    color: '#ffffff',
    fontSize: 12,
    fontWeight: '700',
  },
})

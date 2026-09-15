import { MapboxNavigationView } from '@atomiqlab/react-native-mapbox-navigation'
import { useState } from 'react'
import { Pressable, ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { ALT_DESTINATION, PRIMARY_DESTINATION, TEST_WAYPOINTS } from '../constants/navigation'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

type ThemeOption = 'system' | 'light' | 'dark' | 'day' | 'night'
type StylePreset = 'navigation' | 'standard' | 'standard-satellite' | 'streets' | 'satellite'

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
  // The cancel button lives inside the iOS trip progress bar, so the bar only
  // disappears when both of these are off. Exposed as a chip so that path is
  // actually testable.
  const [showCancelButton, setShowCancelButton] = useState(true)
  const [showsManeuverView, setShowsManeuverView] = useState(true)
  const [showsActionButtons, setShowsActionButtons] = useState(true)
  const [showsSpeedLimits, setShowsSpeedLimits] = useState(true)
  const [showsWayNameLabel, setShowsWayNameLabel] = useState(true)
  // Demonstrates the `colors` prop: a green theme instead of Mapbox blue,
  // covering the route line, the on-map turn arrow, the maneuver banner and the
  // floating buttons — so this toggle exercises every group the prop reaches.
  const [customColors, setCustomColors] = useState(false)
  const [routeAlternatives, setRouteAlternatives] = useState(true)
  // Collapses this panel to a single chip. The panel otherwise covers the
  // native maneuver banner, which is the thing several of the `colors` keys
  // theme — so without this there is no way to actually look at them.
  const [panelOpen, setPanelOpen] = useState(true)
  const [showsContinuousAlternatives, setShowsContinuousAlternatives] = useState(true)

  const destination = destinationMode === 'primary' ? PRIMARY_DESTINATION : ALT_DESTINATION
  // `standard` and `standard-satellite` are Mapbox's v3 Standard styles: 3D
  // buildings, landmarks and lighting. Included here to prove the wrapper works
  // with them — both platforms put the route line and turn arrow in the
  // `middle` slot, so they draw above the basemap but below labels and 3D
  // extrusions rather than fighting them.
  const STYLE_URIS: Record<StylePreset, { day: string; night: string }> = {
    navigation: {
      day: 'mapbox://styles/mapbox/navigation-day-v1',
      night: 'mapbox://styles/mapbox/navigation-night-v1',
    },
    standard: {
      day: 'mapbox://styles/mapbox/standard',
      night: 'mapbox://styles/mapbox/standard',
    },
    'standard-satellite': {
      day: 'mapbox://styles/mapbox/standard-satellite',
      night: 'mapbox://styles/mapbox/standard-satellite',
    },
    streets: {
      day: 'mapbox://styles/mapbox/streets-v12',
      night: 'mapbox://styles/mapbox/navigation-night-v1',
    },
    satellite: {
      day: 'mapbox://styles/mapbox/satellite-streets-v12',
      night: 'mapbox://styles/mapbox/satellite-streets-v12',
    },
  }
  const styleUris = STYLE_URIS[stylePreset]

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
        showCancelButton={showCancelButton}
        showsManeuverView={showsManeuverView}
        showsActionButtons={showsActionButtons}
        showsSpeedLimits={showsSpeedLimits}
        showsWayNameLabel={showsWayNameLabel}
        colors={
          customColors
            ? {
                routeLine: '#1E9E5A',
                routeLineCasing: '#14532D',
                routeLineAlternative: '#94A3B8',
                congestionModerate: '#F59E0B',
                congestionHeavy: '#DC2626',
                congestionSevere: '#7F1D1D',
                maneuverArrow: '#FFFFFF',
                maneuverArrowStroke: '#14532D',
                maneuverBackground: '#14532D',
                maneuverSubBackground: '#0B3320',
                maneuverText: '#FFFFFF',
                maneuverSecondaryText: '#BBF7D0',
                maneuverDistanceText: '#BBF7D0',
                maneuverTurnIcon: '#FFFFFF',
                tripProgressBackground: '#14532D',
                tripProgressText: '#FFFFFF',
                tripProgressIcon: '#BBF7D0',
                floatingButtonBackground: '#14532D',
                floatingButtonIcon: '#FFFFFF',
              }
            : undefined
        }
        nativeFloatingButtons={{
          showAudioGuidanceButton: true,
          showCameraModeButton: true,
          showRecenterButton: true,
          showCompassButton: true,
        }}
      />

      <View pointerEvents='box-none' style={styles.overlayRoot}>
        {!panelOpen ? (
          <View style={styles.collapsedRow}>
            <ChipButton label='Panel: show' onPress={() => setPanelOpen(true)} />
          </View>
        ) : (
        <ScrollView contentContainerStyle={styles.panel}>
          <Text style={styles.panelTitle}>Appearance + Route Scenario</Text>
          <View style={styles.row}>
            <ChipButton label='Panel: hide' onPress={() => setPanelOpen(false)} />
          </View>
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
                const order: StylePreset[] = [
                  'navigation',
                  'standard',
                  'standard-satellite',
                  'streets',
                  'satellite',
                ]
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
              label={`Cancel: ${showCancelButton ? 'on' : 'off'}`}
              onPress={() => {
                setShowCancelButton((value) => !value)
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
              label={`Colors: ${customColors ? 'custom' : 'mapbox'}`}
              onPress={() => {
                setCustomColors((value) => !value)
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

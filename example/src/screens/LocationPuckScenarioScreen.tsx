import {
  type LocationPuckConfig,
  MapboxNavigationView,
} from '@atomiqlab/react-native-mapbox-navigation'
import { useState } from 'react'
import { Pressable, ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { PRIMARY_DESTINATION } from '../constants/navigation'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

type PuckPreset = 'default' | 'tinted' | '2d' | '3d' | 'none' | 'perState'

/**
 * A publicly hosted glTF car model from Mapbox's own examples.
 *
 * Using a remote URL here keeps the example runnable without adding binary
 * assets to the repo or registering `glb` in the example's Metro config. In a
 * real app you would usually ship the model locally:
 *
 * ```tsx
 * modelUri: require('./assets/car.glb')
 * ```
 *
 * with `config.resolver.assetExts.push('glb', 'gltf')` in `metro.config.js`.
 */
const REMOTE_CAR_MODEL =
  'https://raw.githubusercontent.com/mapbox/mapbox-maps-android/main/app/src/main/assets/sportcar.glb'

/** Zoom-interpolated scale, which behaves consistently across platforms. */
const CAR_SCALE_EXPRESSION = JSON.stringify([
  'interpolate',
  ['linear'],
  ['zoom'],
  14,
  [6, 6, 6],
  18,
  [20, 20, 20],
])

const PUCK_PRESETS: Record<PuckPreset, LocationPuckConfig | undefined> = {
  // Leaves the prop off entirely — the Mapbox SDK default puck.
  default: undefined,

  // Recolours the built-in puck. No assets required.
  tinted: {
    type: 'tinted',
    color: '#2563EB',
    haloColor: '#FFFFFF',
    scale: 1.1,
  },

  // Custom flat images. `bearingImage` rotates to the course.
  '2d': {
    type: '2d',
    bearingImage: { uri: 'https://docs.mapbox.com/mapbox-gl-js/assets/custom_marker.png' },
    scale: 0.6,
  },

  // A glTF model, sized by a zoom expression.
  '3d': {
    type: '3d',
    modelUri: REMOTE_CAR_MODEL,
    rotation: [0, 0, 180],
    scaleExpression: CAR_SCALE_EXPRESSION,
  },

  // Hides the pointer — for apps drawing their own vehicle marker.
  none: {
    type: 'none',
  },

  // A different pointer per navigation state.
  perState: {
    default: { type: 'tinted', color: '#7C3AED', haloColor: '#FFFFFF' },
    activeNavigation: {
      type: '3d',
      modelUri: REMOTE_CAR_MODEL,
      rotation: [0, 0, 180],
      scaleExpression: CAR_SCALE_EXPRESSION,
    },
    arrival: { type: 'tinted', color: '#16A34A', haloColor: '#FFFFFF', scale: 1.3 },
  },
}

const PRESET_ORDER: PuckPreset[] = ['default', 'tinted', '2d', '3d', 'none', 'perState']

const PRESET_HINTS: Record<PuckPreset, string> = {
  default: 'Mapbox SDK default puck (prop omitted).',
  tinted: 'Built-in puck shape, recoloured. No assets needed.',
  '2d': 'Custom bearing image, rotated to the course.',
  '3d': 'Remote glTF model, scaled by a zoom expression.',
  none: 'Pointer hidden; location updates continue.',
  perState: 'Purple idle, 3D car while navigating, green on arrival.',
}

export default function LocationPuckScenarioScreen() {
  const { hasLocationPermission, permissionStatus, resolvedStartOrigin, requestLocationAccess } =
    useNavigationLocation(true)
  const [preset, setPreset] = useState<PuckPreset>('3d')

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <StatusBar barStyle='light-content' />
      <MapboxNavigationView
        enabled={hasLocationPermission}
        style={StyleSheet.absoluteFill}
        startOrigin={resolvedStartOrigin}
        destination={PRIMARY_DESTINATION}
        shouldSimulateRoute
        locationPuck={PUCK_PRESETS[preset]}
        nativeFloatingButtons={{
          showAudioGuidanceButton: true,
          showRecenterButton: true,
        }}
      />

      <View pointerEvents='box-none' style={styles.overlayRoot}>
        <ScrollView contentContainerStyle={styles.panel}>
          <Text style={styles.panelTitle}>Custom Location Puck</Text>
          <Text style={styles.hint}>{PRESET_HINTS[preset]}</Text>
          <View style={styles.row}>
            {PRESET_ORDER.map((option) => (
              <Pressable
                key={option}
                onPress={() => {
                  setPreset(option)
                }}
                style={[styles.chip, preset === option ? styles.chipActive : null]}
              >
                <Text style={styles.chipLabel}>{option}</Text>
              </Pressable>
            ))}
          </View>
          <Text style={styles.note}>
            Remote models are downloaded once and cached. `translation` is Android-only; iOS has
            no per-state puck API, so the pointer is swapped on state changes there.
          </Text>
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
    ...StyleSheet.absoluteFill,
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
  hint: {
    color: 'rgba(226,232,240,0.9)',
    fontSize: 12,
    lineHeight: 17,
  },
  note: {
    color: 'rgba(148,163,184,0.9)',
    fontSize: 11,
    lineHeight: 16,
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
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  chipActive: {
    backgroundColor: '#2563eb',
    borderColor: '#2563eb',
  },
  chipLabel: {
    color: '#f8fafc',
    fontSize: 12,
    fontWeight: '700',
  },
})

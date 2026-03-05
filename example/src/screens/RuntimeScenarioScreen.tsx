import {
  getNavigationSettings,
  MapboxNavigationView,
  resumeCameraFollowing,
  setDistanceUnit as applyDistanceUnit,
  setLanguage as applyLanguage,
  setMuted as applyMute,
  setVoiceVolume as applyVoiceVolume,
  stopNavigation,
} from '@atomiqlab/react-native-mapbox-navigation'
import { useEffect, useState } from 'react'
import { Pressable, ScrollView, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { PRIMARY_DESTINATION } from '../constants/navigation'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

const LANGUAGES = ['en', 'fr', 'sw'] as const

export default function RuntimeScenarioScreen() {
  const {
    hasLocationPermission,
    permissionStatus,
    resolvedStartOrigin,
    requestLocationAccess,
  } = useNavigationLocation(true)
  const [mute, setMute] = useState(false)
  const [voiceVolume, setVoiceVolume] = useState(0.9)
  const [distanceUnit, setDistanceUnit] = useState<'metric' | 'imperial'>('metric')
  const [language, setLanguage] = useState<(typeof LANGUAGES)[number]>('en')
  const [cameraMode, setCameraMode] = useState<'following' | 'overview'>('following')
  const [settingsSnapshot, setSettingsSnapshot] = useState('Not loaded')
  const [lastResult, setLastResult] = useState<string>('idle')

  const refreshSettings = async () => {
    const settings = await getNavigationSettings()
    setSettingsSnapshot(JSON.stringify(settings))
  }

  useEffect(() => {
    void refreshSettings()
  }, [])

  const setNextLanguage = async () => {
    const currentIndex = LANGUAGES.indexOf(language)
    const next = LANGUAGES[(currentIndex + 1) % LANGUAGES.length]
    setLanguage(next)
    await applyLanguage(next)
    setLastResult(`setLanguage(${next})`)
  }

  const toggleDistanceUnit = async () => {
    const next = distanceUnit === 'metric' ? 'imperial' : 'metric'
    setDistanceUnit(next)
    await applyDistanceUnit(next)
    setLastResult(`setDistanceUnit(${next})`)
  }

  const changeVolume = async (delta: number) => {
    const next = Math.max(0, Math.min(1, Math.round((voiceVolume + delta) * 10) / 10))
    setVoiceVolume(next)
    await applyVoiceVolume(next)
    setLastResult(`setVoiceVolume(${next})`)
  }

  const toggleMute = async () => {
    const next = !mute
    setMute(next)
    await applyMute(next)
    setLastResult(`setMuted(${next})`)
  }

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <StatusBar barStyle='light-content' />
      <MapboxNavigationView
        enabled={hasLocationPermission}
        style={StyleSheet.absoluteFill}
        startOrigin={resolvedStartOrigin}
        destination={PRIMARY_DESTINATION}
        shouldSimulateRoute
        mute={mute}
        voiceVolume={voiceVolume}
        distanceUnit={distanceUnit}
        language={language}
        cameraMode={cameraMode}
      />

      <View pointerEvents='box-none' style={styles.overlayRoot}>
        <ScrollView contentContainerStyle={styles.panel}>
          <Text style={styles.panelTitle}>Runtime API Scenario</Text>
          <Text style={styles.text}>last result: {lastResult}</Text>
          <Text style={styles.text}>settings: {settingsSnapshot}</Text>
          <View style={styles.row}>
            <Pressable onPress={() => void toggleMute()} style={styles.button}>
              <Text style={styles.buttonLabel}>{mute ? 'Unmute' : 'Mute'}</Text>
            </Pressable>
            <Pressable onPress={() => void toggleDistanceUnit()} style={styles.button}>
              <Text style={styles.buttonLabel}>Unit: {distanceUnit}</Text>
            </Pressable>
            <Pressable onPress={() => void setNextLanguage()} style={styles.button}>
              <Text style={styles.buttonLabel}>Lang: {language}</Text>
            </Pressable>
          </View>
          <View style={styles.row}>
            <Pressable onPress={() => void changeVolume(-0.1)} style={styles.buttonSecondary}>
              <Text style={styles.buttonLabel}>Vol -</Text>
            </Pressable>
            <Pressable onPress={() => void changeVolume(0.1)} style={styles.buttonSecondary}>
              <Text style={styles.buttonLabel}>Vol +</Text>
            </Pressable>
            <Pressable
              onPress={() => {
                setCameraMode((value) => (value === 'following' ? 'overview' : 'following'))
              }}
              style={styles.buttonSecondary}
            >
              <Text style={styles.buttonLabel}>Camera: {cameraMode}</Text>
            </Pressable>
          </View>
          <View style={styles.row}>
            <Pressable
              onPress={() => {
                void resumeCameraFollowing()
                setCameraMode('following')
                setLastResult('resumeCameraFollowing()')
              }}
              style={styles.buttonSecondary}
            >
              <Text style={styles.buttonLabel}>Resume Follow</Text>
            </Pressable>
            <Pressable
              onPress={() => {
                void stopNavigation().then((result) => {
                  setLastResult(`stopNavigation() => ${result}`)
                })
              }}
              style={styles.buttonSecondary}
            >
              <Text style={styles.buttonLabel}>Stop Session</Text>
            </Pressable>
            <Pressable onPress={() => void refreshSettings()} style={styles.buttonSecondary}>
              <Text style={styles.buttonLabel}>Refresh Settings</Text>
            </Pressable>
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
    gap: 8,
  },
  panelTitle: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '800',
  },
  text: {
    color: '#cbd5e1',
    fontSize: 12,
    lineHeight: 17,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
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
    borderColor: 'rgba(148,163,184,0.34)',
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

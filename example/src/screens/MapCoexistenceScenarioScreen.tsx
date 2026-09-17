import { MapboxNavigationView } from '@atomiqlab/react-native-mapbox-navigation'
import Mapbox, { Camera, MapView, MarkerView } from '@rnmapbox/maps'
import { useState } from 'react'
import { Pressable, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { PRIMARY_DESTINATION } from '../constants/navigation'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

/**
 * `@rnmapbox/maps` reads its token from its own module rather than from the
 * `MBXAccessToken` this package's config plugin writes into Info.plist, so the
 * example hands it the same public token.
 */
Mapbox.setAccessToken(process.env.EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN ?? null)

/**
 * Both Mapbox packages in one app — the scenario that actually breaks builds.
 *
 * `@rnmapbox/maps` wants the Mapbox Maps SDK from CocoaPods while the
 * Navigation SDK v3 exists only as a Swift package, and both declare
 * `com.mapbox.maps` on Android where the plain and `-ndk27` artifacts collide.
 * The config plugin reconciles all of that on its own, so this screen exists to
 * prove it: if the two ever stop sharing one Maps SDK, this is the screen that
 * fails to link rather than a consumer's app.
 *
 * Note the example's `plugins` array lists `@rnmapbox/maps` *after* this
 * package on purpose. Expo runs mods last-registered-first, so that ordering
 * puts `$ExpoMapboxNavigation.post_install` ahead of `$RNMapboxMaps.post_install`
 * in the Podfile — the order that used to require the consumer to reshuffle
 * their plugin list.
 */
export default function MapCoexistenceScenarioScreen() {
  const [mode, setMode] = useState<'map' | 'navigation'>('map')
  const { hasLocationPermission, permissionStatus, resolvedStartOrigin, requestLocationAccess } =
    useNavigationLocation(true)

  return (
    <SafeAreaView style={styles.screen} edges={['bottom']}>
      <StatusBar barStyle='light-content' />

      <View style={styles.toolbar}>
        {(['map', 'navigation'] as const).map((value) => (
          <Pressable
            key={value}
            onPress={() => setMode(value)}
            style={[styles.tab, mode === value && styles.tabActive]}
          >
            <Text style={[styles.tabLabel, mode === value && styles.tabLabelActive]}>
              {value === 'map' ? '@rnmapbox/maps' : 'navigation SDK'}
            </Text>
          </Pressable>
        ))}
      </View>

      <View style={styles.stage}>
        {mode === 'map' ? (
          <MapView style={styles.fill} styleURL='mapbox://styles/mapbox/standard'>
            <Camera
              defaultSettings={{
                centerCoordinate: [PRIMARY_DESTINATION.longitude, PRIMARY_DESTINATION.latitude],
                zoomLevel: 13,
              }}
            />
            <MarkerView coordinate={[PRIMARY_DESTINATION.longitude, PRIMARY_DESTINATION.latitude]}>
              <View style={styles.marker} />
            </MarkerView>
          </MapView>
        ) : hasLocationPermission ? (
          <MapboxNavigationView
            enabled
            style={styles.fill}
            startOrigin={resolvedStartOrigin}
            destination={PRIMARY_DESTINATION}
          />
        ) : (
          <LocationPermissionOverlay
            hasLocationPermission={hasLocationPermission}
            permissionStatus={permissionStatus}
            requestLocationAccess={requestLocationAccess}
          />
        )}
      </View>

      <Text style={styles.footnote}>
        Both screens render Mapbox Maps. They share one Maps SDK — CocoaPods installs no Mapbox pod
        at all, and Gradle resolves a single `com.mapbox.maps` artifact.
      </Text>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#020617',
  },
  toolbar: {
    flexDirection: 'row',
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 10,
    borderRadius: 10,
    backgroundColor: '#0f172a',
    borderWidth: 1,
    borderColor: '#1e293b',
  },
  tabActive: {
    backgroundColor: '#1d4ed8',
    borderColor: '#3b82f6',
  },
  tabLabel: {
    color: '#94a3b8',
    fontSize: 13,
    fontWeight: '600',
  },
  tabLabelActive: {
    color: '#f8fafc',
  },
  stage: {
    flex: 1,
    overflow: 'hidden',
  },
  fill: {
    flex: 1,
  },
  marker: {
    width: 18,
    height: 18,
    borderRadius: 9,
    borderWidth: 3,
    borderColor: '#f8fafc',
    backgroundColor: '#1d4ed8',
  },
  footnote: {
    color: '#64748b',
    fontSize: 12,
    lineHeight: 18,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
})

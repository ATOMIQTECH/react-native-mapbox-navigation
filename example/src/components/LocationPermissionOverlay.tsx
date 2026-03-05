import { Linking, Pressable, StyleSheet, Text, View } from 'react-native'

import type { LocationPermissionStatus } from '../hooks/useNavigationLocation'

type LocationPermissionOverlayProps = {
  hasLocationPermission: boolean
  permissionStatus: LocationPermissionStatus
  requestLocationAccess: () => Promise<void>
}

export function LocationPermissionOverlay({
  hasLocationPermission,
  permissionStatus,
  requestLocationAccess,
}: LocationPermissionOverlayProps) {
  if (hasLocationPermission) {
    return null
  }

  return (
    <View style={styles.permissionOverlay}>
      <Text style={styles.permissionTitle}>Location Permission Needed</Text>
      <Text style={styles.permissionText}>
        Grant location access to run embedded navigation scenarios in this test app.
      </Text>
      <Pressable
        onPress={() => {
          if (permissionStatus === 'blocked') {
            void Linking.openSettings()
            return
          }
          void requestLocationAccess()
        }}
        style={styles.permissionButton}
      >
        <Text style={styles.permissionButtonLabel}>
          {permissionStatus === 'requesting'
            ? 'Requesting...'
            : permissionStatus === 'blocked'
              ? 'Open Settings'
              : 'Grant Permission'}
        </Text>
      </Pressable>
    </View>
  )
}

const styles = StyleSheet.create({
  permissionOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
    backgroundColor: 'rgba(2,6,23,0.76)',
    gap: 12,
  },
  permissionTitle: {
    color: '#f8fafc',
    fontSize: 20,
    fontWeight: '800',
    textAlign: 'center',
  },
  permissionText: {
    color: '#cbd5e1',
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
  permissionButton: {
    borderRadius: 999,
    backgroundColor: '#2563eb',
    paddingHorizontal: 18,
    paddingVertical: 12,
  },
  permissionButtonLabel: {
    color: '#ffffff',
    fontSize: 13,
    fontWeight: '800',
  },
})

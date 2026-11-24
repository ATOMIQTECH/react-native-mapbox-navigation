import {
  advanceToNextWaypoint,
  MapboxNavigationView,
  type NavigationError,
  type OffRouteEvent,
  type RouteProgress,
  type Waypoint,
} from '@atomiqlab/react-native-mapbox-navigation'
import { useNavigation } from '@react-navigation/native'
import { useState } from 'react'
import { Pressable, StatusBar, StyleSheet, Text, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'

import { LocationPermissionOverlay } from '../components/LocationPermissionOverlay'
import { useNavigationLocation } from '../hooks/useNavigationLocation'

const START: Waypoint = { latitude: 37.7739, longitude: -122.46173, name: 'Start' }
const STOPS: Waypoint[] = [
  { latitude: 37.77359, longitude: -122.46415, name: 'Stop A' },
  { latitude: 37.77336, longitude: -122.46687, name: 'Stop B' },
]
const DESTINATION: Waypoint = { latitude: 37.77326, longitude: -122.46912, name: 'Finish' }
const MOVED_DESTINATION: Waypoint = { latitude: 37.77316, longitude: -122.4716, name: 'Moved' }
const UNROUTABLE_DESTINATION: Waypoint = { latitude: 30, longitude: -140, name: 'Mid-Pacific' }

type Advance = 'automatic' | 'manual'

type JourneyState = {
  advance: Advance
  destination: Waypoint
  routeCount: number
  stopsReached: number[]
  arrived: boolean
  progress?: RouteProgress
  offRoute: string
  lastError: string
  following?: boolean
  advanceResult: string
  overridden: boolean
}

const INITIAL_STATE: JourneyState = {
  advance: 'automatic',
  destination: DESTINATION,
  routeCount: 0,
  stopsReached: [],
  arrived: false,
  offRoute: 'none',
  lastError: 'none',
  advanceResult: '-',
  overridden: false,
}

function describeOffRoute(event: OffRouteEvent): string {
  const state = event.offRoute === true ? 'off' : 'on'
  const failed = event.rerouteFailed === true ? '/failed' : ''
  return `${state}${failed}/${event.reason ?? '-'}`
}

function describeError(error: NavigationError): string {
  return `${error.code} (${error.recoverable ? 'recoverable' : 'fatal'})`
}

function describeLeg(progress: RouteProgress | undefined): string {
  if (progress === undefined) return 'Leg: -'
  return `Leg: ${progress.legIndex ?? 0} | ${Math.round(progress.distanceRemaining)}m left`
}

function describeCamera(following: boolean | undefined): string {
  if (following === undefined) return 'unknown'
  return following ? 'following' : 'free'
}

function JourneyReadout({ state }: { state: JourneyState }) {
  return (
    <>
      <Text style={styles.panelText} testID='journey-routes'>
        Routes: {state.routeCount}
      </Text>
      <Text style={styles.panelText} testID='journey-leg'>
        {describeLeg(state.progress)}
      </Text>
      <Text style={styles.panelText} testID='journey-stops'>
        Stops: {state.stopsReached.length === 0 ? 'none' : state.stopsReached.join(',')}
      </Text>
      <Text style={styles.panelText} testID='journey-arrived'>
        Arrived: {state.arrived ? 'yes' : 'no'}
      </Text>
      <Text style={styles.panelText} testID='journey-offroute'>
        Off route: {state.offRoute}
      </Text>
      <Text style={styles.panelText} testID='journey-error'>
        Error: {state.lastError}
      </Text>
      <Text style={styles.panelText} testID='journey-camera'>
        Camera: {describeCamera(state.following)}
      </Text>
      <Text style={styles.panelText} testID='journey-advance'>
        Advance: {state.advance} | last call: {state.advanceResult}
      </Text>
      <Text style={styles.panelText} testID='journey-overrides'>
        Overrides: {state.overridden ? 'set' : 'unset'}
      </Text>
    </>
  )
}

function Button({ id, label, onPress }: { id: string; label: string; onPress: () => void }) {
  return (
    <Pressable testID={id} onPress={onPress} style={styles.button}>
      <Text style={styles.buttonLabel}>{label}</Text>
    </Pressable>
  )
}

type Update = (change: (state: JourneyState) => Partial<JourneyState>) => void

function AdvanceControls({ update }: { update: Update }) {
  return (
    <View style={styles.row}>
      <Button
        id='journey-manual'
        label='Toggle advance'
        onPress={() => {
          update((state) => ({ advance: state.advance === 'manual' ? 'automatic' : 'manual' }))
        }}
      />
      <Button
        id='journey-advance-now'
        label='Advance now'
        onPress={() => {
          void advanceToNextWaypoint().then((advanced) => {
            update(() => ({ advanceResult: advanced ? 'advanced' : 'refused' }))
          })
        }}
      />
      <Button
        id='journey-overrides-toggle'
        label='Toggle overrides'
        onPress={() => {
          update((state) => ({ overridden: !state.overridden }))
        }}
      />
    </View>
  )
}

function DestinationControls({ update }: { update: Update }) {
  const navigation = useNavigation()
  return (
    <View style={styles.row}>
      <Button
        id='journey-move'
        label='Move destination'
        onPress={() => {
          update(() => ({ destination: MOVED_DESTINATION }))
        }}
      />
      <Button
        id='journey-unroutable'
        label='Unroutable'
        onPress={() => {
          update(() => ({ destination: UNROUTABLE_DESTINATION }))
        }}
      />
      <Button
        id='journey-cover'
        label='Push screen'
        onPress={() => {
          navigation.navigate('JourneyCover')
        }}
      />
    </View>
  )
}

function JourneyNavigation({
  enabled,
  state,
  update,
}: {
  enabled: boolean
  state: JourneyState
  update: Update
}) {
  return (
    <MapboxNavigationView
      enabled={enabled}
      style={StyleSheet.absoluteFill}
      startOrigin={START}
      waypoints={STOPS}
      destination={state.destination}
      waypointAdvance={state.advance}
      keepAliveWhenDetached
      mute={state.overridden || undefined}
      cameraPitch={state.overridden ? 20 : undefined}
      showsSpeedLimits={state.overridden ? false : undefined}
      language={state.overridden ? 'fr' : undefined}
      showsTripProgress={false}
      showsManeuverView={false}
      onRouteChange={() => {
        update((s) => ({ routeCount: s.routeCount + 1 }))
      }}
      onRouteProgressChange={(progress) => {
        update(() => ({ progress }))
      }}
      onWaypointArrive={(event) => {
        update((s) => ({ stopsReached: [...s.stopsReached, event.index ?? -1] }))
      }}
      onArrive={() => {
        update(() => ({ arrived: true }))
      }}
      onOffRoute={(event) => {
        update(() => ({ offRoute: describeOffRoute(event) }))
      }}
      onError={(error) => {
        update(() => ({ lastError: describeError(error) }))
      }}
      onCameraFollowingStateChange={(camera) => {
        update(() => ({ following: camera.isCameraFollowing }))
      }}
    />
  )
}

export default function JourneyScenarioScreen() {
  const { hasLocationPermission, permissionStatus, requestLocationAccess } =
    useNavigationLocation(true)
  const [state, setState] = useState<JourneyState>(INITIAL_STATE)
  const update: Update = (change) => {
    setState((current) => ({ ...current, ...change(current) }))
  }

  return (
    <SafeAreaView style={styles.screen} edges={['top']}>
      <StatusBar barStyle='light-content' />
      <JourneyNavigation enabled={hasLocationPermission} state={state} update={update} />
      <View pointerEvents='box-none' style={styles.overlayRoot}>
        <View style={styles.panel}>
          <JourneyReadout state={state} />
          <AdvanceControls update={update} />
          <DestinationControls update={update} />
        </View>
      </View>
      <LocationPermissionOverlay
        hasLocationPermission={hasLocationPermission}
        permissionStatus={permissionStatus}
        requestLocationAccess={requestLocationAccess}
      />
    </SafeAreaView>
  )
}

export function JourneyCoverScreen() {
  return (
    <SafeAreaView style={styles.cover}>
      <Text style={styles.coverText} testID='journey-cover-screen'>
        Covering screen
      </Text>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#020617' },
  overlayRoot: {
    ...StyleSheet.absoluteFill,
    justifyContent: 'flex-start',
    paddingHorizontal: 12,
    paddingTop: 10,
  },
  panel: {
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(148,163,184,0.24)',
    backgroundColor: 'rgba(2,6,23,0.86)',
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 4,
  },
  panelText: { color: '#cbd5e1', fontSize: 12, lineHeight: 17 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 6 },
  button: {
    borderRadius: 999,
    backgroundColor: '#2563eb',
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  buttonLabel: { color: '#ffffff', fontSize: 12, fontWeight: '700' },
  cover: { flex: 1, backgroundColor: '#020617', alignItems: 'center', justifyContent: 'center' },
  coverText: { color: '#f8fafc', fontSize: 20, fontWeight: '700' },
})

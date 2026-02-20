import { MapboxNavigationView } from '@atomiqtech/react-native-mapbox-navigation';
import { useMemo, useState } from 'react';
import {
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

type EventLog = {
  id: number;
  message: string;
};

const START_ORIGIN = {
  latitude: 37.7749,
  longitude: -122.4194,
};

const DESTINATION = {
  latitude: 37.7847,
  longitude: -122.4073,
  name: 'Destination',
};

const WAYPOINTS = [{ latitude: 37.7793, longitude: -122.4129 }];

export default function NavigationTestScreen() {
  const [sessionKey, setSessionKey] = useState(0);
  const [simulateRoute, setSimulateRoute] = useState(true);
  const [mute, setMute] = useState(false);
  const [logs, setLogs] = useState<EventLog[]>([]);

  const canRenderNavigation = Platform.OS === 'ios' || Platform.OS === 'android';

  const addLog = (message: string) => {
    setLogs((prev) => [{ id: Date.now(), message }, ...prev].slice(0, 12));
  };

  const locationDebugText = useMemo(() => {
    return logs.find((entry) => entry.message.startsWith('location:'))?.message ?? 'No location updates yet.';
  }, [logs]);

  if (!canRenderNavigation) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.infoCard}>
          <Text style={styles.title}>Mapbox Navigation Test</Text>
          <Text style={styles.subtitle}>
            This screen is native-only. Run on iOS or Android to test our custom Mapbox Navigation module.
          </Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.topControls}>
        <Text style={styles.title}>Mapbox Navigation Test</Text>
        <Text style={styles.subtitle}>Custom Native Module</Text>
        <Text style={styles.subtitle}>
          Start: {START_ORIGIN.latitude.toFixed(4)}, {START_ORIGIN.longitude.toFixed(4)}
        </Text>
        <Text style={styles.subtitle}>
          End: {DESTINATION.latitude.toFixed(4)}, {DESTINATION.longitude.toFixed(4)}
        </Text>
        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>Simulate route</Text>
          <Switch value={simulateRoute} onValueChange={setSimulateRoute} />
        </View>
        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>Mute voice</Text>
          <Switch value={mute} onValueChange={setMute} />
        </View>
        <Pressable
          style={styles.restartButton}
          onPress={() => {
            setSessionKey((value) => value + 1);
            addLog('session restarted');
          }}
        >
          <Text style={styles.restartButtonText}>Restart Navigation Session</Text>
        </Pressable>
      </View>

      <View style={styles.navigationContainer}>
        <MapboxNavigationView
          key={sessionKey}
          style={styles.navigation}
          startOrigin={START_ORIGIN}
          destination={DESTINATION}
          waypoints={WAYPOINTS}
          shouldSimulateRoute={simulateRoute}
          showCancelButton={true}
          distanceUnit="metric"
          language="en"
          mute={mute}
          onLocationChange={(location) => {
            addLog(`location: ${location.latitude.toFixed(5)}, ${location.longitude.toFixed(5)}`);
          }}
          onRouteProgressChange={(progress) => {
            addLog(`progress: ${(progress.fractionTraveled * 100).toFixed(1)}%`);
          }}
          onArrive={(point) => {
            addLog(`arrived: ${point.name ?? `index ${point.index ?? '-'}`}`);
          }}
          onCancelNavigation={() => {
            addLog('navigation canceled');
          }}
          onError={(error) => {
            addLog(`error: ${error.message ?? 'unknown error'}`);
          }}
        />
      </View>

      <View style={styles.logContainer}>
        <Text style={styles.logTitle}>Latest location</Text>
        <Text style={styles.logText}>{locationDebugText}</Text>
        <Text style={styles.logTitle}>Event log</Text>
        <ScrollView contentContainerStyle={styles.logList}>
          {logs.length === 0 ? <Text style={styles.logText}>No events yet.</Text> : null}
          {logs.map((entry) => (
            <Text key={entry.id} style={styles.logText}>
              {entry.message}
            </Text>
          ))}
        </ScrollView>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0f172a',
  },
  topControls: {
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 10,
    gap: 6,
  },
  title: {
    color: '#f8fafc',
    fontSize: 20,
    fontWeight: '700',
  },
  subtitle: {
    color: '#cbd5e1',
    fontSize: 12,
  },
  switchRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 2,
  },
  switchLabel: {
    color: '#f1f5f9',
    fontSize: 14,
    fontWeight: '600',
  },
  restartButton: {
    alignItems: 'center',
    backgroundColor: '#0ea5e9',
    borderRadius: 10,
    marginTop: 4,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  restartButtonText: {
    color: '#082f49',
    fontWeight: '700',
  },
  navigationContainer: {
    flex: 1,
    marginHorizontal: 12,
    overflow: 'hidden',
    borderRadius: 12,
  },
  navigation: {
    flex: 1,
  },
  logContainer: {
    backgroundColor: '#111827',
    borderTopColor: '#1f2937',
    borderTopWidth: 1,
    gap: 4,
    maxHeight: 190,
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  logList: {
    gap: 4,
    paddingBottom: 8,
  },
  logTitle: {
    color: '#93c5fd',
    fontSize: 12,
    fontWeight: '700',
    marginTop: 2,
    textTransform: 'uppercase',
  },
  logText: {
    color: '#e5e7eb',
    fontSize: 12,
  },
  infoCard: {
    backgroundColor: '#111827',
    borderColor: '#1f2937',
    borderWidth: 1,
    borderRadius: 12,
    margin: 16,
    padding: 16,
  },
});

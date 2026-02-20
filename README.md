# @atomiqlab/react-native-mapbox-navigation

Native Mapbox turn-by-turn navigation for Expo apps on iOS and Android.

## Features

- Full-screen native navigation via `startNavigation`.
- Embedded native navigation UI via `MapboxNavigationView`.
- Real-time events: location, route progress, banner instruction, arrival, cancel, and error.
- Runtime controls for mute, voice volume, distance unit, and language.
- Navigation customization: camera mode/pitch/zoom, theme, map style, and UI visibility toggles.
- Expo config plugin that applies required Android and iOS native setup.

## Requirements

- Expo SDK `>=50`
- iOS `14+`
- Mapbox access credentials:
  - Public token (`pk...`)
  - Downloads token (`sk...`) with `DOWNLOADS:READ`

## Installation

```bash
npm install @atomiqlab/react-native-mapbox-navigation
```

## Expo Setup

Add the plugin in your app config (`app.json` or `app.config.js`):

```json
{
  "expo": {
    "plugins": [
      "@atomiqlab/react-native-mapbox-navigation"
    ]
  }
}
```

Set these environment variables:

- `EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN` (Mapbox public token)
- `MAPBOX_DOWNLOADS_TOKEN` (Mapbox downloads token)

Regenerate native projects:

```bash
npx expo prebuild --clean
```

### Token validation behavior

The config plugin now fails fast during prebuild/build when tokens are missing or malformed:

- Missing/invalid public token (`pk...`)
- Missing/invalid downloads token (`sk...`)

This prevents silent runtime failures and surfaces setup issues early.


## Quick Start

```ts
import {
  startNavigation,
  stopNavigation,
  addLocationChangeListener,
  addRouteProgressChangeListener,
  addBannerInstructionListener,
  addArriveListener,
  addCancelNavigationListener,
  addErrorListener,
} from "@atomiqlab/react-native-mapbox-navigation";

await startNavigation({
  destination: { latitude: 37.7847, longitude: -122.4073, name: "Downtown" },
  startOrigin: { latitude: 37.7749, longitude: -122.4194 },
  shouldSimulateRoute: true,
  routeAlternatives: true,
  cameraMode: "following",
  uiTheme: "system",
  distanceUnit: "metric",
  language: "en",
});

const subscriptions = [
  addLocationChangeListener((location) => console.log(location)),
  addRouteProgressChangeListener((progress) => console.log(progress)),
  addBannerInstructionListener((instruction) => console.log(instruction.primaryText)),
  addArriveListener((arrival) => console.log(arrival)),
  addCancelNavigationListener(() => console.log("cancelled")),
  addErrorListener((error) => console.warn(error)),
];

// Cleanup
subscriptions.forEach((sub) => sub.remove());
await stopNavigation();
```

## Embedded Navigation View

```tsx
import { MapboxNavigationView } from "@atomiqlab/react-native-mapbox-navigation";

<MapboxNavigationView
  style={{ flex: 1 }}
  destination={{ latitude: 37.7847, longitude: -122.4073, name: "Downtown" }}
  startOrigin={{ latitude: 37.7749, longitude: -122.4194 }}
  shouldSimulateRoute
  cameraMode="following"
  showsTripProgress
  onBannerInstruction={(instruction) => console.log(instruction.primaryText)}
  onRouteProgressChange={(progress) => console.log(progress.fractionTraveled)}
  onError={(error) => console.warn(error.message)}
/>;
```

```tsx
import React, { useEffect, useMemo, useState } from "react";
import {
  addArriveListener,
  addBannerInstructionListener,
  addCancelNavigationListener,
  addErrorListener,
  addLocationChangeListener,
  addRouteProgressChangeListener,
  getNavigationSettings,
  isNavigating,
  MapboxNavigationView,
  setDistanceUnit,
  setLanguage,
  setMuted,
  setVoiceVolume,
  startNavigation,
  stopNavigation,
  type Waypoint,
} from "@atomiqlab/react-native-mapbox-navigation";
import {
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";

const START: Waypoint = {
  latitude: 37.7749,
  longitude: -122.4194,
  name: "San Francisco",
};
const DEST: Waypoint = {
  latitude: 37.7847,
  longitude: -122.4073,
  name: "Downtown",
};

export default function Index() {
  const [logs, setLogs] = useState<string[]>([]);
  const [navigating, setNavigating] = useState(false);
  const [showEmbedded, setShowEmbedded] = useState(false);

  const [mute, setMuteState] = useState(false);
  const [unit, setUnit] = useState<"metric" | "imperial">("metric");
  const [language, setLanguageState] = useState("en");
  const [volumeInput, setVolumeInput] = useState("1");

  const pushLog = (line: string) =>
    setLogs((prev) =>
      [`${new Date().toLocaleTimeString()}  ${line}`, ...prev].slice(0, 40),
    );

  useEffect(() => {
    const s1 = addLocationChangeListener((e) =>
      pushLog(`location: ${e.latitude.toFixed(5)}, ${e.longitude.toFixed(5)}`),
    );
    const s2 = addRouteProgressChangeListener((e) =>
      pushLog(`progress: ${(e.fractionTraveled * 100).toFixed(1)}%`),
    );
    const s3 = addBannerInstructionListener((e) =>
      pushLog(`banner: ${e.primaryText}`),
    );
    const s4 = addArriveListener((e) => {
      pushLog(`arrive: ${e.name ?? "destination"}`);
      setNavigating(false);
    });
    const s5 = addCancelNavigationListener(() => {
      pushLog("cancelled");
      setNavigating(false);
    });
    const s6 = addErrorListener((e) =>
      pushLog(`error: ${e.message ?? e.code}`),
    );

    (async () => {
      try {
        setNavigating(await isNavigating());
        pushLog(`isNavigating() loaded`);
      } catch (e: any) {
        pushLog(`isNavigating failed: ${e?.message ?? "unknown"}`);
      }
    })();

    return () => {
      s1.remove();
      s2.remove();
      s3.remove();
      s4.remove();
      s5.remove();
      s6.remove();
    };
  }, []);

  const canRun = useMemo(
    () => Platform.OS === "ios" || Platform.OS === "android",
    [],
  );

  if (!canRun) {
    return (
      <SafeAreaView style={styles.center}>
        <Text style={styles.title}>Run on iOS/Android only</Text>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>Mapbox Navigation Full API Test</Text>
        <Text style={styles.status}>
          Status: {navigating ? "Navigating" : "Idle"}
        </Text>

        <View style={styles.row}>
          <Pressable
            style={styles.btn}
            onPress={async () => {
              try {
                await startNavigation({
                  startOrigin: START,
                  destination: DEST,
                  shouldSimulateRoute: true,
                  routeAlternatives: true,
                  cameraMode: "following",
                  uiTheme: "system",
                  mute,
                  voiceVolume: Math.max(
                    0,
                    Math.min(Number(volumeInput) || 1, 1),
                  ),
                  distanceUnit: unit,
                  language,
                });
                setNavigating(true);
                pushLog("startNavigation() ok");
              } catch (e: any) {
                pushLog(`startNavigation failed: ${e?.message ?? "unknown"}`);
              }
            }}
          >
            <Text style={styles.btnText}>startNavigation</Text>
          </Pressable>

          <Pressable
            style={styles.btn}
            onPress={async () => {
              try {
                await stopNavigation();
                setNavigating(false);
                pushLog("stopNavigation() ok");
              } catch (e: any) {
                pushLog(`stopNavigation failed: ${e?.message ?? "unknown"}`);
              }
            }}
          >
            <Text style={styles.btnText}>stopNavigation</Text>
          </Pressable>
        </View>

        <View style={styles.row}>
          <Pressable
            style={styles.btn}
            onPress={async () =>
              pushLog(`isNavigating(): ${await isNavigating()}`)
            }
          >
            <Text style={styles.btnText}>isNavigating</Text>
          </Pressable>

          <Pressable
            style={styles.btn}
            onPress={async () =>
              pushLog(
                `getNavigationSettings(): ${JSON.stringify(await getNavigationSettings())}`,
              )
            }
          >
            <Text style={styles.btnText}>getNavigationSettings</Text>
          </Pressable>
        </View>

        <View style={styles.card}>
          <Text style={styles.label}>mute / setMuted</Text>
          <View style={styles.row}>
            <Pressable
              style={styles.btn}
              onPress={async () => {
                const next = !mute;
                setMuteState(next);
                await setMuted(next);
                pushLog(`setMuted(${next})`);
              }}
            >
              <Text style={styles.btnText}>{mute ? "Unmute" : "Mute"}</Text>
            </Pressable>
          </View>

          <Text style={styles.label}>setVoiceVolume (0..1)</Text>
          <View style={styles.row}>
            <TextInput
              style={styles.input}
              value={volumeInput}
              onChangeText={setVolumeInput}
              keyboardType="decimal-pad"
            />
            <Pressable
              style={styles.btn}
              onPress={async () => {
                const vol = Math.max(0, Math.min(Number(volumeInput) || 1, 1));
                await setVoiceVolume(vol);
                pushLog(`setVoiceVolume(${vol})`);
              }}
            >
              <Text style={styles.btnText}>Apply</Text>
            </Pressable>
          </View>

          <Text style={styles.label}>setDistanceUnit</Text>
          <View style={styles.row}>
            <Pressable
              style={styles.btn}
              onPress={async () => {
                setUnit("metric");
                await setDistanceUnit("metric");
                pushLog("setDistanceUnit(metric)");
              }}
            >
              <Text style={styles.btnText}>Metric</Text>
            </Pressable>
            <Pressable
              style={styles.btn}
              onPress={async () => {
                setUnit("imperial");
                await setDistanceUnit("imperial");
                pushLog("setDistanceUnit(imperial)");
              }}
            >
              <Text style={styles.btnText}>Imperial</Text>
            </Pressable>
          </View>

          <Text style={styles.label}>setLanguage</Text>
          <View style={styles.row}>
            <TextInput
              style={styles.input}
              value={language}
              onChangeText={setLanguageState}
            />
            <Pressable
              style={styles.btn}
              onPress={async () => {
                await setLanguage(language.trim() || "en");
                pushLog(`setLanguage(${language.trim() || "en"})`);
              }}
            >
              <Text style={styles.btnText}>Apply</Text>
            </Pressable>
          </View>
        </View>

        <Pressable
          style={styles.btn}
          onPress={() => setShowEmbedded((v) => !v)}
        >
          <Text style={styles.btnText}>
            {showEmbedded ? "Hide" : "Show"} Embedded MapboxNavigationView
          </Text>
        </Pressable>

        {showEmbedded ? (
          <View style={styles.embeddedWrap}>
            <MapboxNavigationView
              style={{ flex: 1 }}
              destination={DEST}
              startOrigin={START}
              shouldSimulateRoute
              cameraMode="overview"
              uiTheme="system"
              onError={(e) => pushLog(`embedded error: ${e.message ?? e.code}`)}
              onBannerInstruction={(e) =>
                pushLog(`embedded banner: ${e.primaryText}`)
              }
              onArrive={(e) =>
                pushLog(`embedded arrive: ${e.name ?? "destination"}`)
              }
              onCancelNavigation={() => pushLog("embedded cancelled")}
            />
          </View>
        ) : null}

        <View style={styles.card}>
          <Text style={styles.label}>Event Logs</Text>
          {logs.map((l, i) => (
            <Text key={`${l}-${i}`} style={styles.log}>
              {l}
            </Text>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#0b1020" },
  center: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#0b1020",
  },
  content: { padding: 14, gap: 10, paddingBottom: 28 },
  title: { color: "#fff", fontSize: 22, fontWeight: "800" },
  status: { color: "#8ec5ff", fontSize: 13 },
  row: { flexDirection: "row", gap: 8, alignItems: "center" },
  card: { backgroundColor: "#131a2d", borderRadius: 12, padding: 10, gap: 8 },
  label: { color: "#cde4ff", fontSize: 12, fontWeight: "700" },
  input: {
    flex: 1,
    backgroundColor: "#0e1426",
    borderColor: "#2b3a5d",
    borderWidth: 1,
    borderRadius: 8,
    color: "#fff",
    paddingHorizontal: 10,
    paddingVertical: 8,
  },
  btn: {
    flex: 1,
    backgroundColor: "#2563eb",
    borderRadius: 10,
    paddingVertical: 10,
    paddingHorizontal: 10,
    alignItems: "center",
  },
  btnText: { color: "#fff", fontWeight: "700", fontSize: 12 },
  embeddedWrap: { height: 320, borderRadius: 12, overflow: "hidden" },
  log: { color: "#dbeafe", fontSize: 11, marginBottom: 2 },
});
```

## API Overview

Core functions:

- `startNavigation(options)`
- `stopNavigation()`
- `isNavigating()`
- `getNavigationSettings()`
- `setMuted(muted)`
- `setVoiceVolume(volume)`
- `setDistanceUnit(unit)`
- `setLanguage(language)`

Event listeners:

- `addLocationChangeListener(listener)`
- `addRouteProgressChangeListener(listener)`
- `addBannerInstructionListener(listener)`
- `addArriveListener(listener)`
- `addCancelNavigationListener(listener)`
- `addErrorListener(listener)`

Main `NavigationOptions` fields:

- Route: `destination`, `startOrigin`, `waypoints`, `routeAlternatives`, `shouldSimulateRoute`
- Camera: `cameraMode`, `cameraPitch`, `cameraZoom`
- Theme/style: `uiTheme`, `mapStyleUri`, `mapStyleUriDay`, `mapStyleUriNight`
- Guidance: `distanceUnit`, `language`, `mute`, `voiceVolume`
- UI toggles: `showsSpeedLimits`, `showsWayNameLabel`, `showsTripProgress`, `showsManeuverView`, `showsActionButtons`

Full types: `src/MapboxNavigation.types.ts`

## Common Error Codes

- `MAPBOX_TOKEN_INVALID`: invalid/expired token or unauthorized access
- `MAPBOX_TOKEN_FORBIDDEN`: token lacks required scopes/permissions
- `MAPBOX_RATE_LIMITED`: Mapbox rate limit reached
- `ROUTE_FETCH_FAILED`: route request failed with native details
- `CURRENT_LOCATION_UNAVAILABLE`: unable to resolve device location
- `INVALID_COORDINATES`: invalid origin/destination coordinates

Subscribe via `addErrorListener` or `onError` to surface these to developers during testing and production diagnostics.

## Platform Notes

- Android: `startOrigin` is optional (current location is supported).
- iOS: `startOrigin` is optional (current location is resolved at runtime with location permission).

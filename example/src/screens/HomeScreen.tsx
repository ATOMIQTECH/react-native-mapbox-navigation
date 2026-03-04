import { useEffect, useState } from "react";
import * as Location from "expo-location";
import {
  Linking,
  Pressable,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  MapboxNavigationFloatingButton,
  MapboxNavigationFloatingButtonsStack,
  MapboxNavigationView,
  type FloatingButtonsRenderContext,
  type LocationUpdate,
  type Waypoint,
} from "@atomiqlab/react-native-mapbox-navigation";

const START_ORIGIN: Waypoint = {
  latitude: 37.7749,
  longitude: -122.4194,
  name: "San Francisco",
};

const DESTINATION: Waypoint = {
  latitude: 37.7847,
  longitude: -122.4073,
  name: "Union Square",
};

export default function HomeScreen() {
  const [supportSheetOpen, setSupportSheetOpen] = useState(false);
  const [lastAction, setLastAction] = useState("none");
  const [hasLocationPermission, setHasLocationPermission] = useState(false);
  const [permissionStatus, setPermissionStatus] = useState<
    "checking" | "requesting" | "granted" | "denied" | "blocked"
  >("checking");
  const [capturedLocation, setCapturedLocation] = useState<
    LocationUpdate | undefined
  >(undefined);
  const [lastError, setLastError] = useState<string | null>(null);

  const captureDeviceLocation = async () => {
    try {
      const position = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      setCapturedLocation({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracy: position.coords.accuracy ?? undefined,
        altitude: position.coords.altitude ?? undefined,
        bearing: position.coords.heading ?? undefined,
        speed: position.coords.speed ?? undefined,
      });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Unable to capture location.";
      setLastError(`LOCATION_CAPTURE_FAILED: ${message}`);
    }
  };

  const requestLocationAccess = async () => {
    const existingPermission = await Location.getForegroundPermissionsAsync();
    if (existingPermission.granted) {
      setHasLocationPermission(true);
      setPermissionStatus("granted");
      await captureDeviceLocation();
      return;
    }

    setPermissionStatus("requesting");
    const requestedPermission =
      await Location.requestForegroundPermissionsAsync();
    const granted = requestedPermission.granted;
    const blocked = !granted && requestedPermission.canAskAgain === false;

    setHasLocationPermission(granted);
    setPermissionStatus(granted ? "granted" : blocked ? "blocked" : "denied");
    if (granted) {
      await captureDeviceLocation();
    }
  };

  useEffect(() => {
    void requestLocationAccess();
  }, []);

  const resolvedStartOrigin: Waypoint =
    capturedLocation != null
      ? {
          latitude: capturedLocation.latitude,
          longitude: capturedLocation.longitude,
          name: "Current Location",
        }
      : START_ORIGIN;

  const FloatingButtons = ({
    stopNavigation,
    emitAction,
  }: FloatingButtonsRenderContext) => (
    <MapboxNavigationFloatingButtonsStack>
      <MapboxNavigationFloatingButton
        accessibilityLabel="Open chat sheet"
        onPress={() => {
          setSupportSheetOpen(true);
          emitAction("chat");
        }}
      >
        CHAT
      </MapboxNavigationFloatingButton>
      <MapboxNavigationFloatingButton
        accessibilityLabel="Log custom action"
        onPress={() => {
          emitAction("custom-help");
        }}
      >
        HELP
      </MapboxNavigationFloatingButton>
      <MapboxNavigationFloatingButton
        accessibilityLabel="Stop navigation"
        onPress={() => {
          void stopNavigation();
        }}
      >
        END
      </MapboxNavigationFloatingButton>
    </MapboxNavigationFloatingButtonsStack>
  );

  return (
    <SafeAreaView edges={["top"]} style={styles.screen}>
      <StatusBar barStyle="light-content" />
      <MapboxNavigationView
        enabled={hasLocationPermission}
        style={StyleSheet.absoluteFill}
        startOrigin={resolvedStartOrigin}
        destination={DESTINATION}
        shouldSimulateRoute
        mute
        nativeFloatingButtons={{
          showCameraModeButton: false,
          showCompassButton: false,
        }}
        floatingButtonsComponent={FloatingButtons}
        onLocationChange={(location) => {
          setCapturedLocation(location);
        }}
        onError={(error) => {
          setLastError(`${error.code}: ${error.message}`);
        }}
        onOverlayBottomSheetActionPress={(event) => {
          setLastAction(`${event.source}:${event.actionId}`);
        }}
      />

      {!hasLocationPermission ? (
        <View style={styles.permissionOverlay}>
          <Text style={styles.permissionTitle}>Location Permission Needed</Text>
          <Text style={styles.permissionText}>
            Grant location access to start embedded navigation and capture
            device coordinates in this test app.
          </Text>
          <Pressable
            onPress={() => {
              if (permissionStatus === "blocked") {
                void Linking.openSettings();
                return;
              }
              void requestLocationAccess();
            }}
            style={styles.permissionButton}
          >
            <Text style={styles.permissionButtonLabel}>
              {permissionStatus === "requesting"
                ? "Requesting..."
                : permissionStatus === "blocked"
                  ? "Open Settings"
                  : "Grant Permission"}
            </Text>
          </Pressable>
        </View>
      ) : null}

      {supportSheetOpen ? (
        <View pointerEvents="box-none" style={styles.supportOverlay}>
          <Pressable
            style={styles.supportBackdrop}
            onPress={() => setSupportSheetOpen(false)}
          />
          <View style={styles.supportSheet}>
            <Text style={styles.supportTitle}>App-Owned Sheet</Text>
            <Text style={styles.supportText}>
              This is React UI from the example app, separate from the package
              bottom sheet.
            </Text>
            <Pressable
              onPress={() => setSupportSheetOpen(false)}
              style={styles.supportButton}
            >
              <Text style={styles.supportButtonLabel}>Close</Text>
            </Pressable>
          </View>
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: "#020617",
  },
  header: {
    paddingHorizontal: 18,
    paddingTop: 12,
    paddingBottom: 14,
    gap: 6,
  },
  title: {
    color: "#f8fafc",
    fontSize: 26,
    fontWeight: "800",
  },
  subtitle: {
    color: "#bfdbfe",
    fontSize: 14,
    lineHeight: 20,
  },
  status: {
    color: "#93c5fd",
    fontSize: 12,
    fontWeight: "600",
  },
  errorText: {
    color: "#fca5a5",
    fontSize: 12,
    fontWeight: "600",
  },
  mapFrame: {
    flex: 1,
    marginHorizontal: 14,
    marginBottom: 14,
    borderRadius: 24,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: "rgba(148,163,184,0.2)",
    backgroundColor: "#020617",
  },
  permissionOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 24,
    backgroundColor: "rgba(2,6,23,0.76)",
    gap: 12,
  },
  permissionTitle: {
    color: "#f8fafc",
    fontSize: 20,
    fontWeight: "800",
    textAlign: "center",
  },
  permissionText: {
    color: "#cbd5e1",
    fontSize: 14,
    lineHeight: 20,
    textAlign: "center",
  },
  permissionButton: {
    borderRadius: 999,
    backgroundColor: "#2563eb",
    paddingHorizontal: 18,
    paddingVertical: 12,
  },
  permissionButtonLabel: {
    color: "#ffffff",
    fontSize: 13,
    fontWeight: "800",
  },
  supportOverlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: "flex-end",
  },
  supportBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(2,6,23,0.45)",
  },
  supportSheet: {
    margin: 14,
    borderRadius: 24,
    backgroundColor: "#ffffff",
    padding: 18,
    gap: 10,
  },
  supportTitle: {
    color: "#0f172a",
    fontSize: 18,
    fontWeight: "800",
  },
  supportText: {
    color: "#334155",
    fontSize: 14,
    lineHeight: 20,
  },
  supportButton: {
    alignSelf: "flex-start",
    borderRadius: 999,
    backgroundColor: "#0f172a",
    paddingHorizontal: 16,
    paddingVertical: 10,
  },
  supportButtonLabel: {
    color: "#ffffff",
    fontSize: 13,
    fontWeight: "700",
  },
});

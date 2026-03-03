import React, { useEffect, useMemo, useState } from "react";
import {
  PermissionsAndroid,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import {
  MapboxNavigationView,
  resumeCameraFollowing,
  type BannerInstruction,
  type CameraFollowingState,
  type RouteProgress,
  type Waypoint,
} from "@atomiqlab/react-native-mapbox-navigation";

const START: Waypoint = {
  latitude: 37.7749,
  longitude: -122.4194,
  name: "Start",
};
const DEST: Waypoint = {
  latitude: 37.7847,
  longitude: -122.4073,
  name: "Downtown",
};

function formatInstruction(instruction?: BannerInstruction): string {
  const primary = instruction?.primaryText?.trim();
  const secondary = instruction?.secondaryText?.trim();
  if (primary && secondary) return `${primary} • ${secondary}`;
  return primary || secondary || "Waiting for banner…";
}

function formatRemaining(progress?: RouteProgress): string {
  if (!progress) return "Waiting for progress…";
  const meters = Math.round(progress.distanceRemaining);
  const minutes = Math.max(0, Math.round(progress.durationRemaining / 60));
  const pct = Math.round((progress.fractionTraveled ?? 0) * 100);
  return `${meters}m • ${minutes} min • ${pct}%`;
}

export default function EmbeddedNavigationTestScreen() {
  const [permissionGranted, setPermissionGranted] = useState(
    Platform.OS !== "android",
  );
  const [enabled, setEnabled] = useState(Platform.OS !== "android");
  const [useCustomSheet, setUseCustomSheet] = useState(true);
  const [lastError, setLastError] = useState<string>("");
  const [routePointCount, setRoutePointCount] = useState<number>(0);
  const [cameraNotFollowing, setCameraNotFollowing] = useState(false);

  useEffect(() => {
    if (Platform.OS !== "android") {
      return;
    }
    PermissionsAndroid.check(
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
    )
      .then((has) => {
        setPermissionGranted(has);
        setEnabled(has);
      })
      .catch(() => {});
  }, []);

  const requestPermission = async () => {
    if (Platform.OS !== "android") {
      setPermissionGranted(true);
      setEnabled(true);
      return;
    }
    const res = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
    );
    const granted = res === PermissionsAndroid.RESULTS.GRANTED;
    setPermissionGranted(granted);
    if (granted) setEnabled(true);
  };

  const bottomSheet = useMemo(
    () => ({
      enabled: true,
      mode: "overlay" as const,
      initialState:
        Platform.OS === "ios" ? ("hidden" as const) : ("collapsed" as const),
      revealGestureHotzoneHeight: 120,
      revealGestureRightExclusionWidth: 0,
      collapsedHeight: 124,
      expandedHeight: 340,
      collapsedBottomOffset: Platform.OS === "android" ? 26 : 0,
      showHandle: true,
      enableTapToToggle: true,
      colorMode: "dark" as const,
      showDefaultContent: true,
      defaultManeuverTitle: "Next turn",
      defaultTripProgressTitle: "ETA & progress",
      showCurrentStreet: true,
      showRemainingDistance: true,
      showRemainingDuration: true,
      showETA: true,
      showCompletionPercent: true,
      builtInQuickActions: ["overview", "recenter", "toggleMute", "stop"],
    }),
    [],
  );

  return (
    <View style={styles.container}>
      <MapboxNavigationView
        enabled={enabled && permissionGranted}
        style={StyleSheet.absoluteFill}
        startOrigin={START}
        showCancelButton
        showsActionButtons
        showsContinuousAlternatives
        showsManeuverView
        showsSpeedLimits
        showsTripProgress
        showsWayNameLabel
        destination={DEST}
        shouldSimulateRoute
        bottomSheet={bottomSheet}
        onRouteChange={(e) => setRoutePointCount(e.coordinates.length)}
        onCameraFollowingStateChange={(e: CameraFollowingState) =>
          setCameraNotFollowing(!!e.isCameraNotFollowing)
        }
        onError={(e) => setLastError(`${e.code}: ${e.message}`)}
        renderBottomSheet={
          useCustomSheet
            ? ({
                state,
                show,
                hide,
                toggle,
                stopNavigation,
                bannerInstruction,
                routeProgress,
              }) => (
                <View style={styles.sheet}>
                  <View style={styles.sheetHeader}>
                    <Text style={styles.sheetTitle}>Embedded Sheet</Text>
                    <Text style={styles.sheetBadge}>{state.toUpperCase()}</Text>
                  </View>
                  <Text style={styles.sheetPrimary} numberOfLines={2}>
                    {formatInstruction(bannerInstruction)}
                  </Text>
                  <Text style={styles.sheetSecondary} numberOfLines={1}>
                    {formatRemaining(routeProgress)}
                  </Text>
                  <View style={styles.sheetRow}>
                    <Pressable
                      style={styles.sheetBtn}
                      onPress={() => show("collapsed")}
                    >
                      <Text style={styles.sheetBtnText}>Show</Text>
                    </Pressable>
                    <Pressable style={styles.sheetBtn} onPress={toggle}>
                      <Text style={styles.sheetBtnText}>Toggle</Text>
                    </Pressable>
                    <Pressable
                      style={styles.sheetBtnSecondary}
                      onPress={stopNavigation}
                    >
                      <Text style={styles.sheetBtnText}>Stop</Text>
                    </Pressable>
                  </View>
                </View>
              )
            : undefined
        }
      >
        <View pointerEvents="box-none" style={styles.overlayTop}>
          <View style={styles.card}>
            <Text style={styles.title}>Embedded Navigation Test</Text>
            <Text style={styles.meta}>
              permissionGranted: {String(permissionGranted)}
            </Text>
            <Text style={styles.meta}>enabled: {String(enabled)}</Text>
            <Text style={styles.meta}>route points: {routePointCount}</Text>
            <Text style={styles.meta}>
              cameraNotFollowing: {String(cameraNotFollowing)}
            </Text>
            <Text style={styles.meta}>
              custom sheet: {String(useCustomSheet)}
            </Text>
            {lastError ? (
              <Text style={styles.error}>error: {lastError}</Text>
            ) : null}
            {!permissionGranted ? (
              <Pressable style={styles.btn} onPress={requestPermission}>
                <Text style={styles.btnText}>Grant Location Permission</Text>
              </Pressable>
            ) : (
              <View style={styles.row}>
                <Pressable
                  style={styles.btn}
                  onPress={() => setEnabled((v) => !v)}
                >
                  <Text style={styles.btnText}>
                    {enabled ? "Disable" : "Enable"}
                  </Text>
                </Pressable>
                <Pressable
                  style={styles.btn}
                  onPress={() => setUseCustomSheet((v) => !v)}
                >
                  <Text style={styles.btnText}>
                    {useCustomSheet
                      ? "Disable Custom Sheet"
                      : "Enable Custom Sheet"}
                  </Text>
                </Pressable>
                <Pressable
                  style={styles.btn}
                  onPress={() => resumeCameraFollowing().catch(() => {})}
                >
                  <Text style={styles.btnText}>Resume Camera</Text>
                </Pressable>
              </View>
            )}
            <Text style={styles.hint}>
              Swipe/tap the handle to expand. This screen tests simplified
              bottomSheet theming via `colorMode` and onRouteChange payloads.
            </Text>
          </View>
        </View>
      </MapboxNavigationView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#0b1020" },
  overlayTop: {
    ...StyleSheet.absoluteFillObject,
    padding: 12,
    alignItems: "stretch",
  },
  card: {
    backgroundColor: "rgba(15, 23, 42, 0.9)",
    borderRadius: 14,
    padding: 12,
    gap: 6,
  },
  title: { color: "#fff", fontSize: 16, fontWeight: "800" },
  meta: { color: "#9cc7ff", fontSize: 12, fontWeight: "600" },
  hint: { color: "rgba(255,255,255,0.75)", fontSize: 11, fontWeight: "500" },
  error: { color: "#fecaca", fontSize: 11, fontWeight: "700" },
  row: { flexDirection: "row", gap: 8, flexWrap: "wrap" },
  btn: {
    minWidth: 120,
    backgroundColor: "#2563eb",
    borderRadius: 10,
    paddingVertical: 10,
    paddingHorizontal: 10,
    alignItems: "center",
  },
  btnText: { color: "#fff", fontSize: 12, fontWeight: "800" },
  sheet: { flex: 1, gap: 8 },
  sheetHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  sheetTitle: { color: "#fff", fontSize: 14, fontWeight: "800" },
  sheetBadge: {
    color: "#fff",
    fontSize: 10,
    fontWeight: "800",
    backgroundColor: "#2563eb",
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 999,
  },
  sheetPrimary: { color: "#fff", fontSize: 14, fontWeight: "800" },
  sheetSecondary: {
    color: "rgba(255,255,255,0.8)",
    fontSize: 12,
    fontWeight: "600",
  },
  sheetRow: { flexDirection: "row", gap: 8 },
  sheetBtn: {
    flex: 1,
    backgroundColor: "#2563eb",
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: "center",
  },
  sheetBtnSecondary: {
    flex: 1,
    backgroundColor: "#0f172a",
    borderWidth: 1,
    borderColor: "rgba(255,255,255,0.18)",
    borderRadius: 10,
    paddingVertical: 10,
    alignItems: "center",
  },
  sheetBtnText: { color: "#fff", fontSize: 12, fontWeight: "800" },
});

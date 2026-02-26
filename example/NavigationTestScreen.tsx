import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import {
  addArriveListener,
  addBannerInstructionListener,
  addBottomSheetActionPressListener,
  addCancelNavigationListener,
  addDestinationChangedListener,
  addDestinationPreviewListener,
  addErrorListener,
  addJourneyDataChangeListener,
  addLocationChangeListener,
  addRouteProgressChangeListener,
  getNavigationSettings,
  isNavigating,
  setDistanceUnit,
  setLanguage,
  setMuted,
  setVoiceVolume,
  startNavigation,
  stopNavigation,
  type BottomSheetOptions,
  type Waypoint,
} from "@atomiqlab/react-native-mapbox-navigation";

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
const WAYPOINTS: Waypoint[] = [
  { latitude: 37.7793, longitude: -122.4129, name: "WP1" },
];

function coord(value: unknown): string {
  const n = Number(value);
  return Number.isFinite(n) ? n.toFixed(5) : "n/a";
}

export default function NavigationTestScreen() {
  const [logs, setLogs] = useState<string[]>([]);
  const [navigating, setNavigating] = useState(false);

  const [simulate, setSimulate] = useState(true);
  const [mute, setMuteState] = useState(false);
  const [unit, setUnit] = useState<"metric" | "imperial">("metric");
  const [language, setLanguageState] = useState("en");
  const [voiceVolume, setVoiceVolumeState] = useState("1");

  const [showsTripProgress, setShowsTripProgress] = useState(true);
  const [showsManeuverView, setShowsManeuverView] = useState(true);
  const [showsActionButtons, setShowsActionButtons] = useState(true);
  const [bottomSheetEnabled, setBottomSheetEnabled] = useState(true);
  const [routeAlternatives, setRouteAlternatives] = useState(true);
  const [showCurrentStreet, setShowCurrentStreet] = useState(true);
  const [showRemainingDistance, setShowRemainingDistance] = useState(true);
  const [showRemainingDuration, setShowRemainingDuration] = useState(true);
  const [showETA, setShowETA] = useState(true);
  const [showCompletionPercent, setShowCompletionPercent] = useState(true);
  const [startInFlight, setStartInFlight] = useState(false);
  const lastLogRef = useRef({
    location: 0,
    progress: 0,
    journey: 0,
    conflict: 0,
  });

  const pushLog = (line: string) => {
    setLogs((prev) =>
      [`${new Date().toLocaleTimeString()}  ${line}`, ...prev].slice(0, 160),
    );
  };

  useEffect(() => {
    const s1 = addLocationChangeListener((e) => {
      const now = Date.now();
      if (now - lastLogRef.current.location < 1200) {
        return;
      }
      lastLogRef.current.location = now;
      pushLog(`location: ${coord(e.latitude)}, ${coord(e.longitude)}`);
    });
    const s2 = addRouteProgressChangeListener((e) => {
      const now = Date.now();
      if (now - lastLogRef.current.progress < 1200) {
        return;
      }
      lastLogRef.current.progress = now;
      const pct = Number(((e?.fractionTraveled ?? 0) * 100).toFixed(1));
      pushLog(
        `progress: ${pct}% rem=${Math.round(e?.distanceRemaining ?? 0)}m`,
      );
    });
    const s3 = addJourneyDataChangeListener((e) => {
      const now = Date.now();
      if (now - lastLogRef.current.journey < 1200) {
        return;
      }
      lastLogRef.current.journey = now;
      if (e?.currentStreet) {
        pushLog(`journey: street=${e.currentStreet}`);
      }
    });
    const s4 = addBannerInstructionListener((e) =>
      pushLog(`banner: ${e?.primaryText ?? "n/a"}`),
    );
    const s5 = addArriveListener((e) =>
      pushLog(`arrive: ${e?.name ?? "destination"}`),
    );
    const s6 = addCancelNavigationListener(() => pushLog("cancelled"));
    const s7 = addDestinationPreviewListener(() =>
      pushLog("destination preview (android)"),
    );
    const s8 = addDestinationChangedListener((e) =>
      pushLog(
        `destination changed: ${coord(e.latitude)}, ${coord(e.longitude)}`,
      ),
    );
    const s9 = addBottomSheetActionPressListener((e) =>
      pushLog(`sheet action: ${e?.actionId}`),
    );
    const s10 = addErrorListener((e) =>
      {
        if (e?.code === "NAVIGATION_SESSION_CONFLICT") {
          const now = Date.now();
          if (now - lastLogRef.current.conflict < 2500) {
            return;
          }
          lastLogRef.current.conflict = now;
        }
        pushLog(`error: [${e?.code ?? "UNKNOWN"}] ${e?.message ?? "unknown"}`);
      },
    );

    (async () => {
      try {
        setNavigating(await isNavigating());
      } catch {
        setNavigating(false);
      }
    })();

    return () => {
      s1.remove();
      s2.remove();
      s3.remove();
      s4.remove();
      s5.remove();
      s6.remove();
      s7.remove();
      s8.remove();
      s9.remove();
      s10.remove();
    };
  }, []);

  const canRun = useMemo(
    () => Platform.OS === "ios" || Platform.OS === "android",
    [],
  );
  if (!canRun) {
    return (
      <SafeAreaView style={styles.center}>
        <Text style={styles.title}>Run this on iOS/Android.</Text>
      </SafeAreaView>
    );
  }

  const fullScreenBottomSheet: BottomSheetOptions = {
    enabled: bottomSheetEnabled,
    mode: "customNative",
    initialState: "hidden",
    revealOnNativeBannerGesture: true,
    revealGestureHotzoneHeight: 100,
    revealGestureRightExclusionWidth: 80,
    collapsedHeight: 118,
    expandedHeight: 340,
    showsTripProgress,
    showsManeuverView,
    showsActionButtons,
    showCurrentStreet,
    showRemainingDistance,
    showRemainingDuration,
    showETA,
    showCompletionPercent,
    overlayLocationUpdateIntervalMs: 350,
    overlayProgressUpdateIntervalMs: 350,
    showHandle: true,
    enableTapToToggle: true,
    showDefaultContent: true,
    backgroundColor: "#0f172a",
    handleColor: "#93c5fd",
    primaryTextColor: "#ffffff",
    secondaryTextColor: "#bfdbfe",
    primaryTextFontSize: 16,
    primaryTextFontWeight: "700",
    secondaryTextFontSize: 13,
    secondaryTextFontWeight: "500",
    actionButtonBackgroundColor: "#2563eb",
    actionButtonTextColor: "#ffffff",
    actionButtonFontSize: 14,
    actionButtonFontWeight: "700",
    actionButtonCornerRadius: 12,
    actionButtonBorderColor: "#1d4ed8",
    actionButtonBorderWidth: 1,
    secondaryActionButtonBackgroundColor: "#1e293b",
    secondaryActionButtonTextColor: "#bfdbfe",
    actionButtonTitle: "End Navigation",
    secondaryActionButtonTitle: "Support",
    primaryActionButtonBehavior: "stopNavigation",
    secondaryActionButtonBehavior: "emitEvent",
    actionButtonsBottomPadding: 2,
    quickActions: [
      { id: "overview", label: "Overview", variant: "secondary" },
      { id: "recenter", label: "Recenter", variant: "ghost" },
    ],
    quickActionBackgroundColor: "#1d4ed8",
    quickActionTextColor: "#ffffff",
    quickActionSecondaryBackgroundColor: "#0f172a",
    quickActionSecondaryTextColor: "#bfdbfe",
    quickActionGhostTextColor: "#93c5fd",
    quickActionBorderColor: "#334155",
    quickActionBorderWidth: 1,
    quickActionCornerRadius: 12,
    quickActionFontWeight: "700",
    builtInQuickActions: ["overview", "recenter", "toggleMute", "stop"],
    headerTitle: "Trip",
    headerSubtitle: "Hidden until upward swipe from bottom-zone",
    headerBadgeText: Platform.OS.toUpperCase(),
    headerTitleFontSize: 16,
    headerTitleFontWeight: "700",
    headerSubtitleFontSize: 12,
    headerSubtitleFontWeight: "500",
    headerBadgeFontSize: 11,
    headerBadgeFontWeight: "700",
    headerBadgeCornerRadius: 10,
    headerBadgeBorderColor: "#1d4ed8",
    headerBadgeBorderWidth: 1,
  };

  const fullScreenOptions = {
    startOrigin: START,
    destination: DEST,
    waypoints: WAYPOINTS,
    shouldSimulateRoute: simulate,
    routeAlternatives,
    mute,
    voiceVolume: Math.max(0, Math.min(Number(voiceVolume) || 1, 1)),
    distanceUnit: unit,
    language: language.trim() || "en",
    bottomSheet: fullScreenBottomSheet,
    showsTripProgress,
    showsManeuverView,
    showsActionButtons,
    showsSpeedLimits: true,
    showsWayNameLabel: true,
  } as const;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>Navigation Full Feature Test</Text>
        <Text style={styles.status}>Platform: {Platform.OS}</Text>
        <Text style={styles.status}>
          Status: {navigating ? "Full-screen active" : "Idle"}
        </Text>
        <Text style={styles.status}>
          Embedded mode removed. This screen now tests full-screen navigation only.
        </Text>

        <View style={styles.row}>
          <Pressable
            style={styles.btn}
            onPress={async () => {
              if (startInFlight) {
                pushLog("startNavigation skipped: start already in progress");
                return;
              }
              try {
                if (await isNavigating()) {
                  pushLog("startNavigation skipped: a session is already active");
                  setNavigating(true);
                  return;
                }
                setStartInFlight(true);
                await startNavigation(fullScreenOptions);
                setNavigating(true);
                pushLog("startNavigation ok");
              } catch (e: any) {
                pushLog(`startNavigation failed: ${e?.message ?? "unknown"}`);
              } finally {
                setStartInFlight(false);
              }
            }}
          >
            <Text style={styles.btnText}>Start Full-screen</Text>
          </Pressable>
          <Pressable
            style={styles.btn}
            onPress={async () => {
              try {
                await stopNavigation();
                setNavigating(false);
                pushLog("stopNavigation ok");
              } catch (e: any) {
                pushLog(`stopNavigation failed: ${e?.message ?? "unknown"}`);
              }
            }}
          >
            <Text style={styles.btnText}>Stop Full-screen</Text>
          </Pressable>
        </View>

        <View style={styles.row}>
          <Pressable
            style={styles.btn}
            onPress={async () =>
              pushLog(`isNavigating: ${await isNavigating()}`)
            }
          >
            <Text style={styles.btnText}>isNavigating</Text>
          </Pressable>
          <Pressable
            style={styles.btn}
            onPress={async () =>
              pushLog(JSON.stringify(await getNavigationSettings()))
            }
          >
            <Text style={styles.btnText}>getNavigationSettings</Text>
          </Pressable>
        </View>

        <View style={styles.card}>
          <Row
            label="Simulate route"
            value={simulate}
            onValueChange={setSimulate}
          />
          <Row label="Mute" value={mute} onValueChange={setMuteState} />
          <Row
            label="Bottom sheet enabled"
            value={bottomSheetEnabled}
            onValueChange={setBottomSheetEnabled}
          />
          <Row
            label="Route alternatives"
            value={routeAlternatives}
            onValueChange={setRouteAlternatives}
          />
          <Row
            label="Trip progress"
            value={showsTripProgress}
            onValueChange={setShowsTripProgress}
          />
          <Row
            label="Maneuver view"
            value={showsManeuverView}
            onValueChange={setShowsManeuverView}
          />
          <Row
            label="Action buttons"
            value={showsActionButtons}
            onValueChange={setShowsActionButtons}
          />
          <Row
            label="Current street"
            value={showCurrentStreet}
            onValueChange={setShowCurrentStreet}
          />
          <Row
            label="Remaining distance"
            value={showRemainingDistance}
            onValueChange={setShowRemainingDistance}
          />
          <Row
            label="Remaining duration"
            value={showRemainingDuration}
            onValueChange={setShowRemainingDuration}
          />
          <Row label="ETA" value={showETA} onValueChange={setShowETA} />
          <Row
            label="Completion percent"
            value={showCompletionPercent}
            onValueChange={setShowCompletionPercent}
          />
        </View>

        <View style={styles.card}>
          <LabelInput
            label="Language"
            value={language}
            onChangeText={setLanguageState}
          />
          <LabelInput
            label="Voice volume (0..1)"
            value={voiceVolume}
            onChangeText={setVoiceVolumeState}
            keyboardType="decimal-pad"
          />
          <View style={styles.row}>
            <Pressable
              style={styles.btn}
              onPress={async () => {
                const next = !mute;
                await setMuted(next);
                setMuteState(next);
                pushLog(`setMuted(${next})`);
              }}
            >
              <Text style={styles.btnText}>setMuted</Text>
            </Pressable>
            <Pressable
              style={styles.btn}
              onPress={async () => {
                const vol = Math.max(0, Math.min(Number(voiceVolume) || 1, 1));
                await setVoiceVolume(vol);
                pushLog(`setVoiceVolume(${vol})`);
              }}
            >
              <Text style={styles.btnText}>setVoiceVolume</Text>
            </Pressable>
          </View>
          <View style={styles.row}>
            <Pressable
              style={styles.btn}
              onPress={async () => {
                await setDistanceUnit("metric");
                setUnit("metric");
                pushLog("setDistanceUnit(metric)");
              }}
            >
              <Text style={styles.btnText}>Metric</Text>
            </Pressable>
            <Pressable
              style={styles.btn}
              onPress={async () => {
                await setDistanceUnit("imperial");
                setUnit("imperial");
                pushLog("setDistanceUnit(imperial)");
              }}
            >
              <Text style={styles.btnText}>Imperial</Text>
            </Pressable>
            <Pressable
              style={styles.btn}
              onPress={async () => {
                await setLanguage(language.trim() || "en");
                pushLog(`setLanguage(${language.trim() || "en"})`);
              }}
            >
              <Text style={styles.btnText}>setLanguage</Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.logTitle}>Logs</Text>
          {logs.map((line, i) => (
            <Text key={`${line}-${i}`} style={styles.logText}>
              {line}
            </Text>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function Row({
  label,
  value,
  onValueChange,
}: {
  label: string;
  value: boolean;
  onValueChange: (v: boolean) => void;
}) {
  return (
    <View style={styles.rowBetween}>
      <Text style={styles.label}>{label}</Text>
      <Switch value={value} onValueChange={onValueChange} />
    </View>
  );
}

function LabelInput({
  label,
  value,
  onChangeText,
  keyboardType,
}: {
  label: string;
  value: string;
  onChangeText: (v: string) => void;
  keyboardType?: "default" | "decimal-pad";
}) {
  return (
    <View style={styles.inputWrap}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        style={styles.input}
        value={value}
        onChangeText={onChangeText}
        keyboardType={keyboardType}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: "#0b1020" },
  center: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#0b1020",
  },
  content: { padding: 12, gap: 10, paddingBottom: 28 },
  title: { color: "#fff", fontSize: 20, fontWeight: "800" },
  status: { color: "#9cc7ff", fontSize: 12 },
  card: { backgroundColor: "#131a2d", borderRadius: 10, padding: 10, gap: 8 },
  row: { flexDirection: "row", gap: 8, alignItems: "center" },
  rowBetween: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  btn: {
    flex: 1,
    backgroundColor: "#2563eb",
    borderRadius: 10,
    paddingVertical: 10,
    paddingHorizontal: 10,
    alignItems: "center",
  },
  btnText: { color: "#fff", fontSize: 12, fontWeight: "700" },
  label: { color: "#dbeafe", fontSize: 12, fontWeight: "600" },
  inputWrap: { gap: 4 },
  input: {
    backgroundColor: "#0e1426",
    borderColor: "#2b3a5d",
    borderWidth: 1,
    borderRadius: 8,
    color: "#fff",
    paddingHorizontal: 10,
    paddingVertical: 8,
    fontSize: 12,
  },
  logTitle: { color: "#93c5fd", fontSize: 12, fontWeight: "700" },
  logText: { color: "#e5e7eb", fontSize: 11 },
});

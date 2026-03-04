import * as React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import {
  MapboxNavigationView,
  type BannerInstruction,
  type RouteProgress,
  type Waypoint,
} from "@atomiqlab/react-native-mapbox-navigation";

const START: Waypoint = {
  latitude: -1.94995,
  longitude: 30.05885,
  name: "Kigali Convention Centre",
};

const DESTINATION: Waypoint = {
  latitude: -1.94407,
  longitude: 30.06189,
  name: "Kigali Heights",
};

function formatDistance(meters?: number) {
  if (!Number.isFinite(meters ?? NaN)) {
    return "Route";
  }
  if ((meters as number) >= 1000) {
    return `${((meters as number) / 1000).toFixed(1)} km left`;
  }
  return `${Math.round(meters as number)} m left`;
}

function renderInstruction(
  instruction?: BannerInstruction,
  progress?: RouteProgress,
) {
  if (instruction?.primaryText) {
    return instruction.primaryText;
  }
  if (progress?.distanceRemaining != null) {
    return `Continue for ${formatDistance(progress.distanceRemaining)}`;
  }
  return "Waiting for route guidance...";
}

export default function HomeScreen() {
  const [appSheetOpen, setAppSheetOpen] = React.useState(false);
  const [lastAction, setLastAction] = React.useState<string | null>(null);
  const [lastError, setLastError] = React.useState<string | null>(null);

  return (
    <View style={styles.container}>
      <MapboxNavigationView
        enabled
        style={styles.map}
        startOrigin={START}
        destination={DESTINATION}
        shouldSimulateRoute
        uiTheme="light"
        bottomSheet={{
          enabled: true,
          mode: "overlay",
          initialState: "hidden",
          collapsedHeight: 124,
          expandedHeight: 280,
          showDefaultContent: false,
          builtInQuickActions: ["toggleMute", "stop"],
        }}
        floatingButtonsContainerStyle={styles.floatingButtonsAnchor}
        onError={(error) => setLastError(`${error.code}: ${error.message}`)}
        onOverlayBottomSheetActionPress={({ actionId, source }) =>
          setLastAction(`${source}:${actionId}`)
        }
        renderFloatingButtons={({ expand, routeProgress, emitAction }) => (
          <View style={styles.floatingButtonsStack}>
            <Pressable
              onPress={() => {
                setAppSheetOpen(true);
                emitAction("app-sheet");
              }}
              style={[styles.floatingButton, styles.primaryFloatingButton]}
            >
              <Text style={styles.primaryFloatingButtonText}>App Sheet</Text>
            </Pressable>
            <Pressable
              onPress={expand}
              style={[styles.floatingButton, styles.secondaryFloatingButton]}
            >
              <Text style={styles.secondaryFloatingButtonText}>
                {formatDistance(routeProgress?.distanceRemaining)}
              </Text>
            </Pressable>
          </View>
        )}
        renderBottomSheet={({ bannerInstruction, routeProgress, collapse }) => (
          <View style={styles.routeSheet}>
            <Text style={styles.sheetEyebrow}>Package Overlay</Text>
            <Text style={styles.sheetTitle}>
              {renderInstruction(bannerInstruction, routeProgress)}
            </Text>
            <Text style={styles.sheetBody}>
              {routeProgress?.durationRemaining != null
                ? `${Math.round(routeProgress.durationRemaining / 60)} min remaining`
                : "The floating Route button opens this package-owned sheet."}
            </Text>
            <Pressable onPress={collapse} style={styles.sheetButton}>
              <Text style={styles.sheetButtonText}>Hide Route Panel</Text>
            </Pressable>
          </View>
        )}
      />

      {lastError ? (
        <View pointerEvents="none" style={styles.bannerWrap}>
          <View style={styles.errorBanner}>
            <Text style={styles.errorBannerText}>{lastError}</Text>
          </View>
        </View>
      ) : null}

      {lastAction ? (
        <View pointerEvents="none" style={styles.actionChipWrap}>
          <View style={styles.actionChip}>
            <Text style={styles.actionChipText}>{lastAction}</Text>
          </View>
        </View>
      ) : null}

      {appSheetOpen ? (
        <View style={styles.appSheetOverlay}>
          <Pressable
            onPress={() => setAppSheetOpen(false)}
            style={styles.appSheetBackdrop}
          />
          <View style={styles.appSheet}>
            <Text style={styles.appSheetEyebrow}>App-Owned Sheet</Text>
            <Text style={styles.appSheetTitle}>
              This button lives in JS, not inside the native module.
            </Text>
            <Text style={styles.appSheetBody}>
              Use this pattern for feature toggles, business actions, or any
              bottom sheet flow that belongs to your app.
            </Text>
            <Pressable
              onPress={() => setAppSheetOpen(false)}
              style={styles.appSheetClose}
            >
              <Text style={styles.appSheetCloseText}>Close</Text>
            </Pressable>
          </View>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#0f172a",
  },
  map: {
    flex: 1,
  },
  floatingButtonsAnchor: {
    top: 88,
    right: 14,
  },
  floatingButtonsStack: {
    alignItems: "flex-end",
    gap: 10,
  },
  floatingButton: {
    minWidth: 132,
    borderRadius: 999,
    paddingHorizontal: 16,
    paddingVertical: 12,
    shadowColor: "#020617",
    shadowOpacity: 0.18,
    shadowRadius: 12,
    shadowOffset: {
      width: 0,
      height: 6,
    },
    elevation: 4,
  },
  primaryFloatingButton: {
    backgroundColor: "#f97316",
  },
  secondaryFloatingButton: {
    backgroundColor: "rgba(255,255,255,0.96)",
  },
  primaryFloatingButtonText: {
    color: "#ffffff",
    fontSize: 14,
    fontWeight: "700",
    textAlign: "center",
  },
  secondaryFloatingButtonText: {
    color: "#0f172a",
    fontSize: 13,
    fontWeight: "700",
    textAlign: "center",
  },
  routeSheet: {
    gap: 10,
    paddingTop: 8,
  },
  sheetEyebrow: {
    color: "#475569",
    fontSize: 11,
    fontWeight: "700",
    textTransform: "uppercase",
  },
  sheetTitle: {
    color: "#0f172a",
    fontSize: 18,
    fontWeight: "800",
  },
  sheetBody: {
    color: "#334155",
    fontSize: 14,
    lineHeight: 20,
  },
  sheetButton: {
    alignSelf: "flex-start",
    borderRadius: 999,
    backgroundColor: "#0f172a",
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  sheetButtonText: {
    color: "#ffffff",
    fontSize: 13,
    fontWeight: "700",
  },
  bannerWrap: {
    position: "absolute",
    top: 18,
    left: 12,
    right: 12,
    alignItems: "center",
  },
  errorBanner: {
    maxWidth: "100%",
    borderRadius: 14,
    backgroundColor: "rgba(127, 29, 29, 0.94)",
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  errorBannerText: {
    color: "#fee2e2",
    fontSize: 12,
    fontWeight: "600",
    textAlign: "center",
  },
  actionChipWrap: {
    position: "absolute",
    top: 18,
    right: 12,
  },
  actionChip: {
    borderRadius: 999,
    backgroundColor: "rgba(15, 23, 42, 0.88)",
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  actionChipText: {
    color: "#e2e8f0",
    fontSize: 12,
    fontWeight: "700",
  },
  appSheetOverlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: "flex-end",
  },
  appSheetBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(15, 23, 42, 0.36)",
  },
  appSheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    backgroundColor: "#ffffff",
    paddingHorizontal: 20,
    paddingTop: 18,
    paddingBottom: 28,
    gap: 10,
  },
  appSheetEyebrow: {
    color: "#c2410c",
    fontSize: 11,
    fontWeight: "800",
    textTransform: "uppercase",
  },
  appSheetTitle: {
    color: "#111827",
    fontSize: 20,
    fontWeight: "800",
  },
  appSheetBody: {
    color: "#4b5563",
    fontSize: 15,
    lineHeight: 22,
  },
  appSheetClose: {
    alignSelf: "flex-start",
    borderRadius: 999,
    backgroundColor: "#ea580c",
    paddingHorizontal: 16,
    paddingVertical: 11,
  },
  appSheetCloseText: {
    color: "#ffffff",
    fontSize: 13,
    fontWeight: "700",
  },
});

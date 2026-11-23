import ExpoModulesCore
import Foundation
import MapboxNavigationCore

public class MapboxNavigationModule: Module {
  static let moduleEventNames = [
    "onLocationChange",
    "onRouteProgressChange",
    "onJourneyDataChange",
    "onRouteChange",
    "onCameraFollowingStateChange",
    "onBannerInstruction",
    "onArrive",
    "onWaypointArrive",
    "onOffRoute",
    "onCancelNavigation",
    "onError",
    "onBottomSheetActionPress"
  ]

  private var mute = false
  private var voiceVolume: Double = 1
  private var distanceUnit = "metric"
  private var language = Locale.preferredLanguages.first ?? "en"

  public func definition() -> ModuleDefinition {
    Name("MapboxNavigationModule")

    Events(MapboxNavigationModule.moduleEventNames)

    OnCreate {
      MapboxNavigationEventBridge.shared.setEmitter { [weak self] eventName, payload in
        self?.sendEvent(eventName, payload)
      }
    }

    OnDestroy {
      MapboxNavigationEventBridge.shared.clearEmitter()
    }

    OnStartObserving("onLocationChange") {
      MapboxNavigationEventBridge.shared.startObserving("onLocationChange")
    }
    OnStopObserving("onLocationChange") {
      MapboxNavigationEventBridge.shared.stopObserving("onLocationChange")
    }
    OnStartObserving("onRouteProgressChange") {
      MapboxNavigationEventBridge.shared.startObserving("onRouteProgressChange")
    }
    OnStopObserving("onRouteProgressChange") {
      MapboxNavigationEventBridge.shared.stopObserving("onRouteProgressChange")
    }
    OnStartObserving("onJourneyDataChange") {
      MapboxNavigationEventBridge.shared.startObserving("onJourneyDataChange")
    }
    OnStopObserving("onJourneyDataChange") {
      MapboxNavigationEventBridge.shared.stopObserving("onJourneyDataChange")
    }
    OnStartObserving("onRouteChange") {
      MapboxNavigationEventBridge.shared.startObserving("onRouteChange")
    }
    OnStopObserving("onRouteChange") {
      MapboxNavigationEventBridge.shared.stopObserving("onRouteChange")
    }
    OnStartObserving("onCameraFollowingStateChange") {
      MapboxNavigationEventBridge.shared.startObserving("onCameraFollowingStateChange")
    }
    OnStopObserving("onCameraFollowingStateChange") {
      MapboxNavigationEventBridge.shared.stopObserving("onCameraFollowingStateChange")
    }
    OnStartObserving("onBannerInstruction") {
      MapboxNavigationEventBridge.shared.startObserving("onBannerInstruction")
    }
    OnStopObserving("onBannerInstruction") {
      MapboxNavigationEventBridge.shared.stopObserving("onBannerInstruction")
    }
    OnStartObserving("onArrive") {
      MapboxNavigationEventBridge.shared.startObserving("onArrive")
    }
    OnStopObserving("onArrive") {
      MapboxNavigationEventBridge.shared.stopObserving("onArrive")
    }
    OnStartObserving("onWaypointArrive") {
      MapboxNavigationEventBridge.shared.startObserving("onWaypointArrive")
    }
    OnStopObserving("onWaypointArrive") {
      MapboxNavigationEventBridge.shared.stopObserving("onWaypointArrive")
    }
    OnStartObserving("onOffRoute") {
      MapboxNavigationEventBridge.shared.startObserving("onOffRoute")
    }
    OnStopObserving("onOffRoute") {
      MapboxNavigationEventBridge.shared.stopObserving("onOffRoute")
    }
    OnStartObserving("onCancelNavigation") {
      MapboxNavigationEventBridge.shared.startObserving("onCancelNavigation")
    }
    OnStopObserving("onCancelNavigation") {
      MapboxNavigationEventBridge.shared.stopObserving("onCancelNavigation")
    }
    OnStartObserving("onError") {
      MapboxNavigationEventBridge.shared.startObserving("onError")
    }
    OnStopObserving("onError") {
      MapboxNavigationEventBridge.shared.stopObserving("onError")
    }
    OnStartObserving("onBottomSheetActionPress") {
      MapboxNavigationEventBridge.shared.startObserving("onBottomSheetActionPress")
    }
    OnStopObserving("onBottomSheetActionPress") {
      MapboxNavigationEventBridge.shared.stopObserving("onBottomSheetActionPress")
    }

    AsyncFunction("setMuted") { (muted: Bool, promise: Promise) in
      self.mute = muted
      DispatchQueue.main.async {
        MainActor.assumeIsolated {
          MapboxNavigationSession.shared.setMuted(muted)
        }
        promise.resolve(nil)
      }
    }

    AsyncFunction("setVoiceVolume") { (volume: Double, promise: Promise) in
      let clamped = max(0, min(volume, 1))
      self.voiceVolume = clamped
      DispatchQueue.main.async {
        MainActor.assumeIsolated {
          MapboxNavigationSession.shared.setVoiceVolume(clamped)
        }
        promise.resolve(nil)
      }
    }

    AsyncFunction("setDistanceUnit") { (unit: String, promise: Promise) in
      let normalized = unit.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
      let imperial = normalized == "imperial"
      self.distanceUnit = imperial ? "imperial" : "metric"
      DispatchQueue.main.async {
        MainActor.assumeIsolated {
          MapboxNavigationSession.shared.setDistanceUnit(imperial ? "imperial" : "metric")
        }
        promise.resolve(nil)
      }
    }

    AsyncFunction("setLanguage") { (language: String, promise: Promise) in
      let trimmed = language.trimmingCharacters(in: .whitespacesAndNewlines)
      if !trimmed.isEmpty {
        self.language = trimmed
      }
      DispatchQueue.main.async {
        if !trimmed.isEmpty {
          MainActor.assumeIsolated {
            MapboxNavigationSession.shared.setLanguage(trimmed)
          }
        }
        promise.resolve(nil)
      }
    }

    AsyncFunction("getNavigationSettings") { (promise: Promise) in
      DispatchQueue.main.async {
        let isFollowing = NavigationSessionRegistry.shared.isCurrentCameraFollowing()
        let (muted, volume, unit) = MainActor.assumeIsolated {
          let session = MapboxNavigationSession.shared
          return (session.isMuted, session.voiceVolume, session.distanceUnit)
        }
        promise.resolve([
          "isNavigating": NavigationSessionRegistry.shared.isSessionActive(),
          "isCameraFollowing": isFollowing,
          "isCameraNotFollowing": !isFollowing,
          "mute": muted,
          "voiceVolume": volume,
          "distanceUnit": unit,
          "language": self.language
        ])
      }
    }

    AsyncFunction("stopNavigation") { (promise: Promise) in
      let stopped =
        NavigationSessionRegistry.shared.requestStopCurrent() ||
        MapboxNavigationView.requestStopActiveInstance()
      promise.resolve(stopped)
    }

    AsyncFunction("resumeCameraFollowing") { (promise: Promise) in
      let resumed = NavigationSessionRegistry.shared.requestResumeCameraFollowingCurrent()
      promise.resolve(resumed)
    }

    AsyncFunction("advanceToNextWaypoint") { (promise: Promise) in
      let advanced = NavigationSessionRegistry.shared.requestAdvanceLegCurrent()
      promise.resolve(advanced)
    }

    View(MapboxNavigationView.self) {
      Events(
        "onLocationChange",
        "onRouteProgressChange",
        "onJourneyDataChange",
        "onRouteChange",
        "onCameraFollowingStateChange",
        "onBannerInstruction",
        "onArrive",
        "onWaypointArrive",
        "onOffRoute",
        "onCancelNavigation",
        "onError",
        "onBottomSheetActionPress"
      )

      Prop("startOrigin") { (view: MapboxNavigationView, origin: [String: Any]?) in
        view.startOrigin = origin
      }

      Prop("enabled") { (view: MapboxNavigationView, enabled: Bool) in
        view.enabled = enabled
      }

      Prop("destination") { (view: MapboxNavigationView, destination: [String: Any]) in
        view.destination = destination
      }

      Prop("waypoints") { (view: MapboxNavigationView, waypoints: [[String: Any]]?) in
        view.waypoints = waypoints
      }

      Prop("navigationMarkers") { (view: MapboxNavigationView, markers: [[String: Any]]?) in
        view.navigationMarkers = markers
      }

      Prop("navigationLines") { (view: MapboxNavigationView, lines: [[String: Any]]?) in
        view.navigationLines = lines
      }

      Prop("shouldSimulateRoute") { (view: MapboxNavigationView, simulate: Bool) in
        view.shouldSimulateRoute = simulate
      }

      Prop("showCancelButton") { (view: MapboxNavigationView, show: Bool) in
        view.showCancelButton = show
      }

      Prop("mute") { (view: MapboxNavigationView, mute: Bool) in
        view.mute = mute
      }

      Prop("voiceVolume") { (view: MapboxNavigationView, volume: Double) in
        view.voiceVolume = volume
      }

      Prop("cameraPitch") { (view: MapboxNavigationView, pitch: Double) in
        view.cameraPitch = pitch
      }

      Prop("cameraZoom") { (view: MapboxNavigationView, zoom: Double) in
        view.cameraZoom = zoom
      }

      Prop("cameraMode") { (view: MapboxNavigationView, mode: String) in
        view.cameraMode = mode
      }

      Prop("mapStyleUri") { (view: MapboxNavigationView, mapStyleUri: String) in
        view.mapStyleUri = mapStyleUri
      }

      Prop("mapStyleUriDay") { (view: MapboxNavigationView, mapStyleUriDay: String) in
        view.mapStyleUriDay = mapStyleUriDay
      }

      Prop("mapStyleUriNight") { (view: MapboxNavigationView, mapStyleUriNight: String) in
        view.mapStyleUriNight = mapStyleUriNight
      }

      Prop("uiTheme") { (view: MapboxNavigationView, uiTheme: String) in
        view.uiTheme = uiTheme
      }

      Prop("routeAlternatives") { (view: MapboxNavigationView, routeAlternatives: Bool) in
        view.routeAlternatives = routeAlternatives
      }

      Prop("showsSpeedLimits") { (view: MapboxNavigationView, showsSpeedLimits: Bool) in
        view.showsSpeedLimits = showsSpeedLimits
      }

      Prop("showsWayNameLabel") { (view: MapboxNavigationView, showsWayNameLabel: Bool) in
        view.showsWayNameLabel = showsWayNameLabel
      }

      Prop("showsWaypoints") { (view: MapboxNavigationView, showsWaypoints: Bool) in
        view.showsWaypoints = showsWaypoints
      }

      Prop("waypointAdvance") { (view: MapboxNavigationView, waypointAdvance: String?) in
        view.waypointAdvance = waypointAdvance ?? "automatic"
      }

      Prop("showsTripProgress") { (view: MapboxNavigationView, showsTripProgress: Bool) in
        view.showsTripProgress = showsTripProgress
      }

      Prop("showsManeuverView") { (view: MapboxNavigationView, showsManeuverView: Bool) in
        view.showsManeuverView = showsManeuverView
      }

      Prop("showsActionButtons") { (view: MapboxNavigationView, showsActionButtons: Bool) in
        view.showsActionButtons = showsActionButtons
      }

      Prop("showsReportFeedback") { (view: MapboxNavigationView, showsReportFeedback: Bool) in
        view.showsReportFeedback = showsReportFeedback
      }


      Prop("showsContinuousAlternatives") { (view: MapboxNavigationView, showsContinuousAlternatives: Bool) in
        view.showsContinuousAlternatives = showsContinuousAlternatives
      }

      Prop("usesNightStyleWhileInTunnel") { (view: MapboxNavigationView, usesNightStyleWhileInTunnel: Bool) in
        view.usesNightStyleWhileInTunnel = usesNightStyleWhileInTunnel
      }

      Prop("routeLineTracksTraversal") { (view: MapboxNavigationView, routeLineTracksTraversal: Bool) in
        view.routeLineTracksTraversal = routeLineTracksTraversal
      }

      Prop("annotatesIntersectionsAlongRoute") { (view: MapboxNavigationView, annotatesIntersectionsAlongRoute: Bool) in
        view.annotatesIntersectionsAlongRoute = annotatesIntersectionsAlongRoute
      }

      Prop("distanceUnit") { (view: MapboxNavigationView, unit: String) in
        view.distanceUnit = unit
      }

      Prop("nativeFloatingButtons") { (view: MapboxNavigationView, value: [String: Any]?) in
        view.nativeFloatingButtons = value
      }

      Prop("language") { (view: MapboxNavigationView, language: String) in
        view.language = language
      }

      Prop("colors") { (view: MapboxNavigationView, value: [String: Any]?) in
        view.colors = value
      }

      Prop("routeProfile") { (view: MapboxNavigationView, value: String?) in
        view.routeProfile = value ?? "driving-traffic"
        view.restartRouteRequestIfNeeded()
      }

      Prop("routeExclusions") { (view: MapboxNavigationView, value: [String: Any]?) in
        view.routeExclusions = value
        view.restartRouteRequestIfNeeded()
      }

      Prop("vehicle") { (view: MapboxNavigationView, value: [String: Any]?) in
        view.vehicle = value
        view.restartRouteRequestIfNeeded()
      }

      Prop("mapStyleConfig") { (view: MapboxNavigationView, value: [String: Any]?) in
        view.mapStyleConfig = value
      }

      Prop("locationPuck") { (view: MapboxNavigationView, value: [String: Any]?) in
        view.locationPuck = value
      }

      Prop("eventThrottleMs") { (view: MapboxNavigationView, value: Double) in
        view.eventThrottleMs = value
      }

      Prop("cameraPadding") { (view: MapboxNavigationView, value: [String: Any]?) in
        view.cameraPadding = value
      }

      Prop("ornamentBottomMargin") { (view: MapboxNavigationView, value: Double?) in
        view.ornamentBottomMargin = value
      }

      Prop("journeyDataEnabled") { (view: MapboxNavigationView, value: Bool?) in
        view.journeyDataEnabled = value ?? true
      }

      Prop("keepAliveWhenDetached") { (view: MapboxNavigationView, value: Bool?) in
        view.keepAliveWhenDetached = value ?? false
      }

      OnViewDidUpdateProps { (view: MapboxNavigationView) in
        view.didUpdateProps()
      }
    }
  }
}

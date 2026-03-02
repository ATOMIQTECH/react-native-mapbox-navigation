import ExpoModulesCore
import Foundation
import MapboxNavigation

public class MapboxNavigationModule: Module {
  private var mute = false
  private var voiceVolume: Double = 1
  private var distanceUnit = "metric"
  private var language = Locale.preferredLanguages.first ?? "en"

  public func definition() -> ModuleDefinition {
    Name("MapboxNavigationModule")

    Events(
      "onLocationChange",
      "onRouteProgressChange",
      "onJourneyDataChange",
      "onBannerInstruction",
      "onArrive",
      "onCancelNavigation",
      "onError",
      "onBottomSheetActionPress"
    )

    AsyncFunction("setMuted") { (muted: Bool, promise: Promise) in
      self.mute = muted
      promise.resolve(nil)
    }

    AsyncFunction("setVoiceVolume") { (volume: Double, promise: Promise) in
      let clamped = max(0, min(volume, 1))
      self.voiceVolume = clamped
      promise.resolve(nil)
    }

    AsyncFunction("setDistanceUnit") { (unit: String, promise: Promise) in
      let normalized = unit.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
      if normalized == "imperial" {
        self.distanceUnit = "imperial"
      } else {
        self.distanceUnit = "metric"
      }
      promise.resolve(nil)
    }

    AsyncFunction("setLanguage") { (language: String, promise: Promise) in
      let trimmed = language.trimmingCharacters(in: .whitespacesAndNewlines)
      if !trimmed.isEmpty {
        self.language = trimmed
      }
      promise.resolve(nil)
    }

    AsyncFunction("getNavigationSettings") { (promise: Promise) in
      promise.resolve([
        "isNavigating": false,
        "mute": self.mute,
        "voiceVolume": self.voiceVolume,
        "distanceUnit": self.distanceUnit,
        "language": self.language
      ])
    }

    View(MapboxNavigationView.self) {
      Events(
        "onLocationChange",
        "onRouteProgressChange",
        "onJourneyDataChange",
        "onBannerInstruction",
        "onArrive",
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

      Prop("showsEndOfRouteFeedback") { (view: MapboxNavigationView, showsEndOfRouteFeedback: Bool) in
        view.showsEndOfRouteFeedback = showsEndOfRouteFeedback
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

      Prop("language") { (view: MapboxNavigationView, language: String) in
        view.language = language
      }
    }
  }
}

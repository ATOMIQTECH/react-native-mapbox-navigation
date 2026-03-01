package expo.modules.mapboxnavigation

import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MapboxNavigationModule : Module() {
  private var mute = false
  private var voiceVolume = 1.0
  private var distanceUnit = "metric"
  private var language = "en"

  override fun definition() = ModuleDefinition {
    Name("MapboxNavigationModule")

    Events(
      "onLocationChange",
      "onRouteProgressChange",
      "onJourneyDataChange",
      "onBannerInstruction",
      "onArrive",
      "onDestinationPreview",
      "onDestinationChanged",
      "onCancelNavigation",
      "onError",
      "onBottomSheetActionPress"
    )

    OnCreate {
      MapboxNavigationEventBridge.setEmitter { eventName, payload ->
        sendEvent(eventName, payload)
      }
    }

    OnDestroy {
      MapboxNavigationEventBridge.clearEmitter()
    }

    AsyncFunction("setMuted") { muted: Boolean, promise: Promise ->
      this@MapboxNavigationModule.mute = muted
      MapboxAudioGuidanceController.setMuted(muted)
      promise.resolve(null)
    }

    AsyncFunction("setVoiceVolume") { volume: Double, promise: Promise ->
      this@MapboxNavigationModule.voiceVolume = volume
      MapboxAudioGuidanceController.setVoiceVolume(volume)
      promise.resolve(null)
    }

    AsyncFunction("setDistanceUnit") { unit: String, promise: Promise ->
      val normalized = unit.trim().lowercase()
      if (normalized == "metric" || normalized == "imperial") {
        this@MapboxNavigationModule.distanceUnit = normalized
      }
      promise.resolve(null)
    }

    AsyncFunction("setLanguage") { value: String, promise: Promise ->
      val trimmed = value.trim()
      if (trimmed.isNotEmpty()) {
        this@MapboxNavigationModule.language = trimmed
      }
      MapboxAudioGuidanceController.setLanguage(value)
      promise.resolve(null)
    }

    AsyncFunction("getNavigationSettings") { promise: Promise ->
      promise.resolve(
        mapOf(
          "isNavigating" to false,
          "mute" to mute,
          "voiceVolume" to voiceVolume.coerceIn(0.0, 1.0),
          "distanceUnit" to distanceUnit,
          "language" to language
        )
      )
    }

    View(MapboxNavigationView::class) {
      Events(
        "onLocationChange",
        "onRouteProgressChange",
        "onJourneyDataChange",
        "onBannerInstruction",
        "onArrive",
        "onDestinationPreview",
        "onDestinationChanged",
        "onCancelNavigation",
        "onError",
        "onBottomSheetActionPress"
      )

      Prop("startOrigin") { view: MapboxNavigationView, origin: Map<String, Any>? ->
        view.setStartOrigin(origin)
      }

      Prop("enabled") { view: MapboxNavigationView, enabled: Boolean ->
        view.setNavigationEnabled(enabled)
      }

      Prop("destination") { view: MapboxNavigationView, destination: Map<String, Any> ->
        view.setDestination(destination)
      }

      Prop("waypoints") { view: MapboxNavigationView, waypoints: List<Map<String, Any>>? ->
        view.setWaypoints(waypoints)
      }

      Prop("shouldSimulateRoute") { view: MapboxNavigationView, simulate: Boolean ->
        view.setShouldSimulateRoute(simulate)
      }

      Prop("showCancelButton") { view: MapboxNavigationView, show: Boolean ->
        view.setShowCancelButton(show)
      }

      Prop("mute") { view: MapboxNavigationView, value: Boolean ->
        view.setMute(value)
      }

      Prop("voiceVolume") { view: MapboxNavigationView, value: Double ->
        view.setVoiceVolume(value)
      }

      Prop("cameraPitch") { view: MapboxNavigationView, value: Double ->
        view.setCameraPitch(value)
      }

      Prop("cameraZoom") { view: MapboxNavigationView, value: Double ->
        view.setCameraZoom(value)
      }

      Prop("cameraMode") { view: MapboxNavigationView, value: String ->
        view.setCameraMode(value)
      }

      Prop("mapStyleUri") { view: MapboxNavigationView, value: String ->
        view.setMapStyleUri(value)
      }

      Prop("mapStyleUriDay") { view: MapboxNavigationView, value: String ->
        view.setMapStyleUriDay(value)
      }

      Prop("mapStyleUriNight") { view: MapboxNavigationView, value: String ->
        view.setMapStyleUriNight(value)
      }

      Prop("uiTheme") { view: MapboxNavigationView, value: String ->
        view.setUiTheme(value)
      }

      Prop("routeAlternatives") { view: MapboxNavigationView, value: Boolean ->
        view.setRouteAlternatives(value)
      }

      Prop("showsSpeedLimits") { view: MapboxNavigationView, value: Boolean ->
        view.setShowsSpeedLimits(value)
      }

      Prop("showsWayNameLabel") { view: MapboxNavigationView, value: Boolean ->
        view.setShowsWayNameLabel(value)
      }

      Prop("showsTripProgress") { view: MapboxNavigationView, value: Boolean ->
        view.setShowsTripProgress(value)
      }

      Prop("showsManeuverView") { view: MapboxNavigationView, value: Boolean ->
        view.setShowsManeuverView(value)
      }

      Prop("showsActionButtons") { view: MapboxNavigationView, value: Boolean ->
        view.setShowsActionButtons(value)
      }

      Prop("showsReportFeedback") { view: MapboxNavigationView, value: Boolean ->
        view.setShowsReportFeedback(value)
      }

      Prop("showsEndOfRouteFeedback") { view: MapboxNavigationView, value: Boolean ->
        view.setShowsEndOfRouteFeedback(value)
      }

      Prop("showsContinuousAlternatives") { view: MapboxNavigationView, value: Boolean ->
        view.setShowsContinuousAlternatives(value)
      }

      Prop("usesNightStyleWhileInTunnel") { view: MapboxNavigationView, value: Boolean ->
        view.setUsesNightStyleWhileInTunnel(value)
      }

      Prop("routeLineTracksTraversal") { view: MapboxNavigationView, value: Boolean ->
        view.setRouteLineTracksTraversal(value)
      }

      Prop("annotatesIntersectionsAlongRoute") { view: MapboxNavigationView, value: Boolean ->
        view.setAnnotatesIntersectionsAlongRoute(value)
      }

      Prop("androidActionButtons") { view: MapboxNavigationView, value: Map<String, Any>? ->
        view.setAndroidActionButtons(value)
      }

      Prop("distanceUnit") { view: MapboxNavigationView, value: String ->
        view.setDistanceUnit(value)
      }

      Prop("language") { view: MapboxNavigationView, value: String ->
        view.setLanguage(value)
      }
    }
  }
}

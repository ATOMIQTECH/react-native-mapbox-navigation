package expo.modules.mapboxnavigation

import android.content.Intent
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MapboxNavigationModule : Module() {
  private var isNavigating = false

  override fun definition() = ModuleDefinition {
    Name("MapboxNavigationModule")

    Events(
      "onLocationChange",
      "onRouteProgressChange",
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

    AsyncFunction("startNavigation") { options: Map<String, Any?>, promise: Promise ->
      startNavigation(options, promise)
    }

    AsyncFunction("stopNavigation") { promise: Promise ->
      stopNavigation(promise)
    }

    AsyncFunction("setMuted") { _: Boolean, promise: Promise ->
      // Voice control is handled by native navigation UI state.
      promise.resolve(null)
    }

    AsyncFunction("setVoiceVolume") { _: Double, promise: Promise ->
      promise.resolve(null)
    }

    AsyncFunction("setDistanceUnit") { _: String, promise: Promise ->
      promise.resolve(null)
    }

    AsyncFunction("setLanguage") { _: String, promise: Promise ->
      promise.resolve(null)
    }

    AsyncFunction("isNavigating") { promise: Promise ->
      promise.resolve(isNavigating)
    }

    AsyncFunction("getNavigationSettings") { promise: Promise ->
      promise.resolve(
        mapOf(
          "isNavigating" to isNavigating,
          "mute" to false,
          "voiceVolume" to 1.0,
          "distanceUnit" to "metric",
          "language" to "en"
        )
      )
    }

    View(MapboxNavigationView::class) {
      Events(
        "onLocationChange",
        "onRouteProgressChange",
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

      Prop("mute") { view: MapboxNavigationView, mute: Boolean ->
        view.setMute(mute)
      }

      Prop("voiceVolume") { view: MapboxNavigationView, volume: Double ->
        view.setVoiceVolume(volume)
      }

      Prop("cameraPitch") { view: MapboxNavigationView, pitch: Double ->
        view.setCameraPitch(pitch)
      }

      Prop("cameraZoom") { view: MapboxNavigationView, zoom: Double ->
        view.setCameraZoom(zoom)
      }

      Prop("cameraMode") { view: MapboxNavigationView, mode: String ->
        view.setCameraMode(mode)
      }

      Prop("mapStyleUri") { view: MapboxNavigationView, styleUri: String ->
        view.setMapStyleUri(styleUri)
      }

      Prop("mapStyleUriDay") { view: MapboxNavigationView, styleUri: String ->
        view.setMapStyleUriDay(styleUri)
      }

      Prop("mapStyleUriNight") { view: MapboxNavigationView, styleUri: String ->
        view.setMapStyleUriNight(styleUri)
      }

      Prop("uiTheme") { view: MapboxNavigationView, theme: String ->
        view.setUiTheme(theme)
      }

      Prop("routeAlternatives") { view: MapboxNavigationView, routeAlternatives: Boolean ->
        view.setRouteAlternatives(routeAlternatives)
      }

      Prop("showsSpeedLimits") { view: MapboxNavigationView, showsSpeedLimits: Boolean ->
        view.setShowsSpeedLimits(showsSpeedLimits)
      }

      Prop("showsWayNameLabel") { view: MapboxNavigationView, showsWayNameLabel: Boolean ->
        view.setShowsWayNameLabel(showsWayNameLabel)
      }

      Prop("showsTripProgress") { view: MapboxNavigationView, showsTripProgress: Boolean ->
        view.setShowsTripProgress(showsTripProgress)
      }

      Prop("showsManeuverView") { view: MapboxNavigationView, showsManeuverView: Boolean ->
        view.setShowsManeuverView(showsManeuverView)
      }

      Prop("showsActionButtons") { view: MapboxNavigationView, showsActionButtons: Boolean ->
        view.setShowsActionButtons(showsActionButtons)
      }

      Prop("showsReportFeedback") { view: MapboxNavigationView, showsReportFeedback: Boolean ->
        view.setShowsReportFeedback(showsReportFeedback)
      }

      Prop("showsEndOfRouteFeedback") { view: MapboxNavigationView, showsEndOfRouteFeedback: Boolean ->
        view.setShowsEndOfRouteFeedback(showsEndOfRouteFeedback)
      }

      Prop("showsContinuousAlternatives") { view: MapboxNavigationView, showsContinuousAlternatives: Boolean ->
        view.setShowsContinuousAlternatives(showsContinuousAlternatives)
      }

      Prop("usesNightStyleWhileInTunnel") { view: MapboxNavigationView, usesNightStyleWhileInTunnel: Boolean ->
        view.setUsesNightStyleWhileInTunnel(usesNightStyleWhileInTunnel)
      }

      Prop("routeLineTracksTraversal") { view: MapboxNavigationView, routeLineTracksTraversal: Boolean ->
        view.setRouteLineTracksTraversal(routeLineTracksTraversal)
      }

      Prop("annotatesIntersectionsAlongRoute") { view: MapboxNavigationView, annotatesIntersectionsAlongRoute: Boolean ->
        view.setAnnotatesIntersectionsAlongRoute(annotatesIntersectionsAlongRoute)
      }

      Prop("androidActionButtons") { view: MapboxNavigationView, androidActionButtons: Map<String, Any>? ->
        view.setAndroidActionButtons(androidActionButtons)
      }

      Prop("distanceUnit") { view: MapboxNavigationView, unit: String ->
        view.setDistanceUnit(unit)
      }

      Prop("language") { view: MapboxNavigationView, language: String ->
        view.setLanguage(language)
      }
    }
  }

  private fun startNavigation(options: Map<String, Any?>, promise: Promise) {
    val activity = appContext.currentActivity
    if (activity == null) {
      promise.reject("NO_ACTIVITY", "No current activity", null)
      return
    }

    val origin = options["startOrigin"] as? Map<*, *>
    val destination = options["destination"] as? Map<*, *>

    val originLat = (origin?.get("latitude") as? Number)?.toDouble()
    val originLng = (origin?.get("longitude") as? Number)?.toDouble()
    val destLat = (destination?.get("latitude") as? Number)?.toDouble()
    val destLng = (destination?.get("longitude") as? Number)?.toDouble()

    if (destLat == null || destLng == null) {
      promise.reject("INVALID_COORDINATES", "Missing or invalid coordinates", null)
      return
    }

    val shouldSimulate = (options["shouldSimulateRoute"] as? Boolean) ?: false
    val mute = (options["mute"] as? Boolean) ?: false
    val cameraPitch = (options["cameraPitch"] as? Number)?.toDouble()
    val cameraZoom = (options["cameraZoom"] as? Number)?.toDouble()
    val cameraMode = (options["cameraMode"] as? String) ?: "following"
    val mapStyleUri = (options["mapStyleUri"] as? String) ?: ""
    val mapStyleUriDay = (options["mapStyleUriDay"] as? String) ?: ""
    val mapStyleUriNight = (options["mapStyleUriNight"] as? String) ?: ""
    val uiTheme = (options["uiTheme"] as? String) ?: "system"
    val showsContinuousAlternatives = options["showsContinuousAlternatives"] as? Boolean
    val routeAlternatives = (options["routeAlternatives"] as? Boolean)
      ?: showsContinuousAlternatives
      ?: false
    val showsSpeedLimits = (options["showsSpeedLimits"] as? Boolean) ?: true
    val showsWayNameLabel = (options["showsWayNameLabel"] as? Boolean) ?: true
    val showsTripProgress = (options["showsTripProgress"] as? Boolean) ?: true
    val showsManeuverView = (options["showsManeuverView"] as? Boolean) ?: true
    val showsActionButtons = (options["showsActionButtons"] as? Boolean) ?: true
    val showsReportFeedback = options["showsReportFeedback"] as? Boolean
    val showsEndOfRouteFeedback = options["showsEndOfRouteFeedback"] as? Boolean
    val androidActionButtons = options["androidActionButtons"] as? Map<*, *>
    val showEmergencyCallButton = androidActionButtons?.get("showEmergencyCallButton") as? Boolean
    val showCancelRouteButton = androidActionButtons?.get("showCancelRouteButton") as? Boolean
    val showRefreshRouteButton = androidActionButtons?.get("showRefreshRouteButton") as? Boolean
    val showReportFeedbackButton = androidActionButtons?.get("showReportFeedbackButton") as? Boolean
    val showToggleAudioButton = androidActionButtons?.get("showToggleAudioButton") as? Boolean
    val showSearchAlongRouteButton = androidActionButtons?.get("showSearchAlongRouteButton") as? Boolean
    val showStartNavigationButton = androidActionButtons?.get("showStartNavigationButton") as? Boolean
    val showEndNavigationButton = androidActionButtons?.get("showEndNavigationButton") as? Boolean
    val showAlternativeRoutesButton = androidActionButtons?.get("showAlternativeRoutesButton") as? Boolean
    val showStartNavigationFeedbackButton = androidActionButtons?.get("showStartNavigationFeedbackButton") as? Boolean
    val showEndNavigationFeedbackButton = androidActionButtons?.get("showEndNavigationFeedbackButton") as? Boolean
    val waypoints = parseCoordinatesList(options["waypoints"] as? List<*>)

    activity.runOnUiThread {
      val accessToken = try {
        getMapboxAccessToken(activity.packageName)
      } catch (e: IllegalStateException) {
        promise.reject("MISSING_ACCESS_TOKEN", e.message ?: "Missing mapbox_access_token", e)
        return@runOnUiThread
      }

      val intent = Intent(activity, MapboxNavigationActivity::class.java).apply {
        putExtra("accessToken", accessToken)
        if (originLat != null && originLng != null) {
          putExtra("originLat", originLat)
          putExtra("originLng", originLng)
        }
        putExtra("destLat", destLat)
        putExtra("destLng", destLng)
        putExtra("shouldSimulate", shouldSimulate)
        putExtra("mute", mute)
        putExtra("cameraPitch", cameraPitch)
        putExtra("cameraZoom", cameraZoom)
        putExtra("cameraMode", cameraMode)
        putExtra("mapStyleUri", mapStyleUri)
        putExtra("mapStyleUriDay", mapStyleUriDay)
        putExtra("mapStyleUriNight", mapStyleUriNight)
        putExtra("uiTheme", uiTheme)
        putExtra("routeAlternatives", routeAlternatives)
        putExtra("showsSpeedLimits", showsSpeedLimits)
        putExtra("showsWayNameLabel", showsWayNameLabel)
        putExtra("showsTripProgress", showsTripProgress)
        putExtra("showsManeuverView", showsManeuverView)
        putExtra("showsActionButtons", showsActionButtons)
        showsReportFeedback?.let { putExtra("showsReportFeedback", it) }
        showsEndOfRouteFeedback?.let { putExtra("showsEndOfRouteFeedback", it) }
        showEmergencyCallButton?.let { putExtra("showEmergencyCallButton", it) }
        showCancelRouteButton?.let { putExtra("showCancelRouteButton", it) }
        showRefreshRouteButton?.let { putExtra("showRefreshRouteButton", it) }
        showReportFeedbackButton?.let { putExtra("showReportFeedbackButton", it) }
        showToggleAudioButton?.let { putExtra("showToggleAudioButton", it) }
        showSearchAlongRouteButton?.let { putExtra("showSearchAlongRouteButton", it) }
        showStartNavigationButton?.let { putExtra("showStartNavigationButton", it) }
        showEndNavigationButton?.let { putExtra("showEndNavigationButton", it) }
        showAlternativeRoutesButton?.let { putExtra("showAlternativeRoutesButton", it) }
        showStartNavigationFeedbackButton?.let { putExtra("showStartNavigationFeedbackButton", it) }
        showEndNavigationFeedbackButton?.let { putExtra("showEndNavigationFeedbackButton", it) }
        if (waypoints.isNotEmpty()) {
          putExtra("waypointLats", waypoints.map { it.first }.toDoubleArray())
          putExtra("waypointLngs", waypoints.map { it.second }.toDoubleArray())
        }
      }

      activity.startActivity(intent)
      isNavigating = true
      promise.resolve(null)
    }
  }

  private fun getMapboxAccessToken(packageName: String): String {
    val context = appContext.reactContext ?: throw IllegalStateException("Missing React context")
    val resourceId = context.resources.getIdentifier(
      "mapbox_access_token",
      "string",
      packageName
    )

    if (resourceId == 0) {
      throw IllegalStateException("Missing string resource: mapbox_access_token")
    }

    val token = context.getString(resourceId).trim()
    if (token.isEmpty()) {
      throw IllegalStateException("mapbox_access_token is empty")
    }

    return token
  }

  private fun stopNavigation(promise: Promise) {
    val activity = appContext.currentActivity
    activity?.finish()
    isNavigating = false
    promise.resolve(null)
  }

  private fun parseCoordinatesList(value: List<*>?): List<Pair<Double, Double>> {
    if (value.isNullOrEmpty()) {
      return emptyList()
    }

    return value.mapNotNull { item ->
      val map = item as? Map<*, *> ?: return@mapNotNull null
      val latitude = (map["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      val longitude = (map["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
        return@mapNotNull null
      }
      latitude to longitude
    }
  }
}

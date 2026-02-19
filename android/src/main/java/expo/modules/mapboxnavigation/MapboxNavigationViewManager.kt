package expo.modules.mapboxnavigation

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView

class MapboxNavigationView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private var startOrigin: Map<String, Double>? = null
  private var destination: Map<String, Any>? = null
  private var waypoints: List<Map<String, Any>>? = null
  private var shouldSimulateRoute = false
  private var showCancelButton = true
  private var mute = false
  private var voiceVolume = 1.0
  private var cameraPitch = 0.0
  private var cameraZoom = 14.0
  private var cameraMode = "following"
  private var mapStyleUri = ""
  private var mapStyleUriDay = ""
  private var mapStyleUriNight = ""
  private var uiTheme = "system"
  private var routeAlternatives = false
  private var showsSpeedLimits = true
  private var showsWayNameLabel = true
  private var showsTripProgress = true
  private var showsManeuverView = true
  private var showsActionButtons = true
  private var distanceUnit = "metric"
  private var language = "en"

  val onLocationChange by EventDispatcher()
  val onRouteProgressChange by EventDispatcher()
  val onBannerInstruction by EventDispatcher()
  val onArrive by EventDispatcher()
  val onCancelNavigation by EventDispatcher()
  val onError by EventDispatcher()

  init {
    val label = TextView(context).apply {
      text = "Mapbox native navigation view is initializing..."
      gravity = Gravity.CENTER
    }
    addView(
      label,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )
  }

  fun setStartOrigin(origin: Map<String, Double>) {
    startOrigin = origin
  }

  fun setDestination(dest: Map<String, Any>) {
    destination = dest
  }

  fun setWaypoints(wps: List<Map<String, Any>>?) {
    waypoints = wps
  }

  fun setShouldSimulateRoute(simulate: Boolean) {
    shouldSimulateRoute = simulate
  }

  fun setShowCancelButton(show: Boolean) {
    showCancelButton = show
  }

  fun setMute(muted: Boolean) {
    mute = muted
  }

  fun setVoiceVolume(volume: Double) {
    voiceVolume = volume
  }

  fun setCameraPitch(pitch: Double) {
    cameraPitch = pitch
  }

  fun setCameraZoom(zoom: Double) {
    cameraZoom = zoom
  }

  fun setCameraMode(mode: String) {
    cameraMode = mode
  }

  fun setMapStyleUri(styleUri: String) {
    mapStyleUri = styleUri
  }

  fun setMapStyleUriDay(styleUri: String) {
    mapStyleUriDay = styleUri
  }

  fun setMapStyleUriNight(styleUri: String) {
    mapStyleUriNight = styleUri
  }

  fun setUiTheme(theme: String) {
    uiTheme = theme
  }

  fun setRouteAlternatives(enabled: Boolean) {
    routeAlternatives = enabled
  }

  fun setShowsSpeedLimits(enabled: Boolean) {
    showsSpeedLimits = enabled
  }

  fun setShowsWayNameLabel(enabled: Boolean) {
    showsWayNameLabel = enabled
  }

  fun setShowsTripProgress(enabled: Boolean) {
    showsTripProgress = enabled
  }

  fun setShowsManeuverView(enabled: Boolean) {
    showsManeuverView = enabled
  }

  fun setShowsActionButtons(enabled: Boolean) {
    showsActionButtons = enabled
  }

  fun setDistanceUnit(unit: String) {
    distanceUnit = unit
  }

  fun setLanguage(lang: String) {
    language = lang
  }
}

package expo.modules.mapboxnavigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.dropin.NavigationView
import com.mapbox.navigation.dropin.RouteOptionsInterceptor
import com.mapbox.navigation.dropin.navigationview.NavigationViewListener
import androidx.core.content.ContextCompat
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.util.UUID

class MapboxNavigationView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  companion object {
    private const val TAG = "MapboxNavigationView"
    private const val EMBEDDED_BUILD = "1.1.6-embedded-2026-02-26-r4"
  }

  private val expoAppContext: AppContext = appContext
  private var startOrigin: Map<String, Any>? = null
  private var navigationEnabled = false
  private val sessionOwner = "embedded-${UUID.randomUUID()}"
  private var ownsNavigationSession = false
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
  private var showsReportFeedback = true
  private var showsEndOfRouteFeedback = true
  private var showsContinuousAlternatives = true
  private var usesNightStyleWhileInTunnel = true
  private var routeLineTracksTraversal = false
  private var annotatesIntersectionsAlongRoute = false
  private var showEmergencyCallButton: Boolean? = null
  private var showCancelRouteButton: Boolean? = null
  private var showRefreshRouteButton: Boolean? = null
  private var showReportFeedbackButton: Boolean? = null
  private var showToggleAudioButton: Boolean? = null
  private var showSearchAlongRouteButton: Boolean? = null
  private var showStartNavigationButton: Boolean? = null
  private var showEndNavigationButton: Boolean? = null
  private var showAlternativeRoutesButton: Boolean? = null
  private var showStartNavigationFeedbackButton: Boolean? = null
  private var showEndNavigationFeedbackButton: Boolean? = null
  private var distanceUnit = "metric"
  private var language = "en"

  private var navigationView: NavigationView? = null
  private var navigationRoot: FrameLayout? = null
  private var mapboxNavigation: MapboxNavigation? = null
  private var permissionPlaceholderView: TextView? = null
  private var lastLayoutLogKey: String? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private var hasStartedGuidance = false
  private var hasEmittedArrival = false
  private var hasPendingSessionConflict = false
  private var hasStartedDestinationPreview = false
  private var lastStartSignature: String? = null
  private val warnedUnsupportedKeys = mutableSetOf<String>()
  private var latestLatitude: Double? = null
  private var latestLongitude: Double? = null
  private var latestBearing: Double? = null
  private var latestSpeed: Double? = null
  private var latestAltitude: Double? = null
  private var latestAccuracy: Double? = null
  private var latestPrimaryInstruction: String? = null
  private var latestSecondaryInstruction: String? = null
  private var latestStepDistanceRemaining: Double? = null

  val onLocationChange by EventDispatcher()
  val onRouteProgressChange by EventDispatcher()
  val onJourneyDataChange by EventDispatcher()
  val onBannerInstruction by EventDispatcher()
  val onArrive by EventDispatcher()
  val onDestinationPreview by EventDispatcher()
  val onDestinationChanged by EventDispatcher()
  val onCancelNavigation by EventDispatcher()
  val onError by EventDispatcher()

  private val locationObserver = object : LocationObserver {
    override fun onNewRawLocation(rawLocation: android.location.Location) = Unit

    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      val location = locationMatcherResult.enhancedLocation
      latestLatitude = location.latitude
      latestLongitude = location.longitude
      latestBearing = location.bearing.toDouble()
      latestSpeed = location.speed.toDouble()
      latestAltitude = location.altitude
      latestAccuracy = location.accuracy.toDouble()
      onLocationChange(
        mapOf(
          "latitude" to location.latitude,
          "longitude" to location.longitude,
          "bearing" to location.bearing.toDouble(),
          "speed" to location.speed.toDouble(),
          "altitude" to location.altitude,
          "accuracy" to location.accuracy.toDouble()
        )
      )
      emitJourneyData(progress = null)
    }
  }

  private val routeProgressObserver = RouteProgressObserver { routeProgress: RouteProgress ->
    onRouteProgressChange(
      mapOf(
        "distanceTraveled" to routeProgress.distanceTraveled.toDouble(),
        "distanceRemaining" to routeProgress.distanceRemaining.toDouble(),
        "durationRemaining" to routeProgress.durationRemaining,
        "fractionTraveled" to routeProgress.fractionTraveled.toDouble()
      )
    )

    if (!hasEmittedArrival && routeProgress.distanceRemaining <= 5.0) {
      hasEmittedArrival = true
      val destinationName = destination?.get("name") as? String
      onArrive(
        mapOf(
          "name" to (destinationName ?: "Destination")
        )
      )
    }

    emitBannerInstruction(routeProgress.bannerInstructions)
    emitJourneyData(progress = routeProgress)
  }

  private val bannerInstructionsObserver = BannerInstructionsObserver { bannerInstructions ->
    emitBannerInstruction(bannerInstructions)
  }

  private val navigationViewListener = object : NavigationViewListener() {
    override fun onDestinationChanged(destination: Point?) {
      destination?.let { point ->
        onDestinationChanged(
          mapOf(
            "latitude" to point.latitude(),
            "longitude" to point.longitude()
          )
        )
      }
    }

    override fun onDestinationPreview() {
      onDestinationPreview(mapOf("active" to true))
    }

    override fun onRouteFetchFailed(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
      val (code, message) = mapRouteFetchFailure(reasons)
      onError(
        mapOf(
          "code" to code,
          "message" to message
        )
      )
      releaseNavigationSession()
    }

    override fun onRouteFetchSuccessful(routes: List<NavigationRoute>) {
      if (routes.isEmpty() || hasStartedGuidance) {
        return
      }
      hasStartedGuidance = true
      navigationView?.api?.startActiveGuidance(routes)
    }

    override fun onRouteFetchCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
      onError(
        mapOf(
          "code" to "ROUTE_FETCH_CANCELED",
          "message" to "Route fetch canceled (origin: $routerOrigin)."
        )
      )
      releaseNavigationSession()
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    Log.i(TAG, "Embedded view attached ($EMBEDDED_BUILD)")
    if (!navigationEnabled) {
      return
    }
    startEmbeddedIfEnabled()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    // ExpoView does not guarantee child layout. Ensure our embedded Mapbox NavigationView (and placeholder)
    // always fill the host bounds.
    val hostW = right - left
    val hostH = bottom - top
    for (i in 0 until childCount) {
      val child = getChildAt(i)
      if (child.visibility == View.GONE) continue
      child.layout(0, 0, hostW, hostH)
    }

    val nav = navigationView
    val navW = nav?.width ?: 0
    val navH = nav?.height ?: 0
    val navVisible = nav?.visibility ?: -1
    val navShown = nav?.isShown ?: false
    val placeholder = permissionPlaceholderView
    val placeholderVisible = placeholder?.visibility ?: -1
    val key =
      "host=${hostW}x${hostH};nav=${navW}x${navH};navVis=$navVisible;navShown=$navShown;ph=$placeholderVisible"
    if (key != lastLayoutLogKey) {
      lastLayoutLogKey = key
      Log.i(TAG, "layout changed ($EMBEDDED_BUILD): $key")
    }

    if (nav != null && hostW > 0 && hostH > 0 && (navW == 0 || navH == 0)) {
      forceMeasureAndLayoutNavigationView(hostW, hostH)
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = View.MeasureSpec.getSize(widthMeasureSpec)
    val height = View.MeasureSpec.getSize(heightMeasureSpec)
    setMeasuredDimension(width, height)

    val childWidthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
    val childHeightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
    for (i in 0 until childCount) {
      val child = getChildAt(i)
      if (child.visibility == View.GONE) continue
      child.measure(childWidthSpec, childHeightSpec)
    }
  }

  override fun onDetachedFromWindow() {
    deactivateEmbeddedNavigation()
    super.onDetachedFromWindow()
  }

  fun setNavigationEnabled(enabled: Boolean) {
    navigationEnabled = enabled
    if (enabled) {
      if (windowToken != null) {
        startEmbeddedIfEnabled()
      }
    } else {
      deactivateEmbeddedNavigation()
    }
  }

  private fun startEmbeddedIfEnabled() {
    if (!navigationEnabled) {
      return
    }
    Log.i(
      TAG,
      "startEmbeddedIfEnabled ($EMBEDDED_BUILD): enabled=$navigationEnabled hasPerm=${hasLocationPermission()} hasNav=${navigationView != null}"
    )
    if (!hasLocationPermission()) {
      showPermissionPlaceholderIfNeeded(
        "Location permission required.\nGrant ACCESS_FINE_LOCATION to start embedded navigation."
      )
      onError(
        mapOf(
          "code" to "LOCATION_PERMISSION_REQUIRED",
          "message" to "Embedded navigation requires location permission. Request ACCESS_FINE_LOCATION before mounting MapboxNavigationView."
        )
      )
      return
    }

    hidePermissionPlaceholderIfNeeded()
    createNavigationViewIfNeeded()
    if (navigationView == null) {
      Log.e(TAG, "NavigationView is still null after createNavigationViewIfNeeded ($EMBEDDED_BUILD)")
      onError(
        mapOf(
          "code" to "EMBEDDED_VIEW_NOT_CREATED",
          "message" to "Failed to create embedded NavigationView (see native logs for details)."
        )
      )
      return
    }
    attachNavigationObserversWithRetry()
    startNavigationIfReady()
  }

  private fun deactivateEmbeddedNavigation() {
    mainHandler.removeCallbacksAndMessages(null)
    detachNavigationObservers()
    navigationView?.removeListener(navigationViewListener)
    navigationRoot?.let { removeView(it) }
    removeAllViews()
    navigationView = null
    navigationRoot = null
    permissionPlaceholderView = null
    hasStartedGuidance = false
    hasEmittedArrival = false
    hasStartedDestinationPreview = false
    lastStartSignature = null
    releaseNavigationSession()
  }

  fun setStartOrigin(origin: Map<String, Any>?) {
    startOrigin = origin
    if (navigationEnabled) {
      startNavigationIfReady()
    }
  }

  fun setDestination(dest: Map<String, Any>?) {
    destination = dest
    hasEmittedArrival = false
    if (navigationEnabled) {
      startNavigationIfReady()
    }
  }

  fun setWaypoints(wps: List<Map<String, Any>>?) {
    waypoints = wps
    if (navigationEnabled) {
      startNavigationIfReady()
    }
  }

  fun setShouldSimulateRoute(simulate: Boolean) {
    shouldSimulateRoute = simulate
    if (simulate) {
      applySimulationIfNeeded()
    }
  }

  fun setShowCancelButton(show: Boolean) {
    showCancelButton = show
    warnUnsupportedOption(
      "showCancelButton",
      "showCancelButton is not currently supported by the Android embedded Drop-In view and will be ignored."
    )
    applyViewOptions()
  }

  fun setMute(muted: Boolean) {
    mute = muted
    MapboxAudioGuidanceController.setMuted(muted)
  }

  fun setVoiceVolume(volume: Double) {
    voiceVolume = volume
    MapboxAudioGuidanceController.setVoiceVolume(volume)
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
    applyViewOptions()
  }

  fun setMapStyleUriDay(styleUri: String) {
    mapStyleUriDay = styleUri
    applyViewOptions()
  }

  fun setMapStyleUriNight(styleUri: String) {
    mapStyleUriNight = styleUri
    applyViewOptions()
  }

  fun setUiTheme(theme: String) {
    uiTheme = theme
    applyViewOptions()
  }

  fun setRouteAlternatives(enabled: Boolean) {
    routeAlternatives = enabled
    if (navigationEnabled) {
      startNavigationIfReady()
    }
  }

  fun setShowsSpeedLimits(enabled: Boolean) {
    showsSpeedLimits = enabled
    applyViewOptions()
  }

  fun setShowsWayNameLabel(enabled: Boolean) {
    showsWayNameLabel = enabled
    applyViewOptions()
  }

  fun setShowsTripProgress(enabled: Boolean) {
    showsTripProgress = enabled
    applyViewOptions()
  }

  fun setShowsManeuverView(enabled: Boolean) {
    showsManeuverView = enabled
    applyViewOptions()
  }

  fun setShowsActionButtons(enabled: Boolean) {
    showsActionButtons = enabled
    applyViewOptions()
  }

  fun setShowsReportFeedback(enabled: Boolean) {
    showsReportFeedback = enabled
    warnUnsupportedOption(
      "showsReportFeedback",
      "showsReportFeedback is not supported by the current Android Drop-In API in this package version and will be ignored."
    )
    applyViewOptions()
  }

  fun setShowsEndOfRouteFeedback(enabled: Boolean) {
    showsEndOfRouteFeedback = enabled
    warnUnsupportedOption(
      "showsEndOfRouteFeedback",
      "showsEndOfRouteFeedback is not supported by the current Android Drop-In API in this package version and will be ignored."
    )
    applyViewOptions()
  }

  fun setShowsContinuousAlternatives(enabled: Boolean) {
    showsContinuousAlternatives = enabled
    if (navigationEnabled) {
      startNavigationIfReady()
    }
  }

  fun setUsesNightStyleWhileInTunnel(enabled: Boolean) {
    usesNightStyleWhileInTunnel = enabled
    warnUnsupportedOption(
      "usesNightStyleWhileInTunnel",
      "usesNightStyleWhileInTunnel is iOS-only right now and will be ignored on Android."
    )
  }

  fun setRouteLineTracksTraversal(enabled: Boolean) {
    routeLineTracksTraversal = enabled
    warnUnsupportedOption(
      "routeLineTracksTraversal",
      "routeLineTracksTraversal is iOS-only right now and will be ignored on Android."
    )
  }

  fun setAnnotatesIntersectionsAlongRoute(enabled: Boolean) {
    annotatesIntersectionsAlongRoute = enabled
    warnUnsupportedOption(
      "annotatesIntersectionsAlongRoute",
      "annotatesIntersectionsAlongRoute is iOS-only right now and will be ignored on Android."
    )
  }

  fun setAndroidActionButtons(actionButtons: Map<String, Any>?) {
    showEmergencyCallButton = actionButtons?.get("showEmergencyCallButton") as? Boolean
    showCancelRouteButton = actionButtons?.get("showCancelRouteButton") as? Boolean
    showRefreshRouteButton = actionButtons?.get("showRefreshRouteButton") as? Boolean
    showReportFeedbackButton = actionButtons?.get("showReportFeedbackButton") as? Boolean
    showToggleAudioButton = actionButtons?.get("showToggleAudioButton") as? Boolean
    showSearchAlongRouteButton = actionButtons?.get("showSearchAlongRouteButton") as? Boolean
    showStartNavigationButton = actionButtons?.get("showStartNavigationButton") as? Boolean
    showEndNavigationButton = actionButtons?.get("showEndNavigationButton") as? Boolean
    showAlternativeRoutesButton = actionButtons?.get("showAlternativeRoutesButton") as? Boolean
    showStartNavigationFeedbackButton = actionButtons?.get("showStartNavigationFeedbackButton") as? Boolean
    showEndNavigationFeedbackButton = actionButtons?.get("showEndNavigationFeedbackButton") as? Boolean
    if (actionButtons != null) {
      warnUnsupportedOption(
        "androidActionButtons",
        "androidActionButtons fine-grained control is not supported by the current Android Drop-In API in this package version and will be ignored."
      )
    }
    applyViewOptions()
  }

  fun setDistanceUnit(unit: String) {
    distanceUnit = unit
  }

  fun setLanguage(lang: String) {
    language = lang
    MapboxAudioGuidanceController.setLanguage(lang)
  }

  private fun createNavigationViewIfNeeded() {
    if (!navigationEnabled) {
      return
    }
    if (navigationView != null) {
      return
    }
    if (Looper.myLooper() != Looper.getMainLooper()) {
      mainHandler.post { createNavigationViewIfNeeded() }
      return
    }

    val accessToken = runCatching { getMapboxAccessToken() }
      .getOrElse { throwable ->
        Log.e(TAG, "Missing mapbox_access_token string resource ($EMBEDDED_BUILD)", throwable)
        onError(
          mapOf(
            "code" to "MISSING_ACCESS_TOKEN",
            "message" to (throwable.message ?: "Missing mapbox_access_token")
          )
        )
        return
      }

    val activity = expoAppContext.currentActivity
    val navContext = activity ?: context
    if (activity == null) {
      Log.w(TAG, "currentActivity is null; creating NavigationView with non-Activity context ($EMBEDDED_BUILD)")
    }

    val view = runCatching { NavigationView(navContext, null, accessToken) }
      .getOrElse { throwable ->
        Log.e(TAG, "Failed to create embedded NavigationView", throwable)
        onError(
          mapOf(
            "code" to "EMBEDDED_VIEW_INIT_FAILED",
            "message" to (throwable.message ?: "Failed to create embedded navigation view.")
          )
        )
        return
      }
    view.id = View.generateViewId()
    view.layoutParams = LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    view.visibility = View.VISIBLE
    view.alpha = 1f
    view.addListener(navigationViewListener)
    navigationView = view
    val root = FrameLayout(context).apply {
      layoutParams = LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      clipChildren = false
      clipToPadding = false
    }
    navigationRoot = root
    root.addView(
      view,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )
    addView(
      root,
      LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    )
    Log.i(TAG, "NavigationView created and added ($EMBEDDED_BUILD). childCount=$childCount")
    view.requestLayout()
    requestLayout()
    mainHandler.post {
      if (navigationEnabled) {
        forceMeasureAndLayoutNavigationView(width, height)
      }
    }
    // Some OEM + RN combinations can leave the Drop-In NavigationView's internal MapView measured at a
    // tiny fallback size (e.g. 64x64) until a few frames after attach. Retry a few times.
    for (i in 1..8) {
      mainHandler.postDelayed({
        if (!navigationEnabled) return@postDelayed
        val hostW = width
        val hostH = height
        if (hostW <= 0 || hostH <= 0) return@postDelayed
        val nav = navigationView ?: return@postDelayed
        if (!nav.isAttachedToWindow) return@postDelayed
        if (nav.width < hostW || nav.height < hostH) {
          forceMeasureAndLayoutNavigationView(hostW, hostH)
        }
      }, (i * 90L))
    }
    applyViewOptions()
    applySimulationIfNeeded()
  }

  private fun forceMeasureAndLayoutNavigationView(hostW: Int, hostH: Int) {
    if (hostW <= 0 || hostH <= 0) {
      return
    }
    val nav = navigationView ?: return
    val wSpec = View.MeasureSpec.makeMeasureSpec(hostW, View.MeasureSpec.EXACTLY)
    val hSpec = View.MeasureSpec.makeMeasureSpec(hostH, View.MeasureSpec.EXACTLY)
    nav.measure(wSpec, hSpec)
    nav.layout(0, 0, hostW, hostH)
    nav.invalidate()
    Log.i(TAG, "force layout ($EMBEDDED_BUILD): host=${hostW}x${hostH} nav=${nav.width}x${nav.height} vis=${nav.visibility}")
  }

  private fun applySimulationIfNeeded() {
    if (!shouldSimulateRoute) {
      return
    }
    // Some Drop-In builds can throw if routeReplayEnabled is called too early.
    // Apply it only when simulation is enabled and only after the view is attached.
    mainHandler.postDelayed({
      if (!navigationEnabled) {
        return@postDelayed
      }
      val view = navigationView ?: return@postDelayed
      if (!view.isAttachedToWindow) {
        return@postDelayed
      }
      runCatching { view.api.routeReplayEnabled(true) }
        .onFailure { throwable ->
          Log.e(TAG, "routeReplayEnabled(true) failed", throwable)
          onError(
            mapOf(
              "code" to "SIMULATION_ENABLE_FAILED",
              "message" to (throwable.message ?: "Failed to enable route simulation.")
            )
          )
        }
    }, 350L)
  }

  private fun showPermissionPlaceholderIfNeeded(message: String) {
    if (permissionPlaceholderView != null) {
      permissionPlaceholderView?.text = message
      return
    }
    val view = TextView(context).apply {
      text = message
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      gravity = Gravity.CENTER
      setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
      setBackgroundColor(Color.parseColor("#0b1020"))
      layoutParams = LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    }
    permissionPlaceholderView = view
    addView(view)
  }

  private fun hidePermissionPlaceholderIfNeeded() {
    permissionPlaceholderView?.let { existing ->
      removeView(existing)
    }
    permissionPlaceholderView = null
  }

  private fun dpToPx(dp: Float): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
  }

  private fun startNavigationIfReady() {
    if (!navigationEnabled) {
      return
    }
    val view = navigationView ?: return
    val destinationPoint = destination.toAnyPointOrNull() ?: return

    if (!ownsNavigationSession) {
      if (!NavigationSessionRegistry.acquire(sessionOwner)) {
        if (!hasPendingSessionConflict) {
          hasPendingSessionConflict = true
          onError(
            mapOf(
              "code" to "NAVIGATION_SESSION_CONFLICT",
              "message" to "Another navigation session is already active. Stop full-screen or other embedded navigation before mounting this view."
            )
          )
        }
        return
      }
      ownsNavigationSession = true
      hasPendingSessionConflict = false
    }

    val waypointPoints = parseWaypoints(waypoints)
    val originPoint = startOrigin.toAnyPointOrNull()

    if (originPoint == null && waypointPoints.isNotEmpty()) {
      onError(
        mapOf(
          "code" to "INVALID_COORDINATES",
          "message" to "waypoints require startOrigin when using embedded Android navigation."
        )
      )
      releaseNavigationSession()
      hasStartedDestinationPreview = false
      lastStartSignature = null
      return
    }

    val startSignature = buildStartSignature(
      destinationPoint = destinationPoint,
      originPoint = originPoint,
      waypointPoints = waypointPoints,
      routeAlternatives = routeAlternatives,
      showsContinuousAlternatives = showsContinuousAlternatives
    )

    // React Native can re-send props frequently even when values are unchanged.
    // Keep embedded navigation idempotent so it doesn't restart on re-render.
    if (hasStartedDestinationPreview && startSignature == lastStartSignature) {
      return
    }
    lastStartSignature = startSignature
    hasStartedDestinationPreview = true
    hasStartedGuidance = false
    hasEmittedArrival = false

    if (originPoint != null) {
      view.setRouteOptionsInterceptor(
        RouteOptionsInterceptor { builder ->
          val coordinates = mutableListOf<Point>()
          coordinates.add(originPoint)
          coordinates.addAll(waypointPoints)
          coordinates.add(destinationPoint)
          builder.coordinatesList(coordinates)
          // Keep optional arrays aligned with coordinates to avoid route-option validation mismatch.
          // Use concrete layer values because null placeholders can be dropped by the API serializer.
          builder.layersList(MutableList(coordinates.size) { 0 })
          builder.alternatives(routeAlternatives || showsContinuousAlternatives)
        }
      )
    }

    mainHandler.postDelayed({
      if (!navigationEnabled) {
        return@postDelayed
      }
      val activeView = navigationView ?: return@postDelayed
      if (!activeView.isAttachedToWindow) {
        return@postDelayed
      }
      runCatching { activeView.api.startDestinationPreview(destinationPoint) }
        .onFailure { throwable ->
          Log.e(TAG, "startDestinationPreview failed", throwable)
          onError(
            mapOf(
              "code" to "DESTINATION_PREVIEW_FAILED",
              "message" to (throwable.message ?: "Failed to start destination preview.")
            )
          )
        }
    }, 350L)
  }

  private fun applyViewOptions() {
    val view = navigationView ?: return

    val normalizedTheme = uiTheme.trim().lowercase()
    val dayStyle = resolveDayStyleUri(normalizedTheme)
    val nightStyle = resolveNightStyleUri(normalizedTheme, dayStyle)

    view.customizeViewOptions {
      dayStyle?.let { mapStyleUriDay = it }
      nightStyle?.let { mapStyleUriNight = it }
      showSpeedLimit = showsSpeedLimits
      showRoadName = showsWayNameLabel
      showTripProgress = showsTripProgress
      showManeuver = showsManeuverView
      showActionButtons = showsActionButtons
    }
  }

  private fun attachNavigationObserversWithRetry(attempt: Int = 0) {
    if (mapboxNavigation != null) {
      return
    }
    if (!MapboxNavigationProvider.isCreated()) {
      if (attempt < 10) {
        mainHandler.postDelayed({ attachNavigationObserversWithRetry(attempt + 1) }, 150L)
      }
      return
    }

    val navigation = runCatching { MapboxNavigationProvider.retrieve() }
      .getOrElse { throwable ->
        Log.e(TAG, "Unable to retrieve MapboxNavigation", throwable)
        return
      }

    mapboxNavigation = navigation
    navigation.registerLocationObserver(locationObserver)
    navigation.registerRouteProgressObserver(routeProgressObserver)
    navigation.registerBannerInstructionsObserver(bannerInstructionsObserver)

    // Apply best-effort audio settings once the shared MapboxNavigation instance exists.
    MapboxAudioGuidanceController.setMuted(mute)
    MapboxAudioGuidanceController.setVoiceVolume(voiceVolume)
    MapboxAudioGuidanceController.setLanguage(language)
  }

  private fun detachNavigationObservers() {
    mapboxNavigation?.let { navigation ->
      runCatching { navigation.unregisterLocationObserver(locationObserver) }
      runCatching { navigation.unregisterRouteProgressObserver(routeProgressObserver) }
      runCatching { navigation.unregisterBannerInstructionsObserver(bannerInstructionsObserver) }
    }
    mapboxNavigation = null
  }

  private fun emitBannerInstruction(instruction: BannerInstructions?) {
    val primary = instruction?.primary()?.text()?.trim().orEmpty()
    if (primary.isEmpty()) {
      return
    }
    latestPrimaryInstruction = primary
    latestSecondaryInstruction = instruction?.secondary()?.text()?.trim()?.takeIf { it.isNotEmpty() }
    latestStepDistanceRemaining = instruction?.distanceAlongGeometry()

    val payload = mutableMapOf<String, Any>("primaryText" to primary)
    val secondary = instruction?.secondary()?.text()?.trim().orEmpty()
    if (secondary.isNotEmpty()) {
      payload["secondaryText"] = secondary
    }
    payload["stepDistanceRemaining"] = instruction?.distanceAlongGeometry() ?: 0.0
    onBannerInstruction(payload)
    emitJourneyData(progress = null)
  }

  private fun emitJourneyData(progress: RouteProgress?) {
    val payload = mutableMapOf<String, Any>()
    latestLatitude?.let { payload["latitude"] = it }
    latestLongitude?.let { payload["longitude"] = it }
    latestBearing?.let { payload["bearing"] = it }
    latestSpeed?.let { payload["speed"] = it }
    latestAltitude?.let { payload["altitude"] = it }
    latestAccuracy?.let { payload["accuracy"] = it }
    latestPrimaryInstruction?.let { payload["primaryInstruction"] = it }
    latestSecondaryInstruction?.let { payload["secondaryInstruction"] = it }
    latestStepDistanceRemaining?.let { payload["stepDistanceRemaining"] = it }
    latestSecondaryInstruction?.let { payload["currentStreet"] = it }

    if (progress != null) {
      val distanceRemaining = progress.distanceRemaining.toDouble()
      val durationRemaining = progress.durationRemaining
      val fraction = progress.fractionTraveled.toDouble().coerceIn(0.0, 1.0)
      payload["distanceRemaining"] = distanceRemaining
      payload["durationRemaining"] = durationRemaining
      payload["fractionTraveled"] = fraction
      payload["completionPercent"] = Math.round(fraction * 100.0).toInt()
      val etaMillis = System.currentTimeMillis() + (durationRemaining * 1000.0).toLong()
      payload["etaIso8601"] = formatIsoUtc(etaMillis)
    }

    if (payload.isNotEmpty()) {
      onJourneyDataChange(payload)
    }
  }

  private fun mapRouteFetchFailure(reasons: List<RouterFailure>): Pair<String, String> {
    val details = reasons.joinToString(" | ") { it.message.orEmpty() }.trim()
    if (details.contains("401") || details.contains("unauthorized", ignoreCase = true)) {
      return "MAPBOX_TOKEN_INVALID" to
        "Route fetch failed: unauthorized. Check EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN and token scopes."
    }
    if (details.contains("403") || details.contains("forbidden", ignoreCase = true)) {
      return "MAPBOX_TOKEN_FORBIDDEN" to
        "Route fetch failed: access forbidden. Verify token scopes and account permissions."
    }
    if (details.contains("429") || details.contains("rate", ignoreCase = true)) {
      return "MAPBOX_RATE_LIMITED" to
        "Route fetch failed: rate limited by Mapbox."
    }
    return "ROUTE_FETCH_FAILED" to
      (if (details.isNotEmpty()) "Route fetch failed: $details" else "Route fetch failed for unknown reason.")
  }

  private fun formatIsoUtc(timestampMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return formatter.format(java.util.Date(timestampMillis))
  }

  private fun buildStartSignature(
    destinationPoint: Point,
    originPoint: Point?,
    waypointPoints: List<Point>,
    routeAlternatives: Boolean,
    showsContinuousAlternatives: Boolean
  ): String {
    val parts = mutableListOf<String>()
    originPoint?.let { parts.add("o:${it.longitude()},${it.latitude()}") } ?: parts.add("o:null")
    parts.add("d:${destinationPoint.longitude()},${destinationPoint.latitude()}")
    if (waypointPoints.isNotEmpty()) {
      parts.add("w:" + waypointPoints.joinToString("|") { "${it.longitude()},${it.latitude()}" })
    } else {
      parts.add("w:none")
    }
    parts.add("alt:${routeAlternatives || showsContinuousAlternatives}")
    return parts.joinToString(";")
  }

  private fun getMapboxAccessToken(): String {
    val resourceId = context.resources.getIdentifier(
      "mapbox_access_token",
      "string",
      context.packageName
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

  private fun releaseNavigationSession() {
    if (!ownsNavigationSession) {
      return
    }
    NavigationSessionRegistry.release(sessionOwner)
    ownsNavigationSession = false
    hasPendingSessionConflict = false
  }

  private fun warnUnsupportedOption(key: String, message: String) {
    if (!warnedUnsupportedKeys.add(key)) {
      return
    }
    Log.w(TAG, message)
  }

  private fun hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  private fun resolveDayStyleUri(normalizedTheme: String): String? {
    val single = mapStyleUri.trim().takeIf { it.isNotEmpty() }
    val day = mapStyleUriDay.trim().takeIf { it.isNotEmpty() } ?: single
    val night = mapStyleUriNight.trim().takeIf { it.isNotEmpty() } ?: day
    return when (normalizedTheme) {
      "dark", "night" -> night
      else -> day
    }
  }

  private fun resolveNightStyleUri(normalizedTheme: String, resolvedDay: String?): String? {
    val single = mapStyleUri.trim().takeIf { it.isNotEmpty() }
    val day = mapStyleUriDay.trim().takeIf { it.isNotEmpty() } ?: single
    val night = mapStyleUriNight.trim().takeIf { it.isNotEmpty() } ?: day
    return when (normalizedTheme) {
      "light", "day" -> day ?: resolvedDay
      else -> night ?: resolvedDay
    }
  }

  private fun parseWaypoints(value: List<Map<String, Any>>?): List<Point> {
    if (value.isNullOrEmpty()) {
      return emptyList()
    }
    return value.mapNotNull { item ->
      val latitude = (item["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      val longitude = (item["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return@mapNotNull null
      }
      Point.fromLngLat(longitude, latitude)
    }
  }

  private fun Map<String, Any>?.toAnyPointOrNull(): Point? {
    val map = this ?: return null
    val latitude = (map["latitude"] as? Number)?.toDouble() ?: return null
    val longitude = (map["longitude"] as? Number)?.toDouble() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
      return null
    }
    return Point.fromLngLat(longitude, latitude)
  }
}

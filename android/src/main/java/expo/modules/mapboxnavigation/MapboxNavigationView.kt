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
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.util.UUID

/**
 * Android embedded navigation view.
 *
 * Rationale: Mapbox Drop-In NavigationView can render via SurfaceView in some builds, which frequently
 * appears blank behind ReactRootView in React Native hierarchies. This implementation uses a direct
 * MapView + MapboxNavigation core integration (similar to known-working community packages).
 *
 * iOS implementation is unaffected.
 */
@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class MapboxNavigationView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  companion object {
    private const val TAG = "MapboxNavigationView"
    private const val EMBEDDED_BUILD = "1.1.6-embedded-2026-03-01-r1"
  }

  private val expoAppContext: AppContext = appContext
  private val sessionOwner = "embedded-${UUID.randomUUID()}"

  private val mainHandler = Handler(Looper.getMainLooper())

  /**
   * Internal native layer that always stays as the first child.
   * React Native children are added by the framework as siblings above this layer.
   */
  private val nativeLayer: FrameLayout = FrameLayout(context).apply {
    layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
  }

  private var enabled = false
  private var ownsNavigationSession = false

  private var startOrigin: Map<String, Any>? = null
  private var destination: Map<String, Any>? = null
  private var waypoints: List<Map<String, Any>>? = null

  private var shouldSimulateRoute = false
  private var mute = false
  private var voiceVolume = 1.0
  private var distanceUnit = "metric"
  private var language = "en"
  private var cameraMode = "following"
  private var cameraPitch: Double? = null
  private var cameraZoom: Double? = null

  private var mapStyleUri = ""
  private var mapStyleUriDay = ""
  private var mapStyleUriNight = ""
  private var uiTheme = "system"

  private var routeAlternatives = false
  private var showsContinuousAlternatives = true
  private var showsSpeedLimits = true
  private var showsWayNameLabel = true
  private var showsTripProgress = true
  private var showsManeuverView = true
  private var showsActionButtons = true
  private var showsReportFeedback = true
  private var showsEndOfRouteFeedback = true
  private var usesNightStyleWhileInTunnel = true
  private var routeLineTracksTraversal = false
  private var annotatesIntersectionsAlongRoute = false

  private var mapView: MapView? = null
  private var placeholderView: TextView? = null
  private var currentStyle: Style? = null

  private var mapboxNavigation: MapboxNavigation? = null
  private var hasRequestedRoute = false
  private var hasEmittedArrival = false
  private var hasPrimedTextureView = false
  private var hasEnabledLocationComponent = false
  private var hasSetRouteOverviewCamera = false
  private var lastCameraUpdateAtMs: Long = 0L
  private var navigationLocationProvider: NavigationLocationProvider? = null
  private var changePositionDefaultMethod: java.lang.reflect.Method? = null
  private var replayRouteMapper: ReplayRouteMapper? = null

  private var lastLayoutLogAtMs: Long = 0L
  private var lastLayoutSignature: String = ""
  private var lastHostW: Int = -1
  private var lastHostH: Int = -1

  private var lastLoadedStyleUri: String? = null
  private var hasScheduledFirstLayoutPasses: Boolean = false

  private var routeLineApi: MapboxRouteLineApi? = null
  private var routeLineView: MapboxRouteLineView? = null
  private var lastRenderedRoutes: List<NavigationRoute> = emptyList()

  private var hasRoutesReady: Boolean = false
  private var isGuidanceActive: Boolean = false

  val onLocationChange by EventDispatcher()
  val onRouteProgressChange by EventDispatcher()
  val onJourneyDataChange by EventDispatcher()
  val onBannerInstruction by EventDispatcher()
  val onArrive by EventDispatcher()
  val onDestinationPreview by EventDispatcher()
  val onDestinationChanged by EventDispatcher()
  val onCancelNavigation by EventDispatcher()
  val onError by EventDispatcher()
  val onBottomSheetActionPress by EventDispatcher()

  init {
    // Keep the native layer behind any React-managed children.
    addView(nativeLayer)
  }

  private val locationObserver = object : LocationObserver {
    override fun onNewRawLocation(rawLocation: android.location.Location) = Unit

    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      val location = locationMatcherResult.enhancedLocation
      updateFollowingCamera(location)
      updateLocationPuck(location, locationMatcherResult.keyPoints)
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
      emitJourneyData(
        banner = null,
        progress = null,
        latitude = location.latitude,
        longitude = location.longitude,
        bearing = location.bearing.toDouble(),
        speed = location.speed.toDouble(),
        altitude = location.altitude,
        accuracy = location.accuracy.toDouble()
      )
    }
  }

  private val bannerInstructionsObserver = BannerInstructionsObserver { banner ->
    emitBannerInstruction(banner)
    emitJourneyData(banner = banner, progress = null)
    updateActiveBanner(banner = banner, progress = null)
  }

  private val routeProgressObserver = RouteProgressObserver { progress: RouteProgress ->
    updateRouteLineWithProgress(progress)
    updateActiveBanner(banner = progress.bannerInstructions, progress = progress)
    onRouteProgressChange(
      mapOf(
        "distanceTraveled" to progress.distanceTraveled.toDouble(),
        "distanceRemaining" to progress.distanceRemaining.toDouble(),
        "durationRemaining" to progress.durationRemaining,
        "fractionTraveled" to progress.fractionTraveled.toDouble()
      )
    )
    if (!hasEmittedArrival && progress.distanceRemaining <= 5.0) {
      hasEmittedArrival = true
      val name = (destination?.get("name") as? String)?.trim()?.takeIf { it.isNotEmpty() }
      onArrive(mapOf("name" to (name ?: "Destination")))
    }
    emitBannerInstruction(progress.bannerInstructions)
    emitJourneyData(banner = progress.bannerInstructions, progress = progress)
  }

  private val arrivalObserver = object : ArrivalObserver {
    override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
      val name = (destination?.get("name") as? String)?.trim()?.takeIf { it.isNotEmpty() }
      onArrive(mapOf("name" to (name ?: "Destination")))
    }

    override fun onNextRouteLegStart(routeLegProgress: com.mapbox.navigation.base.trip.model.RouteLegProgress) = Unit
    override fun onWaypointArrival(routeProgress: RouteProgress) = Unit
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    Log.i(TAG, "Embedded view attached ($EMBEDDED_BUILD)")
    // MapView needs lifecycle callbacks to start rendering.
    mapView?.let { mv ->
      runCatching { mv.javaClass.methods.firstOrNull { it.name == "onStart" && it.parameterTypes.isEmpty() }?.invoke(mv) }
    }
    if (enabled) {
      startIfReady()
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    // RN sometimes fails to re-measure/layout children of custom native views unless forced.
    val hostW = right - left
    val hostH = bottom - top
    if (hostW > 0 && hostH > 0) {
      // Only force a full measure pass when needed to avoid continuous re-measure loops that can look like "refreshing".
      val sizeChanged = hostW != lastHostW || hostH != lastHostH
      val textureReady = isTextureViewReady()
      if (sizeChanged || !textureReady) {
        lastHostW = hostW
        lastHostH = hostH
        val wSpec = MeasureSpec.makeMeasureSpec(hostW, MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(hostH, MeasureSpec.EXACTLY)
        nativeLayer.measure(wSpec, hSpec)
        nativeLayer.layout(0, 0, hostW, hostH)
        mapView?.let { mv ->
          mv.measure(wSpec, hSpec)
          mv.layout(0, 0, hostW, hostH)
        }
      } else {
        // Layout only; keep bounds aligned without forcing a re-measure.
        nativeLayer.layout(0, 0, hostW, hostH)
        mapView?.layout(0, 0, hostW, hostH)
      }
    }
    val mapW = mapView?.width ?: -1
    val mapH = mapView?.height ?: -1
    val signature = "h=${hostW}x${hostH};n=${nativeLayer.width}x${nativeLayer.height};m=${mapW}x${mapH}"
    val now = System.currentTimeMillis()
    if (signature != lastLayoutSignature || now - lastLayoutLogAtMs > 1000L) {
      lastLayoutSignature = signature
      lastLayoutLogAtMs = now
      Log.i(TAG, "layout ($EMBEDDED_BUILD): host=${hostW}x${hostH} native=${nativeLayer.width}x${nativeLayer.height} map=${mapW}x${mapH}")
      logMapViewComposition(phase = "layout")
    }

    if (!hasPrimedTextureView && hostW > 0 && hostH > 0) {
      hasPrimedTextureView = true
      mainHandler.post { primeTextureViewIfPresent() }
      mainHandler.postDelayed({ primeTextureViewIfPresent() }, 700L)
    }
  }

  override fun onDetachedFromWindow() {
    stopEmbedded(emitCancel = false)
    super.onDetachedFromWindow()
  }

  fun setNavigationEnabled(next: Boolean) {
    enabled = next
    if (next) {
      startIfReady()
    } else {
      stopEmbedded(emitCancel = false)
    }
  }

  fun setStartOrigin(origin: Map<String, Any>?) {
    startOrigin = origin
    if (enabled) startIfReady()
  }

  fun setDestination(dest: Map<String, Any>?) {
    destination = dest
    hasEmittedArrival = false
    hasRequestedRoute = false
    hasSetRouteOverviewCamera = false
    if (enabled) {
      onDestinationChanged(dest.toAnyPointOrNull()?.let { mapOf("latitude" to it.latitude(), "longitude" to it.longitude()) } ?: emptyMap())
      startIfReady()
    }
  }

  fun setWaypoints(wps: List<Map<String, Any>>?) {
    waypoints = wps
    hasRequestedRoute = false
    hasSetRouteOverviewCamera = false
    if (enabled) startIfReady()
  }

  fun setShouldSimulateRoute(simulate: Boolean) {
    shouldSimulateRoute = simulate
    if (simulate) {
      Log.w(TAG, "shouldSimulateRoute is not currently supported for Android embedded mode ($EMBEDDED_BUILD).")
    }
  }

  fun setShowCancelButton(show: Boolean) {
    if (!show) {
      Log.w(TAG, "showCancelButton is not currently supported by the Android embedded view and will be ignored.")
    }
  }

  fun setMute(muted: Boolean) {
    mute = muted
    MapboxAudioGuidanceController.setMuted(muted)
  }

  fun setVoiceVolume(volume: Double) {
    voiceVolume = volume
    MapboxAudioGuidanceController.setVoiceVolume(volume)
  }

  fun setDistanceUnit(unit: String) {
    distanceUnit = unit
  }

  fun setLanguage(lang: String) {
    language = lang
    MapboxAudioGuidanceController.setLanguage(lang)
  }

  fun setCameraMode(mode: String) {
    cameraMode = mode
  }

  fun setCameraPitch(pitch: Double) {
    cameraPitch = pitch
  }

  fun setCameraZoom(zoom: Double) {
    cameraZoom = zoom
  }

  fun setMapStyleUri(styleUri: String) {
    mapStyleUri = styleUri
    applyStyleIfPossible()
  }

  fun setMapStyleUriDay(styleUri: String) {
    mapStyleUriDay = styleUri
    applyStyleIfPossible()
  }

  fun setMapStyleUriNight(styleUri: String) {
    mapStyleUriNight = styleUri
    applyStyleIfPossible()
  }

  fun setUiTheme(theme: String) {
    uiTheme = theme
    applyStyleIfPossible()
  }

  fun setRouteAlternatives(enabled: Boolean) {
    routeAlternatives = enabled
    hasRequestedRoute = false
    if (this.enabled) startIfReady()
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

  fun setShowsReportFeedback(enabled: Boolean) {
    showsReportFeedback = enabled
    if (!enabled) {
      Log.w(TAG, "showsReportFeedback is not supported by the Android embedded view and will be ignored.")
    }
  }

  fun setShowsEndOfRouteFeedback(enabled: Boolean) {
    showsEndOfRouteFeedback = enabled
    if (!enabled) {
      Log.w(TAG, "showsEndOfRouteFeedback is not supported by the Android embedded view and will be ignored.")
    }
  }

  fun setShowsContinuousAlternatives(enabled: Boolean) {
    showsContinuousAlternatives = enabled
    hasRequestedRoute = false
    if (this.enabled) startIfReady()
  }

  fun setUsesNightStyleWhileInTunnel(enabled: Boolean) {
    usesNightStyleWhileInTunnel = enabled
  }

  fun setRouteLineTracksTraversal(enabled: Boolean) {
    routeLineTracksTraversal = enabled
  }

  fun setAnnotatesIntersectionsAlongRoute(enabled: Boolean) {
    annotatesIntersectionsAlongRoute = enabled
  }

  fun setAndroidActionButtons(androidActionButtons: Map<String, Any>?) {
    if (androidActionButtons != null) {
      Log.w(TAG, "androidActionButtons is not supported by the Android embedded view and will be ignored.")
    }
  }

  private fun showPlaceholder(message: String) {
    if (placeholderView != null) {
      placeholderView?.text = message
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
    placeholderView = view
    nativeLayer.addView(view)
  }

  private fun hidePlaceholder() {
    placeholderView?.let { nativeLayer.removeView(it) }
    placeholderView = null
  }

  private fun dpToPx(dp: Float): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
  }

  private fun hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  private fun getMapboxAccessToken(): String {
    val resId = context.resources.getIdentifier("mapbox_access_token", "string", context.packageName)
    if (resId == 0) {
      throw IllegalStateException("Missing string resource: mapbox_access_token")
    }
    val token = context.getString(resId).trim()
    if (token.isEmpty()) {
      throw IllegalStateException("mapbox_access_token is empty")
    }
    return token
  }

  private fun ensureSession(): Boolean {
    if (ownsNavigationSession) {
      return true
    }
    if (!NavigationSessionRegistry.acquire(sessionOwner)) {
      onError(
        mapOf(
          "code" to "NAVIGATION_SESSION_CONFLICT",
          "message" to "Another navigation session is already active. Stop full-screen or other embedded navigation before mounting this view."
        )
      )
      return false
    }
    ownsNavigationSession = true
    return true
  }

  private fun releaseSession() {
    if (!ownsNavigationSession) return
    NavigationSessionRegistry.release(sessionOwner)
    ownsNavigationSession = false
  }

  private fun ensureMapView() {
    if (mapView != null) return
    val token = runCatching { getMapboxAccessToken() }.getOrElse { throwable ->
      onError(mapOf("code" to "MISSING_ACCESS_TOKEN", "message" to (throwable.message ?: "Missing mapbox_access_token")))
      showPlaceholder("Missing Mapbox access token.\nCheck EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN + prebuild.")
      return
    }
    Log.i(TAG, "mapbox_access_token resolved ($EMBEDDED_BUILD): len=${token.length}")

    // Ensure Maps SDK receives the access token across a wide range of Mapbox Maps SDK versions.
    // Some builds do not auto-read the resValue string; setting it explicitly makes style loading reliable.
    setMapsAccessTokenBestEffort(token)

    // Force TextureView to avoid SurfaceView rendering behind ReactRootView.
    val initOptions = createTextureViewInitOptions()

    val view = MapView(context, initOptions).apply {
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    }
    view.setBackgroundColor(Color.BLACK)

    mapView = view
    nativeLayer.removeView(view)
    // Keep the map as the first child in the native layer.
    nativeLayer.addView(view, 0)
    hidePlaceholder()
    applyStyleIfPossible()
    enableUserLocationPuckBestEffort()
    ensureNavigationLocationProvider()
    ensureEmbeddedOverlay()
    updateEmbeddedOverlay()
    mainHandler.postDelayed({ logStyleStatus(phase = "afterCreate") }, 1500L)

    // If the MapView is added after the host has already been laid out, we may not get another full
    // measure/layout pass from React/Expo. Schedule a few bounded "catch-up" passes to ensure the
    // internal TextureView becomes available, then stop to avoid flicker/refresh loops.
    if (!hasScheduledFirstLayoutPasses) {
      hasScheduledFirstLayoutPasses = true
      mainHandler.post { forceMeasureLayoutOnce("afterAdd:0") }
      mainHandler.postDelayed({ forceMeasureLayoutOnce("afterAdd:200") }, 200L)
      mainHandler.postDelayed({ forceMeasureLayoutOnce("afterAdd:800") }, 800L)
    }

    // Start lifecycle for immediate render.
    runCatching { view.javaClass.methods.firstOrNull { it.name == "onStart" && it.parameterTypes.isEmpty() }?.invoke(view) }
    logMapViewComposition(phase = "created")
    mainHandler.postDelayed({ logMapViewComposition(phase = "post") }, 800L)
    mainHandler.postDelayed({ logTextureViewState(phase = "post") }, 1200L)

    // Move camera to origin if present.
    startOrigin.toAnyPointOrNull()?.let { origin ->
      view.getMapboxMap().setCamera(
        CameraOptions.Builder()
          .center(origin)
          .zoom(cameraZoom ?: 14.0)
          .pitch(cameraPitch ?: 0.0)
          .build()
      )
    }
  }

  private fun ensureNavigation() {
    if (mapboxNavigation != null) return

    val token = getMapboxAccessToken()

    if (!MapboxNavigationProvider.isCreated()) {
      // Create MapboxNavigation via reflection to avoid hard-coupling to specific NavigationOptions API shapes.
      runCatching {
        val navOptionsClass = Class.forName("com.mapbox.navigation.base.options.NavigationOptions")
        val builderClass = Class.forName("com.mapbox.navigation.base.options.NavigationOptions\$Builder")
        val builder = builderClass.getConstructor(Context::class.java).newInstance(context)
        // accessToken(String)
        builderClass.methods.firstOrNull { m ->
          m.name == "accessToken" && m.parameterTypes.size == 1 && m.parameterTypes[0] == String::class.java
        }?.invoke(builder, token)
        val build = builderClass.methods.firstOrNull { it.name == "build" && it.parameterTypes.isEmpty() }
          ?: throw IllegalStateException("NavigationOptions.Builder.build() not found")
        val options = build.invoke(builder)
        val create = MapboxNavigationProvider::class.java.methods.firstOrNull { m ->
          m.name == "create" && m.parameterTypes.size == 1 && m.parameterTypes[0].name == navOptionsClass.name
        } ?: throw IllegalStateException("MapboxNavigationProvider.create(NavigationOptions) not found")
        create.invoke(null, options)
      }.onFailure { throwable ->
        Log.w(TAG, "Failed to create MapboxNavigationProvider via NavigationOptions reflection", throwable)
      }
    }

    val nav = runCatching { MapboxNavigationProvider.retrieve() }.getOrElse { throwable ->
      onError(mapOf("code" to "NAVIGATION_INIT_FAILED", "message" to (throwable.message ?: "Failed to init MapboxNavigation")))
      showPlaceholder("Failed to init navigation.\n${throwable.message ?: ""}".trim())
      return
    }

    mapboxNavigation = nav
    nav.registerLocationObserver(locationObserver)
    nav.registerRouteProgressObserver(routeProgressObserver)
    nav.registerBannerInstructionsObserver(bannerInstructionsObserver)
    nav.registerArrivalObserver(arrivalObserver)

    MapboxAudioGuidanceController.setMuted(mute)
    MapboxAudioGuidanceController.setVoiceVolume(voiceVolume)
    MapboxAudioGuidanceController.setLanguage(language)
  }

  private fun startIfReady() {
    if (!enabled) return
    Log.i(TAG, "startIfReady ($EMBEDDED_BUILD): enabled=true hasPerm=${hasLocationPermission()} ownsSession=$ownsNavigationSession")
    if (!hasLocationPermission()) {
      showPlaceholder("Location permission required.\nGrant ACCESS_FINE_LOCATION to start embedded navigation.")
      onError(mapOf("code" to "LOCATION_PERMISSION_REQUIRED", "message" to "Embedded navigation requires location permission."))
      return
    }
    if (!ensureSession()) {
      showPlaceholder("Navigation session conflict.\nStop other navigation sessions first.")
      return
    }

    hidePlaceholder()
    ensureMapView()
    ensureNavigation()

    if (mapView == null || mapboxNavigation == null) {
      return
    }

    val origin = startOrigin.toAnyPointOrNull()
    val dest = destination.toAnyPointOrNull()
    if (origin == null || dest == null) {
      Log.i(TAG, "startIfReady: waiting for startOrigin/destination ($EMBEDDED_BUILD)")
      showPlaceholder("Waiting for startOrigin + destination…")
      return
    }

    if (waypoints?.isNotEmpty() == true) {
      // Require explicit origin when using waypoints to match prior behavior and avoid ambiguous routing.
      if (startOrigin == null) {
        onError(mapOf("code" to "INVALID_COORDINATES", "message" to "waypoints require startOrigin in Android embedded mode."))
        showPlaceholder("Invalid options: waypoints require startOrigin.")
        return
      }
    }

    if (hasRequestedRoute) {
      return
    }
    hasRequestedRoute = true
    hasEmittedArrival = false
    hasRoutesReady = false
    isGuidanceActive = false
    onDestinationPreview(mapOf("active" to true))

    val allWaypoints = parseWaypoints(waypoints)
    val coordinates = mutableListOf<Point>()
    coordinates.add(origin)
    coordinates.addAll(allWaypoints)
    coordinates.add(dest)
    Log.i(TAG, "requestRoutes: coords=${coordinates.size} alt=${routeAlternatives || showsContinuousAlternatives} ($EMBEDDED_BUILD)")

    val routeOptions = RouteOptions.builder()
      .applyDefaultNavigationOptions()
      .applyLanguageAndVoiceUnitOptions(context)
      .coordinatesList(coordinates)
      .steps(true)
      .voiceInstructions(true)
      .alternatives(routeAlternatives || showsContinuousAlternatives)
      .language(language.trim().ifEmpty { "en" })
      .voiceUnits(if (distanceUnit.trim().lowercase() == "imperial") "imperial" else "metric")
      .build()

    mapboxNavigation?.requestRoutes(
      routeOptions,
      object : NavigationRouterCallback {
        override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: RouterOrigin) {
          Log.i(TAG, "onRoutesReady: count=${routes.size} origin=$routerOrigin ($EMBEDDED_BUILD)")
          if (routes.isEmpty()) {
            hasRequestedRoute = false
            onError(mapOf("code" to "NO_ROUTE", "message" to "No route found"))
            showPlaceholder("No route found.")
            return
          }
          val nav = mapboxNavigation ?: return
          nav.setNavigationRoutes(routes)
          // Render route preview line on the map.
          lastRenderedRoutes = routes
          ensureRouteLineComponents()
          mainHandler.post { renderRoutesToMapIfReady() }

          // One-time route overview camera (mirrors drop-in route preview).
          if (!hasSetRouteOverviewCamera) {
            hasSetRouteOverviewCamera = true
            val overviewPoints = buildList {
              add(origin)
              addAll(allWaypoints)
              add(dest)
            }
            mainHandler.post { setOverviewCameraBestEffort(overviewPoints) }
          }

          hasRoutesReady = true
          isGuidanceActive = false
          mainHandler.post { updateEmbeddedOverlay() }
        }

        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
          Log.w(TAG, "onFailure: reasons=${reasons.size} ($EMBEDDED_BUILD)")
          hasRequestedRoute = false
          val message = reasons.joinToString(", ") { it.message ?: it.toString() }
          onError(mapOf("code" to "ROUTE_ERROR", "message" to "Route fetch failed: $message"))
          showPlaceholder("Route fetch failed.\n$message")
          hasRoutesReady = false
          isGuidanceActive = false
          mainHandler.post { updateEmbeddedOverlay() }
        }

        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
          Log.w(TAG, "onCanceled: origin=$routerOrigin ($EMBEDDED_BUILD)")
          hasRequestedRoute = false
          onError(mapOf("code" to "ROUTE_FETCH_CANCELED", "message" to "Route fetch canceled (origin: $routerOrigin)."))
          hasRoutesReady = false
          isGuidanceActive = false
          mainHandler.post { updateEmbeddedOverlay() }
        }
      }
    )
  }

  private fun stopEmbedded(emitCancel: Boolean) {
    mainHandler.removeCallbacksAndMessages(null)

    mapboxNavigation?.let { nav ->
      runCatching { nav.unregisterLocationObserver(locationObserver) }
      runCatching { nav.unregisterRouteProgressObserver(routeProgressObserver) }
      runCatching { nav.unregisterBannerInstructionsObserver(bannerInstructionsObserver) }
      runCatching { nav.unregisterArrivalObserver(arrivalObserver) }
      runCatching { nav.setNavigationRoutes(emptyList()) }
      runCatching { nav.stopTripSession() }
      runCatching { nav.mapboxReplayer.stop() }
      runCatching { nav.mapboxReplayer.clearEvents() }
    }
    mapboxNavigation = null

    runCatching { routeLineApi?.cancel() }
    runCatching { routeLineView?.cancel() }
    routeLineApi = null
    routeLineView = null
    lastRenderedRoutes = emptyList()
    currentStyle = null
    hasEnabledLocationComponent = false
    navigationLocationProvider = null
    changePositionDefaultMethod = null
    replayRouteMapper = null
    hasSetRouteOverviewCamera = false
    lastCameraUpdateAtMs = 0L
    hasRoutesReady = false
    isGuidanceActive = false
    removeEmbeddedOverlay()

    mapView?.let { mv ->
      runCatching { mv.javaClass.methods.firstOrNull { it.name == "onStop" && it.parameterTypes.isEmpty() }?.invoke(mv) }
      runCatching { mv.javaClass.methods.firstOrNull { it.name == "onDestroy" && it.parameterTypes.isEmpty() }?.invoke(mv) }
    }
    mapView?.let { nativeLayer.removeView(it) }
    mapView = null
    hasScheduledFirstLayoutPasses = false

    hidePlaceholder()
    hasRequestedRoute = false
    hasEmittedArrival = false
    if (emitCancel) {
      onCancelNavigation(emptyMap())
    }
    releaseSession()
  }

  private fun applyStyleIfPossible() {
    val mv = mapView ?: return
    val normalizedTheme = uiTheme.trim().lowercase()
    val single = mapStyleUri.trim().takeIf { it.isNotEmpty() }
    val day = mapStyleUriDay.trim().takeIf { it.isNotEmpty() } ?: single
    val night = mapStyleUriNight.trim().takeIf { it.isNotEmpty() } ?: day
    val resolved = when (normalizedTheme) {
      "dark", "night" -> night
      else -> day
    } ?: "mapbox://styles/mapbox/navigation-day-v1"
    val alreadyLoaded = (lastLoadedStyleUri == resolved) && runCatching {
      val mapboxMap = mv.getMapboxMap()
      val getStyle = mapboxMap.javaClass.methods.firstOrNull { it.name == "getStyle" && it.parameterTypes.isEmpty() }
      getStyle?.invoke(mapboxMap) != null
    }.getOrDefault(false)
    if (alreadyLoaded) {
      return
    }
    lastLoadedStyleUri = resolved
    Log.i(TAG, "loadStyleUri ($EMBEDDED_BUILD): $resolved")
    runCatching {
      mv.getMapboxMap().loadStyleUri(resolved) { style ->
        currentStyle = style
        ensureRouteLineComponents()
        renderRoutesToMapIfReady()
      }
    }.onFailure {
      // Fallback overload.
      runCatching { mv.getMapboxMap().loadStyleUri(resolved) }
    }
    mainHandler.postDelayed({ logStyleStatus(phase = "afterLoadStyleUri") }, 2000L)
  }

  private fun ensureRouteLineComponents() {
    if (routeLineApi != null && routeLineView != null) return
    val options = MapboxRouteLineOptions.Builder(context).build()
    routeLineApi = MapboxRouteLineApi(options)
    routeLineView = MapboxRouteLineView(options)
    currentStyle?.let { style ->
      runCatching { routeLineView?.initializeLayers(style) }
    }
  }

  // --- Embedded overlay UI (preview + start/stop) ---

  private var overlayRoot: FrameLayout? = null
  private var previewCard: ViewGroup? = null
  private var previewTitle: TextView? = null
  private var previewFrom: TextView? = null
  private var previewTo: TextView? = null
  private var previewMeta: TextView? = null
  private var startButton: TextView? = null
  private var overviewButton: TextView? = null

  private var activeBannerCard: ViewGroup? = null
  private var activePrimary: TextView? = null
  private var activeSecondary: TextView? = null
  private var stopButton: TextView? = null

  private fun ensureEmbeddedOverlay() {
    if (overlayRoot != null) return
    val root = FrameLayout(context).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    }

    // Preview card (route preview + Start button) - modern bottom sheet style.
    val pCard = LinearLayout(context).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = Gravity.BOTTOM
        leftMargin = dpToPx(12f)
        rightMargin = dpToPx(12f)
        bottomMargin = dpToPx(18f)
      }
      orientation = LinearLayout.VERTICAL
      setPadding(dpToPx(16f), dpToPx(14f), dpToPx(16f), dpToPx(14f))
      background = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dpToPx(18f).toFloat()
        setColor(Color.parseColor("#0f172a")) // slate-900
        setStroke(dpToPx(1f), Color.parseColor("#1e293b")) // slate-800
      }
      elevation = dpToPx(10f).toFloat()
    }
    val headerRow = LinearLayout(context).apply {
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    val pTitle = TextView(context).apply {
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      setTypeface(typeface, android.graphics.Typeface.BOLD)
      text = "Trip preview"
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    val badge = TextView(context).apply {
      setTextColor(Color.parseColor("#93c5fd"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
      setTypeface(typeface, android.graphics.Typeface.BOLD)
      text = "EMBEDDED"
      setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
      background = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dpToPx(999f).toFloat()
        setColor(Color.parseColor("#0b1020"))
        setStroke(dpToPx(1f), Color.parseColor("#1e293b"))
      }
    }
    headerRow.addView(pTitle)
    headerRow.addView(badge)

    val fromText = TextView(context).apply {
      setTextColor(Color.parseColor("#e2e8f0")) // slate-200
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      text = "From: …"
      setPadding(0, dpToPx(10f), 0, 0)
    }
    val toText = TextView(context).apply {
      setTextColor(Color.parseColor("#e2e8f0"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      text = "To: …"
      setPadding(0, dpToPx(4f), 0, 0)
    }
    val metaText = TextView(context).apply {
      setTextColor(Color.parseColor("#93c5fd")) // blue-300
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      text = "Loading route…"
      setPadding(0, dpToPx(8f), 0, 0)
    }

    val divider = View(context).apply {
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dpToPx(1f)
      ).apply { topMargin = dpToPx(12f) }
      setBackgroundColor(Color.parseColor("#1e293b"))
    }

    val buttonsRow = LinearLayout(context).apply {
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply { topMargin = dpToPx(12f) }
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }

    val overviewBtn = TextView(context).apply {
      setPadding(dpToPx(14f), dpToPx(10f), dpToPx(14f), dpToPx(10f))
      setTextColor(Color.parseColor("#e2e8f0"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(typeface, android.graphics.Typeface.BOLD)
      text = "Overview"
      background = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dpToPx(14f).toFloat()
        setColor(Color.parseColor("#1e293b"))
        setStroke(dpToPx(1f), Color.parseColor("#334155"))
      }
      setOnClickListener { setOverviewCameraBestEffort(buildOverviewPoints()) }
    }
    val spacer = View(context).apply {
      layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
    }
    val startBtn = TextView(context).apply {
      setPadding(dpToPx(16f), dpToPx(10f), dpToPx(16f), dpToPx(10f))
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      setTypeface(typeface, android.graphics.Typeface.BOLD)
      text = "Start"
      background = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dpToPx(14f).toFloat()
        setColor(Color.parseColor("#2563eb"))
      }
      setOnClickListener { startEmbeddedGuidance() }
    }

    buttonsRow.addView(overviewBtn)
    buttonsRow.addView(spacer)
    buttonsRow.addView(startBtn)

    pCard.addView(headerRow)
    pCard.addView(fromText)
    pCard.addView(toText)
    pCard.addView(metaText)
    pCard.addView(divider)
    pCard.addView(buttonsRow)

    // Active banner (simple maneuver + Stop)
    val aCard = LinearLayout(context).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = Gravity.TOP
        leftMargin = dpToPx(12f)
        rightMargin = dpToPx(12f)
        topMargin = dpToPx(12f)
      }
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dpToPx(14f), dpToPx(12f), dpToPx(14f), dpToPx(12f))
      background = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dpToPx(16f).toFloat()
        setColor(Color.parseColor("#0b1020")) // deep
        setStroke(dpToPx(1f), Color.parseColor("#1e293b"))
      }
      elevation = dpToPx(10f).toFloat()
      visibility = View.GONE
    }
    val aTextCol = LinearLayout(context).apply {
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      orientation = LinearLayout.VERTICAL
    }
    val aPrimary = TextView(context).apply {
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setTypeface(typeface, android.graphics.Typeface.BOLD)
      text = "Starting…"
    }
    val aSecondary = TextView(context).apply {
      setTextColor(Color.parseColor("#93c5fd")) // blue-300
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setPadding(0, dpToPx(3f), 0, 0)
      text = ""
    }
    val stopBtn = TextView(context).apply {
      setPadding(dpToPx(12f), dpToPx(8f), dpToPx(12f), dpToPx(8f))
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      setTypeface(typeface, android.graphics.Typeface.BOLD)
      text = "Stop"
      background = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = dpToPx(14f).toFloat()
        setColor(Color.parseColor("#ef4444")) // red-500
      }
      setOnClickListener { stopEmbedded(emitCancel = true) }
    }
    aTextCol.addView(aPrimary)
    aTextCol.addView(aSecondary)
    aCard.addView(aTextCol)
    aCard.addView(stopBtn)

    root.addView(aCard)
    root.addView(pCard)

    overlayRoot = root
    previewCard = pCard
    previewTitle = pTitle
    previewFrom = fromText
    previewTo = toText
    previewMeta = metaText
    startButton = startBtn
    overviewButton = overviewBtn
    activeBannerCard = aCard
    activePrimary = aPrimary
    activeSecondary = aSecondary
    stopButton = stopBtn

    nativeLayer.addView(root)
  }

  private fun removeEmbeddedOverlay() {
    overlayRoot?.let { nativeLayer.removeView(it) }
    overlayRoot = null
    previewCard = null
    previewTitle = null
    previewFrom = null
    previewTo = null
    previewMeta = null
    startButton = null
    overviewButton = null
    activeBannerCard = null
    activePrimary = null
    activeSecondary = null
    stopButton = null
  }

  private fun buildOverviewPoints(): List<Point> {
    val origin = startOrigin.toAnyPointOrNull() ?: return emptyList()
    val dest = destination.toAnyPointOrNull() ?: return emptyList()
    val mid = parseWaypoints(waypoints)
    return buildList {
      add(origin)
      addAll(mid)
      add(dest)
    }
  }

  private fun updateEmbeddedOverlay() {
    if (!showsActionButtons) {
      previewCard?.visibility = View.GONE
      activeBannerCard?.visibility = View.GONE
      return
    }

    val hasRoute = hasRoutesReady && lastRenderedRoutes.isNotEmpty()
    val fromName = (startOrigin?.get("name") as? String)?.trim().takeIf { !it.isNullOrEmpty() } ?: "Current location"
    val toName = (destination?.get("name") as? String)?.trim().takeIf { !it.isNullOrEmpty() } ?: "Destination"

    val primaryRoute = lastRenderedRoutes.firstOrNull()?.directionsRoute
    val km = primaryRoute?.distance()?.let { it / 1000.0 }
    val mins = primaryRoute?.duration()?.let { it / 60.0 }
    val meta = when {
      km != null && mins != null -> String.format("≈ %.1f km • %d min", km, Math.round(mins).toInt())
      km != null -> String.format("≈ %.1f km", km)
      else -> ""
    }

    if (!isGuidanceActive) {
      activeBannerCard?.visibility = View.GONE
      previewCard?.visibility = View.VISIBLE
      previewTitle?.text = "Trip preview"
      previewFrom?.text = "From: $fromName"
      previewTo?.text = "To: $toName"
      previewMeta?.text = if (hasRoute) meta.ifEmpty { "Ready" } else "Loading route…"
      startButton?.alpha = if (hasRoute) 1.0f else 0.5f
      startButton?.isEnabled = hasRoute
    } else {
      previewCard?.visibility = View.GONE
      activeBannerCard?.visibility = View.VISIBLE
    }
  }

  private fun startEmbeddedGuidance() {
    if (!showsActionButtons) return
    if (isGuidanceActive) return
    if (!hasRoutesReady || lastRenderedRoutes.isEmpty()) return
    val nav = mapboxNavigation ?: return
    isGuidanceActive = true
    mainHandler.post { updateEmbeddedOverlay() }
    // Move from preview into navigator, then start the appropriate trip session.
    runCatching { nav.setRoutesPreview(lastRenderedRoutes) }
    runCatching { nav.moveRoutesFromPreviewToNavigator() }
    runCatching { nav.setNavigationRoutes(lastRenderedRoutes) }

    if (shouldSimulateRoute) {
      val primary = lastRenderedRoutes.firstOrNull()?.directionsRoute
      if (primary != null) {
        val mapper = replayRouteMapper ?: ReplayRouteMapper().also { replayRouteMapper = it }
        runCatching {
          val events = mapper.mapDirectionsRouteGeometry(primary)
          nav.mapboxReplayer.clearEvents()
          nav.mapboxReplayer.pushEvents(events)
          nav.startReplayTripSession()
          nav.mapboxReplayer.playFirstLocation()
          nav.mapboxReplayer.play()
        }.onFailure { throwable ->
          Log.w(TAG, "Failed to start replay trip session ($EMBEDDED_BUILD)", throwable)
          runCatching { nav.startTripSession() }
        }
      } else {
        runCatching { nav.startReplayTripSession() }
      }
    } else {
      runCatching { nav.startTripSession() }
    }

    // Switch camera to following when guidance starts.
    lastCameraUpdateAtMs = 0L
  }

  private fun updateActiveBanner(banner: BannerInstructions?, progress: RouteProgress?) {
    if (!isGuidanceActive) return
    val primaryText = banner?.primary()?.text()?.trim().orEmpty()
    if (primaryText.isNotEmpty()) {
      activePrimary?.text = primaryText
    }
    if (progress != null) {
      val meters = Math.round(progress.distanceRemaining).toInt()
      val mins = Math.round(progress.durationRemaining / 60.0).toInt()
      activeSecondary?.text = "Remaining: ${meters}m • ${mins}min"
    } else {
      val secondaryText = banner?.secondary()?.text()?.trim().orEmpty()
      if (secondaryText.isNotEmpty()) {
        activeSecondary?.text = secondaryText
      }
    }
  }

  private fun renderRoutesToMapIfReady() {
    val style = currentStyle ?: return
    val api = routeLineApi ?: return
    val view = routeLineView ?: return
    val routes = lastRenderedRoutes
    if (routes.isEmpty()) return
    Log.i(TAG, "renderRoutesToMapIfReady: count=${routes.size} ($EMBEDDED_BUILD)")
    api.setNavigationRoutes(routes) { value ->
      view.renderRouteDrawData(style, value)
    }
  }

  private fun updateRouteLineWithProgress(progress: RouteProgress) {
    val style = currentStyle ?: return
    val api = routeLineApi ?: return
    val view = routeLineView ?: return
    api.updateWithRouteProgress(progress) { result ->
      view.renderRouteLineUpdate(style, result)
    }
  }

  private fun updateFollowingCamera(location: android.location.Location) {
    if (cameraMode.trim().lowercase() != "following") return
    val mv = mapView ?: return
    val now = System.currentTimeMillis()
    if (now - lastCameraUpdateAtMs < 600L) return
    lastCameraUpdateAtMs = now

    val center = Point.fromLngLat(location.longitude, location.latitude)
    runCatching {
      mv.getMapboxMap().setCamera(
        CameraOptions.Builder()
          .center(center)
          .zoom(cameraZoom ?: 15.5)
          .pitch(cameraPitch ?: 45.0)
          .bearing(location.bearing.toDouble())
          .build()
      )
    }
  }

  private fun setOverviewCameraBestEffort(points: List<Point>) {
    val mv = mapView ?: return
    if (points.isEmpty()) return
    val mapboxMap = mv.getMapboxMap()

    val top = dpToPx(60f).toDouble()
    val left = dpToPx(24f).toDouble()
    val right = dpToPx(24f).toDouble()
    val bottom = dpToPx(220f).toDouble()

    val camera = runCatching {
      val edgeInsetsClass = Class.forName("com.mapbox.maps.EdgeInsets")
      val insets = edgeInsetsClass.getConstructor(
        Double::class.javaPrimitiveType,
        Double::class.javaPrimitiveType,
        Double::class.javaPrimitiveType,
        Double::class.javaPrimitiveType
      ).newInstance(top, left, bottom, right)
      val method = mapboxMap.javaClass.methods.firstOrNull { m ->
        m.name == "cameraForCoordinates" &&
          m.parameterTypes.size == 4 &&
          java.util.List::class.java.isAssignableFrom(m.parameterTypes[0]) &&
          m.parameterTypes[1].name == edgeInsetsClass.name
      }
      method?.invoke(mapboxMap, points, insets, null, null) as? CameraOptions
    }.getOrNull()

    if (camera != null) {
      runCatching { mapboxMap.setCamera(camera) }
      return
    }

    val start = points.first()
    val end = points.last()
    val center = Point.fromLngLat(
      (start.longitude() + end.longitude()) / 2.0,
      (start.latitude() + end.latitude()) / 2.0
    )
    runCatching {
      mapboxMap.setCamera(
        CameraOptions.Builder()
          .center(center)
          .zoom(cameraZoom ?: 12.5)
          .pitch(0.0)
          .build()
      )
    }
  }

  private fun enableUserLocationPuckBestEffort() {
    if (hasEnabledLocationComponent) return
    if (!hasLocationPermission()) return
    val mv = mapView ?: return
    hasEnabledLocationComponent = true
    runCatching {
      mv.location.updateSettings {
        enabled = true
      }
    }.onFailure { throwable ->
      hasEnabledLocationComponent = false
      Log.w(TAG, "Failed to enable Mapbox location component ($EMBEDDED_BUILD)", throwable)
    }
  }

  private fun ensureNavigationLocationProvider() {
    if (!hasLocationPermission()) return
    val mv = mapView ?: return
    if (navigationLocationProvider != null) return

    val provider = NavigationLocationProvider()
    navigationLocationProvider = provider
    // Make the location component read locations from Navigation (so puck matches enhanced location).
    runCatching { mv.location.setLocationProvider(provider) }

    if (changePositionDefaultMethod == null) {
      changePositionDefaultMethod = runCatching {
        NavigationLocationProvider::class.java.getDeclaredMethod(
          "changePosition\$default",
          NavigationLocationProvider::class.java,
          android.location.Location::class.java,
          java.util.List::class.java,
          kotlin.jvm.functions.Function1::class.java,
          kotlin.jvm.functions.Function1::class.java,
          Int::class.javaPrimitiveType,
          Any::class.java
        )
      }.getOrNull()
    }
  }

  private fun updateLocationPuck(location: android.location.Location, keyPoints: List<android.location.Location>?) {
    if (!hasLocationPermission()) return
    val provider = navigationLocationProvider ?: run {
      ensureNavigationLocationProvider()
      navigationLocationProvider
    } ?: return

    val points = keyPoints ?: emptyList()
    // Call Kotlin default-args helper to avoid providing animator-option lambdas.
    val method = changePositionDefaultMethod
    if (method != null) {
      runCatching {
        // mask=12 -> use defaults for the last 2 params (animator option lambdas)
        method.invoke(null, provider, location, points, null, null, 12, null)
      }
      return
    }

    // Fallback: attempt direct invocation (works if function params are nullable in this SDK build).
    runCatching { provider.changePosition(location, points, null, null) }
  }

  private fun emitBannerInstruction(instruction: BannerInstructions?) {
    val primary = instruction?.primary()?.text()?.trim().orEmpty()
    if (primary.isEmpty()) return
    val payload = mutableMapOf<String, Any>("primaryText" to primary)
    val secondary = instruction?.secondary()?.text()?.trim().orEmpty()
    if (secondary.isNotEmpty()) {
      payload["secondaryText"] = secondary
    }
    payload["stepDistanceRemaining"] = instruction?.distanceAlongGeometry() ?: 0.0
    onBannerInstruction(payload)
  }

  private fun emitJourneyData(
    banner: BannerInstructions?,
    progress: RouteProgress?,
    latitude: Double? = null,
    longitude: Double? = null,
    bearing: Double? = null,
    speed: Double? = null,
    altitude: Double? = null,
    accuracy: Double? = null,
  ) {
    val payload = mutableMapOf<String, Any>()
    latitude?.let { payload["latitude"] = it }
    longitude?.let { payload["longitude"] = it }
    bearing?.let { payload["bearing"] = it }
    speed?.let { payload["speed"] = it }
    altitude?.let { payload["altitude"] = it }
    accuracy?.let { payload["accuracy"] = it }

    val primary = banner?.primary()?.text()?.trim()?.takeIf { it.isNotEmpty() }
    val secondary = banner?.secondary()?.text()?.trim()?.takeIf { it.isNotEmpty() }
    primary?.let { payload["primaryInstruction"] = it }
    secondary?.let {
      payload["secondaryInstruction"] = it
      payload["currentStreet"] = it
    }
    banner?.distanceAlongGeometry()?.let { payload["stepDistanceRemaining"] = it }

    if (progress != null) {
      payload["distanceRemaining"] = progress.distanceRemaining.toDouble()
      payload["durationRemaining"] = progress.durationRemaining
      payload["fractionTraveled"] = progress.fractionTraveled.toDouble().coerceIn(0.0, 1.0)
      payload["completionPercent"] = Math.round(progress.fractionTraveled.toDouble().coerceIn(0.0, 1.0) * 100.0).toInt()
    }

    onJourneyDataChange(payload)
  }

  private fun parseWaypoints(value: List<Map<String, Any>>?): List<Point> {
    if (value.isNullOrEmpty()) return emptyList()
    return value.mapNotNull { item ->
      val lat = (item["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      val lng = (item["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null
      Point.fromLngLat(lng, lat)
    }
  }

  private fun Map<String, Any>?.toAnyPointOrNull(): Point? {
    val map = this ?: return null
    val lat = (map["latitude"] as? Number)?.toDouble() ?: return null
    val lng = (map["longitude"] as? Number)?.toDouble() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return Point.fromLngLat(lng, lat)
  }

  private fun createTextureViewInitOptions(): MapInitOptions {
    // We cannot reference MapInitOptions.Builder directly because some Maps SDK versions don't ship it.
    // Use reflection so compilation succeeds regardless of the app's resolved Maps SDK.
    runCatching {
      val builderClass = Class.forName("com.mapbox.maps.MapInitOptions\$Builder")
      val builder = builderClass.getConstructor(Context::class.java).newInstance(context)
      builderClass.methods.firstOrNull { m ->
        m.name == "textureView" &&
          m.parameterTypes.size == 1 &&
          (m.parameterTypes[0] == Boolean::class.javaPrimitiveType || m.parameterTypes[0] == java.lang.Boolean::class.java)
      }?.invoke(builder, true)
      val build = builderClass.methods.firstOrNull { it.name == "build" && it.parameterTypes.isEmpty() }
        ?: throw IllegalStateException("MapInitOptions.Builder.build() not found")
      val built = build.invoke(builder)
      if (built is MapInitOptions) return built
    }.onFailure { throwable ->
      Log.w(TAG, "MapInitOptions.Builder path unavailable; falling back ($EMBEDDED_BUILD)", throwable)
    }

    // Fallback for older Maps SDK builds.
    return MapInitOptions(context).also { opts ->
      runCatching {
        val method = opts.javaClass.methods.firstOrNull { m ->
          m.parameterTypes.size == 1 &&
            (m.parameterTypes[0] == Boolean::class.javaPrimitiveType || m.parameterTypes[0] == java.lang.Boolean::class.java) &&
            (m.name == "setTextureView" || m.name == "textureView")
        }
        method?.invoke(opts, true)
      }
      // Some versions expose translucent texture surface toggles via mapOptions; disable if possible to avoid "ghosting".
      disableTranslucentTextureSurfaceBestEffort(opts)
    }
  }

  private fun disableTranslucentTextureSurfaceBestEffort(initOptions: Any) {
    runCatching {
      val getMapOptions = initOptions.javaClass.methods.firstOrNull { it.name == "getMapOptions" && it.parameterTypes.isEmpty() }
      val mapOptions = getMapOptions?.invoke(initOptions) ?: return
      // Look for `translucentTextureSurface(Boolean)` / `setTranslucentTextureSurface(Boolean)`
      val m1 = mapOptions.javaClass.methods.firstOrNull { m ->
        (m.name == "translucentTextureSurface" || m.name == "setTranslucentTextureSurface") &&
          m.parameterTypes.size == 1 &&
          (m.parameterTypes[0] == Boolean::class.javaPrimitiveType || m.parameterTypes[0] == java.lang.Boolean::class.java)
      }
      m1?.invoke(mapOptions, false)
    }
  }

  private fun primeTextureViewIfPresent() {
    val mv = mapView ?: return
    val texture = findFirstTextureView(mv) ?: run {
      Log.w(TAG, "TextureView not found inside MapView ($EMBEDDED_BUILD)")
      return
    }
    // Make sure the render surface is opaque and fully visible.
    runCatching { texture.alpha = 1f }
    runCatching { texture.isOpaque = true }
    runCatching { texture.setBackgroundColor(Color.BLACK) }
    runCatching { texture.setLayerType(View.LAYER_TYPE_HARDWARE, null) }
    logTextureViewState(phase = "primed")
  }

  private fun findFirstTextureView(root: View): TextureView? {
    if (root is TextureView) return root
    if (root is ViewGroup) {
      for (i in 0 until root.childCount) {
        val found = findFirstTextureView(root.getChildAt(i))
        if (found != null) return found
      }
    }
    return null
  }

  private fun isTextureViewReady(): Boolean {
    val mv = mapView ?: return false
    val texture = findFirstTextureView(mv) ?: return false
    val available = runCatching { texture.isAvailable }.getOrDefault(false)
    return available && texture.width > 0 && texture.height > 0
  }

  private fun forceMeasureLayoutOnce(reason: String) {
    val hostW = if (width > 0) width else lastHostW
    val hostH = if (height > 0) height else lastHostH
    if (hostW <= 0 || hostH <= 0) return
    val wSpec = MeasureSpec.makeMeasureSpec(hostW, MeasureSpec.EXACTLY)
    val hSpec = MeasureSpec.makeMeasureSpec(hostH, MeasureSpec.EXACTLY)
    nativeLayer.measure(wSpec, hSpec)
    nativeLayer.layout(0, 0, hostW, hostH)
    mapView?.let { mv ->
      mv.measure(wSpec, hSpec)
      mv.layout(0, 0, hostW, hostH)
    }
    Log.i(
      TAG,
      "forceMeasureLayoutOnce ($EMBEDDED_BUILD): reason=$reason host=${hostW}x${hostH} textureReady=${isTextureViewReady()}"
    )
  }

  private fun logTextureViewState(phase: String) {
    val mv = mapView ?: return
    val texture = findFirstTextureView(mv) ?: return
    val available = runCatching { texture.isAvailable }.getOrDefault(false)
    val opaque = runCatching { texture.isOpaque }.getOrDefault(false)
    Log.i(
      TAG,
      "textureView ($EMBEDDED_BUILD): phase=$phase avail=$available opaque=$opaque alpha=${texture.alpha} vis=${texture.visibility} w=${texture.width} h=${texture.height}"
    )
  }

  private fun setMapsAccessTokenBestEffort(token: String) {
    val candidates = listOf(
      "com.mapbox.maps.MapboxOptions",
      "com.mapbox.maps.MapboxMapsOptions",
      "com.mapbox.common.MapboxOptions",
    )
    for (className in candidates) {
      val ok = runCatching {
        val clazz = Class.forName(className)
        val instance = runCatching { clazz.getField("INSTANCE").get(null) }.getOrNull()
        val target = instance ?: clazz

        // Kotlin object property: setAccessToken(String)
        val setter = target.javaClass.methods.firstOrNull { m ->
          m.name == "setAccessToken" && m.parameterTypes.size == 1 && m.parameterTypes[0] == String::class.java
        }
        if (setter != null) {
          setter.invoke(target, token)
          Log.i(TAG, "maps access token set via $className.setAccessToken ($EMBEDDED_BUILD)")
          return
        }

        // Java-style static: accessToken(String)
        val method = target.javaClass.methods.firstOrNull { m ->
          (m.name == "accessToken" || m.name == "setToken") &&
            m.parameterTypes.size == 1 &&
            m.parameterTypes[0] == String::class.java
        }
        if (method != null) {
          method.invoke(target, token)
          Log.i(TAG, "maps access token set via $className.${method.name} ($EMBEDDED_BUILD)")
          return
        }
      }.isSuccess
      if (ok) return
    }
    Log.w(TAG, "maps access token setter not found; style load may fail ($EMBEDDED_BUILD)")
  }

  private fun logStyleStatus(phase: String) {
    val mv = mapView ?: return
    val mapboxMap = mv.getMapboxMap()
    val styleLoaded = runCatching {
      // Some versions expose getStyle(); others expose a `style` property.
      val getStyle = mapboxMap.javaClass.methods.firstOrNull { it.name == "getStyle" && it.parameterTypes.isEmpty() }
      val style = getStyle?.invoke(mapboxMap)
      style != null
    }.getOrDefault(false)
    Log.i(TAG, "style status ($EMBEDDED_BUILD): phase=$phase loaded=$styleLoaded")
  }

  private fun logMapViewComposition(phase: String) {
    val mv = mapView ?: return
    val (textureCount, surfaceCount) = countTextureAndSurfaceViews(mv)
    Log.i(
      TAG,
      "mapView composition ($EMBEDDED_BUILD): phase=$phase textureViews=$textureCount surfaceViews=$surfaceCount w=${mv.width} h=${mv.height}"
    )
  }

  private fun countTextureAndSurfaceViews(root: View): Pair<Int, Int> {
    var texture = 0
    var surface = 0
    fun walk(node: View) {
      when (node) {
        is TextureView -> texture += 1
        is SurfaceView -> surface += 1
      }
      if (node is ViewGroup) {
        for (i in 0 until node.childCount) {
          walk(node.getChildAt(i))
        }
      }
    }
    walk(root)
    return Pair(texture, surface)
  }
}

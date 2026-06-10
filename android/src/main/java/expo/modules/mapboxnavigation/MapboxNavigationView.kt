@file:OptIn(com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI::class)

package expo.modules.mapboxnavigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.geojson.utils.PolylineUtils
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.dropin.NavigationView
import com.mapbox.navigation.dropin.RouteOptionsInterceptor
import com.mapbox.navigation.dropin.map.MapViewObserver
import com.mapbox.navigation.dropin.map.MapViewBinder
import com.mapbox.navigation.dropin.navigationview.NavigationViewListener
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.compass.CompassPlugin
import com.mapbox.maps.plugin.scalebar.ScaleBarPlugin
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.util.UUID

/**
 * Android embedded navigation view using Mapbox's official Drop-In UI.
 *
 * This view exists specifically to match the look/feel of Mapbox's SDK UI (like iOS),
 * including the route preview info panel and the Start Navigation button.
 */
class MapboxNavigationView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private data class NavigationMarkerPayload(
    val id: String,
    val point: Point,
    val label: String?,
    val glyph: String,
    val badge: String?,
    val variant: String,
    // Customization: custom colors/opacity override variant-based defaults
    val customColor: Int?,
    val customBadgeColor: Int?,
    val customOpacity: Float?,
    val size: String,
    val markerStyle: String,  // "pin" | "dot"
    val showTail: Boolean,
    val selected: Boolean,
    val allowOverlap: Boolean,
    val anchorOffsetY: Int?,  // custom dp offset, overrides size-preset
  )

  private data class NavigationMarkerMetrics(
    val bubbleSizeDp: Int,
    val badgeSizeDp: Int,
    val strokeWidthDp: Int,
    val tailSizeDp: Int,
    val glyphTextSp: Float,
    val badgeTextSp: Float,
    val tailOverlapDp: Int,
    val badgeInsetDp: Int,
    val elevationDp: Int,
    val offsetYDp: Int,
  )

  companion object {
    private const val TAG = "MapboxNavigationView"
    private const val EMBEDDED_BUILD = "2.0.0-embedded-2026-03-02-r1"
    @Volatile private var activeInstance: MapboxNavigationView? = null

    fun requestStopActiveInstance(): Boolean {
      val instance = activeInstance ?: return false
      instance.mainHandler.post {
        instance.enabled = false
        instance.stopEmbedded(emitCancel = true)
      }
      return true
    }
  }

  private val expoAppContext: AppContext = appContext
  private val sessionOwner = "embedded-${UUID.randomUUID()}"
  private val mainHandler = Handler(Looper.getMainLooper())

  /**
   * Internal native layer that stays as the first child.
   * React Native children are added by the framework as siblings above this layer.
   */
  private val nativeLayer: FrameLayout = FrameLayout(context).apply {
    layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    setBackgroundColor(Color.BLACK)
  }

  private var enabled = false
  private var ownsNavigationSession = false

  private var startOrigin: Map<String, Any>? = null
  private var destination: Map<String, Any>? = null
  private var waypoints: List<Map<String, Any>>? = null
  private var navigationMarkers: List<Map<String, Any>>? = null

  private var shouldSimulateRoute = false
  private var mute = false
  private var voiceVolume = 1.0
  private var distanceUnit = "metric"
  private var language = "en"

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
  private var showNativeAudioGuidanceButton = true
  private var showNativeCameraModeButton = true
  private var showNativeRecenterButton = true
  private var showNativeCompassButton = true
  private var cameraMode = "following"

  private var navigationView: NavigationView? = null
  private var placeholderView: TextView? = null
  private var immersiveNavBarsHidden = false
  private var attachedMapView: MapView? = null
  private val navigationMarkerViews = linkedMapOf<String, View>()

  private var mapboxNavigation: MapboxNavigation? = null
  private var hasRequestedRoute = false
  private var hasEmittedArrival = false
  private var isCameraFollowing = true
  private var touchStartX = 0f
  private var touchStartY = 0f
  private val touchSlopPx = 8f * context.resources.displayMetrics.density
  private var lastJourneyLocation: android.location.Location? = null
  private var lastJourneyProgress: RouteProgress? = null
  private var lastJourneyBanner: BannerInstructions? = null

  val onLocationChange by EventDispatcher()
  val onRouteProgressChange by EventDispatcher()
  val onJourneyDataChange by EventDispatcher()
  val onRouteChange by EventDispatcher()
  val onCameraFollowingStateChange by EventDispatcher()
  val onBannerInstruction by EventDispatcher()
  val onArrive by EventDispatcher()
  val onDestinationPreview by EventDispatcher()
  val onDestinationChanged by EventDispatcher()
  val onCancelNavigation by EventDispatcher()
  val onError by EventDispatcher()
  val onBottomSheetActionPress by EventDispatcher()

  init {
    addView(nativeLayer)
  }

  private val mapViewObserver = object : MapViewObserver() {
    override fun onAttached(mapView: MapView) {
      attachedMapView = mapView
      // If Drop-In uses SurfaceView, it can render behind RN in some hierarchies.
      // Make SurfaceView explicitly top/overlay when present.
      val (textureCount, surfaceCount) = countTextureAndSurfaceViews(mapView)
      Log.i(
        TAG,
        "dropin map attached ($EMBEDDED_BUILD): map=${mapView.width}x${mapView.height} textures=$textureCount surfaces=$surfaceCount"
      )
      if (surfaceCount > 0) {
        findFirstSurfaceView(mapView)?.let { surface ->
          Log.w(TAG, "SurfaceView detected inside Drop-In; enabling z-order overlay ($EMBEDDED_BUILD)")
          // Avoid setZOrderOnTop(true): it can cover Drop-In UI overlays (info panel/buttons).
          // Media overlay is the safest best-effort tweak.
          runCatching { surface.setZOrderMediaOverlay(true) }
        }
      }
      primeTextureViewIfPresent(mapView)
      mapView.setOnTouchListener { _, event ->
        when (event.actionMasked) {
          android.view.MotionEvent.ACTION_DOWN -> {
            touchStartX = event.x
            touchStartY = event.y
          }
          android.view.MotionEvent.ACTION_MOVE -> {
            val dx = kotlin.math.abs(event.x - touchStartX)
            val dy = kotlin.math.abs(event.y - touchStartY)
            if (dx + dy > touchSlopPx) {
              setCameraFollowingState(false, "gesture")
            }
          }
        }
        false
      }
      hideMapOrnaments(mapView)
      renderNavigationMarkersIfPossible()
    }

    override fun onDetached(mapView: MapView) {
      if (attachedMapView === mapView) {
        clearNavigationMarkers(mapView)
        attachedMapView = null
      }
    }
  }

  private val navigationViewListener = object : NavigationViewListener() {
    override fun onDestinationChanged(destination: Point?) {
      val point = destination ?: return
      onDestinationChanged(mapOf("latitude" to point.latitude(), "longitude" to point.longitude()))
    }

    override fun onDestinationPreview() {
      onDestinationPreview(mapOf("active" to true))
      hideNativeBottomPanelIfRequested(navigationView)
      scheduleBottomPanelHidePasses()
    }

    override fun onActiveNavigation() {
      // Re-apply options once active guidance starts to ensure maneuver/top UI is visible.
      applyDropInOptions()
      applyCameraMode("active-navigation")
      hideNativeBottomPanelIfRequested(navigationView)
      scheduleBottomPanelHidePasses()
      hidePlaceholder()
    }

    override fun onIdleCameraMode() {
      setCameraFollowingState(false, "idle-camera")
    }

    override fun onOverviewCameraMode() {
      setCameraFollowingState(false, "overview-camera")
    }

    override fun onFollowingCameraMode() {
      setCameraFollowingState(true, "following-camera")
    }

    override fun onFreeDrive() {
      // Hardening: in some RN/gesture scenarios Drop-In may bounce back to free-drive unexpectedly.
      // If navigation is still enabled and we have a destination, automatically rebuild preview.
      if (!enabled) return
      if (destination.toAnyPointOrNull() == null) return
      if (hasEmittedArrival) return
      hasRequestedRoute = false
      mainHandler.postDelayed({ if (enabled) startIfReady() }, 250)
    }

    override fun onRouteFetchFailed(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
      val message = reasons.joinToString(", ") { it.message ?: it.toString() }
      onError(mapOf("code" to "ROUTE_ERROR", "message" to "Route fetch failed: $message"))
      showPlaceholder("Route fetch failed.\n$message")
      hasRequestedRoute = false
    }

    override fun onRouteFetchCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
      hasRequestedRoute = false
      onError(
        mapOf(
          "code" to "ROUTE_FETCH_CANCELED",
          "message" to "Route fetch canceled (origin: $routerOrigin)."
        )
      )
    }

    override fun onRouteFetchSuccessful(routes: List<NavigationRoute>) {
      // If Drop-In decides to fetch routes internally (e.g. user interaction), that's fine.
      if (routes.isEmpty()) return
      emitRouteChange(routes.first())
    }
  }

  private val locationObserver = object : LocationObserver {
    override fun onNewRawLocation(rawLocation: android.location.Location) = Unit

    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      val location = locationMatcherResult.enhancedLocation
      lastJourneyLocation = location
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
      emitJourneySnapshot()
    }
  }

  private val bannerInstructionsObserver = BannerInstructionsObserver { banner ->
    lastJourneyBanner = banner
    emitBannerInstruction(banner)
    emitJourneySnapshot()
  }

  private fun emitArrivalIfNeeded() {
    if (hasEmittedArrival) return
    hasEmittedArrival = true
    val name = (destination?.get("name") as? String)?.trim()?.takeIf { it.isNotEmpty() }
    onArrive(mapOf("name" to (name ?: "Destination")))
  }

  private val routeProgressObserver = RouteProgressObserver { progress: RouteProgress ->
    lastJourneyProgress = progress
    lastJourneyBanner = progress.bannerInstructions ?: lastJourneyBanner
    onRouteProgressChange(
      mapOf(
        "distanceTraveled" to progress.distanceTraveled.toDouble(),
        "distanceRemaining" to progress.distanceRemaining.toDouble(),
        "durationRemaining" to progress.durationRemaining,
        "fractionTraveled" to progress.fractionTraveled.toDouble()
      )
    )
    if (!hasEmittedArrival && progress.distanceRemaining <= 5.0) {
      emitArrivalIfNeeded()
    }
    emitBannerInstruction(progress.bannerInstructions)
    emitJourneySnapshot()
  }

  private val arrivalObserver = object : ArrivalObserver {
    override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
      emitArrivalIfNeeded()
    }

    override fun onNextRouteLegStart(routeLegProgress: com.mapbox.navigation.base.trip.model.RouteLegProgress) = Unit
    override fun onWaypointArrival(routeProgress: RouteProgress) = Unit
  }

  private val routesObserver = RoutesObserver { routeUpdateResult ->
    routeUpdateResult.navigationRoutes.firstOrNull()?.let { route ->
      emitRouteChange(route)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    Log.i(TAG, "Embedded Drop-In attached ($EMBEDDED_BUILD)")
    if (enabled) startIfReady()
  }

  override fun onDetachedFromWindow() {
    stopEmbedded(emitCancel = false)
    super.onDetachedFromWindow()
  }

  fun setNavigationEnabled(next: Boolean) {
    enabled = next
    if (next) startIfReady() else stopEmbedded(emitCancel = false)
  }

  fun setStartOrigin(origin: Map<String, Any>?) {
    startOrigin = origin
    hasRequestedRoute = false
    if (enabled) startIfReady()
  }

  fun setDestination(dest: Map<String, Any>?) {
    destination = dest
    hasEmittedArrival = false
    hasRequestedRoute = false
    if (enabled) {
      onDestinationChanged(dest.toAnyPointOrNull()?.let { mapOf("latitude" to it.latitude(), "longitude" to it.longitude()) } ?: emptyMap())
      startIfReady()
    }
  }

  fun setWaypoints(wps: List<Map<String, Any>>?) {
    waypoints = wps
    hasRequestedRoute = false
    if (enabled) startIfReady()
  }

  fun setNavigationMarkers(markers: List<Map<String, Any>>?) {
    navigationMarkers = markers
    renderNavigationMarkersIfPossible()
  }

  fun setShouldSimulateRoute(simulate: Boolean) {
    shouldSimulateRoute = simulate
    navigationView?.api?.routeReplayEnabled(simulate)
  }

  fun setShowCancelButton(show: Boolean) {
    if (!show) {
      Log.w(TAG, "showCancelButton is not currently supported by the Android embedded Drop-In view and will be ignored.")
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
    val normalized = mode.trim().lowercase()
    cameraMode = if (normalized == "overview") "overview" else "following"
    applyCameraMode("prop")
  }
  fun setCameraPitch(pitch: Double) = Unit
  fun setCameraZoom(zoom: Double) = Unit

  fun setMapStyleUri(styleUri: String) {
    mapStyleUri = styleUri
    applyDropInOptions()
  }

  fun setMapStyleUriDay(styleUri: String) {
    mapStyleUriDay = styleUri
    applyDropInOptions()
  }

  fun setMapStyleUriNight(styleUri: String) {
    mapStyleUriNight = styleUri
    applyDropInOptions()
  }

  fun setUiTheme(theme: String) {
    uiTheme = theme
    applyDropInOptions()
  }

  fun setRouteAlternatives(enabled: Boolean) {
    routeAlternatives = enabled
    hasRequestedRoute = false
    if (this.enabled) startIfReady()
  }

  fun setShowsSpeedLimits(enabled: Boolean) {
    showsSpeedLimits = enabled
    applyDropInOptions()
  }

  fun setShowsWayNameLabel(enabled: Boolean) {
    showsWayNameLabel = enabled
    applyDropInOptions()
  }

  fun setShowsTripProgress(enabled: Boolean) {
    showsTripProgress = enabled
    applyDropInOptions()
  }

  fun setShowsManeuverView(enabled: Boolean) {
    showsManeuverView = enabled
    applyDropInOptions()
  }

  fun setShowsActionButtons(enabled: Boolean) {
    showsActionButtons = enabled
    applyDropInOptions()
  }

  fun setShowsReportFeedback(enabled: Boolean) {
    showsReportFeedback = enabled
    if (!enabled) {
      Log.w(TAG, "showsReportFeedback is not supported by the Android embedded Drop-In view and will be ignored.")
    }
  }

  fun setShowsEndOfRouteFeedback(enabled: Boolean) {
    showsEndOfRouteFeedback = enabled
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
      Log.w(TAG, "androidActionButtons is not supported by the Android embedded Drop-In view and will be ignored.")
    }
  }

  fun setNativeFloatingButtons(options: Map<String, Any>?) {
    showNativeAudioGuidanceButton = options?.get("showAudioGuidanceButton") as? Boolean ?: true
    showNativeCameraModeButton = options?.get("showCameraModeButton") as? Boolean ?: true
    showNativeRecenterButton = options?.get("showRecenterButton") as? Boolean ?: true
    showNativeCompassButton = options?.get("showCompassButton") as? Boolean ?: true
    applyDropInOptions()
  }

  private fun hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  private fun ensureSession(): Boolean {
    if (ownsNavigationSession) return true
    if (!NavigationSessionRegistry.acquire(sessionOwner)) {
      onError(
        mapOf(
          "code" to "NAVIGATION_SESSION_CONFLICT",
          "message" to "Another embedded navigation session is already active. Stop other embedded navigation before mounting this view."
        )
      )
      return false
    }
    ownsNavigationSession = true
    activeInstance = this
    NavigationSessionRegistry.registerStopHandler(sessionOwner) {
      mainHandler.post {
        enabled = false
        stopEmbedded(emitCancel = true)
      }
    }
    NavigationSessionRegistry.registerResumeCameraFollowingHandler(sessionOwner) {
      mainHandler.post { resumeCameraFollowingInternal("module") }
    }
    NavigationSessionRegistry.registerCameraFollowingProvider(sessionOwner) {
      isCameraFollowing
    }
    return true
  }

  private fun releaseSession() {
    if (!ownsNavigationSession) return
    NavigationSessionRegistry.release(sessionOwner)
    ownsNavigationSession = false
    if (activeInstance === this) {
      activeInstance = null
    }
  }

  private fun getMapboxAccessToken(): String {
    val resId = context.resources.getIdentifier("mapbox_access_token", "string", context.packageName)
    if (resId == 0) throw IllegalStateException("Missing string resource: mapbox_access_token")
    val token = context.getString(resId).trim()
    if (token.isEmpty()) throw IllegalStateException("mapbox_access_token is empty")
    return token
  }

  private fun ensureNavigationView() {
    if (navigationView != null) return
    val token = runCatching { getMapboxAccessToken() }.getOrElse { throwable ->
      onError(mapOf("code" to "MISSING_ACCESS_TOKEN", "message" to (throwable.message ?: "Missing mapbox_access_token")))
      showPlaceholder("Missing Mapbox access token.\nCheck EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN + prebuild.")
      return
    }

    val activity = expoAppContext.currentActivity as? AppCompatActivity
    if (activity == null) {
      onError(
        mapOf(
          "code" to "NO_ACTIVITY",
          "message" to "Embedded navigation requires an active AppCompatActivity host."
        )
      )
      showPlaceholder("Waiting for host activity…")
      return
    }

    val vmo = activity as ViewModelStoreOwner

    val view = try {
      NavigationView(activity, null, token, vmo)
    } catch (e: Throwable) {
      onError(mapOf("code" to "NAVIGATION_INIT_FAILED", "message" to (e.message ?: "Failed to create Drop-In NavigationView")))
      showPlaceholder("Failed to create Mapbox NavigationView.\n${e.message ?: ""}".trim())
      return
    }

    // React Native view trees sometimes lack ViewTree owners, which Drop-In relies on
    // (ViewModels, SavedState, collectors). Attach them best-effort via reflection so we
    // don't force additional androidx dependencies onto host apps.
    attachViewTreeOwnersIfPossible(view, activity)
    Log.i(TAG, "Drop-In NavigationView created ($EMBEDDED_BUILD): hasVMO=true")

    // Force TextureView-backed MapView to avoid SurfaceView composition issues in React Native.
    // This keeps Mapbox's official Drop-In UI while making the map render reliably.
    view.customizeViewBinders {
      mapViewBinder = TextureMapViewBinder()
    }

    view.layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    view.addListener(navigationViewListener)
    view.registerMapObserver(mapViewObserver)
    view.api.routeReplayEnabled(shouldSimulateRoute)

    navigationView = view
    nativeLayer.addView(view)
    Log.i(TAG, "Drop-In NavigationView added ($EMBEDDED_BUILD): nativeChildren=${nativeLayer.childCount}")
    scheduleLayoutNudges()
    scheduleBottomPanelHidePasses()
    hidePlaceholder()
    applyDropInOptions()
  }

  private fun scheduleLayoutNudges() {
    // Drop-In may attach the internal MapView before the view is laid out (map=0x0).
    // Nudge a couple of layout passes after mount to ensure sizing settles.
    val root = this
    val nv = navigationView ?: return
    fun nudge(reason: String) {
      val w = root.width
      val h = root.height
      if (w <= 0 || h <= 0) {
        Log.w(TAG, "layout nudge skipped ($EMBEDDED_BUILD): $reason root=${w}x${h}")
        return
      }
      val wSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
      val hSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
      nativeLayer.measure(wSpec, hSpec)
      nativeLayer.layout(0, 0, w, h)
      nv.measure(wSpec, hSpec)
      nv.layout(0, 0, w, h)
      nv.invalidate()
      Log.i(TAG, "layout nudged ($EMBEDDED_BUILD): $reason root=${w}x${h} nv=${nv.width}x${nv.height}")
    }
    mainHandler.post { nudge("post") }
    mainHandler.postDelayed({ nudge("post+200ms") }, 200)
    mainHandler.postDelayed({ nudge("post+800ms") }, 800)
  }

  private class TextureMapViewBinder : MapViewBinder() {
    override fun getMapView(context: Context): com.mapbox.maps.MapView {
      val init = com.mapbox.maps.MapInitOptions(context).apply {
        textureView = true
      }
      return com.mapbox.maps.MapView(context, init)
    }
  }

  private fun ensureNavigation() {
    if (mapboxNavigation != null) return

    val nav = runCatching { MapboxNavigationApp.current() }.getOrElse { throwable ->
      onError(mapOf("code" to "NAVIGATION_INIT_FAILED", "message" to (throwable.message ?: "Failed to init MapboxNavigation")))
      showPlaceholder("Failed to init navigation.\n${throwable.message ?: ""}".trim())
      return
    } ?: run {
      val message = "MapboxNavigationApp is not attached yet."
      onError(mapOf("code" to "NAVIGATION_INIT_FAILED", "message" to message))
      showPlaceholder("Failed to init navigation.\n$message")
      return
    }

    mapboxNavigation = nav
    nav.registerLocationObserver(locationObserver)
    nav.registerRouteProgressObserver(routeProgressObserver)
    nav.registerBannerInstructionsObserver(bannerInstructionsObserver)
    nav.registerArrivalObserver(arrivalObserver)
    nav.registerRoutesObserver(routesObserver)

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

    ensureNavigationView()
    ensureNavigation()
    applyDropInOptions()
    setNavigationBarsHidden(true)
    applyCameraMode("start")

    val nav = mapboxNavigation ?: return
    val view = navigationView ?: return

    val origin = startOrigin.toAnyPointOrNull()
    val dest = destination.toAnyPointOrNull()
    if (dest == null) {
      showPlaceholder("Waiting for destination…")
      Log.w(TAG, "startIfReady ($EMBEDDED_BUILD): missing destination")
      return
    }
    if (hasRequestedRoute) return
    hasRequestedRoute = true
    hasEmittedArrival = false
    hidePlaceholder()

    if (origin != null) {
      val midPoints = parseWaypoints(waypoints)
      val coordinates = buildList {
        add(origin)
        addAll(midPoints)
        add(dest)
      }
      Log.i(
        TAG,
        "startIfReady ($EMBEDDED_BUILD): configuring dropin route interceptor coords=${coordinates.size} alt=$routeAlternatives"
      )
      view.setRouteOptionsInterceptor(
        RouteOptionsInterceptor { builder ->
          builder
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(context)
            .coordinatesList(coordinates)
            .alternatives(routeAlternatives)
            .steps(true)
            .bannerInstructions(true)
            .voiceInstructions(true)
            .layersList(MutableList<Int?>(coordinates.size) { null })
        }
      )
    } else {
      Log.i(TAG, "startIfReady ($EMBEDDED_BUILD): preview with device origin")
    }

    view.api.routeReplayEnabled(shouldSimulateRoute)
    view.api.startDestinationPreview(dest)
    val preview = view.api.startRoutePreview()
    if (preview.isError) {
      val message = preview.error?.message ?: "unknown"
      val shouldFallback = origin != null && message.contains("cannot be empty", ignoreCase = true)
      if (shouldFallback) {
        Log.w(TAG, "Drop-In preview returned empty routes; falling back to explicit route request ($EMBEDDED_BUILD)")
        val midPoints = parseWaypoints(waypoints)
        val coordinates = buildList {
          add(origin)
          addAll(midPoints)
          add(dest)
        }
        requestAndStartPreviewFromCoordinates(view, nav, coordinates)
      } else {
        hasRequestedRoute = false
        onError(
          mapOf(
            "code" to "ROUTE_ERROR",
            "message" to "Failed to start route preview: $message"
          )
        )
        showPlaceholder("Failed to start route preview.")
        return
      }
    } else {
      applyDropInOptions()
      mainHandler.postDelayed({ applyDropInOptions() }, 180)
      tryStartActiveGuidance(view, nav)
    }
  }

  private fun requestAndStartPreviewFromCoordinates(
    view: NavigationView,
    nav: MapboxNavigation,
    coordinates: List<Point>,
  ) {
    if (coordinates.size < 2) {
      hasRequestedRoute = false
      onError(mapOf("code" to "ROUTE_ERROR", "message" to "At least origin and destination are required."))
      showPlaceholder("Missing origin/destination for route preview.")
      return
    }

    val routeOptions = RouteOptions.builder()
      .applyDefaultNavigationOptions()
      .applyLanguageAndVoiceUnitOptions(context)
      .coordinatesList(coordinates)
      .alternatives(routeAlternatives)
      .steps(true)
      .bannerInstructions(true)
      .voiceInstructions(true)
      .layersList(MutableList<Int?>(coordinates.size) { null })
      .build()

    nav.requestRoutes(
      routeOptions,
      object : NavigationRouterCallback {
        override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: RouterOrigin) {
          if (routes.isEmpty()) {
            hasRequestedRoute = false
            onError(mapOf("code" to "NO_ROUTE", "message" to "No route found"))
            showPlaceholder("No route found.")
            return
          }
          emitRouteChange(routes.first())
          val expected = view.api.startRoutePreview(routes)
          if (expected.isError) {
            hasRequestedRoute = false
            onError(
              mapOf(
                "code" to "ROUTE_ERROR",
                "message" to "Failed to start route preview: ${expected.error?.message}"
              )
            )
            showPlaceholder("Failed to start route preview.")
          } else {
            applyDropInOptions()
            mainHandler.postDelayed({ applyDropInOptions() }, 180)
            tryStartActiveGuidance(view, nav)
          }
        }

        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
          hasRequestedRoute = false
          val message = reasons.joinToString(", ") { it.message ?: it.toString() }
          onError(mapOf("code" to "ROUTE_ERROR", "message" to "Route fetch failed: $message"))
          showPlaceholder("Route fetch failed.\n$message")
        }

        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
          hasRequestedRoute = false
          onError(mapOf("code" to "ROUTE_FETCH_CANCELED", "message" to "Route fetch canceled (origin: $routerOrigin)."))
        }
      }
    )
  }

  private fun stopEmbedded(emitCancel: Boolean) {
    mainHandler.removeCallbacksAndMessages(null)

    attachedMapView?.let { mapView ->
      clearNavigationMarkers(mapView)
    }
    attachedMapView = null

    navigationView?.let { nv ->
      runCatching { nv.removeListener(navigationViewListener) }
      runCatching { nv.unregisterMapObserver(mapViewObserver) }
      runCatching { nativeLayer.removeView(nv) }
    }
    navigationView = null
    setNavigationBarsHidden(false)

    mapboxNavigation?.let { nav ->
      runCatching { nav.unregisterLocationObserver(locationObserver) }
      runCatching { nav.unregisterRouteProgressObserver(routeProgressObserver) }
      runCatching { nav.unregisterBannerInstructionsObserver(bannerInstructionsObserver) }
      runCatching { nav.unregisterArrivalObserver(arrivalObserver) }
      runCatching { nav.unregisterRoutesObserver(routesObserver) }
      runCatching { nav.setNavigationRoutes(emptyList()) }
    }
    mapboxNavigation = null

    hidePlaceholder()
    hasRequestedRoute = false
    hasEmittedArrival = false
    lastJourneyLocation = null
    lastJourneyProgress = null
    lastJourneyBanner = null
    setCameraFollowingState(true, "stop")
    if (emitCancel) onCancelNavigation(emptyMap())
    releaseSession()
  }

  private fun applyDropInOptions() {
    val view = navigationView ?: return
    val single = mapStyleUri.trim().takeIf { it.isNotEmpty() }
    val day = mapStyleUriDay.trim().takeIf { it.isNotEmpty() } ?: single
    val night = mapStyleUriNight.trim().takeIf { it.isNotEmpty() } ?: day
    val resolvedDay = day ?: "mapbox://styles/mapbox/navigation-day-v1"
    val resolvedNight = night ?: "mapbox://styles/mapbox/navigation-night-v1"

    view.customizeViewOptions {
      mapStyleUriDay = resolvedDay
      mapStyleUriNight = resolvedNight

      val nativeFloatingButtonsVisible =
        showsActionButtons &&
          (showNativeAudioGuidanceButton ||
            showNativeCameraModeButton ||
            showNativeRecenterButton ||
            showNativeCompassButton)
      val hideNativeBottomPanel = shouldHideNativeBottomPanel()

      // Preserve expected iOS parity by honoring UI props in Android as well.
      showManeuver = showsManeuverView
      showInfoPanelInFreeDrive = false
      isInfoPanelHideable = true
      infoPanelForcedState = if (hideNativeBottomPanel) 5 else 0
      showTripProgress = showsTripProgress
      showActionButtons = nativeFloatingButtonsVisible
      showToggleAudioActionButton = showNativeAudioGuidanceButton
      showCameraModeActionButton = showNativeCameraModeButton
      showRecenterActionButton = showNativeRecenterButton
      showCompassActionButton = showNativeCompassButton
      showRoadName = showsWayNameLabel
      showSpeedLimit = showsSpeedLimits
      showArrivalText = showsTripProgress
      showPoiName = false
      enableMapLongClickIntercept = false

      // Hide all native bottom controls (preview/start/end/action panel).
      showStartNavigationButton = false
      showEndNavigationButton = false
      showRoutePreviewButton = false
      showMapScalebar = false
    }

    hideNativeBottomPanelIfRequested(view)
  }

  private fun scheduleBottomPanelHidePasses() {
    if (!shouldHideNativeBottomPanel()) return
    val view = navigationView ?: return
    fun stylePass(reason: String) {
      hideNativeBottomPanelIfRequested(view)
      Log.d(TAG, "dropin style pass ($EMBEDDED_BUILD): $reason")
    }
    mainHandler.post { stylePass("post") }
    mainHandler.postDelayed({ stylePass("post+180ms") }, 180)
    mainHandler.postDelayed({ stylePass("post+650ms") }, 650)
    mainHandler.postDelayed({ stylePass("post+1400ms") }, 1400)
    mainHandler.postDelayed({ stylePass("post+2400ms") }, 2400)
  }

  private fun tryStartActiveGuidance(view: NavigationView, nav: MapboxNavigation) {
    var didStartGuidance = false

    runCatching {
      val expected = view.api.startActiveGuidance()
      if (!expected.isError) {
        didStartGuidance = true
        return@runCatching
      }

      val fallbackRoutes = nav.getNavigationRoutes()
      if (fallbackRoutes.isEmpty()) {
        Log.w(TAG, "Drop-In startActiveGuidance returned an error and no routes are available ($EMBEDDED_BUILD): ${expected.error}")
        return@runCatching
      }

      val fallback = view.api.startActiveGuidance(fallbackRoutes)
      if (!fallback.isError) {
        didStartGuidance = true
      } else {
        Log.w(TAG, "Drop-In startActiveGuidance fallback failed ($EMBEDDED_BUILD): ${fallback.error}")
      }
    }.onFailure { throwable ->
      Log.w(TAG, "Failed to request Drop-In active guidance ($EMBEDDED_BUILD)", throwable)
    }

    if (!didStartGuidance) return

    runCatching {
      if (shouldSimulateRoute) nav.startReplayTripSession() else nav.startTripSession()
    }.onFailure { throwable ->
      Log.w(TAG, "Failed to start shared Mapbox trip session ($EMBEDDED_BUILD)", throwable)
    }
  }

  private fun hideNativeBottomPanelIfRequested(view: NavigationView?) {
    if (!shouldHideNativeBottomPanel()) return
    val target = view ?: return
    runCatching {
      hideViewsByClassNameHints(
        root = target,
        hints = listOf(
          "infopanel",
          "tripprogress",
          "bottombanner",
          "routepreview",
          "bottomsheet",
          "routeinfo",
          "maneuverfooter"
        )
      )
    }
  }

  // ── Navigation Marker Rendering ──────────────────────────────────────────────

  private fun renderNavigationMarkersIfPossible() {
    val mapView = attachedMapView ?: return
    val markerPayloads = navigationMarkers.orEmpty().mapNotNull(::parseNavigationMarker)
    val nextIds = markerPayloads.mapTo(linkedSetOf()) { it.id }
    val annotationManager = mapView.viewAnnotationManager

    navigationMarkerViews.entries.toList().forEach { (markerId, markerView) ->
      if (!nextIds.contains(markerId)) {
        runCatching { annotationManager.removeViewAnnotation(markerView) }
        navigationMarkerViews.remove(markerId)
      }
    }

    markerPayloads.forEach { marker ->
      val existingView = navigationMarkerViews[marker.id]
      val markerView = existingView ?: createNavigationMarkerView(marker)
      bindNavigationMarkerView(markerView, marker)
      val metrics = resolveNavigationMarkerMetrics(marker.size)
      val viewOptions = viewAnnotationOptions {
        geometry(marker.point)
        allowOverlap(marker.allowOverlap)
        visible(true)
        selected(marker.selected)
        offsetY(dp(marker.anchorOffsetY ?: metrics.offsetYDp))
      }

      if (existingView == null) {
        runCatching {
          annotationManager.addViewAnnotation(markerView, viewOptions)
          navigationMarkerViews[marker.id] = markerView
        }.onFailure { throwable ->
          Log.w(TAG, "Failed to add navigation marker '${marker.id}'", throwable)
        }
      } else {
        runCatching {
          if (!annotationManager.updateViewAnnotation(existingView, viewOptions)) {
            annotationManager.removeViewAnnotation(existingView)
            annotationManager.addViewAnnotation(existingView, viewOptions)
          }
        }.onFailure { throwable ->
          Log.w(TAG, "Failed to update navigation marker '${marker.id}'", throwable)
        }
      }
    }
  }

  private fun clearNavigationMarkers(mapView: MapView) {
    val annotationManager = mapView.viewAnnotationManager
    navigationMarkerViews.values.forEach { markerView ->
      runCatching { annotationManager.removeViewAnnotation(markerView) }
    }
    navigationMarkerViews.clear()
  }

  private fun parseNavigationMarker(value: Map<String, Any>): NavigationMarkerPayload? {
    val id = (value["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val latitude = (value["latitude"] as? Number)?.toDouble() ?: return null
    val longitude = (value["longitude"] as? Number)?.toDouble() ?: return null
    if (!latitude.isFinite() || !longitude.isFinite()) return null

    val label = (value["label"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    val glyph = (value["glyph"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(2) ?: "•"
    val badge = (value["badge"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(3)
    val variant = normalizeMarkerVariant(value["variant"] as? String)
    val customColor = parseHexColor(value["color"] as? String)
    val customBadgeColor = parseHexColor(value["badgeColor"] as? String)
    val customOpacity = (value["opacity"] as? Number)?.toFloat()?.coerceIn(0f, 1f)
    val size = normalizeMarkerSize(value["size"] as? String)
    val markerStyle = normalizeMarkerStyle(value["markerStyle"] as? String)
    val showTail = (value["showTail"] as? Boolean) ?: (markerStyle == "pin")
    val selected = (value["selected"] as? Boolean) ?: (variant == "primary" || variant == "success")
    val allowOverlap = (value["allowOverlap"] as? Boolean) ?: true
    val anchorOffsetY = (value["anchorOffsetY"] as? Number)?.toInt()

    return NavigationMarkerPayload(
      id = id,
      point = Point.fromLngLat(longitude, latitude),
      label = label,
      glyph = glyph,
      badge = badge,
      variant = variant,
      customColor = customColor,
      customBadgeColor = customBadgeColor,
      customOpacity = customOpacity,
      size = size,
      markerStyle = markerStyle,
      showTail = showTail,
      selected = selected,
      allowOverlap = allowOverlap,
      anchorOffsetY = anchorOffsetY,
    )
  }

  private fun createNavigationMarkerView(marker: NavigationMarkerPayload): View {
    val markerRoot = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      clipChildren = false
      clipToPadding = false
      contentDescription = marker.label ?: marker.id
      isClickable = false
      isFocusable = false
    }

    val bubble = FrameLayout(context).apply {
      id = View.generateViewId()
      layoutParams = LinearLayout.LayoutParams(0, 0).apply { gravity = Gravity.CENTER_HORIZONTAL }
      clipChildren = false
      clipToPadding = false
    }

    val glyphView = TextView(context).apply {
      id = View.generateViewId()
      gravity = Gravity.CENTER
      layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
      setTextColor(Color.WHITE)
      setTypeface(typeface, Typeface.BOLD)
    }

    val badgeView = TextView(context).apply {
      id = View.generateViewId()
      gravity = Gravity.CENTER
      layoutParams = FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.END)
      setTextColor(Color.WHITE)
      setTypeface(typeface, Typeface.BOLD)
    }

    val tail = View(context).apply {
      id = View.generateViewId()
      layoutParams = LinearLayout.LayoutParams(0, 0).apply { gravity = Gravity.CENTER_HORIZONTAL }
      rotation = 45f
    }

    bubble.addView(glyphView)
    bubble.addView(badgeView)
    markerRoot.addView(bubble)
    markerRoot.addView(tail)
    return markerRoot
  }

  private fun bindNavigationMarkerView(markerView: View, marker: NavigationMarkerPayload) {
    val root = markerView as? LinearLayout ?: return
    val bubble = root.getChildAt(0) as? FrameLayout ?: return
    val glyphView = bubble.getChildAt(0) as? TextView ?: return
    val badgeView = bubble.getChildAt(1) as? TextView
    val tail = root.getChildAt(1)
    val metrics = resolveNavigationMarkerMetrics(marker.size)
    val fillColor = marker.customColor ?: resolveMarkerFillColor(marker.variant)
    val alpha = marker.customOpacity ?: resolveMarkerAlpha(marker.variant, marker.selected)

    (bubble.layoutParams as? LinearLayout.LayoutParams)?.apply {
      width = dp(metrics.bubbleSizeDp)
      height = dp(metrics.bubbleSizeDp)
      gravity = Gravity.CENTER_HORIZONTAL
      bubble.layoutParams = this
    }
    bubble.elevation = dp(metrics.elevationDp).toFloat()
    bubble.background = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(fillColor)
      setStroke(dp(metrics.strokeWidthDp), Color.WHITE)
    }

    glyphView.text = marker.glyph
    glyphView.setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.glyphTextSp)

    // Tail: shown for "pin" style when showTail is true
    val showTailView = marker.markerStyle == "pin" && marker.showTail
    tail.visibility = if (showTailView) View.VISIBLE else View.GONE
    if (showTailView) {
      tail.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(2).toFloat()
        setColor(fillColor)
      }
      (tail.layoutParams as? LinearLayout.LayoutParams)?.apply {
        width = dp(metrics.tailSizeDp)
        height = dp(metrics.tailSizeDp)
        gravity = Gravity.CENTER_HORIZONTAL
        topMargin = -dp(metrics.tailOverlapDp)
        tail.layoutParams = this
      }
    }

    badgeView?.let {
      if (marker.badge != null) {
        it.visibility = View.VISIBLE
        it.text = marker.badge
        it.setTextSize(TypedValue.COMPLEX_UNIT_SP, metrics.badgeTextSp)
        val badgeColor = marker.customBadgeColor ?: resolveMarkerBadgeColor(marker.variant)
        (it.layoutParams as? FrameLayout.LayoutParams)?.apply {
          width = dp(metrics.badgeSizeDp)
          height = dp(metrics.badgeSizeDp)
          gravity = Gravity.TOP or Gravity.END
          topMargin = -dp(metrics.badgeInsetDp)
          marginEnd = -dp(metrics.badgeInsetDp)
          it.layoutParams = this
        }
        it.background = GradientDrawable().apply {
          shape = GradientDrawable.OVAL
          setColor(badgeColor)
          setStroke(dp(maxOf(metrics.strokeWidthDp - 1, 1)), Color.WHITE)
        }
      } else {
        it.visibility = View.GONE
      }
    }

    root.alpha = alpha
    root.contentDescription = marker.label ?: marker.id
  }

  private fun normalizeMarkerVariant(raw: String?): String = when (raw?.trim()?.lowercase()) {
    "primary" -> "primary"
    "success" -> "success"
    "warning" -> "warning"
    "danger" -> "danger"
    "muted" -> "muted"
    else -> "default"
  }

  private fun normalizeMarkerSize(raw: String?): String = when (raw?.trim()?.lowercase()) {
    "small" -> "small"
    "large" -> "large"
    else -> "medium"
  }

  private fun normalizeMarkerStyle(raw: String?): String = when (raw?.trim()?.lowercase()) {
    "dot" -> "dot"
    else -> "pin"
  }

  private fun parseHexColor(raw: String?): Int? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Color.parseColor(trimmed) }.getOrNull()
  }

  private fun resolveNavigationMarkerMetrics(size: String): NavigationMarkerMetrics = when (size) {
    "small" -> NavigationMarkerMetrics(
      bubbleSizeDp = 32, badgeSizeDp = 18, strokeWidthDp = 2, tailSizeDp = 10,
      glyphTextSp = 14f, badgeTextSp = 9f, tailOverlapDp = 3, badgeInsetDp = 3,
      elevationDp = 4, offsetYDp = 20,
    )
    "large" -> NavigationMarkerMetrics(
      bubbleSizeDp = 48, badgeSizeDp = 22, strokeWidthDp = 3, tailSizeDp = 14,
      glyphTextSp = 18f, badgeTextSp = 10f, tailOverlapDp = 4, badgeInsetDp = 4,
      elevationDp = 6, offsetYDp = 30,
    )
    else -> NavigationMarkerMetrics(
      bubbleSizeDp = 40, badgeSizeDp = 20, strokeWidthDp = 3, tailSizeDp = 12,
      glyphTextSp = 16f, badgeTextSp = 10f, tailOverlapDp = 4, badgeInsetDp = 4,
      elevationDp = 6, offsetYDp = 26,
    )
  }

  private fun resolveMarkerFillColor(variant: String): Int = when (variant) {
    "primary" -> Color.parseColor("#2563EB")
    "success" -> Color.parseColor("#15803D")
    "warning" -> Color.parseColor("#C2410C")
    "danger" -> Color.parseColor("#B91C1C")
    "muted" -> Color.parseColor("#475569")
    else -> Color.parseColor("#1F2937")
  }

  private fun resolveMarkerBadgeColor(variant: String): Int = when (variant) {
    "primary" -> Color.parseColor("#1D4ED8")
    "success" -> Color.parseColor("#166534")
    "warning" -> Color.parseColor("#9A3412")
    "danger" -> Color.parseColor("#991B1B")
    "muted" -> Color.parseColor("#334155")
    else -> Color.parseColor("#111827")
  }

  private fun resolveMarkerAlpha(variant: String, selected: Boolean): Float = when {
    variant == "muted" -> 0.72f
    !selected && variant == "default" -> 0.92f
    !selected -> 0.96f
    else -> 1f
  }

  private fun dp(value: Int): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    value.toFloat(),
    context.resources.displayMetrics
  ).toInt()

  // ── End Marker Rendering ──────────────────────────────────────────────────────

  private fun hideMapOrnaments(mapView: com.mapbox.maps.MapView) {
    runCatching {
      mapView.getPlugin<CompassPlugin>(Plugin.MAPBOX_COMPASS_PLUGIN_ID)
    }.getOrNull()?.apply {
      enabled = false
      visibility = false
      clickable = false
    }

    runCatching {
      mapView.getPlugin<ScaleBarPlugin>(Plugin.MAPBOX_SCALEBAR_PLUGIN_ID)
    }.getOrNull()?.apply {
      enabled = false
    }
  }

  private fun hideViewsByClassNameHints(root: View, hints: List<String>): Int {
    var hidden = 0
    val name = root.javaClass.name.lowercase()
    val idName = runCatching {
      if (root.id != View.NO_ID) root.resources.getResourceEntryName(root.id).lowercase() else ""
    }.getOrDefault("")
    if (hints.any { hint -> name.contains(hint) || idName.contains(hint) }) {
      root.visibility = View.GONE
      root.alpha = 0f
      root.isClickable = false
      root.isEnabled = false
      hidden += 1
    }
    if (root is ViewGroup) {
      for (i in 0 until root.childCount) {
        hidden += hideViewsByClassNameHints(root.getChildAt(i), hints)
      }
    }
    return hidden
  }

  private fun setNavigationBarsHidden(hidden: Boolean) {
    val activity = expoAppContext.currentActivity ?: return
    activity.runOnUiThread {
      val window = activity.window ?: return@runOnUiThread
      if (hidden) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          }
        } else {
          @Suppress("DEPRECATION")
          window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        immersiveNavBarsHidden = true
      } else if (immersiveNavBarsHidden) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          window.insetsController?.show(WindowInsets.Type.navigationBars())
        } else {
          @Suppress("DEPRECATION")
          run {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          }
        }
        immersiveNavBarsHidden = false
      }
    }
  }

  private fun setCameraFollowingState(next: Boolean, reason: String) {
    if (isCameraFollowing == next) return
    isCameraFollowing = next
    onCameraFollowingStateChange(
      mapOf(
        "isCameraFollowing" to next,
        "isCameraNotFollowing" to !next,
        "reason" to reason
      )
    )
  }

  private fun applyCameraMode(reason: String) {
    if (cameraMode.trim().lowercase() == "overview") {
      moveCameraToOverviewInternal(reason)
    } else {
      resumeCameraFollowingInternal(reason)
    }
  }

  private fun moveCameraToOverviewInternal(reason: String) {
    val view = navigationView
    if (view == null) {
      setCameraFollowingState(false, reason)
      return
    }

    var didRequestOverview = requestDropInCameraMode(view, "Overview")

    if (!didRequestOverview) {
      runCatching {
        val api = view.api
        val methods = api.javaClass.methods
        val candidateNames = listOf(
          "moveCameraToOverview",
          "requestNavigationCameraToOverview",
          "requestNavigationCameraOverview",
          "showRouteOverview",
          "showOverview",
          "overview"
        )
        for (name in candidateNames) {
          val method = methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
          if (method != null) {
            method.invoke(api)
            didRequestOverview = true
            break
          }
        }
      }.onFailure { throwable ->
        Log.w(TAG, "Failed to request overview camera ($EMBEDDED_BUILD)", throwable)
      }
    }

    if (didRequestOverview) setCameraFollowingState(false, reason)
  }

  private fun resumeCameraFollowingInternal(reason: String) {
    val view = navigationView ?: return
    var didResume = false

    runCatching {
      view.api.recenterCamera()
      didResume = true
    }.onFailure { throwable ->
      Log.w(TAG, "Failed to recenter Drop-In camera ($EMBEDDED_BUILD)", throwable)
    }

    if (!didResume) {
      didResume = requestDropInCameraMode(view, "Following")
    }

    if (didResume) setCameraFollowingState(true, reason)
  }

  private fun requestDropInCameraMode(view: NavigationView, targetModeName: String): Boolean {
    return runCatching {
      val navigationContext = view.javaClass
        .getMethod("getNavigationContext\$libnavui_dropin_release")
        .invoke(view)
      val store = navigationContext.javaClass.getMethod("getStore").invoke(navigationContext)
      val actionClass = Class.forName("com.mapbox.navigation.ui.app.internal.camera.CameraAction\$SetCameraMode")
      val targetModeClass = Class.forName("com.mapbox.navigation.ui.app.internal.camera.TargetCameraMode")
      val targetModeInstance = Class
        .forName("com.mapbox.navigation.ui.app.internal.camera.TargetCameraMode\$$targetModeName")
        .getField("INSTANCE")
        .get(null)
      val action = actionClass.getConstructor(targetModeClass).newInstance(targetModeInstance)
      val dispatch = store.javaClass.getMethod(
        "dispatch",
        Class.forName("com.mapbox.navigation.ui.app.internal.Action")
      )
      dispatch.invoke(store, action)
      true
    }.getOrElse { throwable ->
      Log.w(TAG, "Failed to dispatch Drop-In camera mode '$targetModeName' ($EMBEDDED_BUILD)", throwable)
      false
    }
  }

  private fun attachViewTreeOwnersIfPossible(view: View, activity: android.app.Activity?) {
    if (activity == null) return

    val lifecycleOwner = activity as? LifecycleOwner
    val viewModelStoreOwner = activity as? ViewModelStoreOwner
    val savedStateRegistryOwner = activity as? SavedStateRegistryOwner

    runCatching {
      val cls = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
      val set = cls.getMethod("set", View::class.java, LifecycleOwner::class.java)
      if (lifecycleOwner != null) set.invoke(null, view, lifecycleOwner)
      Log.i(TAG, "Attached ViewTreeLifecycleOwner ($EMBEDDED_BUILD)")
    }

    runCatching {
      val cls = Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner")
      val set = cls.getMethod("set", View::class.java, ViewModelStoreOwner::class.java)
      if (viewModelStoreOwner != null) set.invoke(null, view, viewModelStoreOwner)
      Log.i(TAG, "Attached ViewTreeViewModelStoreOwner ($EMBEDDED_BUILD)")
    }

    runCatching {
      val cls = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
      val set = cls.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
      if (savedStateRegistryOwner != null) set.invoke(null, view, savedStateRegistryOwner)
      Log.i(TAG, "Attached ViewTreeSavedStateRegistryOwner ($EMBEDDED_BUILD)")
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

  private fun shouldHideNativeBottomPanel(): Boolean {
    return !showsTripProgress
  }

  private fun emitJourneySnapshot() {
    val location = lastJourneyLocation
    emitJourneyData(
      banner = lastJourneyBanner,
      progress = lastJourneyProgress,
      latitude = location?.latitude,
      longitude = location?.longitude,
      bearing = location?.bearing?.toDouble(),
      speed = location?.speed?.toDouble(),
      altitude = location?.altitude,
      accuracy = location?.accuracy?.toDouble()
    )
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

  private fun emitRouteChange(route: NavigationRoute) {
    val geometry = route.directionsRoute.geometry() ?: return
    val points = runCatching { PolylineUtils.decode(geometry, 6) }.getOrElse { return }
    if (points.isEmpty()) return
    val coords = points.map { p ->
      mapOf(
        "latitude" to p.latitude(),
        "longitude" to p.longitude()
      )
    }
    onRouteChange(mapOf("coordinates" to coords))
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

  private fun primeTextureViewIfPresent(root: View) {
    val texture = findFirstTextureView(root) ?: return
    runCatching { texture.alpha = 1f }
    runCatching { texture.isOpaque = true }
    runCatching { texture.setBackgroundColor(Color.BLACK) }
    runCatching { texture.setLayerType(View.LAYER_TYPE_HARDWARE, null) }
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

  private fun findFirstSurfaceView(root: View): SurfaceView? {
    if (root is SurfaceView) return root
    if (root is ViewGroup) {
      for (i in 0 until root.childCount) {
        val found = findFirstSurfaceView(root.getChildAt(i))
        if (found != null) return found
      }
    }
    return null
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

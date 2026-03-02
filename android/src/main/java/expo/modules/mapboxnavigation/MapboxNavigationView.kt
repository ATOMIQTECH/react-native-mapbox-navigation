@file:OptIn(com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI::class)

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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
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
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.dropin.NavigationView
import com.mapbox.navigation.dropin.RouteOptionsInterceptor
import com.mapbox.navigation.dropin.map.MapViewObserver
import com.mapbox.navigation.dropin.map.MapViewBinder
import com.mapbox.navigation.dropin.navigationview.NavigationViewListener
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
  companion object {
    private const val TAG = "MapboxNavigationView"
    private const val EMBEDDED_BUILD = "2.0.0-embedded-2026-03-02-r1"
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

  private var navigationView: NavigationView? = null
  private var placeholderView: TextView? = null

  private var mapboxNavigation: MapboxNavigation? = null
  private var hasRequestedRoute = false
  private var hasEmittedArrival = false

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
    addView(nativeLayer)
  }

  private val mapViewObserver = object : MapViewObserver() {
    override fun onAttached(mapView: com.mapbox.maps.MapView) {
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
    }

    override fun onDetached(mapView: com.mapbox.maps.MapView) = Unit
  }

  private val navigationViewListener = object : NavigationViewListener() {
    override fun onDestinationChanged(destination: Point?) {
      val point = destination ?: return
      onDestinationChanged(mapOf("latitude" to point.latitude(), "longitude" to point.longitude()))
    }

    override fun onDestinationPreview() {
      onDestinationPreview(mapOf("active" to true))
    }

    override fun onActiveNavigation() {
      // Re-apply options once active guidance starts to ensure maneuver/top UI is visible.
      applyDropInOptions()
      hidePlaceholder()
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

    override fun onRouteFetchSuccessful(routes: List<NavigationRoute>) {
      // If Drop-In decides to fetch routes internally (e.g. user interaction), that's fine.
      if (routes.isEmpty()) return
    }
  }

  private val locationObserver = object : LocationObserver {
    override fun onNewRawLocation(rawLocation: android.location.Location) = Unit

    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      val location = locationMatcherResult.enhancedLocation
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
  }

  private val routeProgressObserver = RouteProgressObserver { progress: RouteProgress ->
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

  fun setCameraMode(mode: String) = Unit
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
    if (!enabled) {
      Log.w(TAG, "showsEndOfRouteFeedback is not supported by the Android embedded Drop-In view and will be ignored.")
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
      Log.w(TAG, "androidActionButtons is not supported by the Android embedded Drop-In view and will be ignored.")
    }
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
    return true
  }

  private fun releaseSession() {
    if (!ownsNavigationSession) return
    NavigationSessionRegistry.release(sessionOwner)
    ownsNavigationSession = false
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

    val token = runCatching { getMapboxAccessToken() }.getOrElse { return }

    if (!MapboxNavigationProvider.isCreated()) {
      runCatching {
        val navOptionsClass = Class.forName("com.mapbox.navigation.base.options.NavigationOptions")
        val builderClass = Class.forName("com.mapbox.navigation.base.options.NavigationOptions\$Builder")
        val hostContext = expoAppContext.currentActivity ?: context
        val builder = builderClass.getConstructor(Context::class.java).newInstance(hostContext)
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

    ensureNavigationView()
    ensureNavigation()
    applyDropInOptions()

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
            .layersList(MutableList(coordinates.size) { 0 })
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
      tryStartActiveGuidance(view)
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
            tryStartActiveGuidance(view)
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

    navigationView?.let { nv ->
      runCatching { nv.removeListener(navigationViewListener) }
      runCatching { nv.unregisterMapObserver(mapViewObserver) }
      runCatching { nativeLayer.removeView(nv) }
    }
    navigationView = null

    mapboxNavigation?.let { nav ->
      runCatching { nav.unregisterLocationObserver(locationObserver) }
      runCatching { nav.unregisterRouteProgressObserver(routeProgressObserver) }
      runCatching { nav.unregisterBannerInstructionsObserver(bannerInstructionsObserver) }
      runCatching { nav.unregisterArrivalObserver(arrivalObserver) }
      runCatching { nav.setNavigationRoutes(emptyList()) }
    }
    mapboxNavigation = null

    hidePlaceholder()
    hasRequestedRoute = false
    hasEmittedArrival = false
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

      showManeuver = showsManeuverView
      showTripProgress = showsTripProgress
      showActionButtons = showsActionButtons
      showRoadName = showsWayNameLabel
      showSpeedLimit = showsSpeedLimits
      // Force top instruction banner visible in active guidance.
      showManeuver = true
      showArrivalText = true
      enableMapLongClickIntercept = false

      // Ensure Start button is visible in embedded route preview.
      // We auto-start guidance in embedded mode, so hide native start/preview controls.
      showStartNavigationButton = false
      showEndNavigationButton = true
      showRoutePreviewButton = false
      showMapScalebar = true
    }
  }

  private fun tryStartActiveGuidance(view: NavigationView) {
    runCatching {
      val api = view.api
      val methods = api.javaClass.methods
      val direct = methods.firstOrNull { it.name == "startActiveGuidance" && it.parameterTypes.isEmpty() }
      if (direct != null) {
        direct.invoke(api)
        return
      }
      val fallback = methods.firstOrNull { it.name == "startNavigation" && it.parameterTypes.isEmpty() }
      fallback?.invoke(api)
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

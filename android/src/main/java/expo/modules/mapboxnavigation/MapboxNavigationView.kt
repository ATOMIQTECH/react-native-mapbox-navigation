@file:OptIn(com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI::class)

package expo.modules.mapboxnavigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
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
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Point
import com.mapbox.geojson.utils.PolylineUtils
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.LegIndexUpdatedCallback
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.OffRouteObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
// v3 has no Drop-In UI. The navigation UI is assembled from `ui-components`
// widgets and wired to the navigator through the `ui-base` installer, so the
// former `com.mapbox.navigation.dropin.*` imports have no replacement — the
// types below are their functional stand-ins.
import com.mapbox.maps.MapInitOptions
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.Exclude
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.ui.base.installer.Installation
import com.mapbox.navigation.ui.base.installer.installComponents
import com.mapbox.navigation.ui.components.maneuver.maneuver
import com.mapbox.navigation.ui.components.maneuver.model.ManeuverViewOptions
import com.mapbox.navigation.ui.components.maneuver.view.MapboxLaneGuidance
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.maneuver.view.MapboxPrimaryManeuver
import com.mapbox.navigation.ui.components.maneuver.view.MapboxSecondaryManeuver
import com.mapbox.navigation.ui.components.maneuver.view.MapboxStepDistance
import com.mapbox.navigation.ui.components.maneuver.view.MapboxSubManeuver
import com.mapbox.navigation.ui.components.maneuver.view.MapboxTurnIconManeuver
import com.mapbox.navigation.ui.components.voice.audioGuidanceButton
import com.mapbox.navigation.ui.components.voice.view.MapboxAudioGuidanceButton
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.locationPuck
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewDynamicOptionsBuilderBlock
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import kotlinx.coroutines.flow.MutableStateFlow
import com.mapbox.navigation.ui.maps.navigationCamera
import com.mapbox.navigation.ui.maps.routeArrow
import com.mapbox.navigation.ui.maps.routeLine
import com.mapbox.navigation.ui.maps.puck.LocationPuckOptions
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.Plugin
import com.mapbox.maps.plugin.compass.CompassPlugin
import com.mapbox.maps.plugin.scalebar.ScaleBarPlugin
import com.mapbox.maps.AnnotatedFeature
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.viewannotation.annotationAnchor
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
    private const val EMBEDDED_BUILD = "3.0.0-embedded-v3-r1"
    /// `colors` keys that can only be applied by reinstalling the route-arrow
    /// component; see `setColors`.
    private val ARROW_COLOR_KEYS = listOf("maneuverArrow", "maneuverArrowStroke")
    /// v2 inherited these from Drop-In, which defaulted to Mapbox's navigation
    /// styles. The first cut of this migration fell back to `Style.MAPBOX_STREETS`
    /// instead, which silently changed the default look of every Android
    /// consumer. These restore it. Standard/3D styles are opt-in via
    /// `mapStyleUri` — both platforms place the route layers in the `middle`
    /// slot, so they compose correctly with Standard's 3D content.
    private val BOOLEAN_STYLE_CONFIG_KEYS = listOf(
      "show3dObjects",
      "showRoadLabels",
      "showPlaceLabels",
      "showPointOfInterestLabels",
      "showTransitLabels",
    )
    private const val NAVIGATION_DAY_STYLE = "mapbox://styles/mapbox/navigation-day-v1"
    private const val NAVIGATION_NIGHT_STYLE = "mapbox://styles/mapbox/navigation-night-v1"
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
  private var routeProfile = "driving-traffic"
  private var routeExclusions: Map<String, Any>? = null
  private var vehicleConstraints: Map<String, Any>? = null
  private var mapStyleConfig: Map<String, Any>? = null
  private var warnedMapStyleConfigUnsupported = false
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
  private var cameraPitch: Double? = null
  private var cameraZoom: Double? = null
  private var colorOverrides: Map<String, Any> = emptyMap()
  /**
   * Runtime colour updates for the route line.
   *
   * The route line component takes its options once at install time, so without
   * this a `colors` change after mount could not reach the live line. v3
   * provides `RouteLineConfig.viewOptionsUpdates` precisely for this, which
   * lets Android match iOS instead of requiring a remount.
   */
  private val routeLineOptionsUpdates =
    MutableStateFlow<MapboxRouteLineViewDynamicOptionsBuilderBlock>({})
  /**
   * The route-arrow installation, kept apart from the rest.
   *
   * `RouteArrowConfig` has no equivalent of the route line's
   * `viewOptionsUpdates` — its options are read once, when the component is
   * installed — so the only way to recolour the on-map turn arrow after mount is
   * to install that one component again. Holding its handle separately keeps
   * that from disturbing the others.
   */
  private var routeArrowInstallation: Installation? = null
  private var locationPuckStates: Map<String, Map<String, Any>> = emptyMap()
  private var appliedLocationPuckStates: Map<String, Map<String, Any>>? = null

  private var placeholderView: TextView? = null
  private var immersiveNavBarsHidden = false
  /// The MapView we now own outright. Under Drop-In this was whatever MapView
  /// the SDK happened to attach, surfaced through a `MapViewObserver`.
  private var attachedMapView: MapView? = null
  /// The style currently loaded, so a re-apply with no change is a no-op.
  private var appliedStyleUri: String? = null
  /// Keeps the style-loaded subscription alive; cancelled at teardown.
  private var styleLoadedCancelable: com.mapbox.common.Cancelable? = null
  /// The unit the process-wide NavigationOptions were built with.
  private var appliedDistanceUnit: String? = null

  // The navigation UI, which v3 requires us to build and own.
  private var maneuverView: MapboxManeuverView? = null
  private var soundButton: MapboxAudioGuidanceButton? = null
  /// Handles returned by the individual component installers.
  ///
  /// `installComponents` itself returns Unit and ties teardown to the lifecycle
  /// owner's ON_DESTROY — but this view can be unmounted while its host activity
  /// lives on, so the components would outlive it. Keeping each `Installation`
  /// lets teardown detach them at unmount instead of leaking observers across
  /// remounts.
  private val componentInstallations = mutableListOf<Installation>()

  /// We construct these rather than letting the installer make its own, because
  /// the camera props (`cameraMode`, `cameraPitch`, `cameraZoom`) and
  /// `resumeCameraFollowing()` need a handle to drive them. Drop-In hid both
  /// behind its own camera API.
  private var viewportDataSource: MapboxNavigationViewportDataSource? = null
  private var navigationCamera: NavigationCamera? = null
  private var warnedUnsupportedChrome = false
  private val navigationMarkerViews = linkedMapOf<String, View>()

  private var mapboxNavigation: MapboxNavigation? = null
  private var hasRequestedRoute = false
  /// Set while free drive runs purely to obtain a first location fix, because
  /// `startOrigin` was omitted. Drop-In used to resolve the device location
  /// itself; v3 leaves that to the host.
  private var awaitingOriginFix = false
  private var hasEmittedArrival = false
  private var isCameraFollowing = true
  private var touchStartX = 0f
  private var touchStartY = 0f
  private val touchSlopPx = 8f * context.resources.displayMetrics.density
  /**
   * Minimum gap between the continuous, high-frequency events
   * (`onLocationChange`, `onRouteProgressChange`, `onJourneyDataChange`).
   *
   * Mapbox emits these on every location fix, and each one crosses the bridge
   * with a payload. On a long drive that is a meaningful amount of avoidable
   * CPU, garbage and battery. Discrete events (arrival, banner, errors) are
   * never throttled.
   */
  private var eventThrottleMs = 0L
  private var lastLocationEmitAtMs = 0L
  private var lastProgressEmitAtMs = 0L
  private var lastJourneyEmitAtMs = 0L
  /// v3 reports `com.mapbox.common.location.Location`, not
  /// `android.location.Location`. Latitude/longitude are non-null; everything
  /// else is a nullable `Double`, and `accuracy` is now `horizontalAccuracy`.
  private var lastJourneyLocation: com.mapbox.common.location.Location? = null
  private var lastJourneyProgress: RouteProgress? = null
  private var lastJourneyBanner: BannerInstructions? = null

  val onLocationChange by EventDispatcher()
  val onRouteProgressChange by EventDispatcher()
  val onJourneyDataChange by EventDispatcher()
  val onRouteChange by EventDispatcher()
  val onCameraFollowingStateChange by EventDispatcher()
  val onBannerInstruction by EventDispatcher()
  val onArrive by EventDispatcher()
  val onWaypointArrive by EventDispatcher()
  val onOffRoute by EventDispatcher()
  val onDestinationPreview by EventDispatcher()
  val onDestinationChanged by EventDispatcher()
  val onCancelNavigation by EventDispatcher()
  val onError by EventDispatcher()
  val onBottomSheetActionPress by EventDispatcher()

  init {
    addView(nativeLayer)
  }



  private val locationObserver = object : LocationObserver {
    override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) = Unit

    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      val location = locationMatcherResult.enhancedLocation
      // Always keep the latest fix for the journey snapshot, even when the
      // outgoing event itself is throttled.
      lastJourneyLocation = location
      if (awaitingOriginFix) {
        // We were only in free drive to learn where we are; now we can route.
        awaitingOriginFix = false
        mainHandler.post { startIfReady() }
      }
      if (!shouldEmitThrottled(lastLocationEmitAtMs)) return
      lastLocationEmitAtMs = nowMs()
      dispatchLocationChange(
        mapOf(
          "latitude" to location.latitude,
          "longitude" to location.longitude,
          // These are nullable in v3; the JS payload keys and their numeric
          // shape are part of the public contract, so absent values become 0.0
          // rather than null.
          "bearing" to (location.bearing ?: 0.0),
          "speed" to (location.speed ?: 0.0),
          "altitude" to (location.altitude ?: 0.0),
          "accuracy" to (location.horizontalAccuracy ?: 0.0)
        )
      )
      emitJourneySnapshot()
    }
  }

  private val bannerInstructionsObserver = BannerInstructionsObserver { banner ->
    lastJourneyBanner = banner
    emitBannerInstructionFrom(banner)
    emitJourneySnapshot()
  }

  private fun emitArrivalIfNeeded(routeProgress: RouteProgress? = null) {
    if (hasEmittedArrival) return
    hasEmittedArrival = true
    val name = (destination?.get("name") as? String)?.trim()?.takeIf { it.isNotEmpty() }
    dispatchArrive(
      mapOf(
        "index" to routeProgress?.currentLegProgress?.legIndex,
        "name" to (name ?: "Destination"),
        "isFinalDestination" to true,
        "remainingWaypoints" to (routeProgress?.remainingWaypoints ?: 0)
      )
    )
  }

  private val routeProgressObserver = RouteProgressObserver { progress: RouteProgress ->
    lastJourneyProgress = progress
    lastJourneyBanner = progress.bannerInstructions ?: lastJourneyBanner

    if (shouldEmitThrottled(lastProgressEmitAtMs)) {
      lastProgressEmitAtMs = nowMs()
      dispatchRouteProgressChange(
        mapOf(
          "distanceTraveled" to progress.distanceTraveled.toDouble(),
          "distanceRemaining" to progress.distanceRemaining.toDouble(),
          "durationRemaining" to progress.durationRemaining,
          "fractionTraveled" to progress.fractionTraveled.toDouble(),
          "legIndex" to (progress.currentLegProgress?.legIndex ?: 0)
        )
      )
    }

    // Arrival and banner changes are discrete and must never be throttled.
    if (!hasEmittedArrival && progress.distanceRemaining <= 5.0) {
      emitArrivalIfNeeded(progress)
    }
    emitBannerInstructionFrom(progress.bannerInstructions)
    emitJourneySnapshot()
  }

  /**
   * Emits when the user leaves (or rejoins) the active route.
   *
   * Mapbox detects this and reroutes internally, but the app had no way to
   * observe it — useful for showing a "rerouting" indicator.
   */
  private val offRouteObserver = OffRouteObserver { offRoute ->
    dispatchOffRoute(mapOf("offRoute" to offRoute))
  }

  private val arrivalObserver = object : ArrivalObserver {
    override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
      emitArrivalIfNeeded(routeProgress)
    }

    /**
     * Intermediate stop reached. Previously a no-op, so multi-stop routes had no
     * way to observe waypoint arrivals at all.
     */
    override fun onWaypointArrival(routeProgress: RouteProgress) {
      dispatchWaypointArrive(
        mapOf(
          "index" to routeProgress.currentLegProgress?.legIndex,
          "name" to (currentLegDestinationName(routeProgress) ?: ""),
          "isFinalDestination" to false,
          "remainingWaypoints" to routeProgress.remainingWaypoints
        )
      )
    }

    /**
     * Intentionally not surfaced as an event.
     *
     * This fires right after `onWaypointArrival` (and after a programmatic
     * `advanceToNextWaypoint()`), so emitting here would double-report a single
     * stop. Leg transitions are observable via `RouteProgress.legIndex`.
     */
    override fun onNextRouteLegStart(
      routeLegProgress: com.mapbox.navigation.base.trip.model.RouteLegProgress
    ) = Unit
  }

  /** Best-effort name for the stop that was just reached. */
  private fun currentLegDestinationName(routeProgress: RouteProgress): String? {
    val legIndex = routeProgress.currentLegProgress?.legIndex ?: return null
    val wps = waypoints
    // `waypoints` holds the intermediate stops, so leg N ends at index N.
    return (wps?.getOrNull(legIndex)?.get("name") as? String)?.trim()?.takeIf { it.isNotEmpty() }
  }

  private val routesObserver = RoutesObserver { routeUpdateResult ->
    routeUpdateResult.navigationRoutes.firstOrNull()?.let { route ->
      emitRouteChangeFrom(route)
      // Feed the camera's viewport the new route.
      //
      // The installed navigationCamera component forwards route *progress* to
      // the viewport data source but not route *changes*, so without this the
      // SDK logs "You're calling #onRouteProgressChanged but you didn't call
      // #onRouteChanged" and the viewport has no geometry to frame — overview
      // mode in particular then points somewhere arbitrary rather than at the
      // route. Drop-In owned this wiring in v2.
      viewportDataSource?.let { dataSource ->
        runCatching {
          dataSource.onRouteChanged(route)
          dataSource.evaluate()
        }.onFailure { throwable ->
          Log.w(TAG, "Failed to hand the new route to the camera viewport", throwable)
        }
      }
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    Log.i(TAG, "Embedded navigation view attached ($EMBEDDED_BUILD)")
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
      dispatchDestinationChanged(dest.toAnyPointOrNull()?.let { mapOf("latitude" to it.latitude(), "longitude" to it.longitude()) } ?: emptyMap())
      startIfReady()
    }
  }

  fun setWaypoints(wps: List<Map<String, Any>>?) {
    waypoints = wps
    hasRequestedRoute = false
    if (enabled) startIfReady()
  }

  /**
   * Advance the active route to the next leg on a multi-waypoint route without
   * restarting the session. Driven by a business event (e.g. a passenger is
   * picked up/dropped off) rather than physical arrival. Runs the SDK call on
   * the main thread; returns whether a session was present to accept it.
   *
   * `navigateNextRouteLeg` advances relative to the SDK's own current leg and
   * reports success via the callback (false when already on the final leg), so
   * sequential calls advance correctly and no manual leg-count guard is needed.
   */
  private fun advanceToNextLeg(): Boolean {
    if (mapboxNavigation == null) return false
    mainHandler.post {
      val nav = mapboxNavigation ?: return@post
      runCatching {
        nav.navigateNextRouteLeg(object : LegIndexUpdatedCallback {
          override fun onLegIndexUpdatedCallback(success: Boolean) {
            if (!success) {
              Log.w(TAG, "navigateNextRouteLeg did not advance (already on final leg?)")
            }
          }
        })
      }.onFailure { Log.w(TAG, "navigateNextRouteLeg failed: ${it.message}") }
    }
    return true
  }

  fun setEventThrottleMs(value: Double) {
    eventThrottleMs = if (value.isFinite()) value.coerceIn(0.0, 10_000.0).toLong() else 0L
  }

  fun setLocationPuck(puck: Map<String, Any>?) {
    val nextStates = LocationPuckFactory.parseStates(puck)
    if (nextStates == locationPuckStates) return
    locationPuckStates = nextStates
    applyLocationPuck()
  }

  /**
   * Colour overrides for the route line, the on-map turn arrow and the chrome.
   *
   * Applied live, including after mount. Each group reaches the SDK by a
   * different route, so they are dispatched separately:
   *
   *  - route line: through the `viewOptionsUpdates` flow the component watches;
   *  - turn arrow: by reinstalling that one component, which has no such flow;
   *  - chrome: straight onto the views, which we own.
   *
   * The arrow is the expensive one, so it is only reinstalled when an arrow
   * colour actually changed rather than on every `colors` update.
   */
  fun setColors(colors: Map<String, Any>?) {
    val next = colors ?: emptyMap()
    if (next == colorOverrides) return
    val previous = colorOverrides
    colorOverrides = next

    val colorResources = buildRouteLineColorResources()
    routeLineOptionsUpdates.value = {
      colorResources?.let { routeLineColorResources(it) }
    }

    if (ARROW_COLOR_KEYS.any { previous[it] != next[it] }) {
      reinstallRouteArrow()
    }

    applyChromeColors()
  }

  fun setNavigationMarkers(markers: List<Map<String, Any>>?) {
    navigationMarkers = markers
    renderNavigationMarkersIfPossible()
  }

  fun setShouldSimulateRoute(simulate: Boolean) {
    shouldSimulateRoute = simulate
    // v2 could toggle Drop-In's replay at any time via `api.routeReplayEnabled`.
    // In v3 simulation is chosen when the trip session starts
    // (`startReplayTripSession` vs `startTripSession`), so the flag is just
    // recorded here and read by startSession().
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
    applyDistanceUnit()
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
  fun setCameraPitch(pitch: Double) {
    cameraPitch = pitch.takeIf { it.isFinite() }?.coerceIn(0.0, 85.0)
    // Only nudge the camera itself — going through applyCameraMode() would also
    // recenter, overriding a manual pan the user is in the middle of.
    applyCameraPitchZoom("pitch")
  }

  fun setCameraZoom(zoom: Double) {
    cameraZoom = zoom.takeIf { it.isFinite() }?.coerceIn(1.0, 22.0)
    applyCameraPitchZoom("zoom")
  }

  fun setMapStyleUri(styleUri: String) {
    mapStyleUri = styleUri
    applyMapStyle()
    applyUiVisibility()
  }

  fun setMapStyleUriDay(styleUri: String) {
    mapStyleUriDay = styleUri
    applyMapStyle()
    applyUiVisibility()
  }

  fun setMapStyleUriNight(styleUri: String) {
    mapStyleUriNight = styleUri
    applyMapStyle()
    applyUiVisibility()
  }

  fun setUiTheme(theme: String) {
    uiTheme = theme
    applyMapStyle()
    applyUiVisibility()
  }

  /** Directions profile. Changing it re-requests the route. */
  fun setRouteProfile(profile: String?) {
    val next = profile?.trim()?.ifEmpty { null } ?: "driving-traffic"
    if (next == routeProfile) return
    routeProfile = next
    restartRouteRequest()
  }

  /** Road classes and points to route around. Changing it re-requests. */
  fun setRouteExclusions(exclusions: Map<String, Any>?) {
    if (exclusions == routeExclusions) return
    routeExclusions = exclusions
    restartRouteRequest()
  }

  /** Vehicle dimensions. Changing them re-requests. */
  fun setVehicle(constraints: Map<String, Any>?) {
    if (constraints == vehicleConstraints) return
    vehicleConstraints = constraints
    restartRouteRequest()
  }

  /** Standard-style basemap configuration. Applied live. */
  fun setMapStyleConfig(config: Map<String, Any>?) {
    if (config == mapStyleConfig) return
    mapStyleConfig = config
    applyMapStyleConfig()
  }

  /**
   * Re-request the route after a change to the request parameters.
   *
   * Clearing `hasRequestedRoute` is what the existing `setRouteAlternatives`
   * does; the routing props follow the same path so that changing a profile
   * mid-trip actually produces a new route instead of leaving a stale one.
   */
  private fun restartRouteRequest() {
    hasRequestedRoute = false
    if (enabled) startIfReady()
  }

  fun setRouteAlternatives(enabled: Boolean) {
    routeAlternatives = enabled
    hasRequestedRoute = false
    if (this.enabled) startIfReady()
  }

  fun setShowsSpeedLimits(enabled: Boolean) {
    showsSpeedLimits = enabled
    applyUiVisibility()
  }

  fun setShowsWayNameLabel(enabled: Boolean) {
    showsWayNameLabel = enabled
    applyUiVisibility()
  }

  fun setShowsTripProgress(enabled: Boolean) {
    showsTripProgress = enabled
    applyUiVisibility()
  }

  fun setShowsManeuverView(enabled: Boolean) {
    showsManeuverView = enabled
    applyUiVisibility()
  }

  fun setShowsActionButtons(enabled: Boolean) {
    showsActionButtons = enabled
    applyUiVisibility()
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
    applyUiVisibility()
  }

  // ---------------------------------------------------------------------------
  // Event dispatch
  // ---------------------------------------------------------------------------

  /**
   * Send an event to both the view prop callback and the module-level listeners
   * that back the exported `add*Listener` helpers.
   *
   * The view previously dispatched only view events, so every `add*Listener`
   * helper silently received nothing. The bridge drops the payload when JS has
   * no subscription for that event.
   */

  /**
   * Drop null entries so the payload satisfies `EventDispatcher`, which requires
   * `Map<String, Any>`. JS receives a missing key instead of an explicit null,
   * matching the optional fields in the TypeScript types.
   */
  private fun sanitizeEventPayload(payload: Map<String, Any?>): Map<String, Any> =
    payload.filterValues { it != null }.mapValues { (_, value) -> value as Any }

  private fun dispatchLocationChange(payload: Map<String, Any?> = emptyMap()) {
    onLocationChange(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onLocationChange", payload)
  }

  private fun dispatchRouteProgressChange(payload: Map<String, Any?> = emptyMap()) {
    onRouteProgressChange(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onRouteProgressChange", payload)
  }

  private fun dispatchJourneyDataChange(payload: Map<String, Any?> = emptyMap()) {
    onJourneyDataChange(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onJourneyDataChange", payload)
  }

  private fun dispatchRouteChange(payload: Map<String, Any?> = emptyMap()) {
    onRouteChange(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onRouteChange", payload)
  }

  private fun dispatchCameraFollowingStateChange(payload: Map<String, Any?> = emptyMap()) {
    onCameraFollowingStateChange(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onCameraFollowingStateChange", payload)
  }

  private fun dispatchBannerInstruction(payload: Map<String, Any?> = emptyMap()) {
    onBannerInstruction(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onBannerInstruction", payload)
  }

  private fun dispatchArrive(payload: Map<String, Any?> = emptyMap()) {
    onArrive(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onArrive", payload)
  }

  private fun dispatchWaypointArrive(payload: Map<String, Any?> = emptyMap()) {
    onWaypointArrive(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onWaypointArrive", payload)
  }

  private fun dispatchOffRoute(payload: Map<String, Any?> = emptyMap()) {
    onOffRoute(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onOffRoute", payload)
  }


  private fun dispatchDestinationChanged(payload: Map<String, Any?> = emptyMap()) {
    onDestinationChanged(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onDestinationChanged", payload)
  }

  private fun dispatchCancelNavigation(payload: Map<String, Any?> = emptyMap()) {
    onCancelNavigation(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onCancelNavigation", payload)
  }

  private fun dispatchError(payload: Map<String, Any?> = emptyMap()) {
    onError(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onError", payload)
  }

  private fun dispatchBottomSheetActionPress(payload: Map<String, Any?> = emptyMap()) {
    onBottomSheetActionPress(sanitizeEventPayload(payload))
    MapboxNavigationEventBridge.emit("onBottomSheetActionPress", payload)
  }
  /**
   * Whether enough time has passed to emit a throttled event.
   *
   * `lastAtMs` is read and written by the caller so each stream throttles
   * independently.
   */
  private fun shouldEmitThrottled(lastAtMs: Long): Boolean {
    if (eventThrottleMs <= 0L) return true
    return android.os.SystemClock.elapsedRealtime() - lastAtMs >= eventThrottleMs
  }

  private fun nowMs(): Long = android.os.SystemClock.elapsedRealtime()

  private fun hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  private fun ensureSession(): Boolean {
    if (ownsNavigationSession) return true
    if (!NavigationSessionRegistry.acquire(sessionOwner)) {
      dispatchError(
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
    NavigationSessionRegistry.registerAdvanceLegHandler(sessionOwner) {
      advanceToNextLeg()
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

  /**
   * Force a measure/layout pass over the natively added children.
   *
   * React Native drives layout from JS via Yoga, and `ReactViewGroup` swallows
   * the `requestLayout()` that `addView` would normally trigger. Children added
   * natively — our MapView and the four widgets — therefore stay at 0x0 even
   * though this view and its container are correctly sized. The MapView's
   * TextureView never gets a surface and the map renders black while the GL
   * thread logs "Android surface is not valid" forever.
   *
   * This existed in the v2 code as `scheduleLayoutNudges` and was mistakenly
   * removed during the v3 rewrite as Drop-In cruft. It is not Drop-In specific:
   * it is a React Native layout-timing workaround, and the symptom (map=0x0) is
   * identical either way.
   */
  private fun nudgeChildLayout(reason: String) {
    val w = width
    val h = height
    if (w <= 0 || h <= 0) {
      Log.w(TAG, "layout nudge skipped ($EMBEDDED_BUILD): $reason root=${w}x${h}")
      return
    }
    val wSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
    val hSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
    nativeLayer.measure(wSpec, hSpec)
    nativeLayer.layout(0, 0, w, h)
    attachedMapView?.let { mapView ->
      mapView.measure(wSpec, hSpec)
      mapView.layout(0, 0, w, h)
      mapView.invalidate()
    }
    Log.i(
      TAG,
      "layout nudged ($EMBEDDED_BUILD): $reason root=${w}x${h} map=" +
        "${attachedMapView?.width ?: 0}x${attachedMapView?.height ?: 0}"
    )
  }

  private fun scheduleLayoutNudges() {
    mainHandler.post { nudgeChildLayout("post") }
    mainHandler.postDelayed({ nudgeChildLayout("post+200ms") }, 200)
    mainHandler.postDelayed({ nudgeChildLayout("post+800ms") }, 800)
  }

  /**
   * Re-lay out natively added children whenever React Native resizes us.
   *
   * The timed nudges above cover mount; this covers every later size change
   * (rotation, a parent flex change) without relying on more timers.
   */
  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (attachedMapView != null && w > 0 && h > 0) {
      nudgeChildLayout("onSizeChanged")
    }
  }

  /**
   * Build and attach the navigation UI.
   *
   * v2 handed this entire job to Drop-In: one `NavigationView` supplied the
   * map, maneuver banner, trip progress, speed limit, sound button and info
   * panel. v3 ships no Drop-In, so the UI is assembled here from
   * `ui-components` widgets and bound to the navigator by `installComponents`.
   *
   * Two workarounds from the Drop-In era are deliberately gone:
   *
   *  * `TextureMapViewBinder` — we create the MapView ourselves, so
   *    `MapInitOptions(textureView = true)` asks for a TextureView directly
   *    instead of swapping out an SDK-internal binder.
   *  * the SurfaceView z-order fixes in the old `MapViewObserver` — there is no
   *    longer an SDK-chosen SurfaceView to fight with.
   */
  @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
  private fun ensureNavigationUi() {
    if (attachedMapView != null) return

    val token = runCatching { getMapboxAccessToken() }.getOrElse { throwable ->
      dispatchError(mapOf("code" to "MISSING_ACCESS_TOKEN", "message" to (throwable.message ?: "Missing mapbox_access_token")))
      showPlaceholder("Missing Mapbox access token.\nCheck EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN + prebuild.")
      return
    }
    // The Maps SDK reads the token from resources itself; resolving it here is
    // purely so a misconfigured app gets MISSING_ACCESS_TOKEN instead of an
    // opaque failure deep inside the SDK.
    check(token.isNotEmpty())

    val activity = expoAppContext.currentActivity as? AppCompatActivity
    if (activity == null) {
      dispatchError(
        mapOf(
          "code" to "NO_ACTIVITY",
          "message" to "Embedded navigation requires an active AppCompatActivity host."
        )
      )
      showPlaceholder("Waiting for host activity…")
      return
    }

    val nav = mapboxNavigation
    if (nav == null) {
      // installComponents binds to a live MapboxNavigation, so the navigator has
      // to exist first. startIfReady() calls ensureNavigation() before this.
      dispatchError(
        mapOf("code" to "NAVIGATION_INIT_FAILED", "message" to "Navigator is not ready yet.")
      )
      return
    }

    val mapView = try {
      MapView(
        activity,
        MapInitOptions(
          context = activity,
          // React Native view trees composite poorly with SurfaceView; a
          // TextureView renders reliably inside them.
          textureView = true,
          styleUri = resolveStyleUri()
        )
      )
    } catch (e: Throwable) {
      dispatchError(mapOf("code" to "NAVIGATION_INIT_FAILED", "message" to (e.message ?: "Failed to create MapView")))
      showPlaceholder("Failed to create Mapbox MapView.\n${e.message ?: ""}".trim())
      return
    }

    mapView.layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    nativeLayer.addView(mapView)
    attachedMapView = mapView
    appliedStyleUri = resolveStyleUri()
    // The first style comes from `MapInitOptions`, so there is no `loadStyle`
    // callback to hang the config off. Subscribing covers that load and every
    // later one; writes to a style import are ignored while it is still
    // loading, so a `mapStyleConfig` set at mount needs this to land at all.
    styleLoadedCancelable = runCatching {
      mapView.mapboxMap.subscribeStyleLoaded { applyMapStyleConfig() }
    }.getOrNull()

    // `installComponents` attaches on the lifecycle owner's ON_CREATE and
    // detaches on ON_DESTROY, so the owners still have to be present on the
    // view tree — React Native does not always provide them.
    attachViewTreeOwnersIfPossible(this, activity)

    // Only the maneuver banner and the sound toggle are installed.
    //
    // `MapboxTripProgressView` and `MapboxSpeedInfoView` are deliberately left
    // out. Under Drop-In they sat inside Mapbox's own styled container, which
    // gave them an exact width; standing alone over the map they do not lay out
    // usably — `tripProgressContainer` measures itself at wrap_content, so the
    // bar collapses to a ~125dp strip in the corner with its readouts clipped —
    // and neither exposes enough theming to blend into a host app anyway.
    // Reinstating the trip progress bar was tried and reverted for exactly that
    // reason. Trip progress is already available, and themeable, through this
    // package's own JS overlay (`bottomSheet`), which is what consumers
    // actually ship. See docs/v3-migration.md.
    val maneuver = MapboxManeuverView(activity).also { maneuverView = it }
    val sound = MapboxAudioGuidanceButton(activity).also { soundButton = it }
    addNavigationChrome(maneuver, sound)
    applyChromeColors()

    val dataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)
    val camera = NavigationCamera(mapView.mapboxMap, mapView.camera, dataSource)
    viewportDataSource = dataSource
    navigationCamera = camera

    nav.installComponents(activity) {
      // Map layer: these replace Drop-In's internal map wiring.
      componentInstallations += routeLine(mapView) {
        // The vanishing route line — the traversed part dimming behind the puck
        // — is opt-in on Android, and `routeLineTracksTraversal` was being
        // stored and never read. iOS has had this wired all along.
        apiOptions = MapboxRouteLineApiOptions.Builder()
          .vanishingRouteLineEnabled(routeLineTracksTraversal)
          .build()
        buildRouteLineColorResources()?.let { colorResources ->
          viewOptions = MapboxRouteLineViewOptions.Builder(activity)
            .routeLineColorResources(colorResources)
            .build()
        }
        viewOptionsUpdates = routeLineOptionsUpdates
      }
      routeArrowInstallation = routeArrow(mapView) {
        buildRouteArrowOptions(activity)?.let { options = it }
      }
      componentInstallations += navigationCamera(mapView) {
        // Hand the installer our instances so the props can drive them.
        this.viewportDataSource = dataSource
        this.navigationCamera = camera
      }
      componentInstallations += locationPuck(mapView)
      // Widget layer: these replace Drop-In's chrome.
      componentInstallations += maneuver(maneuver) {
        // `ManeuverConfig` carries its *own* formatter options and builds them
        // from the device locale — it does not read the ones on
        // `NavigationOptions`. Without this the banner showed "50 ft" on a
        // US-locale device while `distanceUnit` said metric and iOS showed
        // metres. Verified on the emulator.
        distanceFormatterOptions = DistanceFormatterOptions.Builder(activity)
          .unitType(resolveUnitType())
          .build()
      }
      componentInstallations += audioGuidanceButton(sound)
    }

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

    Log.i(TAG, "v3 navigation UI attached ($EMBEDDED_BUILD): nativeChildren=${nativeLayer.childCount}")
    // Without this the MapView stays 0x0 under React Native and renders black.
    scheduleLayoutNudges()
    hideMapOrnaments(mapView)
    hidePlaceholder()
    applyUiVisibility()
    applyLocationPuck()
    renderNavigationMarkersIfPossible()
    applyCameraPitchZoom("ui-attached")
  }

  /**
   * Lay the widgets over the map.
   *
   * Built in code rather than XML so the package ships no layout resources for
   * host apps to merge, matching how the rest of this view is constructed.
   *
   * Placement follows Mapbox's own arrangement, and the constraint that matters
   * is that these four widgets must not overlap each other: the first attempt
   * pinned the sound button under a fixed top margin (so it collided with the
   * maneuver banner, whose height is dynamic) and left the speed-limit view
   * sitting on top of the trip-progress bar.
   *
   *   maneuver        top, full width      (height varies with the instruction)
   *   sound button    bottom-right
   *
   * Anchoring the sound button to the *bottom* rather than the top is what keeps
   * it clear of the maneuver banner without having to guess its height.
   */
  private fun addNavigationChrome(
    maneuver: MapboxManeuverView,
    sound: MapboxAudioGuidanceButton
  ) {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    nativeLayer.addView(
      maneuver,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = android.view.Gravity.TOP
        setMargins(dp(8), dp(8), dp(8), 0)
      }
    )

    // The banner inflates its secondary rows — lane guidance, the "then" step,
    // the expandable upcoming-maneuver list — only once an instruction needs
    // them, so a one-shot recolour at mount would miss them. Recolouring on each
    // layout pass catches them as they appear. Text colours and image tints do
    // not themselves trigger layout, so this cannot feed back on itself.
    maneuver.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      tintManeuverDescendants(maneuver)
    }

    // Bottom-right, and anchored to the bottom rather than under a fixed top
    // margin: the maneuver banner's height varies with the instruction, so a
    // top-anchored control collides with it.
    nativeLayer.addView(
      sound,
      FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply {
        gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
        setMargins(0, 0, dp(12), dp(24))
      }
    )
  }

  /**
   * Parse one `colors` key, tolerating garbage.
   *
   * A bad colour string is a consumer typo, not a reason to take down
   * navigation, so it is logged and skipped and the SDK default stands.
   *
   * `Color.parseColor` reads 8-digit hex as `#AARRGGBB` while iOS reads it as
   * `#RRGGBBAA`. The public type documents 3- and 6-digit hex as the portable
   * forms for exactly this reason; both parse identically here.
   */
  private fun color(key: String): Int? =
    (colorOverrides[key] as? String)?.let { raw ->
      runCatching { Color.parseColor(raw.trim()) }.getOrElse {
        Log.w(TAG, "Ignoring unparseable colors.$key value '$raw'")
        null
      }
    }

  /**
   * Translate the arrow entries of `colors` into `RouteArrowOptions`.
   *
   * Returns null when neither is set, so the caller can leave the installer to
   * build its own defaults instead of being handed an all-default object.
   */
  private fun buildRouteArrowOptions(context: Context): RouteArrowOptions? {
    val arrow = color("maneuverArrow")
    val stroke = color("maneuverArrowStroke")
    if (arrow == null && stroke == null) return null

    val builder = RouteArrowOptions.Builder(context)
    arrow?.let { builder.withArrowColor(it) }
    stroke?.let { builder.withArrowCasingColor(it) }
    return builder.build()
  }

  /** Install the route-arrow component, replacing any previous installation. */
  @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
  private fun reinstallRouteArrow() {
    val nav = mapboxNavigation ?: return
    val activity = expoAppContext.currentActivity as? AppCompatActivity ?: return
    val mapView = attachedMapView ?: return

    runCatching {
      routeArrowInstallation?.uninstall()
      routeArrowInstallation = null
      nav.installComponents(activity) {
        routeArrowInstallation = routeArrow(mapView) {
          buildRouteArrowOptions(activity)?.let { options = it }
        }
      }
    }.onFailure { error ->
      Log.w(TAG, "Could not reinstall the route arrow for a colors change", error)
    }
  }

  /**
   * Apply the chrome entries of `colors` to the maneuver banner and the voice
   * button.
   *
   * Two mechanisms, because v3 only gives us the first one for part of it:
   *
   *  - `ManeuverViewOptions` covers the banner backgrounds as plain colour ints,
   *    and `updateManeuverViewOptions` accepts them at any time.
   *  - text and turn-icon colours are only reachable through `@StyleRes` text
   *    appearances and a themed `Context`, which cannot be synthesised from a
   *    runtime colour. So those are set straight onto the views instead — they
   *    are `AppCompatTextView`s and `AppCompatImageView`s, and the SDK's own
   *    render path never rewrites the text colour or the image tint, so the
   *    values hold across instruction changes.
   *
   * Reapplied on every layout pass of the banner (see `addNavigationChrome`),
   * which is what catches views the SDK creates later: the lane-guidance strip,
   * the "then" row, and the rows of the expandable upcoming-maneuver list.
   */
  private fun applyChromeColors() {
    if (colorOverrides.isEmpty()) return

    maneuverView?.let { maneuver ->
      val background = color("maneuverBackground")
      val subBackground = color("maneuverSubBackground") ?: background
      if (background != null || subBackground != null) {
        // `ManeuverViewOptions` is the documented path, so keep it: it is what
        // the sub-layouts the banner inflates later will be built from.
        val options = ManeuverViewOptions.Builder()
        background?.let { options.maneuverBackgroundColor(it) }
        subBackground?.let {
          options.subManeuverBackgroundColor(it)
          options.upcomingManeuverBackgroundColor(it)
        }
        runCatching { maneuver.updateManeuverViewOptions(options.build()) }
          .onFailure { Log.w(TAG, "Could not apply maneuver background colors", it) }

        // …but it does not repaint the layouts that already exist — verified on
        // device: the banner stayed Mapbox navy with no error logged. So paint
        // them directly as well, by the ids the SDK's own layouts declare.
        background?.let { setBackgroundById(maneuver, "mainManeuverLayout", it) }
        subBackground?.let {
          setBackgroundById(maneuver, "subManeuverLayout", it)
          setBackgroundById(maneuver, "laneGuidanceRecycler", it)
          setBackgroundById(maneuver, "upcomingManeuverRecycler", it)
        }
      }
      tintManeuverDescendants(maneuver)
    }

    soundButton?.let { button ->
      color("floatingButtonBackground")?.let { background ->
        // Tinted rather than replaced, so the shape drawable keeps its rounded
        // outline. Applied to both the button and its inner container: the
        // `audioGuidanceButtonBackground` attribute lands on whichever of the
        // two the SDK's layout assigns it to, and a tint on the wrong one
        // leaves a white button — which, with a light `floatingButtonIcon`,
        // renders as an invisible control.
        val tint = ColorStateList.valueOf(background)
        button.backgroundTintList = tint
        button.containerView.backgroundTintList = tint
      }
      color("floatingButtonIcon")?.let { icon ->
        button.iconImage.imageTintList = ColorStateList.valueOf(icon)
        button.textView.setTextColor(icon)
      }
    }
  }

  /**
   * Paint one of the SDK's own layouts, looked up by resource name.
   *
   * Library resources merge into the host app's package, so the ids in
   * `ui-components`' `R.txt` — `mainManeuverLayout`, `subManeuverLayout` and
   * the two recyclers — are resolvable at runtime. Looked up by name rather
   * than referenced directly because they are not part of the SDK's Kotlin
   * API, and missing quietly if the SDK ever renames one: the banner then keeps
   * its default background instead of the view failing to build.
   */
  private fun setBackgroundById(root: View, resourceName: String, color: Int) {
    val id = runCatching {
      resources.getIdentifier(resourceName, "id", context.packageName)
    }.getOrDefault(0)
    if (id == 0) {
      Log.w(TAG, "No id/$resourceName in this Navigation SDK build; leaving its colour alone")
      return
    }
    root.findViewById<View>(id)?.setBackgroundColor(color)
  }

  /**
   * Walk the maneuver banner and recolour the text and icons inside it.
   *
   * The banner is a `ConstraintLayout` the SDK inflates and repopulates, and it
   * exposes no accessors for its children, so matching on the concrete view
   * types is the only way in. They are all public API.
   */
  private fun tintManeuverDescendants(root: View) {
    val primaryText = color("maneuverText")
    val secondaryText = color("maneuverSecondaryText")
    val distanceText = color("maneuverDistanceText")
    val turnIcon = color("maneuverTurnIcon")
    if (primaryText == null && secondaryText == null &&
      distanceText == null && turnIcon == null
    ) {
      return
    }

    when (root) {
      is MapboxPrimaryManeuver -> primaryText?.let { root.setTextColor(it) }
      is MapboxSecondaryManeuver -> secondaryText?.let { root.setTextColor(it) }
      is MapboxSubManeuver -> secondaryText?.let { root.setTextColor(it) }
      is MapboxStepDistance -> distanceText?.let { root.setTextColor(it) }
      // A flat tint, because the two-tone turn icon takes its emphasised and
      // de-emphasised strokes from theme attributes that cannot be set at
      // runtime. Documented on `MapboxNavigationColors.maneuverTurnIcon`.
      is MapboxTurnIconManeuver ->
        turnIcon?.let { root.imageTintList = ColorStateList.valueOf(it) }
      is MapboxLaneGuidance ->
        turnIcon?.let { root.imageTintList = ColorStateList.valueOf(it) }
      is ViewGroup -> for (index in 0 until root.childCount) {
        tintManeuverDescendants(root.getChildAt(index))
      }
    }
  }

  /**
   * Translate the `colors` prop into Mapbox's colour resources.
   *
   * Returns null when nothing was supplied, so the SDK defaults stand rather
   * than being replaced by an all-default object.
   *
   * `routeLine` deliberately also sets the low and unknown congestion colours:
   * congestion shading is painted over the base line, so leaving those at their
   * Mapbox-blue defaults makes a recoloured route still look blue wherever
   * traffic data is absent or free-flowing — which is most of a typical route.
   */
  private fun buildRouteLineColorResources(): RouteLineColorResources? {
    if (colorOverrides.isEmpty()) return null

    val builder = RouteLineColorResources.Builder()

    color("routeLine")?.let { value ->
      builder.routeDefaultColor(value)
      builder.routeLowCongestionColor(value)
      builder.routeUnknownCongestionColor(value)
    }
    color("routeLineCasing")?.let { builder.routeCasingColor(it) }
    color("routeLineTraversed")?.let { builder.routeLineTraveledColor(it) }
    color("routeLineAlternative")?.let { value ->
      builder.alternativeRouteDefaultColor(value)
      builder.alternativeRouteLowCongestionColor(value)
      builder.alternativeRouteUnknownCongestionColor(value)
    }
    color("routeLineAlternativeCasing")?.let { builder.alternativeRouteCasingColor(it) }
    // Explicit congestion overrides come last so they win over `routeLine`.
    color("congestionLow")?.let { builder.routeLowCongestionColor(it) }
    color("congestionModerate")?.let { builder.routeModerateCongestionColor(it) }
    color("congestionHeavy")?.let { builder.routeHeavyCongestionColor(it) }
    color("congestionSevere")?.let { builder.routeSevereCongestionColor(it) }
    color("congestionUnknown")?.let { builder.routeUnknownCongestionColor(it) }
    color("restrictedRoad")?.let { builder.restrictedRoadColor(it) }

    return builder.build()
  }

  /** Resolve the map style from the day/night/base props, preferring the explicit ones. */
  // MARK: routing options

  /** Whether the active profile accepts the driving-only request parameters. */
  private fun isDrivingProfile(): Boolean {
    val profile = resolveDirectionsProfile()
    return profile == DirectionsCriteria.PROFILE_DRIVING ||
      profile == DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
  }

  /** Map the `routeProfile` prop onto a Directions profile constant. */
  private fun resolveDirectionsProfile(): String =
    when (routeProfile.trim().lowercase()) {
      "driving" -> DirectionsCriteria.PROFILE_DRIVING
      "walking" -> DirectionsCriteria.PROFILE_WALKING
      "cycling" -> DirectionsCriteria.PROFILE_CYCLING
      "driving-traffic" -> DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
      else -> {
        dispatchError(
          mapOf(
            "code" to "INVALID_ROUTE_PROFILE",
            "message" to "Unknown routeProfile '$routeProfile'. Falling back to driving-traffic."
          )
        )
        DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
      }
    }

  /**
   * Apply the `routeExclusions` prop.
   *
   * The Directions API shares one `exclude` parameter between road classes and
   * excluded points, which is why this is a single `Exclude` object rather than
   * two independent builder calls — setting `excludeList` as well would
   * overwrite it.
   */
  private fun applyRouteExclusions(builder: RouteOptions.Builder) {
    val exclusions = routeExclusions ?: return
    val driving = isDrivingProfile()
    val dropped = mutableListOf<String>()

    val criteria = (exclusions["roadClasses"] as? List<*>)?.mapNotNull { raw ->
      val name = (raw as? String)?.trim()
      // Walking and cycling accept only `ferry` and `cash_only_tolls`; the
      // Directions API answers 422 for the rest rather than ignoring them,
      // which fails the whole route request. Verified against the API.
      if (!driving && name != "ferry" && name != "cashOnlyTolls") {
        if (name != null) dropped += name
        return@mapNotNull null
      }
      when (name) {
        "toll" -> DirectionsCriteria.EXCLUDE_TOLL
        "motorway" -> DirectionsCriteria.EXCLUDE_MOTORWAY
        "ferry" -> DirectionsCriteria.EXCLUDE_FERRY
        "tunnel" -> DirectionsCriteria.EXCLUDE_TUNNEL
        "restricted" -> DirectionsCriteria.EXCLUDE_RESTRICTED
        "unpaved" -> DirectionsCriteria.EXCLUDE_UNPAVED
        "cashOnlyTolls" -> DirectionsCriteria.EXCLUDE_CASH_ONLY_TOLLS
        else -> {
          dispatchError(
            mapOf(
              "code" to "INVALID_ROAD_CLASS",
              "message" to "Unknown routeExclusions.roadClasses entry '$raw'. Ignored."
            )
          )
          null
        }
      }
    }

    if (dropped.isNotEmpty()) {
      dispatchError(
        mapOf(
          "code" to "ROAD_CLASS_NOT_SUPPORTED_BY_PROFILE",
          "message" to "The '$routeProfile' profile only supports excluding ferry and " +
            "cashOnlyTolls, so ${dropped.sorted().joinToString(", ")} " +
            "${if (dropped.size == 1) "was" else "were"} dropped. Sending them would " +
            "have failed the route request."
        )
      )
    }

    val rawLocations = exclusions["locations"] as? List<*>
    if (!rawLocations.isNullOrEmpty() && !driving) {
      dispatchError(
        mapOf(
          "code" to "EXCLUDED_LOCATIONS_NOT_SUPPORTED_BY_PROFILE",
          "message" to "routeExclusions.locations is only supported on the driving and " +
            "driving-traffic profiles, so it was dropped for '$routeProfile'."
        )
      )
    }

    var points = (if (driving) rawLocations else null)?.mapNotNull { entry ->
      val map = entry as? Map<*, *> ?: return@mapNotNull null
      val latitude = (map["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      val longitude = (map["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      Point.fromLngLat(longitude, latitude)
    }

    // The API rejects the whole request past 50 points, so trim rather than let
    // a long list fail the route outright.
    if (points != null && points.size > 50) {
      dispatchError(
        mapOf(
          "code" to "TOO_MANY_EXCLUDED_LOCATIONS",
          "message" to "routeExclusions.locations supports at most 50 points; " +
            "using the first 50 of ${points.size}."
        )
      )
      points = points.take(50)
    }

    if (criteria.isNullOrEmpty() && points.isNullOrEmpty()) return

    val exclude = Exclude.builder().apply {
      if (!criteria.isNullOrEmpty()) criteria(criteria)
      if (!points.isNullOrEmpty()) points(points)
    }.build()
    builder.excludeObject(exclude)
  }

  /**
   * Apply the `vehicle` prop.
   *
   * Metres and metric tons, matching both the Directions API's units and the
   * iOS `Measurement` units the same prop feeds there, so the prop means the
   * same thing on both platforms.
   */
  private fun applyVehicleConstraints(builder: RouteOptions.Builder) {
    val constraints = vehicleConstraints ?: return
    if (constraints.isEmpty()) return
    // `max_height`, `max_width` and `max_weight` are driving-only: the
    // Directions API answers 422 "Invalid query param" for them on walking and
    // cycling, which fails the whole request. Verified against the API.
    if (!isDrivingProfile()) {
      dispatchError(
        mapOf(
          "code" to "VEHICLE_NOT_SUPPORTED_BY_PROFILE",
          "message" to "Vehicle dimensions are only supported on the driving and " +
            "driving-traffic profiles, so `vehicle` was dropped for '$routeProfile'. " +
            "Sending it would have failed the route request."
        )
      )
      return
    }
    (constraints["maxHeight"] as? Number)?.let { builder.maxHeight(it.toDouble()) }
    (constraints["maxWidth"] as? Number)?.let { builder.maxWidth(it.toDouble()) }
    (constraints["maxWeight"] as? Number)?.let { builder.maxWeight(it.toDouble()) }
  }

  // MARK: Standard style configuration

  /**
   * Apply the `mapStyleConfig` prop to the basemap style import.
   *
   * Standard styles expose their basemap as a configurable import rather than
   * fixed layers, which is what `setStyleImportConfigProperty` writes to.
   *
   * Classic styles (`navigation-day-v1`, `streets-v12`) have no `basemap`
   * import, so every write returns an error. That is not worth surfacing per
   * property — the consumer just paired a config with a style that has nothing
   * to configure — so it is reported once and then dropped.
   *
   * Called on every style load as well as on prop changes: writes are ignored
   * while a style is still loading, so a config set at mount would land too
   * early, and switching `mapStyleUri` replaces the import that the previous
   * config was written to.
   */
  private fun applyMapStyleConfig() {
    val config = mapStyleConfig ?: return
    if (config.isEmpty()) return
    val mapView = attachedMapView ?: return

    val values = buildMap<String, Value> {
      (config["lightPreset"] as? String)?.let { put("lightPreset", Value.valueOf(it)) }
      for (key in BOOLEAN_STYLE_CONFIG_KEYS) {
        (config[key] as? Boolean)?.let { put(key, Value.valueOf(it)) }
      }
    }
    if (values.isEmpty()) return

    val failed = mutableListOf<String>()
    runCatching {
      val style = mapView.mapboxMap.style ?: return
      for ((key, value) in values) {
        val result = style.setStyleImportConfigProperty("basemap", key, value)
        if (result.isError) failed += key
      }
    }.onFailure {
      Log.w(TAG, "Could not apply mapStyleConfig", it)
      return
    }

    if (failed.isNotEmpty() && !warnedMapStyleConfigUnsupported) {
      warnedMapStyleConfigUnsupported = true
      dispatchError(
        mapOf(
          "code" to "MAP_STYLE_CONFIG_UNSUPPORTED",
          "message" to "This map style has no configurable basemap import, so " +
            "mapStyleConfig (${failed.sorted().joinToString(", ")}) had no effect. " +
            "Use a Mapbox Standard style such as mapbox://styles/mapbox/standard."
        )
      )
    }
  }

  /**
   * Build the process-wide `NavigationOptions`.
   *
   * `distanceFormatterOptions` is the only way the `distanceUnit` prop can reach
   * the SDK: everything that renders a distance — the maneuver banner's step
   * distance, the voice instructions, the trip-progress readouts — formats it
   * through the formatter built from these options. Before this, `distanceUnit`
   * was stored on both the view and the module and applied nowhere, so Android
   * silently used the locale default; the same app that showed "200 m" on iOS
   * showed "100 ft" on Android.
   */
  private fun buildNavigationOptions(): NavigationOptions =
    NavigationOptions.Builder(context.applicationContext)
      .distanceFormatterOptions(
        DistanceFormatterOptions.Builder(context.applicationContext)
          .unitType(resolveUnitType())
          .build()
      )
      .build()

  private fun resolveUnitType(): UnitType =
    if (distanceUnit.trim().lowercase() == "imperial") UnitType.IMPERIAL else UnitType.METRIC

  /**
   * Re-apply `distanceUnit` when it changes after the navigator exists.
   *
   * The formatter is baked into the process-wide `NavigationOptions`, so the
   * only way to change it is to set the app up again — which recreates
   * `MapboxNavigation` and would drop a trip in progress. So it is re-applied
   * only while no session is running; mid-trip it is deferred to the next one
   * and said so out loud rather than appearing to work.
   */
  private fun applyDistanceUnit() {
    if (distanceUnit == appliedDistanceUnit) return
    if (mapboxNavigation == null) return
    if (hasRequestedRoute) {
      Log.w(
        TAG,
        "distanceUnit changed to '$distanceUnit' during an active session. Mapbox " +
          "bakes the distance formatter into the process-wide NavigationOptions, so " +
          "this takes effect on the next navigation session."
      )
      return
    }
    runCatching {
      MapboxNavigationApp.setup(buildNavigationOptions())
      appliedDistanceUnit = distanceUnit
    }.onFailure { Log.w(TAG, "Could not re-apply distanceUnit '$distanceUnit'", it) }
  }

  private fun resolveStyleUri(): String {
    val night = prefersNightMap()
    // Prefer the prop matching the resolved theme, then the theme-agnostic one,
    // then the other theme's — so a consumer who only sets `mapStyleUriDay`
    // still gets it in dark mode rather than falling through to a default.
    val candidates =
      if (night) listOf(mapStyleUriNight, mapStyleUri, mapStyleUriDay)
      else listOf(mapStyleUriDay, mapStyleUri, mapStyleUriNight)
    return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
      ?: if (night) NAVIGATION_NIGHT_STYLE else NAVIGATION_DAY_STYLE
  }

  /**
   * Resolve `uiTheme` to a day/night choice for the map.
   *
   * `system` follows the Android night-mode configuration, which is the closest
   * equivalent to what iOS's `StyleManager` does. iOS additionally switches on
   * sunrise/sunset; there is no v3 Android counterpart for that, so the theme is
   * the only input here.
   */
  private fun prefersNightMap(): Boolean = when (uiTheme.trim().lowercase()) {
    "dark", "night" -> true
    "light", "day" -> false
    else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
  }

  /**
   * Push the resolved style onto a map that already exists.
   *
   * Without this, `mapStyleUri`, `mapStyleUriDay`, `mapStyleUriNight` and
   * `uiTheme` only had an effect if they were set before the MapView was
   * created: their setters called `applyUiVisibility()`, which touches widget
   * visibility and nothing else, so a style change after mount was dropped.
   */
  private fun applyMapStyle() {
    val mapView = attachedMapView ?: return
    val styleUri = resolveStyleUri()
    if (styleUri == appliedStyleUri) return
    appliedStyleUri = styleUri
    runCatching { mapView.mapboxMap.loadStyle(styleUri) { applyMapStyleConfig() } }
      .onFailure { Log.w(TAG, "Could not load map style '$styleUri'", it) }
  }



  private fun ensureNavigation() {
    if (mapboxNavigation != null) return

    // Set up MapboxNavigationApp before reading from it.
    //
    // v2 never had to do this: Drop-In's `NavigationView` called
    // `MapboxNavigationApp.setup(...)` internally as part of its own
    // construction, so `current()` was always populated by the time we asked.
    // With Drop-In gone nothing initialises it, and `current()` returns null
    // forever — the view then fails with NAVIGATION_INIT_FAILED and never
    // requests a route. This is invisible at compile time.
    val activity = expoAppContext.currentActivity as? AppCompatActivity
    runCatching {
      if (!MapboxNavigationApp.isSetup()) {
        MapboxNavigationApp.setup(buildNavigationOptions())
        appliedDistanceUnit = distanceUnit
      }
      // Attaching to the host activity's lifecycle is what actually creates the
      // MapboxNavigation instance that `current()` returns.
      activity?.let { MapboxNavigationApp.attach(it) }
    }.onFailure { throwable ->
      Log.w(TAG, "MapboxNavigationApp setup failed", throwable)
    }

    val nav = runCatching { MapboxNavigationApp.current() }.getOrElse { throwable ->
      dispatchError(mapOf("code" to "NAVIGATION_INIT_FAILED", "message" to (throwable.message ?: "Failed to init MapboxNavigation")))
      showPlaceholder("Failed to init navigation.\n${throwable.message ?: ""}".trim())
      return
    } ?: run {
      val message = "MapboxNavigationApp is not attached yet."
      dispatchError(mapOf("code" to "NAVIGATION_INIT_FAILED", "message" to message))
      showPlaceholder("Failed to init navigation.\n$message")
      return
    }

    mapboxNavigation = nav
    nav.registerLocationObserver(locationObserver)
    nav.registerRouteProgressObserver(routeProgressObserver)
    nav.registerBannerInstructionsObserver(bannerInstructionsObserver)
    nav.registerArrivalObserver(arrivalObserver)
    nav.registerRoutesObserver(routesObserver)
    nav.registerOffRouteObserver(offRouteObserver)

    MapboxAudioGuidanceController.setMuted(mute)
    MapboxAudioGuidanceController.setVoiceVolume(voiceVolume)
    MapboxAudioGuidanceController.setLanguage(language)
  }

  private fun startIfReady() {
    if (!enabled) return
    Log.i(TAG, "startIfReady ($EMBEDDED_BUILD): enabled=true hasPerm=${hasLocationPermission()} ownsSession=$ownsNavigationSession")
    if (!hasLocationPermission()) {
      showPlaceholder("Location permission required.\nGrant ACCESS_FINE_LOCATION to start embedded navigation.")
      dispatchError(mapOf("code" to "LOCATION_PERMISSION_REQUIRED", "message" to "Embedded navigation requires location permission."))
      return
    }
    if (!ensureSession()) {
      showPlaceholder("Navigation session conflict.\nStop other navigation sessions first.")
      return
    }

    // Navigator first: `installComponents` binds the UI to a live
    // MapboxNavigation, so the UI cannot be built before it exists.
    ensureNavigation()
    ensureNavigationUi()
    applyUiVisibility()
    setNavigationBarsHidden(true)
    applyCameraMode("start")

    val nav = mapboxNavigation ?: return

    val dest = destination.toAnyPointOrNull()
    if (dest == null) {
      showPlaceholder("Waiting for destination…")
      Log.w(TAG, "startIfReady ($EMBEDDED_BUILD): missing destination")
      return
    }

    // `startOrigin` is documented optional. Drop-In used to source the device
    // location itself; v3 has no equivalent, so fall back to the newest fix
    // from the navigator and, failing that, run free drive until one arrives.
    val origin = startOrigin.toAnyPointOrNull()
      ?: lastJourneyLocation?.let { Point.fromLngLat(it.longitude, it.latitude) }

    if (origin == null) {
      if (!awaitingOriginFix) {
        awaitingOriginFix = true
        Log.i(TAG, "startIfReady ($EMBEDDED_BUILD): no origin yet, starting free drive for a fix")
        runCatching { nav.startTripSession() }.onFailure { throwable ->
          awaitingOriginFix = false
          dispatchError(
            mapOf(
              "code" to "NAVIGATION_INIT_FAILED",
              "message" to (throwable.message ?: "Could not start a location session")
            )
          )
        }
      }
      showPlaceholder("Waiting for your location…")
      return
    }

    if (hasRequestedRoute) return
    hasRequestedRoute = true
    hasEmittedArrival = false
    hidePlaceholder()

    val coordinates = buildList {
      add(origin)
      addAll(parseWaypoints(waypoints))
      add(dest)
    }
    Log.i(
      TAG,
      "startIfReady ($EMBEDDED_BUILD): requesting route coords=${coordinates.size} alt=$routeAlternatives"
    )
    requestRouteAndStartGuidance(nav, coordinates)
  }

  /**
   * Request a route and go straight into guidance.
   *
   * v2 drove this through Drop-In in three steps —
   * `api.startDestinationPreview`, `api.startRoutePreview`, then
   * `api.startActiveGuidance` — with a fallback path for when the preview came
   * back empty. None of those exist in v3, and the preview was never a
   * user-facing step here anyway: the old code walked straight through it. So
   * the flow collapses to requesting routes, handing them to the navigator and
   * starting the session, which also removes the empty-preview fallback.
   *
   * `requestRoutes` and `NavigationRouterCallback` are unchanged from v2.
   */
  private fun requestRouteAndStartGuidance(nav: MapboxNavigation, coordinates: List<Point>) {
    if (coordinates.size < 2) {
      hasRequestedRoute = false
      dispatchError(mapOf("code" to "ROUTE_ERROR", "message" to "At least origin and destination are required."))
      showPlaceholder("Missing origin/destination for route request.")
      return
    }

    val routeOptionsBuilder = RouteOptions.builder()
      .applyDefaultNavigationOptions()
      .applyLanguageAndVoiceUnitOptions(context)
      .coordinatesList(coordinates)
      .alternatives(routeAlternatives)
      .steps(true)
      .bannerInstructions(true)
      .voiceInstructions(true)
      .layersList(MutableList<Int?>(coordinates.size) { null })

    // After `applyDefaultNavigationOptions()`, which sets driving-traffic.
    routeOptionsBuilder.profile(resolveDirectionsProfile())
    applyRouteExclusions(routeOptionsBuilder)
    applyVehicleConstraints(routeOptionsBuilder)

    val routeOptions = routeOptionsBuilder.build()

    nav.requestRoutes(
      routeOptions,
      object : NavigationRouterCallback {
        // `RouterOrigin` was an enum-like type in v2 and is a plain String in v3.
        override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
          if (routes.isEmpty()) {
            hasRequestedRoute = false
            dispatchError(mapOf("code" to "NO_ROUTE", "message" to "No route found"))
            showPlaceholder("No route found.")
            return
          }
          mainHandler.post {
            emitRouteChangeFrom(routes.first())
            nav.setNavigationRoutes(routes)
            startSession(nav, routes.first())
            hidePlaceholder()
            applyUiVisibility()
            applyCameraMode("guidance-started")
          }
        }

        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
          hasRequestedRoute = false
          val message = reasons.firstOrNull()?.message ?: "Route request failed"
          dispatchError(mapOf("code" to "ROUTE_ERROR", "message" to message))
          showPlaceholder("Failed to request a route.")
        }

        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
          hasRequestedRoute = false
        }
      }
    )
  }

  /**
   * Start the trip session, simulating the route when asked.
   *
   * v2 set `api.routeReplayEnabled(...)` on Drop-In and let it decide. v3 makes
   * this an explicit choice of entry point: `startReplayTripSession` consumes
   * events pushed onto `mapboxReplayer`, while `startTripSession` uses real
   * device location.
   */
  private fun startSession(nav: MapboxNavigation, route: NavigationRoute) {
    if (!shouldSimulateRoute) {
      runCatching { nav.startTripSession() }.onFailure { throwable ->
        dispatchError(
          mapOf(
            "code" to "NAVIGATION_INIT_FAILED",
            "message" to (throwable.message ?: "Could not start the trip session")
          )
        )
      }
      return
    }

    runCatching {
      val replayer = nav.mapboxReplayer
      replayer.clearEvents()
      val events = ReplayRouteMapper().mapDirectionsRouteGeometry(route.directionsRoute)
      replayer.pushEvents(events)
      events.firstOrNull()?.let { replayer.seekTo(it) }
      nav.startReplayTripSession()
      replayer.play()
    }.onFailure { throwable ->
      Log.w(TAG, "Route simulation failed; falling back to live location", throwable)
      runCatching { nav.startTripSession() }
    }
  }


  private fun stopEmbedded(emitCancel: Boolean) {
    mainHandler.removeCallbacksAndMessages(null)

    attachedMapView?.let { mapView ->
      clearNavigationMarkers(mapView)
      // We own the MapView now, so we are responsible for detaching it. Under
      // Drop-In removing the NavigationView took its internal map with it.
      runCatching { nativeLayer.removeView(mapView) }
    }
    attachedMapView = null
    viewportDataSource = null
    navigationCamera = null

    // Detach the installed components explicitly. `installComponents` would
    // otherwise keep them alive until the host activity is destroyed, which
    // outlives this view whenever React Native unmounts it.
    componentInstallations.forEach { installation ->
      runCatching { installation.uninstall() }
    }
    componentInstallations.clear()
    // Held apart from the list so a colours change can replace just this one.
    routeArrowInstallation?.let { installation ->
      runCatching { installation.uninstall() }
    }
    routeArrowInstallation = null

    listOfNotNull(maneuverView, soundButton).forEach { child ->
      runCatching { nativeLayer.removeView(child) }
    }
    maneuverView = null
    soundButton = null
    appliedStyleUri = null
    runCatching { styleLoadedCancelable?.cancel() }
    styleLoadedCancelable = null
    setNavigationBarsHidden(false)

    mapboxNavigation?.let { nav ->
      runCatching { nav.unregisterLocationObserver(locationObserver) }
      runCatching { nav.unregisterRouteProgressObserver(routeProgressObserver) }
      runCatching { nav.unregisterBannerInstructionsObserver(bannerInstructionsObserver) }
      runCatching { nav.unregisterArrivalObserver(arrivalObserver) }
      runCatching { nav.unregisterRoutesObserver(routesObserver) }
      runCatching { nav.unregisterOffRouteObserver(offRouteObserver) }
      runCatching { nav.setNavigationRoutes(emptyList()) }
      // Clearing routes alone leaves the shared MapboxNavigation instance in
      // Free Drive mode, so its foreground-service notification ("Free Drive
      // session") lingers indefinitely after a trip ends. Stop the trip
      // session outright so the notification/service actually tears down.
      runCatching { nav.stopTripSession() }
    }
    mapboxNavigation = null

    hidePlaceholder()
    hasRequestedRoute = false
    hasEmittedArrival = false
    lastJourneyLocation = null
    lastJourneyProgress = null
    lastJourneyBanner = null
    setCameraFollowingState(true, "stop")
    if (emitCancel) dispatchCancelNavigation(emptyMap())
    releaseSession()
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
      // Maps 11 reshaped these options: `geometry` became `annotatedFeature`,
      // and the single anchor plus offset pair became `variableAnchors` — here
      // via the `annotationAnchor` DSL, with one entry to pin the anchor the
      // way v2's fixed `offsetY` did. `selected` is deprecated in favour of
      // `priority`, where a higher number draws on top.
      val viewOptions = viewAnnotationOptions {
        annotatedFeature(AnnotatedFeature.valueOf(marker.point))
        allowOverlap(marker.allowOverlap)
        visible(true)
        priority(if (marker.selected) 1 else 0)
        annotationAnchor {
          anchor(ViewAnnotationAnchor.BOTTOM)
          offsetY(dp(marker.anchorOffsetY ?: metrics.offsetYDp).toDouble())
        }
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
    dispatchCameraFollowingStateChange(
      mapOf(
        "isCameraFollowing" to next,
        "isCameraNotFollowing" to !next,
        "reason" to reason
      )
    )
  }

  /**
   * Apply `cameraPitch` / `cameraZoom` directly to the map camera.
   *
   * The Drop-In UI owns its own NavigationCamera and recomputes the viewport on
   * every location update while following, so these values reliably stick only
   * while the camera is idle or in overview. That is still a real improvement
   * over the previous behaviour, where both setters were no-ops.
   */
  /**
   * Apply `cameraPitch` / `cameraZoom`.
   *
   * v2 wrote straight to `mapboxMap.setCamera(...)`, which the navigation
   * camera then overwrote on its next frame — the code even logged a warning
   * saying so, meaning these two props effectively did nothing while
   * following. v3 exposes the supported mechanism for this
   * (`*PropertyOverride`) on the viewport data source, so the values now
   * actually hold. That is a behaviour change, and a deliberate one: the
   * alternative is knowingly keeping a broken prop.
   */
  private fun applyCameraPitchZoom(reason: String) {
    if (cameraPitch == null && cameraZoom == null) return
    val dataSource = viewportDataSource ?: return

    runCatching {
      if (cameraMode.trim().lowercase() == "overview") {
        cameraPitch?.let { dataSource.overviewPitchPropertyOverride(it) }
        cameraZoom?.let { dataSource.overviewZoomPropertyOverride(it) }
      } else {
        cameraPitch?.let { dataSource.followingPitchPropertyOverride(it) }
        cameraZoom?.let { dataSource.followingZoomPropertyOverride(it) }
      }
      dataSource.evaluate()
    }.onFailure { throwable ->
      Log.w(TAG, "Failed to apply camera pitch/zoom ($reason)", throwable)
    }
  }

  /**
   * Apply the configured location puck.
   *
   * v2 pushed a per-state `LocationPuckOptions` into Drop-In's
   * `customizeViewStyles`. `LocationPuckOptions` still exists in v3 and
   * `LocationPuckFactory` still builds one, but nothing consumes it any more:
   * the `locationPuck` component takes a single `LocationPuck`. The puck is
   * therefore set straight on the map's location component, using the
   * active-navigation puck — the state this view spends nearly all its time in.
   *
   * Per-state pucks are consequently collapsed to one on Android. The JS prop
   * shape is unchanged, so callers keep working, but only the
   * `activeNavigation` (or `default`) entry has a visible effect.
   */
  private fun applyLocationPuck() {
    val mapView = attachedMapView ?: return
    // Asset loading is async; avoid rebuilding for an unchanged configuration.
    if (appliedLocationPuckStates == locationPuckStates) return
    val requested = locationPuckStates

    if (requested.isEmpty()) {
      if (appliedLocationPuckStates != null) {
        runCatching {
          mapView.location.locationPuck = LocationPuckOptions.Builder(context).build().activeNavigationPuck
          appliedLocationPuckStates = null
        }.onFailure { throwable ->
          Log.w(TAG, "Failed to restore the default location puck", throwable)
        }
      }
      return
    }

    LocationPuckFactory.buildOptions(context, requested) { options ->
      // A newer configuration may have arrived while assets were loading.
      if (locationPuckStates !== requested) return@buildOptions
      runCatching {
        mapView.location.locationPuck = options.activeNavigationPuck
        appliedLocationPuckStates = requested
      }.onFailure { throwable ->
        Log.w(TAG, "Failed to apply custom location puck", throwable)
      }
    }
  }

  /**
   * Show or hide the navigation chrome.
   *
   * v2 funnelled this through Drop-In's `customizeViewOptions`, which exposed
   * roughly fifteen `showX` flags covering widgets Drop-In owned — info panel,
   * start/end navigation buttons, route preview button, POI name, arrival text
   * and so on. v3 has no Drop-In and therefore none of those widgets, so only
   * the props that map onto a widget we actually build are honoured; the rest
   * are reported once rather than silently ignored.
   */
  private fun applyUiVisibility() {
    maneuverView?.visibility = if (showsManeuverView) View.VISIBLE else View.GONE
    // The sound toggle is the only action button v3 gives us out of the box.
    soundButton?.visibility = if (showsActionButtons) View.VISIBLE else View.GONE

    if (warnedUnsupportedChrome) return
    val unsupported = buildList {
      if (!showsWayNameLabel) add("showsWayNameLabel")
      if (!showsReportFeedback) add("showsReportFeedback")
      if (!showsEndOfRouteFeedback) add("showsEndOfRouteFeedback")
      // Both are honoured on iOS, but their Android widgets do not lay out
      // usably outside Drop-In's container and cannot be themed from a runtime
      // colour, so neither view is installed. See addNavigationChrome.
      if (showsTripProgress) add("showsTripProgress (use the bottomSheet overlay instead)")
      if (showsSpeedLimits) add("showsSpeedLimits")
      // These are real iOS `NavigationViewController` features with no v3
      // Android counterpart. Listed so they are visibly inert rather than
      // quietly accepted.
      if (annotatesIntersectionsAlongRoute) add("annotatesIntersectionsAlongRoute")
      if (!usesNightStyleWhileInTunnel) add("usesNightStyleWhileInTunnel")
      if (!showsContinuousAlternatives) add("showsContinuousAlternatives")
      // v3 Android installs only the audio-guidance button; the rest of the
      // native floating buttons have no widget. `showsActionButtons` controls
      // the one that exists.
      if (!showNativeAudioGuidanceButton) add("nativeFloatingButtons.showAudioGuidanceButton")
      if (showNativeCameraModeButton) add("nativeFloatingButtons.showCameraModeButton")
      if (showNativeRecenterButton) add("nativeFloatingButtons.showRecenterButton")
      if (showNativeCompassButton) add("nativeFloatingButtons.showCompassButton")
    }
    if (unsupported.isNotEmpty()) {
      warnedUnsupportedChrome = true
      Log.w(
        TAG,
        "These props have no effect on Android with Navigation SDK v3, which " +
          "ships no Drop-In UI to host the corresponding widgets: " +
          unsupported.joinToString(", ")
      )
    }
  }

  private fun applyCameraMode(reason: String) {
    if (cameraMode.trim().lowercase() == "overview") {
      moveCameraToOverviewInternal(reason)
    } else {
      resumeCameraFollowingInternal(reason)
    }
    applyCameraPitchZoom(reason)
  }

  private fun moveCameraToOverviewInternal(reason: String) {
    // v2 had to guess: Drop-In exposed no camera handle, so this reflected over
    // `view.api` looking for any of six plausible method names. v3 gives us the
    // NavigationCamera directly, so the reflection is gone.
    val camera = navigationCamera
    if (camera == null) {
      setCameraFollowingState(false, reason)
      return
    }
    runCatching { camera.requestNavigationCameraToOverview() }
      .onSuccess { setCameraFollowingState(false, reason) }
      .onFailure { throwable ->
        Log.w(TAG, "Failed to request overview camera ($reason)", throwable)
      }
  }

  private fun resumeCameraFollowingInternal(reason: String) {
    val camera = navigationCamera ?: return
    runCatching { camera.requestNavigationCameraToFollowing() }
      .onSuccess { setCameraFollowingState(true, reason) }
      .onFailure { throwable ->
        Log.w(TAG, "Failed to resume following camera ($reason)", throwable)
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
    if (!shouldEmitThrottled(lastJourneyEmitAtMs)) return
    lastJourneyEmitAtMs = nowMs()
    val location = lastJourneyLocation
    emitJourneyData(
      banner = lastJourneyBanner,
      progress = lastJourneyProgress,
      latitude = location?.latitude,
      longitude = location?.longitude,
      bearing = location?.bearing,
      speed = location?.speed,
      altitude = location?.altitude,
      accuracy = location?.horizontalAccuracy
    )
  }

  private fun emitBannerInstructionFrom(instruction: BannerInstructions?) {
    val primary = instruction?.primary()?.text()?.trim().orEmpty()
    if (primary.isEmpty()) return
    val payload = mutableMapOf<String, Any>("primaryText" to primary)
    val secondary = instruction?.secondary()?.text()?.trim().orEmpty()
    if (secondary.isNotEmpty()) {
      payload["secondaryText"] = secondary
    }
    payload["stepDistanceRemaining"] = instruction?.distanceAlongGeometry() ?: 0.0
    dispatchBannerInstruction(payload)
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
    dispatchJourneyDataChange(payload)
  }

  private fun emitRouteChangeFrom(route: NavigationRoute) {
    val geometry = route.directionsRoute.geometry() ?: return
    val points = runCatching { PolylineUtils.decode(geometry, 6) }.getOrElse { return }
    if (points.isEmpty()) return
    val coords = points.map { p ->
      mapOf(
        "latitude" to p.latitude(),
        "longitude" to p.longitude()
      )
    }
    dispatchRouteChange(mapOf("coordinates" to coords))
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


}

@file:OptIn(
  com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI::class,
  com.mapbox.navigation.base.ExperimentalMapboxNavigationAPI::class
)

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
import com.mapbox.api.directions.v5.models.Bearing
import com.mapbox.maps.EdgeInsets
import com.mapbox.navigation.core.directions.session.RoutesExtra
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Value
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.LineLayer
import com.mapbox.maps.extension.style.layers.getLayerAs
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.geojson.utils.PolylineUtils
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalController
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.reroute.RerouteController
import com.mapbox.navigation.core.reroute.RerouteStateV2
import com.mapbox.navigation.base.speed.model.SpeedLimitInfo
import com.mapbox.navigation.base.speed.model.SpeedUnit
import com.mapbox.navigation.base.road.model.Road
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.ui.maps.PredictiveCacheController
import com.mapbox.api.directions.v5.models.BannerComponents
import com.mapbox.api.directions.v5.models.LegStep
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.BannerInstructionsObserver
import com.mapbox.navigation.core.trip.session.LegIndexUpdatedCallback
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.OffRouteObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
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
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.logo.logo
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

class MapboxNavigationView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private data class NavigationMarkerPayload(
    val id: String,
    val point: Point,
    val label: String?,
    val glyph: String,
    val badge: String?,
    val variant: String,
    val customColor: Int?,
    val customBadgeColor: Int?,
    val customOpacity: Float?,
    val size: String,
    val markerStyle: String,
    val showTail: Boolean,
    val selected: Boolean,
    val allowOverlap: Boolean,
    val anchorOffsetY: Int?,
    val image: NavigationMarkerImage? = null,
    val animatesMovement: Boolean = false,
  )

  private data class NavigationMarkerImage(val uri: String, val scale: Float)

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
    private const val MATCHED_LOCATION_MAX_DRIFT_M = 30.0
    private const val WAYPOINT_LAYER_ID = "mapbox-navigation-waypoint-layer"
    private const val MARKER_GLIDE_MAX_DISTANCE_M = 500f
    private const val MARKER_GLIDE_MIN_MS = 300L
    private const val MARKER_GLIDE_MAX_MS = 3_000L
    private const val RAW_FIX_MAX_AGE_MS = 5_000L
    private const val ORNAMENT_EDGE_MARGIN_DP = 8.0
    private const val TAG = "MapboxNavigationView"
    private const val EMBEDDED_BUILD = "3.0.0-embedded-v3-r1"
    private const val GUIDANCE_WATCHDOG_TIMEOUT_MS = 20_000L
    private val ARROW_COLOR_KEYS = listOf("maneuverArrow", "maneuverArrowStroke")
    private const val ORIGIN_MAX_ACCURACY_M = 50.0
    private const val ORIGIN_MAX_AGE_MS = 15_000L
    private const val ORIGIN_WAIT_MS = 4_000L
    private const val ORIGIN_HEADING_MIN_SPEED_MPS = 2.0
    private const val ORIGIN_HEADING_TOLERANCE_DEG = 45.0
    private const val BANNER_DISTANCE_STEP_M = 10.0
    private const val BANNER_DISTANCE_MIN_GAP_MS = 250L
    private const val REROUTE_MIN_TARGET_CHANGE_M = 15.0
    private val REROUTE_RETRY_DELAYS_MS = longArrayOf(2_000L, 5_000L, 10_000L, 20_000L)
    private const val LAYER_ORDER_GUARD_MAX_FRAMES = 180
    private val SESSION_CONFLICT_RETRY_DELAYS_MS = longArrayOf(0L, 100L, 400L)
    private const val TOP_LEVEL_ROUTE_LINE_LAYER_ID = "mapbox-top-level-route-layer"
    private val PUCK_LAYER_IDS = listOf("mapbox-location-indicator-layer", "mapbox-location-model-layer")
    private const val DETACHED_KEEP_ALIVE_MS = 10 * 60_000L
    private const val WAYPOINT_NAMES_MAX_TOTAL_CHARS = 500
    @Volatile private var lastKnownLatitude: Double? = null
    @Volatile private var lastKnownLongitude: Double? = null
    private var appliedDistanceUnit: String? = null
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
  private var guidanceWatchdog: Runnable? = null
  private var hasReceivedRouteProgress = false
  private var hasReceivedRawLocation = false

  private val nativeLayer: FrameLayout = FrameLayout(context).apply {
    layoutParams = FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    )
    setBackgroundColor(Color.BLACK)
  }

  private var enabled = false
  private var ownsNavigationSession = false
  private var keepAliveWhenDetached = false
  private var paused = false
  private val detachedTimeout = Runnable {
    if (paused) {
      Log.i(TAG, "Detached for ${DETACHED_KEEP_ALIVE_MS / 60_000} min; ending the kept-alive session")
      paused = false
      stopEmbedded(emitCancel = true)
    }
  }
  private var sessionConflictRetries = 0

  private var startOrigin: Map<String, Any>? = null
  private var destination: Map<String, Any>? = null
  private var waypoints: List<Map<String, Any>>? = null
  private var routedDestination: Point? = null
  private var routedWaypointsProp: List<Map<String, Any>>? = null
  private var routedIntermediates: List<Map<String, Any>> = emptyList()
  private var pendingDestination: Point? = null
  private var pendingWaypointsProp: List<Map<String, Any>>? = null
  private var pendingIntermediates: List<Map<String, Any>> = emptyList()
  private var rerouteRetryAttempt = 0
  private var isTargetRerouteFailing = false
  private val rerouteRetry = Runnable {
    if (!enabled || !hasActiveRoute || pendingDestination == null) return@Runnable
    if (!hasRequestedRoute) scheduleStart()
  }
  private var navigationMarkers: List<Map<String, Any>>? = null
  private var navigationLines: List<Map<String, Any>>? = null
  private val renderedNavigationLineIds = linkedSetOf<String>()

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
  private val routeLineOptionsUpdates =
    MutableStateFlow<MapboxRouteLineViewDynamicOptionsBuilderBlock>({})
  private var routeArrowInstallation: Installation? = null
  private var locationPuckStates: Map<String, Map<String, Any>> = emptyMap()
  private var appliedLocationPuckStates: Map<String, Map<String, Any>>? = null

  private var placeholderView: TextView? = null
  private var immersiveNavBarsHidden = false
  private var attachedMapView: MapView? = null
  private var ornamentBottomMargin: Double? = null
  private var appliedStyleUri: String? = null
  private var styleLoadedCancelable: com.mapbox.common.Cancelable? = null
  private var layerOrderGuard: com.mapbox.common.Cancelable? = null
  private var layerOrderGuardFrames = 0
  private var hasGuardedArrowLayers = false

  private var maneuverView: MapboxManeuverView? = null
  private var soundButton: MapboxAudioGuidanceButton? = null
  private val componentInstallations = mutableListOf<Installation>()

  private var viewportDataSource: MapboxNavigationViewportDataSource? = null
  private var navigationCamera: NavigationCamera? = null
  private var warnedUnsupportedChrome = false
  private val navigationMarkerViews = linkedMapOf<String, View>()
  private val markerShownAt = mutableMapOf<String, Point>()
  private val markerUpdatedAt = mutableMapOf<String, Long>()
  private val markerAnimators = mutableMapOf<String, android.animation.ValueAnimator>()

  @Volatile private var mapboxNavigation: MapboxNavigation? = null
  private var predictiveCacheController: PredictiveCacheController? = null
  private var maneuverInstallation: Installation? = null
  private var rerouteController: RerouteController? = null
  private var lastOffRouteKey: String? = null
  private var lastWaypointArrivalLeg: Int? = null
  private var lastRoadName: String? = null
  private var hasRequestedRoute = false
  private var routeRequestId: Long? = null
  private var routeGeneration = 0
  private var startScheduled = false
  private var hasActiveRoute = false
  private var originWaitStartedAtMs = 0L
  private var lastEmittedRouteSignature: String? = null
  private var lastBannerSignature: String? = null
  private var lastBannerDistance = Double.NaN
  private var lastBannerEmitAtMs = 0L
  private var languageExplicit = false
  private var cameraPadding: Map<String, Any>? = null
  private var journeyDataEnabled = true
  private var awaitingOriginFix = false
  private var hasEmittedArrival = false
  private var isCameraFollowing = true
  private var touchStartX = 0f
  private var touchStartY = 0f
  private val touchSlopPx = 8f * context.resources.displayMetrics.density
  private var eventThrottleMs = 0L
  private var lastLocationEmitAtMs = 0L
  private var lastProgressEmitAtMs = 0L
  private var lastJourneyEmitAtMs = 0L
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



  private var lastRawLocation: com.mapbox.common.location.Location? = null

  private val locationObserver = object : LocationObserver {
    override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {
      lastRawLocation = rawLocation
      hasReceivedRawLocation = true
    }

    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      val matched = locationMatcherResult.enhancedLocation
      val location = reportedLocation(matched, lastRawLocation)
      lastJourneyLocation = location
      lastKnownLatitude = location.latitude
      lastKnownLongitude = location.longitude
      val roadName = roadNameOf(locationMatcherResult.road)
      lastRoadName = roadName
      if (awaitingOriginFix && (isUsableOrigin(location) || originWaitExpired())) {
        awaitingOriginFix = false
        mainHandler.post { startIfReady() }
      }
      if (!shouldEmitThrottled(lastLocationEmitAtMs)) return
      lastLocationEmitAtMs = nowMs()
      dispatchLocationChange(
        mapOf(
          "latitude" to location.latitude,
          "longitude" to location.longitude,
          "bearing" to (location.bearing ?: 0.0),
          "speed" to (location.speed ?: 0.0),
          "altitude" to (location.altitude ?: 0.0),
          "accuracy" to (location.horizontalAccuracy ?: 0.0),
          "timestamp" to location.timestamp.toDouble(),
          "speedLimit" to speedLimit(locationMatcherResult.speedLimitInfo),
          "roadName" to roadName,
          "matched" to (location === matched)
        )
      )
      emitJourneySnapshot()
    }
  }

  private fun reportedLocation(
    matched: com.mapbox.common.location.Location,
    raw: com.mapbox.common.location.Location?
  ): com.mapbox.common.location.Location {
    if (raw == null) return matched
    if (kotlin.math.abs(raw.timestamp - matched.timestamp) > RAW_FIX_MAX_AGE_MS) return matched
    val accuracy = raw.horizontalAccuracy ?: return matched
    if (accuracy < 0) return matched
    val distance = FloatArray(1)
    android.location.Location.distanceBetween(
      matched.latitude, matched.longitude, raw.latitude, raw.longitude, distance
    )
    val allowed = maxOf(MATCHED_LOCATION_MAX_DRIFT_M, accuracy * 2)
    return if (distance[0].toDouble() > allowed) raw else matched
  }

  private fun speedLimit(info: SpeedLimitInfo?): Map<String, Any>? {
    val speed = info?.speed?.takeIf { it > 0 } ?: return null
    return when (info.unit) {
      SpeedUnit.MILES_PER_HOUR -> mapOf("value" to speed, "unit" to "mph")
      SpeedUnit.METERS_PER_SECOND -> mapOf("value" to Math.round(speed * 3.6).toInt(), "unit" to "km/h")
      else -> mapOf("value" to speed, "unit" to "km/h")
    }
  }


  private fun roadNameOf(road: Road?): String? {
    val text = road?.components
      ?.mapNotNull { it.text.trim().takeIf { part -> part.isNotEmpty() } }
      ?.joinToString(" ")
      ?.replace(Regex("\\s+"), " ")
      ?.trim()
    return text?.takeIf { it.isNotEmpty() }
  }

  private val bannerInstructionsObserver = BannerInstructionsObserver { banner ->
    lastJourneyBanner = banner
    emitBannerInstructionFrom(banner)
    emitJourneySnapshot()
  }

  private fun hostWaypointIndex(legIndex: Int): Int {
    val passed = (routedWaypointsProp?.size ?: routedIntermediates.size) - routedIntermediates.size
    return passed.coerceAtLeast(0) + legIndex
  }

  private fun emitArrivalIfNeeded(routeProgress: RouteProgress? = null) {
    if (hasEmittedArrival || routeRequestId != null || pendingDestination != null) return
    hasEmittedArrival = true
    val name = (destination?.get("name") as? String)?.trim()?.takeIf { it.isNotEmpty() }
    dispatchArrive(
      mapOf(
        "index" to routeProgress?.currentLegProgress?.legIndex?.let(::hostWaypointIndex),
        "name" to (name ?: "Destination"),
        "isFinalDestination" to true,
        "remainingWaypoints" to (routeProgress?.remainingWaypoints ?: 0)
      )
    )
  }

  private val routeProgressObserver = RouteProgressObserver { progress: RouteProgress ->
    if (!hasReceivedRouteProgress) {
      hasReceivedRouteProgress = true
      cancelGuidanceWatchdog()
    }
    lastJourneyProgress = progress
    lastJourneyBanner = progress.bannerInstructions ?: lastJourneyBanner
    if (!hasGuardedArrowLayers) {
      hasGuardedArrowLayers = true
      armLayerOrderGuard()
    }

    if (shouldEmitThrottled(lastProgressEmitAtMs)) {
      lastProgressEmitAtMs = nowMs()
      dispatchRouteProgressChange(
        mapOf(
          "distanceTraveled" to progress.distanceTraveled.toDouble(),
          "distanceRemaining" to progress.distanceRemaining.toDouble(),
          "durationRemaining" to progress.durationRemaining,
          "fractionTraveled" to progress.fractionTraveled.toDouble(),
          "legIndex" to (progress.currentLegProgress?.legIndex ?: 0),
          "legDistanceRemaining" to progress.currentLegProgress?.distanceRemaining?.toDouble(),
          "legDurationRemaining" to progress.currentLegProgress?.durationRemaining
        )
      )
    }

    if (!hasEmittedArrival && progress.distanceRemaining <= 5.0) {
      emitArrivalIfNeeded(progress)
    }
    emitBannerInstructionFrom(progress.bannerInstructions)
    emitJourneySnapshot()
  }

  private val offRouteObserver = OffRouteObserver { offRoute ->
    emitOffRoute(offRoute, rerouteFailed = false)
  }

  private val rerouteStateObserver = RerouteController.RerouteStateV2Observer { state ->
    when (state) {
      is RerouteStateV2.FetchingRoute -> emitOffRoute(true, rerouteFailed = false)
      is RerouteStateV2.Failed -> emitOffRoute(true, rerouteFailed = true)
      is RerouteStateV2.Deviation.RouteIgnored -> emitOffRoute(false, rerouteFailed = false)
      else -> Unit
    }
  }

  private fun emitOffRoute(
    offRoute: Boolean,
    rerouteFailed: Boolean,
    reason: String = "deviation",
  ) {
    if (!offRoute && isTargetRerouteFailing) return
    val key = "$offRoute|$rerouteFailed|$reason"
    if (key == lastOffRouteKey) return
    if (!offRoute && lastOffRouteKey == null) return
    lastOffRouteKey = key
    val payload = mutableMapOf<String, Any?>("offRoute" to offRoute, "reason" to reason)
    if (rerouteFailed) payload["rerouteFailed"] = true
    dispatchOffRoute(payload)
  }

  private val holdLegArrivalController = object : ArrivalController {
    override fun navigateNextRouteLeg(routeLegProgress: RouteLegProgress): Boolean = false
  }

  private val arrivalObserver = object : ArrivalObserver {
    override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
      emitArrivalIfNeeded(routeProgress)
    }

    override fun onWaypointArrival(routeProgress: RouteProgress) {
      val legIndex = routeProgress.currentLegProgress?.legIndex
      if (legIndex != null && legIndex == lastWaypointArrivalLeg) return
      lastWaypointArrivalLeg = legIndex
      dispatchWaypointArrive(
        mapOf(
          "index" to routeProgress.currentLegProgress?.legIndex?.let(::hostWaypointIndex),
          "name" to (currentLegDestinationName(routeProgress) ?: ""),
          "isFinalDestination" to false,
          "remainingWaypoints" to routeProgress.remainingWaypoints
        )
      )
    }

    override fun onNextRouteLegStart(
      routeLegProgress: com.mapbox.navigation.base.trip.model.RouteLegProgress
    ) = Unit
  }

  private fun currentLegDestinationName(routeProgress: RouteProgress): String? {
    val legIndex = routeProgress.currentLegProgress?.legIndex ?: return null
    return (routedIntermediates.getOrNull(legIndex)?.get("name") as? String)?.trim()?.takeIf { it.isNotEmpty() }
  }

  private val routesObserver = RoutesObserver { routeUpdateResult ->
    if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
      hasGuardedArrowLayers = false
      armLayerOrderGuard()
    }
    routeUpdateResult.navigationRoutes.firstOrNull()?.let { route ->
      if (routeUpdateResult.reason != RoutesExtra.ROUTES_UPDATE_REASON_REFRESH) {
        emitRouteChangeFrom(route)
      }
      if (routeUpdateResult.reason == RoutesExtra.ROUTES_UPDATE_REASON_REROUTE) {
        runCatching { (route.directionsRoute.legs()?.size ?: 0) - 1 }.getOrNull()?.let { remaining ->
          routedIntermediates = routedIntermediates.takeLast(remaining.coerceAtLeast(0))
        }
        lastWaypointArrivalLeg = null
        emitOffRoute(false, rerouteFailed = false)
      }
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
    Log.i(TAG, "Embedded navigation view attached ($EMBEDDED_BUILD) paused=$paused")
    if (paused) {
      resumeEmbedded()
      return
    }
    if (enabled) startIfReady()
  }

  override fun onDetachedFromWindow() {
    if (shouldPauseOnDetach()) pauseEmbedded() else stopEmbedded(emitCancel = false)
    super.onDetachedFromWindow()
  }

  private fun shouldPauseOnDetach(): Boolean =
    keepAliveWhenDetached && enabled && ownsNavigationSession &&
      mapboxNavigation != null && attachedMapView != null

  private fun pauseEmbedded() {
    if (paused) return
    paused = true
    Log.i(TAG, "Detached with keepAliveWhenDetached; keeping the navigation session running")
    keepScreenOn = false
    setNavigationBarsHidden(false)
    mainHandler.removeCallbacks(detachedTimeout)
    mainHandler.postDelayed(detachedTimeout, DETACHED_KEEP_ALIVE_MS)
  }

  private fun resumeEmbedded() {
    paused = false
    mainHandler.removeCallbacks(detachedTimeout)
    if (!enabled || mapboxNavigation == null || attachedMapView == null) {
      stopEmbedded(emitCancel = false)
      if (enabled) startIfReady()
      return
    }
    keepScreenOn = true
    setNavigationBarsHidden(true)
    scheduleLayoutNudges()
    applyCameraPadding()
    if (isCameraFollowing) resumeCameraFollowingInternal("reattach")
    dispatchCameraFollowingStateChange(
      mapOf(
        "isCameraFollowing" to isCameraFollowing,
        "isCameraNotFollowing" to !isCameraFollowing,
        "reason" to "reattach"
      )
    )
    lastBannerSignature = null
    lastBannerDistance = Double.NaN
    runCatching { mapboxNavigation?.getNavigationRoutes()?.firstOrNull() }
      .getOrNull()
      ?.let { emitRouteChangeFrom(it) }
    emitBannerInstructionFrom(lastJourneyBanner)
  }

  fun destroyFromReact() {
    enabled = false
    paused = false
    stopEmbedded(emitCancel = false)
  }

  fun setKeepAliveWhenDetached(value: Boolean) {
    keepAliveWhenDetached = value
    if (!value && paused) {
      paused = false
      stopEmbedded(emitCancel = false)
    }
  }

  fun setNavigationEnabled(next: Boolean) {
    if (next == enabled) return
    enabled = next
    if (next) scheduleStart() else {
      paused = false
      stopEmbedded(emitCancel = false)
    }
  }

  fun setStartOrigin(origin: Map<String, Any>?) {
    val changed = !samePoint(startOrigin, origin)
    startOrigin = origin
    if (changed && !hasActiveRoute) requestNewRoute()
  }

  fun setDestination(dest: Map<String, Any>?) {
    val reference = pendingDestination ?: routedDestination ?: destination.toAnyPointOrNull()
    val next = dest.toAnyPointOrNull()
    val changed = !samePlace(reference, next) ||
      approachOf(destination) != approachOf(dest)
    destination = dest
    if (!changed) return
    if (enabled) {
      dispatchDestinationChanged(dest.toAnyPointOrNull()?.let { mapOf("latitude" to it.latitude(), "longitude" to it.longitude()) } ?: emptyMap())
    }
    requestNewRoute()
  }

  fun setWaypoints(wps: List<Map<String, Any>>?) {
    val changed = !sameWaypointList(pendingWaypointsProp ?: routedWaypointsProp ?: waypoints, wps)
    waypoints = wps
    if (changed) requestNewRoute()
  }

  private fun approachOf(value: Map<String, Any>?): String? =
    (value?.get("approach") as? String)?.trim()?.lowercase()?.takeIf { it == "curb" }

  private fun samePlace(a: Point?, b: Point?): Boolean {
    if (a == null || b == null) return a == null && b == null
    return distanceMeters(a, b) < REROUTE_MIN_TARGET_CHANGE_M
  }

  private fun sameWaypointList(a: List<Map<String, Any>>?, b: List<Map<String, Any>>?): Boolean {
    val la = validWaypoints(a)
    val lb = validWaypoints(b)
    if (la.size != lb.size) return false
    return la.zip(lb).all { (x, y) ->
      samePlace(x.toAnyPointOrNull(), y.toAnyPointOrNull()) && approachOf(x) == approachOf(y)
    }
  }

  private fun distanceMeters(a: Point, b: Point): Double {
    val r = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude())
    val lat2 = Math.toRadians(b.latitude())
    val dLat = lat2 - lat1
    val dLng = Math.toRadians(b.longitude() - a.longitude())
    val h = kotlin.math.sin(dLat / 2).let { it * it } +
      kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLng / 2).let { it * it }
    return 2 * r * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
  }

  fun setOrnamentBottomMargin(margin: Double?) {
    ornamentBottomMargin = margin?.takeIf { it.isFinite() && it >= 0.0 }
    applyOrnamentMargins()
  }

  private fun applyOrnamentMargins() {
    val mapView = attachedMapView ?: return
    val bottom = ornamentBottomMargin ?: return
    val px = ((bottom + ORNAMENT_EDGE_MARGIN_DP) * context.resources.displayMetrics.density).toFloat()
    runCatching {
      mapView.logo.marginBottom = px
      mapView.attribution.marginBottom = px
    }.onFailure { throwable -> Log.w(TAG, "Failed to move the map ornaments", throwable) }
  }

  fun setCameraPadding(padding: Map<String, Any>?) {
    cameraPadding = padding
    applyCameraPadding()
  }

  fun setJourneyDataEnabled(value: Boolean) {
    journeyDataEnabled = value
  }

  private fun requestNewRoute() {
    cancelRouteRequest()
    mainHandler.removeCallbacks(rerouteRetry)
    rerouteRetryAttempt = 0
    hasRequestedRoute = false
    scheduleStart()
  }

  private fun scheduleStart() {
    if (!enabled || startScheduled) return
    startScheduled = true
    mainHandler.post {
      startScheduled = false
      if (enabled) startIfReady()
    }
  }

  private fun cancelRouteRequest() {
    routeGeneration += 1
    val id = routeRequestId ?: return
    routeRequestId = null
    runCatching { mapboxNavigation?.cancelRouteRequest(id) }
  }

  private fun samePoint(a: Map<String, Any>?, b: Map<String, Any>?): Boolean {
    val pa = a.toAnyPointOrNull()
    val pb = b.toAnyPointOrNull()
    if (pa == null || pb == null) return pa == null && pb == null
    return pa.latitude() == pb.latitude() && pa.longitude() == pb.longitude()
  }

  private fun isUsableOrigin(location: com.mapbox.common.location.Location): Boolean {
    val accuracy = location.horizontalAccuracy
    if (accuracy != null && accuracy > ORIGIN_MAX_ACCURACY_M) return false
    val ageMs = System.currentTimeMillis() - location.timestamp
    if (ageMs < -60_000L || ageMs > 86_400_000L) return true
    return ageMs <= ORIGIN_MAX_AGE_MS
  }

  private fun originWaitExpired(): Boolean =
    originWaitStartedAtMs > 0L && nowMs() - originWaitStartedAtMs >= ORIGIN_WAIT_MS

  private fun originHeading(location: com.mapbox.common.location.Location): Double? {
    val bearing = location.bearing ?: return null
    val speed = location.speed ?: return null
    if (!bearing.isFinite() || speed < ORIGIN_HEADING_MIN_SPEED_MPS) return null
    return ((bearing % 360.0) + 360.0) % 360.0
  }

  private data class RouteOrigin(val point: Point, val heading: Double?)

  private fun resolveRouteOrigin(): RouteOrigin? {
    val explicit = startOrigin.toAnyPointOrNull()
    if (explicit != null && !hasActiveRoute) return RouteOrigin(explicit, null)
    val fix = lastJourneyLocation
    if (fix != null && (isUsableOrigin(fix) || originWaitExpired())) {
      return RouteOrigin(Point.fromLngLat(fix.longitude, fix.latitude), originHeading(fix))
    }
    return explicit?.let { RouteOrigin(it, null) }
  }

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

  fun setNavigationLines(lines: List<Map<String, Any>>?) {
    navigationLines = lines
    renderNavigationLinesIfPossible()
  }

  fun setShouldSimulateRoute(simulate: Boolean) {
    shouldSimulateRoute = simulate
  }

  fun setShowCancelButton(show: Boolean) {
    if (!show) {
      Log.w(TAG, "showCancelButton is not currently supported by the Android embedded Drop-In view and will be ignored.")
    }
  }

  private fun mayDriveSharedVoice(): Boolean = ownsNavigationSession || activeInstance == null

  fun setMute(muted: Boolean) {
    mute = muted
    if (mayDriveSharedVoice()) MapboxAudioGuidanceController.setMuted(muted)
  }

  fun setVoiceVolume(volume: Double) {
    voiceVolume = volume
    if (mayDriveSharedVoice()) MapboxAudioGuidanceController.setVoiceVolume(volume)
  }

  fun setDistanceUnit(unit: String) {
    distanceUnit = unit
    applyDistanceUnit()
  }

  fun setLanguage(lang: String) {
    language = lang
    languageExplicit = lang.isNotBlank()
    if (mayDriveSharedVoice()) MapboxAudioGuidanceController.setLanguage(lang)
  }

  fun setCameraMode(mode: String) {
    val normalized = mode.trim().lowercase()
    cameraMode = if (normalized == "overview") "overview" else "following"
    applyCameraMode("prop")
  }
  fun setCameraPitch(pitch: Double) {
    cameraPitch = pitch.takeIf { it.isFinite() }?.coerceIn(0.0, 85.0)
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

  fun setRouteProfile(profile: String?) {
    val next = profile?.trim()?.ifEmpty { null } ?: "driving-traffic"
    if (next == routeProfile) return
    routeProfile = next
    restartRouteRequest()
  }

  fun setRouteExclusions(exclusions: Map<String, Any>?) {
    if (exclusions == routeExclusions) return
    routeExclusions = exclusions
    restartRouteRequest()
  }

  fun setVehicle(constraints: Map<String, Any>?) {
    if (constraints == vehicleConstraints) return
    vehicleConstraints = constraints
    restartRouteRequest()
  }

  fun setMapStyleConfig(config: Map<String, Any>?) {
    if (config == mapStyleConfig) return
    mapStyleConfig = config
    applyMapStyleConfig()
  }

  private fun restartRouteRequest() {
    requestNewRoute()
  }

  fun setRouteAlternatives(enabled: Boolean) {
    if (routeAlternatives == enabled) return
    routeAlternatives = enabled
    requestNewRoute()
  }

  fun setShowsSpeedLimits(enabled: Boolean) {
    showsSpeedLimits = enabled
    applyUiVisibility()
  }

  fun setShowsWayNameLabel(enabled: Boolean) {
    showsWayNameLabel = enabled
    applyUiVisibility()
  }

  private var showsWaypoints = true

  fun setShowsWaypoints(enabled: Boolean) {
    showsWaypoints = enabled
    applyWaypointVisibility()
  }

  private fun armLayerOrderGuard() {
    val mapView = attachedMapView ?: return
    layerOrderGuardFrames = 0
    if (layerOrderGuard != null) return
    layerOrderGuard = runCatching {
      mapView.mapboxMap.subscribeRenderFrameFinished {
        layerOrderGuardFrames += 1
        val settled = runCatching { enforceLayerOrder(mapView) }.getOrDefault(true)
        if (settled || layerOrderGuardFrames >= LAYER_ORDER_GUARD_MAX_FRAMES) cancelLayerOrderGuard()
      }
    }.getOrNull()
  }

  private fun cancelLayerOrderGuard() {
    runCatching { layerOrderGuard?.cancel() }
    layerOrderGuard = null
  }

  private fun enforceLayerOrder(mapView: MapView): Boolean {
    if (attachedMapView !== mapView) return true
    val style = mapView.mapboxMap.style ?: return false
    if (!style.isStyleLoaded()) return false
    val ids = style.styleLayers.map { it.id }
    val topOverlay = ids.indexOfLast { isRouteOverlayLayer(it) }
    if (topOverlay < 0) return false
    applyWaypointVisibility()
    var moved = false
    for (puckLayer in PUCK_LAYER_IDS) {
      val index = ids.indexOf(puckLayer)
      if (index in 0 until topOverlay) {
        style.moveStyleLayer(puckLayer, null)
        moved = true
      }
    }
    if (moved) Log.i(TAG, "Moved the location puck above the route line")
    return PUCK_LAYER_IDS.any { it in ids } && ids.any { it.startsWith("mapbox-navigation-arrow") }
  }

  private fun isRouteOverlayLayer(id: String): Boolean =
    id.startsWith("mapbox-layerGroup") ||
      id == TOP_LEVEL_ROUTE_LINE_LAYER_ID ||
      id.startsWith("mapbox-navigation-arrow") ||
      id == WAYPOINT_LAYER_ID ||
      id.startsWith("rnmbn-line-")

  private var waypointAdvance = "automatic"

  fun setWaypointAdvance(value: String?) {
    waypointAdvance = if (value == "manual") "manual" else "automatic"
    mapboxNavigation?.let { applyArrivalController(it) }
  }

  private fun applyArrivalController(nav: MapboxNavigation) {
    if (waypointAdvance == "manual") {
      runCatching { nav.setArrivalController(holdLegArrivalController) }
        .onFailure { Log.w(TAG, "Could not hold leg advance on arrival", it) }
    } else {
      runCatching { nav.setArrivalController() }
        .onFailure { Log.w(TAG, "Could not restore automatic leg advance", it) }
    }
  }

  private fun applyWaypointVisibility() {
    val style = attachedMapView?.mapboxMap?.style ?: return
    if (!style.styleLayerExists(WAYPOINT_LAYER_ID)) return
    runCatching {
      style.setStyleLayerProperty(
        WAYPOINT_LAYER_ID,
        "visibility",
        Value(if (showsWaypoints) "visible" else "none")
      )
    }
  }

  fun setShowsTripProgress(enabled: Boolean) {
    showsTripProgress = enabled
    applyUiVisibility()
  }

  fun setShowsManeuverView(enabled: Boolean) {
    if (showsManeuverView == enabled) return
    showsManeuverView = enabled
    if (enabled) installManeuverComponentIfNeeded() else uninstallManeuverComponent()
    applyUiVisibility()
  }

  fun setShowsActionButtons(enabled: Boolean) {
    showsActionButtons = enabled
    if (mayDriveSharedVoice()) MapboxAudioGuidanceController.setUserCanToggle(enabled)
    applyUiVisibility()
  }

  fun setShowsReportFeedback(enabled: Boolean) {
    showsReportFeedback = enabled
    if (!enabled) {
      Log.w(TAG, "showsReportFeedback is not supported by the Android embedded Drop-In view and will be ignored.")
    }
  }


  fun setShowsContinuousAlternatives(enabled: Boolean) {
    if (showsContinuousAlternatives == enabled) return
    showsContinuousAlternatives = enabled
    mapboxNavigation?.let { nav ->
      runCatching { nav.setContinuousAlternativesEnabled(enabled) }
        .onFailure { Log.w(TAG, "setContinuousAlternativesEnabled($enabled) failed", it) }
    }
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
    if (!NavigationSessionRegistry.acquireOrTakeOver(sessionOwner)) {
      if (sessionConflictRetries < SESSION_CONFLICT_RETRY_DELAYS_MS.size) {
        val delay = SESSION_CONFLICT_RETRY_DELAYS_MS[sessionConflictRetries]
        sessionConflictRetries += 1
        mainHandler.postDelayed({ if (enabled && !ownsNavigationSession) scheduleStart() }, delay)
        return false
      }
      sessionConflictRetries = 0
      dispatchError(
        mapOf(
          "code" to "NAVIGATION_SESSION_CONFLICT",
          "message" to "Another embedded navigation session is already active. Stop other embedded navigation before mounting this view."
        )
      )
      return false
    }
    ownsNavigationSession = true
    sessionConflictRetries = 0
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
    NavigationSessionRegistry.registerPausedProvider(sessionOwner) { paused }
    NavigationSessionRegistry.registerYieldHandler(sessionOwner) {
      Log.i(TAG, "Paused session yields to a new navigation view")
      paused = false
      stopEmbedded(emitCancel = false)
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

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (attachedMapView != null && w > 0 && h > 0) {
      nudgeChildLayout("onSizeChanged")
    }
  }

  @OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
  private fun ensureNavigationUi() {
    if (attachedMapView != null) return

    val token = runCatching { getMapboxAccessToken() }.getOrElse { throwable ->
      dispatchError(mapOf("code" to "MISSING_ACCESS_TOKEN", "message" to (throwable.message ?: "Missing mapbox_access_token")))
      showPlaceholder("Missing Mapbox access token.\nCheck EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN + prebuild.")
      return
    }
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
          textureView = true,
          styleUri = resolveStyleUri(),
          cameraOptions = initialCameraOptions()
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
    // WARNING: must precede addView, or the MapView binds to the screen fragment's lifecycle and is destroyed when a screen covers it.
    attachViewTreeOwnersIfPossible(this, activity)
    nativeLayer.addView(mapView)
    attachedMapView = mapView
    appliedStyleUri = resolveStyleUri()
    styleLoadedCancelable = runCatching {
      mapView.mapboxMap.subscribeStyleLoaded {
        applyMapStyleConfig()
        renderedNavigationLineIds.clear()
        renderNavigationLinesIfPossible()
        armLayerOrderGuard()
      }
    }.getOrNull()

    val sound = MapboxAudioGuidanceButton(activity).also { soundButton = it }
    addSoundButton(sound)

    val dataSource = MapboxNavigationViewportDataSource(mapView.mapboxMap)
    val camera = NavigationCamera(mapView.mapboxMap, mapView.camera, dataSource)
    viewportDataSource = dataSource
    navigationCamera = camera
    applyCameraPadding()
    applyOrnamentMargins()

    nav.installComponents(activity) {
      componentInstallations += routeLine(mapView) {
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
        this.viewportDataSource = dataSource
        this.navigationCamera = camera
      }
      componentInstallations += locationPuck(mapView)
      componentInstallations += audioGuidanceButton(sound)
    }
    installManeuverComponentIfNeeded()
    applyChromeColors()
    startPredictiveCache(nav, mapView)

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
    scheduleLayoutNudges()
    hideMapOrnaments(mapView)
    hidePlaceholder()
    applyUiVisibility()
    applyLocationPuck()
    renderNavigationMarkersIfPossible()
    renderNavigationLinesIfPossible()
    applyCameraPitchZoom("ui-attached")
    keepScreenOn = true
  }

  private fun startPredictiveCache(nav: MapboxNavigation, mapView: MapView) {
    if (predictiveCacheController != null) return
    predictiveCacheController = PredictiveCache.start(nav, mapView)
  }

  private fun stopPredictiveCache(mapView: MapView?) {
    val controller = predictiveCacheController ?: return
    predictiveCacheController = null
    PredictiveCache.stop(controller, mapView)
  }


  private fun installManeuverComponentIfNeeded() {
    if (!showsManeuverView || maneuverView != null) return
    val nav = mapboxNavigation ?: return
    if (attachedMapView == null) return
    val activity = expoAppContext.currentActivity as? AppCompatActivity ?: return
    val maneuver = MapboxManeuverView(activity)
    maneuverView = maneuver
    addManeuverView(maneuver)
    runCatching {
      nav.installComponents(activity) {
        maneuverInstallation = maneuver(maneuver) {
          distanceFormatterOptions = DistanceFormatterOptions.Builder(activity)
            .unitType(resolveUnitType())
            .build()
        }
      }
    }.onFailure { Log.w(TAG, "Could not install the maneuver banner", it) }
    applyChromeColors()
    scheduleLayoutNudges()
  }

  private fun uninstallManeuverComponent() {
    maneuverInstallation?.let { runCatching { it.uninstall() } }
    maneuverInstallation = null
    maneuverView?.let { runCatching { nativeLayer.removeView(it) } }
    maneuverView = null
  }

  private fun addManeuverView(maneuver: MapboxManeuverView) {
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

    maneuver.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      tintManeuverDescendants(maneuver)
    }
  }

  private fun addSoundButton(sound: MapboxAudioGuidanceButton) {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

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

  private fun color(key: String): Int? =
    (colorOverrides[key] as? String)?.let { raw ->
      HexColor.parse(raw) ?: run {
        Log.w(TAG, "Ignoring unparseable colors.$key value '$raw'")
        null
      }
    }

  private fun buildRouteArrowOptions(context: Context): RouteArrowOptions? {
    val arrow = color("maneuverArrow")
    val stroke = color("maneuverArrowStroke")
    if (arrow == null && stroke == null) return null

    val builder = RouteArrowOptions.Builder(context)
    arrow?.let { builder.withArrowColor(it) }
    stroke?.let { builder.withArrowCasingColor(it) }
    return builder.build()
  }

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

  private fun applyChromeColors() {
    if (colorOverrides.isEmpty()) return

    maneuverView?.let { maneuver ->
      val background = color("maneuverBackground")
      val subBackground = color("maneuverSubBackground") ?: background
      if (background != null || subBackground != null) {
        val options = ManeuverViewOptions.Builder()
        background?.let { options.maneuverBackgroundColor(it) }
        subBackground?.let {
          options.subManeuverBackgroundColor(it)
          options.upcomingManeuverBackgroundColor(it)
        }
        runCatching { maneuver.updateManeuverViewOptions(options.build()) }
          .onFailure { Log.w(TAG, "Could not apply maneuver background colors", it) }

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
      is MapboxTurnIconManeuver ->
        turnIcon?.let { root.imageTintList = ColorStateList.valueOf(it) }
      is MapboxLaneGuidance ->
        turnIcon?.let { root.imageTintList = ColorStateList.valueOf(it) }
      is ViewGroup -> for (index in 0 until root.childCount) {
        tintManeuverDescendants(root.getChildAt(index))
      }
    }
  }

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
    color("congestionLow")?.let { builder.routeLowCongestionColor(it) }
    color("congestionModerate")?.let { builder.routeModerateCongestionColor(it) }
    color("congestionHeavy")?.let { builder.routeHeavyCongestionColor(it) }
    color("congestionSevere")?.let { builder.routeSevereCongestionColor(it) }
    color("congestionUnknown")?.let { builder.routeUnknownCongestionColor(it) }
    color("restrictedRoad")?.let { builder.restrictedRoadColor(it) }

    return builder.build()
  }


  private fun isDrivingProfile(): Boolean {
    val profile = resolveDirectionsProfile()
    return profile == DirectionsCriteria.PROFILE_DRIVING ||
      profile == DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
  }

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

  private fun applyRouteExclusions(builder: RouteOptions.Builder) {
    val exclusions = routeExclusions ?: return
    val driving = isDrivingProfile()
    val dropped = mutableListOf<String>()

    val criteria = (exclusions["roadClasses"] as? List<*>)?.mapNotNull { raw ->
      val name = (raw as? String)?.trim()
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

  private fun applyVehicleConstraints(builder: RouteOptions.Builder) {
    val constraints = vehicleConstraints ?: return
    if (constraints.isEmpty()) return
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
    stopEmbedded(emitCancel = false)
    runCatching {
      MapboxNavigationApp.setup(buildNavigationOptions())
      appliedDistanceUnit = distanceUnit
    }.onFailure { Log.w(TAG, "Could not re-apply distanceUnit '$distanceUnit'", it) }
    if (enabled) startIfReady()
  }

  private fun releaseStaleNavigation() {
    val held = mapboxNavigation ?: return
    val current = runCatching { MapboxNavigationApp.current() }.getOrNull()
    if (held === current) return
    Log.w(TAG, "MapboxNavigation was replaced under this view; binding to the new one")
    stopEmbedded(emitCancel = false)
  }

  private fun resolveStyleUri(): String {
    val night = prefersNightMap()
    val candidates =
      if (night) listOf(mapStyleUriNight, mapStyleUri, mapStyleUriDay)
      else listOf(mapStyleUriDay, mapStyleUri, mapStyleUriNight)
    return candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
      ?: if (night) NAVIGATION_NIGHT_STYLE else NAVIGATION_DAY_STYLE
  }

  private fun prefersNightMap(): Boolean = when (uiTheme.trim().lowercase()) {
    "dark", "night" -> true
    "light", "day" -> false
    else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
  }

  private fun applyMapStyle() {
    val mapView = attachedMapView ?: return
    val styleUri = resolveStyleUri()
    if (styleUri == appliedStyleUri) return
    appliedStyleUri = styleUri
    val state = mapView.mapboxMap.cameraState
    val cameraBefore = CameraOptions.Builder()
      .center(state.center)
      .zoom(state.zoom)
      .bearing(state.bearing)
      .pitch(state.pitch)
      .padding(state.padding)
      .build()
    runCatching {
      mapView.mapboxMap.loadStyle(styleUri) {
        runCatching { mapView.mapboxMap.setCamera(cameraBefore) }
        applyMapStyleConfig()
        applyWaypointVisibility()
      }
    }
      .onFailure { Log.w(TAG, "Could not load map style '$styleUri'", it) }
  }



  private fun ensureNavigation() {
    if (mapboxNavigation != null) return

    val activity = expoAppContext.currentActivity as? AppCompatActivity
    runCatching {
      if (!MapboxNavigationApp.isSetup() || appliedDistanceUnit != distanceUnit) {
        MapboxNavigationApp.setup(buildNavigationOptions())
        appliedDistanceUnit = distanceUnit
      }
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
    rerouteController = runCatching { nav.getRerouteController() }.getOrNull()?.also { controller ->
      runCatching { controller.registerRerouteStateV2Observer(rerouteStateObserver) }
        .onFailure { Log.w(TAG, "Could not observe reroute state", it) }
    }
    applyArrivalController(nav)
    runCatching { nav.setContinuousAlternativesEnabled(showsContinuousAlternatives) }
      .onFailure { Log.w(TAG, "setContinuousAlternativesEnabled failed", it) }

    MapboxAudioGuidanceController.setMuted(mute)
    MapboxAudioGuidanceController.setVoiceVolume(voiceVolume)
    MapboxAudioGuidanceController.setLanguage(language)
    MapboxAudioGuidanceController.bind(userCanToggle = showsActionButtons)
  }

  private fun startIfReady() {
    if (!enabled) return
    if (!isAttachedToWindow && !paused) return
    Log.i(TAG, "startIfReady ($EMBEDDED_BUILD): enabled=true hasPerm=${hasLocationPermission()} ownsSession=$ownsNavigationSession")
    if (!hasLocationPermission()) {
      showPlaceholder("Location permission required.\nGrant ACCESS_FINE_LOCATION to start embedded navigation.")
      dispatchError(mapOf("code" to "LOCATION_PERMISSION_REQUIRED", "message" to "Embedded navigation requires location permission."))
      return
    }
    releaseStaleNavigation()
    if (!ensureSession()) {
      if (sessionConflictRetries > 0) return
      showPlaceholder("Navigation session conflict.\nStop other navigation sessions first.")
      return
    }

    ensureNavigation()
    ensureNavigationUi()
    applyUiVisibility()
    if (!paused) setNavigationBarsHidden(true)
    if (!hasActiveRoute) applyCameraMode("start")

    val nav = mapboxNavigation ?: return

    val dest = destination.toAnyPointOrNull()
    if (dest == null) {
      showPlaceholder("Waiting for destination…")
      Log.w(TAG, "startIfReady ($EMBEDDED_BUILD): missing destination")
      return
    }

    if (hasRequestedRoute) return

    val origin = resolveRouteOrigin()

    if (origin == null) {
      if (!awaitingOriginFix) {
        awaitingOriginFix = true
        if (originWaitStartedAtMs == 0L) originWaitStartedAtMs = nowMs()
        if (lastJourneyLocation == null) {
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
        mainHandler.postDelayed({
          if (awaitingOriginFix && lastJourneyLocation != null) {
            awaitingOriginFix = false
            startIfReady()
          }
        }, ORIGIN_WAIT_MS + 50L)
        mainHandler.postDelayed({
          if (awaitingOriginFix && lastJourneyLocation == null && !hasReceivedRawLocation) {
            val seconds = GUIDANCE_WATCHDOG_TIMEOUT_MS / 1000
            dispatchError(
              mapOf(
                "code" to "NO_LOCATION_FIX",
                "message" to "No startOrigin was given and no location fix arrived within " +
                  "${seconds}s, so no route can be requested yet. Still waiting. On a " +
                  "simulator or emulator, set a location; otherwise pass startOrigin or " +
                  "shouldSimulateRoute."
              )
            )
          }
        }, GUIDANCE_WATCHDOG_TIMEOUT_MS)
      }
      if (!hasActiveRoute) showPlaceholder("Waiting for your location…")
      return
    }

    hasRequestedRoute = true
    originWaitStartedAtMs = 0L
    if (!hasActiveRoute) hidePlaceholder()

    val requested = validWaypoints(waypoints)
    val intermediates =
      if (hasActiveRoute && routedWaypointsProp != null && sameWaypointList(requested, routedWaypointsProp)) {
        val legIndex = runCatching { lastJourneyProgress?.currentLegProgress?.legIndex }.getOrNull() ?: 0
        routedIntermediates.drop(legIndex.coerceAtLeast(0))
      } else {
        requested
      }
    pendingWaypointsProp = requested
    pendingIntermediates = intermediates
    pendingDestination = dest

    val stops = buildList<Map<String, Any>?> {
      add(null)
      addAll(intermediates)
      add(destination)
    }
    val coordinates = buildList {
      add(origin.point)
      addAll(parseWaypoints(intermediates))
      add(dest)
    }
    Log.i(
      TAG,
      "startIfReady ($EMBEDDED_BUILD): requesting route coords=${coordinates.size} alt=$routeAlternatives heading=${origin.heading}"
    )
    requestRouteAndStartGuidance(nav, coordinates, origin.heading, stops)
  }

  private fun validWaypoints(value: List<Map<String, Any>>?): List<Map<String, Any>> =
    value.orEmpty().filter { it.toAnyPointOrNull() != null }

  private fun approachesFor(stops: List<Map<String, Any>?>): List<String?>? {
    val approaches = stops.mapIndexed { index, stop ->
      if (index == 0) null
      else if (approachOf(stop) == "curb") DirectionsCriteria.APPROACH_CURB
      else DirectionsCriteria.APPROACH_UNRESTRICTED
    }
    return approaches.takeIf { list -> list.any { it == DirectionsCriteria.APPROACH_CURB } }
  }

  private fun waypointNamesFor(stops: List<Map<String, Any>?>): List<String?>? {
    val names = stops.mapIndexed { index, stop ->
      if (index == 0) ""
      else ((stop?.get("name") as? String) ?: "")
        .replace(";", ",")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)
    }
    if (names.all { it.isEmpty() }) return null
    if (names.sumOf { it.length } + names.size > WAYPOINT_NAMES_MAX_TOTAL_CHARS) return null
    return names
  }

  private fun requestRouteAndStartGuidance(
    nav: MapboxNavigation,
    coordinates: List<Point>,
    originHeading: Double? = null,
    stops: List<Map<String, Any>?> = emptyList()
  ) {
    if (coordinates.size < 2) {
      hasRequestedRoute = false
      dispatchError(mapOf("code" to "ROUTE_ERROR", "message" to "At least origin and destination are required."))
      showPlaceholder("Missing origin/destination for route request.")
      return
    }

    val profile = resolveDirectionsProfile()
    val routeOptionsBuilder = RouteOptions.builder()
      .applyDefaultNavigationOptions(profile)
      .applyLanguageAndVoiceUnitOptions(context)
      .coordinatesList(coordinates)
      .voiceUnits(
        if (distanceUnit.trim().lowercase() == "imperial") DirectionsCriteria.IMPERIAL
        else DirectionsCriteria.METRIC
      )
      .alternatives(routeAlternatives)
      .steps(true)
      .bannerInstructions(true)
      .voiceInstructions(true)
      .layersList(MutableList<Int?>(coordinates.size) { null })
    if (profile == DirectionsCriteria.PROFILE_DRIVING_TRAFFIC) {
      routeOptionsBuilder.snappingIncludeClosuresList(
        listOf<Boolean?>(true) + List(coordinates.size - 1) { null }
      )
    }

    if (languageExplicit && language.isNotBlank()) routeOptionsBuilder.language(language)
    if (originHeading != null) {
      routeOptionsBuilder.bearingsList(
        listOf<Bearing?>(
          Bearing.builder().angle(originHeading).degrees(ORIGIN_HEADING_TOLERANCE_DEG).build()
        ) + List(coordinates.size - 1) { null }
      )
    }
    if (stops.size == coordinates.size) {
      approachesFor(stops)?.let { routeOptionsBuilder.approachesList(it) }
      waypointNamesFor(stops)?.let { routeOptionsBuilder.waypointNamesList(it) }
    }
    applyRouteExclusions(routeOptionsBuilder)
    applyVehicleConstraints(routeOptionsBuilder)

    val routeOptions = routeOptionsBuilder.build()

    cancelRouteRequest()
    val generation = routeGeneration
    val isCurrent = { generation == routeGeneration && mapboxNavigation === nav && enabled }
    val targetDestination = pendingDestination
    val targetWaypointsProp = pendingWaypointsProp
    val targetIntermediates = pendingIntermediates
    routeRequestId = nav.requestRoutes(
      routeOptions,
      object : NavigationRouterCallback {
        override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
          if (!isCurrent()) return
          if (routes.isEmpty()) {
            routeRequestId = null
            handleRouteFailure("NO_ROUTE", "No route found", "No route found.")
            return
          }
          mainHandler.post {
            if (!isCurrent()) return@post
            routeRequestId = null
            if (nav.isDestroyed) {
              hasRequestedRoute = false
              return@post
            }
            val isFirstRoute = !hasActiveRoute
            val set = runCatching { nav.setNavigationRoutes(routes) }
            if (set.isFailure) {
              Log.w(TAG, "setNavigationRoutes failed", set.exceptionOrNull())
              handleRouteFailure(
                "ROUTE_ERROR",
                set.exceptionOrNull()?.message ?: "Could not set the route",
                "Failed to start guidance."
              )
              return@post
            }
            routedDestination = targetDestination
            routedWaypointsProp = targetWaypointsProp
            routedIntermediates = targetIntermediates
            pendingDestination = null
            pendingWaypointsProp = null
            pendingIntermediates = emptyList()
            mainHandler.removeCallbacks(rerouteRetry)
            rerouteRetryAttempt = 0
            isTargetRerouteFailing = false
            emitRouteChangeFrom(routes.first())
            hasActiveRoute = true
            hasEmittedArrival = false
            lastWaypointArrivalLeg = null
            emitOffRoute(false, rerouteFailed = false)
            startSession(nav, routes.first())
            hidePlaceholder()
            applyUiVisibility()
            if (isFirstRoute) applyCameraMode("guidance-started")
            MapboxAudioGuidanceController.reapply()
          }
        }

        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
          if (!isCurrent()) return
          routeRequestId = null
          val message = reasons.firstOrNull()?.message ?: "Route request failed"
          val retryable = reasons.isEmpty() || reasons.any { it.isRetryable }
          handleRouteFailure("ROUTE_ERROR", message, "Failed to request a route.", retryable)
        }

        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
          if (!isCurrent()) return
          routeRequestId = null
          hasRequestedRoute = false
        }
      }
    )
  }

  private fun handleRouteFailure(
    code: String,
    message: String,
    placeholder: String,
    retryable: Boolean = true,
  ) {
    hasRequestedRoute = false
    if (!hasActiveRoute) {
      dispatchError(mapOf("code" to code, "message" to message))
      showPlaceholder(placeholder)
      return
    }
    if (rerouteRetryAttempt == 0) {
      dispatchError(
        mapOf(
          "code" to "REROUTE_FAILED",
          "message" to "Could not route to the new target; keeping the current route. $message"
        )
      )
    }
    isTargetRerouteFailing = true
    emitOffRoute(true, rerouteFailed = true, reason = "destinationChange")
    if (!retryable) return
    val delay = REROUTE_RETRY_DELAYS_MS[rerouteRetryAttempt.coerceAtMost(REROUTE_RETRY_DELAYS_MS.size - 1)]
    rerouteRetryAttempt += 1
    mainHandler.removeCallbacks(rerouteRetry)
    mainHandler.postDelayed(rerouteRetry, delay)
  }

  private fun startSession(nav: MapboxNavigation, route: NavigationRoute) {
    if (!shouldSimulateRoute) {
      runCatching { nav.startTripSession() }.onFailure { throwable ->
        dispatchError(
          mapOf(
            "code" to "NAVIGATION_INIT_FAILED",
            "message" to (throwable.message ?: "Could not start the trip session")
          )
        )
      }.onSuccess { armGuidanceWatchdog() }
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
      armGuidanceWatchdog()
    }.onFailure { throwable ->
      Log.w(TAG, "Route simulation failed; falling back to live location", throwable)
      runCatching { nav.startTripSession() }.onSuccess { armGuidanceWatchdog() }
    }
  }

  private fun armGuidanceWatchdog() {
    cancelGuidanceWatchdog()
    if (hasReceivedRouteProgress) return

    val work = Runnable {
      guidanceWatchdog = null
      if (hasReceivedRouteProgress) return@Runnable
      val seconds = GUIDANCE_WATCHDOG_TIMEOUT_MS / 1000
      if (hasReceivedRawLocation) {
        dispatchError(
          mapOf(
            "code" to "GUIDANCE_NOT_PROGRESSING",
            "message" to "Guidance started but produced no progress in ${seconds}s, " +
              "although location fixes are arriving. The navigator is most likely not in " +
              "active guidance: check for a second MapboxNavigationView mounted at the same " +
              "time (only one embedded session is supported), and for " +
              "\"billing session is not running\" in the Mapbox log."
          )
        )
      } else {
        dispatchError(
          mapOf(
            "code" to "NO_LOCATION_FIX",
            "message" to "Guidance started but no location fix arrived within " +
              "${seconds}s, so the navigator produced no progress. " +
              "Check that location permission is granted and the emulator has a location set " +
              "(Extended controls > Location, or `adb emu geo fix <lon> <lat>`), " +
              "or pass `shouldSimulateRoute` to replay the route without device fixes."
          )
        )
      }
    }
    guidanceWatchdog = work
    mainHandler.postDelayed(work, GUIDANCE_WATCHDOG_TIMEOUT_MS)
  }

  private fun cancelGuidanceWatchdog() {
    guidanceWatchdog?.let { mainHandler.removeCallbacks(it) }
    guidanceWatchdog = null
  }


  private fun stopEmbedded(emitCancel: Boolean) {
    cancelRouteRequest()
    mainHandler.removeCallbacksAndMessages(null)
    guidanceWatchdog = null
    hasReceivedRouteProgress = false
    hasReceivedRawLocation = false
    paused = false
    keepScreenOn = false
    startScheduled = false
    hasActiveRoute = false
    originWaitStartedAtMs = 0L
    lastEmittedRouteSignature = null
    lastBannerSignature = null
    lastBannerDistance = Double.NaN
    lastOffRouteKey = null
    lastWaypointArrivalLeg = null
    lastRoadName = null
    routedDestination = null
    routedWaypointsProp = null
    routedIntermediates = emptyList()
    pendingDestination = null
    pendingWaypointsProp = null
    pendingIntermediates = emptyList()
    rerouteRetryAttempt = 0
    isTargetRerouteFailing = false
    appliedLocationPuckStates = null

    componentInstallations.forEach { installation ->
      runCatching { installation.uninstall() }
    }
    componentInstallations.clear()
    routeArrowInstallation?.let { installation ->
      runCatching { installation.uninstall() }
    }
    routeArrowInstallation = null
    maneuverInstallation?.let { runCatching { it.uninstall() } }
    maneuverInstallation = null
    runCatching { styleLoadedCancelable?.cancel() }
    styleLoadedCancelable = null
    cancelLayerOrderGuard()
    stopPredictiveCache(attachedMapView)

    attachedMapView?.let { mapView ->
      clearNavigationMarkers(mapView)
      runCatching { nativeLayer.removeView(mapView) }
      runCatching { mapView.onStop() }
      runCatching { mapView.onDestroy() }
        .onFailure { Log.w(TAG, "MapView.onDestroy failed", it) }
    }
    attachedMapView = null
    renderedNavigationLineIds.clear()
    viewportDataSource = null
    navigationCamera = null

    listOfNotNull(maneuverView, soundButton).forEach { child ->
      runCatching { nativeLayer.removeView(child) }
    }
    maneuverView = null
    soundButton = null
    appliedStyleUri = null
    setNavigationBarsHidden(false)

    mapboxNavigation?.let { nav ->
      MapboxAudioGuidanceController.unbind()
      runCatching { nav.unregisterLocationObserver(locationObserver) }
      runCatching { nav.unregisterRouteProgressObserver(routeProgressObserver) }
      runCatching { nav.unregisterBannerInstructionsObserver(bannerInstructionsObserver) }
      runCatching { nav.unregisterArrivalObserver(arrivalObserver) }
      runCatching { nav.unregisterRoutesObserver(routesObserver) }
      runCatching { nav.unregisterOffRouteObserver(offRouteObserver) }
      rerouteController?.let { controller ->
        runCatching { controller.unregisterRerouteStateV2Observer(rerouteStateObserver) }
      }
      runCatching { nav.setArrivalController() }
      runCatching { nav.setContinuousAlternativesEnabled(true) }
      runCatching { nav.setNavigationRoutes(emptyList()) }
      runCatching { nav.stopTripSession() }
    }
    mapboxNavigation = null
    rerouteController = null

    hidePlaceholder()
    hasRequestedRoute = false
    awaitingOriginFix = false
    hasEmittedArrival = false
    lastJourneyLocation = null
    lastRawLocation = null
    lastJourneyProgress = null
    lastJourneyBanner = null
    setCameraFollowingState(true, "stop")
    if (emitCancel) dispatchCancelNavigation(emptyMap())
    releaseSession()
  }





  private data class NavigationLinePayload(
    val id: String,
    val points: List<Point>,
    val color: Int,
    val width: Double,
    val opacity: Double,
    val dash: List<Double>?,
  )

  private fun parseNavigationLine(value: Map<String, Any>): NavigationLinePayload? {
    val id = (value["id"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val points = (value["coordinates"] as? List<*>).orEmpty().mapNotNull { raw ->
      val point = raw as? Map<*, *> ?: return@mapNotNull null
      val latitude = (point["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      val longitude = (point["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
      if (!latitude.isFinite() || !longitude.isFinite()) return@mapNotNull null
      Point.fromLngLat(longitude, latitude)
    }
    val width = (value["width"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0 } ?: 4.0
    val opacity = ((value["opacity"] as? Number)?.toDouble() ?: 1.0).coerceIn(0.0, 1.0)
    val dash = (value["dash"] as? List<*>).orEmpty()
      .mapNotNull { (it as? Number)?.toDouble()?.takeIf { d -> d.isFinite() && d >= 0 } }
      .takeIf { it.size >= 2 }
    return NavigationLinePayload(
      id = id,
      points = points,
      color = HexColor.parse(value["color"]) ?: 0xFF2563EB.toInt(),
      width = width,
      opacity = opacity,
      dash = dash,
    )
  }

  private fun renderNavigationLinesIfPossible() {
    val mapView = attachedMapView ?: return
    mapView.mapboxMap.getStyle { style -> renderNavigationLines(style) }
  }

  private fun renderNavigationLines(style: Style) {
    val payloads = navigationLines.orEmpty().mapNotNull(::parseNavigationLine).filter { it.points.size >= 2 }
    val nextIds = payloads.mapTo(linkedSetOf()) { it.id }
    for (id in renderedNavigationLineIds.toList()) {
      if (id in nextIds) continue
      runCatching { style.removeStyleLayer(navigationLineLayerId(id)) }
      runCatching { style.removeStyleSource(navigationLineSourceId(id)) }
      renderedNavigationLineIds.remove(id)
    }
    for (line in payloads) {
      val sourceId = navigationLineSourceId(line.id)
      val layerId = navigationLineLayerId(line.id)
      val feature = Feature.fromGeometry(LineString.fromLngLats(line.points))
      val existing = style.getSourceAs<GeoJsonSource>(sourceId)
      if (existing != null) {
        existing.feature(feature)
      } else if (runCatching { style.addSource(geoJsonSource(sourceId) { feature(feature) }) }.isFailure) {
        continue
      }
      val existingLayer = style.getLayerAs<LineLayer>(layerId)
      if (existingLayer != null) {
        runCatching {
          existingLayer.lineColor(line.color)
          existingLayer.lineWidth(line.width)
          existingLayer.lineOpacity(line.opacity)
          existingLayer.lineDasharray(line.dash ?: emptyList())
        }
      } else {
        runCatching {
          style.addLayer(
            lineLayer(layerId, sourceId) {
              lineColor(line.color)
              lineWidth(line.width)
              lineOpacity(line.opacity)
              lineCap(LineCap.ROUND)
              lineJoin(LineJoin.ROUND)
              line.dash?.let { lineDasharray(it) }
            }
          )
        }
      }
      renderedNavigationLineIds.add(line.id)
    }
    if (payloads.isNotEmpty()) armLayerOrderGuard()
  }

  private fun navigationLineSourceId(id: String) = "rnmbn-line-$id"

  private fun navigationLineLayerId(id: String) = "rnmbn-line-$id-layer"

  private fun renderNavigationMarkersIfPossible() {
    val mapView = attachedMapView ?: return
    val markerPayloads = navigationMarkers.orEmpty().mapNotNull(::parseNavigationMarker)
    val nextIds = markerPayloads.mapTo(linkedSetOf()) { it.id }
    val annotationManager = mapView.viewAnnotationManager

    navigationMarkerViews.entries.toList().forEach { (markerId, markerView) ->
      if (!nextIds.contains(markerId)) {
        runCatching { annotationManager.removeViewAnnotation(markerView) }
        navigationMarkerViews.remove(markerId)
        forgetMarkerMotion(markerId)
      }
    }

    markerPayloads.forEach { marker ->
      val picture = marker.image?.let { image -> markerBitmap(image)?.let { image to it } }
      navigationMarkerViews[marker.id]?.let { current ->
        if ((current is android.widget.ImageView) != (picture != null)) {
          runCatching { annotationManager.removeViewAnnotation(current) }
          navigationMarkerViews.remove(marker.id)
        }
      }
      val existingView = navigationMarkerViews[marker.id]
      val markerView = existingView
        ?: picture?.let { (image, bitmap) -> createImageMarkerView(marker, image, bitmap) }
        ?: createNavigationMarkerView(marker)
      if (picture != null) {
        val (image, bitmap) = picture
        val imageView = markerView as android.widget.ImageView
        if (imageView.tag != image.uri) {
          imageView.setImageBitmap(bitmap)
          imageView.tag = image.uri
        }
      } else {
        bindNavigationMarkerView(markerView, marker)
      }
      val metrics = resolveNavigationMarkerMetrics(marker.size)
      val viewOptions = viewAnnotationOptions {
        annotatedFeature(AnnotatedFeature.valueOf(marker.point))
        allowOverlap(marker.allowOverlap)
        visible(true)
        ignoreCameraPadding(true)
        allowOverlapWithPuck(true)
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
          markerShownAt[marker.id] = marker.point
          markerUpdatedAt[marker.id] = android.os.SystemClock.uptimeMillis()
        }.onFailure { throwable ->
          Log.w(TAG, "Failed to add navigation marker '${marker.id}'", throwable)
        }
      } else {
        val shown = markerShownAt[marker.id] ?: marker.point
        runCatching {
          if (!annotationManager.updateViewAnnotation(existingView, viewOptions)) {
            annotationManager.removeViewAnnotation(existingView)
            annotationManager.addViewAnnotation(existingView, viewOptions)
          }
        }.onFailure { throwable ->
          Log.w(TAG, "Failed to update navigation marker '${marker.id}'", throwable)
        }
        glideMarker(marker.id, existingView, shown, marker.point, marker.animatesMovement)
      }
    }
  }

  private fun glideMarker(id: String, view: View, from: Point, to: Point, animates: Boolean) {
    val manager = attachedMapView?.viewAnnotationManager ?: return
    markerAnimators.remove(id)?.cancel()
    val now = android.os.SystemClock.uptimeMillis()
    val since = now - (markerUpdatedAt[id] ?: now)
    markerUpdatedAt[id] = now
    val place = { point: Point ->
      markerShownAt[id] = point
      runCatching {
        manager.updateViewAnnotation(
          view,
          viewAnnotationOptions { annotatedFeature(AnnotatedFeature.valueOf(point)) }
        )
      }
    }
    val distance = FloatArray(1)
    android.location.Location.distanceBetween(
      from.latitude(), from.longitude(), to.latitude(), to.longitude(), distance
    )
    if (!animates || distance[0] < 0.3f || distance[0] > MARKER_GLIDE_MAX_DISTANCE_M || since <= 0L) {
      place(to)
      return
    }
    place(from)
    val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
      duration = since.coerceIn(MARKER_GLIDE_MIN_MS, MARKER_GLIDE_MAX_MS)
      interpolator = android.view.animation.LinearInterpolator()
      addUpdateListener { animation ->
        val t = (animation.animatedValue as Float).toDouble()
        place(
          Point.fromLngLat(
            from.longitude() + (to.longitude() - from.longitude()) * t,
            from.latitude() + (to.latitude() - from.latitude()) * t
          )
        )
      }
    }
    markerAnimators[id] = animator
    animator.start()
  }

  private fun forgetMarkerMotion(id: String) {
    markerAnimators.remove(id)?.cancel()
    markerShownAt.remove(id)
    markerUpdatedAt.remove(id)
  }

  private fun clearNavigationMarkers(mapView: MapView) {
    val annotationManager = mapView.viewAnnotationManager
    navigationMarkerViews.values.forEach { markerView ->
      runCatching { annotationManager.removeViewAnnotation(markerView) }
    }
    navigationMarkerViews.keys.toList().forEach(::forgetMarkerMotion)
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
    val variant = NavigationMarkerOptions.variant(value["variant"] as? String)
    val customColor = HexColor.parse(value["color"])
    val customBadgeColor = HexColor.parse(value["badgeColor"])
    val customOpacity = (value["opacity"] as? Number)?.toFloat()?.coerceIn(0f, 1f)
    val size = NavigationMarkerOptions.size(value["size"] as? String)
    val markerStyle = NavigationMarkerOptions.style(value["markerStyle"] as? String)
    val showTail = (value["showTail"] as? Boolean) ?: (markerStyle == "pin")
    val selected = (value["selected"] as? Boolean) ?: (variant == "primary" || variant == "success")
    val allowOverlap = (value["allowOverlap"] as? Boolean) ?: true
    val anchorOffsetY = (value["anchorOffsetY"] as? Number)?.toInt()
    val image = (value["image"] as? Map<*, *>)?.let { raw ->
      val uri = (raw["uri"] as? String)?.takeIf { it.isNotBlank() }
      val scale = (raw["scale"] as? Number)?.toFloat() ?: resources.displayMetrics.density
      uri?.let { NavigationMarkerImage(it, scale.coerceAtLeast(1f)) }
    }

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
      animatesMovement = (value["animatesMovement"] as? Boolean) ?: false,
      image = image,
    )
  }

  private fun markerBitmap(image: NavigationMarkerImage): android.graphics.Bitmap? {
    MarkerImageLoader.cached(image.uri)?.let { return it }
    MarkerImageLoader.load(context, image.uri) { renderNavigationMarkersIfPossible() }
    return null
  }

  private fun createImageMarkerView(
    marker: NavigationMarkerPayload,
    image: NavigationMarkerImage,
    bitmap: android.graphics.Bitmap
  ): View {
    val density = resources.displayMetrics.density
    return android.widget.ImageView(context).apply {
      setImageBitmap(bitmap)
      scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
      contentDescription = marker.label ?: marker.id
      tag = image.uri
      layoutParams = ViewGroup.LayoutParams(
        (bitmap.width / image.scale * density).toInt(),
        (bitmap.height / image.scale * density).toInt()
      )
    }
  }

  private fun createNavigationMarkerView(marker: NavigationMarkerPayload): View {
    val markerRoot = LinearLayout(context).apply {
      layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
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
    if (isCameraFollowing == next && reason != "guidance-started") return
    isCameraFollowing = next
    dispatchCameraFollowingStateChange(
      mapOf(
        "isCameraFollowing" to next,
        "isCameraNotFollowing" to !next,
        "reason" to reason
      )
    )
  }

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

  private fun applyLocationPuck() {
    val mapView = attachedMapView ?: return
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
      if (locationPuckStates !== requested || attachedMapView !== mapView) return@buildOptions
      runCatching {
        mapView.location.locationPuck = options.activeNavigationPuck
        appliedLocationPuckStates = requested
      }.onFailure { throwable ->
        Log.w(TAG, "Failed to apply custom location puck", throwable)
      }
    }
  }

  private fun applyUiVisibility() {
    maneuverView?.visibility = if (showsManeuverView) View.VISIBLE else View.GONE
    soundButton?.visibility = if (showsActionButtons) View.VISIBLE else View.GONE

    if (warnedUnsupportedChrome) return
    val unsupported = buildList {
      if (!showsWayNameLabel) add("showsWayNameLabel")
      if (!showsReportFeedback) add("showsReportFeedback")
      if (showsTripProgress) add("showsTripProgress (use the bottomSheet overlay instead)")
      if (showsSpeedLimits) add("showsSpeedLimits")
      if (annotatesIntersectionsAlongRoute) add("annotatesIntersectionsAlongRoute")
      if (!usesNightStyleWhileInTunnel) add("usesNightStyleWhileInTunnel")
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

  private fun initialCameraOptions(): CameraOptions? {
    val lat = lastKnownLatitude ?: startOrigin.toAnyPointOrNull()?.latitude()
    val lng = lastKnownLongitude ?: startOrigin.toAnyPointOrNull()?.longitude()
    if (lat == null || lng == null) return null
    return CameraOptions.Builder().center(Point.fromLngLat(lng, lat)).zoom(15.0).build()
  }

  private fun applyCameraPadding() {
    val dataSource = viewportDataSource ?: return
    val padding = cameraPadding ?: return
    val density = context.resources.displayMetrics.density.toDouble()
    fun side(key: String) = ((padding[key] as? Number)?.toDouble() ?: 0.0).coerceAtLeast(0.0) * density
    val insets = EdgeInsets(side("top"), side("left"), side("bottom"), side("right"))
    runCatching {
      dataSource.followingPadding = insets
      dataSource.overviewPadding = insets
      dataSource.evaluate()
    }.onFailure { throwable -> Log.w(TAG, "Failed to apply camera padding", throwable) }
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
    if (!journeyDataEnabled) return
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
    val liveDistance =
      lastJourneyProgress?.currentLegProgress?.currentStepProgress?.distanceRemaining?.toDouble()
    payload["stepDistanceRemaining"] = liveDistance ?: instruction?.distanceAlongGeometry() ?: 0.0
    instruction?.primary()?.type()?.takeIf { it.isNotBlank() }?.let { payload["maneuverType"] = it }
    instruction?.primary()?.modifier()?.takeIf { it.isNotBlank() }?.let {
      payload["maneuverModifier"] = it
    }
    instruction?.primary()?.degrees()?.let { payload["maneuverDegrees"] = it }

    val (maneuverStep, followingStep) = maneuverStepsFor(instruction)
    val drivingSide = (instruction?.primary()?.drivingSide() ?: maneuverStep?.drivingSide())
      ?.trim()?.lowercase()?.takeIf { it == "left" || it == "right" }
    drivingSide?.let { payload["drivingSide"] = it }
    val exitNumber = maneuverStep?.maneuver()?.exit()?.takeIf { it > 0 }
    exitNumber?.let { payload["exitNumber"] = it }
    val lanes = lanesOf(instruction)
    lanes?.let { payload["lanes"] = it }
    val nextManeuver = nextManeuverOf(maneuverStep, followingStep)
    nextManeuver?.let { payload["nextManeuver"] = it }

    val signature = listOf(
      primary,
      secondary,
      instruction?.primary()?.type().orEmpty(),
      instruction?.primary()?.modifier().orEmpty(),
      drivingSide.orEmpty(),
      exitNumber?.toString().orEmpty(),
      lanes?.toString().orEmpty(),
      nextManeuver?.toString().orEmpty()
    ).joinToString("|")
    val distance = (payload["stepDistanceRemaining"] as? Number)?.toDouble() ?: Double.NaN
    val now = nowMs()
    if (signature == lastBannerSignature) {
      val moved = !lastBannerDistance.isFinite() || !distance.isFinite() ||
        kotlin.math.abs(distance - lastBannerDistance) >= BANNER_DISTANCE_STEP_M
      if (!moved || now - lastBannerEmitAtMs < BANNER_DISTANCE_MIN_GAP_MS) return
    }
    lastBannerSignature = signature
    lastBannerDistance = distance
    lastBannerEmitAtMs = now
    dispatchBannerInstruction(payload)
  }

  private fun maneuverStepsFor(instruction: BannerInstructions?): Pair<LegStep?, LegStep?> {
    if (instruction == null) return null to null
    val legProgress = lastJourneyProgress?.currentLegProgress ?: return null to null
    val steps = legProgress.routeLeg?.steps() ?: return null to null
    val currentIndex = legProgress.currentStepProgress?.stepIndex
    val bannerStepIndex =
      if (currentIndex != null &&
        steps.getOrNull(currentIndex)?.bannerInstructions()?.contains(instruction) == true
      ) {
        currentIndex
      } else {
        steps.indexOfFirst { it.bannerInstructions()?.contains(instruction) == true }
          .takeIf { it >= 0 }
          ?: currentIndex
          ?: return null to null
      }
    return steps.getOrNull(bannerStepIndex + 1) to steps.getOrNull(bannerStepIndex + 2)
  }

  private fun lanesOf(instruction: BannerInstructions?): List<Map<String, Any>>? {
    val components = instruction?.sub()?.components() ?: return null
    val lanes = components
      .filter { it.type() == BannerComponents.LANE }
      .map { lane ->
        val payload = mutableMapOf<String, Any>(
          "indications" to (lane.directions() ?: emptyList<String>()),
          "active" to (lane.active() ?: false)
        )
        lane.activeDirection()?.takeIf { it.isNotBlank() }?.let { payload["activeDirection"] = it }
        payload
      }
    return lanes.takeIf { it.isNotEmpty() }
  }

  private fun nextManeuverOf(maneuverStep: LegStep?, followingStep: LegStep?): Map<String, Any>? {
    if (maneuverStep == null || followingStep == null) return null
    val banner = maneuverStep.bannerInstructions()?.firstOrNull()?.primary()
    val text = banner?.text()?.trim()?.takeIf { it.isNotEmpty() }
      ?: followingStep.maneuver().instruction()?.trim()?.takeIf { it.isNotEmpty() }
      ?: return null
    val payload = mutableMapOf<String, Any>(
      "primaryText" to text,
      "distance" to maneuverStep.distance()
    )
    (banner?.type() ?: followingStep.maneuver().type())
      ?.takeIf { it.isNotBlank() }?.let { payload["maneuverType"] = it }
    (banner?.modifier() ?: followingStep.maneuver().modifier())
      ?.takeIf { it.isNotBlank() }?.let { payload["maneuverModifier"] = it }
    banner?.degrees()?.let { payload["maneuverDegrees"] = it }
    return payload
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
    secondary?.let { payload["secondaryInstruction"] = it }
    lastRoadName?.let { payload["currentStreet"] = it }
    (progress?.currentLegProgress?.currentStepProgress?.distanceRemaining?.toDouble()
      ?: banner?.distanceAlongGeometry())
      ?.let { payload["stepDistanceRemaining"] = it }

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
    if (geometry == lastEmittedRouteSignature) return
    lastEmittedRouteSignature = geometry
    val points = runCatching { PolylineUtils.decode(geometry, 6) }.getOrElse { return }
    if (points.isEmpty()) return
    val coords = points.map { p ->
      mapOf(
        "latitude" to p.latitude(),
        "longitude" to p.longitude()
      )
    }
    dispatchRouteChange(mapOf("coordinates" to coords, "routeId" to route.id))
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

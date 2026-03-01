package expo.modules.mapboxnavigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mapbox.api.directions.v5.models.BannerInstructions
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
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
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import java.lang.ref.WeakReference

class MapboxNavigationActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "MapboxNavigationActivity"
    private const val SESSION_OWNER = "fullscreen"
    @Volatile
    private var activeSessionCount: Int = 0
    @Volatile
    private var activeActivityRef: WeakReference<MapboxNavigationActivity>? = null
    @Volatile
    private var stopRequested: Boolean = false

    fun hasActiveSession(): Boolean {
      return activeSessionCount > 0 && activeActivityRef?.get() != null
    }

    fun finishActiveSession() {
      stopRequested = true
      val activity = activeActivityRef?.get() ?: return
      activity.runOnUiThread {
        if (!activity.isFinishing && !activity.isDestroyed) {
          activity.finish()
        }
      }
    }
  }

  private var navigationView: NavigationView? = null
  private lateinit var accessToken: String
  private var startPoint: Point? = null
  private var destinationPoint: Point? = null
  private var waypointPoints: List<Point> = emptyList()
  private var shouldSimulateRoute: Boolean = false
  private var routeAlternatives: Boolean = false
  private var mute: Boolean = false
  private var voiceVolume: Double = 1.0
  private var language: String = "en"
  private var showsSpeedLimits: Boolean = true
  private var showsWayNameLabel: Boolean = true
  private var showsTripProgress: Boolean = true
  private var showsManeuverView: Boolean = true
  private var showsActionButtons: Boolean = true
  private var showsReportFeedback: Boolean = true
  private var showsEndOfRouteFeedback: Boolean = true
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
  private var mapStyleUriDay: String? = null
  private var mapStyleUriNight: String? = null
  private var uiTheme: String = "system"
  private var bottomSheetMode: String = "native"
  private var rootContainer: FrameLayout? = null
  private var customNativeBottomSheetContainer: LinearLayout? = null
  private var customNativePrimaryTextView: TextView? = null
  private var customNativeSecondaryTextView: TextView? = null
  private var customNativeExpanded = false
  private var customNativeHidden = false
  private var customNativeRevealOnNativeBannerGesture = true
  private var customNativeCollapsedHeightPx = 0
  private var customNativeExpandedHeightPx = 0
  private var customNativeHiddenHeightPx = 0
  private var customNativeEnableTapToToggle = true
  private var customNativeShowHandle = true
  private var customNativeHeaderTitle: String? = null
  private var customNativeHeaderSubtitle: String? = null
  private var customNativeHeaderBadgeText: String? = null
  private var customNativeHeaderBadgeBackgroundColor: String? = null
  private var customNativeHeaderBadgeTextColor: String? = null
  private var customNativeHeaderTitleFontSize: Double? = null
  private var customNativeHeaderTitleFontFamily: String? = null
  private var customNativeHeaderTitleFontWeight: String? = null
  private var customNativeHeaderSubtitleFontSize: Double? = null
  private var customNativeHeaderSubtitleFontFamily: String? = null
  private var customNativeHeaderSubtitleFontWeight: String? = null
  private var customNativeHeaderBadgeFontSize: Double? = null
  private var customNativeHeaderBadgeFontFamily: String? = null
  private var customNativeHeaderBadgeFontWeight: String? = null
  private var customNativeHeaderBadgeCornerRadius: Double? = null
  private var customNativeHeaderBadgeBorderColor: String? = null
  private var customNativeHeaderBadgeBorderWidth: Double? = null
  private var customNativeQuickActionIds: Array<String> = emptyArray()
  private var customNativeQuickActionLabels: Array<String> = emptyArray()
  private var customNativeQuickActionVariants: Array<String> = emptyArray()
  private var customNativeRowIds: Array<String> = emptyArray()
  private var customNativeRowTitles: Array<String> = emptyArray()
  private var customNativeRowValues: Array<String> = emptyArray()
  private var customNativeRowSubtitles: Array<String> = emptyArray()
  private var customNativeRowIconSystemNames: Array<String> = emptyArray()
  private var customNativeRowIconTexts: Array<String> = emptyArray()
  private var customNativeRowEmphasis: BooleanArray = BooleanArray(0)
  private var customNativeBackgroundColor: String? = null
  private var customNativePrimaryTextColor: String? = null
  private var customNativeSecondaryTextColor: String? = null
  private var customNativeActionButtonBackgroundColor: String? = null
  private var customNativeActionButtonTextColor: String? = null
  private var customNativeSecondaryActionButtonBackgroundColor: String? = null
  private var customNativeSecondaryActionButtonTextColor: String? = null
  private var customNativeActionButtonBorderColor: String? = null
  private var customNativeActionButtonBorderWidth: Double? = null
  private var customNativeActionButtonCornerRadius: Double? = null
  private var customNativeCornerRadiusPx: Float = 0f
  private var customNativePrimaryTextFontSize: Double? = null
  private var customNativePrimaryTextFontFamily: String? = null
  private var customNativePrimaryTextFontWeight: String? = null
  private var customNativeSecondaryTextFontSize: Double? = null
  private var customNativeSecondaryTextFontFamily: String? = null
  private var customNativeSecondaryTextFontWeight: String? = null
  private var customNativeActionButtonFontSize: Double? = null
  private var customNativeActionButtonFontFamily: String? = null
  private var customNativeActionButtonFontWeight: String? = null
  private var customNativeActionButtonHeight: Double? = null
  private var customNativeActionButtonsBottomPaddingPx: Int = 0
  private var customNativeQuickActionBackgroundColor: String? = null
  private var customNativeQuickActionTextColor: String? = null
  private var customNativeQuickActionSecondaryBackgroundColor: String? = null
  private var customNativeQuickActionSecondaryTextColor: String? = null
  private var customNativeQuickActionGhostTextColor: String? = null
  private var customNativeQuickActionBorderColor: String? = null
  private var customNativeQuickActionBorderWidth: Double? = null
  private var customNativeQuickActionCornerRadius: Double? = null
  private var customNativeQuickActionFontFamily: String? = null
  private var customNativeQuickActionFontWeight: String? = null
  private var customNativeShowCurrentStreet: Boolean = true
  private var customNativeShowRemainingDistance: Boolean = true
  private var customNativeShowRemainingDuration: Boolean = true
  private var customNativeShowEta: Boolean = true
  private var customNativeShowCompletionPercent: Boolean = true
  private var customNativeActionButtonTitle: String? = null
  private var customNativeSecondaryActionButtonTitle: String? = null
  private var customNativeAttachPending: Boolean = false
  private var hasStartedGuidance: Boolean = false
  private var hasEmittedArrival: Boolean = false
  private var destinationName: String = "Destination"
  private var customNativeHandleTouchStartY = 0f
  private var nativeBannerGestureTouchStartY = 0f
  private var nativeBannerGestureTouchStartX = 0f
  private var nativeBannerGestureArmed = false
  private var customNativeRevealHotzoneView: View? = null
  private var customNativeBackdropView: View? = null
  private var customNativeRevealGestureHotzoneHeightPx: Int = 0
  private var customNativeRevealGestureRightExclusionWidthPx: Int = 0
  private var latestLatitude: Double? = null
  private var latestLongitude: Double? = null
  private var latestBearing: Double? = null
  private var latestSpeed: Double? = null
  private var latestAltitude: Double? = null
  private var latestAccuracy: Double? = null
  private var latestPrimaryInstruction: String? = null
  private var latestSecondaryInstruction: String? = null
  private var latestStepDistanceRemaining: Double? = null
  private var latestDistanceRemaining: Double? = null
  private var latestDurationRemaining: Double? = null
  private var latestFractionTraveled: Double? = null
  private var mapboxNavigation: MapboxNavigation? = null
  private val mainHandler = Handler(Looper.getMainLooper())
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
      MapboxNavigationEventBridge.emit(
        "onLocationChange",
        mapOf(
          "latitude" to location.latitude,
          "longitude" to location.longitude,
          "bearing" to location.bearing.toDouble(),
          "speed" to location.speed.toDouble(),
          "altitude" to location.altitude,
          "accuracy" to location.accuracy.toDouble()
        )
      )
      emitJourneyData()
    }
  }
  private val routeProgressObserver = RouteProgressObserver { routeProgress: RouteProgress ->
    MapboxNavigationEventBridge.emit(
      "onRouteProgressChange",
      mapOf(
        "distanceTraveled" to routeProgress.distanceTraveled.toDouble(),
        "distanceRemaining" to routeProgress.distanceRemaining.toDouble(),
        "durationRemaining" to routeProgress.durationRemaining,
        "fractionTraveled" to routeProgress.fractionTraveled.toDouble()
      )
    )
    latestDistanceRemaining = routeProgress.distanceRemaining.toDouble()
    latestDurationRemaining = routeProgress.durationRemaining
    latestFractionTraveled = routeProgress.fractionTraveled.toDouble().coerceIn(0.0, 1.0)

    if (!hasEmittedArrival && routeProgress.distanceRemaining <= 5.0) {
      hasEmittedArrival = true
      MapboxNavigationEventBridge.emit(
        "onArrive",
        mapOf("name" to destinationName)
      )
    }

    emitBannerInstruction(routeProgress.bannerInstructions)
    if ((bottomSheetMode == "customnative" || bottomSheetMode == "overlay") &&
      customNativeBottomSheetContainer == null &&
      !hasStartedGuidance
    ) {
      val looksLikeActiveGuidance =
        routeProgress.distanceTraveled > 1.0 || routeProgress.fractionTraveled > 0.0f
      if (looksLikeActiveGuidance) {
        hasStartedGuidance = true
        attachCustomNativeBottomSheetIfNeeded()
      }
    }
    updateCustomNativeBottomSheet(
      instruction = routeProgress.bannerInstructions?.primary()?.text(),
      progress = routeProgress
    )
    emitJourneyData()
  }
  private val bannerInstructionsObserver = BannerInstructionsObserver { bannerInstructions ->
    emitBannerInstruction(bannerInstructions)
  }
  private val locationPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { result ->
    val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

    if (!granted) {
      showErrorScreen("Location permission is required to start navigation.", null)
      return@registerForActivityResult
    }

    createNavigationViewIfNeeded()
  }
  private val navigationViewListener = object : NavigationViewListener() {
    override fun onDestinationChanged(destination: Point?) {
      Log.d(TAG, "Destination changed: $destination")
      destination?.let { point ->
        MapboxNavigationEventBridge.emit(
          "onDestinationChanged",
          mapOf(
            "latitude" to point.latitude(),
            "longitude" to point.longitude()
          )
        )
      }
    }

    override fun onDestinationPreview() {
      Log.d(TAG, "NavigationView entered destination preview")
      MapboxNavigationEventBridge.emit(
        "onDestinationPreview",
        mapOf("active" to true)
      )
    }

    override fun onRouteFetchFailed(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
      Log.e(TAG, "Route fetch failed. reasons=${reasons.size}, options=$routeOptions")
      val (code, message) = mapRouteFetchFailure(reasons)
      MapboxNavigationEventBridge.emit(
        "onError",
        mapOf(
          "code" to code,
          "message" to message
        )
      )
      showErrorScreen(message, null)
    }

    override fun onRouteFetchSuccessful(routes: List<NavigationRoute>) {
      Log.d(TAG, "Route fetch succeeded. routeCount=${routes.size}")

      if (!shouldSimulateRoute || routes.isEmpty() || hasStartedGuidance) {
        return
      }

      hasStartedGuidance = true
      navigationView?.api?.startActiveGuidance(routes)
      attachCustomNativeBottomSheetIfNeeded()
    }

    override fun onRouteFetchCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
      Log.w(TAG, "Route fetch canceled. origin=$routerOrigin, options=$routeOptions")
      MapboxNavigationEventBridge.emit(
        "onError",
        mapOf(
          "code" to "ROUTE_FETCH_CANCELED",
          "message" to "Route fetch canceled (origin: $routerOrigin)."
        )
      )
    }

    override fun onActiveNavigation() {
      hasStartedGuidance = true
      attachCustomNativeBottomSheetIfNeeded()
    }

    override fun onFreeDrive() {
      if (hasStartedGuidance && !isFinishing && !isDestroyed) {
        finish()
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (!NavigationSessionRegistry.acquire(SESSION_OWNER)) {
      showErrorScreen(
        "Another navigation session is already active. Stop embedded/full-screen navigation before starting a new one.",
        null
      )
      finish()
      return
    }
    activeSessionCount += 1
    activeActivityRef = WeakReference(this)

    try {
      accessToken = resolveAccessToken()
      val originLat = intent.getDoubleExtraOrNull("originLat")
      val originLng = intent.getDoubleExtraOrNull("originLng")
      val destinationLat = intent.getDoubleExtraOrNull("destLat")
      val destinationLng = intent.getDoubleExtraOrNull("destLng")
      destinationName = intent.getStringExtra("destinationName")?.trim()?.takeIf { it.isNotEmpty() }
        ?: "Destination"
      shouldSimulateRoute = intent.getBooleanExtra("shouldSimulate", false)
      mute = intent.getBooleanExtra("mute", false)
      voiceVolume = intent.getDoubleExtra("voiceVolume", 1.0)
      language = intent.getStringExtra("language")?.trim()?.takeIf { it.isNotEmpty() } ?: "en"
      routeAlternatives = intent.getBooleanExtra("routeAlternatives", false)
      showsSpeedLimits = intent.getBooleanExtra("showsSpeedLimits", true)
      showsWayNameLabel = intent.getBooleanExtra("showsWayNameLabel", true)
      showsTripProgress = intent.getBooleanExtra("showsTripProgress", true)
      showsManeuverView = intent.getBooleanExtra("showsManeuverView", true)
      showsActionButtons = intent.getBooleanExtra("showsActionButtons", true)
      showsReportFeedback = intent.getBooleanExtra("showsReportFeedback", true)
      showsEndOfRouteFeedback = intent.getBooleanExtra("showsEndOfRouteFeedback", true)
      showEmergencyCallButton = intent.getBooleanExtraOrNull("showEmergencyCallButton")
      showCancelRouteButton = intent.getBooleanExtraOrNull("showCancelRouteButton")
      showRefreshRouteButton = intent.getBooleanExtraOrNull("showRefreshRouteButton")
      showReportFeedbackButton = intent.getBooleanExtraOrNull("showReportFeedbackButton")
      showToggleAudioButton = intent.getBooleanExtraOrNull("showToggleAudioButton")
      showSearchAlongRouteButton = intent.getBooleanExtraOrNull("showSearchAlongRouteButton")
      showStartNavigationButton = intent.getBooleanExtraOrNull("showStartNavigationButton")
      showEndNavigationButton = intent.getBooleanExtraOrNull("showEndNavigationButton")
      showAlternativeRoutesButton = intent.getBooleanExtraOrNull("showAlternativeRoutesButton")
      showStartNavigationFeedbackButton = intent.getBooleanExtraOrNull("showStartNavigationFeedbackButton")
      showEndNavigationFeedbackButton = intent.getBooleanExtraOrNull("showEndNavigationFeedbackButton")
      logUnsupportedActionButtonOptionsIfRequested()
      mapStyleUriDay = intent.getStringExtra("mapStyleUriDay")?.trim()?.takeIf { it.isNotEmpty() }
      mapStyleUriNight = intent.getStringExtra("mapStyleUriNight")?.trim()?.takeIf { it.isNotEmpty() }
      uiTheme = intent.getStringExtra("uiTheme")?.trim()?.lowercase() ?: "system"
      bottomSheetMode = intent.getStringExtra("bottomSheetMode")?.trim()?.lowercase() ?: "native"
      val initialBottomSheetState = intent.getStringExtra("bottomSheetInitialState")?.trim()?.lowercase()
      customNativeExpanded = initialBottomSheetState == "expanded"
      customNativeHidden = initialBottomSheetState == "hidden"
      customNativeRevealOnNativeBannerGesture = intent.getBooleanExtra("bottomSheetRevealOnNativeBannerGesture", true)
      customNativeEnableTapToToggle = intent.getBooleanExtra("bottomSheetEnableTapToToggle", true)
      customNativeShowHandle = intent.getBooleanExtra("bottomSheetShowHandle", true)
      customNativeRevealGestureHotzoneHeightPx = dpToPx(
        (intent.getDoubleExtra("bottomSheetRevealGestureHotzoneHeight", 100.0)).toFloat()
      ).coerceIn(dpToPx(56f), dpToPx(220f))
      customNativeRevealGestureRightExclusionWidthPx = dpToPx(
        (intent.getDoubleExtra("bottomSheetRevealGestureRightExclusionWidth", 80.0)).toFloat()
      ).coerceIn(dpToPx(48f), dpToPx(220f))
      customNativeCornerRadiusPx = dpToPx(
        (intent.getDoubleExtra("bottomSheetCornerRadius", 16.0)).toFloat()
      ).toFloat().coerceIn(0f, dpToPx(28f).toFloat())
      customNativeHiddenHeightPx = 0
      customNativeCollapsedHeightPx = dpToPx(
        (intent.getDoubleExtra("bottomSheetCollapsedHeight", 120.0)).toFloat()
      )
      customNativeExpandedHeightPx = dpToPx(
        (intent.getDoubleExtra("bottomSheetExpandedHeight", 340.0)).toFloat()
      )
      if (customNativeExpandedHeightPx < customNativeCollapsedHeightPx) {
        customNativeExpandedHeightPx = customNativeCollapsedHeightPx
      }
      if (
        customNativeRevealOnNativeBannerGesture &&
        (bottomSheetMode == "customnative" || bottomSheetMode == "overlay")
      ) {
        customNativeHidden = true
        customNativeExpanded = false
      }
      customNativeHeaderTitle = intent.getStringExtra("bottomSheetHeaderTitle")
      customNativeHeaderTitleFontSize = intent.getDoubleExtraOrNull("bottomSheetHeaderTitleFontSize")
      customNativeHeaderTitleFontFamily = intent.getStringExtra("bottomSheetHeaderTitleFontFamily")
      customNativeHeaderTitleFontWeight = intent.getStringExtra("bottomSheetHeaderTitleFontWeight")
      customNativeHeaderSubtitle = intent.getStringExtra("bottomSheetHeaderSubtitle")
      customNativeHeaderSubtitleFontSize = intent.getDoubleExtraOrNull("bottomSheetHeaderSubtitleFontSize")
      customNativeHeaderSubtitleFontFamily = intent.getStringExtra("bottomSheetHeaderSubtitleFontFamily")
      customNativeHeaderSubtitleFontWeight = intent.getStringExtra("bottomSheetHeaderSubtitleFontWeight")
      customNativeHeaderBadgeText = intent.getStringExtra("bottomSheetHeaderBadgeText")
      customNativeHeaderBadgeFontSize = intent.getDoubleExtraOrNull("bottomSheetHeaderBadgeFontSize")
      customNativeHeaderBadgeFontFamily = intent.getStringExtra("bottomSheetHeaderBadgeFontFamily")
      customNativeHeaderBadgeFontWeight = intent.getStringExtra("bottomSheetHeaderBadgeFontWeight")
      customNativeHeaderBadgeBackgroundColor = intent.getStringExtra("bottomSheetHeaderBadgeBackgroundColor")
      customNativeHeaderBadgeTextColor = intent.getStringExtra("bottomSheetHeaderBadgeTextColor")
      customNativeHeaderBadgeCornerRadius = intent.getDoubleExtraOrNull("bottomSheetHeaderBadgeCornerRadius")
      customNativeHeaderBadgeBorderColor = intent.getStringExtra("bottomSheetHeaderBadgeBorderColor")
      customNativeHeaderBadgeBorderWidth = intent.getDoubleExtraOrNull("bottomSheetHeaderBadgeBorderWidth")
      customNativeQuickActionIds = intent.getStringArrayExtra("bottomSheetQuickActionIds") ?: emptyArray()
      customNativeQuickActionLabels = intent.getStringArrayExtra("bottomSheetQuickActionLabels") ?: emptyArray()
      customNativeQuickActionVariants = intent.getStringArrayExtra("bottomSheetQuickActionVariants") ?: emptyArray()
      customNativeRowIds = intent.getStringArrayExtra("bottomSheetCustomRowIds") ?: emptyArray()
      customNativeRowTitles = intent.getStringArrayExtra("bottomSheetCustomRowTitles") ?: emptyArray()
      customNativeRowValues = intent.getStringArrayExtra("bottomSheetCustomRowValues") ?: emptyArray()
      customNativeRowSubtitles = intent.getStringArrayExtra("bottomSheetCustomRowSubtitles") ?: emptyArray()
      customNativeRowIconSystemNames = intent.getStringArrayExtra("bottomSheetCustomRowIconSystemNames") ?: emptyArray()
      customNativeRowIconTexts = intent.getStringArrayExtra("bottomSheetCustomRowIconTexts") ?: emptyArray()
      customNativeRowEmphasis = intent.getBooleanArrayExtra("bottomSheetCustomRowEmphasis") ?: BooleanArray(0)
      customNativeBackgroundColor = intent.getStringExtra("bottomSheetBackgroundColor")
      customNativePrimaryTextColor = intent.getStringExtra("bottomSheetPrimaryTextColor")
      customNativeSecondaryTextColor = intent.getStringExtra("bottomSheetSecondaryTextColor")
      customNativeActionButtonBackgroundColor = intent.getStringExtra("bottomSheetActionButtonBackgroundColor")
      customNativeActionButtonTextColor = intent.getStringExtra("bottomSheetActionButtonTextColor")
      customNativeSecondaryActionButtonBackgroundColor = intent.getStringExtra("bottomSheetSecondaryActionButtonBackgroundColor")
      customNativeSecondaryActionButtonTextColor = intent.getStringExtra("bottomSheetSecondaryActionButtonTextColor")
      customNativeActionButtonBorderColor = intent.getStringExtra("bottomSheetActionButtonBorderColor")
      customNativeActionButtonBorderWidth = intent.getDoubleExtraOrNull("bottomSheetActionButtonBorderWidth")
      customNativeActionButtonCornerRadius = intent.getDoubleExtraOrNull("bottomSheetActionButtonCornerRadius")
      customNativePrimaryTextFontSize = intent.getDoubleExtraOrNull("bottomSheetPrimaryTextFontSize")
      customNativePrimaryTextFontFamily = intent.getStringExtra("bottomSheetPrimaryTextFontFamily")
      customNativePrimaryTextFontWeight = intent.getStringExtra("bottomSheetPrimaryTextFontWeight")
      customNativeSecondaryTextFontSize = intent.getDoubleExtraOrNull("bottomSheetSecondaryTextFontSize")
      customNativeSecondaryTextFontFamily = intent.getStringExtra("bottomSheetSecondaryTextFontFamily")
      customNativeSecondaryTextFontWeight = intent.getStringExtra("bottomSheetSecondaryTextFontWeight")
      customNativeActionButtonFontSize = intent.getDoubleExtraOrNull("bottomSheetActionButtonFontSize")
      customNativeActionButtonFontFamily = intent.getStringExtra("bottomSheetActionButtonFontFamily")
      customNativeActionButtonFontWeight = intent.getStringExtra("bottomSheetActionButtonFontWeight")
      customNativeActionButtonHeight = intent.getDoubleExtraOrNull("bottomSheetActionButtonHeight")
      customNativeActionButtonsBottomPaddingPx = dpToPx(
        (intent.getDoubleExtra("bottomSheetActionButtonsBottomPadding", 6.0)).toFloat()
      )
      customNativeQuickActionBackgroundColor = intent.getStringExtra("bottomSheetQuickActionBackgroundColor")
      customNativeQuickActionTextColor = intent.getStringExtra("bottomSheetQuickActionTextColor")
      customNativeQuickActionSecondaryBackgroundColor = intent.getStringExtra("bottomSheetQuickActionSecondaryBackgroundColor")
      customNativeQuickActionSecondaryTextColor = intent.getStringExtra("bottomSheetQuickActionSecondaryTextColor")
      customNativeQuickActionGhostTextColor = intent.getStringExtra("bottomSheetQuickActionGhostTextColor")
      customNativeQuickActionBorderColor = intent.getStringExtra("bottomSheetQuickActionBorderColor")
      customNativeQuickActionBorderWidth = intent.getDoubleExtraOrNull("bottomSheetQuickActionBorderWidth")
      customNativeQuickActionCornerRadius = intent.getDoubleExtraOrNull("bottomSheetQuickActionCornerRadius")
      customNativeQuickActionFontFamily = intent.getStringExtra("bottomSheetQuickActionFontFamily")
      customNativeQuickActionFontWeight = intent.getStringExtra("bottomSheetQuickActionFontWeight")
      customNativeShowCurrentStreet = intent.getBooleanExtra("bottomSheetShowCurrentStreet", true)
      customNativeShowRemainingDistance = intent.getBooleanExtra("bottomSheetShowRemainingDistance", true)
      customNativeShowRemainingDuration = intent.getBooleanExtra("bottomSheetShowRemainingDuration", true)
      customNativeShowEta = intent.getBooleanExtra("bottomSheetShowETA", true)
      customNativeShowCompletionPercent = intent.getBooleanExtra("bottomSheetShowCompletionPercent", true)
      customNativeActionButtonTitle = intent.getStringExtra("bottomSheetActionButtonTitle")
      customNativeSecondaryActionButtonTitle = intent.getStringExtra("bottomSheetSecondaryActionButtonTitle")

      val waypointLats = intent.getDoubleArrayExtra("waypointLats")
      val waypointLngs = intent.getDoubleArrayExtra("waypointLngs")
      waypointPoints = parseWaypoints(waypointLats, waypointLngs)

      if (originLat != null && originLng != null) {
        validateLatLng(originLat, originLng, label = "origin")
        startPoint = Point.fromLngLat(originLng, originLat)
      }

      if (destinationLat != null && destinationLng != null) {
        validateLatLng(destinationLat, destinationLng, label = "destination")
        destinationPoint = Point.fromLngLat(destinationLng, destinationLat)
      } else {
        Log.w(TAG, "No valid destination extras provided, starting without preview")
      }
    } catch (throwable: Throwable) {
      showErrorScreen("Navigation init failed: ${throwable.message}", throwable)
      return
    }

    if (hasLocationPermission()) {
      createNavigationViewIfNeeded()
    } else {
      locationPermissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION
        )
      )
    }
  }

  private fun getMapboxAccessToken(): String {
    val resourceId = resources.getIdentifier(
      "mapbox_access_token",
      "string",
      packageName
    )

    if (resourceId == 0) {
      throw IllegalStateException("Missing string resource: mapbox_access_token")
    }

    val token = getString(resourceId).trim()
    if (token.isEmpty()) {
      throw IllegalStateException("mapbox_access_token is empty")
    }

    return token
  }

  private fun createNavigationViewIfNeeded() {
    if (navigationView != null) {
      return
    }

    try {
      delegate.localNightMode = when (uiTheme) {
        "light", "day" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark", "night" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
      }

      navigationView = NavigationView(this, null, accessToken).also { view ->
        view.customizeViewOptions {
          resolveDayStyleUri()?.let { mapStyleUriDay = it }
          resolveNightStyleUri()?.let { mapStyleUriNight = it }
          showSpeedLimit = showsSpeedLimits
          showRoadName = showsWayNameLabel
          // Keep native preview controls available (including "Start") before custom sheet is attached.
          showTripProgress = showsTripProgress
          showManeuver = showsManeuverView
          showActionButtons = showsActionButtons
        }

        if (startPoint != null && destinationPoint != null) {
          view.setRouteOptionsInterceptor(
            RouteOptionsInterceptor { builder ->
              val coordinates = mutableListOf<Point>()
              coordinates.add(startPoint!!)
              coordinates.addAll(waypointPoints)
              coordinates.add(destinationPoint!!)
              builder.coordinatesList(coordinates)
              // Keep optional arrays aligned with coordinates to avoid route-option validation mismatch.
              // Use concrete layer values because null placeholders can be dropped by the API serializer.
              builder.layersList(MutableList(coordinates.size) { 0 })
              builder.alternatives(routeAlternatives)
            }
          )
        }
        view.addListener(navigationViewListener)
        // Avoid full-map gesture interception. A dedicated bottom hot-zone is attached when needed.
      }
      val root = object : FrameLayout(this) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
          maybeHandleNativeBannerSwipe(ev)
          return super.dispatchTouchEvent(ev)
        }
      }.apply {
        layoutParams = FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.MATCH_PARENT
        )
      }
      rootContainer = root
      navigationView?.let { navView ->
        root.addView(
          navView,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
          )
        )
      }
      customNativeAttachPending = bottomSheetMode == "customnative" || bottomSheetMode == "overlay"
      if (customNativeAttachPending && hasStartedGuidance) {
        attachCustomNativeBottomSheetIfNeeded()
      }
      setContentView(root)
      attachNavigationObserversWithRetry()
      applyAudioGuidanceSettingsWithRetry()

      navigationView?.api?.routeReplayEnabled(shouldSimulateRoute)

      // Start destination flow only after the window is attached and active.
      destinationPoint?.let { point ->
        mainHandler.postDelayed({
          if (isFinishing || isDestroyed) return@postDelayed
          if (!hasWindowFocus()) {
            Log.w(TAG, "Skipping destination preview because activity has no window focus")
            return@postDelayed
          }
          navigationView?.api?.startDestinationPreview(point)
        }, 350L)
      }

    } catch (throwable: Throwable) {
      showErrorScreen("Failed to create NavigationView: ${throwable.message}", throwable)
    }
  }

  private fun applyAudioGuidanceSettingsWithRetry(attempt: Int = 0) {
    if (attempt > 20) {
      return
    }
    // No-op until MapboxNavigationProvider is created by the Drop-In view.
    MapboxAudioGuidanceController.setMuted(mute)
    MapboxAudioGuidanceController.setVoiceVolume(voiceVolume)
    MapboxAudioGuidanceController.setLanguage(language)
    if (!MapboxNavigationProvider.isCreated()) {
      mainHandler.postDelayed({ applyAudioGuidanceSettingsWithRetry(attempt + 1) }, 120L)
    }
  }

  private fun attachCustomNativeBottomSheet(root: FrameLayout) {
    if (customNativeBottomSheetContainer != null) {
      return
    }
    val backdrop = View(this).apply {
      setBackgroundColor(0x47000000)
      alpha = if (customNativeHidden) 0f else 1f
      isClickable = !customNativeHidden
      setOnClickListener {
        if (!customNativeHidden) {
          customNativeExpanded = false
          customNativeHidden = true
          applyCustomNativeBottomSheetHeight()
        }
      }
    }
    backdrop.layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT
    )
    root.addView(backdrop)
    customNativeBackdropView = backdrop

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      val bg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(parseColorOrDefault(customNativeBackgroundColor, 0xEE0F172A.toInt()))
        // Subtle border for a more "pro" card-like look by default.
        setStroke(dpToPx(1f), 0x26FFFFFF)
        cornerRadii = floatArrayOf(
          customNativeCornerRadiusPx, customNativeCornerRadiusPx,
          customNativeCornerRadiusPx, customNativeCornerRadiusPx,
          0f, 0f,
          0f, 0f
        )
      }
      background = bg
      setPadding(dpToPx(14f), dpToPx(8f), dpToPx(14f), dpToPx(14f))
      clipToPadding = false
      clipToOutline = true
      elevation = dpToPx(8f).toFloat()
    }
    container.layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      currentCustomNativeBottomSheetHeight(),
      Gravity.BOTTOM
    )
    customNativeBottomSheetContainer = container
    container.alpha = if (customNativeHidden) 0f else 1f
    container.setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          customNativeHandleTouchStartY = event.rawY
          false
        }
        MotionEvent.ACTION_UP -> {
          val deltaY = event.rawY - customNativeHandleTouchStartY
          if (deltaY > 18f) {
            if (customNativeExpanded) {
              customNativeExpanded = false
            } else {
              customNativeHidden = true
            }
            applyCustomNativeBottomSheetHeight()
            return@setOnTouchListener true
          }
          false
        }
        else -> false
      }
    }

    val handle = View(this).apply {
      setBackgroundColor(parseColorOrDefault(intent.getStringExtra("bottomSheetHandleColor"), 0xBFD0D7E2.toInt()))
      layoutParams = LinearLayout.LayoutParams(dpToPx(42f), dpToPx(5f)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
      }
      visibility = if (customNativeShowHandle) View.VISIBLE else View.GONE
    }
    container.addView(handle)

    if (customNativeEnableTapToToggle) {
      handle.setOnClickListener { toggleCustomNativeBottomSheet() }
      handle.setOnTouchListener { _, event ->
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> {
            customNativeHandleTouchStartY = event.rawY
          }
          MotionEvent.ACTION_UP -> {
            val deltaY = event.rawY - customNativeHandleTouchStartY
            if (deltaY < -14f) {
              if (customNativeHidden) {
                customNativeHidden = false
                customNativeExpanded = false
              } else {
                customNativeExpanded = true
              }
              applyCustomNativeBottomSheetHeight()
              return@setOnTouchListener true
            }
            if (deltaY > 14f) {
              if (customNativeExpanded) {
                customNativeExpanded = false
              } else {
                customNativeHidden = true
              }
              applyCustomNativeBottomSheetHeight()
              return@setOnTouchListener true
            }
          }
        }
        false
      }
    }

    val header = buildCustomNativeHeaderView()
    if (header != null) {
      container.addView(header)
    }

    customNativePrimaryTextView = TextView(this).apply {
      text = "Starting navigation..."
      setTextColor(parseColorOrDefault(customNativePrimaryTextColor, 0xFFFFFFFF.toInt()))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, customNativePrimaryTextFontSize?.toFloat() ?: 16f)
      applyConfiguredTypeface(this, customNativePrimaryTextFontFamily, customNativePrimaryTextFontWeight)
      setPadding(0, dpToPx(8f), 0, 0)
      maxLines = 2
    }
    container.addView(customNativePrimaryTextView)

    customNativeSecondaryTextView = TextView(this).apply {
      text = "Waiting for route progress"
      setTextColor(parseColorOrDefault(customNativeSecondaryTextColor, 0xE6BFDBFE.toInt()))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, customNativeSecondaryTextFontSize?.toFloat() ?: 13f)
      applyConfiguredTypeface(this, customNativeSecondaryTextFontFamily, customNativeSecondaryTextFontWeight)
      setPadding(0, dpToPx(2f), 0, 0)
      maxLines = 2
    }
    container.addView(customNativeSecondaryTextView)

    addCustomNativeRows(container)
    addCustomNativeQuickActions(container)
    addCustomNativeBottomButtons(container)

    root.addView(container)
    updateCustomNativeOverlayInteractivity()
  }

  private fun attachCustomNativeBottomSheetIfNeeded() {
    if (!customNativeAttachPending || customNativeBottomSheetContainer != null) {
      return
    }
    val root = rootContainer ?: return
    attachCustomNativeBottomSheet(root)
    configureNativeBannerGestureRevealIfNeeded()
    customNativeAttachPending = false
  }

  private fun addCustomNativeRows(container: LinearLayout) {
    if (customNativeRowIds.isEmpty() || customNativeRowTitles.size != customNativeRowIds.size) {
      return
    }

    val rowsWrap = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(0, dpToPx(8f), 0, 0)
    }

    customNativeRowIds.indices.forEach { index ->
      val row = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dpToPx(4f), 0, dpToPx(2f))
      }

      val top = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }

      val title = TextView(this).apply {
        val iconText = customNativeRowIconTexts.getOrNull(index)?.trim().orEmpty()
        val iconFallback = customNativeRowIconSystemNames
          .getOrNull(index)
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
          ?.replace('.', ' ')
          .orEmpty()
        val prefix = if (iconText.isNotEmpty()) iconText else iconFallback
        text = if (prefix.isNotEmpty()) "$prefix  ${customNativeRowTitles[index]}" else customNativeRowTitles[index]
        setTextColor(parseColorOrDefault(customNativePrimaryTextColor, 0xFFFFFFFF.toInt()))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, (customNativePrimaryTextFontSize?.toFloat() ?: 15f) - 1f)
        applyConfiguredTypeface(this, customNativePrimaryTextFontFamily, customNativePrimaryTextFontWeight)
        if (customNativeRowEmphasis.getOrNull(index) == true) {
          setTypeface(typeface, Typeface.BOLD)
        }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
      }
      top.addView(title)

      val value = customNativeRowValues.getOrNull(index)?.trim().orEmpty()
      if (value.isNotEmpty()) {
        val valueView = TextView(this).apply {
          text = value
          setTextColor(parseColorOrDefault(customNativePrimaryTextColor, 0xFFFFFFFF.toInt()))
          setTextSize(TypedValue.COMPLEX_UNIT_SP, customNativePrimaryTextFontSize?.toFloat() ?: 16f)
          applyConfiguredTypeface(this, customNativePrimaryTextFontFamily, customNativePrimaryTextFontWeight)
          if (customNativeRowEmphasis.getOrNull(index) == true) {
            setTypeface(typeface, Typeface.BOLD)
          }
        }
        top.addView(valueView)
      }

      row.addView(top)

      val subtitle = customNativeRowSubtitles.getOrNull(index)?.trim().orEmpty()
      if (subtitle.isNotEmpty()) {
        val subtitleView = TextView(this).apply {
          text = subtitle
          setTextColor(parseColorOrDefault(customNativeSecondaryTextColor, 0xE6BFDBFE.toInt()))
          setTextSize(TypedValue.COMPLEX_UNIT_SP, customNativeSecondaryTextFontSize?.toFloat() ?: 12f)
          applyConfiguredTypeface(this, customNativeSecondaryTextFontFamily, customNativeSecondaryTextFontWeight)
        }
        row.addView(subtitleView)
      }

      rowsWrap.addView(row)
    }

    container.addView(rowsWrap)
  }

  private fun addCustomNativeQuickActions(container: LinearLayout) {
    if (customNativeQuickActionIds.isEmpty() || customNativeQuickActionLabels.size != customNativeQuickActionIds.size) {
      return
    }
    val row = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(0, dpToPx(8f), 0, kotlin.math.max(0, customNativeActionButtonsBottomPaddingPx))
      gravity = Gravity.CENTER_VERTICAL
    }

    customNativeQuickActionIds.indices.take(4).forEach { index ->
      val button = Button(this).apply {
        text = customNativeQuickActionLabels[index]
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, customNativeActionButtonFontSize?.toFloat() ?: 13f)
        applyConfiguredTypeface(
          this,
          customNativeQuickActionFontFamily ?: customNativeActionButtonFontFamily,
          customNativeQuickActionFontWeight ?: customNativeActionButtonFontWeight
        )
        val buttonCorner = (customNativeQuickActionCornerRadius?.toFloat()
          ?: customNativeActionButtonCornerRadius?.toFloat()
          ?: dpToPx(10f).toFloat()).coerceIn(0f, dpToPx(24f).toFloat())
        val borderWidth = (customNativeQuickActionBorderWidth?.toFloat()
          ?: customNativeActionButtonBorderWidth?.toFloat()
          ?: 0f).coerceIn(0f, dpToPx(4f).toFloat())
        val borderColor = parseColorOrDefault(
          customNativeQuickActionBorderColor ?: customNativeActionButtonBorderColor,
          0x00000000
        )
        val variant = customNativeQuickActionVariants.getOrNull(index)?.trim()?.lowercase().orEmpty()
        when (variant) {
          "ghost" -> {
            background = GradientDrawable().apply {
              shape = GradientDrawable.RECTANGLE
              setColor(0x00000000)
              cornerRadius = buttonCorner
              if (borderWidth > 0f) {
                setStroke(borderWidth.toInt(), borderColor)
              }
            }
            setTextColor(
              parseColorOrDefault(
                customNativeQuickActionGhostTextColor
                  ?: customNativeQuickActionSecondaryTextColor
                  ?: customNativeSecondaryActionButtonTextColor,
                0xFFE6EEF9.toInt()
              )
            )
          }
          "secondary" -> {
            background = GradientDrawable().apply {
              shape = GradientDrawable.RECTANGLE
              setColor(
                parseColorOrDefault(
                  customNativeQuickActionSecondaryBackgroundColor
                    ?: customNativeSecondaryActionButtonBackgroundColor,
                  0x331E293B
                )
              )
              cornerRadius = buttonCorner
              if (borderWidth > 0f) {
                setStroke(borderWidth.toInt(), borderColor)
              }
            }
            setTextColor(
              parseColorOrDefault(
                customNativeQuickActionSecondaryTextColor
                  ?: customNativeSecondaryActionButtonTextColor,
                0xFFE6EEF9.toInt()
              )
            )
          }
          else -> {
            background = GradientDrawable().apply {
              shape = GradientDrawable.RECTANGLE
              setColor(
                parseColorOrDefault(
                  customNativeQuickActionBackgroundColor ?: customNativeActionButtonBackgroundColor,
                  0xFF2563EB.toInt()
                )
              )
              cornerRadius = buttonCorner
              if (borderWidth > 0f) {
                setStroke(borderWidth.toInt(), borderColor)
              }
            }
            setTextColor(
              parseColorOrDefault(
                customNativeQuickActionTextColor ?: customNativeActionButtonTextColor,
                0xFFFFFFFF.toInt()
              )
            )
          }
        }
        setOnClickListener {
          val actionId = customNativeQuickActionIds[index]
          MapboxNavigationEventBridge.emit(
            "onBottomSheetActionPress",
            mapOf("actionId" to actionId)
          )
          when (actionId) {
            "stop" -> finish()
            "toggleMute" -> {
              mute = !mute
              MapboxAudioGuidanceController.setMuted(mute)
            }
            "overview" -> tryInvokeNavigationApi(
              "overview",
              "setCameraToOverview",
              "requestCameraToOverview",
              "setCameraMode",
              arg = "overview"
            )
            "recenter" -> tryInvokeNavigationApi(
              "recenter",
              "setCameraToFollowing",
              "requestCameraToFollowing",
              "setCameraMode",
              arg = "following"
            )
          }
        }
      }
      val params = LinearLayout.LayoutParams(0, dpToPx((customNativeActionButtonHeight ?: 38.0).toFloat()), 1f)
      if (index > 0) {
        params.marginStart = dpToPx(8f)
      }
      row.addView(button, params)
    }

    container.addView(row)
  }

  private fun tryInvokeNavigationApi(vararg methodNames: String, arg: String? = null) {
    val view = navigationView ?: return
    val api = view.api
    runCatching {
      for (name in methodNames) {
        val m = api.javaClass.methods.firstOrNull { it.name == name }
        if (m != null) {
          if (arg != null && m.parameterTypes.size == 1) {
            m.invoke(api, arg)
            return
          }
          if (m.parameterTypes.isEmpty()) {
            m.invoke(api)
            return
          }
        }
      }
    }.onFailure { throwable ->
      Log.w(TAG, "Quick action API invoke failed", throwable)
    }
  }

  private fun addCustomNativeBottomButtons(container: LinearLayout) {
    val primaryTitle = customNativeActionButtonTitle?.trim().orEmpty()
    val secondaryTitle = customNativeSecondaryActionButtonTitle?.trim().orEmpty()
    if (primaryTitle.isEmpty() && secondaryTitle.isEmpty()) {
      return
    }

    val row = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(0, dpToPx(8f), 0, 0)
      gravity = Gravity.CENTER_VERTICAL
    }

    if (primaryTitle.isNotEmpty()) {
      val primary = Button(this).apply {
        text = primaryTitle
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, customNativeActionButtonFontSize?.toFloat() ?: 14f)
        applyConfiguredTypeface(this, customNativeActionButtonFontFamily, customNativeActionButtonFontWeight)
        val buttonCorner = (customNativeActionButtonCornerRadius?.toFloat()
          ?: dpToPx(10f).toFloat()).coerceIn(0f, dpToPx(24f).toFloat())
        background = GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          setColor(parseColorOrDefault(customNativeActionButtonBackgroundColor, 0xFF2563EB.toInt()))
          cornerRadius = buttonCorner
          val borderWidth = (customNativeActionButtonBorderWidth?.toFloat() ?: 0f).coerceIn(0f, dpToPx(4f).toFloat())
          if (borderWidth > 0f) {
            setStroke(borderWidth.toInt(), parseColorOrDefault(customNativeActionButtonBorderColor, 0x00000000))
          }
        }
        setTextColor(parseColorOrDefault(customNativeActionButtonTextColor, 0xFFFFFFFF.toInt()))
        setOnClickListener { finish() }
      }
      row.addView(primary, LinearLayout.LayoutParams(0, dpToPx((customNativeActionButtonHeight ?: 42.0).toFloat()), 1f))
    }

    if (secondaryTitle.isNotEmpty()) {
      val secondary = Button(this).apply {
        text = secondaryTitle
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, customNativeActionButtonFontSize?.toFloat() ?: 14f)
        applyConfiguredTypeface(this, customNativeActionButtonFontFamily, customNativeActionButtonFontWeight)
        val buttonCorner = (customNativeActionButtonCornerRadius?.toFloat()
          ?: dpToPx(10f).toFloat()).coerceIn(0f, dpToPx(24f).toFloat())
        background = GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          setColor(parseColorOrDefault(customNativeSecondaryActionButtonBackgroundColor, 0x331E293B))
          cornerRadius = buttonCorner
          val borderWidth = (customNativeActionButtonBorderWidth?.toFloat() ?: 0f).coerceIn(0f, dpToPx(4f).toFloat())
          if (borderWidth > 0f) {
            setStroke(borderWidth.toInt(), parseColorOrDefault(customNativeActionButtonBorderColor, 0x00000000))
          }
        }
        setTextColor(parseColorOrDefault(customNativeSecondaryActionButtonTextColor, 0xFFE6EEF9.toInt()))
        setOnClickListener {
          MapboxNavigationEventBridge.emit(
            "onBottomSheetActionPress",
            mapOf("actionId" to "secondary")
          )
        }
      }
      val params = LinearLayout.LayoutParams(0, dpToPx((customNativeActionButtonHeight ?: 42.0).toFloat()), 1f)
      if (primaryTitle.isNotEmpty()) {
        params.marginStart = dpToPx(8f)
      }
      row.addView(secondary, params)
    }

    container.addView(row)
  }

  private fun buildCustomNativeHeaderView(): View? {
    val title = customNativeHeaderTitle?.trim().orEmpty()
    val subtitle = customNativeHeaderSubtitle?.trim().orEmpty()
    val badge = customNativeHeaderBadgeText?.trim().orEmpty()
    if (title.isEmpty() && subtitle.isEmpty() && badge.isEmpty()) {
      return null
    }

    val row = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(0, dpToPx(8f), 0, 0)
    }

    val textStack = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    if (title.isNotEmpty()) {
      textStack.addView(TextView(this).apply {
        text = title
        setTextColor(parseColorOrDefault(customNativePrimaryTextColor, 0xFFFFFFFF.toInt()))
        setTextSize(
          TypedValue.COMPLEX_UNIT_SP,
          (customNativeHeaderTitleFontSize?.toFloat() ?: 16f).coerceIn(10f, 30f)
        )
        applyConfiguredTypeface(
          this,
          customNativeHeaderTitleFontFamily ?: customNativePrimaryTextFontFamily,
          customNativeHeaderTitleFontWeight ?: customNativePrimaryTextFontWeight ?: "700"
        )
      })
    }
    if (subtitle.isNotEmpty()) {
      textStack.addView(TextView(this).apply {
        text = subtitle
        setTextColor(parseColorOrDefault(customNativeSecondaryTextColor, 0xE6BFDBFE.toInt()))
        setTextSize(
          TypedValue.COMPLEX_UNIT_SP,
          (customNativeHeaderSubtitleFontSize?.toFloat() ?: 12f).coerceIn(10f, 24f)
        )
        applyConfiguredTypeface(
          this,
          customNativeHeaderSubtitleFontFamily ?: customNativeSecondaryTextFontFamily,
          customNativeHeaderSubtitleFontWeight ?: customNativeSecondaryTextFontWeight
        )
      })
    }
    row.addView(textStack)

    if (badge.isNotEmpty()) {
      row.addView(TextView(this).apply {
        text = " $badge "
        setTextColor(parseColorOrDefault(customNativeHeaderBadgeTextColor, 0xFFFFFFFF.toInt()))
        setTextSize(
          TypedValue.COMPLEX_UNIT_SP,
          (customNativeHeaderBadgeFontSize?.toFloat() ?: 11f).coerceIn(10f, 22f)
        )
        applyConfiguredTypeface(
          this,
          customNativeHeaderBadgeFontFamily ?: customNativeActionButtonFontFamily,
          customNativeHeaderBadgeFontWeight ?: customNativeActionButtonFontWeight ?: "700"
        )
        val badgeRadius = (customNativeHeaderBadgeCornerRadius?.toFloat() ?: dpToPx(9f).toFloat())
          .coerceIn(0f, dpToPx(24f).toFloat())
        val badgeStrokeWidth = (customNativeHeaderBadgeBorderWidth?.toFloat() ?: 0f)
          .coerceIn(0f, dpToPx(4f).toFloat())
        background = GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          cornerRadius = badgeRadius
          setColor(parseColorOrDefault(customNativeHeaderBadgeBackgroundColor, 0xFF2563EB.toInt()))
          if (badgeStrokeWidth > 0f) {
            setStroke(
              badgeStrokeWidth.toInt(),
              parseColorOrDefault(customNativeHeaderBadgeBorderColor, 0x00000000)
            )
          }
        }
        setPadding(dpToPx(6f), dpToPx(2f), dpToPx(6f), dpToPx(2f))
      })
    }

    return row
  }

  private fun toggleCustomNativeBottomSheet() {
    if (customNativeHidden) {
      customNativeHidden = false
      customNativeExpanded = false
    } else {
      customNativeExpanded = !customNativeExpanded
    }
    applyCustomNativeBottomSheetHeight()
  }

  private fun currentCustomNativeBottomSheetHeight(): Int {
    return when {
      customNativeHidden -> customNativeHiddenHeightPx
      customNativeExpanded -> customNativeExpandedHeightPx
      else -> customNativeCollapsedHeightPx
    }
  }

  private fun applyCustomNativeBottomSheetHeight() {
    val container = customNativeBottomSheetContainer ?: return
    container.layoutParams = (container.layoutParams as FrameLayout.LayoutParams).apply {
      height = currentCustomNativeBottomSheetHeight()
    }
    container.alpha = if (customNativeHidden) 0f else 1f
    if (!customNativeHidden) {
      container.translationY = 0f
    }
    updateCustomNativeOverlayInteractivity()
    container.requestLayout()
  }

  private fun revealCustomNativeBottomSheet(expanded: Boolean) {
    val container = customNativeBottomSheetContainer ?: return
    if (!customNativeHidden && customNativeExpanded == expanded) {
      return
    }
    customNativeHidden = false
    customNativeExpanded = expanded
    applyCustomNativeBottomSheetHeight()
    container.alpha = 0f
    container.translationY = dpToPx(20f).toFloat()
    container.animate()
      .alpha(1f)
      .translationY(0f)
      .setDuration(220L)
      .start()
    updateCustomNativeOverlayInteractivity()
  }

  private fun configureNativeBannerGestureRevealIfNeeded() {
    customNativeRevealHotzoneView?.let { existing ->
      (existing.parent as? ViewGroup)?.removeView(existing)
      customNativeRevealHotzoneView = null
    }

    val shouldAttach =
      customNativeRevealOnNativeBannerGesture &&
      (bottomSheetMode == "customnative" || bottomSheetMode == "overlay")

    if (!shouldAttach || !hasStartedGuidance) {
      return
    }
    // Do not add an overlay hotzone View here. Overlay views block taps on SDK buttons underneath.
    // We observe swipes from the root container (dispatchTouchEvent) without consuming touches.
    nativeBannerGestureArmed = false
    updateCustomNativeOverlayInteractivity()
  }

  private fun clearNativeBannerGestureRevealIfNeeded() {
    customNativeRevealHotzoneView?.let { existing ->
      (existing.parent as? ViewGroup)?.removeView(existing)
    }
    customNativeRevealHotzoneView = null
    nativeBannerGestureArmed = false
  }

  private fun updateCustomNativeOverlayInteractivity() {
    val hotzone = customNativeRevealHotzoneView
    val shouldEnableHotzone = customNativeHidden && hasStartedGuidance && customNativeRevealOnNativeBannerGesture
    hotzone?.isClickable = shouldEnableHotzone
    hotzone?.isFocusable = shouldEnableHotzone
    hotzone?.visibility = if (shouldEnableHotzone) View.VISIBLE else View.GONE

    val backdrop = customNativeBackdropView
    if (backdrop != null) {
      val visible = !customNativeHidden
      backdrop.isClickable = visible
      backdrop.visibility = if (visible) View.VISIBLE else View.GONE
      backdrop.alpha = if (visible) 1f else 0f
    }
  }

  private fun maybeHandleNativeBannerSwipe(ev: MotionEvent) {
    if (!customNativeRevealOnNativeBannerGesture) return
    if (!(bottomSheetMode == "customnative" || bottomSheetMode == "overlay")) return
    if (!hasStartedGuidance) return
    if (!customNativeHidden) return
    if (customNativeBottomSheetContainer == null) return

    val root = rootContainer ?: return
    val hotzoneHeight = customNativeRevealGestureHotzoneHeightPx
    val rightExclusion = customNativeRevealGestureRightExclusionWidthPx

    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val y = ev.y
        val x = ev.x
        val inBottomZone = y >= (root.height - hotzoneHeight).toFloat()
        val inAllowedX = x <= (root.width - rightExclusion).toFloat()
        nativeBannerGestureArmed = inBottomZone && inAllowedX
        nativeBannerGestureTouchStartY = ev.rawY
        nativeBannerGestureTouchStartX = ev.rawX
      }
      MotionEvent.ACTION_MOVE -> {
        if (!nativeBannerGestureArmed) return
        val dy = nativeBannerGestureTouchStartY - ev.rawY
        val dx = nativeBannerGestureTouchStartX - ev.rawX
        val verticalEnough = kotlin.math.abs(dy) > kotlin.math.abs(dx)
        val upwardEnough = dy > 26f
        if (verticalEnough && upwardEnough) {
          nativeBannerGestureArmed = false
          revealCustomNativeBottomSheet(expanded = true)
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        nativeBannerGestureArmed = false
      }
    }
  }

  private fun updateCustomNativeBottomSheet(instruction: String?, progress: RouteProgress?) {
    val primary = customNativePrimaryTextView ?: return
    val secondary = customNativeSecondaryTextView ?: return
    val nextInstruction = instruction?.trim()
    if (!nextInstruction.isNullOrBlank()) {
      latestPrimaryInstruction = nextInstruction
      primary.text = nextInstruction
    } else if (!latestPrimaryInstruction.isNullOrBlank()) {
      primary.text = latestPrimaryInstruction
    }
    val currentStreet = latestSecondaryInstruction?.trim().orEmpty()
    val parts = mutableListOf<String>()
    if (customNativeShowCurrentStreet && currentStreet.isNotEmpty()) {
      parts.add(currentStreet)
    }
    if (progress != null) {
      val remainingMeters = kotlin.math.max(0, progress.distanceRemaining.toInt())
      val durationRemaining = kotlin.math.max(0.0, progress.durationRemaining)
      val fraction = progress.fractionTraveled.toDouble().coerceIn(0.0, 1.0)
      val percent = Math.round(fraction * 100.0).toInt()
      if (customNativeShowRemainingDistance) {
        parts.add("$remainingMeters m")
      }
      if (customNativeShowRemainingDuration) {
        parts.add(formatDuration(durationRemaining))
      }
      if (customNativeShowEta) {
        val etaMillis = System.currentTimeMillis() + (durationRemaining * 1000.0).toLong()
        parts.add(formatEtaLocal(etaMillis))
      }
      if (customNativeShowCompletionPercent) {
        parts.add("$percent%")
      }
    }
    secondary.text = if (parts.isNotEmpty()) {
      parts.joinToString(" • ")
    } else {
      "Waiting for route progress"
    }
  }

  override fun onDestroy() {
    mainHandler.removeCallbacksAndMessages(null)
    navigationView?.removeListener(navigationViewListener)
    clearNativeBannerGestureRevealIfNeeded()
    detachNavigationObservers()
    super.onDestroy()
    activeSessionCount = (activeSessionCount - 1).coerceAtLeast(0)
    if (activeActivityRef?.get() === this) {
      activeActivityRef = null
    }
    NavigationSessionRegistry.release(SESSION_OWNER)
    // Mirror iOS behavior: emit cancel when navigation is dismissed by the user.
    // Suppress cancel when closed via explicit stopNavigation() call.
    if (!stopRequested && hasStartedGuidance) {
      MapboxNavigationEventBridge.emit("onCancelNavigation", emptyMap())
    }
    stopRequested = false
    navigationView = null
    rootContainer = null
    customNativeAttachPending = false
    customNativeBottomSheetContainer = null
    customNativeBackdropView = null
    customNativePrimaryTextView = null
    customNativeSecondaryTextView = null
  }

  private fun parseColorOrDefault(value: String?, fallback: Int): Int {
    if (value.isNullOrBlank()) return fallback
    return runCatching { android.graphics.Color.parseColor(value.trim()) }.getOrDefault(fallback)
  }

  private fun applyConfiguredTypeface(view: TextView, family: String?, weight: String?) {
    val requestedWeight = resolveTypefaceWeight(weight)
    val trimmedFamily = family?.trim().orEmpty()
    val baseTypeface = if (trimmedFamily.isNotEmpty()) {
      Typeface.create(trimmedFamily, Typeface.NORMAL)
    } else {
      Typeface.defaultFromStyle(Typeface.NORMAL)
    }
    view.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      Typeface.create(baseTypeface, requestedWeight, false)
    } else {
      Typeface.create(baseTypeface, if (requestedWeight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }
  }

  private fun resolveTypefaceWeight(weight: String?): Int {
    return when (weight?.trim()?.lowercase()) {
      "100", "thin" -> 100
      "200", "extralight", "ultralight" -> 200
      "300", "light" -> 300
      "400", "normal", "regular" -> 400
      "500", "medium" -> 500
      "600", "semibold", "demibold" -> 600
      "700", "bold" -> 700
      "800", "extrabold", "heavy" -> 800
      "900", "black" -> 900
      else -> 400
    }
  }

  private fun dpToPx(dp: Float): Int {
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      dp,
      resources.displayMetrics
    ).toInt()
  }

  private fun resolveAccessToken(): String {
    val fromIntent = intent.getStringExtra("accessToken")?.trim().orEmpty()
    if (fromIntent.startsWith("pk.") && fromIntent.length > 20) {
      return fromIntent
    }

    val fromResources = getMapboxAccessToken()
    if (!fromResources.startsWith("pk.") || fromResources.length <= 20) {
      throw IllegalStateException("Invalid Mapbox public token in mapbox_access_token")
    }
    return fromResources
  }

  private fun logUnsupportedActionButtonOptionsIfRequested() {
    val unsupportedKeys = mutableListOf<String>()

    if (intent.hasExtra("showsReportFeedback")) {
      unsupportedKeys.add("showsReportFeedback")
    }
    if (intent.hasExtra("showsEndOfRouteFeedback")) {
      unsupportedKeys.add("showsEndOfRouteFeedback")
    }
    if (intent.hasExtra("showEmergencyCallButton")) {
      unsupportedKeys.add("androidActionButtons.showEmergencyCallButton")
    }
    if (intent.hasExtra("showCancelRouteButton")) {
      unsupportedKeys.add("androidActionButtons.showCancelRouteButton")
    }
    if (intent.hasExtra("showRefreshRouteButton")) {
      unsupportedKeys.add("androidActionButtons.showRefreshRouteButton")
    }
    if (intent.hasExtra("showReportFeedbackButton")) {
      unsupportedKeys.add("androidActionButtons.showReportFeedbackButton")
    }
    if (intent.hasExtra("showToggleAudioButton")) {
      unsupportedKeys.add("androidActionButtons.showToggleAudioButton")
    }
    if (intent.hasExtra("showSearchAlongRouteButton")) {
      unsupportedKeys.add("androidActionButtons.showSearchAlongRouteButton")
    }
    if (intent.hasExtra("showStartNavigationButton")) {
      unsupportedKeys.add("androidActionButtons.showStartNavigationButton")
    }
    if (intent.hasExtra("showEndNavigationButton")) {
      unsupportedKeys.add("androidActionButtons.showEndNavigationButton")
    }
    if (intent.hasExtra("showAlternativeRoutesButton")) {
      unsupportedKeys.add("androidActionButtons.showAlternativeRoutesButton")
    }
    if (intent.hasExtra("showStartNavigationFeedbackButton")) {
      unsupportedKeys.add("androidActionButtons.showStartNavigationFeedbackButton")
    }
    if (intent.hasExtra("showEndNavigationFeedbackButton")) {
      unsupportedKeys.add("androidActionButtons.showEndNavigationFeedbackButton")
    }

    if (unsupportedKeys.isNotEmpty()) {
      Log.w(
        TAG,
        "Ignoring unsupported Android Drop-In options for current SDK surface: ${unsupportedKeys.joinToString(", ")}"
      )
    }
  }

  private fun Intent.getDoubleExtraOrNull(key: String): Double? {
    if (!hasExtra(key)) {
      return null
    }
    val value = getDoubleExtra(key, Double.NaN)
    if (!value.isFinite()) {
      return null
    }
    return value
  }

  private fun Intent.getBooleanExtraOrNull(key: String): Boolean? {
    if (!hasExtra(key)) {
      return null
    }
    return getBooleanExtra(key, false)
  }

  private fun validateLatLng(latitude: Double, longitude: Double, label: String) {
    if (latitude < -90.0 || latitude > 90.0) {
      throw IllegalStateException("Invalid $label latitude: $latitude")
    }
    if (longitude < -180.0 || longitude > 180.0) {
      throw IllegalStateException("Invalid $label longitude: $longitude")
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

  private fun hasLocationPermission(): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fineGranted || coarseGranted
  }

  private fun showErrorScreen(message: String, throwable: Throwable?) {
    if (throwable != null) {
      Log.e(TAG, message, throwable)
    } else {
      Log.e(TAG, message)
    }

    MapboxNavigationEventBridge.emit(
      "onError",
      mapOf(
        "code" to "NATIVE_ERROR",
        "message" to message
      )
    )

    val titleView = TextView(this).apply {
      text = "Navigation Error"
      textSize = 22f
      setTextColor(0xFFFFFFFF.toInt())
      gravity = Gravity.CENTER
    }

    val messageView = TextView(this).apply {
      text = message
      textSize = 15f
      setTextColor(0xFFD6E4FF.toInt())
      gravity = Gravity.CENTER
      setPadding(0, 24, 0, 32)
    }

    val closeButton = Button(this).apply {
      text = "Back"
      setOnClickListener { finish() }
    }

    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setBackgroundColor(0xFF0B1020.toInt())
      setPadding(48, 48, 48, 48)
      addView(
        titleView,
        LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      )
      addView(
        messageView,
        LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      )
      addView(
        closeButton,
        LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      )
    }

    setContentView(
      FrameLayout(this).apply {
        addView(
          content,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
          )
        )
      }
    )
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

    val payload = mutableMapOf<String, Any?>("primaryText" to primary)
    val secondary = instruction?.secondary()?.text()?.trim().orEmpty()
    if (secondary.isNotEmpty()) {
      payload["secondaryText"] = secondary
    }
    payload["stepDistanceRemaining"] = instruction?.distanceAlongGeometry() ?: 0.0

    MapboxNavigationEventBridge.emit("onBannerInstruction", payload)
    emitJourneyData()
  }

  private fun emitJourneyData() {
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
    latestDistanceRemaining?.let { payload["distanceRemaining"] = it }
    latestDurationRemaining?.let { duration ->
      payload["durationRemaining"] = duration
      val etaMillis = System.currentTimeMillis() + (duration * 1000.0).toLong()
      payload["etaIso8601"] = formatIsoUtc(etaMillis)
    }
    latestFractionTraveled?.let { fraction ->
      payload["fractionTraveled"] = fraction
      payload["completionPercent"] = Math.round(fraction * 100.0).toInt()
    }
    if (payload.isNotEmpty()) {
      MapboxNavigationEventBridge.emit("onJourneyDataChange", payload)
    }
  }

  private fun formatDuration(seconds: Double): String {
    val totalMinutes = kotlin.math.max(0, kotlin.math.round(seconds / 60.0).toInt())
    if (totalMinutes < 60) {
      return "${totalMinutes}m"
    }
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
  }

  private fun formatEtaLocal(timestampMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    formatter.timeZone = java.util.TimeZone.getDefault()
    return formatter.format(java.util.Date(timestampMillis))
  }

  private fun formatIsoUtc(timestampMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return formatter.format(java.util.Date(timestampMillis))
  }

  private fun parseWaypoints(
    waypointLats: DoubleArray?,
    waypointLngs: DoubleArray?
  ): List<Point> {
    if (waypointLats == null || waypointLngs == null || waypointLats.size != waypointLngs.size) {
      return emptyList()
    }

    return waypointLats.indices.mapNotNull { index ->
      val latitude = waypointLats[index]
      val longitude = waypointLngs[index]
      if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return@mapNotNull null
      }
      Point.fromLngLat(longitude, latitude)
    }
  }

  private fun resolveDayStyleUri(): String? {
    mapStyleUriDay?.let { return it }
    val legacy = intent.getStringExtra("mapStyleUri")?.trim().orEmpty()
    return legacy.ifEmpty { null }
  }

  private fun resolveNightStyleUri(): String? {
    mapStyleUriNight?.let { return it }
    val dayFallback = resolveDayStyleUri()
    return dayFallback
  }
}

package expo.modules.mapboxnavigation

import android.content.Intent
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MapboxNavigationModule : Module() {
  private val sessionOwner = "fullscreen"
  private var isNavigating = false
  private var isStartInProgress = false
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

    AsyncFunction("startNavigation") { options: Map<String, Any?>, promise: Promise ->
      startNavigation(options, promise)
    }

    AsyncFunction("stopNavigation") { promise: Promise ->
      stopNavigation(promise)
    }

    AsyncFunction("setMuted") { muted: Boolean, promise: Promise ->
      // Best-effort integration via MapboxAudioGuidance.
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
      // Drop-In SDK unit preference is not currently wired through.
      // Keep state so JS getNavigationSettings() returns consistent values.
      val normalized = unit.trim().lowercase()
      if (normalized == "metric" || normalized == "imperial") {
        this@MapboxNavigationModule.distanceUnit = normalized
      }
      promise.resolve(null)
    }

    AsyncFunction("setLanguage") { language: String, promise: Promise ->
      val trimmed = language.trim()
      if (trimmed.isNotEmpty()) {
        this@MapboxNavigationModule.language = trimmed
      }
      MapboxAudioGuidanceController.setLanguage(language)
      promise.resolve(null)
    }

    AsyncFunction("isNavigating") { promise: Promise ->
      isNavigating = MapboxNavigationActivity.hasActiveSession()
      promise.resolve(isNavigating)
    }

    AsyncFunction("getNavigationSettings") { promise: Promise ->
      val active = MapboxNavigationActivity.hasActiveSession()
      isNavigating = active
      promise.resolve(
        mapOf(
          "isNavigating" to active,
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
    synchronized(this) {
      val activeSession = MapboxNavigationActivity.hasActiveSession()
      if (!activeSession && isNavigating) {
        isNavigating = false
      }
      if (isStartInProgress || isNavigating || activeSession) {
        isNavigating = activeSession || isNavigating
        promise.reject(
          "NAVIGATION_ALREADY_ACTIVE",
          "A navigation session is already starting or active.",
          null
        )
        return
      }
      if (!NavigationSessionRegistry.acquire(sessionOwner)) {
        promise.reject(
          "NAVIGATION_SESSION_CONFLICT",
          "Another navigation session is already active. Stop embedded/full-screen navigation before starting a new one.",
          null
        )
        return
      }
      isStartInProgress = true
    }

    val activity = appContext.currentActivity
    if (activity == null) {
      synchronized(this) {
        isStartInProgress = false
      }
      NavigationSessionRegistry.release(sessionOwner)
      promise.reject("NO_ACTIVITY", "No current activity", null)
      return
    }

    val origin = options["startOrigin"] as? Map<*, *>
    val destination = options["destination"] as? Map<*, *>

    val originLat = (origin?.get("latitude") as? Number)?.toDouble()
    val originLng = (origin?.get("longitude") as? Number)?.toDouble()
    val destLat = (destination?.get("latitude") as? Number)?.toDouble()
    val destLng = (destination?.get("longitude") as? Number)?.toDouble()
    val destinationName = destination?.get("name") as? String

    if (destLat == null || destLng == null) {
      synchronized(this) {
        isStartInProgress = false
      }
      NavigationSessionRegistry.release(sessionOwner)
      promise.reject("INVALID_COORDINATES", "Missing or invalid coordinates", null)
      return
    }

    val shouldSimulate = (options["shouldSimulateRoute"] as? Boolean) ?: false
    val mute = (options["mute"] as? Boolean) ?: false
    val voiceVolume = (options["voiceVolume"] as? Number)?.toDouble() ?: 1.0
    val language = (options["language"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "en"
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
    val bottomSheet = options["bottomSheet"] as? Map<*, *>
    val bottomSheetMode = (bottomSheet?.get("mode") as? String)?.trim()?.lowercase() ?: "native"
    val bottomSheetCollapsedHeight = (bottomSheet?.get("collapsedHeight") as? Number)?.toDouble()
    val bottomSheetExpandedHeight = (bottomSheet?.get("expandedHeight") as? Number)?.toDouble()
    val bottomSheetInitialState = (bottomSheet?.get("initialState") as? String)?.trim()?.lowercase()
    val bottomSheetRevealOnNativeBannerGesture = bottomSheet?.get("revealOnNativeBannerGesture") as? Boolean
    val bottomSheetEnableTapToToggle = bottomSheet?.get("enableTapToToggle") as? Boolean
    val bottomSheetShowHandle = bottomSheet?.get("showHandle") as? Boolean
    val bottomSheetRevealGestureHotzoneHeight = (bottomSheet?.get("revealGestureHotzoneHeight") as? Number)?.toDouble()
    val bottomSheetRevealGestureRightExclusionWidth = (bottomSheet?.get("revealGestureRightExclusionWidth") as? Number)?.toDouble()
    val bottomSheetCornerRadius = (bottomSheet?.get("cornerRadius") as? Number)?.toDouble()
    val bottomSheetBackgroundColor = bottomSheet?.get("backgroundColor") as? String
    val bottomSheetHandleColor = bottomSheet?.get("handleColor") as? String
    val bottomSheetPrimaryTextColor = bottomSheet?.get("primaryTextColor") as? String
    val bottomSheetSecondaryTextColor = bottomSheet?.get("secondaryTextColor") as? String
    val bottomSheetActionButtonBackgroundColor = bottomSheet?.get("actionButtonBackgroundColor") as? String
    val bottomSheetActionButtonTextColor = bottomSheet?.get("actionButtonTextColor") as? String
    val bottomSheetActionButtonTitle = bottomSheet?.get("actionButtonTitle") as? String
    val bottomSheetSecondaryActionButtonTitle = bottomSheet?.get("secondaryActionButtonTitle") as? String
    val bottomSheetActionButtonBorderColor = bottomSheet?.get("actionButtonBorderColor") as? String
    val bottomSheetActionButtonBorderWidth = (bottomSheet?.get("actionButtonBorderWidth") as? Number)?.toDouble()
    val bottomSheetActionButtonCornerRadius = (bottomSheet?.get("actionButtonCornerRadius") as? Number)?.toDouble()
    val bottomSheetSecondaryActionButtonBackgroundColor = bottomSheet?.get("secondaryActionButtonBackgroundColor") as? String
    val bottomSheetSecondaryActionButtonTextColor = bottomSheet?.get("secondaryActionButtonTextColor") as? String
    val bottomSheetPrimaryTextFontSize = (bottomSheet?.get("primaryTextFontSize") as? Number)?.toDouble()
    val bottomSheetPrimaryTextFontFamily = bottomSheet?.get("primaryTextFontFamily") as? String
    val bottomSheetPrimaryTextFontWeight = bottomSheet?.get("primaryTextFontWeight") as? String
    val bottomSheetSecondaryTextFontSize = (bottomSheet?.get("secondaryTextFontSize") as? Number)?.toDouble()
    val bottomSheetSecondaryTextFontFamily = bottomSheet?.get("secondaryTextFontFamily") as? String
    val bottomSheetSecondaryTextFontWeight = bottomSheet?.get("secondaryTextFontWeight") as? String
    val bottomSheetActionButtonFontSize = (bottomSheet?.get("actionButtonFontSize") as? Number)?.toDouble()
    val bottomSheetActionButtonFontFamily = bottomSheet?.get("actionButtonFontFamily") as? String
    val bottomSheetActionButtonFontWeight = bottomSheet?.get("actionButtonFontWeight") as? String
    val bottomSheetActionButtonHeight = (bottomSheet?.get("actionButtonHeight") as? Number)?.toDouble()
    val bottomSheetActionButtonsBottomPadding = (bottomSheet?.get("actionButtonsBottomPadding") as? Number)?.toDouble()
    val bottomSheetQuickActionBackgroundColor = bottomSheet?.get("quickActionBackgroundColor") as? String
    val bottomSheetQuickActionTextColor = bottomSheet?.get("quickActionTextColor") as? String
    val bottomSheetQuickActionSecondaryBackgroundColor = bottomSheet?.get("quickActionSecondaryBackgroundColor") as? String
    val bottomSheetQuickActionSecondaryTextColor = bottomSheet?.get("quickActionSecondaryTextColor") as? String
    val bottomSheetQuickActionGhostTextColor = bottomSheet?.get("quickActionGhostTextColor") as? String
    val bottomSheetQuickActionBorderColor = bottomSheet?.get("quickActionBorderColor") as? String
    val bottomSheetQuickActionBorderWidth = (bottomSheet?.get("quickActionBorderWidth") as? Number)?.toDouble()
    val bottomSheetQuickActionCornerRadius = (bottomSheet?.get("quickActionCornerRadius") as? Number)?.toDouble()
    val bottomSheetQuickActionFontFamily = bottomSheet?.get("quickActionFontFamily") as? String
    val bottomSheetQuickActionFontWeight = bottomSheet?.get("quickActionFontWeight") as? String
    val bottomSheetShowCurrentStreet = bottomSheet?.get("showCurrentStreet") as? Boolean
    val bottomSheetShowRemainingDistance = bottomSheet?.get("showRemainingDistance") as? Boolean
    val bottomSheetShowRemainingDuration = bottomSheet?.get("showRemainingDuration") as? Boolean
    val bottomSheetShowETA = bottomSheet?.get("showETA") as? Boolean
    val bottomSheetShowCompletionPercent = bottomSheet?.get("showCompletionPercent") as? Boolean
    val bottomSheetHeaderTitle = bottomSheet?.get("headerTitle") as? String
    val bottomSheetHeaderTitleFontSize = (bottomSheet?.get("headerTitleFontSize") as? Number)?.toDouble()
    val bottomSheetHeaderTitleFontFamily = bottomSheet?.get("headerTitleFontFamily") as? String
    val bottomSheetHeaderTitleFontWeight = bottomSheet?.get("headerTitleFontWeight") as? String
    val bottomSheetHeaderSubtitle = bottomSheet?.get("headerSubtitle") as? String
    val bottomSheetHeaderSubtitleFontSize = (bottomSheet?.get("headerSubtitleFontSize") as? Number)?.toDouble()
    val bottomSheetHeaderSubtitleFontFamily = bottomSheet?.get("headerSubtitleFontFamily") as? String
    val bottomSheetHeaderSubtitleFontWeight = bottomSheet?.get("headerSubtitleFontWeight") as? String
    val bottomSheetHeaderBadgeText = bottomSheet?.get("headerBadgeText") as? String
    val bottomSheetHeaderBadgeFontSize = (bottomSheet?.get("headerBadgeFontSize") as? Number)?.toDouble()
    val bottomSheetHeaderBadgeFontFamily = bottomSheet?.get("headerBadgeFontFamily") as? String
    val bottomSheetHeaderBadgeFontWeight = bottomSheet?.get("headerBadgeFontWeight") as? String
    val bottomSheetHeaderBadgeBackgroundColor = bottomSheet?.get("headerBadgeBackgroundColor") as? String
    val bottomSheetHeaderBadgeTextColor = bottomSheet?.get("headerBadgeTextColor") as? String
    val bottomSheetHeaderBadgeCornerRadius = (bottomSheet?.get("headerBadgeCornerRadius") as? Number)?.toDouble()
    val bottomSheetHeaderBadgeBorderColor = bottomSheet?.get("headerBadgeBorderColor") as? String
    val bottomSheetHeaderBadgeBorderWidth = (bottomSheet?.get("headerBadgeBorderWidth") as? Number)?.toDouble()
    val quickActions = bottomSheet?.get("quickActions") as? List<*>
    val quickActionIds = quickActions?.mapNotNull { (it as? Map<*, *>)?.get("id") as? String } ?: emptyList()
    val quickActionLabels = quickActions?.mapNotNull { (it as? Map<*, *>)?.get("label") as? String } ?: emptyList()
    val quickActionVariants = quickActions?.mapNotNull { (it as? Map<*, *>)?.get("variant") as? String } ?: emptyList()
    val customRows = bottomSheet?.get("customRows") as? List<*>
    val customRowIds = customRows?.mapNotNull { (it as? Map<*, *>)?.get("id") as? String } ?: emptyList()
    val customRowTitles = customRows?.mapNotNull { (it as? Map<*, *>)?.get("title") as? String } ?: emptyList()
    val customRowValues = customRows?.map { (it as? Map<*, *>)?.get("value") as? String ?: "" } ?: emptyList()
    val customRowSubtitles = customRows?.map { (it as? Map<*, *>)?.get("subtitle") as? String ?: "" } ?: emptyList()
    val customRowIconSystemNames = customRows?.map { (it as? Map<*, *>)?.get("iconSystemName") as? String ?: "" } ?: emptyList()
    val customRowIconTexts = customRows?.map { (it as? Map<*, *>)?.get("iconText") as? String ?: "" } ?: emptyList()
    val customRowEmphasis = customRows?.map {
      ((it as? Map<*, *>)?.get("emphasis") as? Boolean) == true
    } ?: emptyList()
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
        synchronized(this) {
          isStartInProgress = false
        }
        NavigationSessionRegistry.release(sessionOwner)
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
        destinationName?.takeIf { it.isNotBlank() }?.let { putExtra("destinationName", it) }
        putExtra("shouldSimulate", shouldSimulate)
        putExtra("mute", mute)
        putExtra("voiceVolume", voiceVolume)
        putExtra("language", language)
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
        putExtra("bottomSheetMode", bottomSheetMode)
        bottomSheetCollapsedHeight?.let { putExtra("bottomSheetCollapsedHeight", it) }
        bottomSheetExpandedHeight?.let { putExtra("bottomSheetExpandedHeight", it) }
        bottomSheetInitialState?.let { putExtra("bottomSheetInitialState", it) }
        bottomSheetRevealOnNativeBannerGesture?.let { putExtra("bottomSheetRevealOnNativeBannerGesture", it) }
        bottomSheetEnableTapToToggle?.let { putExtra("bottomSheetEnableTapToToggle", it) }
        bottomSheetShowHandle?.let { putExtra("bottomSheetShowHandle", it) }
        bottomSheetRevealGestureHotzoneHeight?.let { putExtra("bottomSheetRevealGestureHotzoneHeight", it) }
        bottomSheetRevealGestureRightExclusionWidth?.let { putExtra("bottomSheetRevealGestureRightExclusionWidth", it) }
        bottomSheetCornerRadius?.let { putExtra("bottomSheetCornerRadius", it) }
        bottomSheetBackgroundColor?.let { putExtra("bottomSheetBackgroundColor", it) }
        bottomSheetHandleColor?.let { putExtra("bottomSheetHandleColor", it) }
        bottomSheetPrimaryTextColor?.let { putExtra("bottomSheetPrimaryTextColor", it) }
        bottomSheetSecondaryTextColor?.let { putExtra("bottomSheetSecondaryTextColor", it) }
        bottomSheetActionButtonBackgroundColor?.let { putExtra("bottomSheetActionButtonBackgroundColor", it) }
        bottomSheetActionButtonTextColor?.let { putExtra("bottomSheetActionButtonTextColor", it) }
        bottomSheetActionButtonTitle?.let { putExtra("bottomSheetActionButtonTitle", it) }
        bottomSheetSecondaryActionButtonTitle?.let { putExtra("bottomSheetSecondaryActionButtonTitle", it) }
        bottomSheetActionButtonBorderColor?.let { putExtra("bottomSheetActionButtonBorderColor", it) }
        bottomSheetActionButtonBorderWidth?.let { putExtra("bottomSheetActionButtonBorderWidth", it) }
        bottomSheetActionButtonCornerRadius?.let { putExtra("bottomSheetActionButtonCornerRadius", it) }
        bottomSheetSecondaryActionButtonBackgroundColor?.let { putExtra("bottomSheetSecondaryActionButtonBackgroundColor", it) }
        bottomSheetSecondaryActionButtonTextColor?.let { putExtra("bottomSheetSecondaryActionButtonTextColor", it) }
        bottomSheetPrimaryTextFontSize?.let { putExtra("bottomSheetPrimaryTextFontSize", it) }
        bottomSheetPrimaryTextFontFamily?.let { putExtra("bottomSheetPrimaryTextFontFamily", it) }
        bottomSheetPrimaryTextFontWeight?.let { putExtra("bottomSheetPrimaryTextFontWeight", it) }
        bottomSheetSecondaryTextFontSize?.let { putExtra("bottomSheetSecondaryTextFontSize", it) }
        bottomSheetSecondaryTextFontFamily?.let { putExtra("bottomSheetSecondaryTextFontFamily", it) }
        bottomSheetSecondaryTextFontWeight?.let { putExtra("bottomSheetSecondaryTextFontWeight", it) }
        bottomSheetActionButtonFontSize?.let { putExtra("bottomSheetActionButtonFontSize", it) }
        bottomSheetActionButtonFontFamily?.let { putExtra("bottomSheetActionButtonFontFamily", it) }
        bottomSheetActionButtonFontWeight?.let { putExtra("bottomSheetActionButtonFontWeight", it) }
        bottomSheetActionButtonHeight?.let { putExtra("bottomSheetActionButtonHeight", it) }
        bottomSheetActionButtonsBottomPadding?.let { putExtra("bottomSheetActionButtonsBottomPadding", it) }
        bottomSheetQuickActionBackgroundColor?.let { putExtra("bottomSheetQuickActionBackgroundColor", it) }
        bottomSheetQuickActionTextColor?.let { putExtra("bottomSheetQuickActionTextColor", it) }
        bottomSheetQuickActionSecondaryBackgroundColor?.let { putExtra("bottomSheetQuickActionSecondaryBackgroundColor", it) }
        bottomSheetQuickActionSecondaryTextColor?.let { putExtra("bottomSheetQuickActionSecondaryTextColor", it) }
        bottomSheetQuickActionGhostTextColor?.let { putExtra("bottomSheetQuickActionGhostTextColor", it) }
        bottomSheetQuickActionBorderColor?.let { putExtra("bottomSheetQuickActionBorderColor", it) }
        bottomSheetQuickActionBorderWidth?.let { putExtra("bottomSheetQuickActionBorderWidth", it) }
        bottomSheetQuickActionCornerRadius?.let { putExtra("bottomSheetQuickActionCornerRadius", it) }
        bottomSheetQuickActionFontFamily?.let { putExtra("bottomSheetQuickActionFontFamily", it) }
        bottomSheetQuickActionFontWeight?.let { putExtra("bottomSheetQuickActionFontWeight", it) }
        bottomSheetShowCurrentStreet?.let { putExtra("bottomSheetShowCurrentStreet", it) }
        bottomSheetShowRemainingDistance?.let { putExtra("bottomSheetShowRemainingDistance", it) }
        bottomSheetShowRemainingDuration?.let { putExtra("bottomSheetShowRemainingDuration", it) }
        bottomSheetShowETA?.let { putExtra("bottomSheetShowETA", it) }
        bottomSheetShowCompletionPercent?.let { putExtra("bottomSheetShowCompletionPercent", it) }
        bottomSheetHeaderTitle?.let { putExtra("bottomSheetHeaderTitle", it) }
        bottomSheetHeaderTitleFontSize?.let { putExtra("bottomSheetHeaderTitleFontSize", it) }
        bottomSheetHeaderTitleFontFamily?.let { putExtra("bottomSheetHeaderTitleFontFamily", it) }
        bottomSheetHeaderTitleFontWeight?.let { putExtra("bottomSheetHeaderTitleFontWeight", it) }
        bottomSheetHeaderSubtitle?.let { putExtra("bottomSheetHeaderSubtitle", it) }
        bottomSheetHeaderSubtitleFontSize?.let { putExtra("bottomSheetHeaderSubtitleFontSize", it) }
        bottomSheetHeaderSubtitleFontFamily?.let { putExtra("bottomSheetHeaderSubtitleFontFamily", it) }
        bottomSheetHeaderSubtitleFontWeight?.let { putExtra("bottomSheetHeaderSubtitleFontWeight", it) }
        bottomSheetHeaderBadgeText?.let { putExtra("bottomSheetHeaderBadgeText", it) }
        bottomSheetHeaderBadgeFontSize?.let { putExtra("bottomSheetHeaderBadgeFontSize", it) }
        bottomSheetHeaderBadgeFontFamily?.let { putExtra("bottomSheetHeaderBadgeFontFamily", it) }
        bottomSheetHeaderBadgeFontWeight?.let { putExtra("bottomSheetHeaderBadgeFontWeight", it) }
        bottomSheetHeaderBadgeBackgroundColor?.let { putExtra("bottomSheetHeaderBadgeBackgroundColor", it) }
        bottomSheetHeaderBadgeTextColor?.let { putExtra("bottomSheetHeaderBadgeTextColor", it) }
        bottomSheetHeaderBadgeCornerRadius?.let { putExtra("bottomSheetHeaderBadgeCornerRadius", it) }
        bottomSheetHeaderBadgeBorderColor?.let { putExtra("bottomSheetHeaderBadgeBorderColor", it) }
        bottomSheetHeaderBadgeBorderWidth?.let { putExtra("bottomSheetHeaderBadgeBorderWidth", it) }
        if (quickActionIds.isNotEmpty() && quickActionLabels.size == quickActionIds.size) {
          putExtra("bottomSheetQuickActionIds", quickActionIds.toTypedArray())
          putExtra("bottomSheetQuickActionLabels", quickActionLabels.toTypedArray())
          putExtra("bottomSheetQuickActionVariants", quickActionVariants.toTypedArray())
        }
        if (customRowIds.isNotEmpty() && customRowTitles.size == customRowIds.size) {
          putExtra("bottomSheetCustomRowIds", customRowIds.toTypedArray())
          putExtra("bottomSheetCustomRowTitles", customRowTitles.toTypedArray())
          putExtra("bottomSheetCustomRowValues", customRowValues.toTypedArray())
          putExtra("bottomSheetCustomRowSubtitles", customRowSubtitles.toTypedArray())
          putExtra("bottomSheetCustomRowIconSystemNames", customRowIconSystemNames.toTypedArray())
          putExtra("bottomSheetCustomRowIconTexts", customRowIconTexts.toTypedArray())
          putExtra("bottomSheetCustomRowEmphasis", customRowEmphasis.toBooleanArray())
        }
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

      try {
        activity.startActivity(intent)
        isNavigating = true
        promise.resolve(null)
      } catch (error: Throwable) {
        isNavigating = false
        NavigationSessionRegistry.release(sessionOwner)
        promise.reject("START_NAVIGATION_FAILED", error.message ?: "Unable to start navigation", error)
      } finally {
        synchronized(this) {
          isStartInProgress = false
        }
      }
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
    MapboxNavigationActivity.finishActiveSession()
    isStartInProgress = false
    isNavigating = false
    NavigationSessionRegistry.release(sessionOwner)
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

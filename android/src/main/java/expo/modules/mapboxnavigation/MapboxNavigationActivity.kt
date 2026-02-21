package expo.modules.mapboxnavigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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

class MapboxNavigationActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "MapboxNavigationActivity"
  }

  private var navigationView: NavigationView? = null
  private lateinit var accessToken: String
  private var startPoint: Point? = null
  private var destinationPoint: Point? = null
  private var waypointPoints: List<Point> = emptyList()
  private var shouldSimulateRoute: Boolean = false
  private var routeAlternatives: Boolean = false
  private var showsSpeedLimits: Boolean = true
  private var showsWayNameLabel: Boolean = true
  private var showsTripProgress: Boolean = true
  private var showsManeuverView: Boolean = true
  private var showsActionButtons: Boolean = true
  private var mapStyleUriDay: String? = null
  private var mapStyleUriNight: String? = null
  private var uiTheme: String = "system"
  private var hasStartedGuidance: Boolean = false
  private var mapboxNavigation: MapboxNavigation? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private val locationObserver = object : LocationObserver {
    override fun onNewRawLocation(rawLocation: android.location.Location) = Unit

    override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
      val location = locationMatcherResult.enhancedLocation
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

    emitBannerInstruction(routeProgress.bannerInstructions)
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
    }

    override fun onDestinationPreview() {
      Log.d(TAG, "NavigationView entered destination preview")
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
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    try {
      accessToken = resolveAccessToken()
      val originLat = intent.getDoubleExtraOrNull("originLat")
      val originLng = intent.getDoubleExtraOrNull("originLng")
      val destinationLat = intent.getDoubleExtraOrNull("destLat")
      val destinationLng = intent.getDoubleExtraOrNull("destLng")
      shouldSimulateRoute = intent.getBooleanExtra("shouldSimulate", false)
      routeAlternatives = intent.getBooleanExtra("routeAlternatives", false)
      showsSpeedLimits = intent.getBooleanExtra("showsSpeedLimits", true)
      showsWayNameLabel = intent.getBooleanExtra("showsWayNameLabel", true)
      showsTripProgress = intent.getBooleanExtra("showsTripProgress", true)
      showsManeuverView = intent.getBooleanExtra("showsManeuverView", true)
      showsActionButtons = intent.getBooleanExtra("showsActionButtons", true)
      mapStyleUriDay = intent.getStringExtra("mapStyleUriDay")?.trim()?.takeIf { it.isNotEmpty() }
      mapStyleUriNight = intent.getStringExtra("mapStyleUriNight")?.trim()?.takeIf { it.isNotEmpty() }
      uiTheme = intent.getStringExtra("uiTheme")?.trim()?.lowercase() ?: "system"

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
              builder.alternatives(routeAlternatives)
            }
          )
        }
        view.addListener(navigationViewListener)
      }
      setContentView(navigationView ?: FrameLayout(this))
      attachNavigationObserversWithRetry()

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

  override fun onDestroy() {
    mainHandler.removeCallbacksAndMessages(null)
    navigationView?.removeListener(navigationViewListener)
    detachNavigationObservers()
    super.onDestroy()
    navigationView = null
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

    val payload = mutableMapOf<String, Any?>("primaryText" to primary)
    val secondary = instruction?.secondary()?.text()?.trim().orEmpty()
    if (secondary.isNotEmpty()) {
      payload["secondaryText"] = secondary
    }
    payload["stepDistanceRemaining"] = instruction?.distanceAlongGeometry() ?: 0.0

    MapboxNavigationEventBridge.emit("onBannerInstruction", payload)
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

import ExpoModulesCore
// Navigation SDK v3 splits the old monolithic `MapboxNavigation` module in two:
// `MapboxNavigationCore` holds the navigator, routing and NavigationMapView,
// and `MapboxNavigationUIKit` holds NavigationViewController, NavigationOptions
// and the day/night styles. `MapboxCoreNavigation` no longer exists.
import MapboxNavigationCore
import MapboxNavigationUIKit
import MapboxDirections
import CoreLocation
import UIKit
import MapboxMaps
import QuartzCore
import Turf

private struct NavigationMarkerPayload {
  let id: String
  let coordinate: CLLocationCoordinate2D
  let label: String?
  let glyph: String
  let badge: String?
  let variant: String
  // Customization: these override variant-based defaults when set
  let customColor: UIColor?
  let customBadgeColor: UIColor?
  let customOpacity: CGFloat?
  let size: String
  let markerStyle: String   // "pin" | "dot"
  let showTail: Bool
  let selected: Bool
  let allowOverlap: Bool
  let anchorOffsetY: CGFloat?  // custom Y offset, overrides size-preset
}

private struct NavigationMarkerMetrics {
  let bubbleSize: CGFloat
  let badgeSize: CGFloat
  let strokeWidth: CGFloat
  let tailSize: CGFloat
  let glyphFontSize: CGFloat
  let badgeFontSize: CGFloat
  let tailOverlap: CGFloat
  let badgeInset: CGFloat
  let markerHeight: CGFloat
  let markerWidth: CGFloat
  let offsetY: CGFloat
}

/// Shared formatter — constructing an ISO8601DateFormatter is relatively
/// expensive and the journey payload is emitted on every location update.
private let journeyEtaFormatter = ISO8601DateFormatter()

private enum NavigationMarkerViewTag {
  static let bubble = 9101
  static let glyph = 9102
  static let badge = 9103
  static let tail = 9104
}

private extension String {
  var nilIfEmpty: String? {
    isEmpty ? nil : self
  }
}

private extension Comparable {
  func clamped(to range: ClosedRange<Self>) -> Self {
    min(max(self, range.lowerBound), range.upperBound)
  }
}

class MapboxNavigationView: ExpoView {
  private static weak var activeInstance: MapboxNavigationView?
  static func requestStopActiveInstance() -> Bool {
    guard let instance = activeInstance else { return false }
    DispatchQueue.main.async {
      instance.enabled = false
      instance.cleanupNavigation()
      instance.dispatchCancelNavigation([:])
    }
    return true
  }

  private let sessionOwner = "embedded-\(UUID().uuidString)"
  var enabled: Bool = false {
    didSet { handleEnabledChange() }
  }
  var startOrigin: [String: Any]? {
    didSet { startNavigationIfReady() }
  }
  var destination: [String: Any]? {
    didSet { startNavigationIfReady() }
  }
  var waypoints: [[String: Any]]? {
    didSet { startNavigationIfReady() }
  }
  var navigationMarkers: [[String: Any]]? {
    didSet { renderNavigationMarkersIfPossible() }
  }
  var shouldSimulateRoute: Bool = false {
    didSet { startNavigationIfReady() }
  }
  var showCancelButton: Bool = true {
    didSet { applyDynamicUIOptionsIfPossible() }
  }
  var mute: Bool = false
  var voiceVolume: Double = 1
  var cameraPitch: Double?
  var cameraZoom: Double?
  var cameraMode: String = "following" {
    didSet {
      let normalized = cameraMode.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
      if normalized == "overview" {
        setCameraFollowingState(false, reason: "prop")
      } else if normalized == "following" {
        resumeCameraFollowingInternal(reason: "prop")
      }
    }
  }
  var mapStyleUri: String?
  var mapStyleUriDay: String?
  var mapStyleUriNight: String?
  var uiTheme: String = "system"
  var routeAlternatives: Bool = false
  var showsSpeedLimits: Bool = true {
    didSet { applyDynamicUIOptionsIfPossible() }
  }
  var showsWayNameLabel: Bool = true {
    didSet { applyDynamicUIOptionsIfPossible() }
  }
  var showsTripProgress: Bool = true {
    didSet { applyDynamicUIOptionsIfPossible() }
  }
  var showsManeuverView: Bool = true {
    didSet { applyDynamicUIOptionsIfPossible() }
  }
  var showsActionButtons: Bool = true {
    didSet { applyDynamicUIOptionsIfPossible() }
  }
  var showsReportFeedback: Bool = true
  var showsEndOfRouteFeedback: Bool = true
  var showsContinuousAlternatives: Bool = true
  var usesNightStyleWhileInTunnel: Bool = true
  var routeLineTracksTraversal: Bool = false
  var annotatesIntersectionsAlongRoute: Bool = false
  var nativeFloatingButtons: [String: Any]? = nil {
    didSet {
      if let navigationViewController {
        applyNativeFloatingButtonsConfiguration(to: navigationViewController)
      }
    }
  }
  var distanceUnit: String = "metric"
  var language: String = "en"
  var locationPuck: [String: Any]? {
    didSet {
      puckStates = LocationPuckFactory.parseStates(locationPuck)
      applyLocationPuck(for: currentPuckState, force: true)
    }
  }
  
  private var navigationViewController: NavigationViewController?
  /// Keyed by marker id.
  ///
  /// v10 tracked the `UIView` and passed it back to the manager for every
  /// update. Maps v11 replaced that with a `ViewAnnotation` object that owns its
  /// view and is mutated in place, so the annotation is what has to be retained.
  private var navigationMarkerAnnotations = [String: ViewAnnotation]()
  private var hostViewController: UIViewController?
  private var isRouteCalculationInProgress = false
  private var hasPendingSessionConflict = false
  private var routeRequestToken = UUID()
  private var isCameraFollowing = true
  private var hasCameraPanGesture = false
  private var puckStates = [LocationPuckState: [String: Any]]()
  private var currentPuckState: LocationPuckState = .idle
  /// Guards against a slow asset load applying a puck for a state the session
  /// has already moved on from.
  private var puckApplyToken = UUID()
  /// Minimum gap between the continuous, high-frequency events
  /// (`onLocationChange`, `onRouteProgressChange`, `onJourneyDataChange`).
  ///
  /// The SDK reports progress on every location fix and each event crosses the
  /// bridge with a payload; on a long drive that is a meaningful amount of
  /// avoidable CPU, garbage and battery. Discrete events (arrival, banner
  /// changes, errors) are never throttled.
  var eventThrottleMs: Double = 0
  private var lastContinuousEmitAt: CFTimeInterval = 0
  private var lastDynamicUIRefreshAt: CFTimeInterval = 0
  private static let dynamicUIRefreshInterval: CFTimeInterval = 2.0
  /// Used only to resolve a starting coordinate when `startOrigin` is omitted.
  /// Android already falls back to the device location, so iOS previously just
  /// never started navigation in that case.
  private lazy var originLocationManager: CLLocationManager = {
    let manager = CLLocationManager()
    manager.delegate = self
    manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    return manager
  }()
  private var isWaitingForOriginFix = false
  
  let onLocationChange = EventDispatcher()
  let onRouteProgressChange = EventDispatcher()
  let onJourneyDataChange = EventDispatcher()
  let onRouteChange = EventDispatcher()
  let onCameraFollowingStateChange = EventDispatcher()
  let onBannerInstruction = EventDispatcher()
  let onArrive = EventDispatcher()
  let onWaypointArrive = EventDispatcher()
  let onOffRoute = EventDispatcher()
  let onCancelNavigation = EventDispatcher()
  let onError = EventDispatcher()
  let onBottomSheetActionPress = EventDispatcher()
  
  required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    setupView()
  }
  
  private func setupView() {
    backgroundColor = .black
  }
  
  override func didMoveToWindow() {
    super.didMoveToWindow()
    
    if window != nil && enabled {
      startNavigationIfReady()
    } else {
      cleanupNavigation()
    }
  }

  private func handleEnabledChange() {
    if enabled {
      if window != nil {
        startNavigationIfReady()
      }
    } else {
      cleanupNavigation()
    }
  }
  
  private func startNavigationIfReady() {
    guard enabled else {
      return
    }
    guard navigationViewController == nil else {
      return
    }
    guard !isRouteCalculationInProgress else {
      return
    }
    guard let dest = destination,
          let destLat = (dest["latitude"] as? NSNumber)?.doubleValue,
          let destLng = (dest["longitude"] as? NSNumber)?.doubleValue else {
      return
    }

    // Refuse to start without an access token rather than let the SDK trap.
    //
    // v3 needs the token when the provider is *constructed*, not when a route
    // is first requested as in v2, and it reports the failure with
    // `assertionFailure` — which crashes Debug builds outright and silently
    // yields an empty token in Release. Reporting it as a normal `onError`
    // keeps a misconfigured app alive and tells the developer exactly what to
    // fix.
    guard MapboxNavigationSession.isConfigured else {
      dispatchError([
        "code": "MISSING_ACCESS_TOKEN",
        "message": "No Mapbox access token found. Set MBXAccessToken in Info.plist "
          + "(the Expo config plugin does this from EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN)."
      ])
      return
    }

    // `startOrigin` is documented as optional. Fall back to the device's last
    // known fix, and wait for one if it isn't available yet.
    guard let resolvedOrigin = resolveStartOrigin() else {
      requestOriginFix()
      return
    }
    let originLat = resolvedOrigin.latitude
    let originLng = resolvedOrigin.longitude

    guard NavigationSessionRegistry.shared.acquire(owner: sessionOwner) else {
      if !hasPendingSessionConflict {
        hasPendingSessionConflict = true
        dispatchError([
          "code": "NAVIGATION_SESSION_CONFLICT",
          "message": "Another embedded navigation session is already active. Stop other embedded navigation before mounting this view."
        ])
      }
      return
    }
    hasPendingSessionConflict = false
    MapboxNavigationView.activeInstance = self
    NavigationSessionRegistry.shared.registerStopHandler(owner: sessionOwner) { [weak self] in
      DispatchQueue.main.async {
        guard let self = self else { return }
        self.enabled = false
        self.cleanupNavigation()
        self.dispatchCancelNavigation([:])
      }
    }
    NavigationSessionRegistry.shared.registerResumeCameraFollowingHandler(owner: sessionOwner) { [weak self] in
      DispatchQueue.main.async {
        self?.resumeCameraFollowingInternal(reason: "module")
      }
    }
    NavigationSessionRegistry.shared.registerCameraFollowingProvider(owner: sessionOwner) { [weak self] in
      return self?.isCameraFollowing ?? true
    }
    NavigationSessionRegistry.shared.registerAdvanceLegHandler(owner: sessionOwner) { [weak self] in
      guard let self = self else { return false }
      DispatchQueue.main.async {
        self.advanceToNextLeg()
      }
      return true
    }
    
    let originCoord = CLLocationCoordinate2D(latitude: originLat, longitude: originLng)
    let destCoord = CLLocationCoordinate2D(latitude: destLat, longitude: destLng)
    
    var waypointsList = [Waypoint(coordinate: originCoord)]
    
    // Add intermediate waypoints
    if let intermediateWaypoints = waypoints {
      for wp in intermediateWaypoints {
        if let lat = (wp["latitude"] as? NSNumber)?.doubleValue,
           let lng = (wp["longitude"] as? NSNumber)?.doubleValue {
          let coord = CLLocationCoordinate2D(latitude: lat, longitude: lng)
          // v3 turned `Waypoint` from a class into a struct, so its properties
          // can no longer be assigned after construction — the name goes in
          // through the initialiser instead.
          waypointsList.append(Waypoint(coordinate: coord, name: wp["name"] as? String))
        }
      }
    }
    
    // Add final destination
    let destinationName = (dest["name"] as? String) ?? (dest["title"] as? String) ?? "Destination"
    waypointsList.append(Waypoint(coordinate: destCoord, name: destinationName))
    
    let routeOptions = NavigationRouteOptions(waypoints: waypointsList)
    routeOptions.locale = Locale(identifier: language)
    routeOptions.distanceMeasurementSystem = distanceUnit == "imperial" ? .imperial : .metric
    routeOptions.includesAlternativeRoutes = routeAlternatives
    
    let requestToken = UUID()
    routeRequestToken = requestToken
    isRouteCalculationInProgress = true

    // v2 took a completion handler off `Directions.shared.calculate`. v3's
    // `RoutingProvider.calculateRoutes(options:)` hands back a
    // `Task<NavigationRoutes, Error>` instead, so the same flow is expressed by
    // awaiting the task. The `@MainActor` Task replaces the explicit
    // `DispatchQueue.main.async` hop the success path used to make.
    Task { @MainActor [weak self] in
      guard let self else { return }

      // `shouldSimulateRoute` is part of `CoreConfig` in v3 rather than a
      // per-service argument, so it must be in place before the provider that
      // serves this request is built.
      MapboxNavigationSession.shared.setSimulatesRoute(self.shouldSimulateRoute)
      let routingProvider = MapboxNavigationSession.shared.mapboxNavigation.routingProvider()

      do {
        let navigationRoutes = try await routingProvider.calculateRoutes(options: routeOptions).value

        guard self.routeRequestToken == requestToken else {
          // A newer embedded start/stop cycle occurred; ignore stale route results.
          NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
          return
        }

        self.isRouteCalculationInProgress = false
        guard self.enabled, self.window != nil, self.navigationViewController == nil else {
          NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
          return
        }

        self.emitRouteChange(route: navigationRoutes.mainRoute.route)
        self.embedNavigation(navigationRoutes: navigationRoutes, routeOptions: routeOptions)
      } catch {
        guard self.routeRequestToken == requestToken else {
          NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
          return
        }
        self.isRouteCalculationInProgress = false
        NavigationSessionRegistry.shared.release(owner: self.sessionOwner)

        // v2 surfaced "routed fine but returned zero routes" as NO_ROUTE by
        // inspecting the response. v3's `NavigationRoutes.mainRoute` is
        // non-optional, so that condition arrives as a thrown
        // `DirectionsError.unableToRoute` — which is the SDK's mapping of the
        // Directions API's `NoRoute` code. Matching it keeps the JS-visible
        // error code stable for consumers switching on it.
        if case DirectionsError.unableToRoute = error {
          self.dispatchError([
            "code": "NO_ROUTE",
            "message": "No route found"
          ])
        } else {
          self.dispatchError([
            "code": "ROUTE_ERROR",
            "message": error.localizedDescription
          ])
        }
      }
    }
  }
  
  private func embedNavigation(navigationRoutes: NavigationRoutes, routeOptions: NavigationRouteOptions) {
    let navigationOptions = buildNavigationOptions()

    let viewController = NavigationViewController(
      navigationRoutes: navigationRoutes,
      navigationOptions: navigationOptions
    )
    
    viewController.delegate = self
    attachMapPanDetection(to: viewController)
    
    // v2 set these on the global `NavigationSettings.shared`. In v3 they belong
    // to the provider, and `MapboxNavigationSession` owns applying them.
    MapboxNavigationSession.shared.setDistanceUnit(distanceUnit)
    MapboxNavigationSession.shared.setMuted(mute)
    MapboxNavigationSession.shared.setVoiceVolume(voiceVolume)
    viewController.showsSpeedLimits = showsSpeedLimits
    applySpeedLimitVisibility(to: viewController)
    applyNativeFloatingButtonsConfiguration(to: viewController)
    applyEmbeddedBannerVisibility(to: viewController)
    // These are all first-class NavigationViewController properties in the
    // Mapbox Navigation iOS v2 SDK; earlier versions of this package warned
    // that they were unsupported and silently dropped them.
    viewController.showsContinuousAlternatives = showsContinuousAlternatives
    viewController.usesNightStyleWhileInTunnel = usesNightStyleWhileInTunnel
    viewController.routeLineTracksTraversal = routeLineTracksTraversal
    viewController.annotatesIntersectionsAlongRoute = annotatesIntersectionsAlongRoute
    applyInterfaceStyle(to: viewController)
    applyCameraConfiguration(to: viewController)
    
    // Attach to the nearest owning view controller in the current RN hierarchy.
    if let parent = nearestViewController() {
      // Add as child view controller
      parent.addChild(viewController)
      addSubview(viewController.view)
      viewController.view.frame = bounds
      viewController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
      viewController.didMove(toParent: parent)
      
      // The view has now loaded, so `styleManager` exists — force the map's
      // day/night tiles to match `uiTheme` (see applyMapStyle).
      applyMapStyle(to: viewController)

      navigationViewController = viewController
      renderNavigationMarkersIfPossible()
      applyLocationPuck(for: .activeNavigation, force: true)
      hostViewController = parent
    } else {
      NavigationSessionRegistry.shared.release(owner: sessionOwner)
      dispatchError([
        "code": "NO_HOST_VIEW_CONTROLLER",
        "message": "Unable to attach embedded navigation to a host view controller."
      ])
    }
  }
  
  override func layoutSubviews() {
    super.layoutSubviews()
    navigationViewController?.view.frame = bounds
  }
  
  deinit {
    cleanupNavigation()
  }
  
  private func cleanupNavigation() {
    // Invalidate any in-flight route calculation callback so it can't re-attach navigation after teardown.
    routeRequestToken = UUID()
    isRouteCalculationInProgress = false
    clearNavigationMarkers()
    navigationViewController?.willMove(toParent: nil)
    navigationViewController?.view.removeFromSuperview()
    navigationViewController?.removeFromParent()
    navigationViewController = nil
    hasCameraPanGesture = false
    if isWaitingForOriginFix {
      isWaitingForOriginFix = false
      originLocationManager.stopUpdatingLocation()
    }
    setCameraFollowingState(true, reason: "cleanup")
    NavigationSessionRegistry.shared.release(owner: sessionOwner)
    if MapboxNavigationView.activeInstance === self {
      MapboxNavigationView.activeInstance = nil
    }
    hasPendingSessionConflict = false
  }

  private func currentNavigationMapView() -> MapView? {
    navigationViewController?.navigationMapView?.mapView
  }

  private func renderNavigationMarkersIfPossible() {
    guard let mapView = currentNavigationMapView() else { return }
    guard let annotationManager = mapView.viewAnnotations else { return }

    let markerPayloads = (navigationMarkers ?? []).compactMap(parseNavigationMarker)
    let nextIds = Set(markerPayloads.map(\.id))

    for (markerId, annotation) in Array(navigationMarkerAnnotations) where !nextIds.contains(markerId) {
      annotation.remove()
      navigationMarkerAnnotations.removeValue(forKey: markerId)
    }

    for marker in markerPayloads {
      let metrics = resolveNavigationMarkerMetrics(marker.size)

      if let annotation = navigationMarkerAnnotations[marker.id] {
        // v11 updates are plain property mutation on the retained annotation —
        // there is no throwing `update(view:options:)` to fall back from, so the
        // add/remove/re-add recovery dance v10 needed is gone.
        bindNavigationMarkerView(annotation.view, marker: marker, metrics: metrics)
        applyNavigationMarkerOptions(to: annotation, marker: marker, metrics: metrics)
        continue
      }

      let markerView = makeNavigationMarkerView(marker, metrics: metrics)
      bindNavigationMarkerView(markerView, marker: marker, metrics: metrics)

      let annotation = ViewAnnotation(
        annotatedFeature: .geometry(Turf.Point(marker.coordinate)),
        view: markerView
      )
      applyNavigationMarkerOptions(to: annotation, marker: marker, metrics: metrics)
      annotationManager.add(annotation)
      navigationMarkerAnnotations[marker.id] = annotation
    }
  }

  private func clearNavigationMarkers() {
    // `ViewAnnotation.remove()` is safe once the map is gone, so unlike v10 this
    // no longer needs the manager to be reachable to tear annotations down.
    for annotation in navigationMarkerAnnotations.values {
      annotation.remove()
    }
    navigationMarkerAnnotations.removeAll()
  }

  private func parseNavigationMarker(_ value: [String: Any]) -> NavigationMarkerPayload? {
    guard let rawId = value["id"] as? String else { return nil }
    let id = rawId.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !id.isEmpty else { return nil }

    guard let latitude = (value["latitude"] as? NSNumber)?.doubleValue,
          let longitude = (value["longitude"] as? NSNumber)?.doubleValue,
          latitude.isFinite,
          longitude.isFinite else { return nil }

    let label = (value["label"] as? String)?
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .nilIfEmpty
    let glyph = ((value["glyph"] as? String)?
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .nilIfEmpty ?? "•")
      .prefix(2)
    let badge = (value["badge"] as? String)?
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .nilIfEmpty?
      .prefix(3)
    let variant = normalizeMarkerVariant(value["variant"] as? String)
    let customColor = parseHexColor(value["color"])
    let customBadgeColor = parseHexColor(value["badgeColor"])
    let customOpacity = (value["opacity"] as? NSNumber).map { CGFloat($0.doubleValue).clamped(to: 0...1) }
    let size = normalizeMarkerSize(value["size"] as? String)
    let markerStyle = normalizeMarkerStyle(value["markerStyle"] as? String)
    let showTail = (value["showTail"] as? Bool) ?? (markerStyle == "pin")
    let selected = (value["selected"] as? Bool) ?? (variant == "primary" || variant == "success")
    let allowOverlap = (value["allowOverlap"] as? Bool) ?? true
    let anchorOffsetY = (value["anchorOffsetY"] as? NSNumber).map { CGFloat($0.doubleValue) }

    return NavigationMarkerPayload(
      id: id,
      coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
      label: label,
      glyph: String(glyph),
      badge: badge.map(String.init),
      variant: variant,
      customColor: customColor,
      customBadgeColor: customBadgeColor,
      customOpacity: customOpacity,
      size: size,
      markerStyle: markerStyle,
      showTail: showTail,
      selected: selected,
      allowOverlap: allowOverlap,
      anchorOffsetY: anchorOffsetY
    )
  }

  /// Apply marker placement to a v11 `ViewAnnotation`.
  ///
  /// Maps v11 made `ViewAnnotationOptions.geometry`, `.anchor`, `.offsetX`,
  /// `.offsetY` and `.associatedFeatureId` **unavailable** — they are declared
  /// `@available(*, unavailable)` and trap with `fatalError()`. The single
  /// anchor plus offset pair is replaced by `variableAnchors`, a list of
  /// candidate `ViewAnnotationAnchorConfig`s; supplying exactly one entry
  /// reproduces v10's fixed-anchor behaviour rather than letting the SDK pick.
  ///
  /// `selected` is deprecated in favour of `priority`, where a higher number
  /// draws on top. v10's `selected: true` meant "place above others", so it maps
  /// to a positive priority.
  ///
  /// Width and height are no longer part of placement — the annotation measures
  /// its view — so `metrics.markerWidth` / `markerHeight` are applied to the
  /// view itself in `bindNavigationMarkerView`.
  private func applyNavigationMarkerOptions(
    to annotation: ViewAnnotation,
    marker: NavigationMarkerPayload,
    metrics: NavigationMarkerMetrics
  ) {
    let offsetY = marker.anchorOffsetY ?? metrics.offsetY
    annotation.annotatedFeature = .geometry(Turf.Point(marker.coordinate))
    annotation.allowOverlap = marker.allowOverlap
    annotation.visible = true
    annotation.variableAnchors = [
      ViewAnnotationAnchorConfig(anchor: .bottom, offsetX: 0, offsetY: offsetY)
    ]
    annotation.priority = marker.selected ? 1 : 0
  }

  private func makeNavigationMarkerView(
    _ marker: NavigationMarkerPayload,
    metrics: NavigationMarkerMetrics
  ) -> UIView {
    let markerView = UIView(frame: CGRect(origin: .zero,
      size: CGSize(width: metrics.markerWidth, height: metrics.markerHeight)))
    markerView.backgroundColor = .clear
    markerView.clipsToBounds = false
    markerView.isUserInteractionEnabled = false
    markerView.accessibilityLabel = marker.label ?? marker.id

    let bubble = UIView()
    bubble.tag = NavigationMarkerViewTag.bubble
    bubble.clipsToBounds = false

    let glyphLabel = UILabel()
    glyphLabel.tag = NavigationMarkerViewTag.glyph
    glyphLabel.textAlignment = .center
    glyphLabel.textColor = .white

    let badgeLabel = UILabel()
    badgeLabel.tag = NavigationMarkerViewTag.badge
    badgeLabel.textAlignment = .center
    badgeLabel.textColor = .white

    let tail = UIView()
    tail.tag = NavigationMarkerViewTag.tail

    markerView.addSubview(bubble)
    bubble.addSubview(glyphLabel)
    bubble.addSubview(badgeLabel)
    markerView.addSubview(tail)

    bindNavigationMarkerView(markerView, marker: marker, metrics: metrics)
    return markerView
  }

  private func bindNavigationMarkerView(
    _ markerView: UIView,
    marker: NavigationMarkerPayload,
    metrics: NavigationMarkerMetrics
  ) {
    guard let bubble = markerView.viewWithTag(NavigationMarkerViewTag.bubble),
          let glyphLabel = markerView.viewWithTag(NavigationMarkerViewTag.glyph) as? UILabel,
          let badgeLabel = markerView.viewWithTag(NavigationMarkerViewTag.badge) as? UILabel,
          let tail = markerView.viewWithTag(NavigationMarkerViewTag.tail) else { return }

    let fillColor = marker.customColor ?? resolveMarkerFillColor(marker.variant)
    let alpha = marker.customOpacity ?? resolveMarkerAlpha(marker.variant, selected: marker.selected)
    let bubbleOriginX = (metrics.markerWidth - metrics.bubbleSize) / 2

    markerView.frame = CGRect(origin: .zero,
      size: CGSize(width: metrics.markerWidth, height: metrics.markerHeight))
    markerView.bounds = markerView.frame
    markerView.alpha = alpha
    markerView.accessibilityLabel = marker.label ?? marker.id

    bubble.frame = CGRect(x: bubbleOriginX, y: 0, width: metrics.bubbleSize, height: metrics.bubbleSize)
    bubble.layer.cornerRadius = metrics.bubbleSize / 2
    bubble.layer.borderWidth = metrics.strokeWidth
    bubble.layer.borderColor = UIColor.white.cgColor
    bubble.backgroundColor = fillColor
    bubble.layer.shadowColor = UIColor.black.withAlphaComponent(0.2).cgColor
    bubble.layer.shadowOpacity = 1
    bubble.layer.shadowRadius = 8
    bubble.layer.shadowOffset = CGSize(width: 0, height: 4)

    glyphLabel.frame = bubble.bounds
    glyphLabel.font = .boldSystemFont(ofSize: metrics.glyphFontSize)
    glyphLabel.text = marker.glyph

    // Tail: only for pin style when showTail is true
    let showTailView = marker.markerStyle == "pin" && marker.showTail
    tail.isHidden = !showTailView
    if showTailView {
      tail.transform = .identity
      tail.frame = CGRect(
        x: (metrics.markerWidth - metrics.tailSize) / 2,
        y: bubble.frame.maxY - metrics.tailOverlap,
        width: metrics.tailSize,
        height: metrics.tailSize
      )
      tail.backgroundColor = fillColor
      tail.layer.cornerRadius = 2
      tail.transform = CGAffineTransform(rotationAngle: .pi / 4)
    }

    if let badge = marker.badge {
      badgeLabel.isHidden = false
      badgeLabel.text = badge
      badgeLabel.font = .boldSystemFont(ofSize: metrics.badgeFontSize)
      let badgeColor = marker.customBadgeColor ?? resolveMarkerBadgeColor(marker.variant)
      badgeLabel.frame = CGRect(
        x: bubble.frame.maxX - metrics.badgeSize + metrics.badgeInset,
        y: -metrics.badgeInset,
        width: metrics.badgeSize,
        height: metrics.badgeSize
      )
      badgeLabel.layer.cornerRadius = metrics.badgeSize / 2
      badgeLabel.layer.masksToBounds = true
      badgeLabel.layer.borderWidth = max(metrics.strokeWidth - 1, 1)
      badgeLabel.layer.borderColor = UIColor.white.cgColor
      badgeLabel.backgroundColor = badgeColor
    } else {
      badgeLabel.isHidden = true
    }
  }

  private func normalizeMarkerVariant(_ raw: String?) -> String {
    switch raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "primary": return "primary"
    case "success": return "success"
    case "warning": return "warning"
    case "danger":  return "danger"
    case "muted":   return "muted"
    default:        return "default"
    }
  }

  private func normalizeMarkerSize(_ raw: String?) -> String {
    switch raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "small": return "small"
    case "large": return "large"
    default:      return "medium"
    }
  }

  private func normalizeMarkerStyle(_ raw: String?) -> String {
    switch raw?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "dot": return "dot"
    default:    return "pin"
    }
  }

  private func parseHexColor(_ raw: Any?) -> UIColor? {
    guard let str = raw as? String else { return nil }
    var hex = str.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !hex.isEmpty else { return nil }
    if hex.hasPrefix("#") { hex = String(hex.dropFirst()) }
    guard hex.count == 6 || hex.count == 8,
          let value = UInt32(hex.prefix(6), radix: 16) else { return nil }
    return hexColor(value)
  }

  private func resolveNavigationMarkerMetrics(_ size: String) -> NavigationMarkerMetrics {
    switch size {
    case "small":
      return NavigationMarkerMetrics(
        bubbleSize: 32, badgeSize: 18, strokeWidth: 2, tailSize: 10,
        glyphFontSize: 14, badgeFontSize: 9, tailOverlap: 3, badgeInset: 3,
        markerHeight: 40, markerWidth: 44, offsetY: 20
      )
    case "large":
      return NavigationMarkerMetrics(
        bubbleSize: 48, badgeSize: 22, strokeWidth: 3, tailSize: 14,
        glyphFontSize: 18, badgeFontSize: 10, tailOverlap: 4, badgeInset: 4,
        markerHeight: 58, markerWidth: 56, offsetY: 30
      )
    default:
      return NavigationMarkerMetrics(
        bubbleSize: 40, badgeSize: 20, strokeWidth: 3, tailSize: 12,
        glyphFontSize: 16, badgeFontSize: 10, tailOverlap: 4, badgeInset: 4,
        markerHeight: 50, markerWidth: 48, offsetY: 26
      )
    }
  }

  private func resolveMarkerFillColor(_ variant: String) -> UIColor {
    switch variant {
    case "primary": return hexColor(0x2563EB)
    case "success": return hexColor(0x15803D)
    case "warning": return hexColor(0xC2410C)
    case "danger":  return hexColor(0xB91C1C)
    case "muted":   return hexColor(0x475569)
    default:        return hexColor(0x1F2937)
    }
  }

  private func resolveMarkerBadgeColor(_ variant: String) -> UIColor {
    switch variant {
    case "primary": return hexColor(0x1D4ED8)
    case "success": return hexColor(0x166534)
    case "warning": return hexColor(0x9A3412)
    case "danger":  return hexColor(0x991B1B)
    case "muted":   return hexColor(0x334155)
    default:        return hexColor(0x111827)
    }
  }

  private func resolveMarkerAlpha(_ variant: String, selected: Bool) -> CGFloat {
    if variant == "muted" { return 0.72 }
    if !selected && variant == "default" { return 0.92 }
    if !selected { return 0.96 }
    return 1
  }

  private func hexColor(_ hex: UInt32) -> UIColor {
    UIColor(
      red:   CGFloat((hex & 0xFF0000) >> 16) / 255,
      green: CGFloat((hex & 0x00FF00) >> 8)  / 255,
      blue:  CGFloat( hex & 0x0000FF)         / 255,
      alpha: 1
    )
  }

  // MARK: - Event dispatch

  /// Send an event to both the view prop callback and the module-level
  /// listeners that back the exported `add*Listener` helpers. The bridge
  /// drops the payload when nothing is subscribed.

  private func dispatchLocationChange(_ payload: [String: Any]) {
    onLocationChange(payload)
    MapboxNavigationEventBridge.shared.emit("onLocationChange", payload)
  }

  private func dispatchRouteProgressChange(_ payload: [String: Any]) {
    onRouteProgressChange(payload)
    MapboxNavigationEventBridge.shared.emit("onRouteProgressChange", payload)
  }

  private func dispatchJourneyDataChange(_ payload: [String: Any]) {
    onJourneyDataChange(payload)
    MapboxNavigationEventBridge.shared.emit("onJourneyDataChange", payload)
  }

  private func dispatchRouteChange(_ payload: [String: Any]) {
    onRouteChange(payload)
    MapboxNavigationEventBridge.shared.emit("onRouteChange", payload)
  }

  private func dispatchCameraFollowingStateChange(_ payload: [String: Any]) {
    onCameraFollowingStateChange(payload)
    MapboxNavigationEventBridge.shared.emit("onCameraFollowingStateChange", payload)
  }

  private func dispatchBannerInstruction(_ payload: [String: Any]) {
    onBannerInstruction(payload)
    MapboxNavigationEventBridge.shared.emit("onBannerInstruction", payload)
  }

  private func dispatchArrive(_ payload: [String: Any]) {
    onArrive(payload)
    MapboxNavigationEventBridge.shared.emit("onArrive", payload)
  }

  private func dispatchWaypointArrive(_ payload: [String: Any]) {
    onWaypointArrive(payload)
    MapboxNavigationEventBridge.shared.emit("onWaypointArrive", payload)
  }

  private func dispatchOffRoute(_ payload: [String: Any]) {
    onOffRoute(payload)
    MapboxNavigationEventBridge.shared.emit("onOffRoute", payload)
  }

  private func dispatchCancelNavigation(_ payload: [String: Any]) {
    onCancelNavigation(payload)
    MapboxNavigationEventBridge.shared.emit("onCancelNavigation", payload)
  }

  private func dispatchError(_ payload: [String: Any]) {
    onError(payload)
    MapboxNavigationEventBridge.shared.emit("onError", payload)
  }

  private func dispatchBottomSheetActionPress(_ payload: [String: Any]) {
    onBottomSheetActionPress(payload)
    MapboxNavigationEventBridge.shared.emit("onBottomSheetActionPress", payload)
  }
  /// Advance the active route to the next leg on a multi-waypoint route without
  /// restarting the session. Driven by a business event (e.g. a passenger is
  /// picked up/dropped off) rather than physical arrival at the waypoint. The
  /// SDK removes the completed leg, recomputes the ETA, and re-focuses the next
  /// waypoint. Must be called on the main thread.
  private func advanceToNextLeg() {
    // v2 reached the router through `navigationService.router` and called
    // `advanceLegIndex(completionHandler:)`. v3 removed both: the navigator is
    // `mapboxNavigation.navigation()` and the operation is `switchLeg`, which
    // takes the target index rather than implicitly stepping forward.
    guard let navigationViewController else { return }
    let navigation = navigationViewController.mapboxNavigation.navigation()
    guard let progress = navigation.currentRouteProgress?.routeProgress else { return }
    let legCount = progress.route.legs.count
    // Already on the final leg (destination) — nothing to advance to.
    guard progress.legIndex < legCount - 1 else { return }
    navigation.switchLeg(newLegIndex: progress.legIndex + 1)
  }

  /// Force the *map* style (day/night tiles) to follow `uiTheme`.
  ///
  /// Setting `overrideUserInterfaceStyle` only themes the UIKit chrome; the map
  /// itself is driven by `StyleManager`, which otherwise auto-switches day/night
  /// by time of day — so during daylight the map stayed light even when the app
  /// was dark. Must be called after the controller's view has loaded (its
  /// `styleManager` is set up in `viewDidLoad`).
  private func applyMapStyle(to viewController: NavigationViewController) {
    guard let styleManager = viewController.styleManager else { return }
    switch uiTheme.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "light", "day":
      styleManager.automaticallyAdjustsStyleForTimeOfDay = false
      styleManager.applyStyle(type: .day)
    case "dark", "night":
      styleManager.automaticallyAdjustsStyleForTimeOfDay = false
      styleManager.applyStyle(type: .night)
    default:
      styleManager.automaticallyAdjustsStyleForTimeOfDay = true
    }
  }

  // MARK: - Start origin resolution

  /// Prefer an explicit `startOrigin`, otherwise the device's last known fix.
  private func resolveStartOrigin() -> CLLocationCoordinate2D? {
    if let origin = startOrigin,
       let latitude = (origin["latitude"] as? NSNumber)?.doubleValue,
       let longitude = (origin["longitude"] as? NSNumber)?.doubleValue,
       CLLocationCoordinate2DIsValid(CLLocationCoordinate2D(latitude: latitude, longitude: longitude)) {
      return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    guard let coordinate = originLocationManager.location?.coordinate,
          CLLocationCoordinate2DIsValid(coordinate) else {
      return nil
    }
    return coordinate
  }

  /// Begin listening for a single location fix so navigation can start without
  /// an explicit `startOrigin`.
  private func requestOriginFix() {
    guard !isWaitingForOriginFix else { return }

    let status: CLAuthorizationStatus
    if #available(iOS 14.0, *) {
      status = originLocationManager.authorizationStatus
    } else {
      status = CLLocationManager.authorizationStatus()
    }

    switch status {
    case .notDetermined:
      isWaitingForOriginFix = true
      originLocationManager.requestWhenInUseAuthorization()
    case .authorizedWhenInUse, .authorizedAlways:
      isWaitingForOriginFix = true
      originLocationManager.requestLocation()
    default:
      dispatchError([
        "code": "LOCATION_PERMISSION_REQUIRED",
        "message": "Embedded navigation needs location permission, or an explicit startOrigin."
      ])
    }
  }

  private func stopWaitingForOriginFix() {
    isWaitingForOriginFix = false
  }

  // MARK: - Custom location puck

  /// Resolve and apply the puck configured for `state`.
  ///
  /// Falls back to the `default` appearance when the state has no override, and
  /// leaves the SDK puck untouched when neither is configured. Asset loading is
  /// asynchronous, so a token guards against a late load overwriting a puck for
  /// a state the session has since left.
  private func applyLocationPuck(for state: LocationPuckState, force: Bool = false) {
    guard force || state != currentPuckState else { return }
    currentPuckState = state

    guard navigationViewController?.navigationMapView != nil else { return }
    guard let appearance = puckStates[state] ?? puckStates[.default] else { return }

    let token = UUID()
    puckApplyToken = token

    LocationPuckFactory.shared.resolve(appearance: appearance) { [weak self] resolution in
      guard let self, self.puckApplyToken == token else { return }
      guard let mapView = self.navigationViewController?.navigationMapView else { return }

      // v2 assigned `NavigationMapView.userLocationStyle`. v3 removed
      // `UserLocationStyle` and takes the Maps `PuckType` through `puckType`.
      switch resolution {
      case .style(let puckType):
        mapView.puckType = puckType
      case .hidden:
        // Still means "transparent puck, location updates continue": v3's
        // NavigationMapView substitutes an all-clear 2D puck for a nil
        // `puckType` rather than disabling location.
        mapView.puckType = nil
      case .sdkDefault:
        // Deliberately `.puck2D()` and not v3's `.puck3D(.navigationDefault)`.
        // v2's default here was the plain 2D puck, and this migration must not
        // silently change what existing consumers see.
        mapView.puckType = .puck2D()
      }
    }
  }

  private func applyDynamicUIOptionsIfPossible() {
    guard let viewController = navigationViewController else { return }
    viewController.showsSpeedLimits = showsSpeedLimits
    applySpeedLimitVisibility(to: viewController)
    applyEmbeddedBannerVisibility(to: viewController)
  }

  private func attachMapPanDetection(to viewController: NavigationViewController) {
    guard !hasCameraPanGesture else { return }
    guard let mapView = viewController.navigationMapView?.mapView else { return }
    let pan = UIPanGestureRecognizer(target: self, action: #selector(handleMapPanGesture(_:)))
    pan.cancelsTouchesInView = false
    pan.delegate = self
    mapView.addGestureRecognizer(pan)
    hasCameraPanGesture = true
  }

  @objc
  private func handleMapPanGesture(_ gesture: UIPanGestureRecognizer) {
    let translation = gesture.translation(in: self)
    if gesture.state == .changed && abs(translation.y) + abs(translation.x) > 8 {
      setCameraFollowingState(false, reason: "gesture")
    }
  }

  private func setCameraFollowingState(_ next: Bool, reason: String) {
    if isCameraFollowing == next { return }
    isCameraFollowing = next
    dispatchCameraFollowingStateChange([
      "isCameraFollowing": next,
      "isCameraNotFollowing": !next,
      "reason": reason
    ])
  }

  private func resumeCameraFollowingInternal(reason: String) {
    navigationViewController?.navigationMapView?.navigationCamera.update(cameraState: .following)
    setCameraFollowingState(true, reason: reason)
  }

  private func applyEmbeddedBannerVisibility(to viewController: NavigationViewController) {
    // Best-effort: Mapbox iOS v2 exposes top/bottom banner controllers internally.
    // We access them via KVC to avoid hard dependencies across SDK patch versions.
    let showManeuver = showsManeuverView
    let showTripProgress = showsTripProgress
    let showActionButtons = showsActionButtons
    let showCancel = showCancelButton

    if let top = resolveTopBannerController(from: viewController) {
      top.view.isHidden = !showManeuver
      top.view.alpha = showManeuver ? 1 : 0
      top.view.isUserInteractionEnabled = showManeuver
      if showManeuver && !showsWayNameLabel {
        hideSubtreeLabelsByHints(
          root: top.view,
          hints: ["street", "wayname", "road", "current"]
        )
      }
    }
    // Fallback for SDK builds where top banner controller isn't exposed via KVC.
    if !showManeuver {
      hideSubtreeByHints(
        root: viewController.view,
        hints: [
          "topbanner",
          "instructionbanner",
          "maneuver",
          "top_banner",
          "instruction",
          "instructionview",
          "maneuverbanner",
          "floatinginstruction",
          "instructionscard",
          "followingturns",
          "upcomingmaneuver",
          "nextmaneuver",
          "stepsoverview",
          "instructionlist",
          "simulating",
          "simulation",
          "replay",
          "speedmultiplier",
          "speedbadge",
          "guidance",
          "lane",
          "junction",
          "upcoming"
        ]
      )
      hideSubtreeByLabelTextHints(
        root: viewController.view,
        textHints: ["simulating", "1x", "following", "turn", "upcoming", "next"]
      )
    } else {
      showSubtreeByHints(
        root: viewController.view,
        hints: [
          "topbanner",
          "instructionbanner",
          "maneuver",
          "top_banner",
          "instruction",
          "instructionview",
          "maneuverbanner",
          "floatinginstruction",
          "instructionscard"
        ]
      )
    }

    if let bottom = resolveBottomBannerController(from: viewController) {
      let showBottom = showTripProgress || showActionButtons
      bottom.view.isHidden = !showBottom
      bottom.view.alpha = showBottom ? 1 : 0
      bottom.view.isUserInteractionEnabled = showBottom

      bottom.distanceRemainingLabel?.isHidden = !showTripProgress
      bottom.timeRemainingLabel?.isHidden = !showTripProgress
      bottom.arrivalTimeLabel?.isHidden = !showTripProgress

      let cancelVisible = showActionButtons && showCancel
      bottom.cancelButton?.isHidden = !cancelVisible
      bottom.cancelButton?.alpha = cancelVisible ? 1 : 0
      bottom.cancelButton?.isUserInteractionEnabled = cancelVisible
    }
    let showBottom = showTripProgress || showActionButtons
    // Fallback for SDK builds where bottom banner controller isn't exposed via KVC.
    if !showBottom {
      hideSubtreeByHints(
        root: viewController.view,
        hints: [
          "bottombanner",
          "tripprogress",
          "bottom_banner",
          "infopanel",
          "routeoverview",
          "routepreview",
          "footer"
        ]
      )
    } else {
      showSubtreeByHints(
        root: viewController.view,
        hints: [
          "bottombanner",
          "tripprogress",
          "bottom_banner",
          "infopanel",
          "routeoverview",
          "routepreview",
          "footer"
        ]
      )
    }
  }

  private func applySpeedLimitVisibility(to viewController: NavigationViewController) {
    if showsSpeedLimits {
      showSubtreeByHints(
        root: viewController.view,
        hints: ["speedlimit", "speed_limit"]
      )
      return
    }
    hideSubtreeByHints(
      root: viewController.view,
      hints: ["speedlimit", "speed_limit"]
    )
  }

  private func hideSubtreeLabelsByHints(root: UIView, hints: [String]) {
    for view in root.subviews {
      let id = (view.accessibilityIdentifier ?? "").lowercased()
      let cls = String(describing: type(of: view)).lowercased()
      if (view is UILabel) && hints.contains(where: { id.contains($0) || cls.contains($0) }) {
        view.isHidden = true
        view.alpha = 0
      }
      if let nested = view as? UIView {
        hideSubtreeLabelsByHints(root: nested, hints: hints)
      }
    }
  }

  private func hideSubtreeByLabelTextHints(root: UIView, textHints: [String]) {
    for view in root.subviews {
      if let label = view as? UILabel {
        let text = (label.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if textHints.contains(where: { text.contains($0) }) {
          label.isHidden = true
          label.alpha = 0
          // Hide small parent stack/card that usually hosts this label.
          var parent = label.superview
          var steps = 0
          while let p = parent, steps < 2 {
            p.isHidden = true
            p.alpha = 0
            p.isUserInteractionEnabled = false
            parent = p.superview
            steps += 1
          }
        }
      }
      if let nested = view as? UIView {
        hideSubtreeByLabelTextHints(root: nested, textHints: textHints)
      }
    }
  }

  private func hideSubtreeByHints(root: UIView, hints: [String]) {
    for view in root.subviews {
      let id = (view.accessibilityIdentifier ?? "").lowercased()
      let cls = String(describing: type(of: view)).lowercased()
      if hints.contains(where: { id.contains($0) || cls.contains($0) }) {
        view.isHidden = true
        view.alpha = 0
        view.isUserInteractionEnabled = false
      }
      if let nested = view as? UIView {
        hideSubtreeByHints(root: nested, hints: hints)
      }
    }
  }

  private func showSubtreeByHints(root: UIView, hints: [String]) {
    for view in root.subviews {
      let id = (view.accessibilityIdentifier ?? "").lowercased()
      let cls = String(describing: type(of: view)).lowercased()
      if hints.contains(where: { id.contains($0) || cls.contains($0) }) {
        view.isHidden = false
        view.alpha = 1
        view.isUserInteractionEnabled = true
      }
      if let nested = view as? UIView {
        showSubtreeByHints(root: nested, hints: hints)
      }
    }
  }

  private func applyNativeFloatingButtonsConfiguration(to viewController: NavigationViewController) {
    let options = nativeFloatingButtons ?? [:]
    let showOverview = (options["showOverviewButton"] as? Bool) ?? true
    let showAudio = (options["showAudioGuidanceButton"] as? Bool) ?? true
    let showFeedback = (options["showFeedbackButton"] as? Bool) ?? true

    // Honour both switches: the dedicated prop and the floating-button toggle.
    viewController.showsReportFeedback = showsReportFeedback && showFeedback
    viewController.loadViewIfNeeded()

    let existingButtons = viewController.floatingButtons ?? []
    guard !existingButtons.isEmpty else {
      if !showOverview && !showAudio && !showFeedback {
        viewController.floatingButtons = []
      }
      return
    }

    let filteredButtons = existingButtons.enumerated().compactMap { index, button in
      switch index {
      case 0:
        return showOverview ? button : nil
      case 1:
        return showAudio ? button : nil
      case 2:
        return showFeedback ? button : nil
      default:
        return button
      }
    }
    viewController.floatingButtons = filteredButtons
  }

  private func resolveTopBannerController(from viewController: NavigationViewController) -> UIViewController? {
    let selector = NSSelectorFromString("topBannerViewController")
    guard viewController.responds(to: selector) else {
      return nil
    }
    return viewController.value(forKey: "topBannerViewController") as? UIViewController
  }

  private func resolveBottomBannerController(from viewController: NavigationViewController) -> BottomBannerViewController? {
    let selector = NSSelectorFromString("bottomBannerViewController")
    guard viewController.responds(to: selector) else {
      return nil
    }
    return viewController.value(forKey: "bottomBannerViewController") as? BottomBannerViewController
  }

  // MARK: -

  /// Apply `cameraMode` / `cameraPitch` / `cameraZoom`.
  ///
  /// v3 reshaped the camera: `NavigationViewportDataSource` became
  /// `MobileViewportDataSource`, the separate `followingMobileCamera` /
  /// `overviewMobileCamera` properties collapsed into one
  /// `currentNavigationCameraOptions` struct holding `followingCamera` and
  /// `overviewCamera`, and `follow()` / `moveToOverview()` became a single
  /// `update(cameraState:)`. The `options` gates kept both their name and their
  /// type (`NavigationViewportDataSourceOptions`).
  ///
  /// - Note: A pre-existing bug is preserved here rather than silently changed.
  ///   In the following branch the `*UpdatesAllowed` gates are left `true`
  ///   while `cameraPitch` / `cameraZoom` are written. Mapbox documents that a
  ///   manual value "will be overriden" unless the matching gate is disabled
  ///   first, so those two props have almost certainly never taken effect in
  ///   following mode — the overview branch below gets this right and disables
  ///   the gate. Fixing it would start honouring a pitch/zoom that consumers
  ///   have been setting to no effect, which is a behaviour change and belongs
  ///   in its own release rather than buried in a migration. Tracked in
  ///   docs/v3-migration.md.
  private func applyCameraConfiguration(to viewController: NavigationViewController) {
    guard
      let navigationMapView = viewController.navigationMapView,
      let viewportDataSource = navigationMapView.navigationCamera
      .viewportDataSource as? MobileViewportDataSource else {
      return
    }

    let normalizedMode = cameraMode.lowercased()
    // Read-modify-write once: `currentNavigationCameraOptions` publishes on
    // every set, so mutating it field by field would emit needless updates.
    var cameraOptions = viewportDataSource.currentNavigationCameraOptions

    if normalizedMode == "overview" {
      // Overview is a distinct NavigationCamera state. This branch previously
      // mutated the *following* viewport and then called follow(), so overview
      // never actually engaged.
      viewportDataSource.options.overviewCameraOptions.pitchUpdatesAllowed = false
      cameraOptions.overviewCamera.pitch = 0

      if let zoom = cameraZoom {
        // Pin the caller's zoom, otherwise let the SDK frame the whole route.
        viewportDataSource.options.overviewCameraOptions.zoomUpdatesAllowed = false
        cameraOptions.overviewCamera.zoom = CGFloat(zoom.clamped(to: 1...22))
      }

      viewportDataSource.currentNavigationCameraOptions = cameraOptions
      navigationMapView.navigationCamera.update(cameraState: .overview)
      setCameraFollowingState(false, reason: "config")
      return
    }

    // Keep dynamic camera updates in following mode so turn-by-turn camera
    // behavior (zoom/pitch/bearing adaptation) stays managed by the SDK.
    viewportDataSource.options.followingCameraOptions.pitchUpdatesAllowed = true
    viewportDataSource.options.followingCameraOptions.zoomUpdatesAllowed = true
    viewportDataSource.options.followingCameraOptions.bearingUpdatesAllowed = true

    if let pitch = cameraPitch {
      cameraOptions.followingCamera.pitch = CGFloat(pitch.clamped(to: 0...85))
    }

    if let zoom = cameraZoom {
      cameraOptions.followingCamera.zoom = CGFloat(zoom.clamped(to: 1...22))
    }

    viewportDataSource.currentNavigationCameraOptions = cameraOptions
    navigationMapView.navigationCamera.update(cameraState: .following)
    setCameraFollowingState(true, reason: "config")
  }

  /// Build the v3 `NavigationOptions`.
  ///
  /// v2 built this around a `NavigationService` it was handed. v3 replaced that
  /// single argument with three collaborators taken from the provider —
  /// `mapboxNavigation`, `voiceController` and `eventsManager` — so the options
  /// are now assembled from `MapboxNavigationSession` instead of a per-call
  /// service. `DayStyle` / `NightStyle` and `Style.mapStyleURL` are unchanged.
  private func buildNavigationOptions() -> NavigationOptions {
    let provider = MapboxNavigationSession.shared.provider

    let dayStyleURL = normalizedStyleURL(
      primary: mapStyleUriDay,
      fallback: mapStyleUri
    )
    let nightStyleURL = normalizedStyleURL(
      primary: mapStyleUriNight,
      fallback: mapStyleUriDay ?? mapStyleUri
    )

    let styles: [Style]?
    if dayStyleURL == nil && nightStyleURL == nil {
      styles = nil
    } else {
      let dayStyle = DayStyle()
      if let dayStyleURL {
        dayStyle.mapStyleURL = dayStyleURL
      }

      let nightStyle = NightStyle()
      if let nightStyleURL {
        nightStyle.mapStyleURL = nightStyleURL
      } else if let dayStyleURL {
        nightStyle.mapStyleURL = dayStyleURL
      }

      styles = [dayStyle, nightStyle]
    }

    return NavigationOptions(
      mapboxNavigation: provider.mapboxNavigation,
      voiceController: provider.routeVoiceController,
      eventsManager: provider.eventsManager(),
      styles: styles,
      // Carrying the provider's predictive cache manager through is what keeps
      // v3's map-tile prefetching active for the embedded map; omitting it
      // silently disables caching.
      predictiveCacheManager: provider.predictiveCacheManager
    )
  }

  private func normalizedStyleURL(primary: String?, fallback: String?) -> URL? {
    let normalizedPrimary = primary?.trimmingCharacters(in: .whitespacesAndNewlines)
    let normalizedFallback = fallback?.trimmingCharacters(in: .whitespacesAndNewlines)
    let raw = (normalizedPrimary?.isEmpty == false ? normalizedPrimary : nil)
      ?? (normalizedFallback?.isEmpty == false ? normalizedFallback : nil)

    guard let raw, let url = URL(string: raw) else {
      return nil
    }
    return url
  }

  private func applyInterfaceStyle(to viewController: UIViewController) {
    switch uiTheme.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "light", "day":
      viewController.overrideUserInterfaceStyle = .light
    case "dark", "night":
      viewController.overrideUserInterfaceStyle = .dark
    default:
      viewController.overrideUserInterfaceStyle = .unspecified
    }
  }

  private func emitRouteChange(route: Route) {
    guard let shape = route.shape else { return }
    emitRouteChange(coordinates: shape.coordinates)
  }

  private func emitRouteChange(coordinates: [CLLocationCoordinate2D]) {
    guard !coordinates.isEmpty else { return }
    let coords = coordinates.map { coordinate in
      [
        "latitude": coordinate.latitude,
        "longitude": coordinate.longitude
      ]
    }
    dispatchRouteChange(["coordinates": coords])
  }

  private func nearestViewController() -> UIViewController? {
    var responder: UIResponder? = self
    while let current = responder {
      if let vc = current as? UIViewController {
        return vc
      }
      responder = current.next
    }
    return window?.rootViewController
  }
}

// MARK: - NavigationViewControllerDelegate
extension MapboxNavigationView: NavigationViewControllerDelegate {
  // v3 narrowed this callback: v2 also passed `at location: CLLocation?` and
  // `proactive: Bool`. Neither was used here, so nothing is lost.
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didRerouteAlong route: Route
  ) {
    emitRouteChange(route: route)
  }

  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didUpdate progress: RouteProgress,
    with location: CLLocation,
    rawLocation: CLLocation
  ) {
    // Mapbox rebuilds parts of its banner hierarchy as guidance proceeds, so
    // the visibility pass has to run more than once — but it walks the entire
    // view tree, so throttle it instead of running it on every update.
    if !showsManeuverView || !showsTripProgress || !showsActionButtons || !showsSpeedLimits || !showsWayNameLabel {
      let now = CACurrentMediaTime()
      if now - lastDynamicUIRefreshAt >= Self.dynamicUIRefreshInterval {
        lastDynamicUIRefreshAt = now
        applyDynamicUIOptionsIfPossible()
      }
    }

    if cameraMode.lowercased() == "following" && isCameraFollowing {
      navigationViewController.navigationMapView?.navigationCamera.update(cameraState: .following)
    }

    // Throttle the continuous stream; banner/arrival below are unaffected.
    let nowTime = CACurrentMediaTime()
    let throttleSeconds = max(0, min(eventThrottleMs, 10_000)) / 1000
    let shouldEmitContinuous =
      throttleSeconds <= 0 || (nowTime - lastContinuousEmitAt) >= throttleSeconds

    if shouldEmitContinuous {
      lastContinuousEmitAt = nowTime
    }

    guard shouldEmitContinuous else {
      dispatchBannerInstruction([
        "primaryText": progress.currentLegProgress.currentStep.instructions,
        "stepDistanceRemaining": progress.currentLegProgress.currentStepProgress.distanceRemaining
      ])
      return
    }

    dispatchLocationChange([
      "latitude": location.coordinate.latitude,
      "longitude": location.coordinate.longitude,
      "bearing": location.course,
      "speed": location.speed,
      "altitude": location.altitude,
      "accuracy": location.horizontalAccuracy
    ])
    
    dispatchRouteProgressChange([
      "distanceTraveled": progress.distanceTraveled,
      "distanceRemaining": progress.distanceRemaining,
      "durationRemaining": progress.durationRemaining,
      "fractionTraveled": progress.fractionTraveled,
      "legIndex": progress.legIndex
    ])

    dispatchBannerInstruction([
      "primaryText": progress.currentLegProgress.currentStep.instructions,
      "stepDistanceRemaining": progress.currentLegProgress.currentStepProgress.distanceRemaining
    ])

    let secondaryInstruction = progress.currentLegProgress.currentStep.names?.first
      ?? progress.currentLegProgress.currentStep.description
    let durationRemaining = progress.durationRemaining
    dispatchJourneyDataChange([
      "latitude": location.coordinate.latitude,
      "longitude": location.coordinate.longitude,
      "bearing": location.course,
      "speed": location.speed,
      "altitude": location.altitude,
      "accuracy": location.horizontalAccuracy,
      "primaryInstruction": progress.currentLegProgress.currentStep.instructions,
      "secondaryInstruction": secondaryInstruction,
      "currentStreet": secondaryInstruction,
      "stepDistanceRemaining": progress.currentLegProgress.currentStepProgress.distanceRemaining,
      "distanceRemaining": progress.distanceRemaining,
      "durationRemaining": durationRemaining,
      "fractionTraveled": progress.fractionTraveled,
      "completionPercent": Int((max(0, min(progress.fractionTraveled, 1)) * 100).rounded()),
      "etaIso8601": journeyEtaFormatter.string(from: Date().addingTimeInterval(durationRemaining))
    ])
  }
  
  /// v3 changed this from `-> Bool` to `Void`.
  ///
  /// In v2 the return value decided whether the SDK auto-advanced to the next
  /// leg, and this returned `true` to keep the default behaviour. v3 moved that
  /// decision to `CoreConfig.multilegAdvancing`, whose default is
  /// `.automatically` — so leg advance still happens on its own and the
  /// behaviour is unchanged. `MapboxNavigationSession` leaves that field at its
  /// default deliberately.
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didArriveAt waypoint: Waypoint
  ) {
    // This fires for EVERY leg destination, not just the final one. Previously
    // an intermediate stop emitted a plain `onArrive`, so on a multi-stop route
    // the JS end-of-route flow triggered at the first waypoint.
    // `navigationService` is gone in v3; route progress now comes off the
    // navigator via the view controller's `mapboxNavigation`.
    guard let progress = navigationViewController.mapboxNavigation.navigation()
      .currentRouteProgress?.routeProgress
    else { return }
    let isFinal = progress.isFinalLeg
    let payload: [String: Any] = [
      "index": progress.legIndex,
      "name": waypoint.name ?? "",
      "isFinalDestination": isFinal,
      "remainingWaypoints": progress.remainingWaypoints.count
    ]

    if isFinal {
      applyLocationPuck(for: .arrival)
      dispatchArrive(payload)
    } else {
      dispatchWaypointArrive(payload)
    }
  }

  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    willRerouteFrom location: CLLocation?
  ) {
    var payload: [String: Any] = [:]
    if let location {
      payload["latitude"] = location.coordinate.latitude
      payload["longitude"] = location.coordinate.longitude
    }
    dispatchOffRoute(payload)
  }

  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didFailToRerouteWith error: Error
  ) {
    dispatchError([
      "code": "REROUTE_FAILED",
      "message": error.localizedDescription
    ])
  }
  
  func navigationViewControllerDidDismiss(
    _ navigationViewController: NavigationViewController,
    byCanceling canceled: Bool
  ) {
    if canceled {
      dispatchCancelNavigation([:])
    }
    cleanupNavigation()
  }
}

// MARK: - CLLocationManagerDelegate
extension MapboxNavigationView: CLLocationManagerDelegate {
  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    guard isWaitingForOriginFix, locations.last != nil else { return }
    stopWaitingForOriginFix()
    startNavigationIfReady()
  }

  func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    guard isWaitingForOriginFix else { return }
    stopWaitingForOriginFix()
    dispatchError([
      "code": "START_ORIGIN_UNAVAILABLE",
      "message": "Could not determine a starting location: \(error.localizedDescription). Pass startOrigin explicitly."
    ])
  }

  func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    guard isWaitingForOriginFix else { return }
    let status: CLAuthorizationStatus
    if #available(iOS 14.0, *) {
      status = manager.authorizationStatus
    } else {
      status = CLLocationManager.authorizationStatus()
    }

    if status == .authorizedWhenInUse || status == .authorizedAlways {
      manager.requestLocation()
    } else if status != .notDetermined {
      stopWaitingForOriginFix()
      dispatchError([
        "code": "LOCATION_PERMISSION_REQUIRED",
        "message": "Embedded navigation needs location permission, or an explicit startOrigin."
      ])
    }
  }
}

extension MapboxNavigationView: UIGestureRecognizerDelegate {
  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
    true
  }
}

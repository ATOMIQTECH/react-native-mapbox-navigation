import Combine
import ExpoModulesCore
import MapboxNavigationCore
import MapboxNavigationUIKit
@_spi(ExperimentalMapboxAPI) import MapboxDirections
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
  let customColor: UIColor?
  let customBadgeColor: UIColor?
  let customOpacity: CGFloat?
  let size: String
  let markerStyle: String
  let showTail: Bool
  let selected: Bool
  let allowOverlap: Bool
  let anchorOffsetY: CGFloat?
  let image: NavigationMarkerImage?
  let animatesMovement: Bool
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

private let journeyEtaFormatter = ISO8601DateFormatter()

private enum NavigationMarkerViewTag {
  static let image = 9100
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
    didSet { handleRouteTargetChange() }
  }
  var waypoints: [[String: Any]]? {
    didSet { handleRouteTargetChange() }
  }
  var navigationMarkers: [[String: Any]]? {
    didSet { renderNavigationMarkersIfPossible() }
  }
  var navigationLines: [[String: Any]]? {
    didSet { renderNavigationLinesIfPossible() }
  }
  private var renderedNavigationLineIds = Set<String>()
  var shouldSimulateRoute: Bool = false {
    didSet { startNavigationIfReady() }
  }
  var showCancelButton: Bool = true {
    didSet { applyDynamicUIOptionsIfPossible() }
  }
  var mute: Bool = false {
    didSet {
      guard mute != oldValue, navigationViewController != nil else { return }
      MapboxNavigationSession.shared.setMuted(mute)
    }
  }
  var voiceVolume: Double = 1 {
    didSet {
      guard voiceVolume != oldValue, navigationViewController != nil else { return }
      MapboxNavigationSession.shared.setVoiceVolume(voiceVolume)
    }
  }
  var journeyDataEnabled: Bool = true
  var keepAliveWhenDetached: Bool = false {
    didSet {
      if !keepAliveWhenDetached, isDetachedWhileAlive {
        endDetachedSession()
      }
    }
  }
  var cameraPitch: Double?
  var cameraZoom: Double?
  var cameraMode: String = "following" {
    didSet {
      let normalized = cameraMode.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
      if normalized == "overview" {
        setCameraFollowingState(false, reason: "prop")
        if let viewController = navigationViewController {
          if viewController.navigationMapView?.navigationCamera.currentCameraState != .overview {
            pendingCameraReason = "prop"
          }
          applyCameraConfiguration(to: viewController)
        }
      } else if normalized == "following" {
        resumeCameraFollowingInternal(reason: "prop")
      }
    }
  }
  var mapStyleUri: String? {
    didSet { scheduleMapStyleUpdate() }
  }
  var mapStyleUriDay: String? {
    didSet { scheduleMapStyleUpdate() }
  }
  var mapStyleUriNight: String? {
    didSet { scheduleMapStyleUpdate() }
  }
  var cameraPadding: [String: Any]? {
    didSet { applyCameraPadding() }
  }
  var ornamentBottomMargin: Double? {
    didSet { navigationViewController?.extraOrnamentBottomMargin = resolvedOrnamentBottomMargin }
  }
  private var resolvedOrnamentBottomMargin: CGFloat {
    CGFloat(max(0, ornamentBottomMargin ?? 0))
  }
  var uiTheme: String = "system"
  var routeAlternatives: Bool = false
  var routeProfile: String = "driving-traffic"
  var routeExclusions: [String: Any]?
  var vehicle: [String: Any]?
  var mapStyleConfig: [String: Any]? {
    didSet {
      hasWarnedAboutMapStyleConfig = false
      applyMapStyleConfig(afterStyleLoad: true)
    }
  }
  var showsSpeedLimits: Bool = true {
    didSet { applyDynamicUIOptionsIfPossible() }
  }
  var showsWayNameLabel: Bool = true {
    didSet { applyDynamicUIOptionsIfPossible() }
  }
  var showsWaypoints: Bool = true
  var waypointAdvance: String = "automatic" {
    didSet {
      MapboxNavigationSession.legAdvance.set(waypointAdvance != "manual")
    }
  }
  var showsTripProgress: Bool = true {
    didSet { applyBannerChange() }
  }
  var showsManeuverView: Bool = true {
    didSet { applyBannerChange() }
  }
  var showsActionButtons: Bool = true {
    didSet {
      applyDynamicUIOptionsIfPossible()
      if let navigationViewController {
        applyNativeResumeButtonVisibility(to: navigationViewController)
      }
    }
  }
  var showsReportFeedback: Bool = true
  var showsContinuousAlternatives: Bool = true {
    didSet {
      MapboxNavigationSession.acceptsFasterRoutes.set(showsContinuousAlternatives)
      navigationViewController?.showsContinuousAlternatives = showsContinuousAlternatives
    }
  }
  var usesNightStyleWhileInTunnel: Bool = true
  var routeLineTracksTraversal: Bool = false
  var annotatesIntersectionsAlongRoute: Bool = false
  var nativeFloatingButtons: [String: Any]? = nil {
    didSet {
      if let navigationViewController {
        applyNativeFloatingButtonsConfiguration(to: navigationViewController)
        applyNativeResumeButtonVisibility(to: navigationViewController)
      }
    }
  }
  var distanceUnit: String = "metric"
  var language: String = "en" {
    didSet {
      guard language != oldValue, navigationViewController != nil else { return }
      MapboxNavigationSession.shared.setLanguage(resolvedLanguage)
    }
  }
  var colors: [String: Any]? {
    didSet {
      MapboxNavigationTheme.configure(with: colors)
      MapboxNavigationTheme.applyAppearance()
      applyRouteLineColors(redraw: true)
      if let viewController = navigationViewController {
        MapboxNavigationTheme.refresh(in: viewController)
      }
    }
  }
  var locationPuck: [String: Any]? {
    didSet {
      puckStates = LocationPuckFactory.parseStates(locationPuck)
      applyLocationPuck(for: currentPuckState, force: true)
    }
  }
  
  private var navigationViewController: EmbeddedNavigationViewController?
  private var usesEmptyBanners = false
  private var routesSubscription: AnyCancellable?
  private var cameraStateSubscription: AnyCancellable?
  private var locationMatchingSubscription: AnyCancellable?
  private var lastLocationEmitAt: CFTimeInterval = 0
  private var pendingCameraReason: String?
  private var lastEmittedRouteKey: String?
  private var lastBannerKey: String?
  private var lastBannerDistance: CLLocationDistance = 0
  private var lastBannerEmitAt: CFTimeInterval = 0
  private var lastBannerPayload: [String: Any]?
  private var isReportedOffRoute = false
  private var isDetachedWhileAlive = false
  private let detachedTeardown = DeferredWork()
  private static let detachedSessionLifetime: TimeInterval = 10 * 60
  private let windowGraceTeardown = DeferredWork()
  private static let windowGrace: TimeInterval = 0.5
  private var sdkViewportPadding: UIEdgeInsets?


  private var routedDestination: CLLocationCoordinate2D?
  private var routedDestinationCurb = false
  private var routedIntermediateWaypoints: [Waypoint] = []
  private var routedWaypointsProp: [Waypoint] = []
  private var needsInPlaceReroute = false
  private var needsMapStyleUpdate = false
  private var cameraBeforeStyleSwap: CameraOptions?
  private var inPlaceRerouteToken = UUID()
  private var inPlaceRerouteTask: Task<NavigationRoutes, Error>?
  private var inPlaceRerouteTarget: InPlaceRerouteTarget?
  private var inPlaceRerouteAttempt = 0
  private var inPlaceRerouteRetry: DispatchWorkItem?
  private var isTargetRerouteFailing = false
  private var sessionStateSubscription: AnyCancellable?
  private static let inPlaceRerouteRetryDelays: [TimeInterval] = [2, 5, 10, 20]

  private struct InPlaceRerouteTarget {
    let destination: CLLocationCoordinate2D
    let curb: Bool
    let waypoints: [Waypoint]

    func matches(_ other: InPlaceRerouteTarget) -> Bool {
      RouteTarget.isSamePlace(destination, other.destination)
        && curb == other.curb
        && RouteTarget.isSameWaypointList(waypoints, other.waypoints)
    }
  }
  private var navigationMarkerAnnotations = [String: ViewAnnotation]()
  private struct MarkerMotion {
    let from: CLLocationCoordinate2D
    let to: CLLocationCoordinate2D
    let start: CFTimeInterval
    let duration: CFTimeInterval
  }
  private var markerShownAt = [String: CLLocationCoordinate2D]()
  private var markerUpdatedAt = [String: CFTimeInterval]()
  private var markerMotions = [String: MarkerMotion]()
  private var markerDisplayLink: CADisplayLink?
  private static let markerGlideMaxDistance: CLLocationDistance = 500
  private var hostViewController: UIViewController?
  private var isRouteCalculationInProgress = false
  private var hasPendingSessionConflict = false
  private var routeRequestToken = UUID()
  private var hasWarnedAboutMapStyleConfig = false
  private var styleLoadedSubscription: AnyCancellable?
  private var isCameraFollowing = true
  private var hasCameraPanGesture = false
  private var puckStates = [LocationPuckState: [String: Any]]()
  private var currentPuckState: LocationPuckState = .idle
  private var puckApplyToken = UUID()
  var eventThrottleMs: Double = 0
  private var lastContinuousEmitAt: CFTimeInterval = 0
  private var lastDynamicUIRefreshAt: CFTimeInterval = 0
  private static let dynamicUIRefreshInterval: CFTimeInterval = 2.0
  private lazy var originLocationManager: CLLocationManager = {
    let manager = CLLocationManager()
    manager.delegate = self
    manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
    return manager
  }()
  private var isWaitingForOriginFix = false
  private var guidanceWatchdog: DispatchWorkItem?
  private var shouldRestartWhenWindowAppears = false
  private var hasReceivedRouteProgress = false
  private static let guidanceWatchdogTimeout: TimeInterval = 20
  private var bestOriginFix: CLLocation?
  private var acceptedOriginFix: CLLocation?
  private var hasOriginWaitTimedOut = false
  private var originWaitTimeout: DispatchWorkItem?
  private var originNoFixReport: DispatchWorkItem?
  private static let originMaxAge: TimeInterval = 15
  private static let originMaxAccuracy: CLLocationAccuracy = 50
  private static let originWaitSeconds: TimeInterval = 4
  private static let headingMinimumSpeed: CLLocationSpeed = 2
  
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
      windowGraceTeardown.cancel()
      shouldRestartWhenWindowAppears = false
      if isDetachedWhileAlive {
        resumeDetachedSession()
      } else {
        startNavigationIfReady()
      }
    } else if window == nil, enabled, keepAliveWhenDetached, navigationViewController != nil {
      pauseForDetach()
    } else if window == nil, enabled {
      windowGraceTeardown.schedule(after: Self.windowGrace) { [weak self] in
        guard let self, self.window == nil else { return }
        self.cleanupNavigation()
      }
    } else {
      cleanupNavigation()
    }
  }

  private func pauseForDetach() {
    guard !isDetachedWhileAlive else { return }
    isDetachedWhileAlive = true
    detachedTeardown.schedule(after: Self.detachedSessionLifetime) { [weak self] in
      guard let self, self.isDetachedWhileAlive, self.window == nil else { return }
      self.endDetachedSession()
      self.dispatchCancelNavigation([:])
    }
  }

  private func resumeDetachedSession() {
    isDetachedWhileAlive = false
    detachedTeardown.cancel()
    guard let viewController = navigationViewController else {
      startNavigationIfReady()
      return
    }
    viewController.view.frame = bounds
    applyCameraPadding()
    resumeCameraFollowingInternal(reason: "reattach")
    if let mainRoute = viewController.mapboxNavigation.tripSession().currentNavigationRoutes?.mainRoute {
      emitRouteChange(navigationRoute: mainRoute)
    }
    lastBannerKey = nil
    if let progress = viewController.mapboxNavigation.navigation().currentRouteProgress?.routeProgress {
      dispatchBannerInstructionIfChanged(for: progress)
    } else if let lastBannerPayload {
      dispatchBannerInstruction(lastBannerPayload)
    }
  }

  private func endDetachedSession() {
    cleanupNavigation()
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

    guard MapboxNavigationSession.isConfigured else {
      dispatchError([
        "code": "MISSING_ACCESS_TOKEN",
        "message": "No Mapbox access token found. Set MBXAccessToken in Info.plist "
          + "(the Expo config plugin does this from EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN)."
      ])
      return
    }

    guard let resolvedOrigin = resolveStartOrigin() else {
      requestOriginFix()
      return
    }
    stopWaitingForOriginFix()
    acceptedOriginFix = nil

    let acquired = NavigationSessionRegistry.shared.acquire(owner: sessionOwner)
      || (NavigationSessionRegistry.shared.requestYieldCurrent()
        && NavigationSessionRegistry.shared.acquire(owner: sessionOwner))
    guard acquired else {
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
    NavigationSessionRegistry.shared.registerYieldHandler(owner: sessionOwner) { [weak self] in
      guard let self, self.isDetachedWhileAlive, self.window == nil else { return false }
      self.endDetachedSession()
      return true
    }
    NavigationSessionRegistry.shared.registerAdvanceLegHandler(owner: sessionOwner) { [weak self] in
      guard let self = self else { return false }
      DispatchQueue.main.async {
        self.advanceToNextLeg()
      }
      return true
    }
    
    let destCoord = CLLocationCoordinate2D(latitude: destLat, longitude: destLng)
    let intermediateWaypoints = parseIntermediateWaypoints()
    let destinationWaypoint = makeDestinationWaypoint(dest, coordinate: destCoord)
    let routeOptions = makeRouteOptions(
      waypoints: [resolvedOrigin.waypoint]
        + intermediateWaypoints
        + [destinationWaypoint]
    )

    let requestToken = UUID()
    routeRequestToken = requestToken
    isRouteCalculationInProgress = true

    Task { @MainActor [weak self] in
      guard let self else { return }

      MapboxNavigationSession.shared.prepareForNavigation(
        language: self.resolvedLanguage,
        distanceUnit: self.distanceUnit,
        continuousAlternatives: self.showsContinuousAlternatives,
        simulatesRoute: self.shouldSimulateRoute,
        routeProfile: self.routeProfile
      )
      let routingProvider = MapboxNavigationSession.shared.mapboxNavigation.routingProvider()

      do {
        let navigationRoutes = try await routingProvider.calculateRoutes(options: routeOptions).value

        guard self.routeRequestToken == requestToken else { return }

        self.isRouteCalculationInProgress = false
        guard self.navigationViewController == nil else { return }
        guard self.enabled, self.window != nil else {
          if self.enabled, self.window == nil {
            self.shouldRestartWhenWindowAppears = true
            self.dispatchError([
              "code": "ROUTE_DISCARDED_NO_WINDOW",
              "message": "A route was calculated while this view was not attached to a window, "
                + "so it could not be shown and was discarded. Navigation will retry "
                + "automatically once the view enters a window. If it never does, the view is "
                + "being mounted inside a container that is never attached (a collapsed sheet, "
                + "an off-screen tab, or a zero-size parent)."
            ])
          }
          NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
          return
        }

        self.routedDestination = destCoord
        self.routedDestinationCurb = !destinationWaypoint.allowsArrivingOnOppositeSide
        self.routedIntermediateWaypoints = intermediateWaypoints
        self.routedWaypointsProp = intermediateWaypoints
        self.emitRouteChange(navigationRoute: navigationRoutes.mainRoute)
        self.embedNavigation(navigationRoutes: navigationRoutes, routeOptions: routeOptions)
      } catch {
        guard self.routeRequestToken == requestToken else { return }
        self.isRouteCalculationInProgress = false
        NavigationSessionRegistry.shared.release(owner: self.sessionOwner)

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
    isRouteCalculationInProgress = true
    defer { isRouteCalculationInProgress = false }
    let navigationOptions = buildNavigationOptions()

    let viewController = EmbeddedNavigationViewController(
      navigationRoutes: navigationRoutes,
      navigationOptions: navigationOptions
    )
    viewController.extraOrnamentBottomMargin = resolvedOrnamentBottomMargin
    viewController.onViewDidAppear = { [weak self, weak viewController] in
      self?.applyCameraPadding()
      if let viewController { self?.applyEmbeddedBannerVisibility(to: viewController) }
    }

    viewController.delegate = self
    attachMapPanDetection(to: viewController)
    
    MapboxNavigationSession.shared.setMuted(mute)
    MapboxNavigationSession.shared.setVoiceVolume(voiceVolume)
    MapboxNavigationSession.legAdvance.set(waypointAdvance != "manual")
    viewController.showsSpeedLimits = showsSpeedLimits
    applySpeedLimitVisibility(to: viewController)
    applyNativeFloatingButtonsConfiguration(to: viewController)
    applyNativeResumeButtonVisibility(to: viewController)
    applyEmbeddedBannerVisibility(to: viewController)
    viewController.showsContinuousAlternatives = showsContinuousAlternatives
    viewController.usesNightStyleWhileInTunnel = usesNightStyleWhileInTunnel
    viewController.routeLineTracksTraversal = routeLineTracksTraversal
    viewController.annotatesIntersectionsAlongRoute = annotatesIntersectionsAlongRoute
    applyInterfaceStyle(to: viewController)
    applyRouteLineColors()
    applyCameraConfiguration(to: viewController)
    
    if let parent = nearestViewController() {
      parent.addChild(viewController)
      addSubview(viewController.view)
      viewController.view.frame = bounds
      viewController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
      viewController.didMove(toParent: parent)
      
      navigationViewController = viewController
      observeStyleLoads()
      applyMapStyle(to: viewController)
      MapboxNavigationTheme.refresh(in: viewController)

      applyRouteLineColors()
      applyMapStyleConfig()
      renderNavigationMarkersIfPossible()
      renderNavigationLinesIfPossible()
      applyLocationPuck(for: .activeNavigation, force: true)
      hostViewController = parent
      armGuidanceWatchdog()
      MapboxNavigationSession.shared.setGuidanceActive(true)
      observeCameraStates(of: viewController)
      setCameraFollowingState(isCameraFollowing, reason: "guidance-started", force: true)
      observeRoutes(of: viewController)
      observeLocationMatching(of: viewController)
      applyCameraPadding()
      needsInPlaceReroute = true
      performInPlaceRerouteIfNeeded()
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

    if shouldRestartWhenWindowAppears,
       enabled,
       window != nil,
       navigationViewController == nil,
       !isRouteCalculationInProgress,
       bounds.width > 0, bounds.height > 0 {
      shouldRestartWhenWindowAppears = false
      startNavigationIfReady()
    }
    applyCameraPadding()
  }
  
  deinit {
    cleanupNavigation()
  }
  
  private func cleanupNavigation() {
    routeRequestToken = UUID()
    isDetachedWhileAlive = false
    detachedTeardown.cancel()
    windowGraceTeardown.cancel()
    lastBannerPayload = nil
    isReportedOffRoute = false
    isRouteCalculationInProgress = false
    clearNavigationMarkers()
    renderedNavigationLineIds.removeAll()
    navigationViewController?.willMove(toParent: nil)
    navigationViewController?.view.removeFromSuperview()
    navigationViewController?.removeFromParent()
    if navigationViewController != nil {
      MapboxNavigationSession.shared.setGuidanceActive(false)
    }
    navigationViewController = nil
    styleLoadedSubscription = nil
    routesSubscription = nil
    cameraStateSubscription = nil
    locationMatchingSubscription = nil
    lastLocationEmitAt = 0
    pendingCameraReason = nil
    hasCameraPanGesture = false
    cancelGuidanceWatchdog()
    hasReceivedRouteProgress = false
    usesEmptyBanners = false
    sdkViewportPadding = nil
    lastEmittedRouteKey = nil
    lastBannerKey = nil
    routedDestination = nil
    routedDestinationCurb = false
    routedIntermediateWaypoints = []
    routedWaypointsProp = []
    needsInPlaceReroute = false
    needsMapStyleUpdate = false
    cameraBeforeStyleSwap = nil
    inPlaceRerouteToken = UUID()
    inPlaceRerouteTask?.cancel()
    inPlaceRerouteTask = nil
    inPlaceRerouteTarget = nil
    inPlaceRerouteRetry?.cancel()
    inPlaceRerouteRetry = nil
    inPlaceRerouteAttempt = 0
    isTargetRerouteFailing = false
    sessionStateSubscription = nil
    stopWaitingForOriginFix()
    acceptedOriginFix = nil
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
      forgetMarkerMotion(markerId)
    }

    for marker in markerPayloads {
      let metrics = resolveNavigationMarkerMetrics(marker.size)
      let picture = marker.image.flatMap(markerImage)

      if let annotation = navigationMarkerAnnotations[marker.id],
         (annotation.view.tag == NavigationMarkerViewTag.image) != (picture != nil) {
        annotation.remove()
        navigationMarkerAnnotations.removeValue(forKey: marker.id)
      }

      if let picture, let annotation = navigationMarkerAnnotations[marker.id] {
        let shown = markerShownAt[marker.id] ?? marker.coordinate
        if let imageView = annotation.view as? UIImageView, imageView.image !== picture {
          imageView.image = picture
          imageView.frame = CGRect(origin: .zero, size: picture.size)
        }
        applyNavigationMarkerOptions(to: annotation, marker: marker, metrics: metrics)
        glideMarker(marker.id, annotation: annotation, from: shown, to: marker.coordinate, animates: marker.animatesMovement)
        continue
      }

      if let picture {
        let annotation = ViewAnnotation(
          annotatedFeature: .geometry(Turf.Point(marker.coordinate)),
          view: makeImageMarkerView(marker, image: picture)
        )
        applyNavigationMarkerOptions(to: annotation, marker: marker, metrics: metrics)
        annotationManager.add(annotation)
        navigationMarkerAnnotations[marker.id] = annotation
        markerShownAt[marker.id] = marker.coordinate
        markerUpdatedAt[marker.id] = CACurrentMediaTime()
        continue
      }

      if let annotation = navigationMarkerAnnotations[marker.id] {
        let shown = markerShownAt[marker.id] ?? marker.coordinate
        bindNavigationMarkerView(annotation.view, marker: marker, metrics: metrics)
        applyNavigationMarkerOptions(to: annotation, marker: marker, metrics: metrics)
        glideMarker(marker.id, annotation: annotation, from: shown, to: marker.coordinate, animates: marker.animatesMovement)
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
      markerShownAt[marker.id] = marker.coordinate
      markerUpdatedAt[marker.id] = CACurrentMediaTime()
    }
  }

  private func glideMarker(
    _ id: String,
    annotation: ViewAnnotation,
    from: CLLocationCoordinate2D,
    to: CLLocationCoordinate2D,
    animates: Bool
  ) {
    let now = CACurrentMediaTime()
    let since = now - (markerUpdatedAt[id] ?? now)
    markerUpdatedAt[id] = now
    let distance = CLLocation(latitude: from.latitude, longitude: from.longitude)
      .distance(from: CLLocation(latitude: to.latitude, longitude: to.longitude))
    guard animates, distance >= 0.3, distance <= Self.markerGlideMaxDistance, since > 0 else {
      markerMotions.removeValue(forKey: id)
      markerShownAt[id] = to
      annotation.annotatedFeature = .geometry(Turf.Point(to))
      return
    }
    annotation.annotatedFeature = .geometry(Turf.Point(from))
    markerShownAt[id] = from
    markerMotions[id] = MarkerMotion(from: from, to: to, start: now, duration: min(max(since, 0.3), 3))
    if markerDisplayLink == nil {
      let link = CADisplayLink(target: self, selector: #selector(stepMarkerMotions(_:)))
      link.preferredFrameRateRange = CAFrameRateRange(minimum: 15, maximum: 30, preferred: 30)
      link.add(to: .main, forMode: .common)
      markerDisplayLink = link
    }
  }

  @objc
  private func stepMarkerMotions(_ link: CADisplayLink) {
    let now = CACurrentMediaTime()
    for (id, motion) in markerMotions {
      guard let annotation = navigationMarkerAnnotations[id] else {
        markerMotions.removeValue(forKey: id)
        continue
      }
      let t = min(1, max(0, (now - motion.start) / motion.duration))
      let point = CLLocationCoordinate2D(
        latitude: motion.from.latitude + (motion.to.latitude - motion.from.latitude) * t,
        longitude: motion.from.longitude + (motion.to.longitude - motion.from.longitude) * t
      )
      annotation.annotatedFeature = .geometry(Turf.Point(point))
      markerShownAt[id] = point
      if t >= 1 { markerMotions.removeValue(forKey: id) }
    }
    if markerMotions.isEmpty { stopMarkerDisplayLink() }
  }

  private func stopMarkerDisplayLink() {
    markerDisplayLink?.invalidate()
    markerDisplayLink = nil
  }

  private func forgetMarkerMotion(_ id: String) {
    markerMotions.removeValue(forKey: id)
    markerShownAt.removeValue(forKey: id)
    markerUpdatedAt.removeValue(forKey: id)
    if markerMotions.isEmpty { stopMarkerDisplayLink() }
  }

  private static func navigationLineSourceId(_ id: String) -> String { "rnmbn-line-\(id)" }
  private static func navigationLineLayerId(_ id: String) -> String { "rnmbn-line-\(id)-layer" }

  private struct NavigationLinePayload {
    let id: String
    let coordinates: [CLLocationCoordinate2D]
    let color: UIColor
    let width: Double
    let opacity: Double
    let dash: [Double]?
  }

  private func parseNavigationLine(_ value: [String: Any]) -> NavigationLinePayload? {
    guard let rawId = value["id"] as? String else { return nil }
    let id = rawId.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !id.isEmpty else { return nil }
    let points = (value["coordinates"] as? [[String: Any]] ?? []).compactMap { point -> CLLocationCoordinate2D? in
      guard let latitude = (point["latitude"] as? NSNumber)?.doubleValue,
            let longitude = (point["longitude"] as? NSNumber)?.doubleValue,
            latitude.isFinite, longitude.isFinite else { return nil }
      return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
    let width = (value["width"] as? NSNumber)?.doubleValue ?? 4
    let opacity = ((value["opacity"] as? NSNumber)?.doubleValue ?? 1).clamped(to: 0...1)
    let dash = (value["dash"] as? [NSNumber])?.map(\.doubleValue).filter { $0.isFinite && $0 >= 0 }
    return NavigationLinePayload(
      id: id,
      coordinates: points,
      color: HexColor.parse(value["color"]) ?? UIColor.systemBlue,
      width: width.isFinite && width > 0 ? width : 4,
      opacity: opacity,
      dash: (dash?.count ?? 0) >= 2 ? dash : nil
    )
  }

  private func renderNavigationLinesIfPossible() {
    guard let mapboxMap = currentNavigationMapView()?.mapboxMap, mapboxMap.isStyleLoaded else { return }
    let payloads = (navigationLines ?? []).compactMap(parseNavigationLine).filter { $0.coordinates.count >= 2 }
    let nextIds = Set(payloads.map(\.id))

    for id in renderedNavigationLineIds where !nextIds.contains(id) {
      try? mapboxMap.removeLayer(withId: Self.navigationLineLayerId(id))
      try? mapboxMap.removeSource(withId: Self.navigationLineSourceId(id))
    }
    renderedNavigationLineIds = renderedNavigationLineIds.intersection(nextIds)

    for line in payloads {
      let sourceId = Self.navigationLineSourceId(line.id)
      let layerId = Self.navigationLineLayerId(line.id)
      let feature = Feature(geometry: .lineString(LineString(line.coordinates)))
      if mapboxMap.sourceExists(withId: sourceId) {
        mapboxMap.updateGeoJSONSource(withId: sourceId, geoJSON: .feature(feature))
      } else {
        var source = GeoJSONSource(id: sourceId)
        source.data = .feature(feature)
        do {
          try mapboxMap.addSource(source)
        } catch {
          continue
        }
      }
      let style: (inout LineLayer) -> Void = { layer in
        layer.lineColor = .constant(StyleColor(line.color))
        layer.lineWidth = .constant(line.width)
        layer.lineOpacity = .constant(line.opacity)
        layer.lineCap = .constant(.round)
        layer.lineJoin = .constant(.round)
        layer.lineDasharray = line.dash.map { .constant($0) }
      }
      if mapboxMap.layerExists(withId: layerId) {
        try? mapboxMap.updateLayer(withId: layerId, type: LineLayer.self, update: style)
      } else {
        var layer = LineLayer(id: layerId, source: sourceId)
        style(&layer)
        let puckLayer = ["puck-model-layer", "puck"].first(where: mapboxMap.layerExists(withId:))
        try? mapboxMap.addLayer(layer, layerPosition: puckLayer.map { .below($0) })
      }
      renderedNavigationLineIds.insert(line.id)
    }
  }

  private func clearNavigationMarkers() {
    for annotation in navigationMarkerAnnotations.values {
      annotation.remove()
    }
    navigationMarkerAnnotations.removeAll()
    markerMotions.removeAll()
    markerShownAt.removeAll()
    markerUpdatedAt.removeAll()
    stopMarkerDisplayLink()
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
    let variant = NavigationMarkerOptions.variant(value["variant"] as? String)
    let customColor = HexColor.parse(value["color"])
    let customBadgeColor = HexColor.parse(value["badgeColor"])
    let customOpacity = (value["opacity"] as? NSNumber).map { CGFloat($0.doubleValue).clamped(to: 0...1) }
    let size = NavigationMarkerOptions.size(value["size"] as? String)
    let markerStyle = NavigationMarkerOptions.style(value["markerStyle"] as? String)
    let showTail = (value["showTail"] as? Bool) ?? (markerStyle == "pin")
    let selected = (value["selected"] as? Bool) ?? (variant == "primary" || variant == "success")
    let allowOverlap = (value["allowOverlap"] as? Bool) ?? true
    let anchorOffsetY = (value["anchorOffsetY"] as? NSNumber).map { CGFloat($0.doubleValue) }
    let image = (value["image"] as? [String: Any]).flatMap { raw -> NavigationMarkerImage? in
      guard let uri = raw["uri"] as? String, !uri.isEmpty else { return nil }
      let scale = (raw["scale"] as? NSNumber).map { CGFloat($0.doubleValue) } ?? UIScreen.main.scale
      return NavigationMarkerImage(uri: uri, scale: max(scale, 1))
    }

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
      anchorOffsetY: anchorOffsetY,
      image: image,
      animatesMovement: (value["animatesMovement"] as? Bool) ?? false
    )
  }

  private func markerImage(_ image: NavigationMarkerImage) -> UIImage? {
    if let cached = NavigationMarkerImageLoader.cached(image) { return cached }
    NavigationMarkerImageLoader.load(image) { [weak self] in
      self?.renderNavigationMarkersIfPossible()
    }
    return nil
  }

  private func makeImageMarkerView(_ marker: NavigationMarkerPayload, image: UIImage) -> UIView {
    let view = UIImageView(image: image)
    view.tag = NavigationMarkerViewTag.image
    view.frame = CGRect(origin: .zero, size: image.size)
    view.contentMode = .scaleAspectFit
    view.isAccessibilityElement = true
    view.accessibilityLabel = marker.label ?? marker.id
    view.accessibilityIdentifier = marker.id
    return view
  }

  private func applyNavigationMarkerOptions(
    to annotation: ViewAnnotation,
    marker: NavigationMarkerPayload,
    metrics: NavigationMarkerMetrics
  ) {
    let offsetY = marker.anchorOffsetY ?? metrics.offsetY
    annotation.annotatedFeature = .geometry(Turf.Point(marker.coordinate))
    annotation.allowOverlap = marker.allowOverlap
    annotation.visible = true
    annotation.ignoreCameraPadding = true
    annotation.allowOverlapWithPuck = true
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

  private func bannerInstructionPayload(for progress: RouteProgress) -> [String: Any] {
    let legProgress = progress.currentLegProgress
    let stepProgress = legProgress.currentStepProgress
    let visual = stepProgress.currentVisualInstruction
    let primary = visual?.primaryInstruction
    let upcoming = legProgress.upcomingStep

    var payload: [String: Any] = [
      "primaryText": primary?.text ?? upcoming?.instructions ?? legProgress.currentStep.instructions,
      "stepDistanceRemaining": stepProgress.distanceRemaining
    ]
    if let secondary = visual?.secondaryInstruction?.text, !secondary.isEmpty {
      payload["secondaryText"] = secondary
    }
    if let type = primary?.maneuverType ?? upcoming?.maneuverType {
      payload["maneuverType"] = type.rawValue
    }
    if let direction = primary?.maneuverDirection ?? upcoming?.maneuverDirection {
      payload["maneuverModifier"] = direction.rawValue
    }
    if let degrees = primary?.finalHeading {
      payload["maneuverDegrees"] = degrees
    }
    if let drivingSide = visual?.drivingSide ?? upcoming?.drivingSide {
      payload["drivingSide"] = drivingSide.rawValue
    }
    let maneuverType = (primary?.maneuverType ?? upcoming?.maneuverType)?.rawValue ?? ""
    if maneuverType.contains("roundabout") || maneuverType.contains("rotary"),
       let exit = upcoming?.exitIndex, exit > 0 {
      payload["exitNumber"] = exit
    }
    let lanes = Self.lanePayload(visual?.tertiaryInstruction)
    if !lanes.isEmpty {
      payload["lanes"] = lanes
    }
    if let nextManeuver = Self.nextManeuverPayload(for: legProgress) {
      payload["nextManeuver"] = nextManeuver
    }
    return payload
  }

  private static func lanePayload(_ instruction: VisualInstruction?) -> [[String: Any]] {
    guard let instruction else { return [] }
    return instruction.components.compactMap { component in
      guard case let .lane(indications, isUsable, preferredDirection) = component else { return nil }
      var lane: [String: Any] = [
        "indications": indications.descriptions,
        "active": isUsable
      ]
      if isUsable, let preferredDirection {
        lane["activeDirection"] = preferredDirection.rawValue
      }
      return lane
    }
  }

  private static func nextManeuverPayload(for legProgress: RouteLegProgress) -> [String: Any]? {
    guard let upcoming = legProgress.upcomingStep, let followOn = legProgress.followOnStep else { return nil }
    let instruction = upcoming.instructionsDisplayedAlongStep?.first?.primaryInstruction
    let text = instruction?.text ?? followOn.instructions
    guard !text.isEmpty else { return nil }
    var payload: [String: Any] = [
      "primaryText": text,
      "distance": upcoming.distance
    ]
    payload["maneuverType"] = (instruction?.maneuverType ?? followOn.maneuverType).rawValue
    if let direction = instruction?.maneuverDirection ?? followOn.maneuverDirection {
      payload["maneuverModifier"] = direction.rawValue
    }
    if let degrees = instruction?.finalHeading {
      payload["maneuverDegrees"] = degrees
    }
    return payload
  }

  private static func bannerExtrasKey(_ payload: [String: Any]) -> String {
    let lanes = (payload["lanes"] as? [[String: Any]] ?? []).map { lane in
      let indications = (lane["indications"] as? [String] ?? []).joined(separator: "+")
      let active = (lane["active"] as? Bool ?? false) ? "1" : "0"
      return "\(indications)/\(active)/\(lane["activeDirection"] as? String ?? "")"
    }.joined(separator: ",")
    var nextManeuver = ""
    if let next = payload["nextManeuver"] as? [String: Any] {
      nextManeuver = [
        next["primaryText"] as? String ?? "",
        next["maneuverType"] as? String ?? "",
        next["maneuverModifier"] as? String ?? "",
        String(Int(((next["distance"] as? Double) ?? 0).rounded()))
      ].joined(separator: "|")
    }
    let exit = (payload["exitNumber"] as? Int).map(String.init) ?? ""
    return [lanes, nextManeuver, exit, payload["drivingSide"] as? String ?? ""].joined(separator: "\u{1E}")
  }

  private func dispatchBannerInstruction(_ payload: [String: Any]) {
    onBannerInstruction(payload)
    MapboxNavigationEventBridge.shared.emit("onBannerInstruction", payload)
  }

  private func dispatchBannerInstructionIfChanged(for progress: RouteProgress) {
    let payload = bannerInstructionPayload(for: progress)
    let distance = (payload["stepDistanceRemaining"] as? CLLocationDistance) ?? 0
    let degrees = (payload["maneuverDegrees"] as? Double).map { String(Int($0.rounded())) } ?? ""
    let key = [
      payload["primaryText"] as? String ?? "",
      payload["secondaryText"] as? String ?? "",
      payload["maneuverType"] as? String ?? "",
      payload["maneuverModifier"] as? String ?? "",
      degrees,
      Self.bannerExtrasKey(payload)
    ].joined(separator: "\u{1F}")
    let now = CACurrentMediaTime()

    if key == lastBannerKey {
      guard abs(distance - lastBannerDistance) >= 10, now - lastBannerEmitAt >= 0.25 else { return }
    }
    lastBannerKey = key
    lastBannerDistance = distance
    lastBannerEmitAt = now
    lastBannerPayload = payload
    dispatchBannerInstruction(payload)
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
  static let matchedLocationMaxDrift: CLLocationDistance = 30

  static func reportedLocation(matched: CLLocation, raw: CLLocation) -> CLLocation {
    guard CLLocationCoordinate2DIsValid(raw.coordinate), raw.horizontalAccuracy >= 0 else {
      return matched
    }
    let allowed = max(matchedLocationMaxDrift, raw.horizontalAccuracy * 2)
    return matched.distance(from: raw) > allowed ? raw : matched
  }

  private func observeLocationMatching(of viewController: NavigationViewController) {
    locationMatchingSubscription = viewController.mapboxNavigation.navigation().locationMatching
      .receive(on: DispatchQueue.main)
      .sink { [weak self, weak viewController] matching in
        guard let self, let viewController else { return }
        self.emitLocation(matching, from: viewController)
      }
  }

  private func emitLocation(_ matching: MapMatchingState, from viewController: NavigationViewController) {
    let now = CACurrentMediaTime()
    let throttleSeconds = max(0, min(eventThrottleMs, 10_000)) / 1000
    guard throttleSeconds <= 0 || now - lastLocationEmitAt >= throttleSeconds else { return }
    lastLocationEmitAt = now

    let matched = matching.enhancedLocation
    let location = Self.reportedLocation(matched: matched, raw: matching.location)
    var payload: [String: Any] = [
      "latitude": location.coordinate.latitude,
      "longitude": location.coordinate.longitude,
      "bearing": location.course,
      "speed": location.speed,
      "altitude": location.altitude,
      "accuracy": location.horizontalAccuracy,
      "timestamp": location.timestamp.timeIntervalSince1970 * 1000,
      "matched": location === matched
    ]
    let legProgress = viewController.mapboxNavigation.navigation().currentRouteProgress?
      .routeProgress.currentLegProgress
    let limit = matching.speedLimit.value ?? legProgress?.currentSpeedLimit
    if let sign = limit.flatMap(Self.speedLimitSign) {
      payload["speedLimit"] = sign
    }
    if let roadName = matching.roadName?.text.trimmingCharacters(in: .whitespacesAndNewlines),
       !roadName.isEmpty {
      payload["roadName"] = roadName
    }
    dispatchLocationChange(payload)
  }

  private static func speedLimitSign(_ limit: Measurement<UnitSpeed>) -> [String: Any]? {
    let unit: UnitSpeed = limit.unit == .milesPerHour ? .milesPerHour : .kilometersPerHour
    let value = limit.converted(to: unit).value
    guard value.isFinite, value > 0, value < 300 else { return nil }
    return ["value": Int(value.rounded()), "unit": unit == .milesPerHour ? "mph" : "km/h"]
  }


  private func reportBackOnRoute() {
    guard isReportedOffRoute else { return }
    isReportedOffRoute = false
    dispatchOffRoute(["offRoute": false, "reason": isTargetRerouteFailing ? "destinationChange" : "deviation"])
  }

  private func advanceToNextLeg() {
    guard let navigationViewController else { return }
    let navigation = navigationViewController.mapboxNavigation.navigation()
    guard let progress = navigation.currentRouteProgress?.routeProgress else { return }
    let legCount = progress.route.legs.count
    guard progress.legIndex < legCount - 1 else { return }
    navigation.switchLeg(newLegIndex: progress.legIndex + 1)
  }

  private func applyMapStyle(to viewController: NavigationViewController) {
    guard let styleManager = viewController.styleManager else { return }
    defer { applyRouteLineColors(redraw: true) }
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

  private func scheduleMapStyleUpdate() {
    guard navigationViewController != nil else { return }
    needsMapStyleUpdate = true
    DispatchQueue.main.async { [weak self] in
      self?.applyMapStyleIfNeeded()
    }
  }

  private func applyMapStyleIfNeeded() {
    guard needsMapStyleUpdate else { return }
    needsMapStyleUpdate = false
    guard let viewController = navigationViewController,
          let styleManager = viewController.styleManager,
          let navigationMapView = viewController.navigationMapView else { return }

    let dayURL = normalizedStyleURL(primary: mapStyleUriDay, fallback: mapStyleUri)
    let nightURL = normalizedStyleURL(primary: mapStyleUriNight, fallback: mapStyleUriDay ?? mapStyleUri)
      ?? dayURL

    for style in styleManager.styles {
      let requested: URL?
      switch style.styleType {
      case .night: requested = nightURL
      default: requested = dayURL
      }
      style.mapStyleURL = requested ?? type(of: style).init().mapStyleURL
    }

    let current = styleManager.currentStyle ?? styleManager.styles.first
    cameraBeforeStyleSwap = CameraOptions(cameraState: navigationMapView.mapView.mapboxMap.cameraState)
    current?.applyMapStyle(to: navigationMapView)
  }

  private func restoreCameraAfterStyleSwap() {
    guard let camera = cameraBeforeStyleSwap else { return }
    cameraBeforeStyleSwap = nil
    navigationViewController?.navigationMapView?.mapView.mapboxMap.setCamera(to: camera)
  }

  private func parsedCameraPadding() -> UIEdgeInsets? {
    guard let cameraPadding else { return nil }
    func value(_ key: String) -> CGFloat {
      guard let number = (cameraPadding[key] as? NSNumber)?.doubleValue, number.isFinite else { return 0 }
      return CGFloat(max(0, number))
    }
    return UIEdgeInsets(top: value("top"), left: value("left"), bottom: value("bottom"), right: value("right"))
  }

  private func applyCameraPadding() {
    guard let navigationMapView = navigationViewController?.navigationMapView else { return }
    guard let requested = parsedCameraPadding() else {
      if let sdkViewportPadding {
        navigationMapView.viewportPadding = sdkViewportPadding
        self.sdkViewportPadding = nil
      }
      return
    }
    let safeArea = navigationMapView.safeAreaInsets
    let padding = UIEdgeInsets(
      top: max(0, requested.top - safeArea.top),
      left: max(0, requested.left - safeArea.left),
      bottom: max(0, requested.bottom - safeArea.bottom),
      right: max(0, requested.right - safeArea.right)
    )
    guard navigationMapView.viewportPadding != padding else { return }
    if sdkViewportPadding == nil {
      sdkViewportPadding = navigationMapView.viewportPadding
    }
    navigationMapView.viewportPadding = padding
  }

  private struct RouteOrigin {
    let coordinate: CLLocationCoordinate2D
    let heading: CLLocationDirection?

    init(coordinate: CLLocationCoordinate2D, heading: CLLocationDirection? = nil) {
      self.coordinate = coordinate
      self.heading = heading
    }

    init(location: CLLocation) {
      self.init(
        coordinate: location.coordinate,
        heading: MapboxNavigationView.travelHeading(of: location)
      )
    }

    var waypoint: Waypoint {
      var waypoint = Waypoint(coordinate: coordinate)
      if let heading {
        waypoint.heading = heading
        waypoint.headingAccuracy = 45
      }
      return waypoint
    }
  }

  private static func travelHeading(of location: CLLocation) -> CLLocationDirection? {
    guard location.course >= 0, location.speed > headingMinimumSpeed else { return nil }
    return location.course
  }

  private static func isGoodOriginFix(_ location: CLLocation) -> Bool {
    location.horizontalAccuracy >= 0
      && location.horizontalAccuracy <= originMaxAccuracy
      && abs(location.timestamp.timeIntervalSinceNow) < originMaxAge
      && CLLocationCoordinate2DIsValid(location.coordinate)
  }

  private func resolveStartOrigin() -> RouteOrigin? {
    if let origin = startOrigin,
       let latitude = (origin["latitude"] as? NSNumber)?.doubleValue,
       let longitude = (origin["longitude"] as? NSNumber)?.doubleValue,
       CLLocationCoordinate2DIsValid(CLLocationCoordinate2D(latitude: latitude, longitude: longitude)) {
      return RouteOrigin(coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude))
    }

    if let accepted = acceptedOriginFix,
       abs(accepted.timestamp.timeIntervalSinceNow) < 30,
       CLLocationCoordinate2DIsValid(accepted.coordinate) {
      return RouteOrigin(location: accepted)
    }

    if let cached = originLocationManager.location, Self.isGoodOriginFix(cached) {
      return RouteOrigin(location: cached)
    }
    return nil
  }

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
      beginOriginUpdates()
    default:
      dispatchError([
        "code": "LOCATION_PERMISSION_REQUIRED",
        "message": "Embedded navigation needs location permission, or an explicit startOrigin."
      ])
    }
  }

  private func beginOriginUpdates() {
    bestOriginFix = nil
    hasOriginWaitTimedOut = false
    originLocationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
    originLocationManager.startUpdatingLocation()

    originWaitTimeout?.cancel()
    let timeout = DispatchWorkItem { [weak self] in
      guard let self, self.isWaitingForOriginFix else { return }
      self.hasOriginWaitTimedOut = true
      if let best = self.bestOriginFix {
        self.acceptOriginFix(best)
      }
    }
    originWaitTimeout = timeout
    DispatchQueue.main.asyncAfter(deadline: .now() + Self.originWaitSeconds, execute: timeout)

    originNoFixReport?.cancel()
    let noFix = DispatchWorkItem { [weak self] in
      guard let self, self.isWaitingForOriginFix, self.bestOriginFix == nil else { return }
      self.originNoFixReport = nil
      let seconds = Int(Self.guidanceWatchdogTimeout)
      self.dispatchError([
        "code": "NO_LOCATION_FIX",
        "message": "No startOrigin was given and no location fix arrived within \(seconds)s, "
          + "so no route can be requested yet. Still waiting. On a simulator or emulator, set "
          + "a location; otherwise pass startOrigin or shouldSimulateRoute."
      ])
    }
    originNoFixReport = noFix
    DispatchQueue.main.asyncAfter(deadline: .now() + Self.guidanceWatchdogTimeout, execute: noFix)
  }

  private func handleOriginFixes(_ locations: [CLLocation]) {
    for location in locations where location.horizontalAccuracy >= 0
      && CLLocationCoordinate2DIsValid(location.coordinate) {
      if let best = bestOriginFix, best.horizontalAccuracy <= location.horizontalAccuracy {
        continue
      }
      bestOriginFix = location
    }
    guard let best = bestOriginFix else { return }
    if Self.isGoodOriginFix(best) || hasOriginWaitTimedOut {
      acceptOriginFix(best)
    }
  }

  private func acceptOriginFix(_ location: CLLocation) {
    acceptedOriginFix = location
    stopWaitingForOriginFix()
    startNavigationIfReady()
  }

  private func stopWaitingForOriginFix() {
    originWaitTimeout?.cancel()
    originWaitTimeout = nil
    originNoFixReport?.cancel()
    originNoFixReport = nil
    bestOriginFix = nil
    hasOriginWaitTimedOut = false
    guard isWaitingForOriginFix else { return }
    isWaitingForOriginFix = false
    originLocationManager.stopUpdatingLocation()
  }


  private var resolvedLanguage: String {
    let trimmed = language.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? (Locale.preferredLanguages.first ?? "en") : trimmed
  }

  private func parseIntermediateWaypoints() -> [Waypoint] {
    (waypoints ?? []).compactMap { wp in
      guard let lat = (wp["latitude"] as? NSNumber)?.doubleValue,
            let lng = (wp["longitude"] as? NSNumber)?.doubleValue else {
        return nil
      }
      var waypoint = Waypoint(
        coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng),
        name: wp["name"] as? String
      )
      Self.applyApproach(wp, to: &waypoint)
      return waypoint
    }
  }

  private func makeDestinationWaypoint(
    _ dest: [String: Any],
    coordinate: CLLocationCoordinate2D
  ) -> Waypoint {
    let name = (dest["name"] as? String) ?? (dest["title"] as? String) ?? "Destination"
    var waypoint = Waypoint(coordinate: coordinate, name: name)
    Self.applyApproach(dest, to: &waypoint)
    return waypoint
  }

  private static func applyApproach(_ value: [String: Any], to waypoint: inout Waypoint) {
    let approach = (value["approach"] as? String)?
      .trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    waypoint.allowsArrivingOnOppositeSide = approach != "curb"
  }

  private func parseDestinationCoordinate() -> CLLocationCoordinate2D? {
    guard let dest = destination,
          let lat = (dest["latitude"] as? NSNumber)?.doubleValue,
          let lng = (dest["longitude"] as? NSNumber)?.doubleValue else {
      return nil
    }
    let coordinate = CLLocationCoordinate2D(latitude: lat, longitude: lng)
    return CLLocationCoordinate2DIsValid(coordinate) ? coordinate : nil
  }

  private func makeRouteOptions(waypoints: [Waypoint]) -> NavigationRouteOptions {
    let imperial = distanceUnit == "imperial"
    let locale = Locale(identifier: resolvedLanguage)
    let routeOptions = NavigationRouteOptions(
      waypoints: waypoints,
      profileIdentifier: resolveProfileIdentifier(),
      locale: locale,
      distanceUnit: imperial ? .mile : .kilometer
    )
    routeOptions.locale = locale
    routeOptions.unitMeasurementSystem = distanceUnit == "imperial" ? .imperial : .metric
    routeOptions.includesAlternativeRoutes = routeAlternatives
    applyRouteExclusions(to: routeOptions)
    applyVehicleConstraints(to: routeOptions)
    return routeOptions
  }

  private func handleRouteTargetChange() {
    guard navigationViewController != nil else {
      if isRouteCalculationInProgress {
        needsInPlaceReroute = true
      }
      startNavigationIfReady()
      return
    }
    needsInPlaceReroute = true
    DispatchQueue.main.async { [weak self] in
      self?.performInPlaceRerouteIfNeeded()
    }
  }

  func didUpdateProps() {
    performInPlaceRerouteIfNeeded()
    applyMapStyleIfNeeded()
  }

  private func performInPlaceRerouteIfNeeded(isRetry: Bool = false) {
    guard needsInPlaceReroute else { return }
    guard let viewController = navigationViewController else { return }
    needsInPlaceReroute = false
    guard enabled, let dest = destination, let destCoord = parseDestinationCoordinate() else { return }

    let requestedIntermediates = parseIntermediateWaypoints()
    let destinationWaypoint = makeDestinationWaypoint(dest, coordinate: destCoord)
    let destinationCurb = !destinationWaypoint.allowsArrivingOnOppositeSide
    let target = InPlaceRerouteTarget(
      destination: destCoord,
      curb: destinationCurb,
      waypoints: requestedIntermediates
    )
    let waypointsChanged = !RouteTarget.isSameWaypointList(requestedIntermediates, routedWaypointsProp)
      || destinationCurb != routedDestinationCurb

    if let routed = routedDestination, RouteTarget.isSamePlace(routed, destCoord), !waypointsChanged {
      abandonInPlaceReroute()
      return
    }

    if let pending = inPlaceRerouteTarget {
      if pending.matches(target), !isRetry { return }
      if !pending.matches(target) { inPlaceRerouteAttempt = 0 }
    }
    inPlaceRerouteRetry?.cancel()
    inPlaceRerouteRetry = nil
    inPlaceRerouteToken = UUID()
    inPlaceRerouteTask?.cancel()
    inPlaceRerouteTask = nil
    inPlaceRerouteTarget = target

    let navigation = viewController.mapboxNavigation
    guard let matching = navigation.navigation().currentLocationMatching else {
      needsInPlaceReroute = true
      let poll = DispatchWorkItem { [weak self] in
        guard let self else { return }
        self.inPlaceRerouteRetry = nil
        self.performInPlaceRerouteIfNeeded(isRetry: true)
      }
      inPlaceRerouteRetry = poll
      DispatchQueue.main.asyncAfter(deadline: .now() + 1, execute: poll)
      return
    }

    let remainingIntermediates: [Waypoint]
    if waypointsChanged {
      remainingIntermediates = requestedIntermediates
    } else {
      let legIndex = navigation.navigation().currentRouteProgress?.routeProgress.legIndex ?? 0
      remainingIntermediates = Array(routedIntermediateWaypoints.dropFirst(legIndex))
    }

    let origin = RouteOrigin(location: matching.enhancedLocation)
    let routeOptions = makeRouteOptions(
      waypoints: [origin.waypoint]
        + remainingIntermediates
        + [destinationWaypoint]
    )

    let token = UUID()
    inPlaceRerouteToken = token
    let task = navigation.routingProvider().calculateRoutes(options: routeOptions)
    inPlaceRerouteTask = task

    Task { @MainActor [weak self] in
      let result = await task.result
      guard let self,
            self.inPlaceRerouteToken == token,
            self.navigationViewController === viewController,
            self.enabled else { return }
      self.inPlaceRerouteTask = nil

      switch result {
      case .success(let navigationRoutes):
        self.routedDestination = destCoord
        self.routedDestinationCurb = destinationCurb
        self.routedIntermediateWaypoints = remainingIntermediates
        self.routedWaypointsProp = requestedIntermediates
        self.inPlaceRerouteTarget = nil
        self.inPlaceRerouteAttempt = 0
        self.isTargetRerouteFailing = false
        navigation.tripSession().startActiveGuidance(with: navigationRoutes, startLegIndex: 0)
        self.emitRouteChange(navigationRoute: navigationRoutes.mainRoute)
        self.reportBackOnRoute()
      case .failure(let error):
        if error is CancellationError { return }
        var retryable = true
        if case DirectionsError.unableToRoute = error { retryable = false }
        self.handleInPlaceRerouteFailure(
          "Could not route to the new destination; keeping the current route. "
            + error.localizedDescription,
          retryable: retryable
        )
      }
    }
  }

  private func handleInPlaceRerouteFailure(_ message: String, retryable: Bool = true) {
    if inPlaceRerouteAttempt == 0 {
      dispatchError([
        "code": "REROUTE_FAILED",
        "message": message
      ])
    }
    if !isTargetRerouteFailing {
      isTargetRerouteFailing = true
      isReportedOffRoute = true
      dispatchOffRoute(["offRoute": true, "rerouteFailed": true, "reason": "destinationChange"])
    }
    guard retryable else { return }
    let delays = Self.inPlaceRerouteRetryDelays
    let delay = delays[min(inPlaceRerouteAttempt, delays.count - 1)]
    inPlaceRerouteAttempt += 1
    inPlaceRerouteRetry?.cancel()
    let retry = DispatchWorkItem { [weak self] in
      guard let self else { return }
      self.inPlaceRerouteRetry = nil
      guard self.enabled,
            self.navigationViewController != nil,
            self.inPlaceRerouteTarget != nil,
            self.inPlaceRerouteTask == nil else { return }
      self.needsInPlaceReroute = true
      self.performInPlaceRerouteIfNeeded(isRetry: true)
    }
    inPlaceRerouteRetry = retry
    DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: retry)
  }

  private func abandonInPlaceReroute() {
    inPlaceRerouteRetry?.cancel()
    inPlaceRerouteRetry = nil
    if inPlaceRerouteTarget != nil {
      inPlaceRerouteToken = UUID()
      inPlaceRerouteTask?.cancel()
      inPlaceRerouteTask = nil
      inPlaceRerouteTarget = nil
    }
    inPlaceRerouteAttempt = 0
    if isTargetRerouteFailing {
      isTargetRerouteFailing = false
      reportBackOnRoute()
    }
  }


  private func armGuidanceWatchdog() {
    cancelGuidanceWatchdog()
    guard !hasReceivedRouteProgress else { return }

    let work = DispatchWorkItem { [weak self] in
      guard let self else { return }
      guard self.navigationViewController != nil, !self.hasReceivedRouteProgress else { return }
      self.guidanceWatchdog = nil
      let seconds = Int(MapboxNavigationView.guidanceWatchdogTimeout)

      let fix = self.originLocationManager.location
      let fixAge = fix.map { Date().timeIntervalSince($0.timestamp) }
      if let fixAge, fixAge <= MapboxNavigationView.guidanceWatchdogTimeout {
        let age = Int(fixAge)
        self.dispatchError([
          "code": "GUIDANCE_NOT_PROGRESSING",
          "message": "Guidance started but produced no progress in \(seconds)s, "
            + "although the device has a position (fix is \(age)s old). "
            + "The navigator is most likely not in active guidance: check for a second "
            + "MapboxNavigationView mounted at the same time (only one embedded session "
            + "is supported, see NAVIGATION_SESSION_CONFLICT), and for "
            + "\"billing session is not running\" in the Mapbox log."
        ])
      } else {
        self.dispatchError([
          "code": "NO_LOCATION_FIX",
          "message": "Guidance started but no location fix arrived within \(seconds)s, "
            + "so the navigator stayed paused. "
            + "Check that location services are enabled and the simulator has a location set "
            + "(Features > Location, or `xcrun simctl location <udid> start ...`), "
            + "or pass `shouldSimulateRoute` to replay the route without device fixes."
        ])
      }
    }
    guidanceWatchdog = work
    DispatchQueue.main.asyncAfter(
      deadline: .now() + MapboxNavigationView.guidanceWatchdogTimeout,
      execute: work
    )
  }

  private func cancelGuidanceWatchdog() {
    guidanceWatchdog?.cancel()
    guidanceWatchdog = nil
  }



  func restartRouteRequestIfNeeded() {
    guard navigationViewController != nil else {
      startNavigationIfReady()
      return
    }
    cleanupNavigation()
    startNavigationIfReady()
  }

  private func resolveProfileIdentifier() -> ProfileIdentifier {
    switch routeProfile.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "driving":
      return .automobile
    case "walking":
      return .walking
    case "cycling":
      return .cycling
    case "driving-traffic", "":
      return .automobileAvoidingTraffic
    default:
      dispatchError([
        "code": "INVALID_ROUTE_PROFILE",
        "message": "Unknown routeProfile '\(routeProfile)'. Falling back to driving-traffic."
      ])
      return .automobileAvoidingTraffic
    }
  }

  private var isDrivingProfile: Bool {
    let identifier = resolveProfileIdentifier()
    return identifier == .automobile || identifier == .automobileAvoidingTraffic
  }

  private func applyRouteExclusions(to routeOptions: NavigationRouteOptions) {
    guard let routeExclusions else { return }

    if let rawClasses = routeExclusions["roadClasses"] as? [String] {
      var classes: RoadClasses = []
      var dropped: [String] = []
      for raw in rawClasses {
        let name = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if !isDrivingProfile, name != "ferry", name != "cashOnlyTolls" {
          dropped.append(name)
          continue
        }
        switch name {
        case "toll": classes.insert(.toll)
        case "motorway": classes.insert(.motorway)
        case "ferry": classes.insert(.ferry)
        case "tunnel": classes.insert(.tunnel)
        case "restricted": classes.insert(.restricted)
        case "unpaved": classes.insert(.unpaved)
        case "cashOnlyTolls": classes.insert(.cashTollOnly)
        default:
          dispatchError([
            "code": "INVALID_ROAD_CLASS",
            "message": "Unknown routeExclusions.roadClasses entry '\(raw)'. Ignored."
          ])
        }
      }
      if !dropped.isEmpty {
        dispatchError([
          "code": "ROAD_CLASS_NOT_SUPPORTED_BY_PROFILE",
          "message": "The '\(routeProfile)' profile only supports excluding ferry and " +
            "cashOnlyTolls, so \(dropped.sorted().joined(separator: ", ")) " +
            "\(dropped.count == 1 ? "was" : "were") dropped. Sending them would have " +
            "failed the route request."
        ])
      }
      routeOptions.roadClassesToAvoid = classes
    }

    if let rawLocations = routeExclusions["locations"] as? [[String: Any]], !rawLocations.isEmpty {
      guard isDrivingProfile else {
        dispatchError([
          "code": "EXCLUDED_LOCATIONS_NOT_SUPPORTED_BY_PROFILE",
          "message": "routeExclusions.locations is only supported on the driving and " +
            "driving-traffic profiles, so it was dropped for '\(routeProfile)'."
        ])
        return
      }
      var coordinates: [CLLocationCoordinate2D] = []
      for entry in rawLocations {
        guard let latitude = entry["latitude"] as? Double,
              let longitude = entry["longitude"] as? Double else {
          continue
        }
        coordinates.append(CLLocationCoordinate2D(latitude: latitude, longitude: longitude))
      }
      if coordinates.count > 50 {
        dispatchError([
          "code": "TOO_MANY_EXCLUDED_LOCATIONS",
          "message": "routeExclusions.locations supports at most 50 points; using the first 50 of \(coordinates.count)."
        ])
        coordinates = Array(coordinates.prefix(50))
      }
      routeOptions.excludedLocations = coordinates
    }
  }

  private func applyVehicleConstraints(to routeOptions: NavigationRouteOptions) {
    guard let vehicle, !vehicle.isEmpty else { return }
    guard isDrivingProfile else {
      dispatchError([
        "code": "VEHICLE_NOT_SUPPORTED_BY_PROFILE",
        "message": "Vehicle dimensions are only supported on the driving and " +
          "driving-traffic profiles, so `vehicle` was dropped for '\(routeProfile)'. " +
          "Sending it would have failed the route request."
      ])
      return
    }
    if let height = vehicle["maxHeight"] as? Double {
      routeOptions.maximumHeight = Measurement(value: height, unit: UnitLength.meters)
    }
    if let width = vehicle["maxWidth"] as? Double {
      routeOptions.maximumWidth = Measurement(value: width, unit: UnitLength.meters)
    }
    if let weight = vehicle["maxWeight"] as? Double {
      routeOptions.maximumWeight = Measurement(value: weight, unit: UnitMass.metricTons)
    }
  }


  private func observeStyleLoads() {
    guard let mapboxMap = navigationViewController?.navigationMapView?.mapView.mapboxMap else {
      return
    }
    styleLoadedSubscription = mapboxMap.onStyleLoaded.sink { [weak self] _ in
      self?.restoreCameraAfterStyleSwap()
      self?.applyMapStyleConfig(afterStyleLoad: true)
      self?.renderedNavigationLineIds.removeAll()
      self?.renderNavigationLinesIfPossible()
    }
  }

  private func applyMapStyleConfig(afterStyleLoad: Bool = false) {
    guard let config = mapStyleConfig, !config.isEmpty else { return }
    guard let mapboxMap = navigationViewController?.navigationMapView?.mapView.mapboxMap else {
      return
    }

    var values: [String: Any] = [:]
    if let preset = config["lightPreset"] as? String { values["lightPreset"] = preset }
    if let show = config["show3dObjects"] as? Bool { values["show3dObjects"] = show }
    if let show = config["showRoadLabels"] as? Bool { values["showRoadLabels"] = show }
    if let show = config["showPlaceLabels"] as? Bool { values["showPlaceLabels"] = show }
    if let show = config["showPointOfInterestLabels"] as? Bool {
      values["showPointOfInterestLabels"] = show
    }
    if let show = config["showTransitLabels"] as? Bool { values["showTransitLabels"] = show }
    guard !values.isEmpty else { return }

    let importIds = mapboxMap.styleImports.map(\.id)
    let targets = importIds.isEmpty ? ["basemap"] : importIds

    var applied: [String] = []
    var failed: [String] = []
    for (key, value) in values {
      var landed = false
      for importId in targets {
        do {
          try mapboxMap.setStyleImportConfigProperty(for: importId, config: key, value: value)
          landed = true
        } catch {
          continue
        }
      }
      if landed { applied.append(key) } else { failed.append(key) }
    }

    if !failed.isEmpty, afterStyleLoad, !hasWarnedAboutMapStyleConfig {
      hasWarnedAboutMapStyleConfig = true
      dispatchError([
        "code": "MAP_STYLE_CONFIG_UNSUPPORTED",
        "message": "mapStyleConfig (\(failed.sorted().joined(separator: ", "))) had no " +
          "effect: this style's imports (\(importIds.isEmpty ? "none" : importIds.joined(separator: ", "))) " +
          "accept no such configuration. Use a Mapbox Standard style such as " +
          "mapbox://styles/mapbox/standard."
      ])
    }

    Log.info(
      "mapStyleConfig applied=\(applied.sorted()) failed=\(failed.sorted()) imports=\(importIds)",
      category: .navigationUI
    )
  }

  private func applyRouteLineColors(redraw: Bool = false) {
    guard let colors, !colors.isEmpty else { return }
    guard let navigationMapView = navigationViewController?.navigationMapView else { return }

    func color(_ key: String) -> UIColor? {
      HexColor.parse(colors[key])
    }

    if let routeLine = color("routeLine") {
      navigationMapView.routeColor = routeLine
    }
    if let casing = color("routeLineCasing") {
      navigationMapView.routeCasingColor = casing
    }
    if let traversed = color("routeLineTraversed") {
      navigationMapView.traversedRouteColor = traversed
    }
    if let alternative = color("routeLineAlternative") {
      navigationMapView.routeAlternateColor = alternative
    }
    if let alternativeCasing = color("routeLineAlternativeCasing") {
      navigationMapView.routeAlternateCasingColor = alternativeCasing
    }
    if let restricted = color("restrictedRoad") {
      navigationMapView.routeRestrictedAreaColor = restricted
    }
    if let arrow = color("maneuverArrow") {
      navigationMapView.maneuverArrowColor = arrow
    }
    if let arrowStroke = color("maneuverArrowStroke") {
      navigationMapView.maneuverArrowStrokeColor = arrowStroke
    }

    var congestion = navigationMapView.congestionConfiguration
    if let low = color("congestionLow") { congestion.colors.mainRouteColors.low = low }
    if let moderate = color("congestionModerate") { congestion.colors.mainRouteColors.moderate = moderate }
    if let heavy = color("congestionHeavy") { congestion.colors.mainRouteColors.heavy = heavy }
    if let severe = color("congestionSevere") { congestion.colors.mainRouteColors.severe = severe }
    if let unknown = color("congestionUnknown") { congestion.colors.mainRouteColors.unknown = unknown }
    navigationMapView.congestionConfiguration = congestion

    guard redraw else { return }

    guard let routes = navigationViewController?.navigationRoutes else { return }
    navigationMapView.show(
      routes,
      routeAnnotationKinds: navigationMapView.showsRelativeDurationsOnAlternativeManuever
        ? [.relativeDurationsOnAlternativeManuever]
        : []
    )
  }

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

      switch resolution {
      case .style(let puckType):
        mapView.puckType = puckType
      case .hidden:
        mapView.puckType = nil
      case .sdkDefault:
        mapView.puckType = .puck2D()
      }
    }
  }

  private func applyBannerChange() {
    if navigationViewController != nil, usesEmptyBanners, showsManeuverView || showsTripProgress {
      restartRouteRequestIfNeeded()
      return
    }
    applyDynamicUIOptionsIfPossible()
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
    guard gesture.state == .changed, abs(translation.y) + abs(translation.x) > 8 else { return }
    guard let navigationCamera = navigationViewController?.navigationMapView?.navigationCamera,
          navigationCamera.currentCameraState != .idle else {
      setCameraFollowingState(false, reason: "gesture")
      return
    }
    pendingCameraReason = "gesture"
    navigationCamera.stop()
    setCameraFollowingState(false, reason: "gesture")
  }

  private func observeCameraStates(of viewController: NavigationViewController) {
    guard let navigationCamera = viewController.navigationMapView?.navigationCamera else { return }
    cameraStateSubscription = navigationCamera.cameraStates
      .receive(on: DispatchQueue.main)
      .sink { [weak self] state in
        guard let self else { return }
        let reason = self.pendingCameraReason ?? "camera"
        self.pendingCameraReason = nil
        self.setCameraFollowingState(state == .following, reason: reason)
      }
  }

  private func setCameraFollowingState(_ next: Bool, reason: String, force: Bool = false) {
    if isCameraFollowing == next, !force { return }
    isCameraFollowing = next
    dispatchCameraFollowingStateChange([
      "isCameraFollowing": next,
      "isCameraNotFollowing": !next,
      "reason": reason
    ])
  }

  private func resumeCameraFollowingInternal(reason: String) {
    if let navigationCamera = navigationViewController?.navigationMapView?.navigationCamera,
       navigationCamera.currentCameraState != .following {
      pendingCameraReason = reason
      navigationCamera.update(cameraState: .following)
    }
    setCameraFollowingState(true, reason: reason)
  }

  private func applyEmbeddedBannerVisibility(to viewController: NavigationViewController) {
    let showManeuver = showsManeuverView
    let showTripProgress = showsTripProgress
    let showActionButtons = showsActionButtons
    let showCancel = showCancelButton

    if usesEmptyBanners {
      applyBottomBannerVisibility(
        to: viewController,
        showTripProgress: showTripProgress,
        showActionButtons: showActionButtons,
        showCancel: showCancel
      )
      return
    }

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

    let showBottom = applyBottomBannerVisibility(
      to: viewController,
      showTripProgress: showTripProgress,
      showActionButtons: showActionButtons,
      showCancel: showCancel
    )

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

  @discardableResult
  private func applyBottomBannerVisibility(
    to viewController: NavigationViewController,
    showTripProgress: Bool,
    showActionButtons: Bool,
    showCancel: Bool
  ) -> Bool {
    let showBottom = showTripProgress || (showActionButtons && showCancel)

    if let bottom = resolveBottomBannerController(from: viewController) {
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

    viewController.navigationView.bottomBannerContainerView.isHidden = !showBottom
    viewController.navigationView.wayNameView.isHidden = !showsWayNameLabel
    return showBottom
  }

  private func applySpeedLimitVisibility(to viewController: NavigationViewController) {
    guard !usesEmptyBanners else { return }
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
      hideSubtreeLabelsByHints(root: view, hints: hints)
    }
  }

  private func hideSubtreeByLabelTextHints(root: UIView, textHints: [String]) {
    for view in root.subviews {
      if let label = view as? UILabel {
        let text = (label.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if textHints.contains(where: { text.contains($0) }) {
          label.isHidden = true
          label.alpha = 0
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
      hideSubtreeByLabelTextHints(root: view, textHints: textHints)
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
      hideSubtreeByHints(root: view, hints: hints)
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
      showSubtreeByHints(root: view, hints: hints)
    }
  }

  private func applyNativeFloatingButtonsConfiguration(to viewController: NavigationViewController) {
    let options = nativeFloatingButtons ?? [:]
    let showOverview = (options["showOverviewButton"] as? Bool) ?? true
    let showAudio = (options["showAudioGuidanceButton"] as? Bool) ?? true
    let showFeedback = (options["showFeedbackButton"] as? Bool) ?? true

    viewController.showsReportFeedback = showsReportFeedback && showFeedback
    viewController.showsEndOfRouteFeedback = false
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

  private func applyNativeResumeButtonVisibility(to viewController: NavigationViewController) {
    let visible = showsActionButtons
    func walk(_ view: UIView) {
      for subview in view.subviews {
        if let resume = subview as? ResumeButton {
          resume.alpha = visible ? 1 : 0
          resume.isUserInteractionEnabled = visible
        } else {
          walk(subview)
        }
      }
    }
    walk(viewController.navigationView)
  }

  private func resolveTopBannerController(from viewController: NavigationViewController) -> UIViewController? {
    viewController.children.first { $0 is TopBannerViewController }
  }

  private func resolveBottomBannerController(from viewController: NavigationViewController) -> BottomBannerViewController? {
    viewController.children.lazy.compactMap { $0 as? BottomBannerViewController }.first
  }


  private func applyCameraConfiguration(to viewController: NavigationViewController) {
    guard
      let navigationMapView = viewController.navigationMapView,
      let viewportDataSource = navigationMapView.navigationCamera
      .viewportDataSource as? MobileViewportDataSource else {
      return
    }

    let normalizedMode = cameraMode.lowercased()
    var cameraOptions = viewportDataSource.currentNavigationCameraOptions

    if normalizedMode == "overview" {
      viewportDataSource.options.overviewCameraOptions.pitchUpdatesAllowed = false
      cameraOptions.overviewCamera.pitch = 0

      if let zoom = cameraZoom {
        viewportDataSource.options.overviewCameraOptions.zoomUpdatesAllowed = false
        cameraOptions.overviewCamera.zoom = CGFloat(zoom.clamped(to: 1...22))
      }

      viewportDataSource.currentNavigationCameraOptions = cameraOptions
      navigationMapView.navigationCamera.update(cameraState: .overview)
      setCameraFollowingState(false, reason: "config")
      return
    }

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

    MapboxNavigationTheme.configure(with: colors)

    let styles: [Style]?
    if dayStyleURL == nil && nightStyleURL == nil && MapboxNavigationTheme.palette.isEmpty {
      styles = nil
    } else {
      let dayStyle = ThemedDayStyle()
      if let dayStyleURL {
        dayStyle.mapStyleURL = dayStyleURL
      }

      let nightStyle = ThemedNightStyle()
      if let nightStyleURL {
        nightStyle.mapStyleURL = nightStyleURL
      } else if let dayStyleURL {
        nightStyle.mapStyleURL = dayStyleURL
      }

      styles = [dayStyle, nightStyle]
    }

    usesEmptyBanners = !showsManeuverView && !showsTripProgress
    let keepsBottomBanner = showsActionButtons && showCancelButton

    return NavigationOptions(
      mapboxNavigation: provider.mapboxNavigation,
      voiceController: provider.routeVoiceController,
      eventsManager: provider.eventsManager(),
      styles: styles,
      topBanner: usesEmptyBanners ? EmptyBannerViewController() : nil,
      bottomBanner: usesEmptyBanners && !keepsBottomBanner
        ? EmptyBannerViewController()
        : nil,
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

  private static func routeIdString(_ routeId: RouteId) -> String? {
    guard let data = try? JSONEncoder().encode([routeId]) else { return nil }
    return (try? JSONDecoder().decode([String].self, from: data))?.first
  }

  private func emitRouteChange(navigationRoute: NavigationRoute) {
    guard let coordinates = navigationRoute.route.shape?.coordinates, !coordinates.isEmpty else { return }
    let routeId = Self.routeIdString(navigationRoute.routeId)
    let key = routeId ?? {
      let first = coordinates[0]
      let last = coordinates[coordinates.count - 1]
      return "\(coordinates.count):\(first.latitude),\(first.longitude):\(last.latitude),\(last.longitude)"
    }()
    guard key != lastEmittedRouteKey else { return }
    lastEmittedRouteKey = key

    var payload: [String: Any] = [
      "coordinates": coordinates.map { coordinate in
        [
          "latitude": coordinate.latitude,
          "longitude": coordinate.longitude
        ]
      }
    ]
    if let routeId {
      payload["routeId"] = routeId
    }
    dispatchRouteChange(payload)
  }

  private func observeRoutes(of viewController: NavigationViewController) {
    routesSubscription = viewController.mapboxNavigation.tripSession().navigationRoutes
      .compactMap { $0?.mainRoute }
      .receive(on: DispatchQueue.main)
      .sink { [weak self, weak viewController] mainRoute in
        guard let self, let viewController, self.navigationViewController === viewController else { return }
        let remainingStops = max(0, mainRoute.route.legs.count - 1)
        if remainingStops < self.routedIntermediateWaypoints.count {
          self.routedIntermediateWaypoints = Array(self.routedIntermediateWaypoints.suffix(remainingStops))
        }
        self.emitRouteChange(navigationRoute: mainRoute)
      }
    sessionStateSubscription = viewController.mapboxNavigation.tripSession().session
      .receive(on: DispatchQueue.main)
      .sink { [weak self, weak viewController] session in
        guard let self, let viewController, self.navigationViewController === viewController else { return }
        guard case .activeGuidance(.tracking) = session.state else { return }
        guard !self.isTargetRerouteFailing else { return }
        self.reportBackOnRoute()
      }
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

extension MapboxNavigationView: NavigationViewControllerDelegate {
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    waypointCircleLayerWithIdentifier identifier: String,
    sourceIdentifier: String
  ) -> CircleLayer? {
    guard !showsWaypoints else { return nil }
    var layer = CircleLayer(id: identifier, source: sourceIdentifier)
    layer.visibility = .constant(.none)
    return layer
  }

  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    waypointSymbolLayerWithIdentifier identifier: String,
    sourceIdentifier: String
  ) -> SymbolLayer? {
    guard !showsWaypoints else { return nil }
    var layer = SymbolLayer(id: identifier, source: sourceIdentifier)
    layer.visibility = .constant(.none)
    return layer
  }

  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didRerouteAlong route: Route
  ) {
    reportBackOnRoute()
    guard let mainRoute = navigationViewController.navigationRoutes?.mainRoute else { return }
    emitRouteChange(navigationRoute: mainRoute)
  }

  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didUpdate progress: RouteProgress,
    with location: CLLocation,
    rawLocation: CLLocation
  ) {
    if !hasReceivedRouteProgress {
      hasReceivedRouteProgress = true
      cancelGuidanceWatchdog()
    }
    if !usesEmptyBanners,
       !showsManeuverView || !showsTripProgress || !showsActionButtons || !showsSpeedLimits || !showsWayNameLabel {
      let now = CACurrentMediaTime()
      if now - lastDynamicUIRefreshAt >= Self.dynamicUIRefreshInterval {
        lastDynamicUIRefreshAt = now
        applyDynamicUIOptionsIfPossible()
      }
    }

    let nowTime = CACurrentMediaTime()
    let throttleSeconds = max(0, min(eventThrottleMs, 10_000)) / 1000
    let shouldEmitContinuous =
      throttleSeconds <= 0 || (nowTime - lastContinuousEmitAt) >= throttleSeconds

    if shouldEmitContinuous {
      lastContinuousEmitAt = nowTime
    }

    guard shouldEmitContinuous else {
      dispatchBannerInstructionIfChanged(for: progress)
      return
    }

    dispatchRouteProgressChange([
      "distanceTraveled": progress.distanceTraveled,
      "distanceRemaining": progress.distanceRemaining,
      "durationRemaining": progress.durationRemaining,
      "fractionTraveled": progress.fractionTraveled,
      "legIndex": progress.legIndex,
      "legDistanceRemaining": progress.currentLegProgress.distanceRemaining,
      "legDurationRemaining": progress.currentLegProgress.durationRemaining
    ])

    dispatchBannerInstructionIfChanged(for: progress)

    guard journeyDataEnabled else { return }

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
  
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didArriveAt waypoint: Waypoint
  ) {
    guard let progress = navigationViewController.mapboxNavigation.navigation()
      .currentRouteProgress?.routeProgress
    else { return }
    let isFinal = progress.isFinalLeg
    let payload: [String: Any] = [
      "index": max(0, routedWaypointsProp.count - routedIntermediateWaypoints.count) + progress.legIndex,
      "name": waypoint.name ?? "",
      "isFinalDestination": isFinal,
      "remainingWaypoints": progress.remainingWaypoints.count
    ]

    if isFinal {
      guard inPlaceRerouteTarget == nil else { return }
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
    var payload: [String: Any] = ["offRoute": true, "reason": "deviation"]
    if let location {
      payload["latitude"] = location.coordinate.latitude
      payload["longitude"] = location.coordinate.longitude
    }
    isReportedOffRoute = true
    dispatchOffRoute(payload)
  }

  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didFailToRerouteWith error: Error
  ) {
    isReportedOffRoute = true
    dispatchOffRoute(["offRoute": true, "rerouteFailed": true, "reason": "deviation"])
    dispatchError([
      "code": "REROUTE_FAILED",
      "message": error.localizedDescription
    ])
  }
  
  func navigationViewControllerDidDismiss(
    _ navigationViewController: NavigationViewController,
    byCanceling canceled: Bool
  ) {
    guard canceled else { return }
    dispatchCancelNavigation([:])
    cleanupNavigation()
  }
}

extension MapboxNavigationView: CLLocationManagerDelegate {
  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    guard isWaitingForOriginFix else { return }
    handleOriginFixes(locations)
  }

  func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    guard isWaitingForOriginFix else { return }
    if (error as? CLError)?.code == .locationUnknown { return }
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
      beginOriginUpdates()
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

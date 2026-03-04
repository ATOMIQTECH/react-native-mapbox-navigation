import ExpoModulesCore
import MapboxNavigation
import MapboxDirections
import MapboxCoreNavigation
import CoreLocation
import UIKit

class MapboxNavigationView: ExpoView {
  private static weak var activeInstance: MapboxNavigationView?
  static func requestStopActiveInstance() -> Bool {
    guard let instance = activeInstance else { return false }
    DispatchQueue.main.async {
      instance.enabled = false
      instance.cleanupNavigation()
      instance.onCancelNavigation([:])
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
  
  private var navigationViewController: NavigationViewController?
  private var hostViewController: UIViewController?
  private var isRouteCalculationInProgress = false
  private var hasPendingSessionConflict = false
  private var warnedUnsupportedOptions = Set<String>()
  private var routeRequestToken = UUID()
  private var isCameraFollowing = true
  private var hasCameraPanGesture = false
  
  let onLocationChange = EventDispatcher()
  let onRouteProgressChange = EventDispatcher()
  let onJourneyDataChange = EventDispatcher()
  let onRouteChange = EventDispatcher()
  let onCameraFollowingStateChange = EventDispatcher()
  let onBannerInstruction = EventDispatcher()
  let onArrive = EventDispatcher()
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
    guard let origin = startOrigin,
          let dest = destination,
          let originLat = (origin["latitude"] as? NSNumber)?.doubleValue,
          let originLng = (origin["longitude"] as? NSNumber)?.doubleValue,
          let destLat = (dest["latitude"] as? NSNumber)?.doubleValue,
          let destLng = (dest["longitude"] as? NSNumber)?.doubleValue else {
      return
    }

    guard NavigationSessionRegistry.shared.acquire(owner: sessionOwner) else {
      if !hasPendingSessionConflict {
        hasPendingSessionConflict = true
        onError([
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
        self.onCancelNavigation([:])
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
    
    let originCoord = CLLocationCoordinate2D(latitude: originLat, longitude: originLng)
    let destCoord = CLLocationCoordinate2D(latitude: destLat, longitude: destLng)
    
    var waypointsList = [Waypoint(coordinate: originCoord)]
    
    // Add intermediate waypoints
    if let intermediateWaypoints = waypoints {
      for wp in intermediateWaypoints {
        if let lat = (wp["latitude"] as? NSNumber)?.doubleValue,
           let lng = (wp["longitude"] as? NSNumber)?.doubleValue {
          let coord = CLLocationCoordinate2D(latitude: lat, longitude: lng)
          let waypoint = Waypoint(coordinate: coord)
          waypoint.name = wp["name"] as? String
          waypointsList.append(waypoint)
        }
      }
    }
    
    // Add final destination
    let finalWaypoint = Waypoint(coordinate: destCoord)
    finalWaypoint.name = (dest["name"] as? String) ?? (dest["title"] as? String) ?? "Destination"
    waypointsList.append(finalWaypoint)
    
    let routeOptions = NavigationRouteOptions(waypoints: waypointsList)
    routeOptions.locale = Locale(identifier: language)
    routeOptions.distanceMeasurementSystem = distanceUnit == "imperial" ? .imperial : .metric
    routeOptions.includesAlternativeRoutes = routeAlternatives
    
    let requestToken = UUID()
    routeRequestToken = requestToken
    isRouteCalculationInProgress = true
    Directions.shared.calculate(routeOptions) { [weak self] (_, result) in
      guard let self = self else { return }
      if self.routeRequestToken != requestToken {
        // A newer embedded start/stop cycle occurred; ignore stale route results.
        NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
        return
      }

      self.isRouteCalculationInProgress = false
      guard self.enabled, self.window != nil, self.navigationViewController == nil else {
        NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
        return
      }
      
      switch result {
      case .success(let response):
        guard response.routes?.first != nil else {
          NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
          self.onError([
            "code": "NO_ROUTE",
            "message": "No route found"
          ])
          return
        }
        
        DispatchQueue.main.async {
          self.emitRouteChange(from: response)
          self.embedNavigation(response: response, routeOptions: routeOptions)
        }
        
      case .failure(let error):
        NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
        self.onError([
          "code": "ROUTE_ERROR",
          "message": error.localizedDescription
        ])
      }
    }
  }
  
  private func embedNavigation(response: RouteResponse, routeOptions: NavigationRouteOptions) {
    let indexedRouteResponse = IndexedRouteResponse(routeResponse: response, routeIndex: 0)
    let navigationService = MapboxNavigationService(
      indexedRouteResponse: indexedRouteResponse,
      credentials: Directions.shared.credentials,
      simulating: shouldSimulateRoute ? .always : nil
    )
    
    let navigationOptions = buildNavigationOptions(navigationService: navigationService)
    
    let viewController = NavigationViewController(
      for: indexedRouteResponse,
      navigationOptions: navigationOptions
    )
    
    viewController.delegate = self
    attachMapPanDetection(to: viewController)
    
    NavigationSettings.shared.distanceUnit = distanceUnit == "imperial" ? .mile : .kilometer
    NavigationSettings.shared.voiceMuted = mute
    NavigationSettings.shared.voiceVolume = Float(max(0, min(voiceVolume, 1)))
    viewController.showsSpeedLimits = showsSpeedLimits
    applySpeedLimitVisibility(to: viewController)
    applyNativeFloatingButtonsConfiguration(to: viewController)
    applyEmbeddedBannerVisibility(to: viewController)
    if !showsReportFeedback {
      warnUnsupportedOptionOnce(
        key: "showsReportFeedback",
        message: "showsReportFeedback is currently not supported on embedded iOS navigation and will be ignored."
      )
    }
    if !showsContinuousAlternatives {
      warnUnsupportedOptionOnce(
        key: "showsContinuousAlternatives",
        message: "showsContinuousAlternatives is currently not supported on embedded iOS navigation and will be ignored."
      )
    }
    if !usesNightStyleWhileInTunnel {
      warnUnsupportedOptionOnce(
        key: "usesNightStyleWhileInTunnel",
        message: "usesNightStyleWhileInTunnel is currently not supported on embedded iOS navigation and will be ignored."
      )
    }
    if routeLineTracksTraversal {
      warnUnsupportedOptionOnce(
        key: "routeLineTracksTraversal",
        message: "routeLineTracksTraversal is currently not supported on embedded iOS navigation and will be ignored."
      )
    }
    if annotatesIntersectionsAlongRoute {
      warnUnsupportedOptionOnce(
        key: "annotatesIntersectionsAlongRoute",
        message: "annotatesIntersectionsAlongRoute is currently not supported on embedded iOS navigation and will be ignored."
      )
    }
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
      
      navigationViewController = viewController
      hostViewController = parent
    } else {
      NavigationSessionRegistry.shared.release(owner: sessionOwner)
      onError([
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
    navigationViewController?.willMove(toParent: nil)
    navigationViewController?.view.removeFromSuperview()
    navigationViewController?.removeFromParent()
    navigationViewController = nil
    hasCameraPanGesture = false
    setCameraFollowingState(true, reason: "cleanup")
    NavigationSessionRegistry.shared.release(owner: sessionOwner)
    if MapboxNavigationView.activeInstance === self {
      MapboxNavigationView.activeInstance = nil
    }
    hasPendingSessionConflict = false
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
    onCameraFollowingStateChange([
      "isCameraFollowing": next,
      "isCameraNotFollowing": !next,
      "reason": reason
    ])
  }

  private func resumeCameraFollowingInternal(reason: String) {
    navigationViewController?.navigationMapView?.navigationCamera.follow()
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

    viewController.showsReportFeedback = showFeedback
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

  private func applyCameraConfiguration(to viewController: NavigationViewController) {
    guard
      let navigationMapView = viewController.navigationMapView,
      let viewportDataSource = navigationMapView.navigationCamera
      .viewportDataSource as? NavigationViewportDataSource else {
      return
    }

    let normalizedMode = cameraMode.lowercased()

    if normalizedMode == "overview" {
      viewportDataSource.options.followingCameraOptions.zoomUpdatesAllowed = false
      viewportDataSource.followingMobileCamera.zoom = CGFloat(cameraZoom ?? 10)
      viewportDataSource.options.followingCameraOptions.pitchUpdatesAllowed = false
      viewportDataSource.followingMobileCamera.pitch = 0
    } else {
      // Keep dynamic camera updates in following mode so turn-by-turn camera behavior
      // (zoom/pitch/bearing adaptation) remains managed by the SDK.
      viewportDataSource.options.followingCameraOptions.pitchUpdatesAllowed = true
      viewportDataSource.options.followingCameraOptions.zoomUpdatesAllowed = true
      viewportDataSource.options.followingCameraOptions.bearingUpdatesAllowed = true

      if let pitch = cameraPitch {
        viewportDataSource.followingMobileCamera.pitch = CGFloat(max(0, min(pitch, 85)))
      }

      if let zoom = cameraZoom {
        viewportDataSource.followingMobileCamera.zoom = CGFloat(max(1, min(zoom, 22)))
      }
    }

    navigationMapView.navigationCamera.follow()
    setCameraFollowingState(normalizedMode != "overview", reason: "config")
  }

  private func buildNavigationOptions(navigationService: NavigationService) -> NavigationOptions {
    let dayStyleURL = normalizedStyleURL(
      primary: mapStyleUriDay,
      fallback: mapStyleUri
    )
    let nightStyleURL = normalizedStyleURL(
      primary: mapStyleUriNight,
      fallback: mapStyleUriDay ?? mapStyleUri
    )

    guard dayStyleURL != nil || nightStyleURL != nil else {
      return NavigationOptions(navigationService: navigationService)
    }

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

    return NavigationOptions(styles: [dayStyle, nightStyle], navigationService: navigationService)
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

  private func warnUnsupportedOptionOnce(key: String, message: String) {
    guard !warnedUnsupportedOptions.contains(key) else {
      return
    }
    warnedUnsupportedOptions.insert(key)
    NSLog("[MapboxNavigationView] \(message)")
  }

  private func emitRouteChange(from response: RouteResponse) {
    guard let route = response.routes?.first else { return }
    guard let shape = route.shape else { return }
    let coords = shape.coordinates.map { coordinate in
      [
        "latitude": coordinate.latitude,
        "longitude": coordinate.longitude
      ]
    }
    onRouteChange(["coordinates": coords])
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
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didUpdate progress: RouteProgress,
    with location: CLLocation,
    rawLocation: CLLocation
  ) {
    if !showsManeuverView || !showsTripProgress || !showsActionButtons || !showsSpeedLimits || !showsWayNameLabel {
      applyDynamicUIOptionsIfPossible()
    }

    if cameraMode.lowercased() == "following" && isCameraFollowing {
      navigationViewController.navigationMapView?.navigationCamera.follow()
    }

    onLocationChange([
      "latitude": location.coordinate.latitude,
      "longitude": location.coordinate.longitude,
      "bearing": location.course,
      "speed": location.speed,
      "altitude": location.altitude,
      "accuracy": location.horizontalAccuracy
    ])
    
    onRouteProgressChange([
      "distanceTraveled": progress.distanceTraveled,
      "distanceRemaining": progress.distanceRemaining,
      "durationRemaining": progress.durationRemaining,
      "fractionTraveled": progress.fractionTraveled
    ])

    onBannerInstruction([
      "primaryText": progress.currentLegProgress.currentStep.instructions,
      "stepDistanceRemaining": progress.currentLegProgress.currentStepProgress.distanceRemaining
    ])

    let secondaryInstruction = progress.currentLegProgress.currentStep.names?.first
      ?? progress.currentLegProgress.currentStep.description
    let durationRemaining = progress.durationRemaining
    onJourneyDataChange([
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
      "etaIso8601": ISO8601DateFormatter().string(from: Date().addingTimeInterval(durationRemaining))
    ])
  }
  
  func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didArriveAt waypoint: Waypoint
  ) -> Bool {
    onArrive([
      "name": waypoint.name ?? ""
    ])
    return true
  }
  
  func navigationViewControllerDidDismiss(
    _ navigationViewController: NavigationViewController,
    byCanceling canceled: Bool
  ) {
    if canceled {
      onCancelNavigation([:])
    }
    cleanupNavigation()
  }
}

extension MapboxNavigationView: UIGestureRecognizerDelegate {
  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
    true
  }
}

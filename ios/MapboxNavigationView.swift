import ExpoModulesCore
import MapboxNavigation
import MapboxDirections
import MapboxCoreNavigation
import CoreLocation
import UIKit

class MapboxNavigationView: ExpoView {
  private let sessionOwner = "embedded-\(UUID().uuidString)"
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
  var showCancelButton: Bool = true
  var mute: Bool = false
  var voiceVolume: Double = 1
  var cameraPitch: Double?
  var cameraZoom: Double?
  var cameraMode: String = "following"
  var mapStyleUri: String?
  var mapStyleUriDay: String?
  var mapStyleUriNight: String?
  var uiTheme: String = "system"
  var routeAlternatives: Bool = false
  var showsSpeedLimits: Bool = true
  var showsWayNameLabel: Bool = true
  var showsTripProgress: Bool = true
  var showsManeuverView: Bool = true
  var showsActionButtons: Bool = true
  var showsReportFeedback: Bool = true
  var showsEndOfRouteFeedback: Bool = true
  var showsContinuousAlternatives: Bool = true
  var usesNightStyleWhileInTunnel: Bool = true
  var routeLineTracksTraversal: Bool = false
  var annotatesIntersectionsAlongRoute: Bool = false
  var distanceUnit: String = "metric"
  var language: String = "en"
  
  private var navigationViewController: NavigationViewController?
  private var hostViewController: UIViewController?
  private var isRouteCalculationInProgress = false
  private var hasPendingSessionConflict = false
  private var warnedUnsupportedOptions = Set<String>()
  
  let onLocationChange = EventDispatcher()
  let onRouteProgressChange = EventDispatcher()
  let onJourneyDataChange = EventDispatcher()
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
    
    if window != nil {
      startNavigationIfReady()
    } else {
      cleanupNavigation()
    }
  }
  
  private func startNavigationIfReady() {
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
          "message": "Another navigation session is already active. Stop full-screen or other embedded navigation before mounting this view."
        ])
      }
      return
    }
    hasPendingSessionConflict = false
    
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
    
    isRouteCalculationInProgress = true
    Directions.shared.calculate(routeOptions) { [weak self] (_, result) in
      guard let self = self else { return }
      self.isRouteCalculationInProgress = false
      
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
    
    NavigationSettings.shared.distanceUnit = distanceUnit == "imperial" ? .mile : .kilometer
    NavigationSettings.shared.voiceMuted = mute
    NavigationSettings.shared.voiceVolume = Float(max(0, min(voiceVolume, 1)))
    viewController.showsSpeedLimits = showsSpeedLimits
    if !showsManeuverView {
      warnUnsupportedOptionOnce(
        key: "showsManeuverView",
        message: "showsManeuverView is currently not supported on embedded iOS navigation and will be ignored."
      )
    }
    if !showsTripProgress {
      warnUnsupportedOptionOnce(
        key: "showsTripProgress",
        message: "showsTripProgress is currently not supported on embedded iOS navigation and will be ignored."
      )
    }
    if !showsActionButtons {
      warnUnsupportedOptionOnce(
        key: "showsActionButtons",
        message: "showsActionButtons is currently not supported on embedded iOS navigation and will be ignored."
      )
    }
    if !showsReportFeedback {
      warnUnsupportedOptionOnce(
        key: "showsReportFeedback",
        message: "showsReportFeedback is currently not supported on embedded iOS navigation and will be ignored."
      )
    }
    if !showsEndOfRouteFeedback {
      warnUnsupportedOptionOnce(
        key: "showsEndOfRouteFeedback",
        message: "showsEndOfRouteFeedback is currently not supported on embedded iOS navigation and will be ignored."
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
    if !showCancelButton {
      warnUnsupportedOptionOnce(
        key: "showCancelButton",
        message: "showCancelButton is currently not supported for embedded iOS navigation and will be ignored."
      )
    }
    if !showsWayNameLabel {
      warnUnsupportedOptionOnce(
        key: "showsWayNameLabel",
        message: "showsWayNameLabel is currently not supported on embedded iOS navigation and will be ignored."
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
    navigationViewController?.willMove(toParent: nil)
    navigationViewController?.view.removeFromSuperview()
    navigationViewController?.removeFromParent()
    navigationViewController = nil
    NavigationSessionRegistry.shared.release(owner: sessionOwner)
    hasPendingSessionConflict = false
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
    if cameraMode.lowercased() == "following" {
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

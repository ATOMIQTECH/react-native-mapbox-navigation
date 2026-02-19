import ExpoModulesCore
import MapboxNavigation
import MapboxDirections
import MapboxCoreNavigation
import CoreLocation
import UIKit

class MapboxNavigationView: ExpoView {
  var startOrigin: [String: Double]? {
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
  var routeAlternatives: Bool = false
  var showsSpeedLimits: Bool = true
  var showsWayNameLabel: Bool = true
  var distanceUnit: String = "metric"
  var language: String = "en"
  
  private var navigationViewController: NavigationViewController?
  private var hostViewController: UIViewController?
  private var isRouteCalculationInProgress = false
  
  let onLocationChange = EventDispatcher()
  let onRouteProgressChange = EventDispatcher()
  let onBannerInstruction = EventDispatcher()
  let onArrive = EventDispatcher()
  let onCancelNavigation = EventDispatcher()
  let onError = EventDispatcher()
  
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
          let originLat = origin["latitude"],
          let originLng = origin["longitude"],
          let destLat = dest["latitude"] as? Double,
          let destLng = dest["longitude"] as? Double else {
      return
    }
    
    let originCoord = CLLocationCoordinate2D(latitude: originLat, longitude: originLng)
    let destCoord = CLLocationCoordinate2D(latitude: destLat, longitude: destLng)
    
    var waypointsList = [Waypoint(coordinate: originCoord)]
    
    // Add intermediate waypoints
    if let intermediateWaypoints = waypoints {
      for wp in intermediateWaypoints {
        if let lat = wp["latitude"] as? Double,
           let lng = wp["longitude"] as? Double {
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
    applyCameraConfiguration(to: viewController)
    
    // Find the parent view controller
    var parentVC: UIViewController? = self.window?.rootViewController
    while let presented = parentVC?.presentedViewController {
      parentVC = presented
    }
    
    if let parent = parentVC {
      // Add as child view controller
      parent.addChild(viewController)
      addSubview(viewController.view)
      viewController.view.frame = bounds
      viewController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
      viewController.didMove(toParent: parent)
      
      navigationViewController = viewController
      hostViewController = parent
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
    guard
      let styleUri = mapStyleUri?.trimmingCharacters(in: .whitespacesAndNewlines),
      !styleUri.isEmpty,
      let styleURL = URL(string: styleUri)
    else {
      return NavigationOptions(navigationService: navigationService)
    }

    let dayStyle = DayStyle()
    dayStyle.mapStyleURL = styleURL

    let nightStyle = NightStyle()
    nightStyle.mapStyleURL = styleURL

    return NavigationOptions(styles: [dayStyle, nightStyle], navigationService: navigationService)
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

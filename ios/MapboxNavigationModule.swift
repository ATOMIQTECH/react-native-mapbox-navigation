import ExpoModulesCore
import MapboxNavigation
import MapboxDirections
import MapboxCoreNavigation
import CoreLocation
import UIKit

public class MapboxNavigationModule: Module {
  private var navigationViewController: NavigationViewController?
  private var isCurrentlyNavigating = false
  private var currentLanguage = Locale.preferredLanguages.first ?? "en"
  private var currentCameraMode = "following"
  private var currentLocationResolver: CurrentLocationResolver?
  
  public func definition() -> ModuleDefinition {
    Name("MapboxNavigationModule")
    
    // Events that can be sent to JS
    Events(
      "onLocationChange",
      "onRouteProgressChange",
      "onBannerInstruction",
      "onArrive",
      "onCancelNavigation",
      "onError"
    )
    
    // Start navigation
    AsyncFunction("startNavigation") { (options: NavigationStartOptions, promise: Promise) in
      DispatchQueue.main.async {
        self.startNavigation(options: options, promise: promise)
      }
    }
    
    // Stop navigation
    AsyncFunction("stopNavigation") { (promise: Promise) in
      DispatchQueue.main.async {
        self.stopNavigation(promise: promise)
      }
    }
    
    // Set muted state
    AsyncFunction("setMuted") { (muted: Bool, promise: Promise) in
      DispatchQueue.main.async {
        self.setMuted(muted: muted, promise: promise)
      }
    }

    AsyncFunction("setVoiceVolume") { (volume: Double, promise: Promise) in
      DispatchQueue.main.async {
        self.setVoiceVolume(volume: volume, promise: promise)
      }
    }

    AsyncFunction("setDistanceUnit") { (unit: String, promise: Promise) in
      DispatchQueue.main.async {
        self.setDistanceUnit(unit: unit, promise: promise)
      }
    }

    AsyncFunction("setLanguage") { (language: String, promise: Promise) in
      DispatchQueue.main.async {
        self.setLanguage(language: language, promise: promise)
      }
    }
    
    // Check if navigating
    AsyncFunction("isNavigating") { (promise: Promise) in
      promise.resolve(self.isCurrentlyNavigating)
    }

    AsyncFunction("getNavigationSettings") { (promise: Promise) in
      let unit: String = NavigationSettings.shared.distanceUnit == .mile ? "imperial" : "metric"
      promise.resolve([
        "isNavigating": self.isCurrentlyNavigating,
        "mute": NavigationSettings.shared.voiceMuted,
        "voiceVolume": NavigationSettings.shared.voiceVolume,
        "distanceUnit": unit,
        "language": self.currentLanguage
      ])
    }
    
    // View for embedded navigation
    View(MapboxNavigationView.self) {
      Events(
        "onLocationChange",
        "onRouteProgressChange",
        "onBannerInstruction",
        "onArrive",
        "onCancelNavigation",
        "onError"
      )
      
      Prop("startOrigin") { (view: MapboxNavigationView, origin: [String: Double]) in
        view.startOrigin = origin
      }
      
      Prop("destination") { (view: MapboxNavigationView, destination: [String: Any]) in
        view.destination = destination
      }
      
      Prop("waypoints") { (view: MapboxNavigationView, waypoints: [[String: Any]]?) in
        view.waypoints = waypoints
      }
      
      Prop("shouldSimulateRoute") { (view: MapboxNavigationView, simulate: Bool) in
        view.shouldSimulateRoute = simulate
      }
      
      Prop("showCancelButton") { (view: MapboxNavigationView, show: Bool) in
        view.showCancelButton = show
      }
      
      Prop("mute") { (view: MapboxNavigationView, mute: Bool) in
        view.mute = mute
      }

      Prop("voiceVolume") { (view: MapboxNavigationView, volume: Double) in
        view.voiceVolume = volume
      }

      Prop("cameraPitch") { (view: MapboxNavigationView, pitch: Double) in
        view.cameraPitch = pitch
      }

      Prop("cameraZoom") { (view: MapboxNavigationView, zoom: Double) in
        view.cameraZoom = zoom
      }

      Prop("cameraMode") { (view: MapboxNavigationView, mode: String) in
        view.cameraMode = mode
      }

      Prop("mapStyleUri") { (view: MapboxNavigationView, mapStyleUri: String) in
        view.mapStyleUri = mapStyleUri
      }

      Prop("routeAlternatives") { (view: MapboxNavigationView, routeAlternatives: Bool) in
        view.routeAlternatives = routeAlternatives
      }

      Prop("showsSpeedLimits") { (view: MapboxNavigationView, showsSpeedLimits: Bool) in
        view.showsSpeedLimits = showsSpeedLimits
      }

      Prop("showsWayNameLabel") { (view: MapboxNavigationView, showsWayNameLabel: Bool) in
        view.showsWayNameLabel = showsWayNameLabel
      }
      
      Prop("distanceUnit") { (view: MapboxNavigationView, unit: String) in
        view.distanceUnit = unit
      }
      
      Prop("language") { (view: MapboxNavigationView, language: String) in
        view.language = language
      }
    }
  }
  
  private func startNavigation(options: NavigationStartOptions, promise: Promise) {
    guard let destination = options.destination.toCLLocationCoordinate2D() else {
      promise.reject("INVALID_COORDINATES", "Invalid coordinates provided")
      return
    }

    if let origin = options.startOrigin?.toCLLocationCoordinate2D() {
      startNavigation(
        origin: origin,
        destination: destination,
        options: options,
        promise: promise
      )
      return
    }

    resolveCurrentOrigin { [weak self] result in
      guard let self = self else { return }
      switch result {
      case .success(let origin):
        self.startNavigation(
          origin: origin,
          destination: destination,
          options: options,
          promise: promise
        )
      case .failure(let error):
        promise.reject("CURRENT_LOCATION_UNAVAILABLE", error.localizedDescription)
      }
    }
  }

  private func startNavigation(
    origin: CLLocationCoordinate2D,
    destination: CLLocationCoordinate2D,
    options: NavigationStartOptions,
    promise: Promise
  ) {
    
    var waypoints = [Waypoint(coordinate: origin)]
    
    // Add intermediate waypoints if provided
    if let intermediateWaypoints = options.waypoints {
      for wp in intermediateWaypoints {
        if let coord = wp.toCLLocationCoordinate2D() {
          let waypoint = Waypoint(coordinate: coord)
          waypoint.name = wp.name
          waypoints.append(waypoint)
        }
      }
    }
    
    // Add final destination
    let finalWaypoint = Waypoint(coordinate: destination)
    finalWaypoint.name = options.destination.name ?? "Destination"
    waypoints.append(finalWaypoint)
    
    let routeOptions = NavigationRouteOptions(waypoints: waypoints)
    routeOptions.locale = Locale(identifier: options.language ?? currentLanguage)
    routeOptions.distanceMeasurementSystem = options.distanceUnit == "imperial" ? .imperial : .metric
    routeOptions.includesAlternativeRoutes = options.routeAlternatives == true
    
    Directions.shared.calculate(routeOptions) { [weak self] (_, result) in
      guard let self = self else { return }
      
      switch result {
      case .success(let response):
        guard response.routes?.first != nil else {
          promise.reject("NO_ROUTE", "No route found")
          return
        }
        
        let indexedRouteResponse = IndexedRouteResponse(routeResponse: response, routeIndex: 0)
        let navigationService = MapboxNavigationService(
          indexedRouteResponse: indexedRouteResponse,
          credentials: Directions.shared.credentials,
          simulating: options.shouldSimulateRoute == true ? .always : nil
        )
        
        let navigationOptions = self.buildNavigationOptions(
          navigationService: navigationService,
          mapStyleUri: options.mapStyleUri
        )
        
        let viewController = NavigationViewController(
          for: indexedRouteResponse,
          navigationOptions: navigationOptions
        )
        
        viewController.delegate = self
        viewController.modalPresentationStyle = .fullScreen
        
        self.currentLanguage = options.language ?? self.currentLanguage
        NavigationSettings.shared.distanceUnit = options.distanceUnit == "imperial" ? .mile : .kilometer
        NavigationSettings.shared.voiceMuted = options.mute == true
        if let volume = options.voiceVolume {
          NavigationSettings.shared.voiceVolume = Float(max(0, min(volume, 1)))
        }
        self.applyCameraConfiguration(
          to: viewController,
          mode: options.cameraMode,
          pitch: options.cameraPitch,
          zoom: options.cameraZoom
        )
        self.currentCameraMode = options.cameraMode ?? "following"
        viewController.showsSpeedLimits = options.showsSpeedLimits ?? true
        
        self.navigationViewController = viewController
        self.isCurrentlyNavigating = true
        
        if let rootVC = Self.currentTopViewController() {
          rootVC.present(viewController, animated: true) {
            promise.resolve(nil)
          }
        } else {
          promise.reject("NO_ROOT_VC", "Could not find root view controller")
        }
        
      case .failure(let error):
        promise.reject("ROUTE_ERROR", error.localizedDescription)
      }
    }
  }

  private func resolveCurrentOrigin(
    completion: @escaping (Result<CLLocationCoordinate2D, Error>) -> Void
  ) {
    let resolver = CurrentLocationResolver()
    currentLocationResolver = resolver
    resolver.resolve { [weak self] result in
      self?.currentLocationResolver = nil
      completion(result)
    }
  }
  
  private func stopNavigation(promise: Promise) {
    guard let navVC = navigationViewController else {
      promise.resolve(nil)
      return
    }
    
    navVC.dismiss(animated: true) {
      self.navigationViewController = nil
      self.isCurrentlyNavigating = false
      promise.resolve(nil)
    }
  }
  
  private func setMuted(muted: Bool, promise: Promise) {
    NavigationSettings.shared.voiceMuted = muted
    promise.resolve(nil)
  }

  private func setVoiceVolume(volume: Double, promise: Promise) {
    NavigationSettings.shared.voiceVolume = Float(max(0, min(volume, 1)))
    promise.resolve(nil)
  }

  private func setDistanceUnit(unit: String, promise: Promise) {
    NavigationSettings.shared.distanceUnit = unit == "imperial" ? .mile : .kilometer
    promise.resolve(nil)
  }

  private func setLanguage(language: String, promise: Promise) {
    let trimmed = language.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty {
      promise.reject("INVALID_LANGUAGE", "Language cannot be empty")
      return
    }
    currentLanguage = trimmed
    promise.resolve(nil)
  }

  private func applyCameraConfiguration(
    to viewController: NavigationViewController,
    mode: String?,
    pitch: Double?,
    zoom: Double?
  ) {
    guard
      let navigationMapView = viewController.navigationMapView,
      let viewportDataSource = navigationMapView.navigationCamera
      .viewportDataSource as? NavigationViewportDataSource else {
      return
    }

    let normalizedMode = (mode ?? "following").lowercased()

    if normalizedMode == "overview" {
      viewportDataSource.options.followingCameraOptions.zoomUpdatesAllowed = false
      viewportDataSource.followingMobileCamera.zoom = CGFloat(zoom ?? 10)
      viewportDataSource.options.followingCameraOptions.pitchUpdatesAllowed = false
      viewportDataSource.followingMobileCamera.pitch = 0
    } else {
      // Keep dynamic camera updates in following mode so turn-by-turn camera behavior
      // (zoom/pitch/bearing adaptation) remains managed by the SDK.
      viewportDataSource.options.followingCameraOptions.pitchUpdatesAllowed = true
      viewportDataSource.options.followingCameraOptions.zoomUpdatesAllowed = true
      viewportDataSource.options.followingCameraOptions.bearingUpdatesAllowed = true

      if let pitch = pitch {
        viewportDataSource.followingMobileCamera.pitch = CGFloat(max(0, min(pitch, 85)))
      }

      if let zoom = zoom {
        viewportDataSource.followingMobileCamera.zoom = CGFloat(max(1, min(zoom, 22)))
      }
    }

    navigationMapView.navigationCamera.follow()
  }

  private func buildNavigationOptions(
    navigationService: NavigationService,
    mapStyleUri: String?
  ) -> MapboxNavigation.NavigationOptions {
    guard
      let styleUri = mapStyleUri?.trimmingCharacters(in: .whitespacesAndNewlines),
      !styleUri.isEmpty,
      let styleURL = URL(string: styleUri)
    else {
      return MapboxNavigation.NavigationOptions(navigationService: navigationService)
    }

    let dayStyle = DayStyle()
    dayStyle.mapStyleURL = styleURL

    let nightStyle = NightStyle()
    nightStyle.mapStyleURL = styleURL

    return MapboxNavigation.NavigationOptions(
      styles: [dayStyle, nightStyle],
      navigationService: navigationService
    )
  }

  private static func currentTopViewController() -> UIViewController? {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let keyWindow = scenes
      .flatMap { $0.windows }
      .first(where: { $0.isKeyWindow })

    var top = keyWindow?.rootViewController
    while let presented = top?.presentedViewController {
      top = presented
    }
    return top
  }
}

// MARK: - NavigationViewControllerDelegate
extension MapboxNavigationModule: NavigationViewControllerDelegate {
  public func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didUpdate progress: RouteProgress,
    with location: CLLocation,
    rawLocation: CLLocation
  ) {
    if currentCameraMode.lowercased() == "following" {
      navigationViewController.navigationMapView?.navigationCamera.follow()
    }

    sendEvent("onLocationChange", [
      "latitude": location.coordinate.latitude,
      "longitude": location.coordinate.longitude,
      "bearing": location.course,
      "speed": location.speed,
      "altitude": location.altitude,
      "accuracy": location.horizontalAccuracy
    ])
    
    sendEvent("onRouteProgressChange", [
      "distanceTraveled": progress.distanceTraveled,
      "distanceRemaining": progress.distanceRemaining,
      "durationRemaining": progress.durationRemaining,
      "fractionTraveled": progress.fractionTraveled
    ])

    sendEvent("onBannerInstruction", [
      "primaryText": progress.currentLegProgress.currentStep.instructions,
      "stepDistanceRemaining": progress.currentLegProgress.currentStepProgress.distanceRemaining
    ])
  }
  
  public func navigationViewController(
    _ navigationViewController: NavigationViewController,
    didArriveAt waypoint: Waypoint
  ) -> Bool {
    sendEvent("onArrive", [
      "name": waypoint.name ?? ""
    ])
    return true
  }
  
  public func navigationViewControllerDidDismiss(
    _ navigationViewController: NavigationViewController,
    byCanceling canceled: Bool
  ) {
    if canceled {
      sendEvent("onCancelNavigation", [:])
    }
    self.navigationViewController = nil
    self.isCurrentlyNavigating = false
  }
}

// MARK: - Helper structs
struct NavigationStartOptions: Record {
  @Field var startOrigin: Coordinate?
  @Field var destination: DestinationWaypoint
  @Field var waypoints: [DestinationWaypoint]?
  @Field var shouldSimulateRoute: Bool?
  @Field var distanceUnit: String?
  @Field var language: String?
  @Field var mute: Bool?
  @Field var voiceVolume: Double?
  @Field var cameraPitch: Double?
  @Field var cameraZoom: Double?
  @Field var cameraMode: String?
  @Field var mapStyleUri: String?
  @Field var routeAlternatives: Bool?
  @Field var showsSpeedLimits: Bool?
  @Field var showsWayNameLabel: Bool?
}

struct Coordinate: Record {
  @Field var latitude: Double
  @Field var longitude: Double
  
  func toCLLocationCoordinate2D() -> CLLocationCoordinate2D? {
    guard latitude >= -90 && latitude <= 90,
          longitude >= -180 && longitude <= 180 else {
      return nil
    }
    return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
  }
}

struct DestinationWaypoint: Record {
  @Field var latitude: Double
  @Field var longitude: Double
  @Field var name: String?
  
  func toCLLocationCoordinate2D() -> CLLocationCoordinate2D? {
    guard latitude >= -90 && latitude <= 90,
          longitude >= -180 && longitude <= 180 else {
      return nil
    }
    return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
  }
}

private final class CurrentLocationResolver: NSObject, CLLocationManagerDelegate {
  private let locationManager = CLLocationManager()
  private var completion: ((Result<CLLocationCoordinate2D, Error>) -> Void)?
  private var timeoutWorkItem: DispatchWorkItem?

  enum ResolverError: LocalizedError {
    case permissionDenied
    case unavailable
    case timeout

    var errorDescription: String? {
      switch self {
      case .permissionDenied:
        return "Location permission denied."
      case .unavailable:
        return "Unable to resolve current location."
      case .timeout:
        return "Timed out while resolving current location."
      }
    }
  }

  func resolve(
    timeout: TimeInterval = 8.0,
    completion: @escaping (Result<CLLocationCoordinate2D, Error>) -> Void
  ) {
    self.completion = completion
    locationManager.delegate = self
    locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters

    let status = locationAuthorizationStatus()
    switch status {
    case .notDetermined:
      locationManager.requestWhenInUseAuthorization()
    case .restricted, .denied:
      finish(.failure(ResolverError.permissionDenied))
      return
    default:
      requestLocation()
    }

    let timeoutTask = DispatchWorkItem { [weak self] in
      self?.finish(.failure(ResolverError.timeout))
    }
    timeoutWorkItem = timeoutTask
    DispatchQueue.main.asyncAfter(deadline: .now() + timeout, execute: timeoutTask)
  }

  private func requestLocation() {
    if let coordinate = locationManager.location?.coordinate {
      finish(.success(coordinate))
      return
    }
    locationManager.requestLocation()
  }

  private func locationAuthorizationStatus() -> CLAuthorizationStatus {
    if #available(iOS 14.0, *) {
      return locationManager.authorizationStatus
    }
    return CLLocationManager.authorizationStatus()
  }

  func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    let status = locationAuthorizationStatus()
    switch status {
    case .authorizedAlways, .authorizedWhenInUse:
      requestLocation()
    case .denied, .restricted:
      finish(.failure(ResolverError.permissionDenied))
    default:
      break
    }
  }

  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    guard let coordinate = locations.last?.coordinate else {
      finish(.failure(ResolverError.unavailable))
      return
    }
    finish(.success(coordinate))
  }

  func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    if let coordinate = manager.location?.coordinate {
      finish(.success(coordinate))
      return
    }
    finish(.failure(error))
  }

  private func finish(_ result: Result<CLLocationCoordinate2D, Error>) {
    guard let completion = completion else {
      return
    }
    timeoutWorkItem?.cancel()
    timeoutWorkItem = nil
    self.completion = nil
    locationManager.delegate = nil
    completion(result)
  }
}

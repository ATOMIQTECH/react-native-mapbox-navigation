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
  private var warnedUnsupportedOptions = Set<String>()
  private weak var fullScreenBottomSheetView: UIView?
  private weak var fullScreenBottomSheetPrimaryLabel: UILabel?
  private weak var fullScreenBottomSheetSecondaryLabel: UILabel?
  private weak var fullScreenBottomSheetPrimaryActionButton: UIButton?
  private weak var fullScreenBottomSheetSecondaryActionButton: UIButton?
  private var fullScreenBottomSheetCollapsedHeight: CGFloat = 120
  private var fullScreenBottomSheetExpandedHeight: CGFloat = 250
  private var fullScreenBottomSheetExpanded = false
  private var fullScreenBottomSheetTopConstraint: NSLayoutConstraint?
  private var fullScreenPrimaryActionBehavior = "stopNavigation"
  private var fullScreenSecondaryActionBehavior = "emitEvent"
  
  public func definition() -> ModuleDefinition {
    Name("MapboxNavigationModule")
    
    // Events that can be sent to JS
    Events(
      "onLocationChange",
      "onRouteProgressChange",
      "onBannerInstruction",
      "onArrive",
      "onCancelNavigation",
      "onError",
      "onBottomSheetActionPress"
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
        "onError",
        "onBottomSheetActionPress"
      )
      
      Prop("startOrigin") { (view: MapboxNavigationView, origin: [String: Any]?) in
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

      Prop("mapStyleUriDay") { (view: MapboxNavigationView, mapStyleUriDay: String) in
        view.mapStyleUriDay = mapStyleUriDay
      }

      Prop("mapStyleUriNight") { (view: MapboxNavigationView, mapStyleUriNight: String) in
        view.mapStyleUriNight = mapStyleUriNight
      }

      Prop("uiTheme") { (view: MapboxNavigationView, uiTheme: String) in
        view.uiTheme = uiTheme
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

      Prop("showsTripProgress") { (view: MapboxNavigationView, showsTripProgress: Bool) in
        view.showsTripProgress = showsTripProgress
      }

      Prop("showsManeuverView") { (view: MapboxNavigationView, showsManeuverView: Bool) in
        view.showsManeuverView = showsManeuverView
      }

      Prop("showsActionButtons") { (view: MapboxNavigationView, showsActionButtons: Bool) in
        view.showsActionButtons = showsActionButtons
      }

      Prop("showsReportFeedback") { (view: MapboxNavigationView, showsReportFeedback: Bool) in
        view.showsReportFeedback = showsReportFeedback
      }

      Prop("showsEndOfRouteFeedback") { (view: MapboxNavigationView, showsEndOfRouteFeedback: Bool) in
        view.showsEndOfRouteFeedback = showsEndOfRouteFeedback
      }

      Prop("showsContinuousAlternatives") { (view: MapboxNavigationView, showsContinuousAlternatives: Bool) in
        view.showsContinuousAlternatives = showsContinuousAlternatives
      }

      Prop("usesNightStyleWhileInTunnel") { (view: MapboxNavigationView, usesNightStyleWhileInTunnel: Bool) in
        view.usesNightStyleWhileInTunnel = usesNightStyleWhileInTunnel
      }

      Prop("routeLineTracksTraversal") { (view: MapboxNavigationView, routeLineTracksTraversal: Bool) in
        view.routeLineTracksTraversal = routeLineTracksTraversal
      }

      Prop("annotatesIntersectionsAlongRoute") { (view: MapboxNavigationView, annotatesIntersectionsAlongRoute: Bool) in
        view.annotatesIntersectionsAlongRoute = annotatesIntersectionsAlongRoute
      }
      
      Prop("distanceUnit") { (view: MapboxNavigationView, unit: String) in
        view.distanceUnit = unit
      }
      
      Prop("language") { (view: MapboxNavigationView, language: String) in
        view.language = language
      }
    }
  }
  
  private func configuredMapboxPublicToken() -> String? {
    guard let raw = Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String else {
      return nil
    }
    let token = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    guard token.hasPrefix("pk."), token.count > 20 else {
      return nil
    }
    return token
  }

  private func startNavigation(options: NavigationStartOptions, promise: Promise) {
    guard let destination = options.destination.toCLLocationCoordinate2D() else {
      self.emitErrorAndShowScreen([
        "code": "INVALID_COORDINATES",
        "message": "Invalid coordinates provided"
      ])
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
        self.emitErrorAndShowScreen([
          "code": "CURRENT_LOCATION_UNAVAILABLE",
          "message": error.localizedDescription
        ])
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
    guard configuredMapboxPublicToken() != nil else {
      let message = "Missing or invalid MBXAccessToken. Add the package plugin to app.json and set EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN before prebuild."
      self.emitErrorAndShowScreen([
        "code": "MISSING_ACCESS_TOKEN",
        "message": message
      ])
      promise.reject("MISSING_ACCESS_TOKEN", message)
      return
    }

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
          self.emitErrorAndShowScreen([
            "code": "NO_ROUTE",
            "message": "No route found"
          ])
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
          mapStyleUri: options.mapStyleUri,
          mapStyleUriDay: options.mapStyleUriDay,
          mapStyleUriNight: options.mapStyleUriNight
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
        self.applyInterfaceStyle(to: viewController, theme: options.uiTheme)
        self.currentCameraMode = options.cameraMode ?? "following"
        viewController.showsSpeedLimits = options.showsSpeedLimits ?? true
        let hasBottomSheetConfig = options.bottomSheet != nil
        if options.showsManeuverView != nil && !hasBottomSheetConfig {
          self.warnUnsupportedOptionOnce(
            key: "showsManeuverView",
            message: "showsManeuverView is currently not supported on iOS full-screen navigation and will be ignored."
          )
        }
        if options.showsTripProgress != nil && !hasBottomSheetConfig {
          self.warnUnsupportedOptionOnce(
            key: "showsTripProgress",
            message: "showsTripProgress is currently not supported on iOS full-screen navigation and will be ignored."
          )
        }
        if options.showsActionButtons != nil && !hasBottomSheetConfig {
          self.warnUnsupportedOptionOnce(
            key: "showsActionButtons",
            message: "showsActionButtons is currently not supported on iOS full-screen navigation and will be ignored."
          )
        }
        if options.showsReportFeedback != nil {
          self.warnUnsupportedOptionOnce(
            key: "showsReportFeedback",
            message: "showsReportFeedback is currently not supported on iOS full-screen navigation and will be ignored."
          )
        }
        if options.showsEndOfRouteFeedback != nil {
          self.warnUnsupportedOptionOnce(
            key: "showsEndOfRouteFeedback",
            message: "showsEndOfRouteFeedback is currently not supported on iOS full-screen navigation and will be ignored."
          )
        }
        if options.showsContinuousAlternatives != nil {
          self.warnUnsupportedOptionOnce(
            key: "showsContinuousAlternatives",
            message: "showsContinuousAlternatives is currently not supported on iOS full-screen navigation and will be ignored."
          )
        }
        if options.usesNightStyleWhileInTunnel != nil {
          self.warnUnsupportedOptionOnce(
            key: "usesNightStyleWhileInTunnel",
            message: "usesNightStyleWhileInTunnel is currently not supported on iOS full-screen navigation and will be ignored."
          )
        }
        if options.routeLineTracksTraversal != nil {
          self.warnUnsupportedOptionOnce(
            key: "routeLineTracksTraversal",
            message: "routeLineTracksTraversal is currently not supported on iOS full-screen navigation and will be ignored."
          )
        }
        if options.annotatesIntersectionsAlongRoute != nil {
          self.warnUnsupportedOptionOnce(
            key: "annotatesIntersectionsAlongRoute",
            message: "annotatesIntersectionsAlongRoute is currently not supported on iOS full-screen navigation and will be ignored."
          )
        }
        if options.showsWayNameLabel != nil {
          self.warnUnsupportedOptionOnce(
            key: "showsWayNameLabel",
            message: "showsWayNameLabel is currently not supported on iOS NavigationViewController and will be ignored."
          )
        }
        self.configureFullScreenBottomSheetIfNeeded(
          on: viewController,
          options: options
        )
        
        self.navigationViewController = viewController
        self.isCurrentlyNavigating = true
        
        if let rootVC = Self.currentTopViewController() {
          rootVC.present(viewController, animated: true) {
            promise.resolve(nil)
          }
        } else {
          self.emitErrorAndShowScreen([
            "code": "NO_ROOT_VC",
            "message": "Could not find root view controller"
          ])
          promise.reject("NO_ROOT_VC", "Could not find root view controller")
        }
        
      case .failure(let error):
        let (code, message) = self.mapDirectionsError(error)
        self.emitErrorAndShowScreen([
          "code": code,
          "message": message
        ])
        promise.reject(code, message)
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
  
  private func emitErrorAndShowScreen(_ payload: [String: Any]) {
    sendEvent("onError", payload)

    guard let navVC = navigationViewController else {
      return
    }

    if navVC.presentingViewController == nil {
      navigationViewController = nil
      isCurrentlyNavigating = false
      detachFullScreenBottomSheet()
      return
    }

    navVC.dismiss(animated: true) {
      self.navigationViewController = nil
      self.isCurrentlyNavigating = false
      self.detachFullScreenBottomSheet()
    }
  }

  private func mapDirectionsError(_ error: Error) -> (String, String) {
    let message = error.localizedDescription
    let lowered = message.lowercased()

    if lowered.contains("401") || lowered.contains("unauthorized") {
      return ("MAPBOX_TOKEN_INVALID", "Route fetch failed: unauthorized. Check EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN and token scopes.")
    }
    if lowered.contains("403") || lowered.contains("forbidden") {
      return ("MAPBOX_TOKEN_FORBIDDEN", "Route fetch failed: access forbidden. Verify token scopes and account permissions.")
    }
    if lowered.contains("429") || lowered.contains("rate") {
      return ("MAPBOX_RATE_LIMITED", "Route fetch failed: rate limited by Mapbox.")
    }

    return ("ROUTE_ERROR", message)
  }

  private func stopNavigation(promise: Promise) {
    guard let navVC = navigationViewController else {
      detachFullScreenBottomSheet()
      promise.resolve(nil)
      return
    }
    
    navVC.dismiss(animated: true) {
      self.navigationViewController = nil
      self.isCurrentlyNavigating = false
      self.detachFullScreenBottomSheet()
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
    mapStyleUri: String?,
    mapStyleUriDay: String?,
    mapStyleUriNight: String?
  ) -> MapboxNavigation.NavigationOptions {
    let dayStyleURL = normalizedStyleURL(
      primary: mapStyleUriDay,
      fallback: mapStyleUri
    )
    let nightStyleURL = normalizedStyleURL(
      primary: mapStyleUriNight,
      fallback: mapStyleUriDay ?? mapStyleUri
    )

    guard dayStyleURL != nil || nightStyleURL != nil else {
      return MapboxNavigation.NavigationOptions(navigationService: navigationService)
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

    return MapboxNavigation.NavigationOptions(
      styles: [dayStyle, nightStyle],
      navigationService: navigationService
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

  private func applyInterfaceStyle(to viewController: UIViewController, theme: String?) {
    let normalizedTheme = theme?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? "system"
    switch normalizedTheme {
    case "light", "day":
      viewController.overrideUserInterfaceStyle = .light
    case "dark", "night":
      viewController.overrideUserInterfaceStyle = .dark
    default:
      viewController.overrideUserInterfaceStyle = .unspecified
    }
  }

  private func configureFullScreenBottomSheetIfNeeded(
    on viewController: NavigationViewController,
    options: NavigationStartOptions
  ) {
    guard options.bottomSheet?.enabled != false else {
      detachFullScreenBottomSheet()
      return
    }

    let collapsed = CGFloat(options.bottomSheet?.collapsedHeight ?? 120)
    let expanded = CGFloat(options.bottomSheet?.expandedHeight ?? 250)
    let horizontalPadding = CGFloat(options.bottomSheet?.contentHorizontalPadding ?? 14)
    let contentTopSpacing = CGFloat(options.bottomSheet?.contentTopSpacing ?? 0)
    let contentBottomPadding = CGFloat(options.bottomSheet?.contentBottomPadding ?? 10)
    let primaryFontSize = CGFloat(options.bottomSheet?.primaryTextFontSize ?? 16)
    let secondaryFontSize = CGFloat(options.bottomSheet?.secondaryTextFontSize ?? 13)
    let actionButtonFontSize = CGFloat(options.bottomSheet?.actionButtonFontSize ?? 14)
    let actionButtonHeight = CGFloat(options.bottomSheet?.actionButtonHeight ?? 40)
    fullScreenBottomSheetCollapsedHeight = max(72, min(collapsed, 320))
    fullScreenBottomSheetExpandedHeight = max(
      fullScreenBottomSheetCollapsedHeight,
      min(expanded, 500)
    )
    fullScreenBottomSheetExpanded = options.bottomSheet?.initialState == "expanded"

    let container = UIView()
    container.translatesAutoresizingMaskIntoConstraints = false
    container.backgroundColor = colorFromHex(options.bottomSheet?.backgroundColor)
      ?? UIColor(white: 0.06, alpha: 0.92)
    let cornerRadius = CGFloat(options.bottomSheet?.cornerRadius ?? 16)
    container.layer.cornerRadius = max(0, min(cornerRadius, 28))
    container.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
    container.clipsToBounds = true

    let handle = UIView()
    handle.translatesAutoresizingMaskIntoConstraints = false
    handle.backgroundColor = colorFromHex(options.bottomSheet?.handleColor)
      ?? UIColor(white: 0.85, alpha: 0.75)
    handle.layer.cornerRadius = 2.5

    let primaryLabel = UILabel()
    primaryLabel.translatesAutoresizingMaskIntoConstraints = false
    primaryLabel.textColor = colorFromHex(options.bottomSheet?.primaryTextColor) ?? .white
    primaryLabel.font = .systemFont(ofSize: max(10, min(primaryFontSize, 30)), weight: .semibold)
    primaryLabel.numberOfLines = 2
    primaryLabel.text = "Starting navigation..."

    let secondaryLabel = UILabel()
    secondaryLabel.translatesAutoresizingMaskIntoConstraints = false
    secondaryLabel.textColor = colorFromHex(options.bottomSheet?.secondaryTextColor)
      ?? UIColor(white: 0.88, alpha: 0.9)
    secondaryLabel.font = .systemFont(ofSize: max(10, min(secondaryFontSize, 24)), weight: .medium)
    secondaryLabel.numberOfLines = 2
    secondaryLabel.text = "Waiting for route progress"

    let stack = UIStackView(arrangedSubviews: [primaryLabel, secondaryLabel])
    stack.translatesAutoresizingMaskIntoConstraints = false
    stack.axis = .vertical
    stack.spacing = 4

    fullScreenPrimaryActionBehavior = options.bottomSheet?.primaryActionButtonBehavior ?? "stopNavigation"
    fullScreenSecondaryActionBehavior = options.bottomSheet?.secondaryActionButtonBehavior ?? "emitEvent"

    let actionButton = UIButton(type: .system)
    actionButton.translatesAutoresizingMaskIntoConstraints = false
    actionButton.setTitle(options.bottomSheet?.actionButtonTitle ?? "End", for: .normal)
    actionButton.setTitleColor(colorFromHex(options.bottomSheet?.actionButtonTextColor) ?? .white, for: .normal)
    actionButton.backgroundColor = colorFromHex(options.bottomSheet?.actionButtonBackgroundColor)
      ?? UIColor(red: 0.2, green: 0.35, blue: 0.8, alpha: 1)
    actionButton.layer.cornerRadius = CGFloat(options.bottomSheet?.actionButtonCornerRadius ?? 8)
    actionButton.layer.borderColor = colorFromHex(options.bottomSheet?.actionButtonBorderColor)?.cgColor
    actionButton.layer.borderWidth = CGFloat(max(0, min(options.bottomSheet?.actionButtonBorderWidth ?? 0, 6)))
    actionButton.titleLabel?.font = .systemFont(ofSize: max(10, min(actionButtonFontSize, 24)), weight: .semibold)
    actionButton.addTarget(self, action: #selector(onBottomSheetPrimaryActionTap), for: .touchUpInside)
    actionButton.isHidden = options.showsActionButtons == false || options.bottomSheet?.showsActionButtons == false

    let secondaryTitle = options.bottomSheet?.secondaryActionButtonTitle?.trimmingCharacters(in: .whitespacesAndNewlines)
    let hasSecondaryAction = !(secondaryTitle?.isEmpty ?? true)
    let secondaryActionButton = UIButton(type: .system)
    secondaryActionButton.translatesAutoresizingMaskIntoConstraints = false
    secondaryActionButton.setTitle(secondaryTitle, for: .normal)
    secondaryActionButton.setTitleColor(
      colorFromHex(options.bottomSheet?.secondaryActionButtonTextColor)
        ?? colorFromHex(options.bottomSheet?.actionButtonTextColor)
        ?? .white,
      for: .normal
    )
    secondaryActionButton.backgroundColor = colorFromHex(options.bottomSheet?.secondaryActionButtonBackgroundColor)
      ?? UIColor(white: 1, alpha: 0.12)
    secondaryActionButton.layer.cornerRadius = CGFloat(options.bottomSheet?.actionButtonCornerRadius ?? 8)
    secondaryActionButton.layer.borderColor = colorFromHex(options.bottomSheet?.actionButtonBorderColor)?.cgColor
    secondaryActionButton.layer.borderWidth = CGFloat(max(0, min(options.bottomSheet?.actionButtonBorderWidth ?? 0, 6)))
    secondaryActionButton.titleLabel?.font = .systemFont(ofSize: max(10, min(actionButtonFontSize, 24)), weight: .semibold)
    secondaryActionButton.addTarget(self, action: #selector(onBottomSheetSecondaryActionTap), for: .touchUpInside)
    secondaryActionButton.isHidden = !hasSecondaryAction || actionButton.isHidden

    let showManeuverSection = options.showsManeuverView != false && options.bottomSheet?.showsManeuverView != false
    let showTripSection = options.showsTripProgress != false && options.bottomSheet?.showsTripProgress != false
    primaryLabel.isHidden = !showManeuverSection
    secondaryLabel.isHidden = !showTripSection

    container.addSubview(handle)
    container.addSubview(stack)
    let actionStack = UIStackView(arrangedSubviews: [actionButton, secondaryActionButton])
    actionStack.translatesAutoresizingMaskIntoConstraints = false
    actionStack.axis = .horizontal
    actionStack.distribution = .fillEqually
    actionStack.spacing = 8
    container.addSubview(actionStack)
    viewController.view.addSubview(container)

    let topConstraint = container.topAnchor.constraint(
      equalTo: viewController.view.safeAreaLayoutGuide.bottomAnchor,
      constant: -currentBottomSheetHeight()
    )
    fullScreenBottomSheetTopConstraint = topConstraint

    NSLayoutConstraint.activate([
      container.leadingAnchor.constraint(equalTo: viewController.view.leadingAnchor),
      container.trailingAnchor.constraint(equalTo: viewController.view.trailingAnchor),
      container.bottomAnchor.constraint(equalTo: viewController.view.bottomAnchor),
      topConstraint,

      handle.topAnchor.constraint(equalTo: container.topAnchor, constant: 8),
      handle.centerXAnchor.constraint(equalTo: container.centerXAnchor),
      handle.widthAnchor.constraint(equalToConstant: 42),
      handle.heightAnchor.constraint(equalToConstant: 5),

      stack.topAnchor.constraint(equalTo: handle.bottomAnchor, constant: 10 + max(0, min(contentTopSpacing, 20))),
      stack.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: max(0, min(horizontalPadding, 48))),
      stack.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -max(0, min(horizontalPadding, 48))),

      actionStack.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: max(0, min(horizontalPadding, 48))),
      actionStack.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -max(0, min(horizontalPadding, 48))),
      actionStack.bottomAnchor.constraint(equalTo: container.safeAreaLayoutGuide.bottomAnchor, constant: -max(0, min(contentBottomPadding, 40))),
      actionStack.heightAnchor.constraint(equalToConstant: max(32, min(actionButtonHeight, 72))),
      actionStack.topAnchor.constraint(greaterThanOrEqualTo: stack.bottomAnchor, constant: 10),
    ])

    if options.bottomSheet?.enableTapToToggle != false {
      let tap = UITapGestureRecognizer(target: self, action: #selector(onBottomSheetToggleTap))
      tap.cancelsTouchesInView = false
      container.addGestureRecognizer(tap)
    }
    handle.isHidden = options.bottomSheet?.showHandle == false

    fullScreenBottomSheetView?.removeFromSuperview()
    fullScreenBottomSheetView = container
    fullScreenBottomSheetPrimaryLabel = primaryLabel
    fullScreenBottomSheetSecondaryLabel = secondaryLabel
    fullScreenBottomSheetPrimaryActionButton = actionButton
    fullScreenBottomSheetSecondaryActionButton = secondaryActionButton
  }

  private func updateFullScreenBottomSheet(
    instruction: String?,
    progress: RouteProgress?
  ) {
    guard fullScreenBottomSheetView != nil else {
      return
    }
    if let instruction, !instruction.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
      fullScreenBottomSheetPrimaryLabel?.text = instruction
    }
    if let progress {
      let remainingMeters = max(0, Int(progress.distanceRemaining.rounded()))
      let percent = Int((max(0, min(progress.fractionTraveled, 1)) * 100).rounded())
      fullScreenBottomSheetSecondaryLabel?.text = "\(remainingMeters) m remaining • \(percent)% completed"
    }
  }

  private func detachFullScreenBottomSheet() {
    fullScreenBottomSheetView?.removeFromSuperview()
    fullScreenBottomSheetView = nil
    fullScreenBottomSheetPrimaryLabel = nil
    fullScreenBottomSheetSecondaryLabel = nil
    fullScreenBottomSheetPrimaryActionButton = nil
    fullScreenBottomSheetSecondaryActionButton = nil
    fullScreenBottomSheetTopConstraint = nil
  }

  private func currentBottomSheetHeight() -> CGFloat {
    fullScreenBottomSheetExpanded
      ? fullScreenBottomSheetExpandedHeight
      : fullScreenBottomSheetCollapsedHeight
  }

  @objc
  private func onBottomSheetToggleTap() {
    guard fullScreenBottomSheetView != nil else {
      return
    }
    fullScreenBottomSheetExpanded.toggle()
    fullScreenBottomSheetTopConstraint?.constant = -currentBottomSheetHeight()
    UIView.animate(withDuration: 0.2) {
      self.navigationViewController?.view.layoutIfNeeded()
    }
  }

  @objc
  private func onBottomSheetPrimaryActionTap() {
    if fullScreenPrimaryActionBehavior == "emitEvent" {
      sendEvent("onBottomSheetActionPress", [
        "actionId": "primary"
      ])
      return
    }
    if let navVC = navigationViewController {
      navVC.dismiss(animated: true) {
        self.navigationViewController = nil
        self.isCurrentlyNavigating = false
        self.detachFullScreenBottomSheet()
      }
    }
  }

  @objc
  private func onBottomSheetSecondaryActionTap() {
    if fullScreenSecondaryActionBehavior == "none" {
      return
    }
    if fullScreenSecondaryActionBehavior == "stopNavigation" {
      if let navVC = navigationViewController {
        navVC.dismiss(animated: true) {
          self.navigationViewController = nil
          self.isCurrentlyNavigating = false
          self.detachFullScreenBottomSheet()
        }
      }
      return
    }
    sendEvent("onBottomSheetActionPress", [
      "actionId": "secondary"
    ])
  }

  private func colorFromHex(_ value: String?) -> UIColor? {
    guard var hex = value?.trimmingCharacters(in: .whitespacesAndNewlines), !hex.isEmpty else {
      return nil
    }
    if hex.hasPrefix("#") {
      hex.removeFirst()
    }
    if hex.count == 3 {
      hex = hex.map { "\($0)\($0)" }.joined()
    }
    guard hex.count == 6 || hex.count == 8 else {
      return nil
    }
    var intValue: UInt64 = 0
    guard Scanner(string: hex).scanHexInt64(&intValue) else {
      return nil
    }

    if hex.count == 6 {
      let r = CGFloat((intValue & 0xFF0000) >> 16) / 255.0
      let g = CGFloat((intValue & 0x00FF00) >> 8) / 255.0
      let b = CGFloat(intValue & 0x0000FF) / 255.0
      return UIColor(red: r, green: g, blue: b, alpha: 1.0)
    }

    let a = CGFloat((intValue & 0xFF000000) >> 24) / 255.0
    let r = CGFloat((intValue & 0x00FF0000) >> 16) / 255.0
    let g = CGFloat((intValue & 0x0000FF00) >> 8) / 255.0
    let b = CGFloat(intValue & 0x000000FF) / 255.0
    return UIColor(red: r, green: g, blue: b, alpha: a)
  }

  private func warnUnsupportedOptionOnce(key: String, message: String) {
    guard !warnedUnsupportedOptions.contains(key) else {
      return
    }
    warnedUnsupportedOptions.insert(key)
    NSLog("[MapboxNavigationModule] \(message)")
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
    updateFullScreenBottomSheet(
      instruction: progress.currentLegProgress.currentStep.instructions,
      progress: progress
    )
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
    self.detachFullScreenBottomSheet()
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
  @Field var mapStyleUriDay: String?
  @Field var mapStyleUriNight: String?
  @Field var uiTheme: String?
  @Field var routeAlternatives: Bool?
  @Field var showsSpeedLimits: Bool?
  @Field var showsWayNameLabel: Bool?
  @Field var showsTripProgress: Bool?
  @Field var showsManeuverView: Bool?
  @Field var showsActionButtons: Bool?
  @Field var showsReportFeedback: Bool?
  @Field var showsEndOfRouteFeedback: Bool?
  @Field var showsContinuousAlternatives: Bool?
  @Field var usesNightStyleWhileInTunnel: Bool?
  @Field var routeLineTracksTraversal: Bool?
  @Field var annotatesIntersectionsAlongRoute: Bool?
  @Field var bottomSheet: BottomSheetStartOptions?
}

struct BottomSheetStartOptions: Record {
  @Field var enabled: Bool?
  @Field var showsTripProgress: Bool?
  @Field var showsManeuverView: Bool?
  @Field var showsActionButtons: Bool?
  @Field var initialState: String?
  @Field var collapsedHeight: Double?
  @Field var expandedHeight: Double?
  @Field var contentHorizontalPadding: Double?
  @Field var contentBottomPadding: Double?
  @Field var contentTopSpacing: Double?
  @Field var showHandle: Bool?
  @Field var enableTapToToggle: Bool?
  @Field var backgroundColor: String?
  @Field var handleColor: String?
  @Field var primaryTextColor: String?
  @Field var secondaryTextColor: String?
  @Field var actionButtonBackgroundColor: String?
  @Field var actionButtonTextColor: String?
  @Field var actionButtonTitle: String?
  @Field var secondaryActionButtonTitle: String?
  @Field var primaryActionButtonBehavior: String?
  @Field var secondaryActionButtonBehavior: String?
  @Field var actionButtonBorderColor: String?
  @Field var actionButtonBorderWidth: Double?
  @Field var actionButtonCornerRadius: Double?
  @Field var secondaryActionButtonBackgroundColor: String?
  @Field var secondaryActionButtonTextColor: String?
  @Field var primaryTextFontSize: Double?
  @Field var secondaryTextFontSize: Double?
  @Field var actionButtonFontSize: Double?
  @Field var actionButtonHeight: Double?
  @Field var showDefaultContent: Bool?
  @Field var defaultManeuverTitle: String?
  @Field var defaultTripProgressTitle: String?
  @Field var cornerRadius: Double?
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

private final class NavigationErrorViewController: UIViewController {
  private let message: String

  init(message: String) {
    self.message = message
    super.init(nibName: nil, bundle: nil)
  }

  required init?(coder: NSCoder) {
    nil
  }

  override func viewDidLoad() {
    super.viewDidLoad()

    view.backgroundColor = UIColor(red: 11 / 255, green: 16 / 255, blue: 32 / 255, alpha: 1)

    let titleLabel = UILabel()
    titleLabel.translatesAutoresizingMaskIntoConstraints = false
    titleLabel.text = "Navigation Error"
    titleLabel.textColor = .white
    titleLabel.font = UIFont.systemFont(ofSize: 28, weight: .bold)
    titleLabel.textAlignment = .center
    titleLabel.numberOfLines = 0

    let messageLabel = UILabel()
    messageLabel.translatesAutoresizingMaskIntoConstraints = false
    messageLabel.text = message
    messageLabel.textColor = UIColor(red: 214 / 255, green: 228 / 255, blue: 255 / 255, alpha: 1)
    messageLabel.font = UIFont.systemFont(ofSize: 16, weight: .regular)
    messageLabel.textAlignment = .center
    messageLabel.numberOfLines = 0

    let closeButton = UIButton(type: .system)
    closeButton.translatesAutoresizingMaskIntoConstraints = false
    closeButton.setTitle("Back", for: .normal)
    closeButton.titleLabel?.font = UIFont.systemFont(ofSize: 17, weight: .semibold)
    closeButton.backgroundColor = UIColor.white.withAlphaComponent(0.15)
    closeButton.setTitleColor(.white, for: .normal)
    closeButton.layer.cornerRadius = 10
    closeButton.contentEdgeInsets = UIEdgeInsets(top: 12, left: 16, bottom: 12, right: 16)
    closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)

    let stack = UIStackView(arrangedSubviews: [titleLabel, messageLabel, closeButton])
    stack.translatesAutoresizingMaskIntoConstraints = false
    stack.axis = .vertical
    stack.alignment = .fill
    stack.spacing = 20

    view.addSubview(stack)

    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
      stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
      stack.centerYAnchor.constraint(equalTo: view.centerYAnchor),
    ])
  }

  @objc
  private func closeTapped() {
    dismiss(animated: true)
  }
}

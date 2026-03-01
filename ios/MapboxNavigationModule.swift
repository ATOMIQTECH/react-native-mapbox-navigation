import ExpoModulesCore
import MapboxNavigation
import MapboxDirections
import MapboxCoreNavigation
import CoreLocation
import UIKit

private final class NativeBannerGestureDelegate: NSObject, UIGestureRecognizerDelegate {
  var shouldReceiveTouch: ((UITouch) -> Bool)?
  var shouldBegin: ((UIGestureRecognizer) -> Bool)?

  func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
    return shouldBegin?(gestureRecognizer) ?? true
  }

  func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
    return shouldReceiveTouch?(touch) ?? true
  }

  func gestureRecognizer(
    _ gestureRecognizer: UIGestureRecognizer,
    shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
  ) -> Bool {
    return true
  }
}

public class MapboxNavigationModule: Module {
  private let sessionOwner = "fullscreen"
  private var navigationViewController: NavigationViewController?
  private var isCurrentlyNavigating = false
  private var currentLanguage = Locale.preferredLanguages.first ?? "en"
  private var currentCameraMode = "following"
  private var currentLocationResolver: CurrentLocationResolver?
  private var warnedUnsupportedOptions = Set<String>()
  private var isStartInProgress = false
  private weak var nativeBottomBannerCancelButton: UIButton?
  private weak var fullScreenBottomSheetView: UIView?
  private weak var fullScreenBottomSheetBackdropView: UIView?
  private weak var fullScreenBottomSheetPrimaryLabel: UILabel?
  private weak var fullScreenBottomSheetSecondaryLabel: UILabel?
  private weak var fullScreenBottomSheetPrimaryActionButton: UIButton?
  private weak var fullScreenBottomSheetSecondaryActionButton: UIButton?
  private var fullScreenBottomSheetCollapsedHeight: CGFloat = 120
  private var fullScreenBottomSheetExpandedHeight: CGFloat = 250
  private var fullScreenBottomSheetHiddenHeight: CGFloat = 0
  private var fullScreenBottomSheetExpanded = false
  private var fullScreenBottomSheetHidden = false
  private var fullScreenBottomSheetTopConstraint: NSLayoutConstraint?
  private weak var fullScreenBottomSheetHandleView: UIView?
  private var revealCustomSheetFromNativeBannerGesture = false
  private weak var nativeBottomBannerGestureTargetView: UIView?
  private weak var nativeBottomBannerRevealHotzoneView: UIView?
  private var nativeBottomBannerRevealZoneHeight: CGFloat = 0
  private var nativeBottomBannerRevealTrailingExclusion: CGFloat = 0
  private var nativeBottomBannerTapGesture: UITapGestureRecognizer?
  private var nativeBottomBannerPanGesture: UIPanGestureRecognizer?
  private let nativeBannerGestureDelegate = NativeBannerGestureDelegate()
  private var fullScreenPrimaryActionBehavior = "stopNavigation"
  private var fullScreenSecondaryActionBehavior = "emitEvent"
  private var fullScreenCustomActionByTag: [Int: String] = [:]
  private var fullScreenCustomActionTagSeed = 5_000
  private var isUsingCustomNativeBottomSheet = false
  private var latestJourneyLocation: CLLocation?
  private var latestJourneyInstructionPrimary: String?
  private var latestJourneyInstructionSecondary: String?
  private var latestJourneyStepDistanceRemaining: CLLocationDistance?
  private var latestJourneyProgressDistanceRemaining: CLLocationDistance?
  private var latestJourneyProgressDurationRemaining: TimeInterval?
  private var latestJourneyProgressFractionTraveled: Double?
  private var bottomSheetShowCurrentStreet = true
  private var bottomSheetShowRemainingDistance = true
  private var bottomSheetShowRemainingDuration = true
  private var bottomSheetShowETA = true
  private var bottomSheetShowCompletionPercent = true
  
  public func definition() -> ModuleDefinition {
    Name("MapboxNavigationModule")
    
    // Events that can be sent to JS
    Events(
      "onLocationChange",
      "onRouteProgressChange",
      "onJourneyDataChange",
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
        "onJourneyDataChange",
        "onBannerInstruction",
        "onArrive",
        "onCancelNavigation",
        "onError",
        "onBottomSheetActionPress"
      )
      
      Prop("startOrigin") { (view: MapboxNavigationView, origin: [String: Any]?) in
        view.startOrigin = origin
      }

      Prop("enabled") { (view: MapboxNavigationView, enabled: Bool) in
        view.enabled = enabled
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
    guard !isStartInProgress, navigationViewController == nil, !isCurrentlyNavigating else {
      let message = "A navigation session is already starting or active."
      promise.reject("NAVIGATION_ALREADY_ACTIVE", message)
      return
    }

    guard configuredMapboxPublicToken() != nil else {
      let message = "Missing or invalid MBXAccessToken. Add the package plugin to app.json and set EXPO_PUBLIC_MAPBOX_ACCESS_TOKEN before prebuild."
      self.emitErrorAndShowScreen([
        "code": "MISSING_ACCESS_TOKEN",
        "message": message
      ])
      promise.reject("MISSING_ACCESS_TOKEN", message)
      return
    }

    guard NavigationSessionRegistry.shared.acquire(owner: sessionOwner) else {
      let message = "Another navigation session is already active. Stop embedded/full-screen navigation before starting a new one."
      self.emitErrorAndShowScreen([
        "code": "NAVIGATION_SESSION_CONFLICT",
        "message": message
      ])
      promise.reject("NAVIGATION_SESSION_CONFLICT", message)
      return
    }

    isStartInProgress = true

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
        self.isStartInProgress = false
        guard response.routes?.first != nil else {
          NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
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
        let requestedBottomSheetMode = options.bottomSheet?.mode?
          .trimmingCharacters(in: .whitespacesAndNewlines)
          .lowercased()
        let useCustomNativeBottomSheet = requestedBottomSheetMode == "customnative"
          || requestedBottomSheetMode == "overlay"
        let shouldRenderCustomNativeBottomSheet =
          useCustomNativeBottomSheet && options.bottomSheet?.enabled != false
        let revealFromNativeBannerGesture =
          shouldRenderCustomNativeBottomSheet && (options.bottomSheet?.revealOnNativeBannerGesture ?? true)
        self.isUsingCustomNativeBottomSheet = shouldRenderCustomNativeBottomSheet
        self.revealCustomSheetFromNativeBannerGesture = revealFromNativeBannerGesture
        self.setNativeBottomBannerHidden(shouldRenderCustomNativeBottomSheet, in: viewController)
        if shouldRenderCustomNativeBottomSheet {
          self.configureFullScreenBottomSheetIfNeeded(on: viewController, options: options)
          self.configureNativeBannerGestureRevealIfNeeded(on: viewController, options: options)
        } else {
          self.clearNativeBannerGestureRevealIfNeeded()
          self.applyNativeBottomBannerCustomization(to: viewController, options: options)
          self.detachFullScreenBottomSheet()
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
        if options.bottomSheet?.mode == "overlay" {
          self.warnUnsupportedOptionOnce(
            key: "bottomSheet.mode.overlay.fullscreen",
            message: "bottomSheet.mode='overlay' is treated as bottomSheet.mode='customNative' for iOS full-screen navigation."
          )
        }
        
        self.navigationViewController = viewController
        self.isCurrentlyNavigating = true
        
        if let rootVC = Self.currentTopViewController() {
          rootVC.present(viewController, animated: true) {
            promise.resolve(nil)
          }
        } else {
          NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
          self.emitErrorAndShowScreen([
            "code": "NO_ROOT_VC",
            "message": "Could not find root view controller"
          ])
          promise.reject("NO_ROOT_VC", "Could not find root view controller")
        }
        
      case .failure(let error):
        self.isStartInProgress = false
        NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
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
    isStartInProgress = false
    NavigationSessionRegistry.shared.release(owner: sessionOwner)

    guard let navVC = navigationViewController else {
      return
    }

    if navVC.presentingViewController == nil {
      navigationViewController = nil
      isCurrentlyNavigating = false
      isUsingCustomNativeBottomSheet = false
      detachFullScreenBottomSheet()
      return
    }

    navVC.dismiss(animated: true) {
      self.navigationViewController = nil
      self.isCurrentlyNavigating = false
      self.isUsingCustomNativeBottomSheet = false
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
    dismissActiveNavigation(emitCancelEvent: false) {
      promise.resolve(nil)
    }
  }

  private func dismissActiveNavigation(
    emitCancelEvent: Bool,
    completion: (() -> Void)? = nil
  ) {
    isStartInProgress = false

    let finalize = {
      self.navigationViewController = nil
      self.isCurrentlyNavigating = false
      self.isUsingCustomNativeBottomSheet = false
      NavigationSessionRegistry.shared.release(owner: self.sessionOwner)
      self.detachFullScreenBottomSheet()
      if emitCancelEvent {
        self.sendEvent("onCancelNavigation", [:])
      }
      completion?()
    }

    guard let navVC = navigationViewController else {
      finalize()
      return
    }

    if navVC.presentingViewController == nil {
      finalize()
      return
    }

    navVC.dismiss(animated: true) {
      finalize()
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

  private func applyNativeBottomBannerCustomization(
    to viewController: NavigationViewController,
    options: NavigationStartOptions
  ) {
    guard let banner = resolveBottomBannerController(from: viewController) else {
      warnUnsupportedOptionOnce(
        key: "ios.nativeBottomBanner.missing",
        message: "Unable to access iOS native BottomBannerViewController for customization on this SDK build."
      )
      return
    }

    let bottomSheet = options.bottomSheet
    let showTripProgress = options.showsTripProgress != false && bottomSheet?.showsTripProgress != false
    let showActionButtons = options.showsActionButtons != false && bottomSheet?.showsActionButtons != false

    if let backgroundColor = colorFromHex(bottomSheet?.backgroundColor) {
      banner.view.backgroundColor = backgroundColor
      banner.bottomBannerView.backgroundColor = backgroundColor
    }

    if let cornerRadius = bottomSheet?.cornerRadius {
      banner.bottomBannerView.clipsToBounds = true
      banner.bottomBannerView.layer.cornerRadius = CGFloat(max(0, min(cornerRadius, 28)))
      banner.bottomBannerView.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
    }

    if let primaryColor = colorFromHex(bottomSheet?.primaryTextColor) {
      banner.arrivalTimeLabel?.textColor = primaryColor
    }
    if let secondaryColor = colorFromHex(bottomSheet?.secondaryTextColor) {
      banner.distanceRemainingLabel?.textColor = secondaryColor
      banner.timeRemainingLabel?.textColor = secondaryColor
    }

    if let primaryFontSize = bottomSheet?.primaryTextFontSize {
      banner.arrivalTimeLabel?.font = resolvedFont(
        size: CGFloat(max(10, min(primaryFontSize, 34))),
        defaultWeight: .semibold,
        family: bottomSheet?.primaryTextFontFamily,
        weightName: bottomSheet?.primaryTextFontWeight
      )
    }
    if let secondaryFontSize = bottomSheet?.secondaryTextFontSize {
      let size = CGFloat(max(10, min(secondaryFontSize, 28)))
      let secondaryFont = resolvedFont(
        size: size,
        defaultWeight: .medium,
        family: bottomSheet?.secondaryTextFontFamily,
        weightName: bottomSheet?.secondaryTextFontWeight
      )
      banner.distanceRemainingLabel?.font = secondaryFont
      banner.timeRemainingLabel?.font = secondaryFont
    }

    let cancelButton = banner.cancelButton
    cancelButton?.isHidden = !showActionButtons
    if let title = bottomSheet?.actionButtonTitle?.trimmingCharacters(in: .whitespacesAndNewlines),
       !title.isEmpty {
      cancelButton?.setTitle(title, for: .normal)
    }
    if let buttonTextColor = colorFromHex(bottomSheet?.actionButtonTextColor) {
      cancelButton?.setTitleColor(buttonTextColor, for: .normal)
    }
    if let buttonBackgroundColor = colorFromHex(bottomSheet?.actionButtonBackgroundColor) {
      cancelButton?.backgroundColor = buttonBackgroundColor
    }
    if let buttonBorderColor = colorFromHex(bottomSheet?.actionButtonBorderColor) {
      cancelButton?.layer.borderColor = buttonBorderColor.cgColor
    }
    if let buttonBorderWidth = bottomSheet?.actionButtonBorderWidth {
      cancelButton?.layer.borderWidth = CGFloat(max(0, min(buttonBorderWidth, 6)))
    }
    let buttonCornerRadius = CGFloat(max(0, min(bottomSheet?.actionButtonCornerRadius ?? 10, 18)))
    cancelButton?.layer.cornerRadius = buttonCornerRadius
    cancelButton?.clipsToBounds = true
    if let buttonFontSize = bottomSheet?.actionButtonFontSize {
      cancelButton?.titleLabel?.font = resolvedFont(
        size: CGFloat(max(10, min(buttonFontSize, 26))),
        defaultWeight: .semibold,
        family: bottomSheet?.actionButtonFontFamily,
        weightName: bottomSheet?.actionButtonFontWeight
      )
    }

    banner.distanceRemainingLabel?.isHidden = !showTripProgress
    banner.timeRemainingLabel?.isHidden = !showTripProgress
    banner.arrivalTimeLabel?.isHidden = !showTripProgress

    nativeBottomBannerCancelButton?.removeTarget(
      self,
      action: #selector(onNativeBottomBannerCancelTap),
      for: .touchUpInside
    )
    if let cancelButton {
      cancelButton.addTarget(
        self,
        action: #selector(onNativeBottomBannerCancelTap),
        for: .touchUpInside
      )
      nativeBottomBannerCancelButton = cancelButton
    } else {
      nativeBottomBannerCancelButton = nil
    }
  }

  private func setNativeBottomBannerHidden(
    _ hidden: Bool,
    in viewController: NavigationViewController
  ) {
    guard let banner = resolveBottomBannerController(from: viewController) else {
      return
    }
    banner.view.isHidden = hidden
    banner.bottomBannerView.isHidden = hidden
    banner.bottomBannerView.alpha = hidden ? 0 : 1
  }

  private func resolveBottomBannerController(
    from viewController: NavigationViewController
  ) -> BottomBannerViewController? {
    let selector = NSSelectorFromString("bottomBannerViewController")
    guard viewController.responds(to: selector) else {
      return nil
    }
    return viewController.value(forKey: "bottomBannerViewController") as? BottomBannerViewController
  }

  private func configureNativeBannerGestureRevealIfNeeded(
    on viewController: NavigationViewController,
    options: NavigationStartOptions
  ) {
    clearNativeBannerGestureRevealIfNeeded()
    guard revealCustomSheetFromNativeBannerGesture else { return }
    let requestedHeight = CGFloat(options.bottomSheet?.revealGestureHotzoneHeight ?? 100)
    let revealZoneHeight = max(56, min(requestedHeight, 220))
    let trailingExclusion = CGFloat(
      max(48, min(options.bottomSheet?.revealGestureRightExclusionWidth ?? 80, 220))
    )
    nativeBottomBannerRevealZoneHeight = revealZoneHeight
    nativeBottomBannerRevealTrailingExclusion = trailingExclusion

    let pan = UIPanGestureRecognizer(target: self, action: #selector(onNativeBannerRevealPan(_:)))
    pan.cancelsTouchesInView = false
    pan.delaysTouchesBegan = false
    pan.delaysTouchesEnded = false

    nativeBannerGestureDelegate.shouldReceiveTouch = { [weak self] touch in
      guard let self else { return false }
      guard self.fullScreenBottomSheetHidden else { return false }
      let location = touch.location(in: viewController.view)
      let inBottomZone = location.y >= (viewController.view.bounds.height - self.nativeBottomBannerRevealZoneHeight)
      let inAllowedX = location.x <= (viewController.view.bounds.width - self.nativeBottomBannerRevealTrailingExclusion)
      if !inBottomZone || !inAllowedX {
        return false
      }
      return true
    }
    nativeBannerGestureDelegate.shouldBegin = { [weak self] _ in
      guard let self else { return false }
      return self.fullScreenBottomSheetHidden
    }
    pan.delegate = nativeBannerGestureDelegate
    viewController.view.addGestureRecognizer(pan)
    nativeBottomBannerPanGesture = pan
    nativeBottomBannerGestureTargetView = viewController.view
    nativeBottomBannerRevealHotzoneView = nil
  }

  private func clearNativeBannerGestureRevealIfNeeded() {
    if let tap = nativeBottomBannerTapGesture {
      nativeBottomBannerGestureTargetView?.removeGestureRecognizer(tap)
    }
    if let pan = nativeBottomBannerPanGesture {
      nativeBottomBannerGestureTargetView?.removeGestureRecognizer(pan)
    }
    nativeBottomBannerTapGesture = nil
    nativeBottomBannerPanGesture = nil
    nativeBottomBannerGestureTargetView = nil
    nativeBottomBannerRevealHotzoneView = nil
    nativeBottomBannerRevealZoneHeight = 0
    nativeBottomBannerRevealTrailingExclusion = 0
  }

  @objc
  private func onNativeBottomBannerCancelTap() {
    sendEvent("onBottomSheetActionPress", [
      "actionId": "cancel"
    ])
    dismissActiveNavigation(emitCancelEvent: true)
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
    let contentBottomPadding = CGFloat(options.bottomSheet?.contentBottomPadding ?? 6)
    let actionButtonsBottomPadding = CGFloat(options.bottomSheet?.actionButtonsBottomPadding ?? 4)
    let primaryFontSize = CGFloat(options.bottomSheet?.primaryTextFontSize ?? 16)
    let secondaryFontSize = CGFloat(options.bottomSheet?.secondaryTextFontSize ?? 13)
    let actionButtonFontSize = CGFloat(options.bottomSheet?.actionButtonFontSize ?? 14)
    let actionButtonHeight = CGFloat(options.bottomSheet?.actionButtonHeight ?? 40)
    bottomSheetShowCurrentStreet = options.bottomSheet?.showCurrentStreet ?? true
    bottomSheetShowRemainingDistance = options.bottomSheet?.showRemainingDistance ?? true
    bottomSheetShowRemainingDuration = options.bottomSheet?.showRemainingDuration ?? true
    bottomSheetShowETA = options.bottomSheet?.showETA ?? true
    bottomSheetShowCompletionPercent = options.bottomSheet?.showCompletionPercent ?? true
    fullScreenBottomSheetCollapsedHeight = max(72, min(collapsed, 320))
    fullScreenBottomSheetExpandedHeight = max(
      fullScreenBottomSheetCollapsedHeight,
      min(expanded, 500)
    )
    let initialState = options.bottomSheet?.initialState?
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .lowercased()
    fullScreenBottomSheetHidden = revealCustomSheetFromNativeBannerGesture || initialState == "hidden"
    fullScreenBottomSheetExpanded = initialState == "expanded"

    let backdrop = UIView()
    backdrop.translatesAutoresizingMaskIntoConstraints = false
    backdrop.backgroundColor = UIColor.black.withAlphaComponent(0.28)
    backdrop.alpha = fullScreenBottomSheetHidden ? 0 : 1
    backdrop.isUserInteractionEnabled = !fullScreenBottomSheetHidden
    let backdropTap = UITapGestureRecognizer(target: self, action: #selector(onBottomSheetBackdropTap))
    backdropTap.cancelsTouchesInView = true
    backdrop.addGestureRecognizer(backdropTap)

    let container = UIView()
    container.translatesAutoresizingMaskIntoConstraints = false
    container.backgroundColor = colorFromHex(options.bottomSheet?.backgroundColor)
      ?? UIColor(white: 0.06, alpha: 0.92)
    let cornerRadius = CGFloat(options.bottomSheet?.cornerRadius ?? 16)
    container.layer.cornerRadius = max(0, min(cornerRadius, 28))
    container.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
    container.clipsToBounds = true
    container.isUserInteractionEnabled = !fullScreenBottomSheetHidden

    // Wrap the container so we can add a drop shadow without disabling clipping on the content view.
    let shadowWrap = UIView()
    shadowWrap.translatesAutoresizingMaskIntoConstraints = false
    shadowWrap.layer.shadowColor = UIColor.black.cgColor
    shadowWrap.layer.shadowOpacity = 0.22
    shadowWrap.layer.shadowRadius = 16
    shadowWrap.layer.shadowOffset = CGSize(width: 0, height: -6)
    shadowWrap.alpha = fullScreenBottomSheetHidden ? 0 : 1
    shadowWrap.isUserInteractionEnabled = !fullScreenBottomSheetHidden
    shadowWrap.addSubview(container)
    NSLayoutConstraint.activate([
      container.topAnchor.constraint(equalTo: shadowWrap.topAnchor),
      container.leadingAnchor.constraint(equalTo: shadowWrap.leadingAnchor),
      container.trailingAnchor.constraint(equalTo: shadowWrap.trailingAnchor),
      container.bottomAnchor.constraint(equalTo: shadowWrap.bottomAnchor),
    ])

    let handle = UIView()
    handle.translatesAutoresizingMaskIntoConstraints = false
    handle.backgroundColor = colorFromHex(options.bottomSheet?.handleColor)
      ?? UIColor(white: 0.85, alpha: 0.75)
    handle.layer.cornerRadius = 2.5

    let primaryLabel = UILabel()
    primaryLabel.translatesAutoresizingMaskIntoConstraints = false
    primaryLabel.textColor = colorFromHex(options.bottomSheet?.primaryTextColor) ?? .white
    primaryLabel.font = resolvedFont(
      size: max(10, min(primaryFontSize, 30)),
      defaultWeight: .semibold,
      family: options.bottomSheet?.primaryTextFontFamily,
      weightName: options.bottomSheet?.primaryTextFontWeight
    )
    primaryLabel.numberOfLines = 2
    primaryLabel.text = "Starting navigation..."

    let secondaryLabel = UILabel()
    secondaryLabel.translatesAutoresizingMaskIntoConstraints = false
    secondaryLabel.textColor = colorFromHex(options.bottomSheet?.secondaryTextColor)
      ?? UIColor(white: 0.88, alpha: 0.9)
    secondaryLabel.font = resolvedFont(
      size: max(10, min(secondaryFontSize, 24)),
      defaultWeight: .medium,
      family: options.bottomSheet?.secondaryTextFontFamily,
      weightName: options.bottomSheet?.secondaryTextFontWeight
    )
    secondaryLabel.numberOfLines = 2
    secondaryLabel.text = "Waiting for route progress"

    let stack = UIStackView(arrangedSubviews: [primaryLabel, secondaryLabel])
    stack.translatesAutoresizingMaskIntoConstraints = false
    stack.axis = .vertical
    stack.spacing = 4

    fullScreenPrimaryActionBehavior = options.bottomSheet?.primaryActionButtonBehavior ?? "stopNavigation"
    fullScreenSecondaryActionBehavior = options.bottomSheet?.secondaryActionButtonBehavior ?? "emitEvent"

    let primaryTitle = options.bottomSheet?.actionButtonTitle?.trimmingCharacters(in: .whitespacesAndNewlines)
    let hasPrimaryAction = !(primaryTitle?.isEmpty ?? true)
    let actionButton = UIButton(type: .system)
    actionButton.translatesAutoresizingMaskIntoConstraints = false
    actionButton.setTitle(primaryTitle, for: .normal)
    actionButton.setTitleColor(colorFromHex(options.bottomSheet?.actionButtonTextColor) ?? .white, for: .normal)
    actionButton.backgroundColor = colorFromHex(options.bottomSheet?.actionButtonBackgroundColor)
      ?? UIColor(red: 0.2, green: 0.35, blue: 0.8, alpha: 1)
    actionButton.layer.cornerRadius = CGFloat(options.bottomSheet?.actionButtonCornerRadius ?? 8)
    actionButton.layer.borderColor = colorFromHex(options.bottomSheet?.actionButtonBorderColor)?.cgColor
    actionButton.layer.borderWidth = CGFloat(max(0, min(options.bottomSheet?.actionButtonBorderWidth ?? 0, 6)))
    actionButton.titleLabel?.font = resolvedFont(
      size: max(10, min(actionButtonFontSize, 24)),
      defaultWeight: .semibold,
      family: options.bottomSheet?.actionButtonFontFamily,
      weightName: options.bottomSheet?.actionButtonFontWeight
    )
    actionButton.addTarget(self, action: #selector(onBottomSheetPrimaryActionTap), for: .touchUpInside)
    actionButton.isHidden = !hasPrimaryAction || options.showsActionButtons == false || options.bottomSheet?.showsActionButtons == false

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
    secondaryActionButton.titleLabel?.font = resolvedFont(
      size: max(10, min(actionButtonFontSize, 24)),
      defaultWeight: .semibold,
      family: options.bottomSheet?.actionButtonFontFamily,
      weightName: options.bottomSheet?.actionButtonFontWeight
    )
    secondaryActionButton.addTarget(self, action: #selector(onBottomSheetSecondaryActionTap), for: .touchUpInside)
    secondaryActionButton.isHidden = !hasSecondaryAction || actionButton.isHidden

    let showManeuverSection = options.showsManeuverView != false && options.bottomSheet?.showsManeuverView != false
    let showTripSection = options.showsTripProgress != false && options.bottomSheet?.showsTripProgress != false
    primaryLabel.isHidden = !showManeuverSection
    secondaryLabel.isHidden = !showTripSection
    fullScreenCustomActionByTag.removeAll()

    container.addSubview(handle)
    let contentStack = UIStackView()
    contentStack.translatesAutoresizingMaskIntoConstraints = false
    contentStack.axis = .vertical
    contentStack.spacing = 10
    contentStack.alignment = .fill

    if let headerView = makeCustomNativeHeaderView(options: options) {
      contentStack.addArrangedSubview(headerView)
    }

    contentStack.addArrangedSubview(stack)

    let customRows = options.bottomSheet?.customRows ?? []
    if !customRows.isEmpty {
      let rowsStack = UIStackView()
      rowsStack.translatesAutoresizingMaskIntoConstraints = false
      rowsStack.axis = .vertical
      rowsStack.spacing = 8
      rowsStack.alignment = .fill
      for row in customRows {
        rowsStack.addArrangedSubview(
          makeCustomNativeRowView(
            row: row,
            primaryColor: colorFromHex(options.bottomSheet?.primaryTextColor) ?? .white,
            secondaryColor: colorFromHex(options.bottomSheet?.secondaryTextColor) ?? UIColor(white: 0.88, alpha: 0.9),
            primaryFontSize: primaryFontSize,
            secondaryFontSize: secondaryFontSize,
            options: options
          )
        )
      }
      contentStack.addArrangedSubview(rowsStack)
    }

    let quickActions = options.bottomSheet?.quickActions ?? []
    if !quickActions.isEmpty {
      let quickActionsRow = UIStackView()
      quickActionsRow.translatesAutoresizingMaskIntoConstraints = false
      quickActionsRow.axis = .horizontal
      quickActionsRow.spacing = 8
      quickActionsRow.alignment = .fill
      quickActionsRow.distribution = .fillEqually

      for action in quickActions.prefix(4) {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.setTitle(action.label, for: .normal)
        styleCustomNativeQuickActionButton(
          button,
          variant: action.variant,
          options: options,
          actionButtonFontSize: actionButtonFontSize
        )
        fullScreenCustomActionTagSeed += 1
        button.tag = fullScreenCustomActionTagSeed
        fullScreenCustomActionByTag[button.tag] = action.id
        button.addTarget(self, action: #selector(onCustomNativeQuickActionTap(_:)), for: .touchUpInside)
        button.heightAnchor.constraint(equalToConstant: max(28, min(actionButtonHeight - 4, 60))).isActive = true
        quickActionsRow.addArrangedSubview(button)
      }

      contentStack.addArrangedSubview(quickActionsRow)
    }

    let actionStack = UIStackView(arrangedSubviews: [actionButton, secondaryActionButton])
    actionStack.translatesAutoresizingMaskIntoConstraints = false
    actionStack.axis = .horizontal
    actionStack.distribution = .fillEqually
    actionStack.spacing = 8
    actionStack.isHidden = actionButton.isHidden && secondaryActionButton.isHidden
    if !actionStack.isHidden {
      contentStack.addArrangedSubview(actionStack)
      actionStack.heightAnchor.constraint(equalToConstant: max(32, min(actionButtonHeight, 72))).isActive = true
      contentStack.setCustomSpacing(max(0, min(actionButtonsBottomPadding, 32)), after: actionStack)
    }

    container.addSubview(contentStack)
    viewController.view.addSubview(backdrop)
    viewController.view.addSubview(shadowWrap)

    let topConstraint = shadowWrap.topAnchor.constraint(
      equalTo: viewController.view.safeAreaLayoutGuide.bottomAnchor,
      constant: -currentBottomSheetHeight()
    )
    fullScreenBottomSheetTopConstraint = topConstraint

    NSLayoutConstraint.activate([
      backdrop.topAnchor.constraint(equalTo: viewController.view.topAnchor),
      backdrop.leadingAnchor.constraint(equalTo: viewController.view.leadingAnchor),
      backdrop.trailingAnchor.constraint(equalTo: viewController.view.trailingAnchor),
      backdrop.bottomAnchor.constraint(equalTo: viewController.view.bottomAnchor),

      shadowWrap.leadingAnchor.constraint(equalTo: viewController.view.leadingAnchor),
      shadowWrap.trailingAnchor.constraint(equalTo: viewController.view.trailingAnchor),
      shadowWrap.bottomAnchor.constraint(equalTo: viewController.view.bottomAnchor),
      topConstraint,

      handle.topAnchor.constraint(equalTo: container.topAnchor, constant: 8),
      handle.centerXAnchor.constraint(equalTo: container.centerXAnchor),
      handle.widthAnchor.constraint(equalToConstant: 42),
      handle.heightAnchor.constraint(equalToConstant: 5),

      contentStack.topAnchor.constraint(equalTo: handle.bottomAnchor, constant: 10 + max(0, min(contentTopSpacing, 20))),
      contentStack.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: max(0, min(horizontalPadding, 48))),
      contentStack.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -max(0, min(horizontalPadding, 48))),
      contentStack.bottomAnchor.constraint(lessThanOrEqualTo: container.safeAreaLayoutGuide.bottomAnchor, constant: -max(0, min(contentBottomPadding, 40))),
    ])

    if options.bottomSheet?.enableTapToToggle != false {
      let tap = UITapGestureRecognizer(target: self, action: #selector(onBottomSheetToggleTap))
      tap.cancelsTouchesInView = false
      handle.isUserInteractionEnabled = true
      handle.addGestureRecognizer(tap)
      let pan = UIPanGestureRecognizer(target: self, action: #selector(onBottomSheetHandlePan(_:)))
      pan.cancelsTouchesInView = false
      handle.addGestureRecognizer(pan)
      let containerPan = UIPanGestureRecognizer(target: self, action: #selector(onBottomSheetHandlePan(_:)))
      containerPan.cancelsTouchesInView = false
      container.addGestureRecognizer(containerPan)
    }
    handle.isHidden = options.bottomSheet?.showHandle == false

    fullScreenBottomSheetBackdropView?.removeFromSuperview()
    fullScreenBottomSheetBackdropView = backdrop
    fullScreenBottomSheetView?.removeFromSuperview()
    fullScreenBottomSheetView = shadowWrap
    fullScreenBottomSheetHandleView = handle
    fullScreenBottomSheetPrimaryLabel = primaryLabel
    fullScreenBottomSheetSecondaryLabel = secondaryLabel
    fullScreenBottomSheetPrimaryActionButton = actionButton
    fullScreenBottomSheetSecondaryActionButton = secondaryActionButton
  }

  private func updateFullScreenBottomSheet(
    instruction: String?,
    progress: RouteProgress?
  ) {
    guard isUsingCustomNativeBottomSheet, fullScreenBottomSheetView != nil else {
      return
    }
    if let instruction, !instruction.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
      fullScreenBottomSheetPrimaryLabel?.text = instruction
      latestJourneyInstructionPrimary = instruction
    } else if let cached = latestJourneyInstructionPrimary, !cached.isEmpty {
      fullScreenBottomSheetPrimaryLabel?.text = cached
    }

    var parts: [String] = []
    if bottomSheetShowCurrentStreet,
       let street = latestJourneyInstructionSecondary?.trimmingCharacters(in: .whitespacesAndNewlines),
       !street.isEmpty {
      parts.append(street)
    }

    if let progress {
      let remainingMeters = max(0, Int(progress.distanceRemaining.rounded()))
      let durationRemaining = max(0, progress.durationRemaining)
      let fraction = max(0, min(progress.fractionTraveled, 1))
      if bottomSheetShowRemainingDistance {
        parts.append("\(remainingMeters) m")
      }
      if bottomSheetShowRemainingDuration {
        parts.append(formatDuration(durationRemaining))
      }
      if bottomSheetShowETA {
        parts.append(formatETA(durationRemaining))
      }
      if bottomSheetShowCompletionPercent {
        parts.append("\(Int((fraction * 100).rounded()))%")
      }
    }

    if parts.isEmpty, bottomSheetShowCurrentStreet,
       let street = latestJourneyInstructionSecondary?.trimmingCharacters(in: .whitespacesAndNewlines),
       !street.isEmpty {
      parts.append(street)
    }
    fullScreenBottomSheetSecondaryLabel?.text = parts.isEmpty
      ? "Waiting for route progress"
      : parts.joined(separator: " • ")
  }

  private func formatDuration(_ seconds: TimeInterval) -> String {
    let totalMinutes = max(0, Int((seconds / 60.0).rounded()))
    if totalMinutes < 60 {
      return "\(totalMinutes)m"
    }
    let hours = totalMinutes / 60
    let minutes = totalMinutes % 60
    return minutes > 0 ? "\(hours)h \(minutes)m" : "\(hours)h"
  }

  private func formatETA(_ durationRemaining: TimeInterval) -> String {
    let date = Date().addingTimeInterval(durationRemaining)
    let formatter = DateFormatter()
    formatter.locale = Locale.current
    formatter.timeStyle = .short
    formatter.dateStyle = .none
    return formatter.string(from: date)
  }

  private func emitJourneyData() {
    var payload: [String: Any] = [:]
    if let location = latestJourneyLocation {
      payload["latitude"] = location.coordinate.latitude
      payload["longitude"] = location.coordinate.longitude
      payload["bearing"] = location.course
      payload["speed"] = location.speed
      payload["altitude"] = location.altitude
      payload["accuracy"] = location.horizontalAccuracy
    }
    if let instruction = latestJourneyInstructionPrimary, !instruction.isEmpty {
      payload["primaryInstruction"] = instruction
    }
    if let secondary = latestJourneyInstructionSecondary, !secondary.isEmpty {
      payload["secondaryInstruction"] = secondary
      payload["currentStreet"] = secondary
    }
    if let stepDistance = latestJourneyStepDistanceRemaining {
      payload["stepDistanceRemaining"] = stepDistance
    }
    if let distanceRemaining = latestJourneyProgressDistanceRemaining {
      payload["distanceRemaining"] = distanceRemaining
    }
    if let durationRemaining = latestJourneyProgressDurationRemaining {
      payload["durationRemaining"] = durationRemaining
      payload["etaIso8601"] = ISO8601DateFormatter().string(from: Date().addingTimeInterval(durationRemaining))
    }
    if let fraction = latestJourneyProgressFractionTraveled {
      payload["fractionTraveled"] = fraction
      payload["completionPercent"] = Int((fraction * 100).rounded())
    }
    if !payload.isEmpty {
      sendEvent("onJourneyDataChange", payload)
    }
  }

  private func detachFullScreenBottomSheet() {
    clearNativeBannerGestureRevealIfNeeded()
    nativeBottomBannerCancelButton?.removeTarget(
      self,
      action: #selector(onNativeBottomBannerCancelTap),
      for: .touchUpInside
    )
    nativeBottomBannerCancelButton = nil
    fullScreenBottomSheetBackdropView?.removeFromSuperview()
    fullScreenBottomSheetBackdropView = nil
    fullScreenBottomSheetView?.removeFromSuperview()
    fullScreenBottomSheetView = nil
    fullScreenBottomSheetPrimaryLabel = nil
    fullScreenBottomSheetSecondaryLabel = nil
    fullScreenBottomSheetPrimaryActionButton = nil
    fullScreenBottomSheetSecondaryActionButton = nil
    fullScreenBottomSheetTopConstraint = nil
    fullScreenBottomSheetHandleView = nil
    fullScreenBottomSheetHidden = false
    revealCustomSheetFromNativeBannerGesture = false
  }

  private func currentBottomSheetHeight() -> CGFloat {
    if fullScreenBottomSheetHidden {
      return fullScreenBottomSheetHiddenHeight
    }
    return fullScreenBottomSheetExpanded
      ? fullScreenBottomSheetExpandedHeight
      : fullScreenBottomSheetCollapsedHeight
  }

  @objc
  private func onBottomSheetToggleTap() {
    guard fullScreenBottomSheetView != nil else {
      return
    }
    if fullScreenBottomSheetHidden {
      fullScreenBottomSheetHidden = false
      fullScreenBottomSheetExpanded = false
    } else {
      fullScreenBottomSheetExpanded.toggle()
    }
    applyFullScreenBottomSheetState(animated: true)
  }

  @objc
  private func onNativeBannerRevealPan(_ recognizer: UIPanGestureRecognizer) {
    guard recognizer.state == .ended else { return }
    let translationY = recognizer.translation(in: recognizer.view).y
    let velocityY = recognizer.velocity(in: recognizer.view).y
    if translationY < -10 || velocityY < -140 {
      revealCustomNativeBottomSheet(expanded: true)
    }
  }

  private func revealCustomNativeBottomSheet(expanded: Bool) {
    guard fullScreenBottomSheetView != nil else { return }
    fullScreenBottomSheetHidden = false
    fullScreenBottomSheetExpanded = expanded
    applyFullScreenBottomSheetState(animated: true, duration: 0.24, options: [.curveEaseOut])
  }

  @objc
  private func onBottomSheetHandlePan(_ recognizer: UIPanGestureRecognizer) {
    guard fullScreenBottomSheetView != nil else {
      return
    }
    if recognizer.state != .ended {
      return
    }
    let translationY = recognizer.translation(in: recognizer.view).y
    let velocityY = recognizer.velocity(in: recognizer.view).y
    let upward = translationY < -12 || velocityY < -160
    let downward = translationY > 12 || velocityY > 160
    if upward {
      if fullScreenBottomSheetHidden {
        fullScreenBottomSheetHidden = false
        fullScreenBottomSheetExpanded = false
      } else {
        fullScreenBottomSheetExpanded = true
      }
    } else if downward {
      if fullScreenBottomSheetExpanded {
        fullScreenBottomSheetExpanded = false
      } else {
        fullScreenBottomSheetHidden = true
      }
    } else {
      return
    }
    applyFullScreenBottomSheetState(animated: true, duration: 0.22)
  }

  @objc
  private func onBottomSheetBackdropTap() {
    guard !fullScreenBottomSheetHidden else { return }
    fullScreenBottomSheetExpanded = false
    fullScreenBottomSheetHidden = true
    applyFullScreenBottomSheetState(animated: true)
  }

  private func applyFullScreenBottomSheetState(
    animated: Bool,
    duration: TimeInterval = 0.2,
    options: UIView.AnimationOptions = []
  ) {
    fullScreenBottomSheetTopConstraint?.constant = -currentBottomSheetHeight()
    nativeBottomBannerRevealHotzoneView?.isUserInteractionEnabled = fullScreenBottomSheetHidden
    fullScreenBottomSheetView?.isUserInteractionEnabled = !fullScreenBottomSheetHidden
    let animations = {
      self.fullScreenBottomSheetView?.alpha = self.fullScreenBottomSheetHidden ? 0 : 1
      self.fullScreenBottomSheetBackdropView?.alpha = self.fullScreenBottomSheetHidden ? 0 : 1
      self.fullScreenBottomSheetBackdropView?.isUserInteractionEnabled = !self.fullScreenBottomSheetHidden
      self.navigationViewController?.view.layoutIfNeeded()
    }
    if animated {
      UIView.animate(withDuration: duration, delay: 0, options: options, animations: animations)
    } else {
      animations()
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
    dismissActiveNavigation(emitCancelEvent: true)
  }

  @objc
  private func onBottomSheetSecondaryActionTap() {
    if fullScreenSecondaryActionBehavior == "none" {
      return
    }
    if fullScreenSecondaryActionBehavior == "stopNavigation" {
      dismissActiveNavigation(emitCancelEvent: true)
      return
    }
    sendEvent("onBottomSheetActionPress", [
      "actionId": "secondary"
    ])
  }

  @objc
  private func onCustomNativeQuickActionTap(_ sender: UIButton) {
    guard let actionId = fullScreenCustomActionByTag[sender.tag] else {
      return
    }
    sendEvent("onBottomSheetActionPress", [
      "actionId": actionId
    ])

    // Built-in behavior (best effort) so quick actions work out of the box.
    switch actionId {
    case "stop":
      dismissActiveNavigation(emitCancelEvent: true)
    case "toggleMute":
      NavigationSettings.shared.voiceMuted = !NavigationSettings.shared.voiceMuted
    case "overview":
      if let navVC = navigationViewController {
        applyCameraConfiguration(to: navVC, mode: "overview", pitch: nil, zoom: nil)
        currentCameraMode = "overview"
      }
    case "recenter":
      if let navVC = navigationViewController {
        applyCameraConfiguration(to: navVC, mode: "following", pitch: nil, zoom: nil)
        currentCameraMode = "following"
      }
    default:
      break
    }
  }

  private func makeCustomNativeRowView(
    row: BottomSheetCustomRowStartOptions,
    primaryColor: UIColor,
    secondaryColor: UIColor,
    primaryFontSize: CGFloat,
    secondaryFontSize: CGFloat,
    options: NavigationStartOptions
  ) -> UIView {
    let container = UIView()
    container.translatesAutoresizingMaskIntoConstraints = false

    let leadingStack = UIStackView()
    leadingStack.translatesAutoresizingMaskIntoConstraints = false
    leadingStack.axis = .horizontal
    leadingStack.spacing = 6
    leadingStack.alignment = .center

    if let iconName = row.iconSystemName?.trimmingCharacters(in: .whitespacesAndNewlines),
       !iconName.isEmpty,
       let image = UIImage(systemName: iconName) {
      let iconView = UIImageView(image: image)
      iconView.translatesAutoresizingMaskIntoConstraints = false
      iconView.tintColor = secondaryColor
      iconView.contentMode = .scaleAspectFit
      iconView.setContentHuggingPriority(.required, for: .horizontal)
      iconView.widthAnchor.constraint(equalToConstant: 14).isActive = true
      iconView.heightAnchor.constraint(equalToConstant: 14).isActive = true
      leadingStack.addArrangedSubview(iconView)
    } else if let iconText = row.iconText?.trimmingCharacters(in: .whitespacesAndNewlines),
              !iconText.isEmpty {
      let iconLabel = UILabel()
      iconLabel.translatesAutoresizingMaskIntoConstraints = false
      iconLabel.text = iconText
      iconLabel.textColor = secondaryColor
      iconLabel.font = resolvedFont(
        size: max(10, min(secondaryFontSize, 24)),
        defaultWeight: .regular,
        family: options.bottomSheet?.secondaryTextFontFamily,
        weightName: options.bottomSheet?.secondaryTextFontWeight
      )
      iconLabel.setContentHuggingPriority(.required, for: .horizontal)
      leadingStack.addArrangedSubview(iconLabel)
    }

    let title = UILabel()
    title.translatesAutoresizingMaskIntoConstraints = false
    title.numberOfLines = 1
    title.text = row.title
    title.textColor = primaryColor
    title.font = resolvedFont(
      size: max(10, min(primaryFontSize - 2, 28)),
      defaultWeight: row.emphasis == true ? .semibold : .regular,
      family: options.bottomSheet?.primaryTextFontFamily,
      weightName: options.bottomSheet?.primaryTextFontWeight
    )

    let value = UILabel()
    value.translatesAutoresizingMaskIntoConstraints = false
    value.numberOfLines = 1
    value.textAlignment = .right
    value.text = row.value
    value.textColor = primaryColor
    value.font = resolvedFont(
      size: max(10, min(primaryFontSize, 30)),
      defaultWeight: row.emphasis == true ? .bold : .medium,
      family: options.bottomSheet?.primaryTextFontFamily,
      weightName: options.bottomSheet?.primaryTextFontWeight
    )

    leadingStack.addArrangedSubview(title)
    container.addSubview(leadingStack)
    container.addSubview(value)

    var constraints: [NSLayoutConstraint] = [
      leadingStack.topAnchor.constraint(equalTo: container.topAnchor),
      leadingStack.leadingAnchor.constraint(equalTo: container.leadingAnchor),
      leadingStack.trailingAnchor.constraint(lessThanOrEqualTo: value.leadingAnchor, constant: -8),

      value.topAnchor.constraint(equalTo: container.topAnchor),
      value.trailingAnchor.constraint(equalTo: container.trailingAnchor),
    ]

    if row.value?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == true {
      value.isHidden = true
      constraints.append(leadingStack.trailingAnchor.constraint(equalTo: container.trailingAnchor))
    } else {
      constraints.append(value.widthAnchor.constraint(greaterThanOrEqualToConstant: 40))
    }

    if let subtitleRaw = row.subtitle?.trimmingCharacters(in: .whitespacesAndNewlines),
       !subtitleRaw.isEmpty {
      let subtitle = UILabel()
      subtitle.translatesAutoresizingMaskIntoConstraints = false
      subtitle.numberOfLines = 0
      subtitle.text = subtitleRaw
      subtitle.textColor = secondaryColor
      subtitle.font = resolvedFont(
        size: max(10, min(secondaryFontSize, 24)),
        defaultWeight: .regular,
        family: options.bottomSheet?.secondaryTextFontFamily,
        weightName: options.bottomSheet?.secondaryTextFontWeight
      )
      container.addSubview(subtitle)

      constraints.append(contentsOf: [
        subtitle.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 2),
        subtitle.leadingAnchor.constraint(equalTo: container.leadingAnchor),
        subtitle.trailingAnchor.constraint(equalTo: container.trailingAnchor),
        subtitle.bottomAnchor.constraint(equalTo: container.bottomAnchor),
        value.bottomAnchor.constraint(equalTo: title.bottomAnchor),
      ])
    } else {
      constraints.append(contentsOf: [
        leadingStack.bottomAnchor.constraint(equalTo: container.bottomAnchor),
        value.bottomAnchor.constraint(equalTo: container.bottomAnchor),
      ])
    }

    NSLayoutConstraint.activate(constraints)
    return container
  }

  private func styleCustomNativeQuickActionButton(
    _ button: UIButton,
    variant: String?,
    options: NavigationStartOptions,
    actionButtonFontSize: CGFloat
  ) {
    let normalizedVariant = variant?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    let primaryBackground = colorFromHex(options.bottomSheet?.actionButtonBackgroundColor)
      ?? UIColor(red: 0.2, green: 0.35, blue: 0.8, alpha: 1)
    let secondaryBackground = colorFromHex(options.bottomSheet?.secondaryActionButtonBackgroundColor)
      ?? UIColor(white: 1, alpha: 0.12)
    let primaryText = colorFromHex(options.bottomSheet?.actionButtonTextColor) ?? .white
    let secondaryText = colorFromHex(options.bottomSheet?.secondaryActionButtonTextColor)
      ?? colorFromHex(options.bottomSheet?.actionButtonTextColor)
      ?? UIColor(white: 0.9, alpha: 1.0)
    let quickPrimaryBackground = colorFromHex(options.bottomSheet?.quickActionBackgroundColor) ?? primaryBackground
    let quickSecondaryBackground = colorFromHex(options.bottomSheet?.quickActionSecondaryBackgroundColor) ?? secondaryBackground
    let quickPrimaryText = colorFromHex(options.bottomSheet?.quickActionTextColor) ?? primaryText
    let quickSecondaryText = colorFromHex(options.bottomSheet?.quickActionSecondaryTextColor) ?? secondaryText
    let quickGhostText = colorFromHex(options.bottomSheet?.quickActionGhostTextColor) ?? quickSecondaryText
    let quickBorderWidth = CGFloat(max(0, min(
      options.bottomSheet?.quickActionBorderWidth ?? options.bottomSheet?.actionButtonBorderWidth ?? 0,
      6
    )))
    let quickBorderColor = colorFromHex(options.bottomSheet?.quickActionBorderColor)
      ?? colorFromHex(options.bottomSheet?.actionButtonBorderColor)
    let quickCornerRadius = CGFloat(
      max(0, min(options.bottomSheet?.quickActionCornerRadius ?? options.bottomSheet?.actionButtonCornerRadius ?? 8, 24))
    )

    if normalizedVariant == "ghost" {
      button.backgroundColor = .clear
      button.setTitleColor(quickGhostText, for: .normal)
      button.layer.borderWidth = quickBorderWidth
      button.layer.borderColor = quickBorderColor?.cgColor
    } else if normalizedVariant == "secondary" {
      button.backgroundColor = quickSecondaryBackground
      button.setTitleColor(quickSecondaryText, for: .normal)
      button.layer.borderWidth = quickBorderWidth
      button.layer.borderColor = quickBorderColor?.cgColor
    } else {
      button.backgroundColor = quickPrimaryBackground
      button.setTitleColor(quickPrimaryText, for: .normal)
      button.layer.borderWidth = quickBorderWidth
      button.layer.borderColor = quickBorderColor?.cgColor
    }

    button.layer.cornerRadius = quickCornerRadius
    button.clipsToBounds = true
    button.titleLabel?.font = resolvedFont(
      size: max(10, min(actionButtonFontSize, 22)),
      defaultWeight: .semibold,
      family: options.bottomSheet?.quickActionFontFamily ?? options.bottomSheet?.actionButtonFontFamily,
      weightName: options.bottomSheet?.quickActionFontWeight ?? options.bottomSheet?.actionButtonFontWeight
    )
  }

  private func makeCustomNativeHeaderView(options: NavigationStartOptions) -> UIView? {
    let titleText = options.bottomSheet?.headerTitle?.trimmingCharacters(in: .whitespacesAndNewlines)
    let subtitleText = options.bottomSheet?.headerSubtitle?.trimmingCharacters(in: .whitespacesAndNewlines)
    let badgeText = options.bottomSheet?.headerBadgeText?.trimmingCharacters(in: .whitespacesAndNewlines)

    let hasTitle = !(titleText?.isEmpty ?? true)
    let hasSubtitle = !(subtitleText?.isEmpty ?? true)
    let hasBadge = !(badgeText?.isEmpty ?? true)

    guard hasTitle || hasSubtitle || hasBadge else {
      return nil
    }

    let container = UIView()
    container.translatesAutoresizingMaskIntoConstraints = false

    let textStack = UIStackView()
    textStack.translatesAutoresizingMaskIntoConstraints = false
    textStack.axis = .vertical
    textStack.spacing = 2
    textStack.alignment = .leading

    if let titleText, !titleText.isEmpty {
      let title = UILabel()
      title.translatesAutoresizingMaskIntoConstraints = false
      title.text = titleText
      title.numberOfLines = 1
      title.textColor = colorFromHex(options.bottomSheet?.primaryTextColor) ?? .white
      let titleSize = CGFloat(max(10, min(options.bottomSheet?.headerTitleFontSize ?? 16, 30)))
      title.font = resolvedFont(
        size: titleSize,
        defaultWeight: .semibold,
        family: options.bottomSheet?.headerTitleFontFamily ?? options.bottomSheet?.primaryTextFontFamily,
        weightName: options.bottomSheet?.headerTitleFontWeight ?? options.bottomSheet?.primaryTextFontWeight
      )
      textStack.addArrangedSubview(title)
    }

    if let subtitleText, !subtitleText.isEmpty {
      let subtitle = UILabel()
      subtitle.translatesAutoresizingMaskIntoConstraints = false
      subtitle.text = subtitleText
      subtitle.numberOfLines = 1
      subtitle.textColor = colorFromHex(options.bottomSheet?.secondaryTextColor)
        ?? UIColor(white: 0.88, alpha: 0.9)
      let subtitleSize = CGFloat(max(10, min(options.bottomSheet?.headerSubtitleFontSize ?? 12, 24)))
      subtitle.font = resolvedFont(
        size: subtitleSize,
        defaultWeight: .regular,
        family: options.bottomSheet?.headerSubtitleFontFamily ?? options.bottomSheet?.secondaryTextFontFamily,
        weightName: options.bottomSheet?.headerSubtitleFontWeight ?? options.bottomSheet?.secondaryTextFontWeight
      )
      textStack.addArrangedSubview(subtitle)
    }

    container.addSubview(textStack)

    var constraints: [NSLayoutConstraint] = [
      textStack.topAnchor.constraint(equalTo: container.topAnchor),
      textStack.leadingAnchor.constraint(equalTo: container.leadingAnchor),
      textStack.bottomAnchor.constraint(equalTo: container.bottomAnchor),
    ]

    if let badgeText, !badgeText.isEmpty {
      let badge = UILabel()
      badge.translatesAutoresizingMaskIntoConstraints = false
      badge.text = badgeText
      badge.textColor = colorFromHex(options.bottomSheet?.headerBadgeTextColor) ?? .white
      badge.backgroundColor = colorFromHex(options.bottomSheet?.headerBadgeBackgroundColor)
        ?? UIColor(red: 0.2, green: 0.35, blue: 0.8, alpha: 1)
      let badgeSize = CGFloat(max(10, min(options.bottomSheet?.headerBadgeFontSize ?? 11, 22)))
      badge.font = resolvedFont(
        size: badgeSize,
        defaultWeight: .semibold,
        family: options.bottomSheet?.headerBadgeFontFamily ?? options.bottomSheet?.actionButtonFontFamily,
        weightName: options.bottomSheet?.headerBadgeFontWeight ?? options.bottomSheet?.actionButtonFontWeight
      )
      badge.textAlignment = .center
      badge.layer.cornerRadius = CGFloat(max(0, min(options.bottomSheet?.headerBadgeCornerRadius ?? 9, 24)))
      badge.layer.borderColor = colorFromHex(options.bottomSheet?.headerBadgeBorderColor)?.cgColor
      badge.layer.borderWidth = CGFloat(max(0, min(options.bottomSheet?.headerBadgeBorderWidth ?? 0, 6)))
      badge.clipsToBounds = true
      badge.setContentHuggingPriority(.required, for: .horizontal)
      container.addSubview(badge)

      constraints.append(contentsOf: [
        badge.trailingAnchor.constraint(equalTo: container.trailingAnchor),
        badge.centerYAnchor.constraint(equalTo: container.centerYAnchor),
        badge.leadingAnchor.constraint(greaterThanOrEqualTo: textStack.trailingAnchor, constant: 10),
        badge.heightAnchor.constraint(greaterThanOrEqualToConstant: 18),
      ])
      badge.setContentCompressionResistancePriority(.required, for: .horizontal)
      badge.layoutMargins = UIEdgeInsets(top: 2, left: 8, bottom: 2, right: 8)
      badge.text = " \(badgeText) "
    } else {
      constraints.append(textStack.trailingAnchor.constraint(equalTo: container.trailingAnchor))
    }

    NSLayoutConstraint.activate(constraints)
    return container
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

  private func resolvedFont(
    size: CGFloat,
    defaultWeight: UIFont.Weight,
    family: String?,
    weightName: String?
  ) -> UIFont {
    let clampedSize = max(10, min(size, 40))
    let weight = fontWeight(from: weightName) ?? defaultWeight
    let trimmedFamily = family?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    if !trimmedFamily.isEmpty, let customFont = UIFont(name: trimmedFamily, size: clampedSize) {
      return customFont
    }
    return .systemFont(ofSize: clampedSize, weight: weight)
  }

  private func fontWeight(from value: String?) -> UIFont.Weight? {
    guard let raw = value?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
          !raw.isEmpty else {
      return nil
    }
    switch raw {
    case "100", "thin":
      return .thin
    case "200", "extralight", "ultralight":
      return .ultraLight
    case "300", "light":
      return .light
    case "400", "normal", "regular":
      return .regular
    case "500", "medium":
      return .medium
    case "600", "semibold", "demibold":
      return .semibold
    case "700", "bold":
      return .bold
    case "800", "extrabold", "heavy":
      return .heavy
    case "900", "black":
      return .black
    default:
      return nil
    }
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
    latestJourneyLocation = location
    
    sendEvent("onRouteProgressChange", [
      "distanceTraveled": progress.distanceTraveled,
      "distanceRemaining": progress.distanceRemaining,
      "durationRemaining": progress.durationRemaining,
      "fractionTraveled": progress.fractionTraveled
    ])
    latestJourneyProgressDistanceRemaining = progress.distanceRemaining
    latestJourneyProgressDurationRemaining = progress.durationRemaining
    latestJourneyProgressFractionTraveled = max(0, min(progress.fractionTraveled, 1))

    sendEvent("onBannerInstruction", [
      "primaryText": progress.currentLegProgress.currentStep.instructions,
      "stepDistanceRemaining": progress.currentLegProgress.currentStepProgress.distanceRemaining
    ])
    latestJourneyInstructionPrimary = progress.currentLegProgress.currentStep.instructions
    latestJourneyStepDistanceRemaining = progress.currentLegProgress.currentStepProgress.distanceRemaining
    let secondary = progress.currentLegProgress.currentStep.names?.first
      ?? progress.currentLegProgress.currentStep.description
    if !secondary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
      latestJourneyInstructionSecondary = secondary
    }
    updateFullScreenBottomSheet(
      instruction: progress.currentLegProgress.currentStep.instructions,
      progress: progress
    )
    emitJourneyData()
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
    self.isUsingCustomNativeBottomSheet = false
    self.isStartInProgress = false
    NavigationSessionRegistry.shared.release(owner: sessionOwner)
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
  @Field var mode: String?
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
  @Field var revealOnNativeBannerGesture: Bool?
  @Field var revealGestureHotzoneHeight: Double?
  @Field var revealGestureRightExclusionWidth: Double?
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
  @Field var primaryTextFontFamily: String?
  @Field var primaryTextFontWeight: String?
  @Field var secondaryTextFontSize: Double?
  @Field var secondaryTextFontFamily: String?
  @Field var secondaryTextFontWeight: String?
  @Field var actionButtonFontSize: Double?
  @Field var actionButtonFontFamily: String?
  @Field var actionButtonFontWeight: String?
  @Field var actionButtonHeight: Double?
  @Field var actionButtonsBottomPadding: Double?
  @Field var quickActionBackgroundColor: String?
  @Field var quickActionTextColor: String?
  @Field var quickActionSecondaryBackgroundColor: String?
  @Field var quickActionSecondaryTextColor: String?
  @Field var quickActionGhostTextColor: String?
  @Field var quickActionBorderColor: String?
  @Field var quickActionBorderWidth: Double?
  @Field var quickActionCornerRadius: Double?
  @Field var quickActionFontFamily: String?
  @Field var quickActionFontWeight: String?
  @Field var showCurrentStreet: Bool?
  @Field var showRemainingDistance: Bool?
  @Field var showRemainingDuration: Bool?
  @Field var showETA: Bool?
  @Field var showCompletionPercent: Bool?
  @Field var showDefaultContent: Bool?
  @Field var defaultManeuverTitle: String?
  @Field var defaultTripProgressTitle: String?
  @Field var quickActions: [BottomSheetQuickActionStartOptions]?
  @Field var customRows: [BottomSheetCustomRowStartOptions]?
  @Field var headerTitle: String?
  @Field var headerTitleFontSize: Double?
  @Field var headerTitleFontFamily: String?
  @Field var headerTitleFontWeight: String?
  @Field var headerSubtitle: String?
  @Field var headerSubtitleFontSize: Double?
  @Field var headerSubtitleFontFamily: String?
  @Field var headerSubtitleFontWeight: String?
  @Field var headerBadgeText: String?
  @Field var headerBadgeFontSize: Double?
  @Field var headerBadgeFontFamily: String?
  @Field var headerBadgeFontWeight: String?
  @Field var headerBadgeBackgroundColor: String?
  @Field var headerBadgeTextColor: String?
  @Field var headerBadgeCornerRadius: Double?
  @Field var headerBadgeBorderColor: String?
  @Field var headerBadgeBorderWidth: Double?
  @Field var cornerRadius: Double?
}

struct BottomSheetQuickActionStartOptions: Record {
  @Field var id: String
  @Field var label: String
  @Field var variant: String?
}

struct BottomSheetCustomRowStartOptions: Record {
  @Field var id: String
  @Field var iconSystemName: String?
  @Field var iconText: String?
  @Field var title: String
  @Field var value: String?
  @Field var subtitle: String?
  @Field var emphasis: Bool?
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

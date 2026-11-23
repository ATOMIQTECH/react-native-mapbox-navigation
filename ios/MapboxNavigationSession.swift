import Combine
import CoreLocation
import Foundation
import MapboxNavigationCore

@MainActor
final class MapboxNavigationSession {
  static let shared = MapboxNavigationSession()


  private(set) var isMuted = false
  private(set) var voiceVolume: Double = 1
  private(set) var distanceUnit = "metric"
  private(set) var language: String = Locale.preferredLanguages.first ?? "en"
  private var simulatesRoute = false
  private lazy var guidanceLocationManager = GuidanceLocationManager()
  private lazy var guidanceLocationClient: LocationClient = guidanceLocationManager.makeClient()
  private var isGuidanceActive = false
  private var hasPendingCoreConfig = false

  private var cachedProvider: MapboxNavigationProvider?

  nonisolated static let legAdvance = SessionFlag(true)
  nonisolated static let acceptsFasterRoutes = SessionFlag(true)

  private init() {}


  static var accessToken: String? {
    let candidates = [
      Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String,
      Bundle.main.object(forInfoDictionaryKey: "MGLMapboxAccessToken") as? String,
      UserDefaults.standard.string(forKey: "MBXAccessToken"),
    ]
    return candidates.compactMap { $0 }.first { !$0.isEmpty }
  }

  static var isConfigured: Bool {
    accessToken != nil
  }


  var existingProvider: MapboxNavigationProvider? {
    cachedProvider
  }

  var provider: MapboxNavigationProvider {
    if let cachedProvider {
      return cachedProvider
    }
    let created = MapboxNavigationProvider(coreConfig: makeCoreConfig())
    cachedProvider = created
    hasPendingCoreConfig = false
    applyVoiceSettings(to: created)
    return created
  }

  var mapboxNavigation: MapboxNavigation {
    provider.mapboxNavigation
  }

  func setMuted(_ muted: Bool) {
    isMuted = muted
    guard let cachedProvider else { return }
    applyVoiceSettings(to: cachedProvider)
    if muted {
      cachedProvider.routeVoiceController.speechSynthesizer.interruptSpeaking()
    }
  }

  func setVoiceVolume(_ volume: Double) {
    voiceVolume = volume.clampedToUnitInterval
    guard let cachedProvider else { return }
    applyVoiceSettings(to: cachedProvider)
  }

  func setDistanceUnit(_ unit: String) {
    let normalized = unit == "imperial" ? "imperial" : "metric"
    guard normalized != distanceUnit else { return }
    distanceUnit = normalized
    reapplyCoreConfig()
  }

  func setLanguage(_ language: String) {
    let trimmed = language.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty, trimmed != self.language else { return }
    self.language = trimmed
    reapplyCoreConfig()
    if let cachedProvider {
      cachedProvider.routeVoiceController.speechSynthesizer.locale = Locale(identifier: trimmed)
    }
  }

  func setGuidanceActive(_ active: Bool) {
    isGuidanceActive = active
  }

  func prepareForNavigation(
    language: String,
    distanceUnit: String,
    continuousAlternatives: Bool,
    simulatesRoute: Bool,
    routeProfile: String = "driving-traffic"
  ) {
    guidanceLocationManager.setActivityType(forProfile: routeProfile)
    Self.acceptsFasterRoutes.set(continuousAlternatives)
    if simulatesRoute != self.simulatesRoute {
      self.simulatesRoute = simulatesRoute
      hasPendingCoreConfig = true
    }
    let trimmed = language.trimmingCharacters(in: .whitespacesAndNewlines)
    if !trimmed.isEmpty, trimmed != self.language {
      self.language = trimmed
      hasPendingCoreConfig = true
    }
    let unit = distanceUnit == "imperial" ? "imperial" : "metric"
    if unit != self.distanceUnit {
      self.distanceUnit = unit
      hasPendingCoreConfig = true
    }
    guard !isGuidanceActive else { return }
    if hasPendingCoreConfig {
      reapplyCoreConfig()
    }
  }


  private func makeCoreConfig() -> CoreConfig {
    var routingConfig = RoutingConfig()
    routingConfig.fasterRouteDetectionConfig = FasterRouteDetectionConfig(
      fasterRouteApproval: .manually { [flag = Self.acceptsFasterRoutes] _ in flag.value }
    )
    var config = CoreConfig(
      credentials: .init(),
      routingConfig: routingConfig,
      locationSource: simulatesRoute ? .simulation() : .custom(guidanceLocationClient),
      locale: Locale(identifier: language),
      predictiveCacheConfig: Self.predictiveCacheConfig,
      multilegAdvancing: .manually { [flag = Self.legAdvance] _ in flag.value }
    )
    config.unitOfMeasurement = distanceUnit == "imperial" ? .imperial : .metric
    return config
  }

  private static let predictiveCacheConfig = PredictiveCacheConfig(
    predictiveCacheNavigationConfig: PredictiveCacheNavigationConfig(
      locationConfig: PredictiveCacheLocationConfig(
        currentLocationRadius: 3000,
        routeBufferRadius: 1000,
        destinationLocationRadius: 3000
      )
    ),
    predictiveCacheMapsConfig: PredictiveCacheMapsConfig(
      locationConfig: PredictiveCacheLocationConfig(
        currentLocationRadius: 2000,
        routeBufferRadius: 500,
        destinationLocationRadius: 2000
      ),
      maximumConcurrentRequests: 2,
      zoomRange: 0...16
    )
  )

  private func reapplyCoreConfig() {
    guard let cachedProvider else {
      hasPendingCoreConfig = false
      return
    }
    guard !isGuidanceActive else {
      hasPendingCoreConfig = true
      return
    }
    hasPendingCoreConfig = false
    cachedProvider.apply(coreConfig: makeCoreConfig())
    Task { @MainActor [weak self] in
      guard let self, let provider = self.cachedProvider, provider === cachedProvider else { return }
      self.applyVoiceSettings(to: provider)
    }
  }

  private func applyVoiceSettings(to provider: MapboxNavigationProvider) {
    let synthesizer = provider.routeVoiceController.speechSynthesizer
    synthesizer.muted = isMuted
    synthesizer.volume = voiceVolume >= 1 ? .system : .override(Float(voiceVolume))
    synthesizer.locale = Locale(identifier: language)
  }
}

extension Double {
  fileprivate var clampedToUnitInterval: Double {
    Swift.max(0, Swift.min(self, 1))
  }
}

private final class GuidanceLocationManager: NSObject, CLLocationManagerDelegate {
  private let manager = CLLocationManager()
  private let locationsSubject = PassthroughSubject<CLLocation, Never>()
  private let headingSubject = PassthroughSubject<CLHeading, Never>()

  override init() {
    super.init()
    manager.requestWhenInUseAuthorization()
    manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
    manager.activityType = .automotiveNavigation
    manager.pausesLocationUpdatesAutomatically = false
    manager.distanceFilter = kCLDistanceFilterNone
    let backgroundModes = Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String] ?? []
    if backgroundModes.contains("location") {
      manager.allowsBackgroundLocationUpdates = true
      manager.showsBackgroundLocationIndicator = true
    }
    manager.delegate = self
  }

  func setActivityType(forProfile profile: String) {
    switch profile.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "walking": manager.activityType = .fitness
    case "cycling": manager.activityType = .otherNavigation
    default: manager.activityType = .automotiveNavigation
    }
  }

  func makeClient() -> LocationClient {
    let delegate = self
    return LocationClient(
      locations: delegate.locationsSubject.eraseToAnyPublisher(),
      headings: delegate.headingSubject.eraseToAnyPublisher(),
      startUpdatingLocation: { delegate.manager.startUpdatingLocation() },
      stopUpdatingLocation: { delegate.manager.stopUpdatingLocation() },
      startUpdatingHeading: { delegate.manager.startUpdatingHeading() },
      stopUpdatingHeading: { delegate.manager.stopUpdatingHeading() }
    )
  }

  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    if let location = locations.last {
      locationsSubject.send(location)
    }
  }

  func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
    headingSubject.send(newHeading)
  }
}

final class SessionFlag: @unchecked Sendable {
  private let lock = NSLock()
  private var current: Bool

  init(_ initial: Bool) {
    current = initial
  }

  var value: Bool {
    lock.lock(); defer { lock.unlock() }
    return current
  }

  func set(_ newValue: Bool) {
    lock.lock(); current = newValue; lock.unlock()
  }
}

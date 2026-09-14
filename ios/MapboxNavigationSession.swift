import Foundation
import MapboxNavigationCore

/**
 Owns the v3 `MapboxNavigationProvider` and the navigation settings that used to
 live on `NavigationSettings.shared`.

 Navigation SDK v3 removed `NavigationSettings` entirely. In v2 the mute flag,
 voice volume and distance unit were global mutable state that could be set at
 any time — before, during or between navigation sessions — and the SDK observed
 them. v3 replaces that with two per-provider surfaces: `CoreConfig`
 (units, locale, location source) and the voice controller's
 `SpeechSynthesizing` (muted, volume, locale).

 That change would leak into the public JS API if handled naively, because
 `setMuted()` and friends can legitimately be called before any
 `<MapboxNavigationView>` exists. This store therefore keeps the *desired*
 settings independently of the provider: values are recorded immediately, baked
 into `CoreConfig` when the provider is first built, and pushed onto a live
 provider whenever one exists. The JS signatures do not change.

 Creation is deliberately lazy. Constructing a `MapboxNavigationProvider`
 spins up the navigator and starts a billing session, so it must not happen at
 module load — only when navigation is actually about to start. v2 had the same
 property via `Directions.shared` plus a per-session `MapboxNavigationService`.

 `@MainActor` because `MapboxNavigationProvider.routeVoiceController` and
 `SpeechSynthesizing` are both main-actor isolated in v3.
 */
@MainActor
final class MapboxNavigationSession {
  static let shared = MapboxNavigationSession()

  // MARK: - Desired settings

  /// Mirrors of the settings v2 read back from `NavigationSettings.shared`.
  /// `getNavigationSettings()` reports these, so they must stay authoritative
  /// even when no provider has been created yet.
  private(set) var isMuted = false
  private(set) var voiceVolume: Double = 1
  private(set) var distanceUnit = "metric"
  private(set) var language: String = Locale.preferredLanguages.first ?? "en"
  private var simulatesRoute = false

  private var cachedProvider: MapboxNavigationProvider?

  private init() {}

  // MARK: - Access token

  /// The Mapbox access token, resolved exactly as the SDK resolves it.
  ///
  /// Mirrors `ApiConfiguration.default` in `MapboxNavigationCore`, which reads
  /// `MBXAccessToken`, then the legacy `MGLMapboxAccessToken`, then a
  /// `UserDefaults` override. Kept in sync deliberately: the point is to know
  /// in advance whether the SDK will find a token, so we never construct a
  /// provider that cannot work.
  static var accessToken: String? {
    let candidates = [
      Bundle.main.object(forInfoDictionaryKey: "MBXAccessToken") as? String,
      Bundle.main.object(forInfoDictionaryKey: "MGLMapboxAccessToken") as? String,
      UserDefaults.standard.string(forKey: "MBXAccessToken"),
    ]
    return candidates.compactMap { $0 }.first { !$0.isEmpty }
  }

  /// Whether a usable access token is configured.
  ///
  /// Without one, `MapboxNavigationProvider` is unusable and the SDK reacts
  /// badly in both build configurations: `ApiConfiguration.default` calls
  /// `assertionFailure`, which **traps in Debug** and in Release silently
  /// returns an empty token, leaving every Directions request to fail with an
  /// opaque auth error. Neither is catchable from Swift, so the only way to
  /// keep the promise that this package never crashes its host app is to check
  /// first and refuse to build the provider.
  static var isConfigured: Bool {
    accessToken != nil
  }

  // MARK: - Provider lifecycle

  /// The live provider, if one has been created. Does **not** create one.
  var existingProvider: MapboxNavigationProvider? {
    cachedProvider
  }

  /// The provider, creating it on first use.
  var provider: MapboxNavigationProvider {
    if let cachedProvider {
      return cachedProvider
    }
    let created = MapboxNavigationProvider(coreConfig: makeCoreConfig())
    cachedProvider = created
    applyVoiceSettings(to: created)
    return created
  }

  var mapboxNavigation: MapboxNavigation {
    provider.mapboxNavigation
  }

  /// Tear the provider down between navigation sessions.
  ///
  /// The settings above intentionally survive this, matching v2 — where they
  /// were process-global and outlived any individual navigation service.
  func invalidateProvider() {
    cachedProvider = nil
  }

  // MARK: - Settings

  func setMuted(_ muted: Bool) {
    isMuted = muted
    guard let cachedProvider else { return }
    applyVoiceSettings(to: cachedProvider)
  }

  func setVoiceVolume(_ volume: Double) {
    voiceVolume = volume.clampedToUnitInterval
    guard let cachedProvider else { return }
    applyVoiceSettings(to: cachedProvider)
  }

  func setDistanceUnit(_ unit: String) {
    distanceUnit = unit == "imperial" ? "imperial" : "metric"
    reapplyCoreConfig()
  }

  func setLanguage(_ language: String) {
    self.language = language
    reapplyCoreConfig()
    if let cachedProvider {
      // The synthesizer keeps its own locale, which is what actually selects the
      // spoken-instruction voice.
      cachedProvider.routeVoiceController.speechSynthesizer.locale = Locale(identifier: language)
    }
  }

  /// Backs the `shouldSimulateRoute` prop.
  ///
  /// v2 passed `simulating: .always` to `MapboxNavigationService`'s
  /// initialiser. v3 moves it into `CoreConfig.locationSource`, which means it
  /// has to be set *before* the provider is built — so changing it invalidates
  /// any existing provider rather than trying to mutate a running navigator.
  func setSimulatesRoute(_ simulate: Bool) {
    guard simulatesRoute != simulate else { return }
    simulatesRoute = simulate
    invalidateProvider()
  }

  // MARK: - Internals

  private func makeCoreConfig() -> CoreConfig {
    var config = CoreConfig(
      // `.init()` reads the public token from `MBXAccessToken` in Info.plist,
      // the same source v2's `Directions.shared` used.
      credentials: .init(),
      locationSource: simulatesRoute ? .simulation() : .live,
      locale: Locale(identifier: language)
    )
    config.unitOfMeasurement = distanceUnit == "imperial" ? .imperial : .metric
    return config
  }

  private func reapplyCoreConfig() {
    guard let cachedProvider else { return }
    cachedProvider.apply(coreConfig: makeCoreConfig())
  }

  private func applyVoiceSettings(to provider: MapboxNavigationProvider) {
    let synthesizer = provider.routeVoiceController.speechSynthesizer
    synthesizer.muted = isMuted
    // v2 took a `Float` in 0...1 directly. v3 models "follow the system volume"
    // and "use an explicit level" as distinct cases, so a full-volume request
    // maps to `.system` — which is what v2's 1.0 effectively meant — and
    // anything lower to an explicit override.
    synthesizer.volume = voiceVolume >= 1 ? .system : .override(Float(voiceVolume))
    synthesizer.locale = Locale(identifier: language)
  }
}

extension Double {
  fileprivate var clampedToUnitInterval: Double {
    Swift.max(0, Swift.min(self, 1))
  }
}

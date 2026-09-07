import CoreGraphics
import Foundation
import MapboxMaps
import MapboxNavigation
import UIKit

/// Navigation states a custom puck can be bound to.
///
/// Mirrors the JS `LocationPuckStates` keys. iOS has no equivalent of Android's
/// `LocationPuckOptions`, so the view swaps `userLocationStyle` as the session
/// moves between these states.
enum LocationPuckState: String, CaseIterable {
  case `default`
  case freeDrive
  case destinationPreview
  case routePreview
  case activeNavigation
  case arrival
  case idle
}

/// The outcome of resolving one puck appearance.
enum LocationPuckResolution {
  /// Apply this style to `NavigationMapView.userLocationStyle`.
  case style(UserLocationStyle)
  /// Hide the puck. `userLocationStyle = nil` draws a transparent puck while
  /// keeping location updates flowing.
  case hidden
  /// Leave the Mapbox SDK default in place.
  case sdkDefault
}

/// Loads remote/bundled assets and builds `UserLocationStyle` values from the
/// normalized dictionaries produced by the JS `resolveLocationPuck` helper.
///
/// Asset loading is asynchronous (React Native `require()` resolves to a
/// packager URL in development), so resolution is completion-based. Downloads
/// are cached on disk and decoded images are cached in memory, which keeps
/// per-state pucks from refetching the same file.
final class LocationPuckFactory {
  static let shared = LocationPuckFactory()

  private let imageCache = NSCache<NSString, UIImage>()
  private let session: URLSession
  private let fileManager = FileManager.default
  private let cacheDirectory: URL

  private init() {
    let configuration = URLSessionConfiguration.ephemeral
    configuration.timeoutIntervalForRequest = 20
    configuration.timeoutIntervalForResource = 60
    session = URLSession(configuration: configuration)

    let base = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first
      ?? URL(fileURLWithPath: NSTemporaryDirectory())
    cacheDirectory = base.appendingPathComponent("RNMapboxNavigationPuckAssets", isDirectory: true)
    try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
  }

  // MARK: - Parsing

  /// Split a normalized `locationPuck` payload into per-state dictionaries.
  static func parseStates(_ payload: [String: Any]?) -> [LocationPuckState: [String: Any]] {
    guard let payload else { return [:] }

    var result = [LocationPuckState: [String: Any]]()
    for state in LocationPuckState.allCases {
      if let entry = payload[state.rawValue] as? [String: Any] {
        result[state] = entry
      }
    }
    return result
  }

  // MARK: - Resolution

  /// Build a `UserLocationStyle` for one appearance dictionary.
  ///
  /// The completion is always invoked on the main queue.
  func resolve(
    appearance: [String: Any],
    completion: @escaping (LocationPuckResolution) -> Void
  ) {
    let finish: (LocationPuckResolution) -> Void = { resolution in
      if Thread.isMainThread {
        completion(resolution)
      } else {
        DispatchQueue.main.async { completion(resolution) }
      }
    }

    switch (appearance["type"] as? String)?.lowercased() {
    case "none":
      finish(.hidden)
    case "default":
      finish(.sdkDefault)
    case "tinted":
      finish(.style(makeTintedStyle(appearance)))
    case "2d":
      make2DStyle(appearance, completion: finish)
    case "3d":
      make3DStyle(appearance, completion: finish)
    default:
      finish(.sdkDefault)
    }
  }

  // MARK: - Tinted

  /// Recolor Mapbox's built-in course puck.
  ///
  /// `UserPuckCourseView` draws the standard navigation puck and exposes its
  /// three colors, so tinting needs no image or model assets.
  private func makeTintedStyle(_ appearance: [String: Any]) -> UserLocationStyle {
    let scale = (appearance["scale"] as? NSNumber)?.doubleValue ?? 1
    let side = CGFloat((scale.isFinite ? scale : 1).clamped(to: 0.2...4)) * 75.0
    let courseView = UserPuckCourseView(frame: CGRect(origin: .zero, size: CGSize(width: side, height: side)))

    // `color` paints the puck body/arrow; `haloColor` paints the surrounding
    // circle. Fall back to `bearingColor` so a caller supplying only that key
    // still gets a visibly tinted puck.
    if let body = Self.color(appearance["color"]) ?? Self.color(appearance["bearingColor"]) {
      courseView.puckColor = body
      courseView.shadowColor = body.withAlphaComponent(0.16)
    }
    if let halo = Self.color(appearance["haloColor"]) {
      courseView.fillColor = halo
    }
    if let opacity = (appearance["opacity"] as? NSNumber)?.doubleValue, opacity.isFinite {
      courseView.alpha = CGFloat(opacity.clamped(to: 0...1))
    }

    return .courseView(courseView)
  }

  // MARK: - 2D

  private func make2DStyle(
    _ appearance: [String: Any],
    completion: @escaping (LocationPuckResolution) -> Void
  ) {
    let keys = ["topImage", "bearingImage", "shadowImage"]
    let sources = keys.map { appearance[$0] as? String }

    loadImages(sources) { [weak self] images in
      guard self != nil else {
        completion(.sdkDefault)
        return
      }

      let top = images[0]
      let bearing = images[1]
      let shadow = images[2]

      // Every image failed to load — better to keep the SDK puck than to render
      // an invisible pointer.
      guard top != nil || bearing != nil || shadow != nil else {
        completion(.sdkDefault)
        return
      }

      let opacity = (appearance["opacity"] as? NSNumber)?.doubleValue ?? 1
      let configuration = Puck2DConfiguration(
        topImage: top,
        bearingImage: bearing,
        shadowImage: shadow,
        scale: Self.doubleValue(
          scale: appearance["scale"],
          expression: appearance["scaleExpression"]
        ),
        // Disambiguates the two Puck2DConfiguration overloads that would
        // otherwise both match these argument labels.
        pulsing: nil,
        showsAccuracyRing: false,
        opacity: opacity.isFinite ? opacity.clamped(to: 0...1) : 1
      )

      completion(.style(.puck2D(configuration: configuration)))
    }
  }

  // MARK: - 3D

  private func make3DStyle(
    _ appearance: [String: Any],
    completion: @escaping (LocationPuckResolution) -> Void
  ) {
    guard let rawUri = appearance["modelUri"] as? String, !rawUri.isEmpty else {
      completion(.sdkDefault)
      return
    }

    resolveModelURL(rawUri) { url in
      guard let url else {
        NSLog("[react-native-mapbox-navigation] Could not load 3D puck model: %@", rawUri)
        completion(.sdkDefault)
        return
      }

      // `orientation` is the model's own axis correction, applied before the
      // course bearing rotates it.
      let orientation = Self.doubleArray(appearance["modelRotation"]) ?? [0, 0, 0]
      let model = Model(uri: url, orientation: orientation)

      var configuration = Puck3DConfiguration(model: model)

      if let expression = Self.expression(appearance["scaleExpression"]) {
        configuration.modelScale = .expression(expression)
      } else if let scale = Self.doubleArray(appearance["modelScale"]) {
        configuration.modelScale = .constant(scale)
      }

      if let opacity = (appearance["opacity"] as? NSNumber)?.doubleValue, opacity.isFinite {
        configuration.modelOpacity = .constant(opacity.clamped(to: 0...1))
      }

      completion(.style(.puck3D(configuration: configuration)))
    }
  }

  // MARK: - Asset loading

  /// Load several images, preserving index order. Missing entries stay `nil`.
  private func loadImages(
    _ sources: [String?],
    completion: @escaping ([UIImage?]) -> Void
  ) {
    var results = [UIImage?](repeating: nil, count: sources.count)
    let group = DispatchGroup()
    // Image loads complete on arbitrary URLSession queues, so guard the shared
    // results buffer rather than writing to it concurrently.
    let lock = NSLock()

    for (index, source) in sources.enumerated() {
      guard let source, !source.isEmpty else { continue }
      group.enter()
      loadImage(source) { image in
        lock.lock()
        results[index] = image
        lock.unlock()
        group.leave()
      }
    }

    group.notify(queue: .main) {
      completion(results)
    }
  }

  private func loadImage(_ source: String, completion: @escaping (UIImage?) -> Void) {
    if let cached = imageCache.object(forKey: source as NSString) {
      completion(cached)
      return
    }

    let store: (UIImage?) -> Void = { [weak self] image in
      if let image {
        self?.imageCache.setObject(image, forKey: source as NSString)
      }
      completion(image)
    }

    // A bare name refers to an asset catalog entry or a bundled file.
    guard let url = URL(string: source), let scheme = url.scheme?.lowercased() else {
      store(UIImage(named: source))
      return
    }

    switch scheme {
    case "http", "https":
      session.dataTask(with: url) { data, _, _ in
        store(data.flatMap(UIImage.init(data:)))
      }.resume()
    case "file":
      store((try? Data(contentsOf: url)).flatMap(UIImage.init(data:)))
    default:
      store(UIImage(named: source))
    }
  }

  /// Resolve a model reference to a local file URL.
  ///
  /// Remote models are downloaded and cached so that the Maps SDK always reads
  /// from disk — this keeps model loading working offline after first use and
  /// avoids repeated fetches when several states share a model.
  private func resolveModelURL(_ source: String, completion: @escaping (URL?) -> Void) {
    let finish: (URL?) -> Void = { url in
      if Thread.isMainThread {
        completion(url)
      } else {
        DispatchQueue.main.async { completion(url) }
      }
    }

    guard let url = URL(string: source), let scheme = url.scheme?.lowercased() else {
      // Treat as a bundled resource name, with or without an extension.
      let asURL = URL(fileURLWithPath: source)
      let ext = asURL.pathExtension
      let name = asURL.deletingPathExtension().lastPathComponent
      let resolved = ext.isEmpty
        ? (Bundle.main.url(forResource: name, withExtension: "glb")
          ?? Bundle.main.url(forResource: name, withExtension: "gltf"))
        : Bundle.main.url(forResource: name, withExtension: ext)
      finish(resolved)
      return
    }

    if scheme == "file" {
      finish(fileManager.fileExists(atPath: url.path) ? url : nil)
      return
    }

    guard scheme == "http" || scheme == "https" else {
      finish(nil)
      return
    }

    // Key the cache on the full URL so packager hashes and CDN versions produce
    // distinct files, while preserving the extension for the Maps SDK.
    let ext = url.pathExtension.isEmpty ? "glb" : url.pathExtension
    let key = String(format: "%08x", UInt32(bitPattern: Int32(truncatingIfNeeded: source.hashValue)))
    let destination = cacheDirectory.appendingPathComponent("puck-\(key).\(ext)")

    if fileManager.fileExists(atPath: destination.path) {
      finish(destination)
      return
    }

    session.downloadTask(with: url) { [weak self] temporaryURL, response, _ in
      guard let self,
            let temporaryURL,
            (response as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) ?? true
      else {
        finish(nil)
        return
      }

      do {
        if self.fileManager.fileExists(atPath: destination.path) {
          try self.fileManager.removeItem(at: destination)
        }
        try self.fileManager.moveItem(at: temporaryURL, to: destination)
        finish(destination)
      } catch {
        NSLog(
          "[react-native-mapbox-navigation] Failed to cache puck model: %@",
          error.localizedDescription
        )
        finish(nil)
      }
    }.resume()
  }

  // MARK: - Value helpers

  private static func color(_ raw: Any?) -> UIColor? {
    guard let hex = (raw as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
          hex.hasPrefix("#") else {
      return nil
    }

    var digits = String(hex.dropFirst())
    if digits.count == 3 {
      digits = digits.map { "\($0)\($0)" }.joined()
    }
    guard digits.count == 6 || digits.count == 8,
          let value = UInt64(digits, radix: 16) else {
      return nil
    }

    let hasAlpha = digits.count == 8
    let r = CGFloat((value >> (hasAlpha ? 24 : 16)) & 0xFF) / 255
    let g = CGFloat((value >> (hasAlpha ? 16 : 8)) & 0xFF) / 255
    let b = CGFloat((value >> (hasAlpha ? 8 : 0)) & 0xFF) / 255
    let a = hasAlpha ? CGFloat(value & 0xFF) / 255 : 1

    return UIColor(red: r, green: g, blue: b, alpha: a)
  }

  private static func doubleArray(_ raw: Any?) -> [Double]? {
    guard let numbers = raw as? [NSNumber], !numbers.isEmpty else {
      return nil
    }
    let values = numbers.map(\.doubleValue)
    return values.allSatisfy(\.isFinite) ? values : nil
  }

  /// Decode a JSON Mapbox style expression supplied from JS.
  private static func expression(_ raw: Any?) -> Expression? {
    guard let json = (raw as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
          !json.isEmpty,
          let data = json.data(using: .utf8) else {
      return nil
    }
    return try? JSONDecoder().decode(Expression.self, from: data)
  }

  /// Prefer an expression when present, otherwise a constant scale.
  private static func doubleValue(scale: Any?, expression rawExpression: Any?) -> Value<Double>? {
    if let expression = expression(rawExpression) {
      return .expression(expression)
    }
    guard let value = (scale as? NSNumber)?.doubleValue, value.isFinite else {
      return nil
    }
    return .constant(value)
  }
}

private extension Comparable {
  func clamped(to range: ClosedRange<Self>) -> Self {
    min(max(self, range.lowerBound), range.upperBound)
  }
}

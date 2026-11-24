import CoreGraphics
import CryptoKit
import Foundation
import MapboxMaps
import UIKit

enum LocationPuckState: String, CaseIterable {
  case `default`
  case freeDrive
  case destinationPreview
  case routePreview
  case activeNavigation
  case arrival
  case idle
}

enum LocationPuckResolution {
  case style(PuckType)
  case hidden
  case sdkDefault
}

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


  private static let tintedPuckBaseSide: CGFloat = 96

  private func makeTintedStyle(_ appearance: [String: Any]) -> PuckType {
    let rawScale = (appearance["scale"] as? NSNumber)?.doubleValue ?? 1
    let scale = (rawScale.isFinite ? rawScale : 1).clamped(to: 0.2...4)

    let body = HexColor.parse(appearance["color"])
      ?? HexColor.parse(appearance["bearingColor"])
      ?? UIColor(red: 0.22, green: 0.49, blue: 0.96, alpha: 1)
    let halo = HexColor.parse(appearance["haloColor"]) ?? UIColor.white

    let rawOpacity = (appearance["opacity"] as? NSNumber)?.doubleValue
    let opacity = (rawOpacity?.isFinite == true) ? rawOpacity!.clamped(to: 0...1) : 1

    let side = Self.tintedPuckBaseSide

    return .puck2D(
      Puck2DConfiguration(
        topImage: Self.circleImage(diameter: side * 0.42, fill: body, border: halo, borderWidth: side * 0.055),
        bearingImage: Self.bearingArrowImage(side: side, fill: body),
        shadowImage: Self.circleImage(diameter: side, fill: halo, border: nil, borderWidth: 0),
        scale: .constant(scale),
        pulsing: nil,
        showsAccuracyRing: false,
        opacity: opacity
      )
    )
  }

  private static func circleImage(
    diameter: CGFloat,
    fill: UIColor,
    border: UIColor?,
    borderWidth: CGFloat
  ) -> UIImage {
    let size = CGSize(width: diameter, height: diameter)
    return UIGraphicsImageRenderer(size: size).image { context in
      let cgContext = context.cgContext
      let inset = borderWidth / 2
      let rect = CGRect(origin: .zero, size: size).insetBy(dx: inset, dy: inset)

      cgContext.setFillColor(fill.cgColor)
      cgContext.fillEllipse(in: rect)

      if let border, borderWidth > 0 {
        cgContext.setStrokeColor(border.cgColor)
        cgContext.setLineWidth(borderWidth)
        cgContext.strokeEllipse(in: rect)
      }
    }
  }

  private static func bearingArrowImage(side: CGFloat, fill: UIColor) -> UIImage {
    let size = CGSize(width: side, height: side)
    return UIGraphicsImageRenderer(size: size).image { context in
      let cgContext = context.cgContext
      let midX = side / 2
      let halfWidth = side * 0.19
      let top = side * 0.06
      let bottom = side * 0.40

      let path = UIBezierPath()
      path.move(to: CGPoint(x: midX, y: top))
      path.addLine(to: CGPoint(x: midX + halfWidth, y: bottom))
      path.addLine(to: CGPoint(x: midX - halfWidth, y: bottom))
      path.close()

      cgContext.setFillColor(fill.cgColor)
      cgContext.addPath(path.cgPath)
      cgContext.fillPath()
    }
  }

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
        pulsing: nil,
        showsAccuracyRing: false,
        opacity: opacity.isFinite ? opacity.clamped(to: 0...1) : 1
      )

      completion(.style(.puck2D(configuration)))
    }
  }

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

      completion(.style(.puck3D(configuration)))
    }
  }


  private func loadImages(
    _ sources: [String?],
    completion: @escaping ([UIImage?]) -> Void
  ) {
    var results = [UIImage?](repeating: nil, count: sources.count)
    let group = DispatchGroup()
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

  private func resolveModelURL(_ source: String, completion: @escaping (URL?) -> Void) {
    let finish: (URL?) -> Void = { url in
      if Thread.isMainThread {
        completion(url)
      } else {
        DispatchQueue.main.async { completion(url) }
      }
    }

    guard let url = URL(string: source), let scheme = url.scheme?.lowercased() else {
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

    let ext = url.pathExtension.isEmpty ? "glb" : url.pathExtension
    let key = SHA256.hash(data: Data(source.utf8)).prefix(16).map { String(format: "%02x", $0) }.joined()
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

  private static func doubleArray(_ raw: Any?) -> [Double]? {
    guard let numbers = raw as? [NSNumber], !numbers.isEmpty else {
      return nil
    }
    let values = numbers.map(\.doubleValue)
    return values.allSatisfy(\.isFinite) ? values : nil
  }

  private static func expression(_ raw: Any?) -> MapboxMaps.Expression? {
    guard let json = (raw as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
          !json.isEmpty,
          let data = json.data(using: .utf8) else {
      return nil
    }
    return try? JSONDecoder().decode(MapboxMaps.Expression.self, from: data)
  }

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

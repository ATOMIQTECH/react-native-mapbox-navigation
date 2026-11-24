import UIKit

struct NavigationMarkerImage {
  let uri: String
  let scale: CGFloat

  var cacheKey: String { "\(scale)@\(uri)" }
}

@MainActor
enum NavigationMarkerImageLoader {
  private static let cache: NSCache<NSString, UIImage> = {
    let cache = NSCache<NSString, UIImage>()
    cache.countLimit = 64
    return cache
  }()
  private static var waiting = [String: [() -> Void]]()

  static func cached(_ image: NavigationMarkerImage) -> UIImage? {
    cache.object(forKey: image.cacheKey as NSString)
  }

  static func load(_ image: NavigationMarkerImage, then done: @escaping () -> Void) {
    let key = image.cacheKey
    if cache.object(forKey: key as NSString) != nil {
      done()
      return
    }
    if waiting[key] != nil {
      waiting[key]?.append(done)
      return
    }
    waiting[key] = [done]
    Task.detached(priority: .utility) {
      let decoded = await decode(image)
      await MainActor.run {
        if let decoded { cache.setObject(decoded, forKey: key as NSString) }
        let callbacks = waiting.removeValue(forKey: key) ?? []
        if decoded != nil { callbacks.forEach { $0() } }
      }
    }
  }

  nonisolated private static func decode(_ image: NavigationMarkerImage) async -> UIImage? {
    let uri = image.uri
    if uri.hasPrefix("data:"), let comma = uri.firstIndex(of: ",") {
      return Data(base64Encoded: String(uri[uri.index(after: comma)...]))
        .flatMap { UIImage(data: $0, scale: image.scale) }
    }
    guard let url = URL(string: uri), let scheme = url.scheme?.lowercased() else {
      return UIImage(named: uri)
    }
    switch scheme {
    case "http", "https":
      let data: Data? = await withCheckedContinuation { continuation in
        URLSession.shared.dataTask(with: url) { data, _, _ in
          continuation.resume(returning: data)
        }.resume()
      }
      return data.flatMap { UIImage(data: $0, scale: image.scale) }
    case "file":
      return (try? Data(contentsOf: url)).flatMap { UIImage(data: $0, scale: image.scale) }
    default:
      return UIImage(named: uri)
    }
  }
}

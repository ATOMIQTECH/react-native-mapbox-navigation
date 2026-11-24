import Foundation

final class MapboxNavigationEventBridge {
  static let shared = MapboxNavigationEventBridge()

  private let lock = NSLock()
  private var emitter: ((String, [String: Any]) -> Void)?
  private var observerCounts = [String: Int]()

  private init() {}

  func setEmitter(_ next: @escaping (String, [String: Any]) -> Void) {
    lock.lock()
    defer { lock.unlock() }
    emitter = next
  }

  func clearEmitter() {
    lock.lock()
    defer { lock.unlock() }
    emitter = nil
    observerCounts.removeAll()
  }

  func startObserving(_ eventName: String) {
    lock.lock()
    defer { lock.unlock() }
    observerCounts[eventName, default: 0] += 1
  }

  func stopObserving(_ eventName: String) {
    lock.lock()
    defer { lock.unlock() }
    guard let current = observerCounts[eventName] else { return }
    if current <= 1 {
      observerCounts.removeValue(forKey: eventName)
    } else {
      observerCounts[eventName] = current - 1
    }
  }

  func isObserved(_ eventName: String) -> Bool {
    lock.lock()
    defer { lock.unlock() }
    return observerCounts[eventName] != nil
  }

  func emit(_ eventName: String, _ payload: [String: Any] = [:]) {
    lock.lock()
    let isObserved = observerCounts[eventName] != nil
    let handler = emitter
    lock.unlock()

    guard isObserved, let handler else { return }
    handler(eventName, payload)
  }
}

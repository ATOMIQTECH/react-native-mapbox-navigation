import Foundation

/**
 Relays navigation events from the embedded view to the module's event emitter,
 which backs the `add*Listener` helpers exported from JS.

 iOS previously sent no module-level events at all — the view dispatched only
 *view* events (the `onX` props), so every `add*Listener` helper silently
 received nothing. This mirrors the Android `MapboxNavigationEventBridge`.

 Emission is gated on whether JS actually holds a subscription for the event.
 The module reports that through `startObserving` / `stopObserving`, so an app
 that never calls a listener helper pays no bridge cost for it — which matters
 during active guidance, where the continuous events fire on every location
 update.
 */
final class MapboxNavigationEventBridge {
  static let shared = MapboxNavigationEventBridge()

  private let lock = NSLock()
  private var emitter: ((String, [String: Any]) -> Void)?
  /// Subscription counts per event name, keyed by the JS event name.
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

  /// Whether JS currently holds at least one subscription for `eventName`.
  func isObserved(_ eventName: String) -> Bool {
    lock.lock()
    defer { lock.unlock() }
    return observerCounts[eventName] != nil
  }

  /// Emit to JS module listeners, skipping the bridge crossing entirely when
  /// nothing is subscribed.
  func emit(_ eventName: String, _ payload: [String: Any] = [:]) {
    lock.lock()
    let isObserved = observerCounts[eventName] != nil
    let handler = emitter
    lock.unlock()

    guard isObserved, let handler else { return }
    handler(eventName, payload)
  }
}

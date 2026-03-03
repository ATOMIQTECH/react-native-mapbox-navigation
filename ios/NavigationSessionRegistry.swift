import Foundation

final class NavigationSessionRegistry {
  static let shared = NavigationSessionRegistry()

  private let lock = NSLock()
  private var owner: String?
  private var stopHandlers: [String: () -> Void] = [:]
  private var resumeCameraHandlers: [String: () -> Void] = [:]
  private var cameraFollowingProviders: [String: () -> Bool] = [:]

  private init() {}

  func acquire(owner newOwner: String) -> Bool {
    lock.lock()
    defer { lock.unlock() }

    if let current = owner, current != newOwner {
      return false
    }
    owner = newOwner
    return true
  }

  func release(owner releasingOwner: String) {
    lock.lock()
    defer { lock.unlock() }

    stopHandlers.removeValue(forKey: releasingOwner)
    resumeCameraHandlers.removeValue(forKey: releasingOwner)
    cameraFollowingProviders.removeValue(forKey: releasingOwner)
    guard owner == releasingOwner else {
      return
    }
    owner = nil
  }

  func registerStopHandler(owner: String, handler: @escaping () -> Void) {
    lock.lock()
    defer { lock.unlock() }
    stopHandlers[owner] = handler
  }

  func registerResumeCameraFollowingHandler(owner: String, handler: @escaping () -> Void) {
    lock.lock()
    defer { lock.unlock() }
    resumeCameraHandlers[owner] = handler
  }

  func registerCameraFollowingProvider(owner: String, provider: @escaping () -> Bool) {
    lock.lock()
    defer { lock.unlock() }
    cameraFollowingProviders[owner] = provider
  }

  func requestStopCurrent() -> Bool {
    lock.lock()
    let current = owner
    let handler = current.flatMap { stopHandlers[$0] }
    lock.unlock()
    guard let handler else {
      return false
    }
    handler()
    return true
  }

  func requestResumeCameraFollowingCurrent() -> Bool {
    lock.lock()
    let current = owner
    let handler = current.flatMap { resumeCameraHandlers[$0] }
    lock.unlock()
    guard let handler else {
      return false
    }
    handler()
    return true
  }

  func isCurrentCameraFollowing() -> Bool {
    lock.lock()
    let current = owner
    let provider = current.flatMap { cameraFollowingProviders[$0] }
    lock.unlock()
    guard let provider else {
      return true
    }
    return provider()
  }
}

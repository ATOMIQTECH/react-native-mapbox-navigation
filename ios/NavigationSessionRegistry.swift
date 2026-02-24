import Foundation

final class NavigationSessionRegistry {
  static let shared = NavigationSessionRegistry()

  private let lock = NSLock()
  private var owner: String?

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

    guard owner == releasingOwner else {
      return
    }
    owner = nil
  }
}


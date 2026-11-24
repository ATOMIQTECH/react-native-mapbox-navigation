import Foundation

final class DeferredWork {
  private var item: DispatchWorkItem?

  func schedule(after delay: TimeInterval, _ work: @escaping () -> Void) {
    cancel()
    let next = DispatchWorkItem(block: work)
    item = next
    DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: next)
  }

  func cancel() {
    item?.cancel()
    item = nil
  }
}

package expo.modules.mapboxnavigation

import java.util.concurrent.ConcurrentHashMap

/**
 * Relays navigation events from the embedded view to the module's event
 * emitter, which backs the `add*Listener` helpers exported from JS.
 *
 * Without this relay the view only dispatched *view* events (the `onX` props),
 * so every `add*Listener` helper silently received nothing.
 *
 * Emission is gated on whether JS actually holds a subscription for the event.
 * The module reports that through [startObserving] / [stopObserving], so an app
 * that never calls a listener helper pays no bridge cost for it — which matters
 * during active guidance, where the continuous events fire on every location
 * update.
 */
object MapboxNavigationEventBridge {
  @Volatile
  private var emitter: ((String, Map<String, Any?>) -> Unit)? = null

  /** Subscription counts per event name, keyed by the JS event name. */
  private val observerCounts = ConcurrentHashMap<String, Int>()

  fun setEmitter(nextEmitter: (String, Map<String, Any?>) -> Unit) {
    emitter = nextEmitter
  }

  fun clearEmitter() {
    emitter = null
    observerCounts.clear()
  }

  fun startObserving(eventName: String) {
    observerCounts.compute(eventName) { _, current -> (current ?: 0) + 1 }
  }

  fun stopObserving(eventName: String) {
    observerCounts.compute(eventName) { _, current ->
      val next = (current ?: 0) - 1
      if (next <= 0) null else next
    }
  }

  /** Whether JS currently holds at least one subscription for [eventName]. */
  fun isObserved(eventName: String): Boolean = observerCounts.containsKey(eventName)

  /**
   * Emit to JS module listeners, skipping the bridge crossing entirely when
   * nothing is subscribed.
   */
  fun emit(eventName: String, payload: Map<String, Any?> = emptyMap()) {
    if (!isObserved(eventName)) return
    emitter?.invoke(eventName, payload)
  }
}

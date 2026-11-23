package expo.modules.mapboxnavigation

import java.util.concurrent.ConcurrentHashMap

object MapboxNavigationEventBridge {
  @Volatile
  private var emitter: ((String, Map<String, Any?>) -> Unit)? = null

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

  fun isObserved(eventName: String): Boolean = observerCounts.containsKey(eventName)

  fun emit(eventName: String, payload: Map<String, Any?> = emptyMap()) {
    if (!isObserved(eventName)) return
    emitter?.invoke(eventName, payload)
  }
}

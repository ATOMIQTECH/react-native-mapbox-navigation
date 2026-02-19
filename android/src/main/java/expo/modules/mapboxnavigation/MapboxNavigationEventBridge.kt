package expo.modules.mapboxnavigation

object MapboxNavigationEventBridge {
  @Volatile
  private var emitter: ((String, Map<String, Any?>) -> Unit)? = null

  fun setEmitter(nextEmitter: (String, Map<String, Any?>) -> Unit) {
    emitter = nextEmitter
  }

  fun clearEmitter() {
    emitter = null
  }

  fun emit(eventName: String, payload: Map<String, Any?> = emptyMap()) {
    emitter?.invoke(eventName, payload)
  }
}

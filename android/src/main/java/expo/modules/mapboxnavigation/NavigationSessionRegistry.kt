package expo.modules.mapboxnavigation

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object NavigationSessionRegistry {
  private val lock = ReentrantLock()
  private var owner: String? = null
  private val stopHandlers = mutableMapOf<String, () -> Unit>()

  fun acquire(newOwner: String): Boolean {
    return lock.withLock {
      val current = owner
      if (current != null && current != newOwner) {
        return@withLock false
      }
      owner = newOwner
      true
    }
  }

  fun release(releasingOwner: String) {
    lock.withLock {
      if (owner == releasingOwner) {
        owner = null
      }
      stopHandlers.remove(releasingOwner)
    }
  }

  fun registerStopHandler(owner: String, handler: () -> Unit) {
    lock.withLock {
      stopHandlers[owner] = handler
    }
  }

  fun requestStopCurrent(): Boolean {
    val handler = lock.withLock {
      val current = owner ?: return false
      stopHandlers[current]
    } ?: return false
    handler.invoke()
    return true
  }
}

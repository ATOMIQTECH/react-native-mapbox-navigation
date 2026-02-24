package expo.modules.mapboxnavigation

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object NavigationSessionRegistry {
  private val lock = ReentrantLock()
  private var owner: String? = null

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
    }
  }
}

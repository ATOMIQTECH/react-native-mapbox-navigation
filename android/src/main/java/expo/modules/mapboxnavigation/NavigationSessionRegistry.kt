package expo.modules.mapboxnavigation

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object NavigationSessionRegistry {
  private val lock = ReentrantLock()
  private var owner: String? = null
  private val stopHandlers = mutableMapOf<String, () -> Unit>()
  private val resumeCameraHandlers = mutableMapOf<String, () -> Unit>()
  private val cameraFollowingProviders = mutableMapOf<String, () -> Boolean>()
  private val advanceLegHandlers = mutableMapOf<String, () -> Boolean>()

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
      resumeCameraHandlers.remove(releasingOwner)
      cameraFollowingProviders.remove(releasingOwner)
      advanceLegHandlers.remove(releasingOwner)
    }
  }

  fun registerStopHandler(owner: String, handler: () -> Unit) {
    lock.withLock {
      stopHandlers[owner] = handler
    }
  }

  fun registerResumeCameraFollowingHandler(owner: String, handler: () -> Unit) {
    lock.withLock {
      resumeCameraHandlers[owner] = handler
    }
  }

  fun registerCameraFollowingProvider(owner: String, provider: () -> Boolean) {
    lock.withLock {
      cameraFollowingProviders[owner] = provider
    }
  }

  fun registerAdvanceLegHandler(owner: String, handler: () -> Boolean) {
    lock.withLock {
      advanceLegHandlers[owner] = handler
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

  fun requestResumeCameraFollowingCurrent(): Boolean {
    val handler = lock.withLock {
      val current = owner ?: return false
      resumeCameraHandlers[current]
    } ?: return false
    handler.invoke()
    return true
  }

  fun requestAdvanceLegCurrent(): Boolean {
    val handler = lock.withLock {
      val current = owner ?: return false
      advanceLegHandlers[current]
    } ?: return false
    return handler.invoke()
  }

  /**
   * Whether an embedded navigation session currently holds the registry.
   *
   * Backs `getNavigationSettings().isNavigating`, which previously reported a
   * hardcoded `false`.
   */
  fun isSessionActive(): Boolean = lock.withLock { owner != null }

  fun isCurrentCameraFollowing(): Boolean {
    val provider = lock.withLock {
      val current = owner ?: return true
      cameraFollowingProviders[current]
    } ?: return true
    return provider.invoke()
  }
}

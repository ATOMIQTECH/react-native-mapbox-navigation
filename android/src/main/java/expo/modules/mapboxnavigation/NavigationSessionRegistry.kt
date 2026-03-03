package expo.modules.mapboxnavigation

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object NavigationSessionRegistry {
  private val lock = ReentrantLock()
  private var owner: String? = null
  private val stopHandlers = mutableMapOf<String, () -> Unit>()
  private val resumeCameraHandlers = mutableMapOf<String, () -> Unit>()
  private val cameraFollowingProviders = mutableMapOf<String, () -> Boolean>()

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

  fun isCurrentCameraFollowing(): Boolean {
    val provider = lock.withLock {
      val current = owner ?: return true
      cameraFollowingProviders[current]
    } ?: return true
    return provider.invoke()
  }
}

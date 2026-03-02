package expo.modules.mapboxnavigation

import android.util.Log
import com.mapbox.navigation.core.MapboxNavigationProvider

internal object MapboxAudioGuidanceController {
  private const val TAG = "MapboxAudioGuidance"

  @Volatile
  private var audioGuidance: Any? = null

  @Volatile
  private var attached: Boolean = false

  @Volatile
  private var warnedUnavailable: Boolean = false

  private fun ensureAttached(): Any? {
    if (!MapboxNavigationProvider.isCreated()) {
      return null
    }
    val navigation = runCatching { MapboxNavigationProvider.retrieve() }
      .getOrElse { throwable ->
        Log.w(TAG, "Failed to retrieve MapboxNavigation", throwable)
        return null
      }

    val guidance = audioGuidance ?: runCatching {
      val clazz = Class.forName("com.mapbox.navigation.ui.voice.api.MapboxAudioGuidance")
      clazz.getDeclaredConstructor().newInstance()
    }.getOrElse { throwable ->
      if (!warnedUnavailable) {
        warnedUnavailable = true
        Log.w(TAG, "MapboxAudioGuidance is not available in this build; mute/volume/language will be no-op.", throwable)
      }
      return null
    }.also { audioGuidance = it }

    if (!attached) {
      runCatching {
        val method = guidance.javaClass.methods.firstOrNull { it.name == "onAttached" && it.parameterTypes.size == 1 }
        method?.invoke(guidance, navigation)
      }
        .onFailure { throwable ->
          Log.w(TAG, "Failed to attach MapboxAudioGuidance", throwable)
          return null
        }
      attached = true
    }
    return guidance
  }

  fun setMuted(muted: Boolean) {
    val guidance = ensureAttached() ?: return
    runCatching {
      val methodName = if (muted) "mute" else "unmute"
      guidance.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }?.invoke(guidance)
    }.onFailure { throwable ->
      Log.w(TAG, "setMuted($muted) failed", throwable)
    }
  }

  fun setVoiceVolume(volume: Double) {
    val guidance = ensureAttached() ?: return
    val level = volume.toFloat().coerceIn(0f, 1f)
    runCatching {
      val getPlayer = guidance.javaClass.methods.firstOrNull { it.name == "getCurrentVoiceInstructionsPlayer" && it.parameterTypes.isEmpty() }
      val player = getPlayer?.invoke(guidance) ?: return@runCatching
      val speechVolume = runCatching {
        val clazz = Class.forName("com.mapbox.navigation.ui.voice.model.SpeechVolume")
        clazz.getDeclaredConstructor(Float::class.javaPrimitiveType).newInstance(level)
      }.getOrNull()
      val volumeMethod = player.javaClass.methods.firstOrNull { it.name == "volume" && it.parameterTypes.size == 1 }
      if (speechVolume != null) {
        volumeMethod?.invoke(player, speechVolume)
      }
    }.onFailure { throwable ->
      Log.w(TAG, "setVoiceVolume($volume) failed", throwable)
    }
  }

  fun setLanguage(language: String) {
    val guidance = ensureAttached() ?: return
    val trimmed = language.trim()
    if (trimmed.isEmpty()) return
    runCatching {
      val getPlayer = guidance.javaClass.methods.firstOrNull { it.name == "getCurrentVoiceInstructionsPlayer" && it.parameterTypes.isEmpty() }
      val player = getPlayer?.invoke(guidance) ?: return@runCatching
      val updateLang = player.javaClass.methods.firstOrNull { it.name == "updateLanguage" && it.parameterTypes.size == 1 }
      updateLang?.invoke(player, trimmed)
    }.onFailure { throwable ->
      Log.w(TAG, "setLanguage($language) failed", throwable)
    }
  }
}

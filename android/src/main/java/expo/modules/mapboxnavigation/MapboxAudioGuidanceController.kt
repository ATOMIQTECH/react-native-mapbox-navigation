package expo.modules.mapboxnavigation

import android.util.Log
import com.mapbox.navigation.ui.voice.api.MapboxAudioGuidance
import com.mapbox.navigation.ui.voice.model.SpeechVolume

internal object MapboxAudioGuidanceController {
  private const val TAG = "MapboxAudioGuidance"

  @Volatile
  private var warnedUnavailable: Boolean = false

  private fun getAudioGuidance(): MapboxAudioGuidance? {
    return runCatching { MapboxAudioGuidance.getRegisteredInstance() }
      .getOrElse { throwable ->
        if (!warnedUnavailable) {
          warnedUnavailable = true
          Log.w(
            TAG,
            "MapboxAudioGuidance is not registered yet; mute/volume/language will be applied once Drop-In attaches.",
            throwable
          )
        }
        return null
      }
  }

  fun setMuted(muted: Boolean) {
    val guidance = getAudioGuidance() ?: return
    runCatching {
      if (muted) guidance.mute() else guidance.unmute()
    }.onFailure { throwable ->
      Log.w(TAG, "setMuted($muted) failed", throwable)
    }
  }

  fun setVoiceVolume(volume: Double) {
    val guidance = getAudioGuidance() ?: return
    val level = volume.toFloat().coerceIn(0f, 1f)
    runCatching {
      val player = guidance.getCurrentVoiceInstructionsPlayer() ?: return@runCatching
      player.volume(SpeechVolume(level))
    }.onFailure { throwable: Throwable ->
      Log.w(TAG, "setVoiceVolume($volume) failed", throwable)
    }
  }

  fun setLanguage(language: String) {
    val guidance = getAudioGuidance() ?: return
    val trimmed = language.trim()
    if (trimmed.isEmpty()) return
    runCatching {
      val player = guidance.getCurrentVoiceInstructionsPlayer() ?: return@runCatching
      player.updateLanguage(trimmed)
    }.onFailure { throwable: Throwable ->
      Log.w(TAG, "setLanguage($language) failed", throwable)
    }
  }
}

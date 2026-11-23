package expo.modules.mapboxnavigation

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.mapbox.navigation.voice.api.MapboxAudioGuidance
import com.mapbox.navigation.voice.model.SpeechVolume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object MapboxAudioGuidanceController {
  private const val TAG = "MapboxAudioGuidance"
  private const val RESTORE_WINDOW_MS = 5_000L

  private val mainHandler = Handler(Looper.getMainLooper())

  @Volatile
  private var warnedUnavailable: Boolean = false

  @Volatile private var desiredMuted: Boolean? = null
  @Volatile private var desiredVolume: Double? = null
  @Volatile private var desiredLanguage: String? = null

  private var scope: CoroutineScope? = null
  private var enforceJob: Job? = null
  private var boundGuidance: MapboxAudioGuidance? = null
  private var enforceAlways = true
  private var enforceUntilMs = 0L

  private fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
  }

  private fun getAudioGuidance(): MapboxAudioGuidance? {
    return runCatching { MapboxAudioGuidance.getRegisteredInstance() }
      .getOrElse { throwable ->
        if (!warnedUnavailable) {
          warnedUnavailable = true
          Log.w(
            TAG,
            "MapboxAudioGuidance is not registered yet; mute/volume/language will be applied once navigation attaches.",
            throwable
          )
        }
        return null
      }
  }

  fun setMuted(muted: Boolean) {
    desiredMuted = muted
    onMain { applyMuted() }
  }

  fun setVoiceVolume(volume: Double) {
    desiredVolume = volume
    onMain { applyVolume() }
  }

  fun setLanguage(language: String) {
    val trimmed = language.trim()
    if (trimmed.isEmpty()) return
    desiredLanguage = trimmed
    onMain { applyLanguage() }
  }

  fun reapply() {
    onMain {
      applyMuted()
      applyVolume()
      applyLanguage()
    }
  }

  fun bind(userCanToggle: Boolean) {
    onMain {
      enforceAlways = !userCanToggle
      enforceUntilMs = SystemClock.elapsedRealtime() + RESTORE_WINDOW_MS
      val guidance = getAudioGuidance() ?: return@onMain
      if (guidance === boundGuidance && enforceJob?.isActive == true) {
        applyMuted()
        return@onMain
      }
      enforceJob?.cancel()
      val activeScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
      boundGuidance = guidance
      enforceJob = activeScope.launch {
        guidance.stateFlow().collect { state ->
          val desired = desiredMuted ?: return@collect
          if (state.isMuted == desired) return@collect
          if (!enforceAlways && SystemClock.elapsedRealtime() > enforceUntilMs) return@collect
          runCatching { if (desired) guidance.mute() else guidance.unmute() }
            .onFailure { Log.w(TAG, "Could not restore mute=$desired", it) }
        }
      }
      applyMuted()
    }
  }

  fun setUserCanToggle(userCanToggle: Boolean) {
    onMain { enforceAlways = !userCanToggle }
  }

  fun unbind() {
    onMain {
      enforceJob?.cancel()
      enforceJob = null
      boundGuidance = null
    }
  }

  private fun applyMuted() {
    val muted = desiredMuted ?: return
    val guidance = getAudioGuidance() ?: return
    runCatching {
      if (muted) guidance.mute() else guidance.unmute()
    }.onFailure { throwable ->
      Log.w(TAG, "setMuted($muted) failed", throwable)
    }
  }

  private fun applyVolume() {
    val volume = desiredVolume ?: return
    val guidance = getAudioGuidance() ?: return
    val level = volume.toFloat().coerceIn(0f, 1f)
    runCatching {
      val player = guidance.getCurrentVoiceInstructionsPlayer() ?: return@runCatching
      player.volume(SpeechVolume(level))
    }.onFailure { throwable: Throwable ->
      Log.w(TAG, "setVoiceVolume($volume) failed", throwable)
    }
  }

  private fun applyLanguage() {
    val language = desiredLanguage ?: return
    val guidance = getAudioGuidance() ?: return
    runCatching {
      val player = guidance.getCurrentVoiceInstructionsPlayer() ?: return@runCatching
      player.updateLanguage(language)
    }.onFailure { throwable: Throwable ->
      Log.w(TAG, "setLanguage($language) failed", throwable)
    }
  }
}

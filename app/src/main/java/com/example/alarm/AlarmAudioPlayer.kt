package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.example.model.BuiltInSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmAudioPlayer(private val context: Context) {
  private var mediaPlayer: MediaPlayer? = null
  private var rampJob: Job? = null

  fun start(
    soundId: String,
    soundUriString: String?,
    rampSeconds: Int,
    maxVolumePercent: Int,
    scope: CoroutineScope,
    onRampProgress: ((Float) -> Unit)? = null,
  ) {
    stop()
    val targetVolume = (maxVolumePercent.coerceIn(10, 100) / 100f)
    val startVolume = if (rampSeconds > 0) 0.05f else targetVolume

    val player = createPlayer(soundId, soundUriString) ?: return
    mediaPlayer = player
    player.isLooping = true
    player.setVolume(startVolume, startVolume)
    player.start()

    if (rampSeconds > 0) {
      rampJob =
        scope.launch {
          val steps = (rampSeconds * 2).coerceAtLeast(2)
          val stepDelayMs = 500L
          for (step in 1..steps) {
            if (!isActive) break
            val progress = step.toFloat() / steps.toFloat()
            val eased = progress * progress
            val vol = startVolume + (targetVolume - startVolume) * eased
            try {
              mediaPlayer?.setVolume(vol, vol)
            } catch (_: Exception) {}
            onRampProgress?.invoke(progress)
            delay(stepDelayMs)
          }
          onRampProgress?.invoke(1f)
        }
    } else {
      onRampProgress?.invoke(1f)
    }
  }

  fun preview(soundId: String, soundUriString: String?, maxVolumePercent: Int, scope: CoroutineScope, onStopped: () -> Unit) {
    stop()
    val player = createPlayer(soundId, soundUriString) ?: run {
      onStopped()
      return
    }
    val vol = (maxVolumePercent.coerceIn(10, 100) / 100f)
    mediaPlayer = player
    player.isLooping = false
    player.setVolume(vol, vol)
    player.setOnCompletionListener {
      stop()
      onStopped()
    }
    player.start()
    rampJob =
      scope.launch {
        delay(5000L)
        stop()
        onStopped()
      }
  }

  fun stop() {
    rampJob?.cancel()
    rampJob = null
    try {
      mediaPlayer?.let { mp ->
        if (mp.isPlaying) mp.stop()
        mp.reset()
        mp.release()
      }
    } catch (e: Exception) {
      Log.w("AlarmAudioPlayer", "Error releasing MediaPlayer: ${e.message}")
    } finally {
      mediaPlayer = null
    }
  }

  private fun createPlayer(soundId: String, soundUriString: String?): MediaPlayer? {
    val audioAttrs =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    val builtIn = BuiltInSound.fromId(soundId)
    if (builtIn.rawResId != null) {
      return try {
        val afd = context.resources.openRawResourceFd(builtIn.rawResId)
        MediaPlayer().apply {
          setAudioAttributes(audioAttrs)
          setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          afd.close()
          prepare()
        }
      } catch (e: Exception) {
        Log.e("AlarmAudioPlayer", "Failed to load raw sound $soundId", e)
        null
      }
    }

    val uri =
      soundUriString?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    return try {
      MediaPlayer().apply {
        setAudioAttributes(audioAttrs)
        setDataSource(context, uri)
        prepare()
      }
    } catch (e: Exception) {
      Log.w("AlarmAudioPlayer", "Custom URI playback failed, falling back to Copper Bell: ${e.message}")
      try {
        val afd = context.resources.openRawResourceFd(BuiltInSound.COPPER_BELL.rawResId!!)
        MediaPlayer().apply {
          setAudioAttributes(audioAttrs)
          setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          afd.close()
          prepare()
        }
      } catch (_: Exception) {
        null
      }
    }
  }
}

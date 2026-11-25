package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.model.AlarmProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var audioPlayer: AlarmAudioPlayer
  private var vibrator: Vibrator? = null
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onCreate() {
    super.onCreate()
    audioPlayer = AlarmAudioPlayer(applicationContext)
    createChannelIfNeeded()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        stopAlarmPlayback()
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_START, null -> {
        val alarmId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
        val label = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty().ifBlank { "Rust Alarm" }
        val snoozeCount = intent?.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0) ?: 0
        val isPreview = intent?.getBooleanExtra(AlarmScheduler.EXTRA_IS_PREVIEW, false) ?: false

        val alarm = AlarmRuntimeStore.findAlarmById(this, alarmId) ?: AlarmProfile(id = alarmId, label = label)
        _activeState.value =
          ActiveAlarmRuntime(
            alarmId = alarmId,
            label = alarm.label.ifBlank { label },
            snoozeCount = snoozeCount,
            rampProgress = if (alarm.rampSeconds > 0L) 0f else 1f,
            sunriseEnabled = alarm.sunriseEnabled,
            isPreview = isPreview,
          )

        acquireWakeLock()
        val notification = buildNotification(alarmId, alarm.label.ifBlank { label }, snoozeCount, isPreview)
        startForeground(NOTIFICATION_ID, notification)

        audioPlayer.start(
          soundId = alarm.soundId,
          soundUriString = alarm.soundUri,
          rampSeconds = alarm.rampSeconds.toInt(),
          maxVolumePercent = alarm.maxVolumePercent.toInt(),
          scope = serviceScope,
          onRampProgress = { progress ->
            _activeState.value = _activeState.value?.copy(rampProgress = progress)
          },
        )

        if (alarm.vibrate) {
          startVibration()
        }

        // Launch full-screen wake UI immediately when active
        val wakeIntent =
          Intent(this, WakeAlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarm.label.ifBlank { label })
            putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
            putExtra(AlarmScheduler.EXTRA_IS_PREVIEW, isPreview)
          }
        runCatching { startActivity(wakeIntent) }
      }
    }
    return START_NOT_STICKY
  }

  private fun buildNotification(
    alarmId: String,
    label: String,
    snoozeCount: Int,
    isPreview: Boolean,
  ): Notification {
    val fullScreenIntent =
      Intent(this, WakeAlarmActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
        putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
        putExtra(AlarmScheduler.EXTRA_IS_PREVIEW, isPreview)
      }
    val fullScreenPi =
      PendingIntent.getActivity(
        this,
        alarmId.hashCode(),
        fullScreenIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val dismissIntent =
      Intent(this, AlarmReceiver::class.java).apply {
        action = AlarmReceiver.ACTION_DISMISS_NOTIF
        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
        putExtra(AlarmScheduler.EXTRA_IS_PREVIEW, isPreview)
      }
    val dismissPi =
      PendingIntent.getBroadcast(
        this,
        alarmId.hashCode() + 101,
        dismissIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val snoozeIntent =
      Intent(this, AlarmReceiver::class.java).apply {
        action = AlarmReceiver.ACTION_SNOOZE_NOTIF
        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
        putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
        putExtra(AlarmScheduler.EXTRA_IS_PREVIEW, isPreview)
      }
    val snoozePi =
      PendingIntent.getBroadcast(
        this,
        alarmId.hashCode() + 202,
        snoozeIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val builder =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(label)
        .setContentText(
          if (snoozeCount > 0) "Snooze cycle #$snoozeCount — Tap to wake"
          else "Tap to open sunrise wake controls"
        )
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setFullScreenIntent(fullScreenPi, true)
        .setContentIntent(fullScreenPi)
        .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPi)

    val alarm = AlarmRuntimeStore.findAlarmById(this, alarmId)
    val nextSnooze = alarm?.nextSnoozeMinutes(snoozeCount)
    if (nextSnooze != null) {
      builder.addAction(android.R.drawable.ic_popup_sync, "Snooze ${nextSnooze}m", snoozePi)
    }

    return builder.build()
  }

  private fun createChannelIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(NotificationManager::class.java) ?: return
      val existing = nm.getNotificationChannel(CHANNEL_ID)
      if (existing == null) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = getString(R.string.alarm_channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
          }
        nm.createNotificationChannel(channel)
      }
    }
  }

  private fun startVibration() {
    val vib =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
    vibrator = vib
    val pattern = longArrayOf(0L, 450L, 350L, 450L, 900L)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vib?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    } else {
      @Suppress("DEPRECATION")
      vib?.vibrate(pattern, 0)
    }
  }

  private fun acquireWakeLock() {
    val pm = getSystemService(PowerManager::class.java) ?: return
    if (wakeLock?.isHeld == true) return
    wakeLock =
      pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rustalarm:AlarmServiceWakeLock").apply {
        acquire(5 * 60 * 1000L)
      }
  }

  private fun stopAlarmPlayback() {
    audioPlayer.stop()
    vibrator?.cancel()
    _activeState.value = null
    if (wakeLock?.isHeld == true) {
      runCatching { wakeLock?.release() }
    }
  }

  override fun onDestroy() {
    stopAlarmPlayback()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  data class ActiveAlarmRuntime(
    val alarmId: String,
    val label: String,
    val snoozeCount: Int,
    val rampProgress: Float,
    val sunriseEnabled: Boolean,
    val isPreview: Boolean,
  )

  companion object {
    const val ACTION_START = "com.example.alarm.ACTION_START"
    const val ACTION_STOP = "com.example.alarm.ACTION_STOP"
    private const val CHANNEL_ID = "rust_alarm_wake_channel"
    private const val NOTIFICATION_ID = 4091

    private val _activeState = MutableStateFlow<ActiveAlarmRuntime?>(null)
    val activeState: StateFlow<ActiveAlarmRuntime?> = _activeState.asStateFlow()

    fun stopService(context: Context) {
      context.stopService(Intent(context, AlarmService::class.java))
    }
  }
}

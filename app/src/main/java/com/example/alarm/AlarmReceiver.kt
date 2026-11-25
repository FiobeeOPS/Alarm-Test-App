package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
        AlarmScheduler.rescheduleFromLocalSnapshot(context)
      }
      ACTION_ALARM_TRIGGER -> {
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty()
        val snoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)

        // Reschedule next recurring day if this is a repeating alarm (on initial trigger)
        if (snoozeCount == 0 && alarmId.isNotBlank()) {
          val alarm = AlarmRuntimeStore.findAlarmById(context, alarmId)
          if (alarm != null && alarm.enabled && alarm.repeatDays.isNotEmpty()) {
            AlarmScheduler.scheduleAlarm(context, alarm)
          }
        }

        val serviceIntent =
          Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
            putExtra(AlarmScheduler.EXTRA_IS_PREVIEW, false)
          }
        ContextCompat.startForegroundService(context, serviceIntent)
      }
      ACTION_SNOOZE_NOTIF -> {
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty()
        val snoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
        val isPreview = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_PREVIEW, false)
        AlarmService.stopService(context)
        if (!isPreview && alarmId.isNotBlank()) {
          val alarm = AlarmRuntimeStore.findAlarmById(context, alarmId)
          val nextDelay = alarm?.nextSnoozeMinutes(snoozeCount)
          if (nextDelay != null) {
            val nextCount = snoozeCount + 1
            AlarmRuntimeStore.recordSnoozeUsed(context, nextCount)
            AlarmScheduler.scheduleSnooze(context, alarmId, label, nextDelay, nextCount)
          }
        }
      }
      ACTION_DISMISS_NOTIF -> {
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID).orEmpty()
        val snoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
        val isPreview = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_PREVIEW, false)
        AlarmService.stopService(context)
        if (!isPreview && alarmId.isNotBlank()) {
          val alarm = AlarmRuntimeStore.findAlarmById(context, alarmId)
          AlarmRuntimeStore.recordDismissedContext(context, alarmId, alarm?.linkedRoutineId, snoozeCount)
        }
      }
    }
  }

  companion object {
    const val ACTION_ALARM_TRIGGER = "com.example.alarm.ACTION_ALARM_TRIGGER"
    const val ACTION_SNOOZE_NOTIF = "com.example.alarm.ACTION_SNOOZE_NOTIF"
    const val ACTION_DISMISS_NOTIF = "com.example.alarm.ACTION_DISMISS_NOTIF"
  }
}

package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.model.AlarmProfile
import java.util.Calendar

object AlarmScheduler {
  const val EXTRA_ALARM_ID = "extra_alarm_id"
  const val EXTRA_ALARM_LABEL = "extra_alarm_label"
  const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"
  const val EXTRA_IS_PREVIEW = "extra_is_preview"

  fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(AlarmManager::class.java)
    return am?.canScheduleExactAlarms() ?: false
  }

  fun syncAlarms(context: Context, alarms: List<AlarmProfile>) {
    val previous = AlarmRuntimeStore.loadAlarmsSnapshot(context)
    previous.forEach { old ->
      if (alarms.none { it.id == old.id && it.enabled }) {
        cancelAlarm(context, old.id)
      }
    }
    AlarmRuntimeStore.saveAlarmsSnapshot(context, alarms)
    alarms.forEach { alarm ->
      if (alarm.enabled) {
        scheduleAlarm(context, alarm)
      } else {
        cancelAlarm(context, alarm.id)
      }
    }
  }

  fun rescheduleFromLocalSnapshot(context: Context) {
    val cached = AlarmRuntimeStore.loadAlarmsSnapshot(context)
    cached.filter { it.enabled }.forEach { scheduleAlarm(context, it) }
  }

  fun cancelAllCachedAlarms(context: Context) {
    val cached = AlarmRuntimeStore.loadAlarmsSnapshot(context)
    cached.forEach { cancelAlarm(context, it.id) }
    AlarmRuntimeStore.clearAllOnSignOut(context)
  }

  fun scheduleAlarm(context: Context, alarm: AlarmProfile) {
    if (alarm.id.isBlank()) return
    val triggerAtMillis = computeNextTriggerMillis(alarm)
    scheduleAtEpochMillis(
      context = context,
      alarmId = alarm.id,
      label = alarm.label,
      triggerAtMillis = triggerAtMillis,
      snoozeCount = 0,
    )
  }

  fun scheduleSnooze(context: Context, alarmId: String, label: String, delayMinutes: Int, nextSnoozeCount: Int) {
    val triggerAtMillis = System.currentTimeMillis() + delayMinutes.coerceIn(1, 60) * 60_000L
    scheduleAtEpochMillis(
      context = context,
      alarmId = alarmId,
      label = label,
      triggerAtMillis = triggerAtMillis,
      snoozeCount = nextSnoozeCount,
    )
  }

  fun cancelAlarm(context: Context, alarmId: String) {
    if (alarmId.isBlank()) return
    val am = context.getSystemService(AlarmManager::class.java) ?: return
    val intent = Intent(context, AlarmReceiver::class.java).apply {
      action = AlarmReceiver.ACTION_ALARM_TRIGGER
    }
    val pi =
      PendingIntent.getBroadcast(
        context,
        alarmId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    am.cancel(pi)
  }

  private fun scheduleAtEpochMillis(
    context: Context,
    alarmId: String,
    label: String,
    triggerAtMillis: Long,
    snoozeCount: Int,
  ) {
    val am = context.getSystemService(AlarmManager::class.java) ?: return
    val triggerIntent =
      Intent(context, AlarmReceiver::class.java).apply {
        action = AlarmReceiver.ACTION_ALARM_TRIGGER
        putExtra(EXTRA_ALARM_ID, alarmId)
        putExtra(EXTRA_ALARM_LABEL, label)
        putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
      }
    val pendingTrigger =
      PendingIntent.getBroadcast(
        context,
        alarmId.hashCode(),
        triggerIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val showIntent =
      PendingIntent.getActivity(
        context,
        alarmId.hashCode(),
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    if (canScheduleExactAlarms(context)) {
      am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), pendingTrigger)
    } else {
      am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingTrigger)
    }
  }

  fun computeNextTriggerMillis(alarm: AlarmProfile, nowMillis: Long = System.currentTimeMillis()): Long {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val base =
      Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, alarm.hour.toInt().coerceIn(0, 23))
        set(Calendar.MINUTE, alarm.minute.toInt().coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }

    if (alarm.repeatDays.isEmpty()) {
      if (base.timeInMillis <= now.timeInMillis) {
        base.add(Calendar.DAY_OF_YEAR, 1)
      }
      return base.timeInMillis
    }

    val targetDays = alarm.repeatDays.map { it.toInt() }.toSet()
    for (offset in 0..7) {
      val candidate = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
      val isoDay = calendarDayToIso(candidate.get(Calendar.DAY_OF_WEEK))
      if (isoDay in targetDays && candidate.timeInMillis > now.timeInMillis) {
        return candidate.timeInMillis
      }
    }
    base.add(Calendar.DAY_OF_YEAR, 1)
    return base.timeInMillis
  }

  private fun calendarDayToIso(calendarDay: Int): Int =
    when (calendarDay) {
      Calendar.MONDAY -> 1
      Calendar.TUESDAY -> 2
      Calendar.WEDNESDAY -> 3
      Calendar.THURSDAY -> 4
      Calendar.FRIDAY -> 5
      Calendar.SATURDAY -> 6
      Calendar.SUNDAY -> 7
      else -> 1
    }
}

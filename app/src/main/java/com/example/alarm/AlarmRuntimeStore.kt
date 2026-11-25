package com.example.alarm

import android.content.Context
import com.example.model.AlarmProfile
import com.example.model.SnoozeMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight local snapshot store so BOOT_COMPLETED and cold-started AlarmReceiver/WakeAlarmActivity
 * can reschedule alarms and track snooze cycle counts immediately without waiting on network.
 */
object AlarmRuntimeStore {
  private const val PREFS_NAME = "rust_alarm_runtime_prefs"
  private const val KEY_ALARMS_JSON = "cached_alarms_json"
  private const val KEY_LAST_MORNING_SNOOZES = "last_morning_snoozes"
  private const val KEY_LAST_DISMISSED_ALARM_ID = "last_dismissed_alarm_id"
  private const val KEY_LAST_DISMISSED_ROUTINE_ID = "last_dismissed_routine_id"

  fun saveAlarmsSnapshot(context: Context, alarms: List<AlarmProfile>) {
    val arr = JSONArray()
    alarms.forEach { alarm ->
      val obj =
        JSONObject().apply {
          put("id", alarm.id)
          put("label", alarm.label)
          put("hour", alarm.hour)
          put("minute", alarm.minute)
          put("repeatDays", JSONArray(alarm.repeatDays))
          put("enabled", alarm.enabled)
          put("vibrate", alarm.vibrate)
          put("soundId", alarm.soundId)
          put("soundTitle", alarm.soundTitle)
          put("soundUri", alarm.soundUri ?: "")
          put("rampSeconds", alarm.rampSeconds)
          put("maxVolumePercent", alarm.maxVolumePercent)
          put("sunriseEnabled", alarm.sunriseEnabled)
          put("snoozeMode", alarm.snoozeMode)
          put("snoozeIntervalMinutes", alarm.snoozeIntervalMinutes)
          put("snoozeMaxCount", alarm.snoozeMaxCount)
          put("snoozeSequenceMinutes", JSONArray(alarm.snoozeSequenceMinutes))
          put("linkedRoutineId", alarm.linkedRoutineId ?: "")
        }
      arr.put(obj)
    }
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_ALARMS_JSON, arr.toString())
      .apply()
  }

  fun loadAlarmsSnapshot(context: Context): List<AlarmProfile> {
    val raw =
      context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_ALARMS_JSON, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.getJSONObject(i)
          val daysArr = obj.optJSONArray("repeatDays") ?: JSONArray()
          val days = buildList {
            for (d in 0 until daysArr.length()) add(daysArr.optLong(d))
          }
          val seqArr = obj.optJSONArray("snoozeSequenceMinutes") ?: JSONArray()
          val seq = buildList {
            for (s in 0 until seqArr.length()) add(seqArr.optLong(s))
          }
          add(
            AlarmProfile(
              id = obj.optString("id"),
              label = obj.optString("label"),
              hour = obj.optLong("hour", 7L),
              minute = obj.optLong("minute", 0L),
              repeatDays = days,
              enabled = obj.optBoolean("enabled", false),
              vibrate = obj.optBoolean("vibrate", true),
              soundId = obj.optString("soundId", "COPPER_BELL"),
              soundTitle = obj.optString("soundTitle", "Copper Bell"),
              soundUri = obj.optString("soundUri").takeIf { it.isNotBlank() },
              rampSeconds = obj.optLong("rampSeconds", 30L),
              maxVolumePercent = obj.optLong("maxVolumePercent", 85L),
              sunriseEnabled = obj.optBoolean("sunriseEnabled", true),
              snoozeMode = obj.optString("snoozeMode", SnoozeMode.DIMINISHING.name),
              snoozeIntervalMinutes = obj.optLong("snoozeIntervalMinutes", 9L),
              snoozeMaxCount = obj.optLong("snoozeMaxCount", 3L),
              snoozeSequenceMinutes = seq,
              linkedRoutineId = obj.optString("linkedRoutineId").takeIf { it.isNotBlank() },
            )
          )
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  fun findAlarmById(context: Context, alarmId: String): AlarmProfile? =
    loadAlarmsSnapshot(context).firstOrNull { it.id == alarmId }

  fun recordSnoozeUsed(context: Context, count: Int) {
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putInt(KEY_LAST_MORNING_SNOOZES, count.coerceAtLeast(0))
      .apply()
  }

  fun getLastMorningSnoozes(context: Context): Int =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_LAST_MORNING_SNOOZES, 0)

  fun recordDismissedContext(context: Context, alarmId: String, routineId: String?, snoozeCount: Int) {
    context
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putInt(KEY_LAST_MORNING_SNOOZES, snoozeCount.coerceAtLeast(0))
      .putString(KEY_LAST_DISMISSED_ALARM_ID, alarmId)
      .putString(KEY_LAST_DISMISSED_ROUTINE_ID, routineId ?: "")
      .apply()
  }

  fun clearAllOnSignOut(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
  }
}

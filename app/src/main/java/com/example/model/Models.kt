package com.example.model

import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToInt

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

enum class SnoozeMode(val title: String, val subtitle: String) {
  FIXED("Fixed", "Same interval every repeat"),
  DIMINISHING("Diminishing", "Progressively shorter nudges"),
  CUSTOM("Custom", "Explicit sequence per snooze"),
  ;

  companion object {
    fun safeValueOf(raw: String): SnoozeMode =
      entries.firstOrNull { it.name == raw } ?: FIXED
  }
}

enum class WakeMood(val label: String) {
  REFRESHED("Refreshed"),
  STEADY("Steady"),
  GROGGY("Groggy"),
  TIRED("Tired"),
  ;

  companion object {
    fun safeValueOf(raw: String): WakeMood =
      entries.firstOrNull { it.name == raw } ?: STEADY
  }
}

enum class BuiltInSound(
  val id: String,
  val title: String,
  val rawResId: Int?,
  val description: String,
) {
  COPPER_BELL("COPPER_BELL", "Copper Bell", R.raw.chime_copper_bell, "Warm resonant 3-note chime"),
  SUNRISE_CHORD("SUNRISE_CHORD", "Sunrise Chord", R.raw.chime_sunrise_chord, "Soft swelling harmonic pad"),
  MECHANICAL_PULSE("MECHANICAL_PULSE", "Mechanical Pulse", R.raw.chime_mechanical_pulse, "Crisp dual-tone industrial tap"),
  FOUNDRY_GONG("FOUNDRY_GONG", "Foundry Gong", R.raw.chime_foundry_gong, "Deep grounding bronze resonance"),
  SYSTEM_RINGTONE("SYSTEM_RINGTONE", "System Alarm Tone", null, "Selected Android system alarm"),
  CUSTOM_AUDIO("CUSTOM_AUDIO", "Custom Audio File", null, "User audio via Document Picker"),
  ;

  companion object {
    fun fromId(id: String): BuiltInSound =
      entries.firstOrNull { it.id == id } ?: COPPER_BELL
  }
}

@Keep
@IgnoreExtraProperties
data class UserSettings(
  @DocumentId val id: String = "settings",
  val userId: String = "",
  val targetSleepMinutes: Long = 480L,
  val defaultSnoozeMinutes: Long = 9L,
  val defaultSnoozeMaxCount: Long = 3L,
  val defaultRampSeconds: Long = 30L,
  val sunriseGlowEnabled: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AlarmProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val label: String = "",
  val hour: Long = 7L,
  val minute: Long = 0L,
  val repeatDays: List<Long> = emptyList(),
  val enabled: Boolean = true,
  val vibrate: Boolean = true,
  val soundId: String = BuiltInSound.COPPER_BELL.id,
  val soundTitle: String = BuiltInSound.COPPER_BELL.title,
  val soundUri: String? = null,
  val rampSeconds: Long = 30L,
  val maxVolumePercent: Long = 85L,
  val sunriseEnabled: Boolean = true,
  val snoozeMode: String = SnoozeMode.DIMINISHING.name,
  val snoozeIntervalMinutes: Long = 9L,
  val snoozeMaxCount: Long = 3L,
  val snoozeSequenceMinutes: List<Long> = listOf(9L, 6L, 3L),
  val linkedRoutineId: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedSnoozeSequence(): List<Int> {
    val maxCount = snoozeMaxCount.toInt().coerceIn(0, 10)
    if (maxCount == 0) return emptyList()
    val base = snoozeIntervalMinutes.toInt().coerceIn(1, 30)
    return when (SnoozeMode.safeValueOf(snoozeMode)) {
      SnoozeMode.FIXED -> List(maxCount) { base }
      SnoozeMode.DIMINISHING -> {
        List(maxCount) { idx ->
          val step = (base - idx * 3).coerceAtLeast(2)
          step
        }
      }
      SnoozeMode.CUSTOM -> {
        if (snoozeSequenceMinutes.isEmpty()) {
          List(maxCount) { base }
        } else {
          List(maxCount) { idx ->
            val picked = snoozeSequenceMinutes.getOrElse(idx) { snoozeSequenceMinutes.last() }
            picked.toInt().coerceIn(1, 30)
          }
        }
      }
    }
  }

  fun nextSnoozeMinutes(currentSnoozeIndex: Int): Int? {
    val seq = resolvedSnoozeSequence()
    return seq.getOrNull(currentSnoozeIndex)
  }

  fun snoozeSummary(): String {
    val seq = resolvedSnoozeSequence()
    if (seq.isEmpty()) return "Snooze disabled"
    return when (SnoozeMode.safeValueOf(snoozeMode)) {
      SnoozeMode.FIXED -> "${seq.first()}m x${seq.size} fixed"
      SnoozeMode.DIMINISHING -> "${seq.joinToString("->") { "${it}m" }} nudge"
      SnoozeMode.CUSTOM -> "${seq.joinToString("->") { "${it}m" }} custom"
    }
  }
}

@Keep
@IgnoreExtraProperties
data class RoutineStep(
  val stepId: String = "",
  val title: String = "",
  val durationMinutes: Long = 5L,
  val iconKey: String = "check",
)

@Keep
@IgnoreExtraProperties
data class MorningRoutine(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val defaultRoutine: Boolean = false,
  val steps: List<RoutineStep> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun totalMinutes(): Int = steps.sumOf { it.durationMinutes.toInt().coerceAtLeast(1) }
}

@Keep
@IgnoreExtraProperties
data class RoutineRun(
  @DocumentId val id: String = "",
  val userId: String = "",
  val routineId: String = "",
  val routineTitle: String = "",
  val dateKey: String = "",
  val completedStepCount: Long = 0L,
  val totalStepCount: Long = 1L,
  val elapsedSeconds: Long = 0L,
  val completed: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class SleepLog(
  @DocumentId val id: String = "",
  val userId: String = "",
  val dateKey: String = "",
  val bedtimeEpochMs: Long = 0L,
  val wakeEpochMs: Long = 0L,
  val durationMinutes: Long = 480L,
  val qualityRating: Long = 4L,
  val snoozeCountUsed: Long = 0L,
  val energyScore: Long = 7L,
  val mood: String = WakeMood.STEADY.name,
  val readinessScore: Long = 80L,
  val tags: List<String> = emptyList(),
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class ReadinessBreakdown(
  val totalScore: Int,
  val durationPoints: Int,
  val qualityPoints: Int,
  val energyPoints: Int,
  val snoozePoints: Int,
)

object ReadinessCalculator {
  fun calculate(
    durationMinutes: Int,
    targetSleepMinutes: Int,
    qualityRating: Int,
    energyScore: Int,
    snoozeCountUsed: Int,
    mood: WakeMood,
  ): ReadinessBreakdown {
    val safeTarget = targetSleepMinutes.coerceIn(240, 720)
    val durationRatio = (durationMinutes.toFloat() / safeTarget.toFloat()).coerceIn(0f, 1.15f)
    val durationPoints =
      if (durationRatio <= 1f) {
        (durationRatio * 35f).roundToInt()
      } else {
        35 - ((durationRatio - 1f) * 20f).roundToInt().coerceAtMost(5)
      }.coerceIn(0, 35)

    val qualityPoints = ((qualityRating.coerceIn(1, 5) / 5f) * 25f).roundToInt()
    val moodAdjustment =
      when (mood) {
        WakeMood.REFRESHED -> 4
        WakeMood.STEADY -> 2
        WakeMood.GROGGY -> -2
        WakeMood.TIRED -> -4
      }
    val energyPoints = (((energyScore.coerceIn(1, 10) / 10f) * 21f).roundToInt() + moodAdjustment)
      .coerceIn(0, 25)
    val snoozePoints = (15 - snoozeCountUsed.coerceAtLeast(0) * 4).coerceIn(0, 15)
    val total = (durationPoints + qualityPoints + energyPoints + snoozePoints).coerceIn(0, 100)
    return ReadinessBreakdown(
      totalScore = total,
      durationPoints = durationPoints,
      qualityPoints = qualityPoints,
      energyPoints = energyPoints,
      snoozePoints = snoozePoints,
    )
  }
}

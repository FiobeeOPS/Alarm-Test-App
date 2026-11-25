package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlarmRepository
import com.example.data.WellnessRepository
import com.example.model.ReadinessCalculator
import com.example.model.RoutineRun
import com.example.model.SleepLog
import com.example.model.UiState
import com.example.model.UserSettings
import com.example.model.WakeMood
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class HealthMetricsSummary(
  val logs: List<SleepLog> = emptyList(),
  val routineRuns: List<RoutineRun> = emptyList(),
  val settings: UserSettings = UserSettings(),
  val latestReadiness: Int = 0,
  val avgSleepMinutes7d: Int = 0,
  val avgReadiness7d: Int = 0,
  val avgSnoozes7d: Float = 0f,
  val routineCompletionRate: Int = 0,
  val routineStreakDays: Int = 0,
)

class MetricsViewModel(
  private val wellnessRepository: WellnessRepository,
  private val alarmRepository: AlarmRepository,
  private val currentUserId: String,
) : ViewModel() {

  val summaryState: StateFlow<UiState<HealthMetricsSummary>> =
    combine(
      wellnessRepository.observeSleepLogs(currentUserId),
      wellnessRepository.observeRoutineRuns(currentUserId),
      alarmRepository.observeSettings(currentUserId),
    ) { logs, runs, settings ->
      val recent7 = logs.take(7)
      val avgSleep =
        if (recent7.isNotEmpty()) recent7.map { it.durationMinutes.toInt() }.average().roundToInt() else 0
      val avgReadiness =
        if (recent7.isNotEmpty()) recent7.map { it.readinessScore.toInt() }.average().roundToInt() else 0
      val avgSnoozes =
        if (recent7.isNotEmpty()) recent7.map { it.snoozeCountUsed.toFloat() }.average().toFloat() else 0f
      val completedRuns = runs.count { it.completed }
      val completionRate =
        if (runs.isNotEmpty()) ((completedRuns.toFloat() / runs.size.toFloat()) * 100f).roundToInt() else 0
      val streak = runs.filter { it.completed }.map { it.dateKey }.distinct().size

      UiState.Success(
        HealthMetricsSummary(
          logs = logs,
          routineRuns = runs,
          settings = settings,
          latestReadiness = logs.firstOrNull()?.readinessScore?.toInt() ?: 0,
          avgSleepMinutes7d = avgSleep,
          avgReadiness7d = avgReadiness,
          avgSnoozes7d = avgSnoozes,
          routineCompletionRate = completionRate,
          routineStreakDays = streak,
        )
      ) as UiState<HealthMetricsSummary>
    }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing health metrics", root)
        emit(UiState.Error(root.message ?: "Failed to load health metrics"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  fun saveSleepAndReadiness(
    existingId: String,
    dateKey: String,
    bedtimeEpochMs: Long,
    wakeEpochMs: Long,
    durationMinutes: Int,
    qualityRating: Int,
    snoozeCountUsed: Int,
    energyScore: Int,
    mood: WakeMood,
    tags: List<String>,
    notes: String,
    targetSleepMinutes: Int,
    onSaved: () -> Unit = {},
  ) {
    viewModelScope.launch {
      val readiness =
        ReadinessCalculator.calculate(
          durationMinutes = durationMinutes,
          targetSleepMinutes = targetSleepMinutes,
          qualityRating = qualityRating,
          energyScore = energyScore,
          snoozeCountUsed = snoozeCountUsed,
          mood = mood,
        )
      val log =
        SleepLog(
          id = existingId,
          userId = currentUserId,
          dateKey = dateKey,
          bedtimeEpochMs = bedtimeEpochMs,
          wakeEpochMs = wakeEpochMs,
          durationMinutes = durationMinutes.toLong(),
          qualityRating = qualityRating.toLong(),
          snoozeCountUsed = snoozeCountUsed.toLong(),
          energyScore = energyScore.toLong(),
          mood = mood.name,
          readinessScore = readiness.totalScore.toLong(),
          tags = tags,
          notes = notes,
        )
      val result = wellnessRepository.saveSleepLog(currentUserId, log)
      if (result.isSuccess) {
        _messages.tryEmit("Sleep & readiness logged (${readiness.totalScore}/100)")
        onSaved()
      } else {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save sleep log")
      }
    }
  }

  fun deleteSleepLog(logId: String) {
    viewModelScope.launch {
      val result = wellnessRepository.deleteSleepLog(currentUserId, logId)
      if (result.isSuccess) {
        _messages.tryEmit("Sleep log deleted")
      } else {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to delete log")
      }
    }
  }

  private companion object {
    const val TAG = "MetricsVM"
  }
}

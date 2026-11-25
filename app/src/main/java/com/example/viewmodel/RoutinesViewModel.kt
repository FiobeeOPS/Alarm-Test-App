package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.WellnessRepository
import com.example.model.MorningRoutine
import com.example.model.RoutineRun
import com.example.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActiveRoutineTimerState(
  val routine: MorningRoutine,
  val currentStepIndex: Int = 0,
  val remainingSeconds: Int = 0,
  val stepTotalSeconds: Int = 60,
  val totalElapsedSeconds: Int = 0,
  val isPaused: Boolean = false,
  val completedStepIndices: Set<Int> = emptySet(),
  val finished: Boolean = false,
)

class RoutinesViewModel(
  private val wellnessRepository: WellnessRepository,
  private val currentUserId: String,
) : ViewModel() {

  val routinesState: StateFlow<UiState<List<MorningRoutine>>> =
    wellnessRepository
      .observeRoutines(currentUserId)
      .map<List<MorningRoutine>, UiState<List<MorningRoutine>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing routines", root)
        emit(UiState.Error(root.message ?: "Failed to load routines"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val routineRunsState: StateFlow<List<RoutineRun>> =
    wellnessRepository
      .observeRoutineRuns(currentUserId)
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing routine runs", firestoreError ?: error)
        emit(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList(),
      )

  private val _activeTimer = MutableStateFlow<ActiveRoutineTimerState?>(null)
  val activeTimer: StateFlow<ActiveRoutineTimerState?> = _activeTimer.asStateFlow()

  private val _stepChimeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
  val stepChimeEvents: SharedFlow<Unit> = _stepChimeEvents.asSharedFlow()

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  private var tickerJob: Job? = null

  fun saveRoutine(routine: MorningRoutine, onSaved: () -> Unit = {}) {
    viewModelScope.launch {
      val result = wellnessRepository.saveRoutine(currentUserId, routine)
      if (result.isSuccess) {
        _messages.tryEmit("Morning routine saved")
        onSaved()
      } else {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save routine")
      }
    }
  }

  fun deleteRoutine(routineId: String) {
    viewModelScope.launch {
      val result = wellnessRepository.deleteRoutine(currentUserId, routineId)
      if (result.isSuccess) {
        _messages.tryEmit("Routine deleted")
      } else {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to delete routine")
      }
    }
  }

  fun createStarterRoutine() {
    viewModelScope.launch {
      val result = wellnessRepository.createStarterRoutine(currentUserId)
      if (result.isSuccess) {
        _messages.tryEmit("Foundry Morning Protocol ready")
      } else {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to create routine")
      }
    }
  }

  fun startRoutine(routine: MorningRoutine) {
    if (routine.steps.isEmpty()) {
      _messages.tryEmit("Add at least one step before starting this routine")
      return
    }
    val firstStepSeconds = (routine.steps.first().durationMinutes.toInt().coerceAtLeast(1)) * 60
    _activeTimer.value =
      ActiveRoutineTimerState(
        routine = routine,
        currentStepIndex = 0,
        remainingSeconds = firstStepSeconds,
        stepTotalSeconds = firstStepSeconds,
        totalElapsedSeconds = 0,
        isPaused = false,
      )
    startTicker()
  }

  fun startRoutineById(routineId: String) {
    val list = (routinesState.value as? UiState.Success)?.data.orEmpty()
    val match = list.firstOrNull { it.id == routineId } ?: list.firstOrNull()
    if (match != null) {
      startRoutine(match)
    }
  }

  fun togglePauseTimer() {
    val current = _activeTimer.value ?: return
    _activeTimer.value = current.copy(isPaused = !current.isPaused)
  }

  fun completeCurrentStep() {
    val current = _activeTimer.value ?: return
    val updatedCompleted = current.completedStepIndices + current.currentStepIndex
    _stepChimeEvents.tryEmit(Unit)
    advanceStep(current.copy(completedStepIndices = updatedCompleted))
  }

  fun skipCurrentStep() {
    val current = _activeTimer.value ?: return
    advanceStep(current)
  }

  fun cancelTimer(savePartialIfProgressed: Boolean = false) {
    val current = _activeTimer.value
    tickerJob?.cancel()
    tickerJob = null
    if (savePartialIfProgressed && current != null && current.completedStepIndices.isNotEmpty()) {
      persistRoutineRun(current, completedAll = false)
    }
    _activeTimer.value = null
  }

  private fun advanceStep(state: ActiveRoutineTimerState) {
    val nextIndex = state.currentStepIndex + 1
    if (nextIndex >= state.routine.steps.size) {
      tickerJob?.cancel()
      tickerJob = null
      val doneState = state.copy(finished = true, remainingSeconds = 0)
      _activeTimer.value = doneState
      persistRoutineRun(doneState, completedAll = state.completedStepIndices.size == state.routine.steps.size)
    } else {
      val nextSec = (state.routine.steps[nextIndex].durationMinutes.toInt().coerceAtLeast(1)) * 60
      _activeTimer.value =
        state.copy(
          currentStepIndex = nextIndex,
          remainingSeconds = nextSec,
          stepTotalSeconds = nextSec,
        )
    }
  }

  private fun startTicker() {
    tickerJob?.cancel()
    tickerJob =
      viewModelScope.launch {
        while (isActive) {
          delay(1000L)
          val current = _activeTimer.value ?: break
          if (!current.isPaused && !current.finished) {
            val nextRem = (current.remainingSeconds - 1).coerceAtLeast(0)
            val nextElapsed = current.totalElapsedSeconds + 1
            if (nextRem == 0 && current.remainingSeconds > 0) {
              _stepChimeEvents.tryEmit(Unit)
            }
            _activeTimer.value = current.copy(remainingSeconds = nextRem, totalElapsedSeconds = nextElapsed)
          }
        }
      }
  }

  private fun persistRoutineRun(state: ActiveRoutineTimerState, completedAll: Boolean) {
    viewModelScope.launch {
      val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
      val run =
        RoutineRun(
          userId = currentUserId,
          routineId = state.routine.id.ifBlank { "routine" },
          routineTitle = state.routine.title,
          dateKey = todayKey,
          completedStepCount = state.completedStepIndices.size.toLong(),
          totalStepCount = state.routine.steps.size.coerceAtLeast(1).toLong(),
          elapsedSeconds = state.totalElapsedSeconds.toLong(),
          completed = completedAll,
        )
      val result = wellnessRepository.logRoutineRun(currentUserId, run)
      if (result.isSuccess) {
        _messages.tryEmit("Morning routine logged")
      } else {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save routine log")
      }
    }
  }

  override fun onCleared() {
    tickerJob?.cancel()
    super.onCleared()
  }

  private companion object {
    const val TAG = "RoutinesVM"
  }
}

package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlarmRepository
import com.example.data.WellnessRepository
import com.example.model.AlarmProfile
import com.example.model.MorningRoutine
import com.example.model.SnoozeMode
import com.example.model.UiState
import com.example.model.UserSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(
  private val alarmRepository: AlarmRepository,
  private val wellnessRepository: WellnessRepository,
  private val currentUserId: String,
) : ViewModel() {

  val alarmsState: StateFlow<UiState<List<AlarmProfile>>> =
    alarmRepository
      .observeAlarms(currentUserId)
      .map<List<AlarmProfile>, UiState<List<AlarmProfile>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Error observing alarms", root)
        emit(UiState.Error(root.message ?: "Failed to load alarms"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
      )

  val routinesState: StateFlow<List<MorningRoutine>> =
    wellnessRepository
      .observeRoutines(currentUserId)
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing routines in AlarmsVM", firestoreError ?: error)
        emit(emptyList())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList(),
      )

  val settingsState: StateFlow<UserSettings> =
    alarmRepository
      .observeSettings(currentUserId)
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing settings", firestoreError ?: error)
        emit(UserSettings(userId = currentUserId))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UserSettings(userId = currentUserId),
      )

  private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val messages: SharedFlow<String> = _messages.asSharedFlow()

  fun saveAlarm(alarm: AlarmProfile, onSaved: () -> Unit = {}) {
    viewModelScope.launch {
      val result = alarmRepository.upsertAlarm(currentUserId, alarm)
      if (result.isSuccess) {
        _messages.tryEmit(if (alarm.id.isBlank()) "Alarm created & synced" else "Alarm updated")
        onSaved()
      } else {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save alarm")
      }
    }
  }

  fun toggleAlarm(alarmId: String, enabled: Boolean) {
    viewModelScope.launch {
      val result = alarmRepository.toggleAlarm(currentUserId, alarmId, enabled)
      if (result.isFailure) {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to update alarm")
      }
    }
  }

  fun deleteAlarm(alarmId: String) {
    viewModelScope.launch {
      val result = alarmRepository.deleteAlarm(currentUserId, alarmId)
      if (result.isSuccess) {
        _messages.tryEmit("Alarm deleted")
      } else {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to delete alarm")
      }
    }
  }

  fun createStarterAlarm() {
    val settings = settingsState.value
    val base = settings.defaultSnoozeMinutes.toInt().coerceIn(1, 30)
    val count = settings.defaultSnoozeMaxCount.toInt().coerceIn(1, 10)
    val diminishingSeq = List(count) { idx -> (base - idx * 3).coerceAtLeast(2).toLong() }
    val starter =
      AlarmProfile(
        userId = currentUserId,
        label = "Morning Foundry Wake",
        hour = 6L,
        minute = 30L,
        repeatDays = listOf(1L, 2L, 3L, 4L, 5L),
        enabled = true,
        vibrate = true,
        rampSeconds = settings.defaultRampSeconds,
        maxVolumePercent = 85L,
        sunriseEnabled = settings.sunriseGlowEnabled,
        snoozeMode = SnoozeMode.DIMINISHING.name,
        snoozeIntervalMinutes = base.toLong(),
        snoozeMaxCount = count.toLong(),
        snoozeSequenceMinutes = diminishingSeq,
        linkedRoutineId = routinesState.value.firstOrNull()?.id,
      )
    saveAlarm(starter)
  }

  fun saveSettings(settings: UserSettings) {
    viewModelScope.launch {
      val result = alarmRepository.saveSettings(currentUserId, settings)
      if (result.isSuccess) {
        _messages.tryEmit("Preferences synced")
      } else {
        _messages.tryEmit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save preferences")
      }
    }
  }

  private companion object {
    const val TAG = "AlarmsVM"
  }
}

package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AlarmProfile
import com.example.model.BuiltInSound
import com.example.model.SnoozeMode
import com.example.model.UserSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AlarmRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireMatchingUid(expectedUserId: String): String {
    val uid =
      auth.currentUser?.uid
        ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user access." }
    return uid
  }

  fun observeAlarms(userId: String): Flow<List<AlarmProfile>> = flow {
    val uid = requireMatchingUid(userId)
    val path = "users/$uid/alarms"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("alarms")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(AlarmProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareBy<AlarmProfile> { it.hour }.thenBy { it.minute }.thenBy { it.label })
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(toLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeSettings(userId: String): Flow<UserSettings> = flow {
    val uid = requireMatchingUid(userId)
    val path = "users/$uid/preferences/settings"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("preferences")
        .document("settings")
        .snapshots()
        .map { snapshot ->
          snapshot.toObject(UserSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: UserSettings(userId = uid)
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val toLog = firestoreError ?: (error as? Exception)
          if (toLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(toLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  suspend fun saveSettings(userId: String, settings: UserSettings): Result<Unit> {
    val path = "users/$userId/preferences/settings"
    return try {
      val uid = requireMatchingUid(userId)
      val targetSleep = settings.targetSleepMinutes.coerceIn(180L, 720L)
      val defaultSnooze = settings.defaultSnoozeMinutes.coerceIn(1L, 30L)
      val defaultMaxSnooze = settings.defaultSnoozeMaxCount.coerceIn(0L, 10L)
      val defaultRamp = settings.defaultRampSeconds.coerceIn(0L, 300L)

      val docRef = db.collection("users").document(uid).collection("preferences").document("settings")
      val existingSnap = docRef.get().await()
      if (existingSnap.exists()) {
        docRef
          .update(
            mapOf(
              "targetSleepMinutes" to targetSleep,
              "defaultSnoozeMinutes" to defaultSnooze,
              "defaultSnoozeMaxCount" to defaultMaxSnooze,
              "defaultRampSeconds" to defaultRamp,
              "sunriseGlowEnabled" to settings.sunriseGlowEnabled,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "targetSleepMinutes" to targetSleep,
              "defaultSnoozeMinutes" to defaultSnooze,
              "defaultSnoozeMaxCount" to defaultMaxSnooze,
              "defaultRampSeconds" to defaultRamp,
              "sunriseGlowEnabled" to settings.sunriseGlowEnabled,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun upsertAlarm(userId: String, alarm: AlarmProfile): Result<String> {
    var path = "users/$userId/alarms"
    return try {
      val uid = requireMatchingUid(userId)
      val cleanLabel = alarm.label.trim().take(60)
      val cleanHour = alarm.hour.coerceIn(0L, 23L)
      val cleanMinute = alarm.minute.coerceIn(0L, 59L)
      val cleanDays = alarm.repeatDays.map { it.coerceIn(1L, 7L) }.distinct().sorted().take(7)
      val cleanSoundId = alarm.soundId.trim().ifBlank { BuiltInSound.COPPER_BELL.id }.take(64)
      val cleanSoundTitle = alarm.soundTitle.trim().ifBlank { BuiltInSound.COPPER_BELL.title }.take(80)
      val cleanSoundUri = alarm.soundUri?.trim()?.takeIf { it.isNotEmpty() }?.take(512)
      val cleanRamp = alarm.rampSeconds.coerceIn(0L, 300L)
      val cleanVolume = alarm.maxVolumePercent.coerceIn(10L, 100L)
      val cleanSnoozeMode = SnoozeMode.safeValueOf(alarm.snoozeMode).name
      val cleanSnoozeMin = alarm.snoozeIntervalMinutes.coerceIn(1L, 30L)
      val cleanSnoozeMax = alarm.snoozeMaxCount.coerceIn(0L, 10L)
      val cleanSeq =
        alarm.snoozeSequenceMinutes.map { it.coerceIn(1L, 30L) }.take(10).ifEmpty {
          listOf(cleanSnoozeMin)
        }
      val cleanLinkedRoutine = alarm.linkedRoutineId?.trim()?.takeIf { it.isNotEmpty() }?.take(128)

      val collectionRef = db.collection("users").document(uid).collection("alarms")
      val isUpdate = alarm.id.isNotBlank()
      val docRef = if (isUpdate) collectionRef.document(alarm.id) else collectionRef.document()
      path = docRef.path

      if (isUpdate) {
        val updates =
          mutableMapOf<String, Any>(
            "label" to cleanLabel,
            "hour" to cleanHour,
            "minute" to cleanMinute,
            "repeatDays" to cleanDays,
            "enabled" to alarm.enabled,
            "vibrate" to alarm.vibrate,
            "soundId" to cleanSoundId,
            "soundTitle" to cleanSoundTitle,
            "soundUri" to (cleanSoundUri ?: FieldValue.delete()),
            "rampSeconds" to cleanRamp,
            "maxVolumePercent" to cleanVolume,
            "sunriseEnabled" to alarm.sunriseEnabled,
            "snoozeMode" to cleanSnoozeMode,
            "snoozeIntervalMinutes" to cleanSnoozeMin,
            "snoozeMaxCount" to cleanSnoozeMax,
            "snoozeSequenceMinutes" to cleanSeq,
            "linkedRoutineId" to (cleanLinkedRoutine ?: FieldValue.delete()),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        docRef.update(updates).await()
      } else {
        val payload =
          mapOf(
            "userId" to uid,
            "label" to cleanLabel,
            "hour" to cleanHour,
            "minute" to cleanMinute,
            "repeatDays" to cleanDays,
            "enabled" to alarm.enabled,
            "vibrate" to alarm.vibrate,
            "soundId" to cleanSoundId,
            "soundTitle" to cleanSoundTitle,
            "soundUri" to cleanSoundUri,
            "rampSeconds" to cleanRamp,
            "maxVolumePercent" to cleanVolume,
            "sunriseEnabled" to alarm.sunriseEnabled,
            "snoozeMode" to cleanSnoozeMode,
            "snoozeIntervalMinutes" to cleanSnoozeMin,
            "snoozeMaxCount" to cleanSnoozeMax,
            "snoozeSequenceMinutes" to cleanSeq,
            "linkedRoutineId" to cleanLinkedRoutine,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
            .filterValues { it != null }
        docRef.set(payload).await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (alarm.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun toggleAlarm(userId: String, alarmId: String, enabled: Boolean): Result<Unit> {
    val path = "users/$userId/alarms/$alarmId"
    return try {
      val uid = requireMatchingUid(userId)
      require(alarmId.isNotBlank()) { "Alarm ID must not be blank" }
      db.collection("users")
        .document(uid)
        .collection("alarms")
        .document(alarmId)
        .update(mapOf("enabled" to enabled, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteAlarm(userId: String, alarmId: String): Result<Unit> {
    val path = "users/$userId/alarms/$alarmId"
    return try {
      val uid = requireMatchingUid(userId)
      require(alarmId.isNotBlank()) { "Alarm ID must not be blank" }
      db.collection("users").document(uid).collection("alarms").document(alarmId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }
}

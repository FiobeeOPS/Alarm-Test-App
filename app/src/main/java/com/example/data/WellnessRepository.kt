package com.example.data

import android.content.Context
import com.example.R
import com.example.model.MorningRoutine
import com.example.model.RoutineRun
import com.example.model.RoutineStep
import com.example.model.SleepLog
import com.example.model.WakeMood
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
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

class WellnessRepository(
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

  fun observeRoutines(userId: String): Flow<List<MorningRoutine>> = flow {
    val uid = requireMatchingUid(userId)
    val path = "users/$uid/routines"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("routines")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(MorningRoutine::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
              compareByDescending<MorningRoutine> { it.defaultRoutine }
                .thenBy { it.createdAt ?: Timestamp(0, 0) }
            )
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

  fun observeRoutineRuns(userId: String): Flow<List<RoutineRun>> = flow {
    val uid = requireMatchingUid(userId)
    val path = "users/$uid/routineRuns"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("routineRuns")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(RoutineRun::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
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

  fun observeSleepLogs(userId: String): Flow<List<SleepLog>> = flow {
    val uid = requireMatchingUid(userId)
    val path = "users/$uid/sleepLogs"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("sleepLogs")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(SleepLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(
              compareByDescending<SleepLog> { it.dateKey }
                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
            )
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

  suspend fun saveRoutine(userId: String, routine: MorningRoutine): Result<String> {
    var path = "users/$userId/routines"
    return try {
      val uid = requireMatchingUid(userId)
      val cleanTitle = routine.title.trim().ifBlank { "Morning Routine" }.take(60)
      val cleanSteps =
        routine.steps.take(20).mapIndexed { index, step ->
          mapOf(
            "stepId" to step.stepId.ifBlank { "step_${index + 1}" }.take(64),
            "title" to step.title.trim().ifBlank { "Step ${index + 1}" }.take(60),
            "durationMinutes" to step.durationMinutes.coerceIn(1L, 120L),
            "iconKey" to step.iconKey.ifBlank { "check" }.take(24),
          )
        }

      val collectionRef = db.collection("users").document(uid).collection("routines")
      val isUpdate = routine.id.isNotBlank()
      val docRef = if (isUpdate) collectionRef.document(routine.id) else collectionRef.document()
      path = docRef.path

      if (isUpdate) {
        docRef
          .update(
            mapOf(
              "title" to cleanTitle,
              "defaultRoutine" to routine.defaultRoutine,
              "steps" to cleanSteps,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "title" to cleanTitle,
              "defaultRoutine" to routine.defaultRoutine,
              "steps" to cleanSteps,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (routine.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteRoutine(userId: String, routineId: String): Result<Unit> {
    val path = "users/$userId/routines/$routineId"
    return try {
      val uid = requireMatchingUid(userId)
      require(routineId.isNotBlank()) { "Routine ID must not be blank" }
      db.collection("users").document(uid).collection("routines").document(routineId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun logRoutineRun(userId: String, run: RoutineRun): Result<String> {
    var path = "users/$userId/routineRuns"
    return try {
      val uid = requireMatchingUid(userId)
      val docRef = db.collection("users").document(uid).collection("routineRuns").document()
      path = docRef.path
      docRef
        .set(
          mapOf(
            "userId" to uid,
            "routineId" to run.routineId.trim().ifBlank { "custom" }.take(128),
            "routineTitle" to run.routineTitle.trim().ifBlank { "Morning Routine" }.take(60),
            "dateKey" to run.dateKey,
            "completedStepCount" to run.completedStepCount.coerceIn(0L, 20L),
            "totalStepCount" to run.totalStepCount.coerceIn(1L, 20L),
            "elapsedSeconds" to run.elapsedSeconds.coerceIn(0L, 86400L),
            "completed" to run.completed,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun saveSleepLog(userId: String, log: SleepLog): Result<String> {
    var path = "users/$userId/sleepLogs"
    return try {
      val uid = requireMatchingUid(userId)
      val collectionRef = db.collection("users").document(uid).collection("sleepLogs")
      val isUpdate = log.id.isNotBlank()
      val docRef = if (isUpdate) collectionRef.document(log.id) else collectionRef.document()
      path = docRef.path

      val cleanDuration = log.durationMinutes.coerceIn(15L, 1440L)
      val cleanQuality = log.qualityRating.coerceIn(1L, 5L)
      val cleanSnooze = log.snoozeCountUsed.coerceIn(0L, 20L)
      val cleanEnergy = log.energyScore.coerceIn(1L, 10L)
      val cleanMood = WakeMood.safeValueOf(log.mood).name
      val cleanReadiness = log.readinessScore.coerceIn(0L, 100L)
      val cleanTags = log.tags.map { it.trim().take(28) }.filter { it.isNotEmpty() }.distinct().take(10)
      val cleanNotes = log.notes.trim().take(300)

      if (isUpdate) {
        docRef
          .update(
            mapOf(
              "dateKey" to log.dateKey,
              "bedtimeEpochMs" to log.bedtimeEpochMs.coerceAtLeast(0L),
              "wakeEpochMs" to log.wakeEpochMs.coerceAtLeast(0L),
              "durationMinutes" to cleanDuration,
              "qualityRating" to cleanQuality,
              "snoozeCountUsed" to cleanSnooze,
              "energyScore" to cleanEnergy,
              "mood" to cleanMood,
              "readinessScore" to cleanReadiness,
              "tags" to cleanTags,
              "notes" to cleanNotes,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .set(
            mapOf(
              "userId" to uid,
              "dateKey" to log.dateKey,
              "bedtimeEpochMs" to log.bedtimeEpochMs.coerceAtLeast(0L),
              "wakeEpochMs" to log.wakeEpochMs.coerceAtLeast(0L),
              "durationMinutes" to cleanDuration,
              "qualityRating" to cleanQuality,
              "snoozeCountUsed" to cleanSnooze,
              "energyScore" to cleanEnergy,
              "mood" to cleanMood,
              "readinessScore" to cleanReadiness,
              "tags" to cleanTags,
              "notes" to cleanNotes,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (log.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteSleepLog(userId: String, logId: String): Result<Unit> {
    val path = "users/$userId/sleepLogs/$logId"
    return try {
      val uid = requireMatchingUid(userId)
      require(logId.isNotBlank()) { "SleepLog ID must not be blank" }
      db.collection("users").document(uid).collection("sleepLogs").document(logId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun createStarterRoutine(userId: String): Result<String> {
    val starter =
      MorningRoutine(
        userId = userId,
        title = "Foundry Morning Protocol",
        defaultRoutine = true,
        steps =
          listOf(
            RoutineStep(stepId = "hydrate", title = "Hydrate 500ml Water", durationMinutes = 2L, iconKey = "water"),
            RoutineStep(stepId = "sunlight", title = "Natural Sunlight Exposure", durationMinutes = 5L, iconKey = "sun"),
            RoutineStep(stepId = "mobility", title = "Spine & Shoulder Mobility", durationMinutes = 6L, iconKey = "stretch"),
            RoutineStep(stepId = "focus", title = "Set Top 3 Daily Priorities", durationMinutes = 3L, iconKey = "focus"),
          ),
      )
    return saveRoutine(userId, starter)
  }
}

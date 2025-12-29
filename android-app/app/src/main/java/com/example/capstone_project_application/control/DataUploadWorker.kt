package com.example.capstone_project_application.control

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.capstone_project_application.entity.AppDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Background worker for uploading unsynced data to Firebase Firestore.
 * ONLY uploads data for participants who have completed all required trials.
 */
class DataUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val firestore = FirebaseFirestore.getInstance()
    private val roomDb = AppDatabase.getDatabase(appContext)

    companion object {
        private const val TAG = "DataUploadWorker"
        private const val BATCH_SIZE = 450
        private const val COLLECTION_PARTICIPANTS = "participants"
        private const val COLLECTION_TARGET_TRIALS = "target_trials"
        private const val EXPECTED_TRIAL_COUNT = 15 // MUST match TargetController.TOTAL_TRIALS
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "╔═══════════════════════════════════════════╗")
                Log.d(TAG, "STARTING DATA UPLOAD JOB")
                Log.d(TAG, "╚═══════════════════════════════════════════╝")

                uploadParticipants()
                uploadTargetTrials()

                Log.d(TAG, "╔═══════════════════════════════════════════╗")
                Log.d(TAG, "UPLOAD JOB COMPLETED SUCCESSFULLY")
                Log.d(TAG, "╚═══════════════════════════════════════════╝")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "✗✗✗ UPLOAD FAILED ✗✗✗", e)
                Result.retry()
            }
        }
    }

    // ===========================
    // Participant Upload
    // ===========================

    private suspend fun uploadParticipants() {
        val unsyncedParticipants = roomDb.participantDao().getUnsyncedParticipants()

        if (unsyncedParticipants.isEmpty()) {
            Log.d(TAG, "→ No participants to upload")
            return
        }

        Log.d(TAG, "→ Uploading ${unsyncedParticipants.size} participant(s)")

        for (participant in unsyncedParticipants) {
            val participantMap = mapOf(
                "participantId" to participant.participantId,
                "age" to participant.age,
                "gender" to participant.gender,
                "hasGlasses" to participant.hasGlasses,
                "hasAttentionDeficit" to participant.hasAttentionDeficit,
                "consentGiven" to participant.consentGiven,
                "registrationTimestamp" to participant.registrationTimestamp,
                "jndThreshold" to participant.jndThreshold
            )

            firestore.collection(COLLECTION_PARTICIPANTS)
                .document(participant.participantId)
                .set(participantMap)
                .await()

            Log.d(TAG, "  ✓ Participant ${participant.participantId.take(8)}... uploaded")
        }

        val participantIds = unsyncedParticipants.map { it.participantId }
        roomDb.participantDao().markAsUploaded(participantIds)

        Log.d(TAG, "✓ All participants uploaded and marked as synced")
    }

    // ===========================
    // Target Trial Upload - WITH COMPLETION CHECK
    // ===========================

    private suspend fun uploadTargetTrials() {
        val unsyncedTrials = roomDb.targetTrialDao().getUnsyncedTrials()

        if (unsyncedTrials.isEmpty()) {
            Log.d(TAG, "→ No target trials to upload")
            return
        }

        Log.d(TAG, "→ Found ${unsyncedTrials.size} unsynced trial(s)")

        // Group by participant and check completion
        val trialsByParticipant = unsyncedTrials.groupBy { it.participantId }
        val uploadedTrialIds = mutableListOf<Int>()
        val incompleteParticipants = mutableListOf<String>()

        for ((participantId, trials) in trialsByParticipant) {
            val trialCount = trials.size

            // CRITICAL CHECK: Only upload if participant completed ALL trials
            if (trialCount < EXPECTED_TRIAL_COUNT) {
                Log.w(TAG, "  ⚠ Participant ${participantId.take(8)}... has only $trialCount/$EXPECTED_TRIAL_COUNT trials - SKIPPING upload")
                incompleteParticipants.add(participantId)
                continue
            }

            Log.d(TAG, "  → Uploading $trialCount trials for participant ${participantId.take(8)}...")

            // Upload in chunks to respect Firestore batch limit
            trials.chunked(BATCH_SIZE).forEach { chunk ->
                uploadTrialBatch(participantId, chunk)
                uploadedTrialIds.addAll(chunk.map { it.id })
            }
        }

        // Mark only uploaded trials as synced
        if (uploadedTrialIds.isNotEmpty()) {
            roomDb.targetTrialDao().markAsUploaded(uploadedTrialIds)
            Log.d(TAG, "✓ ${uploadedTrialIds.size} trials uploaded and marked as synced")
        }

        // Log warning about incomplete data
        if (incompleteParticipants.isNotEmpty()) {
            Log.w(TAG, "⚠ ${incompleteParticipants.size} participant(s) with incomplete data not uploaded")
            Log.w(TAG, "  This data will remain in local database until completed or cleared")
        }
    }

    private suspend fun uploadTrialBatch(
        participantId: String,
        trials: List<com.example.capstone_project_application.entity.TargetTrialResult>
    ) {
        val batch = firestore.batch()
        val participantRef = firestore.collection(COLLECTION_PARTICIPANTS).document(participantId)
        val trialsCollection = participantRef.collection(COLLECTION_TARGET_TRIALS)

        for (trial in trials) {
            val trialMap = buildTrialMap(trial)
            val documentId = "trial_${trial.trialNumber}"
            val newTrialRef = trialsCollection.document(documentId)
            batch.set(newTrialRef, trialMap)
        }

        batch.commit().await()
        Log.d(TAG, "    ✓ Batch of ${trials.size} trials uploaded")
    }

    private fun buildTrialMap(
        trial: com.example.capstone_project_application.entity.TargetTrialResult
    ): MutableMap<String, Any?> {
        val movementPathList = parseMovementPath(trial.movementPath)

        val trialMap = mutableMapOf<String, Any?>(
            "trialNumber" to trial.trialNumber,
            "trialType" to trial.trialType,
            "targetIndex" to trial.targetIndex,
            "trialStartTimestamp" to trial.trialStartTimestamp,
            "responseTimestamp" to trial.responseTimestamp,
            "totalResponseTime" to trial.totalResponseTime,
            "movementPath" to movementPathList,
            "pathLength" to trial.pathLength,
            "averageSpeed" to trial.averageSpeed,
            "initialHue" to trial.initialHue,
            "goBeepTimestamp" to trial.goBeepTimestamp
        )

        trial.firstMovementTimestamp?.let { trialMap["firstMovementTimestamp"] = it }
        trial.targetReachedTimestamp?.let { trialMap["targetReachedTimestamp"] = it }
        trial.reactionTime?.let { trialMap["reactionTime"] = it }
        trial.movementTime?.let { trialMap["movementTime"] = it }
        trial.finalHue?.let { trialMap["finalHue"] = it }

        return trialMap
    }

    private fun parseMovementPath(movementPath: String): List<Map<String, Any>> {
        val movementPathList = mutableListOf<Map<String, Any>>()

        try {
            val pathArray = JSONArray(movementPath)
            for (i in 0 until pathArray.length()) {
                val point = pathArray.getJSONObject(i)
                movementPathList.add(mapOf(
                    "x" to point.getDouble("x"),
                    "y" to point.getDouble("y"),
                    "t" to point.getLong("t")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing movement path", e)
        }

        return movementPathList
    }
}
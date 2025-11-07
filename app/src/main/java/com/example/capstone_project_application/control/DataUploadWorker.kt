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
 * A Worker class to handle uploading unsynced data from Room to Firebase Firestore.
 */
class DataUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val firestore = FirebaseFirestore.getInstance()
    private val roomDb = AppDatabase.getDatabase(appContext)

    companion object {
        private const val TAG = "DataUploadWorker"
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "════════════════════════════════════════")
                Log.d(TAG, "STARTING DATA UPLOAD JOB")
                Log.d(TAG, "════════════════════════════════════════")

                uploadParticipants()
                uploadTargetTrials()
                uploadMovementData()

                Log.d(TAG, "════════════════════════════════════════")
                Log.d(TAG, "UPLOAD JOB COMPLETED SUCCESSFULLY")
                Log.d(TAG, "════════════════════════════════════════")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "✗✗✗ UPLOAD FAILED ✗✗✗", e)
                Result.retry()
            }
        }
    }

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

            firestore.collection("participants")
                .document(participant.participantId)
                .set(participantMap)
                .await()

            Log.d(TAG, "  ✓ Participant ${participant.participantId.take(8)}... uploaded")
        }

        val participantIds = unsyncedParticipants.map { it.participantId }
        roomDb.participantDao().markAsUploaded(participantIds)

        Log.d(TAG, "✓ All participants uploaded and marked as synced")
    }

    private suspend fun uploadTargetTrials() {
        val unsyncedTrials = roomDb.targetTrialDao().getUnsyncedTrials()

        if (unsyncedTrials.isEmpty()) {
            Log.d(TAG, "→ No target trials to upload")
            return
        }

        Log.d(TAG, "→ Uploading ${unsyncedTrials.size} target trial(s)")

        val trialsByParticipant = unsyncedTrials.groupBy { it.participantId }

        for ((participantId, trials) in trialsByParticipant) {
            val participantRef = firestore.collection("participants").document(participantId)
            Log.d(TAG, "  → Uploading ${trials.size} trials for participant ${participantId.take(8)}...")

            for (trial in trials) {
                val movementPathList = mutableListOf<Map<String, Any>>()
                try {
                    val pathArray = JSONArray(trial.movementPath)
                    for (i in 0 until pathArray.length()) {
                        val point = pathArray.getJSONObject(i)
                        movementPathList.add(mapOf(
                            "x" to point.getDouble("x"),
                            "y" to point.getDouble("y"),
                            "t" to point.getLong("t")
                        ))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "    ✗ Error parsing movement path for trial ${trial.trialNumber}", e)
                }

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

                participantRef.collection("target_trials")
                    .add(trialMap)
                    .await()

                Log.d(TAG, "    ✓ Trial ${trial.trialNumber} uploaded")
            }
        }

        val syncedIds = unsyncedTrials.map { it.id }
        roomDb.targetTrialDao().markAsUploaded(syncedIds)

        Log.d(TAG, "✓ All target trials uploaded and marked as synced")
    }

    private suspend fun uploadMovementData() {
        val unsyncedData = roomDb.movementDataDao().getAllUnsyncedData()

        if (unsyncedData.isEmpty()) {
            Log.d(TAG, "→ No movement data to upload")
            return
        }

        Log.d(TAG, "→ Uploading ${unsyncedData.size} movement data point(s)")

        val dataByParticipant = unsyncedData.groupBy { it.participantId }

        for ((participantId, movementDataList) in dataByParticipant) {
            val participantRef = firestore.collection("participants").document(participantId)
            Log.d(TAG, "  → Uploading ${movementDataList.size} data points for participant ${participantId.take(8)}...")

            for (movementData in movementDataList) {
                val dataMap = mapOf(
                    "timestamp" to movementData.timestamp,
                    "latitude" to movementData.latitude,
                    "longitude" to movementData.longitude,
                    "altitude" to movementData.altitude,
                    "speed" to movementData.speed,
                    "accelX" to movementData.accelX,
                    "accelY" to movementData.accelY,
                    "accelZ" to movementData.accelZ,
                    "gyroX" to movementData.gyroX,
                    "gyroY" to movementData.gyroY,
                    "gyroZ" to movementData.gyroZ
                )

                participantRef.collection("movement_data")
                    .add(dataMap)
                    .await()
            }

            Log.d(TAG, "  ✓ Movement data uploaded for participant ${participantId.take(8)}...")
        }

        val syncedIds = unsyncedData.map { it.id }
        roomDb.movementDataDao().markAsUploaded(syncedIds)

        Log.d(TAG, "✓ All movement data uploaded and marked as synced")
    }
}
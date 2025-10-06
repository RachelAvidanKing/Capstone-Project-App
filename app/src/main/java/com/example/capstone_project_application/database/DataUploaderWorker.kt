package com.example.capstone_project_application.database

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * A Worker class to handle uploading unsynced data from Room to Firebase Firestore.
 * This uses WorkManager to ensure the task runs reliably, even if the app is closed.
 */
class DataUploaderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val firestore = FirebaseFirestore.getInstance()
    private val roomDb = AppDatabase.getDatabase(appContext)

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("DataUploaderWorker", "Starting data upload job")

                // Upload participants first - always try this
                uploadParticipants()

                // Then upload movement data (if any exists)
                uploadMovementData()

                Log.d("DataUploaderWorker", "Upload job completed successfully")
                Result.success()
            } catch (e: Exception) {
                Log.e("DataUploaderWorker", "Upload failed", e)
                Result.retry()
            }
        }
    }

    private suspend fun uploadParticipants() {
        val unsyncedParticipants = roomDb.participantDao().getUnsyncedParticipants()

        if (unsyncedParticipants.isNotEmpty()) {
            Log.d("DataUploaderWorker", "Uploading ${unsyncedParticipants.size} participants")

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

                // Upload to Firestore
                firestore.collection("participants")
                    .document(participant.participantId)
                    .set(participantMap) // Use the map here
                    .await()
            }

            // Mark as uploaded
            val participantIds = unsyncedParticipants.map { it.participantId }
            roomDb.participantDao().markAsUploaded(participantIds)

            Log.d("DataUploaderWorker", "Participants uploaded successfully")
        }
    }

    private suspend fun uploadMovementData() {
        val unsyncedData = roomDb.movementDataDao().getAllUnsyncedData()

        if (unsyncedData.isNotEmpty()) {
            Log.d("DataUploaderWorker", "Uploading ${unsyncedData.size} movement data points")

            // Group by participant for efficient upload
            val dataByParticipant = unsyncedData.groupBy { it.participantId }

            // Upload data to Firestore
            for ((participantId, movementDataList) in dataByParticipant) {
                val participantRef = firestore.collection("participants").document(participantId)

                // Upload each movement data point
                for (movementData in movementDataList) {
                    // Convert to map for Firestore (removing Room-specific fields)
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
            }

            // Mark data as synced in the local database
            val syncedIds = unsyncedData.map { it.id }
            roomDb.movementDataDao().markAsUploaded(syncedIds)

            Log.d("DataUploaderWorker", "Movement data uploaded successfully")
        }
    }
}
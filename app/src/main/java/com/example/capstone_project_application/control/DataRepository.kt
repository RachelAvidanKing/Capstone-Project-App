package com.example.capstone_project_application.control

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.capstone_project_application.entity.AppDatabase
import com.example.capstone_project_application.entity.MovementDataPoint
import com.example.capstone_project_application.entity.Participant
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository class to handle data operations and business logic
 */
class DataRepository(private val database: AppDatabase, private val context: Context) {

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("capstone_prefs", Context.MODE_PRIVATE)

    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "DataRepository"
        private const val EXPECTED_TRIAL_COUNT = 15 // Total number of target trials
    }

    /**
     * Get the current participant ID
     */
    fun getCurrentParticipantId(): String {
        return sharedPrefs.getString("current_participant_id", null)
            ?: throw IllegalStateException("No participant ID set")
    }

    /**
     * Set a specific participant ID (used during login)
     */
    fun setParticipantId(participantId: String) {
        sharedPrefs.edit()
            .putString("current_participant_id", participantId)
            .apply()
        Log.d(TAG, "Participant ID set to: $participantId")
    }

    /**
     * Fetch participant data from Firebase and check completion status
     * Returns null if participant doesn't exist
     */
    suspend fun fetchParticipantFromFirebase(participantId: String): Participant? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching participant $participantId from Firebase...")

                val document = firestore.collection("participants")
                    .document(participantId)
                    .get()
                    .await()

                if (!document.exists()) {
                    Log.d(TAG, "Participant $participantId not found in Firebase")
                    return@withContext null
                }

                val data = document.data ?: return@withContext null

                val participant = Participant(
                    participantId = participantId,
                    age = (data["age"] as? Long)?.toInt() ?: 0,
                    gender = data["gender"] as? String ?: "",
                    hasGlasses = data["hasGlasses"] as? Boolean ?: false,
                    hasAttentionDeficit = data["hasAttentionDeficit"] as? Boolean ?: false,
                    consentGiven = data["consentGiven"] as? Boolean ?: false,
                    registrationTimestamp = data["registrationTimestamp"] as? Long ?: 0L,
                    jndThreshold = (data["jndThreshold"] as? Long)?.toInt(),
                    isUploaded = true
                )

                Log.d(TAG, "Successfully fetched participant: $participantId, JND: ${participant.jndThreshold}")
                participant
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching participant from Firebase", e)
                null
            }
        }
    }

    /**
     * Check if participant has completed all target trials in Firebase
     */
    suspend fun hasCompletedExperiment(participantId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val trialsSnapshot = firestore.collection("participants")
                    .document(participantId)
                    .collection("target_trials")
                    .get()
                    .await()

                val trialCount = trialsSnapshot.documents.size
                val isComplete = trialCount >= EXPECTED_TRIAL_COUNT

                Log.d(TAG, "Participant $participantId has $trialCount trials (complete: $isComplete)")
                isComplete
            } catch (e: Exception) {
                Log.e(TAG, "Error checking experiment completion", e)
                false
            }
        }
    }

    /**
     * Save an existing participant locally and set as current
     */
    suspend fun setExistingParticipant(participant: Participant) {
        withContext(Dispatchers.IO) {
            database.participantDao().insert(participant)

            sharedPrefs.edit()
                .putString("current_participant_id", participant.participantId)
                .apply()

            Log.d(TAG, "Existing participant saved locally and set as current: ${participant.participantId}")
        }
    }

    /**
     * Register a new participant with consent and demographics.
     * Uses the participant ID that was set during login.
     */
    suspend fun registerParticipant(
        age: Int,
        gender: String,
        consentGiven: Boolean,
        hasGlasses: Boolean,
        hasAttentionDeficit: Boolean
    ): String {
        return withContext(Dispatchers.IO) {
            val participantId = getCurrentParticipantId()

            val participant = Participant(
                participantId = participantId,
                age = age,
                gender = gender,
                hasGlasses = hasGlasses,
                hasAttentionDeficit = hasAttentionDeficit,
                consentGiven = consentGiven,
                registrationTimestamp = System.currentTimeMillis(),
                jndThreshold = null,
                isUploaded = false
            )

            database.participantDao().insert(participant)
            Log.d(TAG, "New participant registered: $participantId")
            participantId
        }
    }

    /**
     * Upload only participant demographics to Firebase
     * Used when exiting registration page
     */
    suspend fun uploadParticipantDemographics(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val participant = getCurrentParticipant() ?: return@withContext false

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

                val updatedParticipant = participant.copy(isUploaded = true)
                database.participantDao().update(updatedParticipant)

                Log.d(TAG, "✓ Participant demographics uploaded to Firebase")
                true
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error uploading participant demographics", e)
                false
            }
        }
    }

    /**
     * Clear current participant session (logout)
     */
    fun clearCurrentParticipant() {
        sharedPrefs.edit()
            .remove("current_participant_id")
            .apply()
        Log.d(TAG, "Current participant session cleared")
    }

    /**
     * Delete incomplete local data (threshold/target trials that weren't completed)
     */
    suspend fun clearIncompleteTrialData() {
        withContext(Dispatchers.IO) {
            try {
                val participantId = getCurrentParticipantId()

                // Delete all local target trials (they weren't completed)
                val trials = database.targetTrialDao().getTrialsForParticipant(participantId)
                trials.forEach { trial ->
                    database.targetTrialDao().delete(trial)
                }

                Log.d(TAG, "✓ Cleared ${trials.size} incomplete trial(s)")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing incomplete data", e)
            }
        }
    }

    /**
     * Insert movement data for the current participant
     */
    suspend fun insertMovementData(
        latitude: Double,
        longitude: Double,
        altitude: Double,
        speed: Float,
        accelX: Float,
        accelY: Float,
        accelZ: Float,
        gyroX: Float = 0.0f,
        gyroY: Float = 0.0f,
        gyroZ: Float = 0.0f
    ) {
        withContext(Dispatchers.IO) {
            val movementData = MovementDataPoint(
                participantId = getCurrentParticipantId(),
                timestamp = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                speed = speed,
                accelX = accelX,
                accelY = accelY,
                accelZ = accelZ,
                gyroX = gyroX,
                gyroY = gyroY,
                gyroZ = gyroZ
            )
            database.movementDataDao().insert(movementData)
        }
    }

    /**
     * Get movement data for analysis by demographics
     */
    suspend fun getMovementDataByDemographics(gender: String, minAge: Int, maxAge: Int): List<MovementDataPoint> {
        return withContext(Dispatchers.IO) {
            database.movementDataDao().getMovementDataByDemographics(gender, minAge, maxAge)
        }
    }

    /**
     * Get unsynced data count
     */
    suspend fun getUnsyncedDataCount(): Int {
        return withContext(Dispatchers.IO) {
            database.movementDataDao().getAllUnsyncedData().size
        }
    }

    /**
     * Check if participant is registered
     */
    suspend fun isParticipantRegistered(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val participantId = getCurrentParticipantId()
                database.participantDao().getParticipant(participantId) != null
            } catch (e: IllegalStateException) {
                false
            }
        }
    }

    /**
     * Get current participant info
     */
    suspend fun getCurrentParticipant(): Participant? {
        return withContext(Dispatchers.IO) {
            try {
                database.participantDao().getParticipant(getCurrentParticipantId())
            } catch (e: IllegalStateException) {
                null
            }
        }
    }

    suspend fun updateParticipant(participant: Participant) {
        database.participantDao().update(participant)
    }
}
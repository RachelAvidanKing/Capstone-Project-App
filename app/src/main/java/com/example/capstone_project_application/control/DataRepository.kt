package com.example.capstone_project_application.control

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.capstone_project_application.entity.AppDatabase
import com.example.capstone_project_application.entity.MovementDataPoint
import com.example.capstone_project_application.entity.Participant
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository class serving as the single source of truth for data operations.
 *
 * This class abstracts data sources (local Room database and remote Firebase Firestore)
 * and provides a clean API for managing participant registration, authentication,
 * experiment data, and synchronization between local and remote storage.
 *
 * ## Responsibilities:
 * - Participant registration and authentication
 * - Experiment progress tracking
 * - Local data persistence via Room
 * - Remote data synchronization via Firebase Firestore
 * - Session management via SharedPreferences
 *
 * ## Architecture:
 * Implements the Repository pattern from Clean Architecture, providing a consistent
 * interface regardless of data source.
 *
 * @property database The local Room database instance for offline data persistence
 * @property context Android context for accessing SharedPreferences and resources
 */
class DataRepository(
    private val database: AppDatabase,
    private val context: Context
) {

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "DataRepository"
        private const val PREFS_NAME = "capstone_prefs"
        private const val KEY_PARTICIPANT_ID = "current_participant_id"
        private const val EXPECTED_TRIAL_COUNT = 15

        // Firebase collection names
        private const val COLLECTION_PARTICIPANTS = "participants"
        private const val COLLECTION_TARGET_TRIALS = "target_trials"
        private const val COLLECTION_MOVEMENT_DATA = "movement_data"
    }

    // ===========================
    // Session Management
    // ===========================

    /**
     * Retrieves the current participant ID from the session.
     *
     * @return The participant ID string
     * @throws IllegalStateException if no participant ID is set
     */
    fun getCurrentParticipantId(): String {
        return sharedPrefs.getString(KEY_PARTICIPANT_ID, null)
            ?: throw IllegalStateException("No participant ID set")
    }

    /**
     * Sets the current participant ID in the session.
     * This is typically called during login before registration is complete.
     *
     * @param participantId The unique participant identifier
     */
    fun setParticipantId(participantId: String) {
        sharedPrefs.edit()
            .putString(KEY_PARTICIPANT_ID, participantId)
            .apply()
        Log.d(TAG, "Participant ID set to: $participantId")
    }

    /**
     * Clears the current participant session (logout operation).
     * This removes all session data but preserves local database records.
     */
    fun clearCurrentParticipant() {
        sharedPrefs.edit()
            .remove(KEY_PARTICIPANT_ID)
            .apply()
        Log.d(TAG, "Current participant session cleared")
    }

    // ===========================
    // Participant Operations
    // ===========================

    /**
     * Fetches participant data from Firebase and checks their completion status.
     *
     * This is used during login to determine if a returning participant exists
     * and what stage of the experiment they should resume from.
     *
     * @param participantId The unique participant identifier
     * @return Participant object if found, null if participant doesn't exist or on error
     */
    suspend fun fetchParticipantFromFirebase(participantId: String): Participant? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching participant $participantId from Firebase...")

                val document = fetchParticipantDocument(participantId)

                if (!document.exists()) {
                    Log.d(TAG, "Participant $participantId not found in Firebase")
                    return@withContext null
                }

                val data = document.data ?: return@withContext null
                val participant = parseParticipantData(participantId, data)

                Log.d(TAG, "Successfully fetched participant: $participantId, JND: ${participant.jndThreshold}")
                participant
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching participant from Firebase", e)
                null
            }
        }
    }

    /**
     * Fetches the raw participant document from Firestore.
     *
     * @param participantId The unique participant identifier
     * @return DocumentSnapshot containing participant data
     */
    private suspend fun fetchParticipantDocument(participantId: String): DocumentSnapshot {
        return firestore.collection(COLLECTION_PARTICIPANTS)
            .document(participantId)
            .get()
            .await()
    }

    /**
     * Parses raw Firestore data into a [Participant] object.
     * Provides safe null handling and type casting for all fields.
     *
     * @param participantId The unique participant identifier
     * @param data Raw Firestore document data
     * @return Parsed Participant object
     */
    private fun parseParticipantData(participantId: String, data: Map<String, Any>): Participant {
        return Participant(
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
    }

    /**
     * Checks if a participant has completed all required target trials.
     *
     * This queries Firebase to count the number of trials completed, which is used
     * during login to prevent participants from repeating the experiment.
     *
     * @param participantId The unique participant identifier
     * @return true if the participant has completed all trials, false otherwise
     */
    suspend fun hasCompletedExperiment(participantId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val trialCount = firestore.collection(COLLECTION_PARTICIPANTS)
                    .document(participantId)
                    .collection(COLLECTION_TARGET_TRIALS)
                    .get()
                    .await()
                    .documents
                    .size

                val isComplete = trialCount >= EXPECTED_TRIAL_COUNT

                Log.d(TAG, "Participant $participantId has $trialCount/$EXPECTED_TRIAL_COUNT trials (complete: $isComplete)")
                isComplete
            } catch (e: Exception) {
                Log.e(TAG, "Error checking experiment completion", e)
                false
            }
        }
    }

    /**
     * Saves an existing participant locally and sets them as the current session participant.
     * Used when a returning participant logs in.
     *
     * @param participant The participant object fetched from Firebase
     */
    suspend fun setExistingParticipant(participant: Participant) {
        withContext(Dispatchers.IO) {
            database.participantDao().insert(participant)

            sharedPrefs.edit()
                .putString(KEY_PARTICIPANT_ID, participant.participantId)
                .apply()

            Log.d(TAG, "Existing participant saved locally: ${participant.participantId}")
        }
    }

    /**
     * Registers a new participant with their demographic information.
     * Uses the participant ID that was set during login.
     *
     * @param age Participant's age
     * @param gender Participant's gender
     * @param consentGiven Whether informed consent was provided
     * @param hasGlasses Whether participant wears glasses/contacts
     * @param hasAttentionDeficit Whether participant has attention deficit disorder
     * @return The participant ID
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
            Log.d(TAG, "New participant registered locally: $participantId")
            participantId
        }
    }

    /**
     * Uploads participant demographics to Firebase.
     *
     * This is called after registration or when JND threshold is updated.
     * Marks the participant as uploaded in the local database upon success.
     *
     * @return true if upload succeeded, false otherwise
     */
    suspend fun uploadParticipantDemographics(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val participant = getCurrentParticipant() ?: return@withContext false

                val participantMap = buildParticipantMap(participant)

                firestore.collection(COLLECTION_PARTICIPANTS)
                    .document(participant.participantId)
                    .set(participantMap)
                    .await()

                markParticipantAsUploaded(participant)

                Log.d(TAG, "✓ Participant demographics uploaded to Firebase")
                true
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error uploading participant demographics", e)
                false
            }
        }
    }

    /**
     * Builds a map of participant data for Firebase upload.
     *
     * @param participant The participant object
     * @return Map of field names to values
     */
    private fun buildParticipantMap(participant: Participant): Map<String, Any?> {
        return mapOf(
            "participantId" to participant.participantId,
            "age" to participant.age,
            "gender" to participant.gender,
            "hasGlasses" to participant.hasGlasses,
            "hasAttentionDeficit" to participant.hasAttentionDeficit,
            "consentGiven" to participant.consentGiven,
            "registrationTimestamp" to participant.registrationTimestamp,
            "jndThreshold" to participant.jndThreshold
        )
    }

    /**
     * Marks a participant as uploaded in the local database.
     *
     * @param participant The participant to mark as uploaded
     */
    private suspend fun markParticipantAsUploaded(participant: Participant) {
        val updatedParticipant = participant.copy(isUploaded = true)
        database.participantDao().update(updatedParticipant)
    }

    /**
     * Retrieves the current participant from the local database.
     *
     * @return Participant object if found, null if no participant is logged in
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

    /**
     * Updates an existing participant's information in the local database.
     *
     * @param participant The participant object with updated information
     */
    suspend fun updateParticipant(participant: Participant) {
        withContext(Dispatchers.IO) {
            database.participantDao().update(participant)
        }
    }

    /**
     * Checks if a participant is registered in the local database.
     *
     * @return true if registered, false otherwise
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

    // ===========================
    // Trial Data Management
    // ===========================

    /**
     * Deletes incomplete local trial data.
     *
     * This is called when a participant exits mid-experiment without completing
     * all trials. It ensures partial data doesn't corrupt the experiment results.
     */
    suspend fun clearIncompleteTrialData() {
        withContext(Dispatchers.IO) {
            try {
                val participantId = getCurrentParticipantId()

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

    // ===========================
    // Movement Data Operations
    // ===========================

    /**
     * Inserts a movement data point for the current participant.
     *
     * @param latitude GPS latitude
     * @param longitude GPS longitude
     * @param altitude GPS altitude in meters
     * @param speed Movement speed in m/s
     * @param accelX Accelerometer X-axis value
     * @param accelY Accelerometer Y-axis value
     * @param accelZ Accelerometer Z-axis value
     * @param gyroX Gyroscope X-axis value (default: 0.0f)
     * @param gyroY Gyroscope Y-axis value (default: 0.0f)
     * @param gyroZ Gyroscope Z-axis value (default: 0.0f)
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
     * Retrieves movement data filtered by participant demographics.
     * Useful for analyzing patterns across demographic groups.
     *
     * @param gender Gender filter
     * @param minAge Minimum age (inclusive)
     * @param maxAge Maximum age (inclusive)
     * @return List of matching movement data points
     */
    suspend fun getMovementDataByDemographics(
        gender: String,
        minAge: Int,
        maxAge: Int
    ): List<MovementDataPoint> {
        return withContext(Dispatchers.IO) {
            database.movementDataDao().getMovementDataByDemographics(gender, minAge, maxAge)
        }
    }

    /**
     * Counts the number of unsynced movement data points.
     *
     * @return Count of unsynced data points
     */
    suspend fun getUnsyncedDataCount(): Int {
        return withContext(Dispatchers.IO) {
            database.movementDataDao().getAllUnsyncedData().size
        }
    }
}
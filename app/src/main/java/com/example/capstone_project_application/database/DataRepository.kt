package com.example.capstone_project_application.database

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Repository class to handle data operations and business logic
 */
class DataRepository(private val database: AppDatabase, private val context: Context) {

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("capstone_prefs", Context.MODE_PRIVATE)

    /**
     * Get or create the current participant ID
     */
    fun getCurrentParticipantId(): String {
        var participantId = sharedPrefs.getString("current_participant_id", null)
        if (participantId == null) {
            participantId = UUID.randomUUID().toString()
            sharedPrefs.edit()
                .putString("current_participant_id", participantId)
                .apply()
        }
        return participantId
    }

    /**
     * Register a new participant with consent and demographics
     */
    suspend fun registerParticipant(age: Int, gender: String, consentGiven: Boolean): String {
        return withContext(Dispatchers.IO) {
            val participantId = getCurrentParticipantId()
            val participant = Participant(
                participantId = participantId,
                age = age,
                gender = gender,
                consentGiven = consentGiven,
                registrationTimestamp = System.currentTimeMillis()
            )
            database.participantDao().insert(participant)
            participantId
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
            val participantId = getCurrentParticipantId()
            database.participantDao().getParticipant(participantId) != null
        }
    }

    /**
     * Get current participant info
     */
    suspend fun getCurrentParticipant(): Participant? {
        return withContext(Dispatchers.IO) {
            database.participantDao().getParticipant(getCurrentParticipantId())
        }
    }
}
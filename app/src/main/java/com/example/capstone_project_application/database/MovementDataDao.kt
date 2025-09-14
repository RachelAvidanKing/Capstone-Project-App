package com.example.capstone_project_application.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for the MovementDataPoint entity.
 * This interface defines all the database operations (queries).
 */
@Dao
interface MovementDataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dataPoint: MovementDataPoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dataPoints: List<MovementDataPoint>)

    @Query("SELECT * FROM movement_data WHERE isUploaded = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedData(limit: Int): List<MovementDataPoint>

    @Query("SELECT * FROM movement_data WHERE isUploaded = 0 ORDER BY timestamp ASC")
    suspend fun getAllUnsyncedData(): List<MovementDataPoint>

    @Query("UPDATE movement_data SET isUploaded = 1 WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Int>)

    @Query("DELETE FROM movement_data WHERE isUploaded = 1")
    suspend fun deleteUploadedData()

    @Query("SELECT * FROM movement_data WHERE participantId = :participantId ORDER BY timestamp ASC")
    suspend fun getMovementDataForParticipant(participantId: String): List<MovementDataPoint>

    // For analytics - get data by participant demographics
    @Query("""
        SELECT md.* FROM movement_data md
        INNER JOIN participants p ON md.participantId = p.participantId
        WHERE p.gender = :gender AND p.age BETWEEN :minAge AND :maxAge
        ORDER BY md.timestamp ASC
    """)
    suspend fun getMovementDataByDemographics(gender: String, minAge: Int, maxAge: Int): List<MovementDataPoint>

    @Query("SELECT COUNT(*) FROM movement_data WHERE participantId = :participantId")
    suspend fun getDataPointCountForParticipant(participantId: String): Int
}
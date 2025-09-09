package com.example.capstoneprojectapplication.database

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

    @Query("UPDATE movement_data SET isUploaded = 1 WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Int>)

    @Query("DELETE FROM movement_data WHERE isUploaded = 1")
    suspend fun deleteUploadedData()
}

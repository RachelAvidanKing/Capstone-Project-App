package com.example.capstone_project_application.entity

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for the TargetTrialResult entity.
 */
@Dao
interface TargetTrialLocalDataSource {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trialResult: TargetTrialResult)

    @Delete
    suspend fun delete(trialResult: TargetTrialResult)

    @Query("SELECT * FROM target_trial_results WHERE participantId = :participantId ORDER BY trialNumber ASC")
    suspend fun getTrialsForParticipant(participantId: String): List<TargetTrialResult>

    @Query("SELECT COUNT(*) FROM target_trial_results WHERE participantId = :participantId")
    suspend fun getTrialCountForParticipant(participantId: String): Int

    @Query("SELECT * FROM target_trial_results WHERE isUploaded = 0")
    suspend fun getUnsyncedTrials(): List<TargetTrialResult>

    @Query("UPDATE target_trial_results SET isUploaded = 1 WHERE id IN (:trialIds)")
    suspend fun markAsUploaded(trialIds: List<Int>)

    @Query("SELECT * FROM target_trial_results")
    suspend fun getAllTrials(): List<TargetTrialResult>

    @Query("DELETE FROM target_trial_results WHERE participantId = :participantId")
    suspend fun deleteAllTrialsForParticipant(participantId: String)
}
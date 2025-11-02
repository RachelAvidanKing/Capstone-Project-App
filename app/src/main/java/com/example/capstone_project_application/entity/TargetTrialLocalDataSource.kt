package com.example.capstone_project_application.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for TargetTrialResult entity.
 */
@Dao
interface TargetTrialLocalDataSource {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trial: TargetTrialResult)

    @Query("SELECT * FROM target_trial_results WHERE participantId = :participantId ORDER BY trialNumber ASC")
    suspend fun getTrialsForParticipant(participantId: String): List<TargetTrialResult>

    @Query("SELECT * FROM target_trial_results WHERE isUploaded = 0")
    suspend fun getUnsyncedTrials(): List<TargetTrialResult>

    @Query("UPDATE target_trial_results SET isUploaded = 1 WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Int>)

    @Query("SELECT COUNT(*) FROM target_trial_results WHERE participantId = :participantId")
    suspend fun getTrialCountForParticipant(participantId: String): Int

    @Query("DELETE FROM target_trial_results WHERE participantId = :participantId")
    suspend fun deleteTrialsForParticipant(participantId: String)
}
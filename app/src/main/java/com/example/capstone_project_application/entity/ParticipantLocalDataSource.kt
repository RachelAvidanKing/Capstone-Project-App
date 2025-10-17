package com.example.capstone_project_application.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object (DAO) for the Participant entity.
 */
@Dao
interface ParticipantLocalDataSource {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(participant: Participant)

    @Query("SELECT * FROM participants WHERE participantId = :participantId")
    suspend fun getParticipant(participantId: String): Participant?

    @Query("SELECT * FROM participants WHERE isUploaded = 0")
    suspend fun getUnsyncedParticipants(): List<Participant>

    @Query("UPDATE participants SET isUploaded = 1 WHERE participantId IN (:participantIds)")
    suspend fun markAsUploaded(participantIds: List<String>)

    @Query("SELECT * FROM participants")
    suspend fun getAllParticipants(): List<Participant>

    @Update
    suspend fun update(participant: Participant)
}
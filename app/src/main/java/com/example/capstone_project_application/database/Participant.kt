package com.example.capstone_project_application.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents participant information stored locally.
 */
@Entity(tableName = "participants")
data class Participant(
    @PrimaryKey
    val participantId: String, // Unique identifier (could be generated UUID)
    val age: Int,
    val gender: String, // Could be enum: "Male", "Female", "Other", "Prefer not to say"
    val hasGlasses: Boolean,
    val hasAttentionDeficit: Boolean,
    val consentGiven: Boolean,
    val registrationTimestamp: Long,
    var isUploaded: Boolean = false
)
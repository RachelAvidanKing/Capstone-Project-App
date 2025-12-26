package com.example.capstone_project_application.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents participant information stored locally.
 */
@Entity(tableName = "participants")
data class Participant(
    @PrimaryKey
    val participantId: String,
    val age: Int,
    val gender: String,
    val hasGlasses: Boolean,
    val hasAttentionDeficit: Boolean,
    val consentGiven: Boolean,
    val registrationTimestamp: Long,
    var jndThreshold: Int? = null, // The highest hue number where participant gets 50% or less correct
    var isUploaded: Boolean = false // Flag to track if the participant data has been uploaded
)
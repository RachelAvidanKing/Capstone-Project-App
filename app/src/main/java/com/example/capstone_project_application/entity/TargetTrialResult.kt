package com.example.capstone_project_application.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single trial result in the target experiment.
 */
@Entity(tableName = "target_trial_results")
data class TargetTrialResult(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val participantId: String,
    val trialNumber: Int,
    val trialType: String, // "PRE_JND", "PRE_SUPRA", "CONCURRENT_SUPRA"
    val targetIndex: Int, // 0-3 for the four circles
    val selectedIndex: Int, // Which circle the user clicked
    val isCorrect: Boolean,

    // Timing information
    val trialStartTimestamp: Long, // When the trial started
    val firstMovementTimestamp: Long?, // When stylus first moved from origin
    val targetReachedTimestamp: Long?, // When correct target area was reached
    val responseTimestamp: Long, // When user clicked

    // Calculated durations (in milliseconds)
    val reactionTime: Long?, // Time from go beep to first movement
    val movementTime: Long?, // Time from first movement to reaching target
    val totalResponseTime: Long, // Time from go beep to click

    // Movement tracking
    val movementPath: String, // JSON array of [x,y,timestamp] coordinates
    val pathLength: Float, // Total distance traveled
    val averageSpeed: Float, // Average speed during movement

    // Visual stimulus information
    val initialHue: Int,
    val finalHue: Int?, // Only for PRE_JND trials
    val goBeepTimestamp: Long, // When the 4th beep played

    var isUploaded: Boolean = false
)
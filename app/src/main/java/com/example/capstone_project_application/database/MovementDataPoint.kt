package com.example.capstoneprojectapplication.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single row in the local movement_data table.
 * This is the data model for the on-device Room database.
 */
@Entity(tableName = "movement_data")
data class MovementDataPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0, // Primary key for the local database

    val timestamp: Long, // Use milliseconds since epoch for easy sorting
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,

    // Accelerometer or gyroscope data
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,

    // A flag to know if this data has been uploaded to the cloud yet.
    var isUploaded: Boolean = false
)

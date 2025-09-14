package com.example.capstone_project_application.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The main Room database class for the application.
 * This class ties together the entities and DAOs.
 */
@Database(
    entities = [MovementDataPoint::class, Participant::class],
    version = 2, // Incremented version to handle schema change
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movementDataDao(): MovementDataDao
    abstract fun participantDao(): ParticipantDao

    companion object {
        // Volatile ensures that the INSTANCE is always up-to-date and the same for all execution threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // If the INSTANCE is not null, then return it, otherwise create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "capstone_movement_database"
                )
                    // Wipes and rebuilds instead of migrating if no Migration object is provided.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
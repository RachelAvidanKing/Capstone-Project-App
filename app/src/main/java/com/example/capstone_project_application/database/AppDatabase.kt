package com.example.capstone_project_application.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The main Room database class for the application.
 * This class ties together the entities and DAOs.
 */
@Database(
    entities = [MovementDataPoint::class, Participant::class],
    version = 3, // CHANGED: Incremented version from 2 to 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movementDataDao(): MovementDataDao
    abstract fun participantDao(): ParticipantDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2 (existing)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the new jndThreshold column
                db.execSQL("ALTER TABLE participants ADD COLUMN jndThreshold INTEGER")
            }
        }

        // ADDED: New migration from version 2 to 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the new isUploaded column
                db.execSQL("ALTER TABLE participants ADD COLUMN isUploaded INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "capstone_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // CHANGED: Add the new migration here
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
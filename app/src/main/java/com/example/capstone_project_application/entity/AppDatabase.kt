package com.example.capstone_project_application.entity

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
    entities = [MovementDataPoint::class, Participant::class, TargetTrialResult::class],
    version = 4, // CHANGED: Incremented version from 3 to 4
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun movementDataDao(): MovementLocalDataSource
    abstract fun participantDao(): ParticipantLocalDataSource
    abstract fun targetTrialDao(): TargetTrialLocalDataSource

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE participants ADD COLUMN jndThreshold INTEGER")
            }
        }

        // Migration from version 2 to 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE participants ADD COLUMN isUploaded INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration from version 3 to 4 - Add target_trial_results table
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS target_trial_results (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        participantId TEXT NOT NULL,
                        trialNumber INTEGER NOT NULL,
                        trialType TEXT NOT NULL,
                        targetIndex INTEGER NOT NULL,
                        selectedIndex INTEGER NOT NULL,
                        isCorrect INTEGER NOT NULL,
                        trialStartTimestamp INTEGER NOT NULL,
                        firstMovementTimestamp INTEGER,
                        targetReachedTimestamp INTEGER,
                        responseTimestamp INTEGER NOT NULL,
                        reactionTime INTEGER,
                        movementTime INTEGER,
                        totalResponseTime INTEGER NOT NULL,
                        movementPath TEXT NOT NULL,
                        pathLength REAL NOT NULL,
                        averageSpeed REAL NOT NULL,
                        initialHue INTEGER NOT NULL,
                        finalHue INTEGER,
                        goBeepTimestamp INTEGER NOT NULL,
                        isUploaded INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "capstone_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
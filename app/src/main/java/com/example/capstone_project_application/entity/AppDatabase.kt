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
    version = 5, // CHANGED: Incremented version from 4 to 5
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


        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: Create a new temporary table with the updated schema (without selectedIndex and isCorrect)
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS target_trial_results_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                participantId TEXT NOT NULL,
                trialNumber INTEGER NOT NULL,
                trialType TEXT NOT NULL,
                targetIndex INTEGER NOT NULL,
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

                // Step 2: Copy data from old table to new table (excluding selectedIndex and isCorrect columns)
                db.execSQL("""
            INSERT INTO target_trial_results_new 
            (id, participantId, trialNumber, trialType, targetIndex, 
             trialStartTimestamp, firstMovementTimestamp, targetReachedTimestamp, 
             responseTimestamp, reactionTime, movementTime, totalResponseTime,
             movementPath, pathLength, averageSpeed, initialHue, finalHue, 
             goBeepTimestamp, isUploaded)
            SELECT 
             id, participantId, trialNumber, trialType, targetIndex,
             trialStartTimestamp, firstMovementTimestamp, targetReachedTimestamp,
             responseTimestamp, reactionTime, movementTime, totalResponseTime,
             movementPath, pathLength, averageSpeed, initialHue, finalHue,
             goBeepTimestamp, isUploaded
            FROM target_trial_results
        """)

                // Step 3: Drop the old table
                db.execSQL("DROP TABLE target_trial_results")

                // Step 4: Rename the new table to the original name
                db.execSQL("ALTER TABLE target_trial_results_new RENAME TO target_trial_results")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "capstone_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
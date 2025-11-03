package com.example.capstone_project_application.control

import android.content.Context
import android.util.Log
import com.example.capstone_project_application.entity.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class for debugging data storage and uploads.
 * Added to help diagnose issues.
 */
object DebugHelper {
    private const val TAG = "DebugHelper"

    /**
     * Print a summary of all data in the local database
     */
    suspend fun printDatabaseSummary(context: Context) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)

            Log.d(TAG, "════════════════════════════════════════")
            Log.d(TAG, "DATABASE SUMMARY")
            Log.d(TAG, "════════════════════════════════════════")

            // Participants
            val allParticipants = db.participantDao().getAllParticipants()
            Log.d(TAG, "PARTICIPANTS: ${allParticipants.size} total")
            for (p in allParticipants) {
                Log.d(TAG, "  - ${p.participantId.take(8)}...: age=${p.age}, jnd=${p.jndThreshold}, uploaded=${p.isUploaded}")
            }

            val unsyncedParticipants = db.participantDao().getUnsyncedParticipants()
            Log.d(TAG, "  → ${unsyncedParticipants.size} unsynced")

            // Target Trials
            val allTrials = mutableListOf<com.example.capstone_project_application.entity.TargetTrialResult>()
            for (p in allParticipants) {
                val trials = db.targetTrialDao().getTrialsForParticipant(p.participantId)
                allTrials.addAll(trials)
            }
            Log.d(TAG, "TARGET TRIALS: ${allTrials.size} total")

            val unsyncedTrials = db.targetTrialDao().getUnsyncedTrials()
            Log.d(TAG, "  → ${unsyncedTrials.size} unsynced")
            for (trial in unsyncedTrials.take(3)) {
                Log.d(TAG, "    - Trial ${trial.trialNumber}: ${trial.trialType}, correct=${trial.isCorrect}, RT=${trial.reactionTime}ms")
            }
            if (unsyncedTrials.size > 3) {
                Log.d(TAG, "    ... and ${unsyncedTrials.size - 3} more")
            }

            // Movement Data
            val unsyncedMovement = db.movementDataDao().getAllUnsyncedData()
            Log.d(TAG, "MOVEMENT DATA: ${unsyncedMovement.size} unsynced")

            Log.d(TAG, "════════════════════════════════════════")
        }
    }

    /**
     * Force an immediate upload and log the process
     */
    suspend fun forceUploadWithLogging(context: Context) {
        Log.d(TAG, "→→→ FORCING IMMEDIATE UPLOAD →→→")
        printDatabaseSummary(context)
        WorkScheduler.triggerImmediateUpload(context)
        Log.d(TAG, "→→→ Upload request sent to WorkManager →→→")
    }
}
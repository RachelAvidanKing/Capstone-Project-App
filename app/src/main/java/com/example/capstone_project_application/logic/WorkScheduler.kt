package com.example.capstone_project_application.logic

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.capstone_project_application.database.DataUploaderWorker

object WorkScheduler {
    private const val TAG = "WorkScheduler"

    /**
     * Enqueues a one-time work request to upload unsynced data to Firebase.
     * The job will run immediately if network constraints are met.
     *
     * @param context Application context needed to get WorkManager instance.
     */
    fun triggerImmediateUpload(context: Context) {
        Log.d(TAG, "Triggering immediate data upload via Utility.")

        val uploadConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<DataUploaderWorker>()
            .setConstraints(uploadConstraints)
            .build()

        // Use a unique tag if you want to observe or cancel this specific work later
        WorkManager.getInstance(context).enqueue(uploadWorkRequest)
    }
}
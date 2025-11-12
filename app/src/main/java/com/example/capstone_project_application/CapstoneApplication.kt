package com.example.capstone_project_application

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.capstone_project_application.control.DataUploadWorker
import com.example.capstone_project_application.entity.AppDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import java.util.concurrent.TimeUnit


/**
 * Custom Application class for initializing app-wide components
 */
class CapstoneApplication : Application() {

    // Using lazy initialization so the database is only created when first accessed
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        initializeAppCheck()
        setupPeriodicUploadWorker()
    }

    private fun initializeAppCheck() {
        // 1. Initialize the Firebase app
        FirebaseApp.initializeApp(this)

        // 2. Get the App Check instance
        val firebaseAppCheck = FirebaseAppCheck.getInstance()


        if (com.example.capstone_project_application.BuildConfig.DEBUG) {
            // Use the Debug Provider for emulators and development builds
            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d("AppCheck", "Using DebugAppCheckProviderFactory for development.")

            // Force token fetch to generate the logcat token for registration
            firebaseAppCheck.getToken(true)
                .addOnSuccessListener {
                    // This is where we'll see the token in logcat
                    Log.d("AppCheck", "Debug token generated in logcat.")
                }
                .addOnFailureListener { e ->
                    Log.e("AppCheck", "Error generating debug token.", e)
                }

        } else {
            // Use Play Integrity for release/production builds
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d("AppCheck", "Using PlayIntegrityAppCheckProviderFactory for release.")
        }
    }

    /**
     * his function schedules DataUploadWorker to run periodically
     * in the background, even if the app is closed.
     */
    private fun setupPeriodicUploadWorker() {
        // Define constraints: Only run when connected to the internet
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create a periodic request to run our worker every 6 hours
        // WorkManager is smart and will run this at an optimal time
        val repeatingRequest = PeriodicWorkRequestBuilder<DataUploadWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        // Schedule the work, ensuring only one instance of this "periodic-sync"
        // is active at any time.
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "periodic-sync",
            ExistingPeriodicWorkPolicy.KEEP, // Keep the existing work if one is already scheduled
            repeatingRequest
        )

        Log.d("AppCheck", "Periodic background data sync scheduled.")
    }
}
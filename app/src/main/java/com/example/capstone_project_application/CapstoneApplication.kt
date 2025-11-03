package com.example.capstone_project_application

import android.app.Application
import android.util.Log
import com.example.capstone_project_application.entity.AppDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Custom Application class for initializing app-wide components
 */
class CapstoneApplication : Application() {

    // Using lazy initialization so the database is only created when first accessed
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        initializeAppCheck()

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
                    // This is where you'll see the token in logcat
                    Log.d("AppCheck", "Debug token generated in logcat.")
                }
                .addOnFailureListener { e ->
                    Log.e("AppCheck", "Error generating debug token.", e)
                }

        } else {
            // Use Play Integrity for release/production builds (your research tablet)
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.d("AppCheck", "Using PlayIntegrityAppCheckProviderFactory for release.")
        }
    }
}
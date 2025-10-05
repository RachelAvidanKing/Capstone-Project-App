package com.example.capstone_project_application

import android.app.Application
import com.example.capstone_project_application.database.AppDatabase

/**
 * Custom Application class for initializing app-wide components
 */
class CapstoneApplication : Application() {

    // Using lazy initialization so the database is only created when first accessed
    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // Any other app-wide initialization can go here
    }
}
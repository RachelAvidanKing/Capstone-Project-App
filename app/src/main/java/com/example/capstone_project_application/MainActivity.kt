package com.example.capstone_project_application

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        //comment
    }
}

/*
// This would be inside your MainActivity.kt file
// app/src/main/java/com/example/capstoneprojectapplication/MainActivity.kt

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope // Important import
import com.example.capstoneprojectapplication.database.AppDatabase
import com.example.capstoneprojectapplication.database.MovementDataPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // 1. Get a reference to the database.
    // 'lazy' means the database will only be created the first time you actually use it.
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Your UI layout file

        // Example: Let's insert a new data point into the database.
        // This is how you would call it from your activity.
        insertNewMovementData()
    }

    private fun insertNewMovementData() {
        // 2. Launch a coroutine to run the database operation on a background thread.
        // lifecycleScope is tied to your Activity's lifecycle and handles cancellation for you.
        lifecycleScope.launch(Dispatchers.IO) {
            // Dispatchers.IO is the thread pool specifically for I/O operations like database access.

            Log.d("MainActivity", "Inserting a new data point...")

            // 3. Create a sample data point.
            val newDataPoint = MovementDataPoint(
                timestamp = System.currentTimeMillis(),
                latitude = 32.79,
                longitude = 34.98,
                altitude = 100.0,
                speed = 5.0f,
                accelX = 0.1f,
                accelY = 9.8f,
                accelZ = 0.5f
            )

            // 4. Use the DAO to insert the data.
            database.movementDataDao().insert(newDataPoint)

            Log.d("MainActivity", "New data point inserted successfully!")
        }
    }
}

 */
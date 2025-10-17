package com.example.capstone_project_application

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.capstone_project_application.database.AppDatabase
import com.example.capstone_project_application.database.DataRepository
import com.example.capstone_project_application.database.DataUploadWorker
import com.example.capstone_project_application.logic.ThresholdActivity
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import android.content.Intent


class RegistrationActivity : AppCompatActivity() {

    // Get a reference to the database and repository
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    // UI Components
    private lateinit var rgGender: RadioGroup
    private lateinit var rgAge: RadioGroup
    private lateinit var rgEye: RadioGroup
    private lateinit var rgAttentionDeficit: RadioGroup // Added the RadioGroup for attention deficit
    private lateinit var btnNext: Button // Reference for the button from XML

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        initializeViews()

        // Setup periodic data upload to Firebase
        setupPeriodicDataUpload()

        // Check if participant is already registered
        checkParticipantRegistration()
    }

    private fun initializeViews() {
        rgGender = findViewById(R.id.rgGender)
        rgAge = findViewById(R.id.rgAge)
        rgEye = findViewById(R.id.rgEye)
        rgAttentionDeficit = findViewById(R.id.rgAttentionDeficit) // Initialized the new RadioGroup
        btnNext = findViewById(R.id.btnNext) // Get the button from the XML layout

        // Set the click listener on the button from the layout
        btnNext.setOnClickListener {
            handleNextButtonClick()
        }
    }

    private fun handleNextButtonClick() {
        val selectedGender = getSelectedGender()
        val selectedAge = getSelectedAge()
        val hasGlasses = getSelectedEyeIssue()
        val hasAttentionDeficit = getSelectedAttentionDeficit() // Get the new value

        // Update the null check to include the new value
        if (selectedGender == null || selectedAge == null || hasGlasses == null || hasAttentionDeficit == null) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        // Register participant
        lifecycleScope.launch {
            try {
                val participantId = repository.registerParticipant(
                    age = selectedAge,
                    gender = selectedGender,
                    hasGlasses = hasGlasses,
                    hasAttentionDeficit = hasAttentionDeficit,
                    consentGiven = true // Assuming consent is given when they proceed
                )

                Log.d("RegistrationActivity", "Participant registered successfully with demographics. ID: $participantId")
                Toast.makeText(this@RegistrationActivity, "Registration successful!", Toast.LENGTH_LONG).show()
                //
                val intent = Intent(this@RegistrationActivity, ThresholdActivity::class.java)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Log.e("RegistrationActivity", "Error registering participant", e)
                Toast.makeText(this@RegistrationActivity, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getSelectedGender(): String? {
        return when (rgGender.checkedRadioButtonId) {
            R.id.rbMale -> "Male"
            R.id.rbFemale -> "Female"
            else -> null
        }
    }

    private fun getSelectedAge(): Int? {
        return when (rgAge.checkedRadioButtonId) {
            R.id.rbAge1 -> 22 // Average of 18-25
            R.id.rbAge2 -> 30 // Average of 26-35
            R.id.rbAge3 -> 40 // Average of 36-45
            R.id.rbAge4 -> 53 // Average of 46-60
            R.id.rbAge5 -> 65 // Average of 60+
            else -> null
        }
    }

    private fun getSelectedEyeIssue(): Boolean? {
        return when (rgEye.checkedRadioButtonId) {
            R.id.rbGlasses -> true
            R.id.rbNoGlasses -> false
            else -> null
        }
    }

    // New function to get the attention deficit selection
    private fun getSelectedAttentionDeficit(): Boolean? {
        return when (rgAttentionDeficit.checkedRadioButtonId) {
            R.id.rbAddYes -> true
            R.id.rbAddNo -> false
            else -> null
        }
    }

    private fun checkParticipantRegistration() {
        lifecycleScope.launch {
            val isRegistered = repository.isParticipantRegistered()
            if (isRegistered) {
                val participant = repository.getCurrentParticipant()
                participant?.let {
                    Log.d("RegistrationActivity", "Participant already registered: ${it.participantId}")
                    Toast.makeText(this@RegistrationActivity, "Welcome back! You're already registered.", Toast.LENGTH_SHORT).show()
                    // You might want to navigate to the next screen here as well
                }
            }
        }
    }

    private fun setupPeriodicDataUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = PeriodicWorkRequestBuilder<DataUploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DataUploadWork",
            ExistingPeriodicWorkPolicy.KEEP,
            uploadWorkRequest
        )
    }
}
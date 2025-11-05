package com.example.capstone_project_application.boundary

import android.content.Intent
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
import com.example.capstone_project_application.R
import com.example.capstone_project_application.entity.AppDatabase
import com.example.capstone_project_application.control.DataRepository
import com.example.capstone_project_application.control.DataUploadWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class RegistrationActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    private lateinit var rgGender: RadioGroup
    private lateinit var rgAge: RadioGroup
    private lateinit var rgEye: RadioGroup
    private lateinit var rgAttentionDeficit: RadioGroup
    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registration)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializeViews()
        setupPeriodicDataUpload()
    }

    private fun initializeViews() {
        rgGender = findViewById(R.id.rgGender)
        rgAge = findViewById(R.id.rgAge)
        rgEye = findViewById(R.id.rgEye)
        rgAttentionDeficit = findViewById(R.id.rgAttentionDeficit)
        btnNext = findViewById(R.id.btnNext)

        btnNext.setOnClickListener {
            handleNextButtonClick()
        }
    }

    private fun handleNextButtonClick() {
        val selectedGender = getSelectedGender()
        val selectedAge = getSelectedAge()
        val hasGlasses = getSelectedEyeIssue()
        val hasAttentionDeficit = getSelectedAttentionDeficit()

        if (selectedGender == null || selectedAge == null || hasGlasses == null || hasAttentionDeficit == null) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Register participant using the ID that was set in LoginActivity
                val participantId = repository.registerParticipant(
                    age = selectedAge,
                    gender = selectedGender,
                    hasGlasses = hasGlasses,
                    hasAttentionDeficit = hasAttentionDeficit,
                    consentGiven = true
                )

                Log.d("RegistrationActivity", "Participant registered successfully. ID: $participantId")
                Toast.makeText(this@RegistrationActivity, "Registration successful!", Toast.LENGTH_LONG).show()

                val intent = Intent(this@RegistrationActivity, ThresholdActivity::class.java)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Log.e("RegistrationActivity", "Error registering participant", e)
                Toast.makeText(
                    this@RegistrationActivity,
                    "Registration failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
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
            R.id.rbAge1 -> 22
            R.id.rbAge2 -> 30
            R.id.rbAge3 -> 40
            R.id.rbAge4 -> 53
            R.id.rbAge5 -> 65
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

    private fun getSelectedAttentionDeficit(): Boolean? {
        return when (rgAttentionDeficit.checkedRadioButtonId) {
            R.id.rbAddYes -> true
            R.id.rbAddNo -> false
            else -> null
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
package com.example.capstone_project_application.boundary

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.capstone_project_application.R
import com.example.capstone_project_application.control.DataRepository
import com.example.capstone_project_application.entity.AppDatabase
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etIdNumber = findViewById<EditText>(R.id.etIdNumber)
        val rgRegistered = findViewById<RadioGroup>(R.id.rgRegistered)
        val btnContinue = findViewById<Button>(R.id.btnContinue)

        btnContinue.setOnClickListener {
            val idNumber = etIdNumber.text.toString().trim()

            // Validate ID is exactly 9 digits
            if (idNumber.isEmpty()) {
                Toast.makeText(this, "Please enter your ID number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (idNumber.length != 9 || !idNumber.all { it.isDigit() }) {
                Toast.makeText(this, "ID must be exactly 9 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check if registration option selected
            val selectedOptionId = rgRegistered.checkedRadioButtonId
            if (selectedOptionId == -1) {
                Toast.makeText(this, "Please select Yes or No", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedRadio = findViewById<RadioButton>(selectedOptionId)
            val isReturningUser = selectedRadio.id == R.id.rbYes

            // Disable button to prevent multiple clicks
            btnContinue.isEnabled = false

            lifecycleScope.launch {
                handleLogin(idNumber, isReturningUser)
            }
        }
    }

    private suspend fun handleLogin(participantId: String, isReturningUser: Boolean) {
        try {
            if (isReturningUser) {
                // Try to fetch participant from Firebase
                Toast.makeText(this, "Checking registration...", Toast.LENGTH_SHORT).show()

                val participant = repository.fetchParticipantFromFirebase(participantId)

                if (participant == null) {
                    // Participant not found in Firebase
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "No registration found. Please select 'No' to register.",
                            Toast.LENGTH_LONG
                        ).show()
                        findViewById<Button>(R.id.btnContinue).isEnabled = true
                    }
                    return
                }

                // Save participant locally and set as current
                repository.setExistingParticipant(participant)

                runOnUiThread {
                    if (participant.jndThreshold != null) {
                        // Has completed JND test, go to target activity
                        Toast.makeText(
                            this,
                            "Welcome back! Continuing to experiment...",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToTargetActivity()
                    } else {
                        // Registered but hasn't completed JND test
                        Toast.makeText(
                            this,
                            "Welcome back! Please complete the threshold test.",
                            Toast.LENGTH_SHORT
                        ).show()
                        navigateToThresholdActivity()
                    }
                }
            } else {
                // New user - check if ID already exists
                val existingParticipant = repository.fetchParticipantFromFirebase(participantId)

                if (existingParticipant != null) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "This ID is already registered. Please select 'Yes' to continue.",
                            Toast.LENGTH_LONG
                        ).show()
                        findViewById<Button>(R.id.btnContinue).isEnabled = true
                    }
                    return
                }

                // New user - set participant ID and go to registration
                repository.setParticipantId(participantId)

                runOnUiThread {
                    Toast.makeText(this, "Proceeding to registration...", Toast.LENGTH_SHORT).show()
                    navigateToRegistration()
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                findViewById<Button>(R.id.btnContinue).isEnabled = true
            }
        }
    }

    private fun navigateToRegistration() {
        val intent = Intent(this, RegistrationActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToThresholdActivity() {
        val intent = Intent(this, ThresholdActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToTargetActivity() {
        val intent = Intent(this, TargetActivity::class.java)
        startActivity(intent)
        finish()
    }
}
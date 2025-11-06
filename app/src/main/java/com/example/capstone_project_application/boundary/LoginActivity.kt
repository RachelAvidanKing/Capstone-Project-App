package com.example.capstone_project_application.boundary

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
        val btnExitApp = findViewById<Button>(R.id.btnExitApp)

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

            btnContinue.isEnabled = false

            lifecycleScope.launch {
                handleLogin(idNumber, isReturningUser)
            }
        }

        btnExitApp.setOnClickListener {
            showExitAppDialog()
        }
    }

    override fun onBackPressed() {
        showExitAppDialog()
        // Don't call super.onBackPressed() because we're handling it with the dialog
    }

    private fun showExitAppDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Application?")
            .setMessage("Are you sure you want to exit the app?")
            .setPositiveButton("Exit") { _, _ ->
                finishAffinity() // Closes the app completely
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun handleLogin(participantId: String, isReturningUser: Boolean) {
        try {
            if (isReturningUser) {
                // Show loading with progress dialog instead of toast
                var progressDialog: AlertDialog? = null
                runOnUiThread {
                    progressDialog = AlertDialog.Builder(this)
                        .setTitle("Please Wait")
                        .setMessage("Checking registration...")
                        .setCancelable(false)
                        .create()
                    progressDialog?.show()
                }

                val participant = repository.fetchParticipantFromFirebase(participantId)

                runOnUiThread {
                    progressDialog?.dismiss()
                }

                if (participant == null) {
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

                // Check if experiment is already complete
                val hasCompleted = repository.hasCompletedExperiment(participantId)
                if (hasCompleted) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "You have already completed this experiment. Thank you for your participation!",
                            Toast.LENGTH_LONG
                        ).show()
                        findViewById<Button>(R.id.btnContinue).isEnabled = true
                    }
                    return
                }

                repository.setExistingParticipant(participant)

                runOnUiThread {
                    // Check what stage they're at
                    if (participant.jndThreshold != null) {
                        // Has JND but hasn't completed target trials - go to target
                        navigateToTargetActivity()
                    } else {
                        // Has demographics but no JND - go to threshold
                        // They must redo threshold test from beginning
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

                repository.setParticipantId(participantId)

                runOnUiThread {
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
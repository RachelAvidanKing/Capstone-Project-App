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
        // REMOVED: RadioGroup rgRegistered = findViewById<RadioGroup>(R.id.rgRegistered)
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

            btnContinue.isEnabled = false

            lifecycleScope.launch {
                handleLogin(idNumber)
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


    private suspend fun handleLogin(participantId: String) {
        // Show loading with progress dialog
        var progressDialog: AlertDialog? = null
        runOnUiThread {
            progressDialog = AlertDialog.Builder(this)
                .setTitle("Please Wait")
                .setMessage("Checking registration...")
                .setCancelable(false)
                .create()
            progressDialog?.show()
        }

        try {
            val hasCompleted = repository.hasCompletedExperiment(participantId)

            if (hasCompleted) {
                runOnUiThread {
                    progressDialog?.dismiss()
                    navigateToCompletionActivity()
                }
                return
            }

            val participant = repository.fetchParticipantFromFirebase(participantId)

            runOnUiThread {
                progressDialog?.dismiss()
            }

            if (participant == null) {
                repository.setParticipantId(participantId)
                runOnUiThread {
                    navigateToRegistration()
                }
                return
            }

            repository.setExistingParticipant(participant)

            // Check what stage they're at
            if (participant.jndThreshold != null) {
                // Has JND but hasn't completed target trials - go to target
                runOnUiThread { navigateToTargetActivity() }
            } else {
                // Has demographics but no JND - go to threshold (redo threshold test)
                runOnUiThread { navigateToThresholdActivity() }
            }

        } catch (e: Exception) {
            runOnUiThread {
                progressDialog?.dismiss()
                Toast.makeText(
                    this,
                    "A connection error occurred. Please try again.",
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

    private fun navigateToCompletionActivity() {
        val intent = Intent(this, CompletionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
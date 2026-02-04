package com.example.capstone_project_application.boundary

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.capstone_project_application.R
import com.example.capstone_project_application.control.DataRepository
import com.example.capstone_project_application.entity.AppDatabase
import kotlinx.coroutines.launch

/**
 * Entry point activity for participant authentication.
 *
 * This activity handles participant login by validating their ID number
 * and determining their experiment progress state:
 * - New participants → Registration
 * - Returning participants → Resume at appropriate stage
 * - Completed participants → Show completion screen
 *
 * ## ID Validation:
 * - Must be exactly 9 digits
 * - Must contain only numeric characters
 *
 * ## Navigation Logic:
 * - No record found → [RegistrationActivity]
 * - Has demographics, no JND → [ThresholdActivity]
 * - Has JND, no target trials → [TargetActivity]
 * - All trials complete → [CompletionActivity]
 */
class LoginActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    private lateinit var etIdNumber: EditText
    private lateinit var btnContinue: Button
    private lateinit var btnExitApp: Button

    companion object {
        private const val ID_LENGTH = 9
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initializeViews()
        setupClickListeners()
    }

    /**
     * Initializes view references.
     */
    private fun initializeViews() {
        etIdNumber = findViewById(R.id.etIdNumber)
        btnContinue = findViewById(R.id.btnContinue)
        btnExitApp = findViewById(R.id.btnExitApp)
    }

    /**
     * Sets up click listeners for buttons.
     */
    private fun setupClickListeners() {
        btnContinue.setOnClickListener {
            handleContinueClick()
        }

        btnExitApp.setOnClickListener {
            showExitAppDialog()
        }
    }

    /**
     * Handles the continue button click event.
     * Validates input and initiates login process.
     */
    private fun handleContinueClick() {
        val idNumber = etIdNumber.text.toString().trim()

        if (!validateIdInput(idNumber)) {
            return
        }

        btnContinue.isEnabled = false

        lifecycleScope.launch {
            handleLogin(idNumber)
        }
    }

    /**
     * Validates the participant ID input.
     *
     * @param idNumber The ID number to validate
     * @return true if valid, false otherwise (with toast message)
     */
    private fun validateIdInput(idNumber: String): Boolean {
        return when {
            idNumber.isEmpty() -> {
                showToast("Please enter your ID number")
                false
            }
            idNumber.length != ID_LENGTH || !idNumber.all { it.isDigit() } -> {
                showToast("ID must be exactly $ID_LENGTH digits")
                false
            }
            else -> true
        }
    }

    /**
     * Handles back press by showing exit confirmation dialog.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showExitAppDialog()
    }

    /**
     * Shows a confirmation dialog for exiting the application.
     */
    private fun showExitAppDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Application?")
            .setMessage("Are you sure you want to exit the app?")
            .setPositiveButton("Exit") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Handles the login process by checking participant status.
     * Checks local database first, then Firebase if online.
     *
     * @param participantId The validated participant ID
     */
    private suspend fun handleLogin(participantId: String) {
        val progressDialog = showProgressDialog()

        try {
            repository.setParticipantId(participantId)

            // STEP 1: Check local database first (offline-first approach)
            val localParticipant = repository.getCurrentParticipant()

            if (localParticipant != null) {
                Log.d(TAG, "Participant found in local database")

                val localTrialCount = database.targetTrialDao()
                    .getTrialCountForParticipant(participantId)

                dismissDialog(progressDialog)

                if (localTrialCount >= 15) {
                    navigateToCompletionActivity()
                    return
                } else {
                    navigateBasedOnProgress(localParticipant.jndThreshold)
                    return
                }
            }

            // STEP 2: Not found locally - try Firebase (requires network)
            Log.d(TAG, "Participant not found locally, checking Firebase...")

            try {
                val hasCompleted = repository.hasCompletedExperiment(participantId)
                if (hasCompleted) {
                    dismissDialog(progressDialog)
                    navigateToCompletionActivity()
                    return
                }

                val firebaseParticipant = repository.fetchParticipantFromFirebase(participantId)

                dismissDialog(progressDialog)

                if (firebaseParticipant == null) {
                    navigateToRegistration()
                } else {
                    repository.setExistingParticipant(firebaseParticipant)
                    navigateBasedOnProgress(firebaseParticipant.jndThreshold)
                }

            } catch (networkException: Exception) {
                Log.w(TAG, "Could not reach Firebase (offline?): ${networkException.message}")
                dismissDialog(progressDialog)

                showToast("Working offline. Starting new registration.", Toast.LENGTH_LONG)
                navigateToRegistration()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during login", e)
            dismissDialog(progressDialog)
            handleLoginError()
        }
    }

    /**
     * Navigates to the appropriate screen based on experiment progress.
     *
     * @param jndThreshold The participant's JND threshold (null if not completed)
     */
    private fun navigateBasedOnProgress(jndThreshold: Int?) {
        if (jndThreshold != null) {
            // Has JND threshold - continue to target trials
            navigateToTargetActivity()
        } else {
            // Has demographics but no JND - redo threshold test
            navigateToThresholdActivity()
        }
    }

    /**
     * Shows a progress dialog during login.
     *
     * @return The AlertDialog instance
     */
    private fun showProgressDialog(): AlertDialog {
        return AlertDialog.Builder(this)
            .setTitle("Please Wait")
            .setMessage("Checking registration...")
            .setCancelable(false)
            .create()
            .apply { show() }
    }

    /**
     * Dismisses a dialog on the UI thread.
     *
     * @param dialog The dialog to dismiss
     */
    private fun dismissDialog(dialog: AlertDialog?) {
        runOnUiThread {
            dialog?.dismiss()
        }
    }

    /**
     * Handles login errors by showing a message and re-enabling the continue button.
     */
    private fun handleLoginError() {
        runOnUiThread {
            showToast("A connection error occurred. Please try again.", Toast.LENGTH_LONG)
            btnContinue.isEnabled = true
        }
    }

    // ===========================
    // Navigation Methods
    // ===========================

    /**
     * Navigates to the registration activity for new participants.
     */
    private fun navigateToRegistration() {
        startActivityAndFinish(RegistrationActivity::class.java)
    }

    /**
     * Navigates to the JND threshold explanation screen.
     */
    private fun navigateToThresholdActivity() {
        startActivityAndFinish(ExplainJNDActivity::class.java)
    }

    /**
     * Navigates to the target trial explanation screen.
     */
    private fun navigateToTargetActivity() {
        startActivityAndFinish(ExplainTargetActivity::class.java)
    }

    /**
     * Navigates to the completion screen with cleared back stack.
     */
    private fun navigateToCompletionActivity() {
        val intent = Intent(this, CompletionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    /**
     * Helper method to start an activity and finish current one.
     *
     * @param activityClass The activity class to start
     */
    private fun startActivityAndFinish(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
        finish()
    }

    /**
     * Helper method to show toast messages.
     *
     * @param message The message to display
     * @param duration Toast duration (default: SHORT)
     */
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }
}
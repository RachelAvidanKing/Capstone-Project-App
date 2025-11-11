package com.example.capstone_project_application.boundary

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.capstone_project_application.databinding.ActivityThresholdBinding
import com.example.capstone_project_application.R
import com.example.capstone_project_application.control.ThresholdController
import com.example.capstone_project_application.control.ThresholdTrialState
import com.example.capstone_project_application.entity.AppDatabase
import com.example.capstone_project_application.control.DataRepository
import com.example.capstone_project_application.control.InactivityHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Activity for conducting Just Noticeable Difference (JND) threshold determination.
 *
 * This activity presents pairs of color patches to the participant and asks them
 * to identify which patch is more blue. The test runs for 100 trials and calculates
 * a threshold based on performance.
 *
 * ## Test Algorithm:
 * - Reference hue: 140 (base color)
 * - Test hues: 141, 142, 143, 144, 145, 150, 155, 160, 165, 175
 * - Each hue appears max 10 times, max 5 times on left
 * - Threshold = highest hue with ≤50% correct responses
 *
 * ## User Flow:
 * 1. Participant sees two color patches (A and B)
 * 2. Participant taps the patch they think is more blue
 * 3. After 100 trials, threshold is calculated
 * 4. Threshold is saved and uploaded
 * 5. Participant proceeds to target trials
 *
 * ## Exit Behavior:
 * - Before completion: Warns that progress won't be saved
 * - After completion: Allows exit while preserving threshold
 *
 * Part of the Boundary layer in Entity-Boundary-Control pattern.
 */
class ThresholdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdBinding
    private lateinit var controller: ThresholdController

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }
    private lateinit var inactivityHelper: InactivityHelper

    companion object {
        private const val TAG = "ThresholdActivity"
    }

    // Color mapping for different hues
    private val hueToColorRes = mapOf(
        141 to R.color.blue_141, 142 to R.color.blue_142, 143 to R.color.blue_143,
        144 to R.color.blue_144, 145 to R.color.blue_145, 150 to R.color.blue_150,
        155 to R.color.blue_155, 160 to R.color.blue_160, 165 to R.color.blue_165,
        175 to R.color.blue_175, 140 to R.color.blue_140
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThresholdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeController()
        initializeInactivityHelper()
        setupClickListeners()
        startNextTrial()
    }

    /**
     * Initializes the threshold controller.
     */
    private fun initializeController() {
        controller = ThresholdController()
    }

    /**
     * Initializes the inactivity helper.
     */
    private fun initializeInactivityHelper() {
        inactivityHelper = InactivityHelper(this, repository)
        inactivityHelper.resetTimer()
    }

    /**
     * Sets up click listeners for all interactive elements.
     */
    private fun setupClickListeners() {
        binding.viewA.setOnClickListener {
            inactivityHelper.resetTimer()
            handleUserResponse("A")
        }

        binding.viewB.setOnClickListener {
            inactivityHelper.resetTimer()
            handleUserResponse("B")
        }

        binding.btnExit.setOnClickListener {
            inactivityHelper.resetTimer()
            showExitConfirmationDialog()
        }
    }

    // ===========================
    // Lifecycle Methods
    // ===========================

    override fun onResume() {
        super.onResume()
        inactivityHelper.resetTimer()
    }

    override fun onPause() {
        super.onPause()
        inactivityHelper.stopTimer()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        inactivityHelper.resetTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        inactivityHelper.stopTimer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showExitConfirmationDialog()
    }

    // ===========================
    // Trial Management
    // ===========================

    /**
     * Starts the next trial or completes the experiment if finished.
     */
    private fun startNextTrial() {
        val trialState = controller.startNextTrial()

        if (trialState.isExperimentFinished) {
            completeExperiment()
        } else {
            updateUIForTrial(trialState)
        }
    }

    /**
     * Updates the UI to display the current trial.
     *
     * @param state The current trial state
     */
    private fun updateUIForTrial(state: ThresholdTrialState) {
        binding.tvTrialCounter.text = "Trial ${state.trialCount + 1} of ${state.totalTrials}"
        displayColorPatches(state.hueA, state.hueB)
    }

    /**
     * Displays the color patches for the current trial.
     *
     * @param hueA Hue value for patch A
     * @param hueB Hue value for patch B
     */
    private fun displayColorPatches(hueA: Int, hueB: Int) {
        val colorA = getColorForHue(hueA)
        val colorB = getColorForHue(hueB)

        binding.viewA.setBackgroundColor(colorA)
        binding.viewB.setBackgroundColor(colorB)
    }

    /**
     * Gets the color resource for a given hue value.
     *
     * @param hue The hue value
     * @return Color integer
     */
    private fun getColorForHue(hue: Int): Int {
        val colorRes = hueToColorRes[hue] ?: R.color.blue_140
        return ContextCompat.getColor(this, colorRes)
    }

    /**
     * Handles user response to a trial.
     *
     * @param response User's answer ("A" or "B")
     */
    private fun handleUserResponse(response: String) {
        controller.handleUserResponse(response)
        startNextTrial()
    }

    // ===========================
    // Experiment Completion
    // ===========================

    /**
     * Completes the threshold experiment and calculates JND.
     */
    private fun completeExperiment() {
        disablePatches()

        val jndThreshold = controller.calculateJNDThreshold()
        Log.d(TAG, "Calculated JND Threshold: $jndThreshold")

        lifecycleScope.launch {
            saveThreshold(jndThreshold)
        }
    }

    /**
     * Disables color patches to prevent further interaction.
     */
    private fun disablePatches() {
        binding.viewA.isEnabled = false
        binding.viewB.isEnabled = false
    }

    /**
     * Saves the calculated threshold to database and attempts upload.
     *
     * @param jndThreshold The calculated JND threshold value
     */
    private suspend fun saveThreshold(jndThreshold: Int?) {
        try {
            val participant = repository.getCurrentParticipant()

            if (participant == null) {
                Log.e(TAG, "No participant found in database")
                return
            }

            // Update participant with JND threshold
            val updatedParticipant = participant.copy(jndThreshold = jndThreshold)
            repository.updateParticipant(updatedParticipant)

            Log.d(TAG, "JND Threshold saved locally: ${participant.participantId}")

            // Attempt to upload (with offline fallback)
            val uploadSuccess = repository.uploadParticipantDemographics()
            handleUploadResult(uploadSuccess)

            // Update UI
            showCompletionUI()

        } catch (e: Exception) {
            Log.e(TAG, "Error saving threshold", e)
            showErrorMessage("Error saving threshold data")
        }
    }

    /**
     * Handles the result of the upload attempt.
     *
     * @param success Whether the upload succeeded
     */
    private fun handleUploadResult(success: Boolean) {
        if (success) {
            Log.d(TAG, "✓ JND Threshold uploaded to Firebase")
        } else {
            Log.w(TAG, "⚠ JND saved locally, will upload when online")
        }
    }

    /**
     * Shows the completion UI with continue button.
     */
    private fun showCompletionUI() {
        runOnUiThread {
            showToast("Threshold test complete!")

            binding.tvTitle.text = "Test Complete!"
            binding.btnN.visibility = android.view.View.VISIBLE
            binding.btnN.setOnClickListener { navigateToTargetExplanation() }
        }
    }

    /**
     * Navigates to the target trial explanation screen.
     */
    private fun navigateToTargetExplanation() {
        val intent = Intent(this, ExplainTargetActivity::class.java)
        startActivity(intent)
        finish()
    }

    // ===========================
    // Exit Handling
    // ===========================

    /**
     * Shows appropriate exit dialog based on completion status.
     */
    private fun showExitConfirmationDialog() {
        val participant = runBlocking { repository.getCurrentParticipant() }

        if (participant?.jndThreshold != null) {
            showExitDialogAfterCompletion()
        } else {
            showExitDialogBeforeCompletion()
        }
    }

    /**
     * Shows exit dialog when threshold test is incomplete.
     */
    private fun showExitDialogBeforeCompletion() {
        AlertDialog.Builder(this)
            .setTitle("Exit Threshold Test?")
            .setMessage("Your threshold test progress will NOT be saved. " +
                    "You will need to restart this test when you return.")
            .setPositiveButton("Exit") { _, _ ->
                handleExitBeforeCompletion()
            }
            .setNegativeButton("Continue Test", null)
            .show()
    }

    /**
     * Shows exit dialog when threshold test is complete.
     */
    private fun showExitDialogAfterCompletion() {
        AlertDialog.Builder(this)
            .setTitle("Exit?")
            .setMessage("Your threshold test is complete and saved. " +
                    "You can continue to the main experiment or exit.")
            .setPositiveButton("Exit") { _, _ ->
                handleExitAfterCompletion()
            }
            .setNegativeButton("Continue to Experiment", null)
            .show()
    }

    /**
     * Handles exit before threshold completion.
     */
    private fun handleExitBeforeCompletion() {
        lifecycleScope.launch {
            try {
                repository.clearIncompleteTrialData()
                showToast("Exiting. Your demographics are saved.")

                repository.clearCurrentParticipant()
                navigateToLogin()
            } catch (e: Exception) {
                Log.e(TAG, "Error during exit", e)
                showErrorMessage("Error during exit")
            }
        }
    }

    /**
     * Handles exit after threshold completion.
     */
    private fun handleExitAfterCompletion() {
        lifecycleScope.launch {
            try {
                val participant = repository.getCurrentParticipant()

                // Ensure JND is saved if not already
                if (participant?.jndThreshold == null) {
                    saveThresholdBeforeExit()
                }

                repository.clearCurrentParticipant()
                showToast("Your progress is saved. You can continue later.")

                navigateToLogin()
            } catch (e: Exception) {
                Log.e(TAG, "Error during exit after completion", e)
            }
        }
    }

    /**
     * Saves threshold before exiting (fallback).
     */
    private suspend fun saveThresholdBeforeExit() {
        val participant = repository.getCurrentParticipant() ?: return

        val jndThreshold = controller.calculateJNDThreshold()
        val updatedParticipant = participant.copy(jndThreshold = jndThreshold)

        repository.updateParticipant(updatedParticipant)
        repository.uploadParticipantDemographics()

        Log.d(TAG, "JND saved during exit: $jndThreshold")
    }

    /**
     * Navigates to login screen with cleared back stack.
     */
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    // ===========================
    // Utility Methods
    // ===========================

    /**
     * Shows a toast message.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Shows an error message as toast.
     */
    private fun showErrorMessage(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
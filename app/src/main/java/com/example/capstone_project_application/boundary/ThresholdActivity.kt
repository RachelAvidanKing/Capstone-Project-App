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
import com.example.capstone_project_application.boundary.TargetActivity
import com.example.capstone_project_application.control.WorkScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ThresholdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdBinding
    private lateinit var controller: ThresholdController

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }
    private lateinit var inactivityHelper: InactivityHelper

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

        controller = ThresholdController()
        inactivityHelper = InactivityHelper(this, repository)

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

        inactivityHelper.resetTimer()
        startNextTrial()
    }

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

    override fun onBackPressed() {
        // Custom handling
        showExitConfirmationDialog()
    }

    private fun showExitConfirmationDialog() {
        // Check if threshold test is complete
        val participant = runBlocking { repository.getCurrentParticipant() }

        if (participant?.jndThreshold != null) {
            // Threshold is complete - show different message
            AlertDialog.Builder(this)
                .setTitle("Exit?")
                .setMessage("Your threshold test is complete and saved. You can continue to the main experiment or exit.")
                .setPositiveButton("Exit") { _, _ ->
                    handleExitAfterCompletion()
                }
                .setNegativeButton("Continue to Experiment", null)
                .show()
        } else {
            // Threshold is incomplete
            AlertDialog.Builder(this)
                .setTitle("Exit Threshold Test?")
                .setMessage("Your threshold test progress will NOT be saved. You will need to restart this test when you return.")
                .setPositiveButton("Exit") { _, _ ->
                    handleExit()
                }
                .setNegativeButton("Continue Test", null)
                .show()
        }
    }

    private fun handleExit() {
        lifecycleScope.launch {
            try {
                // Clear incomplete trial data
                repository.clearIncompleteTrialData()

                // Demographics are already saved, no need to upload anything
                Toast.makeText(
                    this@ThresholdActivity,
                    "Exiting. Your demographics are saved.",
                    Toast.LENGTH_SHORT
                ).show()

                // Clear current session
                repository.clearCurrentParticipant()

                // Return to login
                val intent = Intent(this@ThresholdActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("ThresholdActivity", "Error during exit", e)
                Toast.makeText(
                    this@ThresholdActivity,
                    "Error during exit",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun handleExitAfterCompletion() {
        lifecycleScope.launch {
            try {
                // Threshold is complete - but need to ensure JND was saved
                val participant = repository.getCurrentParticipant()

                if (participant?.jndThreshold != null) {
                    // JND is already saved, safe to logout
                    repository.clearCurrentParticipant()

                    Toast.makeText(
                        this@ThresholdActivity,
                        "Your progress is saved. You can continue later.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // JND wasn't saved yet - save it now before exiting
                    val jndThreshold = controller.calculateJNDThreshold()
                    val updatedParticipant = participant?.copy(jndThreshold = jndThreshold)
                    if (updatedParticipant != null) {
                        repository.updateParticipant(updatedParticipant)
                        repository.uploadParticipantDemographics()
                        Log.d("ThresholdActivity", "JND saved during exit: $jndThreshold")
                    }

                    repository.clearCurrentParticipant()

                    Toast.makeText(
                        this@ThresholdActivity,
                        "Threshold saved. You can continue later.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val intent = Intent(this@ThresholdActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("ThresholdActivity", "Error during exit after completion", e)
            }
        }
    }

    private fun startNextTrial() {
        val trialState = controller.startNextTrial()
        if (trialState.isExperimentFinished) {
            completeExperiment()
        } else {
            updateUIForTrial(trialState)
        }
    }

    private fun updateUIForTrial(state: ThresholdTrialState) {
        binding.tvTrialCounter.text = "Trial ${state.trialCount + 1} of ${state.totalTrials}"
        displayPatches(state.hueA, state.hueB)
    }

    private fun displayPatches(hueA: Int, hueB: Int) {
        val colorA = ContextCompat.getColor(this, hueToColorRes[hueA] ?: R.color.blue_140)
        val colorB = ContextCompat.getColor(this, hueToColorRes[hueB] ?: R.color.blue_140)
        binding.viewA.setBackgroundColor(colorA)
        binding.viewB.setBackgroundColor(colorB)
    }

    private fun handleUserResponse(response: String) {
        controller.handleUserResponse(response)
        startNextTrial()
    }

    private fun completeExperiment() {
        binding.viewA.isEnabled = false
        binding.viewB.isEnabled = false

        val jndThreshold = controller.calculateJNDThreshold()
        Log.d("ThresholdActivity", "Calculated JND Threshold: $jndThreshold")

        lifecycleScope.launch {
            saveThreshold(jndThreshold)
        }
    }

    private suspend fun saveThreshold(jndThreshold: Int?) {
        try {
            val participant = repository.getCurrentParticipant()
            if (participant != null) {
                val updatedParticipant = participant.copy(jndThreshold = jndThreshold)
                repository.updateParticipant(updatedParticipant)
                Log.d("ThresholdActivity", "JND Threshold saved locally: ${participant.participantId}")

                // Upload updated participant data with JND threshold (section complete)
                val uploadSuccess = repository.uploadParticipantDemographics()

                if (uploadSuccess) {
                    Log.d("ThresholdActivity", "✓ JND Threshold uploaded to Firebase")
                } else {
                    Log.w("ThresholdActivity", "⚠ JND saved locally, will upload when online")
                }

                runOnUiThread {
                    Toast.makeText(
                        this@ThresholdActivity,
                        "Threshold test complete!",
                        Toast.LENGTH_SHORT
                    ).show()

                    binding.btnNext.visibility = android.view.View.VISIBLE
                    binding.btnNext.setOnClickListener { navigateToNextActivity() }
                    binding.tvTitle.text = "Test Complete!"
                }
            } else {
                Log.e("ThresholdActivity", "No participant found in database")
            }
        } catch (e: Exception) {
            Log.e("ThresholdActivity", "Error saving threshold", e)
        }
    }

    private fun navigateToNextActivity() {
        val intent = Intent(this@ThresholdActivity, TargetActivity::class.java)
        startActivity(intent)
        finish()
    }
}
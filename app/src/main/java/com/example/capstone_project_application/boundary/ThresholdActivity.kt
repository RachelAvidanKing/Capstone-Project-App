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
import com.example.capstone_project_application.boundary.TargetActivity
import com.example.capstone_project_application.control.WorkScheduler
import kotlinx.coroutines.launch

class ThresholdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdBinding
    private lateinit var controller: ThresholdController

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

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

        binding.btnA.setOnClickListener { handleUserResponse("A") }
        binding.btnB.setOnClickListener { handleUserResponse("B") }

        startNextTrial()
    }

    override fun onBackPressed() {
        // Prevent accidental exits - show confirmation dialog
        showExitConfirmationDialog()
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Threshold Test?")
            .setMessage("Are you sure you want to exit? Your progress will be saved.")
            .setPositiveButton("Exit") { _, _ ->
                handleExit()
            }
            .setNegativeButton("Continue Test", null)
            .show()
    }

    private fun handleExit() {
        lifecycleScope.launch {
            try {
                // Upload current data
                WorkScheduler.triggerImmediateUpload(this@ThresholdActivity)

                Toast.makeText(
                    this@ThresholdActivity,
                    "Progress saved. You can continue later.",
                    Toast.LENGTH_LONG
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
                    "Error saving progress",
                    Toast.LENGTH_SHORT
                ).show()
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
        binding.btnA.isEnabled = false
        binding.btnB.isEnabled = false

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
                Log.d("ThresholdActivity", "JND Threshold saved for participant ${participant.participantId}")

                // Upload the updated participant data
                WorkScheduler.triggerImmediateUpload(this@ThresholdActivity)

                Toast.makeText(
                    this@ThresholdActivity,
                    "Threshold test complete!",
                    Toast.LENGTH_LONG
                ).show()

                binding.btnNext.visibility = android.view.View.VISIBLE
                binding.btnNext.setOnClickListener { navigateToNextActivity() }

                // Add exit button
                addExitButton()
            } else {
                Log.e("ThresholdActivity", "No participant found in database")
            }
        } catch (e: Exception) {
            Log.e("ThresholdActivity", "Error saving threshold", e)
        }
    }

    private fun addExitButton() {
        // You can add a button in the XML or create one programmatically
        // For now, we'll just enable the back button to work as exit
        binding.tvTitle.text = "Test Complete!\nContinue or Exit"
    }

    private fun navigateToNextActivity() {
        val intent = Intent(this@ThresholdActivity, TargetActivity::class.java)
        startActivity(intent)
        finish()
    }
}
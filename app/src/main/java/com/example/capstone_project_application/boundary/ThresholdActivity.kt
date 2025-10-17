package com.example.capstone_project_application.boundary // <-- NOTE: Updated package

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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

    // Database and repository are still needed to save the final result
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    // Color resource mapping remains for UI display
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

        // Initialize the controller that holds all the logic
        controller = ThresholdController()

        binding.btnA.setOnClickListener { handleUserResponse("A") }
        binding.btnB.setOnClickListener { handleUserResponse("B") }

        startNextTrial()
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
                WorkScheduler.triggerImmediateUpload(this@ThresholdActivity)

                Toast.makeText(this@ThresholdActivity, "Threshold test complete! Moving to next phase...", Toast.LENGTH_LONG).show()

                binding.btnNext.visibility = android.view.View.VISIBLE
                binding.btnNext.setOnClickListener { navigateToNextActivity() }
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
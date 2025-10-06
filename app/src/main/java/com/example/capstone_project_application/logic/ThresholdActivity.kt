package com.example.capstone_project_application.logic

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.capstone_project_application.databinding.ActivityThresholdBinding
import com.example.capstone_project_application.R
import com.example.capstone_project_application.database.AppDatabase
import com.example.capstone_project_application.database.DataRepository
import kotlinx.coroutines.launch
import kotlin.random.Random

class ThresholdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdBinding

    // Database and repository
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    // Algorithm constants
    private val N_TOTAL = 100
    private val n0 = 140
    private val HUES = listOf(141, 142, 143, 144, 145, 150, 155, 160, 165, 175)
    private val MAX_PER_HUE = 10
    private val MAX_LEFT = 5

    // Color resource mapping
    private val hueToColorRes = mapOf(
        141 to R.color.blue_141,
        142 to R.color.blue_142,
        143 to R.color.blue_143,
        144 to R.color.blue_144,
        145 to R.color.blue_145,
        150 to R.color.blue_150,
        155 to R.color.blue_155,
        160 to R.color.blue_160,
        165 to R.color.blue_165,
        175 to R.color.blue_175,
        140 to R.color.blue_140
    )

    // Tracking variables
    private var trialCount = 0
    private val occurrences = mutableMapOf<Int, Int>()
    private val leftCount = mutableMapOf<Int, Int>()
    private val correctAnswers = mutableMapOf<Int, Int>()

    // Current trial state
    private var currentSelectedHue = 0
    private var currentPosition = ""
    private var currentCorrectAnswer = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThresholdBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeAlgorithm()
        startNextTrial()

        binding.btnA.setOnClickListener {
            handleUserResponse("A")
        }

        binding.btnB.setOnClickListener {
            handleUserResponse("B")
        }
    }

    private fun initializeAlgorithm() {
        // Initialize tracking for each hue
        for (hue in HUES) {
            occurrences[hue] = 0
            leftCount[hue] = 0
            correctAnswers[hue] = 0
        }
        trialCount = 0
        updateTrialInfo()
    }

    private fun startNextTrial() {
        // Check if we've completed all trials
        if (trialCount >= N_TOTAL) {
            completeExperiment()
            return
        }

        // Find valid hues (those that haven't reached MAX_PER_HUE)
        val validHues = HUES.filter { occurrences[it]!! < MAX_PER_HUE }

        if (validHues.isEmpty()) {
            completeExperiment()
            return
        }

        // Randomly select a hue from valid hues
        currentSelectedHue = validHues.random()

        // Determine position (left or right)
        val canBeLeft = leftCount[currentSelectedHue]!! < MAX_LEFT
        val shouldBeLeft = canBeLeft && Random.nextBoolean()

        if (shouldBeLeft) {
            currentPosition = "left"
            leftCount[currentSelectedHue] = leftCount[currentSelectedHue]!! + 1
            // Display: PatchA = selected_hue, PatchB = n0
            displayPatches(currentSelectedHue, n0)
            currentCorrectAnswer = "A"
        } else {
            currentPosition = "right"
            // Display: PatchA = n0, PatchB = selected_hue
            displayPatches(n0, currentSelectedHue)
            currentCorrectAnswer = "B"
        }

        // Increment occurrence count
        occurrences[currentSelectedHue] = occurrences[currentSelectedHue]!! + 1

        // Update trial counter display
        updateTrialInfo()
    }

    private fun displayPatches(hueA: Int, hueB: Int) {
        val colorResA = hueToColorRes[hueA] ?: R.color.blue_140
        val colorResB = hueToColorRes[hueB] ?: R.color.blue_140

        val colorA = ContextCompat.getColor(this, colorResA)
        val colorB = ContextCompat.getColor(this, colorResB)

        binding.viewA.setBackgroundColor(colorA)
        binding.viewB.setBackgroundColor(colorB)
    }

    private fun handleUserResponse(response: String) {
        // Check if response matches correct answer
        if (response == currentCorrectAnswer) {
            correctAnswers[currentSelectedHue] = correctAnswers[currentSelectedHue]!! + 1
        }

        // Increment trial count
        trialCount++

        // Start next trial
        startNextTrial()
    }

    private fun updateTrialInfo() {
        // Update UI with trial progress
        binding.tvTrialCounter.text = "Trial $trialCount of $N_TOTAL"
    }

    private fun calculateJNDThreshold(): Int? {
        // Calculate percentage correct for each hue
        val huePerformance = mutableListOf<Pair<Int, Double>>()

        for (hue in HUES) {
            val correct = correctAnswers[hue]!!
            val total = occurrences[hue]!!
            if (total > 0) {
                val percentage = (correct * 100.0) / total
                huePerformance.add(Pair(hue, percentage))
                Log.d("ThresholdActivity", "Hue $hue: $correct/$total (${String.format("%.1f", percentage)}%)")
            }
        }

        // Sort hues in descending order (highest to lowest)
        huePerformance.sortByDescending { it.first }

        // Find the highest hue number where performance is 50% or less
        var threshold: Int? = null
        for ((hue, percentage) in huePerformance) {
            if (percentage <= 50.0) {
                if (threshold == null || hue > threshold) {
                    threshold = hue
                }
            }
        }

        Log.d("ThresholdActivity", "Calculated JND Threshold: $threshold")
        return threshold
    }

    private fun completeExperiment() {
        // Disable buttons to prevent further clicks
        binding.btnA.isEnabled = false
        binding.btnB.isEnabled = false

        // Calculate JND threshold
        val jndThreshold = calculateJNDThreshold()

        // Save threshold to database
        lifecycleScope.launch {
            try {
                val participant = repository.getCurrentParticipant()
                if (participant != null) {
                    // Update participant with JND threshold
                    val updatedParticipant = participant.copy(jndThreshold = jndThreshold)
                    repository.updateParticipant(updatedParticipant)

                    Log.d("ThresholdActivity", "JND Threshold saved: $jndThreshold for participant ${participant.participantId}")

                    WorkScheduler.triggerImmediateUpload(this@ThresholdActivity)

                    // Show results
                    val message = if (jndThreshold != null) {
                        "Experiment complete! Your JND threshold: Blue $jndThreshold"
                    } else {
                        "Experiment complete! No threshold detected (performance > 50% for all hues)"
                    }
                    Toast.makeText(this@ThresholdActivity, message, Toast.LENGTH_LONG).show()

                    // Show "Next" button to continue to the next activity
                    binding.btnNext.visibility = android.view.View.VISIBLE
                    binding.btnNext.setOnClickListener {
                        navigateToNextActivity()
                    }
                } else {
                    Log.e("ThresholdActivity", "No participant found in database")
                    Toast.makeText(this@ThresholdActivity, "Error: No participant data found", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ThresholdActivity", "Error saving threshold", e)
                Toast.makeText(this@ThresholdActivity, "Error saving results: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToNextActivity() {
        // TODO: Replace with your actual next activity
        // Example:
        // val intent = Intent(this, NextActivity::class.java)
        // startActivity(intent)
        // finish()

        Toast.makeText(this, "Navigate to next activity (not implemented yet)", Toast.LENGTH_SHORT).show()
        finish()
    }
}
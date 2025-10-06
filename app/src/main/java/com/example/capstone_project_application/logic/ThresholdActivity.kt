package com.example.capstone_project_application.logic

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.capstone_project_application.databinding.ActivityThresholdBinding
import com.example.capstone_project_application.R
import kotlin.random.Random

class ThresholdActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThresholdBinding

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
        140 to R.color.blue_140  // Add blue_140 to your colors.xml
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

        // Update trial counter display (optional)
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
        // Optional: Update UI with trial progress
        // For example, you could add a TextView to show "Trial X of N_TOTAL"
        // binding.tvTrialInfo.text = "Trial $trialCount of $N_TOTAL"
    }

    private fun completeExperiment() {
        // Calculate results
        val results = StringBuilder()
        results.append("Experiment Complete!\n\n")

        for (hue in HUES) {
            val correct = correctAnswers[hue]!!
            val total = occurrences[hue]!!
            val percentage = if (total > 0) (correct * 100.0 / total) else 0.0
            results.append("Hue $hue: $correct/$total (${String.format("%.1f", percentage)}%)\n")
        }

        Toast.makeText(this, "Experiment completed! Total trials: $trialCount", Toast.LENGTH_LONG).show()

        // Log results or save to database
        android.util.Log.d("ThresholdActivity", results.toString())

        // You might want to save results to your database here
        // or navigate to a results screen

        // Optionally, finish the activity or show results
        // finish()
    }
}
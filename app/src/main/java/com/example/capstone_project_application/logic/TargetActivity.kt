package com.example.capstone_project_application.logic

import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.capstone_project_application.R
import com.example.capstone_project_application.database.AppDatabase
import com.example.capstone_project_application.database.DataRepository
import kotlinx.coroutines.launch
import kotlin.random.Random

class TargetActivity : AppCompatActivity() {

    private lateinit var circles: List<View>

    // Color resource mapping (same as ThresholdActivity)
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

    private val HUES = listOf(141, 142, 143, 144, 145, 150, 155, 160, 165, 175)
    private val BEEP_INTERVAL_MS = 700L
    private val NUM_BEEPS = 4
    private val TOTAL_TRIALS = 10

    // Database and repository
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    private var jndThreshold: Int? = null
    private var targetCircleIndex: Int = -1
    private var isJndTrial: Boolean = true
    private var targetHue: Int = 140

    // Audio
    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())

    // Trial state
    private var isTrialInProgress = false
    private var currentTrialNumber = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_target)

        circles = listOf(
            findViewById(R.id.circleTopLeft),
            findViewById(R.id.circleTopRight),
            findViewById(R.id.circleBottomLeft),
            findViewById(R.id.circleBottomRight)
        )

        // Initialize tone generator for beeps
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

        // Load JND threshold from database
        lifecycleScope.launch {
            loadJndThresholdAndStartExperiment()
        }

        // Set up click listeners on all circles
        circles.forEachIndexed { index, circle ->
            circle.setOnClickListener {
                handleCircleClick(index)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        handler.removeCallbacksAndMessages(null)
    }

    private suspend fun loadJndThresholdAndStartExperiment() {
        try {
            val participant = repository.getCurrentParticipant()
            if (participant != null && participant.jndThreshold != null) {
                jndThreshold = participant.jndThreshold
                Log.d("TargetActivity", "Loaded JND threshold: $jndThreshold")

                // Start the first trial
                runOnUiThread {
                    startNewTrial()
                }
            } else {
                Log.e("TargetActivity", "No JND threshold found for participant")
                runOnUiThread {
                    Toast.makeText(
                        this@TargetActivity,
                        "Error: No JND threshold found. Please complete the threshold test first.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        } catch (e: Exception) {
            Log.e("TargetActivity", "Error loading JND threshold", e)
            runOnUiThread {
                Toast.makeText(
                    this@TargetActivity,
                    "Error loading threshold data: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    private fun startNewTrial() {
        if (jndThreshold == null || isTrialInProgress) {
            return
        }

        // Check if we've completed all trials
        if (currentTrialNumber >= TOTAL_TRIALS) {
            completeExperiment()
            return
        }

        currentTrialNumber++
        isTrialInProgress = true

        Log.d("TargetActivity", "Starting trial $currentTrialNumber of $TOTAL_TRIALS")

        // Randomly decide if this trial shows JND or above-JND color
        isJndTrial = Random.nextBoolean()

        // Select the target hue
        targetHue = if (isJndTrial) {
            jndThreshold!!
        } else {
            // Select a hue ABOVE the JND threshold (lower hue number = more different from reference)
            val aboveThresholdHues = HUES.filter { it < jndThreshold!! }
            if (aboveThresholdHues.isNotEmpty()) {
                aboveThresholdHues.random()
            } else {
                // If no hues below threshold, use the lowest available hue
                HUES.minOrNull() ?: jndThreshold!!
            }
        }

        // Randomly select which circle will show the target color (0-3)
        targetCircleIndex = Random.nextInt(4)

        Log.d("TargetActivity", "New trial: isJND=$isJndTrial, targetHue=$targetHue, position=$targetCircleIndex")

        // Update trial counter display
        updateTrialDisplay()

        // Hide all circles initially (set to background color)
        hideAllCircles()

        // Start the beep sequence
        startBeepSequence()
    }

    private fun hideAllCircles() {
        val backgroundColor = ContextCompat.getColor(this, R.color.white)
        circles.forEach { circle ->
            circle.backgroundTintList = android.content.res.ColorStateList.valueOf(backgroundColor)
        }
    }

    private fun startBeepSequence() {
        var beepCount = 0

        val beepRunnable = object : Runnable {
            override fun run() {
                beepCount++

                // Play beep - different sound for 4th beep
                if (beepCount == NUM_BEEPS) {
                    playGoBeep() // Special "GO" beep
                } else {
                    playBeep() // Regular beep
                }

                Log.d("TargetActivity", "Beep $beepCount of $NUM_BEEPS")

                // On the 4th beep, show the target
                if (beepCount == NUM_BEEPS) {
                    showTarget()
                } else {
                    // Schedule next beep
                    handler.postDelayed(this, BEEP_INTERVAL_MS)
                }
            }
        }

        // Start the first beep immediately
        handler.post(beepRunnable)
    }

    private fun playBeep() {
        try {
            // Play a beep tone (frequency and duration can be adjusted)
            // ToneGenerator.TONE_PROP_BEEP is a standard beep tone
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200) // 200ms duration
        } catch (e: Exception) {
            Log.e("TargetActivity", "Error playing beep", e)
        }
    }

    private fun playGoBeep() {
        try {
            // Play a different tone for the "GO" signal
            // TONE_CDMA_ALERT_CALL_GUARD is a distinctive tone
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300) // 300ms duration
        } catch (e: Exception) {
            Log.e("TargetActivity", "Error playing GO beep", e)
        }
    }

    private fun showTarget() {
        // Show only the target circle with the target hue
        val colorRes = hueToColorRes[targetHue] ?: R.color.blue_140
        val color = ContextCompat.getColor(this, colorRes)

        circles[targetCircleIndex].backgroundTintList =
            android.content.res.ColorStateList.valueOf(color)

        Log.d("TargetActivity", "Target shown at position $targetCircleIndex with hue $targetHue")

        // Trial is now ready for user interaction
        // isTrialInProgress remains true until user clicks
    }

    private fun handleCircleClick(clickedIndex: Int) {
        if (!isTrialInProgress) {
            return
        }

        // Check if user clicked the target circle
        val isCorrect = clickedIndex == targetCircleIndex

        Log.d("TargetActivity", "Circle $clickedIndex clicked. Correct=$isCorrect, Target was $targetCircleIndex")

        // TODO: Save the trial data (clicked position, correct/incorrect, isJndTrial, etc.)

        // Reset trial state
        isTrialInProgress = false

        // Start next trial after a short delay
        handler.postDelayed({
            startNewTrial()
        }, 1000) // 1 second delay before next trial
    }

    private fun updateTrialDisplay() {
        // Update the trial counter text (similar to ThresholdActivity)
        // You'll need to add a TextView with id tvTrialCounter to activity_target.xml
        val trialCounter = findViewById<android.widget.TextView>(R.id.tvTrialCounter)
        trialCounter?.text = "Trial $currentTrialNumber of $TOTAL_TRIALS"
    }

    private fun completeExperiment() {
        Log.d("TargetActivity", "Target experiment completed - all $TOTAL_TRIALS trials finished")

        Toast.makeText(
            this,
            "Target experiment complete! Thank you for participating.",
            Toast.LENGTH_LONG
        ).show()

        // TODO: Navigate to next activity or finish
        // For now, just finish the activity
        handler.postDelayed({
            finish()
        }, 2000)
    }
}
package com.example.capstone_project_application.boundary

import android.media.AudioManager
import android.media.ToneGenerator
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
import com.example.capstone_project_application.control.TargetController
import com.example.capstone_project_application.control.TargetTrialState
import com.example.capstone_project_application.control.TrialType
import com.example.capstone_project_application.entity.AppDatabase
import com.example.capstone_project_application.control.DataRepository
import kotlinx.coroutines.launch

class TargetActivity : AppCompatActivity() {

    private lateinit var circles: List<View>
    private lateinit var controller: TargetController
    private lateinit var currentTrialState: TargetTrialState

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isTrialInProgress = false

    private val BEEP_INTERVAL_MS = 700L
    private val PRE_BEEP_OFFSET_MS = 350L
    private val NUM_BEEPS = 4

    // Color resource mapping
    private val hueToColorRes = mapOf(
        999 to R.color.blue_obvious,
        141 to R.color.blue_141, 142 to R.color.blue_142, 143 to R.color.blue_143,
        144 to R.color.blue_144, 145 to R.color.blue_145, 150 to R.color.blue_150,
        155 to R.color.blue_155, 160 to R.color.blue_160, 165 to R.color.blue_165,
        175 to R.color.blue_175, 140 to R.color.blue_140
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_target)

        circles = listOf(
            findViewById(R.id.circleTopLeft), findViewById(R.id.circleTopRight),
            findViewById(R.id.circleBottomLeft), findViewById(R.id.circleBottomRight)
        )

        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

        lifecycleScope.launch {
            val participant = repository.getCurrentParticipant()
            if (participant?.jndThreshold != null) {
                controller = TargetController(participant.jndThreshold!!)
                startNewTrial()
            } else {
                Toast.makeText(this@TargetActivity, "Error: JND threshold not found.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        circles.forEachIndexed { index, circle ->
            circle.setOnClickListener { handleCircleClick(index) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        handler.removeCallbacksAndMessages(null) // Important to prevent leaks
    }

    private fun startNewTrial() {
        if (isTrialInProgress) return

        currentTrialState = controller.startNewTrial()
        if (currentTrialState.isExperimentFinished) {
            completeExperiment()
            return
        }

        isTrialInProgress = true
        Log.d("TargetActivity", "Starting Trial ${currentTrialState.trialCount}: Type = ${currentTrialState.trialType}")

        updateTrialDisplay(currentTrialState)
        resetCirclesToNeutral()
        startBeepAndTargetSequence()
    }

    private fun startBeepAndTargetSequence() {
        // --- Schedule Beeps ---
        for (i in 1..NUM_BEEPS) {
            val beepTime = (i - 1) * BEEP_INTERVAL_MS
            val isLastBeep = i == NUM_BEEPS
            handler.postDelayed({
                if (isLastBeep) playGoBeep() else playBeep()
            }, beepTime)
        }

        // --- Schedule Target Visuals based on Trial Type ---
        val fourthBeepTime = (NUM_BEEPS - 1) * BEEP_INTERVAL_MS

        when (currentTrialState.trialType) {
            TrialType.PRE_JND -> {
                // Show subtle color 350ms BEFORE the 4th beep
                val initialShowTime = fourthBeepTime - PRE_BEEP_OFFSET_MS
                handler.postDelayed({
                    showTarget(currentTrialState.targetCircleIndex, currentTrialState.initialHue)
                }, initialShowTime)

                // Change to OBVIOUS color AT the 4th beep
                handler.postDelayed({
                    showTarget(currentTrialState.targetCircleIndex, currentTrialState.finalHue!!)
                }, fourthBeepTime)
            }
            TrialType.PRE_SUPRA -> {
                // Show obvious color 350ms BEFORE the 4th beep
                val initialShowTime = fourthBeepTime - PRE_BEEP_OFFSET_MS
                handler.postDelayed({
                    showTarget(currentTrialState.targetCircleIndex, currentTrialState.initialHue)
                }, initialShowTime)
            }
            TrialType.CONCURRENT_SUPRA -> {
                // Show obvious color AT the 4th beep
                handler.postDelayed({
                    showTarget(currentTrialState.targetCircleIndex, currentTrialState.initialHue)
                }, fourthBeepTime)
            }
        }
    }

    private fun handleCircleClick(clickedIndex: Int) {
        if (!isTrialInProgress) return

        val isCorrect = controller.checkUserResponse(clickedIndex, currentTrialState.targetCircleIndex)
        Log.d("TargetActivity", "Circle $clickedIndex clicked. Correct: $isCorrect")

        isTrialInProgress = false
        // TODO: Save detailed trial data (response time, correctness, trial type) to the repository

        // Start next trial after a short delay
        handler.postDelayed({ startNewTrial() }, 1000)
    }

    private fun showTarget(index: Int, hue: Int) {
        if (index in circles.indices) {
            val colorRes = hueToColorRes[hue] ?: R.color.blue_140
            val color = ContextCompat.getColor(this, colorRes)
            circles[index].backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }

    private fun resetCirclesToNeutral() {
        val neutralColorRes = R.color.blue_140 // The reference neutral color
        val color = ContextCompat.getColor(this, neutralColorRes)
        circles.forEach { it.backgroundTintList = android.content.res.ColorStateList.valueOf(color) }
    }

    private fun updateTrialDisplay(trialState: TargetTrialState) {
        val trialCounter = findViewById<android.widget.TextView>(R.id.tvTrialCounter)
        trialCounter?.text = "Trial ${trialState.trialCount} of ${trialState.totalTrials}"
    }

    private fun playBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    private fun playGoBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
    }


    private fun completeExperiment() {
        Log.d("TargetActivity", "Target experiment completed.")
        Toast.makeText(this, "Experiment complete! Thank you.", Toast.LENGTH_LONG).show()
        finish()
    }
}
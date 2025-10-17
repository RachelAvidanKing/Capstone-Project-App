package com.example.capstone_project_application.boundary // <-- NOTE: Updated package

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
import com.example.capstone_project_application.entity.AppDatabase
import com.example.capstone_project_application.control.DataRepository
import kotlinx.coroutines.launch

class TargetActivity : AppCompatActivity() {

    private lateinit var circles: List<View>
    private lateinit var controller: TargetController

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isTrialInProgress = false

    private val BEEP_INTERVAL_MS = 700L
    private val NUM_BEEPS = 4

    private val hueToColorRes = mapOf(
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
                // Initialize controller with the required JND threshold
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
        handler.removeCallbacksAndMessages(null)
    }

    private fun startNewTrial() {
        if (isTrialInProgress) return

        val trialState = controller.startNewTrial()
        if (trialState.isExperimentFinished) {
            completeExperiment()
            return
        }

        isTrialInProgress = true
        Log.d("TargetActivity", "Starting trial ${trialState.trialCount} of ${trialState.totalTrials}")
        updateTrialDisplay(trialState)
        hideAllCircles()
        startBeepSequence(trialState)
    }

    private fun handleCircleClick(clickedIndex: Int) {
        if (!isTrialInProgress) return

        val isCorrect = controller.checkUserResponse(clickedIndex)
        Log.d("TargetActivity", "Circle $clickedIndex clicked. Correct: $isCorrect")

        // TODO: Save trial results (correctness, response time, etc.) to the repository

        isTrialInProgress = false
        handler.postDelayed({ startNewTrial() }, 1000)
    }

    private fun startBeepSequence(trialState: TargetTrialState) {
        var beepCount = 0
        val beepRunnable = object : Runnable {
            override fun run() {
                beepCount++
                if (beepCount == NUM_BEEPS) {
                    playGoBeep()
                    showTarget(trialState)
                } else {
                    playBeep()
                    handler.postDelayed(this, BEEP_INTERVAL_MS)
                }
            }
        }
        handler.post(beepRunnable)
    }

    private fun showTarget(trialState: TargetTrialState) {
        val colorRes = hueToColorRes[trialState.targetHue] ?: R.color.blue_140
        val color = ContextCompat.getColor(this, colorRes)
        circles[trialState.targetCircleIndex].backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun updateTrialDisplay(trialState: TargetTrialState) {
        val trialCounter = findViewById<android.widget.TextView>(R.id.tvTrialCounter)
        trialCounter?.text = "Trial ${trialState.trialCount} of ${trialState.totalTrials}"
    }

    private fun hideAllCircles() {
        val backgroundColor = ContextCompat.getColor(this, R.color.white)
        circles.forEach { it.backgroundTintList = android.content.res.ColorStateList.valueOf(backgroundColor) }
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
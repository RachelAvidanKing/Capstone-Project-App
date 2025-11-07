package com.example.capstone_project_application.boundary

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.capstone_project_application.R
import com.example.capstone_project_application.control.MovementTracker
import com.example.capstone_project_application.control.TargetController
import com.example.capstone_project_application.control.TargetTrialState
import com.example.capstone_project_application.control.TrialType
import com.example.capstone_project_application.control.WorkScheduler
import com.example.capstone_project_application.control.InactivityHelper
import com.example.capstone_project_application.entity.AppDatabase
import com.example.capstone_project_application.control.DataRepository
import com.example.capstone_project_application.entity.TargetTrialResult
import kotlinx.coroutines.launch

class TargetActivity : AppCompatActivity() {

    private lateinit var circles: List<View>
    private lateinit var controller: TargetController
    private lateinit var currentTrialState: TargetTrialState
    private val movementTracker = MovementTracker()

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }
    private lateinit var inactivityHelper: InactivityHelper

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isTrialInProgress = false
    private var trialStartTime: Long = 0

    private lateinit var btnExit: Button
    private lateinit var centerX: TextView

    // Track which circles are currently hovered
    private val hoveredCircles = mutableSetOf<Int>()

    private val BEEP_INTERVAL_MS = 700L
    private val PRE_BEEP_OFFSET_MS = 350L
    private val NUM_BEEPS = 4
    private val ENLARGED_SCALE = 1.3f // 30% larger when hovered

    private val hueToColorRes = mapOf(
        999 to R.color.turquoise_obvious, // Changed to turquoise for better visibility
        141 to R.color.blue_141, 142 to R.color.blue_142, 143 to R.color.blue_143,
        144 to R.color.blue_144, 145 to R.color.blue_145, 150 to R.color.blue_150,
        155 to R.color.blue_155, 160 to R.color.blue_160, 165 to R.color.blue_165,
        175 to R.color.blue_175, 140 to R.color.blue_140
    )

    companion object {
        private const val TAG = "TargetActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_target)

        circles = listOf(
            findViewById(R.id.circleTopLeft), findViewById(R.id.circleTopRight),
            findViewById(R.id.circleBottomLeft), findViewById(R.id.circleBottomRight)
        )

        centerX = findViewById(R.id.centerX)
        btnExit = findViewById(R.id.btnExit)

        btnExit.setOnClickListener {
            inactivityHelper.resetTimer()
            showExitConfirmationDialog()
        }

        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        inactivityHelper = InactivityHelper(this, repository)

        setupGlobalTouchTracking()

        lifecycleScope.launch {
            val participant = repository.getCurrentParticipant()
            Log.d(TAG, "Participant loaded: $participant")

            if (participant == null) {
                Toast.makeText(this@TargetActivity, "Error: Participant not found.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            if (participant.jndThreshold == null) {
                Toast.makeText(this@TargetActivity, "Error: JND threshold not calculated. Please complete JND test first.", Toast.LENGTH_LONG).show()
                Log.e(TAG, "JND threshold is null for participant ${participant.participantId}")
                finish()
                return@launch
            }

            Log.d(TAG, "JND Threshold found: ${participant.jndThreshold}")
            controller = TargetController(participant.jndThreshold!!)
            inactivityHelper.resetTimer()
            startNewTrial()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::inactivityHelper.isInitialized) {
            inactivityHelper.resetTimer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::inactivityHelper.isInitialized) {
            inactivityHelper.stopTimer()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::inactivityHelper.isInitialized) {
            inactivityHelper.resetTimer()
        }
    }

    override fun onBackPressed() {
        showExitConfirmationDialog()
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Experiment?")
            .setMessage("Your target trial progress will NOT be saved. You will need to restart this experiment when you return.")
            .setPositiveButton("Exit") { _, _ ->
                handleExit()
            }
            .setNegativeButton("Continue Experiment", null)
            .show()
    }

    private fun handleExit() {
        lifecycleScope.launch {
            try {
                isTrialInProgress = false
                handler.removeCallbacksAndMessages(null)
                repository.clearIncompleteTrialData()

                Toast.makeText(
                    this@TargetActivity,
                    "Exiting. Your JND threshold is saved.",
                    Toast.LENGTH_SHORT
                ).show()

                repository.clearCurrentParticipant()

                val intent = Intent(this@TargetActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Error during exit", e)
                Toast.makeText(
                    this@TargetActivity,
                    "Error during exit",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        handler.removeCallbacksAndMessages(null)
        if (::inactivityHelper.isInitialized) {
            inactivityHelper.stopTimer()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "Touch DOWN at (${ev.rawX}, ${ev.rawY})")
                if (isTrialInProgress) {
                    movementTracker.recordOrigin(ev.rawX, ev.rawY)
                    updateCenterXPosition(ev.rawX, ev.rawY)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTrialInProgress) {
                    movementTracker.processMovement(ev.rawX, ev.rawY)
                    updateCenterXPosition(ev.rawX, ev.rawY)

                    // Check which circles are being hovered
                    updateHoveredCircles(ev.rawX, ev.rawY)
                }
            }
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "Touch UP at (${ev.rawX}, ${ev.rawY})")
                if (isTrialInProgress) {
                    movementTracker.processMovement(ev.rawX, ev.rawY)
                    handleTrialComplete()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupGlobalTouchTracking() {
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setOnTouchListener { _, event ->
            false
        }
    }

    private fun updateCenterXPosition(x: Float, y: Float) {
        // Move the X to follow the touch
        centerX.x = x - centerX.width / 2
        centerX.y = y - centerX.height / 2
    }

    private fun resetCenterXPosition() {
        // Reset X to center of screen
        centerX.post {
            val rootView = findViewById<View>(android.R.id.content)
            centerX.x = (rootView.width - centerX.width) / 2f
            centerX.y = (rootView.height - centerX.height) / 2f
        }
    }

    private fun updateHoveredCircles(x: Float, y: Float) {
        val newHoveredCircles = mutableSetOf<Int>()

        // Check each circle to see if the cursor is over it
        circles.forEachIndexed { index, circle ->
            val location = IntArray(2)
            circle.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]
            val right = left + circle.width
            val bottom = top + circle.height

            if (x >= left && x <= right && y >= top && y <= bottom) {
                newHoveredCircles.add(index)
            }
        }

        // Enlarge newly hovered circles
        newHoveredCircles.forEach { index ->
            if (!hoveredCircles.contains(index)) {
                enlargeCircle(index)
            }
        }

        // Shrink circles that are no longer hovered
        hoveredCircles.forEach { index ->
            if (!newHoveredCircles.contains(index)) {
                resetCircleSize(index)
            }
        }

        hoveredCircles.clear()
        hoveredCircles.addAll(newHoveredCircles)
    }

    private fun enlargeCircle(index: Int) {
        if (index !in circles.indices) return

        val circle = circles[index]
        circle.animate()
            .scaleX(ENLARGED_SCALE)
            .scaleY(ENLARGED_SCALE)
            .setDuration(100)
            .start()
    }

    private fun resetCircleSize(index: Int) {
        if (index !in circles.indices) return

        val circle = circles[index]
        circle.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(100)
            .start()
    }

    private fun resetAllCircleSizes() {
        circles.forEachIndexed { index, _ ->
            resetCircleSize(index)
        }
        hoveredCircles.clear()
    }

    private fun handleTrialComplete() {
        if (!isTrialInProgress) return

        val correctTargetReached = movementTracker.hasReachedCorrectTarget()
        val hasMovementData = movementTracker.hasSignificantMovement()

        if (!hasMovementData) {
            Log.w(TAG, "⚠️ No significant movement detected")
            Toast.makeText(this, "Please move to the target before releasing", Toast.LENGTH_SHORT).show()

            // Reset for retry
            resetCenterXPosition()
            resetAllCircleSizes()
            return
        }

        if (!correctTargetReached) {
            Log.w(TAG, "⚠️ Correct target not reached - trial continues")
            Toast.makeText(this, "Please reach the correct target", Toast.LENGTH_SHORT).show()

            // Reset cursor but keep trial going
            resetCenterXPosition()
            resetAllCircleSizes()
            return
        }

        val responseTime = System.currentTimeMillis()

        Log.d(TAG, "Trial complete - Correct target reached")

        isTrialInProgress = false
        saveTrialData(responseTime)

        // Wait a moment before starting next trial
        handler.postDelayed({
            resetCenterXPosition()
            resetAllCircleSizes()
            startNewTrial()
        }, 1500)
    }

    private fun startNewTrial() {
        if (isTrialInProgress) return

        currentTrialState = controller.startNewTrial()
        if (currentTrialState.isExperimentFinished) {
            completeExperiment()
            return
        }

        isTrialInProgress = true
        trialStartTime = System.currentTimeMillis()
        movementTracker.reset()
        resetCenterXPosition()
        resetAllCircleSizes()

        if (currentTrialState.targetCircleIndex in circles.indices) {
            val targetCircle = circles[currentTrialState.targetCircleIndex]
            targetCircle.post {
                val location = IntArray(2)
                targetCircle.getLocationOnScreen(location)
                movementTracker.setTargetBounds(
                    location[0],
                    location[1],
                    location[0] + targetCircle.width,
                    location[1] + targetCircle.height,
                    currentTrialState.targetCircleIndex
                )

                // Also set ALL circle bounds for hover detection
                circles.forEachIndexed { index, circle ->
                    val loc = IntArray(2)
                    circle.getLocationOnScreen(loc)
                    movementTracker.addCircleBounds(
                        index,
                        loc[0],
                        loc[1],
                        loc[0] + circle.width,
                        loc[1] + circle.height
                    )
                }

                Log.d(TAG, "Target bounds set for circle ${currentTrialState.targetCircleIndex}")
            }
        }

        Log.d(TAG, "Starting Trial ${currentTrialState.trialCount}: Type=${currentTrialState.trialType}, Target=${currentTrialState.targetCircleIndex}")

        updateTrialDisplay(currentTrialState)
        resetCirclesToNeutral()

        // Add 1 second delay before beeps
        handler.postDelayed({
            startBeepAndTargetSequence()
        }, 1000)
    }

    private fun startBeepAndTargetSequence() {
        for (i in 1..NUM_BEEPS) {
            val beepTime = (i - 1) * BEEP_INTERVAL_MS
            val isLastBeep = i == NUM_BEEPS
            handler.postDelayed({
                if (isLastBeep) {
                    val goTime = System.currentTimeMillis()
                    movementTracker.recordGoBeep(goTime)
                    Log.d(TAG, "GO BEEP at $goTime")
                    playGoBeep()
                } else {
                    playBeep()
                }
            }, beepTime)
        }

        val fourthBeepTime = (NUM_BEEPS - 1) * BEEP_INTERVAL_MS

        when (currentTrialState.trialType) {
            TrialType.PRE_JND -> {
                val initialShowTime = fourthBeepTime - PRE_BEEP_OFFSET_MS
                handler.postDelayed({
                    showTarget(currentTrialState.targetCircleIndex, currentTrialState.initialHue)
                }, initialShowTime)

                handler.postDelayed({
                    showTarget(currentTrialState.targetCircleIndex, currentTrialState.finalHue!!)
                }, fourthBeepTime)
            }
            TrialType.PRE_SUPRA -> {
                val initialShowTime = fourthBeepTime - PRE_BEEP_OFFSET_MS
                handler.postDelayed({
                    showTarget(currentTrialState.targetCircleIndex, currentTrialState.initialHue)
                }, initialShowTime)
            }
            TrialType.CONCURRENT_SUPRA -> {
                handler.postDelayed({
                    showTarget(currentTrialState.targetCircleIndex, currentTrialState.initialHue)
                }, fourthBeepTime)
            }
        }
    }

    private fun saveTrialData(responseTime: Long) {
        lifecycleScope.launch {
            try {
                val participantId = repository.getCurrentParticipantId()
                val movementData = movementTracker.getMovementData(responseTime)

                Log.d(TAG, "Saving trial data - Movement captured: " +
                        "hasFirstMovement=${movementData.firstMovementTimestamp != null}, " +
                        "pathPoints=${movementData.movementPath.length}")

                val trialResult = TargetTrialResult(
                    participantId = participantId,
                    trialNumber = currentTrialState.trialCount,
                    trialType = currentTrialState.trialType.name,
                    targetIndex = currentTrialState.targetCircleIndex,
                    trialStartTimestamp = trialStartTime,
                    firstMovementTimestamp = movementData.firstMovementTimestamp,
                    targetReachedTimestamp = movementData.targetReachedTimestamp,
                    responseTimestamp = responseTime,
                    reactionTime = movementData.reactionTime,
                    movementTime = movementData.movementTime,
                    totalResponseTime = movementData.totalResponseTime,
                    movementPath = movementData.movementPath,
                    pathLength = movementData.pathLength,
                    averageSpeed = movementData.averageSpeed,
                    initialHue = currentTrialState.initialHue,
                    finalHue = currentTrialState.finalHue,
                    goBeepTimestamp = movementData.goBeepTimestamp
                )

                database.targetTrialDao().insert(trialResult)
                Log.d(TAG, "✓ Trial ${currentTrialState.trialCount} data saved")

            } catch (e: Exception) {
                Log.e(TAG, "✗ Error saving trial data", e)
                Toast.makeText(this@TargetActivity, "Error saving trial data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTarget(index: Int, hue: Int) {
        if (index in circles.indices) {
            val colorRes = hueToColorRes[hue] ?: R.color.blue_140
            val color = ContextCompat.getColor(this, colorRes)
            circles[index].backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }

    private fun resetCirclesToNeutral() {
        val neutralColorRes = R.color.blue_140
        val color = ContextCompat.getColor(this, neutralColorRes)
        circles.forEach { it.backgroundTintList = android.content.res.ColorStateList.valueOf(color) }
    }

    private fun updateTrialDisplay(trialState: TargetTrialState) {
        val trialCounter = findViewById<TextView>(R.id.tvTrialCounter)
        trialCounter?.text = "TRIAL ${trialState.trialCount} OF ${trialState.totalTrials}"
        trialCounter?.textSize = 24f // Larger text
        trialCounter?.setTypeface(null, android.graphics.Typeface.BOLD) // Bold
    }

    private fun playBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    private fun playGoBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
    }

    // File: TargetActivity.kt

    private fun completeExperiment() {
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "TARGET EXPERIMENT COMPLETED")
        Log.d(TAG, "════════════════════════════════════════")

        lifecycleScope.launch {
            try {
                val participantId = repository.getCurrentParticipantId()
                val trialCount = database.targetTrialDao().getTrialCountForParticipant(participantId)
                Log.d(TAG, "✓ Total trials saved locally: $trialCount")

                Log.d(TAG, "→ Triggering Firebase upload...")
                WorkScheduler.triggerImmediateUpload(this@TargetActivity)

            } catch (e: Exception) {
                // Log the error but DO NOT stop the user flow with a Toast/finish()
                Log.e(TAG, "✗ Background error during final data processing/upload trigger.", e)
            }
        }

        val intent = Intent(this@TargetActivity, CompletionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        finish()
    }
}
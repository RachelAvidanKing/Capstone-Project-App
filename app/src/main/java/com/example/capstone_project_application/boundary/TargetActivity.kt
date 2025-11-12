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

/**
 * Main experiment activity for target-reaching trials.
 *
 * This activity implements the core experimental paradigm where participants
 * reach toward colored circles after audio cues. It tracks movement data,
 * reaction times, and target acquisition performance.
 *
 * ## Trial Types:
 * - **PRE_JND**: Target changes from JND threshold color to obvious color before go beep
 * - **PRE_SUPRA**: Target shows obvious color before go beep and remains
 * - **CONCURRENT_SUPRA**: Target shows obvious color at go beep
 *
 * ## Timing Sequence:
 * 1. 1-second preparation period
 * 2. Four beeps at 700ms intervals
 * 3. Target appears based on trial type
 * 4. Fourth beep = GO signal
 * 5. Participant moves to target
 *
 * ## Critical Performance Features:
 * - Throttled hover detection to reduce CPU usage
 * - Efficient movement tracking with pre-allocated collections
 * - Non-blocking UI updates
 *
 * Part of the Boundary layer in Entity-Boundary-Control pattern.
 */
class TargetActivity : AppCompatActivity() {

    // UI Components
    private lateinit var circles: List<View>
    private lateinit var centerX: TextView
    private lateinit var exitButton: Button
    private lateinit var trialCounterText: TextView

    // Controllers and Trackers
    private lateinit var controller: TargetController
    private lateinit var currentTrialState: TargetTrialState
    private val movementTracker = MovementTracker()

    // Data Management
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }
    private lateinit var inactivityHelper: InactivityHelper

    // Audio
    private var toneGenerator: ToneGenerator? = null

    // Handlers
    private val handler = Handler(Looper.getMainLooper())

    // State Management
    private var isTrialInProgress = false
    private var trialStartTime: Long = 0

    // Hover Detection State
    private val hoveredCircles = mutableSetOf<Int>()
    private var lastHoverCheckTime = 0L

    companion object {
        private const val TAG = "TargetActivity"

        // Timing Constants
        private const val BEEP_INTERVAL_MS = 700L
        private const val PRE_BEEP_OFFSET_MS = 350L
        private const val NUM_BEEPS = 4
        private const val PREPARATION_DELAY_MS = 1000L
        private const val INTER_TRIAL_DELAY_MS = 1500L

        // Visual Constants
        private const val ENLARGED_SCALE = 1.3f
        private const val HOVER_THROTTLE_MS = 16L // ~60 FPS

        // Special Hue Identifier
        private const val OBVIOUS_HUE_IDENTIFIER = 999
    }

    // Color mapping for different hues
    private val hueToColorRes = mapOf(
        OBVIOUS_HUE_IDENTIFIER to R.color.turquoise_obvious,
        141 to R.color.blue_141, 142 to R.color.blue_142, 143 to R.color.blue_143,
        144 to R.color.blue_144, 145 to R.color.blue_145, 150 to R.color.blue_150,
        155 to R.color.blue_155, 160 to R.color.blue_160, 165 to R.color.blue_165,
        175 to R.color.blue_175, 140 to R.color.blue_140
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_target)

        initializeViews()
        initializeAudio()
        initializeInactivityHelper()
        loadParticipantAndStartExperiment()
    }

    /**
     * Initializes all view references.
     */
    private fun initializeViews() {
        circles = listOf(
            findViewById(R.id.circleTopLeft),
            findViewById(R.id.circleTopRight),
            findViewById(R.id.circleBottomLeft),
            findViewById(R.id.circleBottomRight)
        )

        centerX = findViewById(R.id.centerX)
        exitButton = findViewById(R.id.btnExit)
        trialCounterText = findViewById(R.id.tvTrialCounter)

        exitButton.setOnClickListener {
            inactivityHelper.resetTimer()
            showExitConfirmationDialog()
        }
    }

    /**
     * Initializes the tone generator for audio feedback.
     */
    private fun initializeAudio() {
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    }

    /**
     * Initializes the inactivity helper for automatic logout.
     */
    private fun initializeInactivityHelper() {
        inactivityHelper = InactivityHelper(this, repository)
    }

    /**
     * Loads participant data and initializes the experiment controller.
     */
    private fun loadParticipantAndStartExperiment() {
        lifecycleScope.launch {
            val participant = repository.getCurrentParticipant()

            if (participant == null) {
                showErrorAndFinish("Error: Participant not found.")
                return@launch
            }

            if (participant.jndThreshold == null) {
                showErrorAndFinish("Error: JND threshold not calculated. Please complete JND test first.")
                return@launch
            }

            Log.d(TAG, "JND Threshold found: ${participant.jndThreshold}")
            controller = TargetController(participant.jndThreshold!!)
            inactivityHelper.resetTimer()
            startNewTrial()
        }
    }

    /**
     * Shows an error message and finishes the activity.
     */
    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
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
        releaseResources()
    }

    /**
     * Releases all resources (audio, handlers, timers).
     */
    private fun releaseResources() {
        toneGenerator?.release()
        handler.removeCallbacksAndMessages(null)
        inactivityHelper.stopTimer()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showExitConfirmationDialog()
    }

    // ===========================
    // Touch Event Handling
    // ===========================

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(ev)
            MotionEvent.ACTION_MOVE -> handleTouchMove(ev)
            MotionEvent.ACTION_UP -> handleTouchUp(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Handles touch down event (finger touches screen).
     */
    private fun handleTouchDown(event: MotionEvent) {
        if (!isTrialInProgress) return

        Log.d(TAG, "Touch DOWN at (${event.rawX}, ${event.rawY})")
        movementTracker.recordOrigin(event.rawX, event.rawY)
        updateCenterXPosition(event.rawX, event.rawY)
    }

    /**
     * Handles touch move event (finger moves on screen).
     * OPTIMIZATION: Throttles hover detection to reduce CPU usage.
     */
    private fun handleTouchMove(event: MotionEvent) {
        if (!isTrialInProgress) return

        movementTracker.processMovement(event.rawX, event.rawY)
        updateCenterXPosition(event.rawX, event.rawY)

        // CRITICAL OPTIMIZATION: Throttle hover detection
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastHoverCheckTime >= HOVER_THROTTLE_MS) {
            updateHoveredCircles(event.rawX, event.rawY)
            lastHoverCheckTime = currentTime
        }
    }

    /**
     * Handles touch up event (finger releases from screen).
     */
    private fun handleTouchUp(event: MotionEvent) {
        if (!isTrialInProgress) return

        Log.d(TAG, "Touch UP at (${event.rawX}, ${event.rawY})")
        movementTracker.processMovement(event.rawX, event.rawY)
        handleTrialComplete()
    }

    /**
     * Updates the center X cursor position to follow touch.
     */
    private fun updateCenterXPosition(x: Float, y: Float) {
        centerX.x = x - centerX.width / 2
        centerX.y = y - centerX.height / 2
    }

    /**
     * Resets the center X cursor to screen center.
     */
    private fun resetCenterXPosition() {
        centerX.post {
            val rootView = findViewById<View>(android.R.id.content)
            centerX.x = (rootView.width - centerX.width) / 2f
            centerX.y = (rootView.height - centerX.height) / 2f
        }
    }

    /**
     * Updates which circles are currently hovered.
     * Uses efficient set operations to minimize animation calls.
     */
    private fun updateHoveredCircles(x: Float, y: Float) {
        val newHoveredCircles = mutableSetOf<Int>()

        // Identify currently hovered circles
        circles.forEachIndexed { index, circle ->
            if (isPointInCircle(x, y, circle)) {
                newHoveredCircles.add(index)
            }
        }

        // Enlarge newly hovered circles
        newHoveredCircles.subtract(hoveredCircles).forEach { index ->
            enlargeCircle(index)
        }

        // Shrink circles no longer hovered
        hoveredCircles.subtract(newHoveredCircles).forEach { index ->
            resetCircleSize(index)
        }

        hoveredCircles.clear()
        hoveredCircles.addAll(newHoveredCircles)
    }

    /**
     * Checks if a point is within a circle's bounds.
     * OPTIMIZATION: Inline method to reduce method call overhead.
     */
    private inline fun isPointInCircle(x: Float, y: Float, circle: View): Boolean {
        val location = IntArray(2)
        circle.getLocationOnScreen(location)
        return x >= location[0] &&
                x <= location[0] + circle.width &&
                y >= location[1] &&
                y <= location[1] + circle.height
    }

    /**
     * Enlarges a circle with animation.
     */
    private fun enlargeCircle(index: Int) {
        if (index !in circles.indices) return

        circles[index].animate()
            .scaleX(ENLARGED_SCALE)
            .scaleY(ENLARGED_SCALE)
            .setDuration(100)
            .start()
    }

    /**
     * Resets circle size with animation.
     */
    private fun resetCircleSize(index: Int) {
        if (index !in circles.indices) return

        circles[index].animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(100)
            .start()
    }

    /**
     * Resets all circle sizes.
     */
    private fun resetAllCircleSizes() {
        circles.indices.forEach { resetCircleSize(it) }
        hoveredCircles.clear()
    }

    // ===========================
    // Trial Management
    // ===========================

    /**
     * Starts a new trial or completes the experiment.
     */
    private fun startNewTrial() {
        if (isTrialInProgress) return

        currentTrialState = controller.startNewTrial()

        if (currentTrialState.isExperimentFinished) {
            completeExperiment()
            return
        }

        initializeTrialState()
        setupTargetBounds()
        updateTrialDisplay()
        resetCirclesToNeutral()

        // Add preparation delay before beeps
        handler.postDelayed({
            startBeepAndTargetSequence()
        }, PREPARATION_DELAY_MS)
    }

    /**
     * Initializes state for a new trial.
     */
    private fun initializeTrialState() {
        isTrialInProgress = true
        trialStartTime = System.currentTimeMillis()
        movementTracker.reset()
        resetCenterXPosition()
        resetAllCircleSizes()

        Log.d(TAG, "Starting Trial ${currentTrialState.trialCount}: " +
                "Type=${currentTrialState.trialType}, " +
                "Target=${currentTrialState.targetCircleIndex}")
    }

    /**
     * Sets up target bounds for movement tracking.
     */
    private fun setupTargetBounds() {
        if (currentTrialState.targetCircleIndex !in circles.indices) return

        val targetCircle = circles[currentTrialState.targetCircleIndex]
        targetCircle.post {
            setTargetBoundsForTracker(targetCircle)
            setAllCircleBoundsForTracker()
        }
    }

    /**
     * Sets the target bounds in the movement tracker.
     */
    private fun setTargetBoundsForTracker(targetCircle: View) {
        val location = IntArray(2)
        targetCircle.getLocationOnScreen(location)

        movementTracker.setTargetBounds(
            location[0],
            location[1],
            location[0] + targetCircle.width,
            location[1] + targetCircle.height,
            currentTrialState.targetCircleIndex
        )

        Log.d(TAG, "Target bounds set for circle ${currentTrialState.targetCircleIndex}")
    }

    /**
     * Sets all circle bounds for hover detection.
     */
    private fun setAllCircleBoundsForTracker() {
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
    }

    /**
     * Starts the beep and target appearance sequence.
     */
    private fun startBeepAndTargetSequence() {
        scheduleBeeps()
        scheduleTargetAppearance()
    }

    /**
     * Schedules all beep sounds.
     */
    private fun scheduleBeeps() {
        for (i in 1..NUM_BEEPS) {
            val beepTime = (i - 1) * BEEP_INTERVAL_MS
            val isLastBeep = i == NUM_BEEPS

            handler.postDelayed({
                if (isLastBeep) {
                    playGoBeepAndRecord()
                } else {
                    playBeep()
                }
            }, beepTime)
        }
    }

    /**
     * Plays the go beep and records its timestamp.
     */
    private fun playGoBeepAndRecord() {
        val goTime = System.currentTimeMillis()
        movementTracker.recordGoBeep(goTime)
        Log.d(TAG, "GO BEEP at $goTime")
        playGoBeep()
    }

    /**
     * Schedules target appearance based on trial type.
     */
    private fun scheduleTargetAppearance() {
        val fourthBeepTime = (NUM_BEEPS - 1) * BEEP_INTERVAL_MS

        when (currentTrialState.trialType) {
            TrialType.PRE_JND -> schedulePreJndTarget(fourthBeepTime)
            TrialType.PRE_SUPRA -> schedulePreSupraTarget(fourthBeepTime)
            TrialType.CONCURRENT_SUPRA -> scheduleConcurrentSupraTarget(fourthBeepTime)
        }
    }

    /**
     * Schedules target appearance for PRE_JND trials.
     */
    private fun schedulePreJndTarget(fourthBeepTime: Long) {
        val initialShowTime = fourthBeepTime - PRE_BEEP_OFFSET_MS

        handler.postDelayed({
            showTarget(currentTrialState.targetCircleIndex, currentTrialState.initialHue)
        }, initialShowTime)

        handler.postDelayed({
            showTarget(currentTrialState.targetCircleIndex, currentTrialState.finalHue!!)
        }, fourthBeepTime)
    }

    /**
     * Schedules target appearance for PRE_SUPRA trials.
     */
    private fun schedulePreSupraTarget(fourthBeepTime: Long) {
        val initialShowTime = fourthBeepTime - PRE_BEEP_OFFSET_MS

        handler.postDelayed({
            showTarget(currentTrialState.targetCircleIndex, currentTrialState.initialHue)
        }, initialShowTime)
    }

    /**
     * Schedules target appearance for CONCURRENT_SUPRA trials.
     */
    private fun scheduleConcurrentSupraTarget(fourthBeepTime: Long) {
        handler.postDelayed({
            showTarget(currentTrialState.targetCircleIndex, currentTrialState.initialHue)
        }, fourthBeepTime)
    }

    /**
     * Handles trial completion logic.
     */
    private fun handleTrialComplete() {
        if (!isTrialInProgress) return

        if (!validateTrialCompletion()) {
            return
        }

        val responseTime = System.currentTimeMillis()
        Log.d(TAG, "Trial complete - Correct target reached")

        isTrialInProgress = false
        saveTrialData(responseTime)

        scheduleNextTrial()
    }

    /**
     * Validates that the trial can be completed.
     * Returns false and resets if validation fails.
     */
    private fun validateTrialCompletion(): Boolean {
        if (!movementTracker.hasSignificantMovement()) {
            Log.w(TAG, "⚠️ No significant movement detected")
            showToast("Please move to the target before releasing")
            resetForRetry()
            return false
        }

        if (!movementTracker.hasReachedCorrectTarget()) {
            Log.w(TAG, "⚠️ Correct target not reached")
            showToast("Please reach the correct target")
            resetForRetry()
            return false
        }

        return true
    }

    /**
     * Resets trial state for retry without counting as complete.
     */
    private fun resetForRetry() {
        resetCenterXPosition()
        resetAllCircleSizes()
    }

    /**
     * Schedules the next trial after a delay.
     */
    private fun scheduleNextTrial() {
        handler.postDelayed({
            resetCenterXPosition()
            resetAllCircleSizes()
            startNewTrial()
        }, INTER_TRIAL_DELAY_MS)
    }

    /**
     * Saves trial data to the database.
     */
    private fun saveTrialData(responseTime: Long) {
        lifecycleScope.launch {
            try {
                val participantId = repository.getCurrentParticipantId()
                val movementData = movementTracker.getMovementData(responseTime)

                Log.d(TAG, "Saving trial data - Movement captured: " +
                        "hasFirstMovement=${movementData.firstMovementTimestamp != null}, " +
                        "pathPoints=${movementData.movementPath.length}")

                val trialResult = buildTrialResult(participantId, movementData, responseTime)

                database.targetTrialDao().insert(trialResult)
                Log.d(TAG, "✓ Trial ${currentTrialState.trialCount} data saved")

            } catch (e: Exception) {
                Log.e(TAG, "✗ Error saving trial data", e)
                showToast("Error saving trial data")
            }
        }
    }

    /**
     * Builds a TargetTrialResult object from movement data.
     */
    private fun buildTrialResult(
        participantId: String,
        movementData: com.example.capstone_project_application.control.TrialMovementData,
        responseTime: Long
    ): TargetTrialResult {
        return TargetTrialResult(
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
    }

    // ===========================
    // Visual Updates
    // ===========================

    /**
     * Shows a target circle with the specified hue.
     */
    private fun showTarget(index: Int, hue: Int) {
        if (index in circles.indices) {
            val colorRes = hueToColorRes[hue] ?: R.color.blue_140
            val color = ContextCompat.getColor(this, colorRes)
            circles[index].backgroundTintList =
                android.content.res.ColorStateList.valueOf(color)
        }
    }

    /**
     * Resets all circles to neutral color.
     */
    private fun resetCirclesToNeutral() {
        val color = ContextCompat.getColor(this, R.color.blue_140)
        val colorState = android.content.res.ColorStateList.valueOf(color)
        circles.forEach { it.backgroundTintList = colorState }
    }

    /**
     * Updates the trial counter display.
     */
    private fun updateTrialDisplay() {
        trialCounterText.text = "TRIAL ${currentTrialState.trialCount} OF ${currentTrialState.totalTrials}"
        trialCounterText.textSize = 24f
        trialCounterText.setTypeface(null, android.graphics.Typeface.BOLD)
    }

    // ===========================
    // Audio Methods
    // ===========================

    /**
     * Plays a regular beep sound.
     */
    private fun playBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    /**
     * Plays the "GO" beep sound (different tone).
     */
    private fun playGoBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
    }

    // ===========================
    // Exit Handling
    // ===========================

    /**
     * Shows exit confirmation dialog.
     */
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Experiment?")
            .setMessage("Your target trial progress will NOT be saved. " +
                    "You will need to restart this experiment when you return.")
            .setPositiveButton("Exit") { _, _ ->
                handleExit()
            }
            .setNegativeButton("Continue Experiment", null)
            .show()
    }

    /**
     * Handles exit by clearing data and returning to login.
     */
    private fun handleExit() {
        lifecycleScope.launch {
            try {
                isTrialInProgress = false
                handler.removeCallbacksAndMessages(null)
                repository.clearIncompleteTrialData()

                showToast("Exiting. Your JND threshold is saved.")
                repository.clearCurrentParticipant()

                navigateToLogin()
            } catch (e: Exception) {
                Log.e(TAG, "Error during exit", e)
                showToast("Error during exit")
            }
        }
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
    // Experiment Completion
    // ===========================

    /**
     * Completes the experiment and triggers data upload.
     */
    private fun completeExperiment() {
        Log.d(TAG, "╔════════════════════════════════════════╗")
        Log.d(TAG, "TARGET EXPERIMENT COMPLETED")
        Log.d(TAG, "╚════════════════════════════════════════╝")

        lifecycleScope.launch {
            try {
                val participantId = repository.getCurrentParticipantId()
                val trialCount = database.targetTrialDao()
                    .getTrialCountForParticipant(participantId)
                Log.d(TAG, "✓ Total trials saved locally: $trialCount")

                Log.d(TAG, "→ Triggering Firebase upload...")
                try {
                    WorkScheduler.triggerImmediateUpload(this@TargetActivity)
                    Log.d(TAG, "✓ Upload job scheduled successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠ Could not schedule upload (will retry later): ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "✗ Error during completion processing", e)
            }
        }

        navigateToCompletionScreen()
    }

    /**
     * Navigates to completion screen.
     */
    private fun navigateToCompletionScreen() {
        val intent = Intent(this, CompletionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    /**
     * Helper method to show toast messages.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
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
 * Implements the experimental paradigm where participants reach toward colored
 * circles after audio cues. Tracks movement data, reaction times, and target
 * acquisition performance.
 *
 * ## Trial Types:
 * - **PRE_JND**: JND threshold hint for 25ms at -350ms, then neutral, then obvious at beep
 * - **PRE_SUPRA**: Obvious hint for 25ms at -350ms, then neutral, then obvious at beep
 * - **CONCURRENT_SUPRA**: Obvious color appears at beep
 *
 * ## Timing Sequence:
 * 1. 1-second preparation period
 * 2. Four beeps at 700ms intervals
 * 3. Target hint appears at -350ms (PRE trials only) for 25ms
 * 4. Target returns to neutral after hint
 * 5. Fourth beep = GO signal
 * 6. Target becomes obvious at beep (all trial types)
 * 7. Participant moves to target
 */
class TargetActivity : AppCompatActivity() {

    private lateinit var circles: List<View>
    private lateinit var centerX: TextView
    private lateinit var exitButton: Button
    private lateinit var trialCounterText: TextView

    private lateinit var controller: TargetController
    private lateinit var currentTrialState: TargetTrialState
    private val movementTracker = MovementTracker()

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }
    private lateinit var inactivityHelper: InactivityHelper

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())

    private var isTrialInProgress = false
    private var isPracticeMode = true
    private var isTransitioning = false
    private var practiceTrialNumber = 0
    private var trialStartTime: Long = 0

    private val hoveredCircles = mutableSetOf<Int>()
    private var lastHoverCheckTime = 0L

    companion object {
        private const val TAG = "TargetActivity"

        private const val BEEP_INTERVAL_MS = 700L
        private const val HINT_OFFSET_MS = 350L
        private const val HINT_DURATION_MS = 25L
        private const val NUM_BEEPS = 4
        private const val PREPARATION_DELAY_MS = 1000L
        private const val INTER_TRIAL_DELAY_MS = 1500L
        private const val TRANSITION_PAUSE_MS = 3000L

        private const val ENLARGED_SCALE = 1.3f
        private const val HOVER_THROTTLE_MS = 16L

        private const val PRACTICE_TRIAL_COUNT = 1
    }

    private val hueToColorRes = mapOf(
        TargetController.OBVIOUS_HUE_IDENTIFIER to R.color.turquoise_obvious,
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

    private fun initializeAudio() {
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    }

    private fun initializeInactivityHelper() {
        inactivityHelper = InactivityHelper(this, repository)
    }

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
            startSequence()
        }
    }

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

    private fun handleTouchDown(event: MotionEvent) {
        if (!isTrialInProgress) return

        Log.d(TAG, "Touch DOWN at (${event.rawX}, ${event.rawY})")
        movementTracker.recordOrigin(event.rawX, event.rawY)
        updateCenterXPosition(event.rawX, event.rawY)
    }

    private fun handleTouchMove(event: MotionEvent) {
        if (!isTrialInProgress) return

        movementTracker.processMovement(event.rawX, event.rawY)
        updateCenterXPosition(event.rawX, event.rawY)

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastHoverCheckTime >= HOVER_THROTTLE_MS) {
            updateHoveredCircles(event.rawX, event.rawY)
            lastHoverCheckTime = currentTime
        }
    }

    private fun handleTouchUp(event: MotionEvent) {
        if (!isTrialInProgress) return

        Log.d(TAG, "Touch UP at (${event.rawX}, ${event.rawY})")
        movementTracker.processMovement(event.rawX, event.rawY)
        handleTrialComplete()
    }

    private fun updateCenterXPosition(x: Float, y: Float) {
        centerX.x = x - centerX.width / 2
        centerX.y = y - centerX.height / 2
    }

    private fun resetCenterXPosition() {
        centerX.post {
            val rootView = findViewById<View>(android.R.id.content)
            centerX.x = (rootView.width - centerX.width) / 2f
            centerX.y = (rootView.height - centerX.height) / 2f
        }
    }

    private fun updateHoveredCircles(x: Float, y: Float) {
        val newHoveredCircles = mutableSetOf<Int>()

        circles.forEachIndexed { index, circle ->
            if (isPointInCircle(x, y, circle)) {
                newHoveredCircles.add(index)
            }
        }

        newHoveredCircles.subtract(hoveredCircles).forEach { index ->
            enlargeCircle(index)
        }

        hoveredCircles.subtract(newHoveredCircles).forEach { index ->
            resetCircleSize(index)
        }

        hoveredCircles.clear()
        hoveredCircles.addAll(newHoveredCircles)
    }

    private inline fun isPointInCircle(x: Float, y: Float, circle: View): Boolean {
        val location = IntArray(2)
        circle.getLocationOnScreen(location)
        return x >= location[0] &&
                x <= location[0] + circle.width &&
                y >= location[1] &&
                y <= location[1] + circle.height
    }

    private fun enlargeCircle(index: Int) {
        if (index !in circles.indices) return

        circles[index].animate()
            .scaleX(ENLARGED_SCALE)
            .scaleY(ENLARGED_SCALE)
            .setDuration(100)
            .start()
    }

    private fun resetCircleSize(index: Int) {
        if (index !in circles.indices) return

        circles[index].animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(100)
            .start()
    }

    private fun resetAllCircleSizes() {
        circles.indices.forEach { resetCircleSize(it) }
        hoveredCircles.clear()
    }

    // ===========================
    // Trial Management
    // ===========================

    private fun startExperimentTrial() {
        if (isTrialInProgress || isTransitioning) return

        currentTrialState = controller.startNewTrial()

        if (currentTrialState.isExperimentFinished) {
            completeExperiment()
            return
        }

        updateTrialDisplay()
        runTrial(currentTrialState)
    }

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

    private fun setupTargetBounds() {
        if (currentTrialState.targetCircleIndex !in circles.indices) return

        val targetCircle = circles[currentTrialState.targetCircleIndex]
        targetCircle.post {
            setTargetBoundsForTracker(targetCircle)
            setAllCircleBoundsForTracker()
        }
    }

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
     * Schedules beeps and target appearance/disappearance based on trial type.
     *
     * Timeline for PRE trials:
     * - t=-350ms: Show hint color
     * - t=-325ms: Return to neutral
     * - t=0ms: Show obvious color + GO beep
     *
     * Timeline for CONCURRENT_SUPRA:
     * - t=0ms: Show obvious color + GO beep
     */
    private fun startBeepAndTargetSequence() {
        scheduleBeeps()
        scheduleTargetAppearance()
    }

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

    private fun playGoBeepAndRecord() {
        val goTime = System.currentTimeMillis()
        movementTracker.recordGoBeep(goTime)
        Log.d(TAG, "GO BEEP at $goTime")
        playGoBeep()
    }

    private fun scheduleTargetAppearance() {
        val goBeepTime = (NUM_BEEPS - 1) * BEEP_INTERVAL_MS

        when (currentTrialState.trialType) {
            TrialType.PRE_JND, TrialType.PRE_SUPRA -> schedulePreTrialSequence(goBeepTime)
            TrialType.CONCURRENT_SUPRA -> scheduleConcurrentTrial(goBeepTime)
        }
    }

    /**
     * Schedules the PRE trial sequence:
     * 1. Show hint at -350ms
     * 2. Return to neutral at -325ms (after 25ms)
     * 3. Show obvious at 0ms (GO beep)
     */
    private fun schedulePreTrialSequence(goBeepTime: Long) {
        val hintTime = goBeepTime - HINT_OFFSET_MS
        val neutralTime = hintTime + HINT_DURATION_MS

        handler.postDelayed({
            currentTrialState.hintHue?.let { hue ->
                showTarget(currentTrialState.targetCircleIndex, hue)
            }
        }, hintTime)

        handler.postDelayed({
            showTarget(currentTrialState.targetCircleIndex, TargetController.NEUTRAL_HUE)
        }, neutralTime)

        handler.postDelayed({
            showTarget(currentTrialState.targetCircleIndex, currentTrialState.finalHue)
        }, goBeepTime)
    }

    /**
     * Schedules the CONCURRENT_SUPRA trial: show obvious at GO beep only.
     */
    private fun scheduleConcurrentTrial(goBeepTime: Long) {
        handler.postDelayed({
            showTarget(currentTrialState.targetCircleIndex, currentTrialState.finalHue)
        }, goBeepTime)
    }

    private fun handleTrialComplete() {
        if (!isTrialInProgress) return

        if (!validateTrialCompletion()) {
            return
        }

        val responseTime = System.currentTimeMillis()
        Log.d(TAG, "Trial complete - Correct target reached")

        isTrialInProgress = false

        if (isPracticeMode) {
            Log.d(TAG, "PRACTICE TRIAL complete. Data not recorded.")
            scheduleNextTrial()
        } else {
            saveTrialData(responseTime)
            scheduleNextTrial()
        }
    }

    private fun validateTrialCompletion(): Boolean {
        if (!movementTracker.hasSignificantMovement()) {
            Log.w(TAG, "No significant movement detected")
            showToast("Please move to the target before releasing")
            resetForRetry()
            return false
        }

        if (!movementTracker.hasReachedCorrectTarget()) {
            Log.w(TAG, "Correct target not reached")
            showToast("Please reach the correct target")
            resetForRetry()
            return false
        }

        return true
    }

    private fun resetForRetry() {
        resetCenterXPosition()
        resetAllCircleSizes()
    }

    private fun scheduleNextTrial() {
        handler.postDelayed({
            resetCenterXPosition()
            resetAllCircleSizes()

            if (isPracticeMode) {
                if (practiceTrialNumber < PRACTICE_TRIAL_COUNT) {
                    startNextPracticeTrial()
                } else {
                    isPracticeMode = false
                    isTransitioning = true
                    showTransitionScreen()
                }
            } else {
                if (!isTransitioning) {
                    startExperimentTrial()
                }
            }
        }, INTER_TRIAL_DELAY_MS)
    }

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
                Log.d(TAG, "Trial ${currentTrialState.trialCount} data saved")

            } catch (e: Exception) {
                Log.e(TAG, "Error saving trial data", e)
                showToast("Error saving trial data")
            }
        }
    }

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
            initialHue = currentTrialState.hintHue,
            finalHue = currentTrialState.finalHue,
            goBeepTimestamp = movementData.goBeepTimestamp
        )
    }

    private fun runTrial(trialState: TargetTrialState) {
        if (isTrialInProgress) return

        currentTrialState = trialState

        initializeTrialState()
        setupTargetBounds()
        resetCirclesToNeutral()

        handler.postDelayed({
            startBeepAndTargetSequence()
        }, PREPARATION_DELAY_MS)
    }

    // ===========================
    // Practice Mode
    // ===========================

    private fun startSequence() {
        if (isPracticeMode) {
            startNextPracticeTrial()
        } else {
            startExperimentTrial()
        }
    }

    private fun startNextPracticeTrial() {
        practiceTrialNumber++

        val mockState = TargetTrialState(
            trialCount = practiceTrialNumber,
            totalTrials = PRACTICE_TRIAL_COUNT,
            targetCircleIndex = 0,
            trialType = TrialType.CONCURRENT_SUPRA,
            hintHue = null,
            finalHue = TargetController.OBVIOUS_HUE_IDENTIFIER,
            isExperimentFinished = false
        )

        updatePracticeTrialDisplay()
        runTrial(mockState)
    }

    private fun showTransitionScreen() {
        handler.removeCallbacksAndMessages(null)

        trialCounterText.text = "Practice Complete!"
        trialCounterText.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_green_light)
        )

        AlertDialog.Builder(this)
            .setTitle("Ready for the Main Experiment")
            .setMessage("You have finished the practice trial.\n\n" +
                    "The main experiment will now begin. You will complete 15 trials.\n\n" +
                    "Please place your finger on the central 'X'. " +
                    "The first trial will start 3 seconds after you tap OK.")
            .setPositiveButton("OK - Start Countdown") { dialog, _ ->
                dialog.dismiss()
                startExperimentCountdown()
            }
            .setCancelable(false)
            .show()
    }

    private fun startExperimentCountdown() {
        val totalSeconds = 3
        var remainingSeconds = totalSeconds

        val countdownRunnable = object : Runnable {
            override fun run() {
                if (remainingSeconds > 0) {
                    trialCounterText.text = "STARTING IN $remainingSeconds..."
                    trialCounterText.setBackgroundColor(
                        ContextCompat.getColor(this@TargetActivity, android.R.color.holo_orange_light)
                    )
                    trialCounterText.textSize = 28f
                    remainingSeconds--
                    handler.postDelayed(this, 1000L)
                } else {
                    isTransitioning = false
                    startExperimentTrial()
                }
            }
        }

        handler.post(countdownRunnable)
    }

    // ===========================
    // Visual Updates
    // ===========================

    private fun showTarget(index: Int, hue: Int) {
        if (index in circles.indices) {
            val colorRes = hueToColorRes[hue] ?: R.color.blue_140
            val color = ContextCompat.getColor(this, colorRes)
            circles[index].backgroundTintList =
                android.content.res.ColorStateList.valueOf(color)
        }
    }

    private fun resetCirclesToNeutral() {
        val color = ContextCompat.getColor(this, R.color.blue_140)
        val colorState = android.content.res.ColorStateList.valueOf(color)
        circles.forEach { it.backgroundTintList = colorState }
    }

    private fun updatePracticeTrialDisplay() {
        trialCounterText.text = "PRACTICE - Not recorded"
        trialCounterText.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_orange_light)
        )
        trialCounterText.textSize = 24f
        trialCounterText.setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun updateTrialDisplay() {
        val countText = if (::currentTrialState.isInitialized) {
            "TRIAL ${currentTrialState.trialCount} OF ${currentTrialState.totalTrials}"
        } else {
            "TRIAL 1 OF ${controller.getTotalTrials()}"
        }

        trialCounterText.text = countText
        trialCounterText.setBackgroundResource(R.drawable.trial_counter_bg)
        trialCounterText.textSize = 24f
        trialCounterText.setTypeface(null, android.graphics.Typeface.BOLD)
    }

    // ===========================
    // Audio Methods
    // ===========================

    private fun playBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    private fun playGoBeep() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
    }

    // ===========================
    // Exit Handling
    // ===========================

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

    private fun completeExperiment() {
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "TARGET EXPERIMENT COMPLETED")
        Log.d(TAG, "═══════════════════════════════════════════")

        lifecycleScope.launch {
            try {
                val participantId = repository.getCurrentParticipantId()
                val trialCount = database.targetTrialDao()
                    .getTrialCountForParticipant(participantId)
                Log.d(TAG, "Total trials saved locally: $trialCount")

                Log.d(TAG, "Triggering Firebase upload...")
                try {
                    WorkScheduler.triggerImmediateUpload(this@TargetActivity)
                    Log.d(TAG, "Upload job scheduled successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not schedule upload (will retry later): ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during completion processing", e)
            }
        }

        navigateToCompletionScreen()
    }

    private fun navigateToCompletionScreen() {
        val intent = Intent(this, CompletionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
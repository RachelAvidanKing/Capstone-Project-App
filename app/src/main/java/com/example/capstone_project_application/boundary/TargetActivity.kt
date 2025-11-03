package com.example.capstone_project_application.boundary

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.capstone_project_application.R
import com.example.capstone_project_application.control.MovementTracker
import com.example.capstone_project_application.control.TargetController
import com.example.capstone_project_application.control.TargetTrialState
import com.example.capstone_project_application.control.TrialType
import com.example.capstone_project_application.control.WorkScheduler
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

    private var toneGenerator: ToneGenerator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isTrialInProgress = false
    private var trialStartTime: Long = 0

    private val BEEP_INTERVAL_MS = 700L
    private val PRE_BEEP_OFFSET_MS = 350L
    private val NUM_BEEPS = 4

    private val hueToColorRes = mapOf(
        999 to R.color.blue_obvious,
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

        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

        // Set up global touch tracking on the root view
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
            startNewTrial()
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

    /**
     * Intercept touch events at the activity level to ensure we capture all movement
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "DISPATCH: Touch DOWN at (${ev.rawX}, ${ev.rawY})")
                movementTracker.recordOrigin(ev.rawX, ev.rawY)
            }
            MotionEvent.ACTION_MOVE -> {
                Log.v(TAG, "DISPATCH: ACTION_MOVE at (${ev.rawX}, ${ev.rawY})")
                if (isTrialInProgress) {
                    val moved = movementTracker.processMovement(ev.rawX, ev.rawY)
                    if (moved) {
                        Log.d(TAG, "DISPATCH: ✓ Movement recorded")
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "DISPATCH: Touch UP at (${ev.rawX}, ${ev.rawY})")
                if (isTrialInProgress) {
                    movementTracker.processMovement(ev.rawX, ev.rawY)
                }
            }
        }

        // Always pass the event to the superclass
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Set up global touch tracking that doesn't interfere with click events
     */
    private fun setupGlobalTouchTracking() {
        val rootView = findViewById<View>(android.R.id.content)

        rootView.setOnTouchListener { _, event ->
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "Touch DOWN at (${event.rawX}, ${event.rawY})")
                    movementTracker.recordOrigin(event.rawX, event.rawY)
                }
                MotionEvent.ACTION_MOVE -> {
                    Log.v(TAG, "ACTION_MOVE detected at (${event.rawX}, ${event.rawY}), trial in progress: $isTrialInProgress")
                    if (isTrialInProgress) {
                        val moved = movementTracker.processMovement(event.rawX, event.rawY)
                        if (moved) {
                            Log.d(TAG, "✓ Movement tracked: (${event.rawX}, ${event.rawY})")
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    Log.d(TAG, "Touch UP at (${event.rawX}, ${event.rawY})")
                    // Process final position
                    if (isTrialInProgress) {
                        movementTracker.processMovement(event.rawX, event.rawY)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    Log.d(TAG, "ACTION_POINTER_DOWN")
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    Log.d(TAG, "ACTION_POINTER_UP")
                }
            }
            // CRITICAL: Return false to not consume the event
            // This allows click listeners on circles to still work
            false
        }
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

        // Set target bounds for movement tracking
        if (currentTrialState.targetCircleIndex in circles.indices) {
            val targetCircle = circles[currentTrialState.targetCircleIndex]
            // Post to ensure the view is laid out
            targetCircle.post {
                val location = IntArray(2)
                targetCircle.getLocationOnScreen(location)
                movementTracker.setTargetBounds(
                    location[0],
                    location[1],
                    location[0] + targetCircle.width,
                    location[1] + targetCircle.height
                )
                Log.d(TAG, "Target bounds set for circle ${currentTrialState.targetCircleIndex}: " +
                        "x=${location[0]}-${location[0] + targetCircle.width}, " +
                        "y=${location[1]}-${location[1] + targetCircle.height}")
            }
        }

        Log.d(TAG, "Starting Trial ${currentTrialState.trialCount}: Type=${currentTrialState.trialType}, Target=${currentTrialState.targetCircleIndex}")

        updateTrialDisplay(currentTrialState)
        resetCirclesToNeutral()
        startBeepAndTargetSequence()
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

    private fun handleCircleClick(clickedIndex: Int) {
        if (!isTrialInProgress) return

        val responseTime = System.currentTimeMillis()
        val isCorrect = controller.checkUserResponse(clickedIndex, currentTrialState.targetCircleIndex)
        Log.d(TAG, "Circle $clickedIndex clicked. Correct: $isCorrect")

        isTrialInProgress = false

        saveTrialData(clickedIndex, isCorrect, responseTime)

        handler.postDelayed({ startNewTrial() }, 1000)
    }

    private fun saveTrialData(selectedIndex: Int, isCorrect: Boolean, responseTime: Long) {
        lifecycleScope.launch {
            try {
                val participantId = repository.getCurrentParticipantId()
                val movementData = movementTracker.getMovementData(responseTime)

                Log.d(TAG, "Saving trial data - Movement captured: " +
                        "hasFirstMovement=${movementData.firstMovementTimestamp != null}, " +
                        "pathPoints=${movementData.movementPath.length}, " +
                        "pathLength=${movementData.pathLength}")

                val trialResult = TargetTrialResult(
                    participantId = participantId,
                    trialNumber = currentTrialState.trialCount,
                    trialType = currentTrialState.trialType.name,
                    targetIndex = currentTrialState.targetCircleIndex,
                    selectedIndex = selectedIndex,
                    isCorrect = isCorrect,
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
                Log.d(TAG, "✓ Trial ${currentTrialState.trialCount} data saved to Room database")

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
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "TARGET EXPERIMENT COMPLETED")
        Log.d(TAG, "════════════════════════════════════════")

        lifecycleScope.launch {
            try {
                val participantId = repository.getCurrentParticipantId()
                val trialCount = database.targetTrialDao().getTrialCountForParticipant(participantId)
                Log.d(TAG, "✓ Total trials saved: $trialCount")

                Log.d(TAG, "→ Triggering Firebase upload...")
                WorkScheduler.triggerImmediateUpload(this@TargetActivity)

                Toast.makeText(this@TargetActivity, "Experiment complete! Uploading data...", Toast.LENGTH_LONG).show()

                handler.postDelayed({
                    finish()
                }, 2000)

            } catch (e: Exception) {
                Log.e(TAG, "✗ Error completing experiment", e)
                Toast.makeText(this@TargetActivity, "Error completing experiment", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
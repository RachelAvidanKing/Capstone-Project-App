package com.example.capstone_project_application.control

import android.graphics.Rect
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Tracks participant movement during target trials.
 *
 * This class records touch movement data and calculates various performance metrics:
 * - Reaction time (time from go beep to first movement)
 * - Movement time (time from first movement to target reached)
 * - Movement path and distance
 * - Average movement speed
 *
 * ## Usage:
 * 1. Call [reset] at the start of each trial
 * 2. Call [recordGoBeep] when the go signal is given
 * 3. Call [recordOrigin] on ACTION_DOWN
 * 4. Call [processMovement] on ACTION_MOVE and ACTION_UP
 * 5. Call [getMovementData] to retrieve metrics
 *
 * ## Performance Notes:
 * - Uses ArrayList for efficient path recording
 * - Minimizes object allocation during movement processing
 * - Implements threshold-based movement detection to filter noise
 *
 * Part of the Control layer in Entity-Boundary-Control pattern.
 */
class MovementTracker {

    // Origin coordinates (where finger first touches)
    private var originX: Float = 0f
    private var originY: Float = 0f

    // Last recorded position (for optimization)
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    // Timing data
    private var goBeepTime: Long = 0
    private var firstMovementTime: Long? = null
    private var targetReachedTime: Long? = null

    // State flags
    private var hasMovementStarted = false

    // Movement path storage (Triple: x, y, timestamp)
    private val movementPath = ArrayList<Triple<Float, Float, Long>>(256) // Pre-allocate for efficiency

    // Target bounds for hit detection
    private var targetBounds: Rect? = null
    private var correctTargetIndex: Int = INVALID_INDEX

    // All circle bounds for hover detection
    private val allCircleBounds = HashMap<Int, Rect>(4) // 4 circles

    companion object {
        private const val MOVEMENT_THRESHOLD_PX = 5f
        private const val INVALID_INDEX = -1
        private const val TAG = "MovementTracker"
    }

    /**
     * Resets the tracker for a new trial.
     * Clears all recorded data and state flags.
     */
    fun reset() {
        originX = 0f
        originY = 0f
        lastX = 0f
        lastY = 0f
        goBeepTime = 0
        firstMovementTime = null
        targetReachedTime = null
        hasMovementStarted = false
        movementPath.clear()
        targetBounds = null
        correctTargetIndex = INVALID_INDEX
        allCircleBounds.clear()
        Log.d(TAG, "Tracker reset for new trial")
    }

    /**
     * Sets the correct target bounds for this trial.
     *
     * @param left Left edge of target in screen coordinates
     * @param top Top edge of target in screen coordinates
     * @param right Right edge of target in screen coordinates
     * @param bottom Bottom edge of target in screen coordinates
     * @param index Target circle index (0-3)
     */
    fun setTargetBounds(left: Int, top: Int, right: Int, bottom: Int, index: Int) {
        targetBounds = Rect(left, top, right, bottom)
        correctTargetIndex = index
        Log.d(TAG, "Correct target bounds set for index $index: $targetBounds")
    }

    /**
     * Adds bounds for a circle (used for hover detection).
     *
     * @param index Circle index (0-3)
     * @param left Left edge in screen coordinates
     * @param top Top edge in screen coordinates
     * @param right Right edge in screen coordinates
     * @param bottom Bottom edge in screen coordinates
     */
    fun addCircleBounds(index: Int, left: Int, top: Int, right: Int, bottom: Int) {
        allCircleBounds[index] = Rect(left, top, right, bottom)
    }

    /**
     * Records the origin point where the user first touches the screen.
     *
     * @param x X-coordinate in screen space
     * @param y Y-coordinate in screen space
     */
    fun recordOrigin(x: Float, y: Float) {
        originX = x
        originY = y
        lastX = x
        lastY = y
        Log.d(TAG, "Origin recorded: ($x, $y)")
    }

    /**
     * Records the timestamp when the go beep is played.
     * This is used as the reference point for reaction time calculations.
     *
     * @param timestamp The go beep timestamp in milliseconds
     */
    fun recordGoBeep(timestamp: Long) {
        goBeepTime = timestamp
        Log.d(TAG, "Go beep recorded at: $timestamp")
    }

    /**
     * Processes a movement event and updates tracking data.
     *
     * This method:
     * 1. Detects when significant movement starts (beyond threshold)
     * 2. Records the movement path
     * 3. Detects when the correct target is reached
     *
     * @param x Current X-coordinate
     * @param y Current Y-coordinate
     * @return true if this is significant movement, false otherwise
     */
    fun processMovement(x: Float, y: Float): Boolean {
        // Ignore movements before go beep
        if (goBeepTime == 0L) {
            Log.v(TAG, "Movement ignored: goBeepTime not set yet")
            return false
        }

        val currentTime = System.currentTimeMillis()
        val distanceFromOrigin = calculateDistance(x, y, originX, originY)

        // Detect movement start
        if (!hasMovementStarted && distanceFromOrigin > MOVEMENT_THRESHOLD_PX) {
            markMovementStarted(currentTime, distanceFromOrigin)
        }

        // Record path and check target reach if movement has started
        if (hasMovementStarted) {
            recordPathPoint(x, y, currentTime)
            checkTargetReached(x, y, currentTime)
            updateLastPosition(x, y)
            return true
        } else if (distanceFromOrigin > 5) {
            Log.v(TAG, "Movement detected but below threshold: ${distanceFromOrigin}px (need ${MOVEMENT_THRESHOLD_PX}px)")
        }

        return false
    }

    /**
     * Marks the movement as started and records the first movement time.
     *
     * @param currentTime Current timestamp
     * @param distanceFromOrigin Distance moved from origin
     */
    private fun markMovementStarted(currentTime: Long, distanceFromOrigin: Float) {
        hasMovementStarted = true
        firstMovementTime = currentTime
        Log.d(TAG, "✓ Movement started: ${currentTime - goBeepTime}ms after go beep, " +
                "distance=${distanceFromOrigin}px from origin")
    }

    /**
     * Records a point in the movement path.
     *
     * @param x X-coordinate
     * @param y Y-coordinate
     * @param timestamp Current timestamp
     */
    private fun recordPathPoint(x: Float, y: Float, timestamp: Long) {
        movementPath.add(Triple(x, y, timestamp))
    }

    /**
     * Checks if the correct target has been reached.
     *
     * @param x Current X-coordinate
     * @param y Current Y-coordinate
     * @param currentTime Current timestamp
     */
    private fun checkTargetReached(x: Float, y: Float, currentTime: Long) {
        if (targetReachedTime == null && targetBounds != null) {
            if (targetBounds!!.contains(x.toInt(), y.toInt())) {
                targetReachedTime = currentTime
                Log.d(TAG, "✓ CORRECT target reached: $correctTargetIndex at " +
                        "${currentTime - goBeepTime}ms")
            }
        }
    }

    /**
     * Updates the last known position.
     *
     * @param x X-coordinate
     * @param y Y-coordinate
     */
    private fun updateLastPosition(x: Float, y: Float) {
        lastX = x
        lastY = y
    }

    /**
     * Calculates Euclidean distance between two points.
     *
     * @param x1 First point X
     * @param y1 First point Y
     * @param x2 Second point X
     * @param y2 Second point Y
     * @return Distance in pixels
     */
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Retrieves comprehensive movement data for the trial.
     *
     * @param responseTimestamp The timestamp when the user released the touch
     * @return [TrialMovementData] containing all calculated metrics
     */
    fun getMovementData(responseTimestamp: Long): TrialMovementData {
        val reactionTime = calculateReactionTime()
        val movementTime = calculateMovementTime()
        val totalResponseTime = responseTimestamp - goBeepTime
        val pathLength = calculatePathLength()
        val averageSpeed = calculateAverageSpeed(movementTime, pathLength)
        val pathJson = convertPathToJson()

        Log.d(TAG, "Movement data: RT=${reactionTime}ms, MT=${movementTime}ms, " +
                "Path=${pathLength}px, Speed=${averageSpeed}px/s")

        return TrialMovementData(
            firstMovementTimestamp = firstMovementTime,
            targetReachedTimestamp = targetReachedTime,
            reactionTime = reactionTime,
            movementTime = movementTime,
            totalResponseTime = totalResponseTime,
            movementPath = pathJson,
            pathLength = pathLength,
            averageSpeed = averageSpeed,
            goBeepTimestamp = goBeepTime
        )
    }

    /**
     * Calculates reaction time (go beep to first movement).
     *
     * @return Reaction time in milliseconds, or null if movement hasn't started
     */
    private fun calculateReactionTime(): Long? {
        return firstMovementTime?.let { it - goBeepTime }
    }

    /**
     * Calculates movement time (first movement to target reached).
     *
     * @return Movement time in milliseconds, or null if target not reached
     */
    private fun calculateMovementTime(): Long? {
        return if (firstMovementTime != null && targetReachedTime != null) {
            targetReachedTime!! - firstMovementTime!!
        } else {
            null
        }
    }

    /**
     * Calculates average movement speed.
     *
     * @param movementTime Movement time in milliseconds
     * @param pathLength Total path length in pixels
     * @return Average speed in pixels per second
     */
    private fun calculateAverageSpeed(movementTime: Long?, pathLength: Float): Float {
        return if (movementTime != null && movementTime > 0) {
            pathLength / (movementTime / 1000f)
        } else {
            0f
        }
    }

    /**
     * Checks if there was significant movement during this trial.
     *
     * @return true if movement started and at least 3 points were recorded
     */
    fun hasSignificantMovement(): Boolean {
        return hasMovementStarted && movementPath.size > 2
    }

    /**
     * Checks if the correct target has been reached.
     *
     * @return true if the correct target was reached
     */
    fun hasReachedCorrectTarget(): Boolean {
        return targetReachedTime != null
    }

    /**
     * Determines which circle (if any) is currently being hovered over.
     *
     * @param x Current X-coordinate
     * @param y Current Y-coordinate
     * @return Circle index (0-3) or -1 if no circle is hovered
     */
    fun getHoveredCircle(x: Float, y: Float): Int {
        allCircleBounds.forEach { (index, bounds) ->
            if (bounds.contains(x.toInt(), y.toInt())) {
                return index
            }
        }
        return INVALID_INDEX
    }

    /**
     * Calculates the total length of the movement path.
     * Uses Euclidean distance between consecutive points.
     *
     * @return Total path length in pixels
     */
    private fun calculatePathLength(): Float {
        if (movementPath.size < 2) return 0f

        var totalDistance = 0f
        for (i in 1 until movementPath.size) {
            val (x1, y1, _) = movementPath[i - 1]
            val (x2, y2, _) = movementPath[i]
            totalDistance += calculateDistance(x1, y1, x2, y2)
        }
        return totalDistance
    }

    /**
     * Converts the movement path to a JSON string for storage.
     *
     * Each point contains:
     * - x: X-coordinate
     * - y: Y-coordinate
     * - t: Time offset from go beep in milliseconds
     *
     * @return JSON array string representation of the path
     */
    private fun convertPathToJson(): String {
        val pathJson = JSONArray()
        for ((x, y, timestamp) in movementPath) {
            val point = JSONObject().apply {
                put("x", x)
                put("y", y)
                put("t", timestamp - goBeepTime) // Relative time
            }
            pathJson.put(point)
        }
        return pathJson.toString()
    }
}

/**
 * Data class containing all movement metrics for a trial.
 *
 * @property firstMovementTimestamp Absolute timestamp of first movement
 * @property targetReachedTimestamp Absolute timestamp when target was reached
 * @property reactionTime Time from go beep to first movement (ms)
 * @property movementTime Time from first movement to target reached (ms)
 * @property totalResponseTime Total time from go beep to response (ms)
 * @property movementPath JSON string of movement coordinates
 * @property pathLength Total distance traveled (pixels)
 * @property averageSpeed Average movement speed (pixels/second)
 * @property goBeepTimestamp Absolute timestamp of go beep
 */
data class TrialMovementData(
    val firstMovementTimestamp: Long?,
    val targetReachedTimestamp: Long?,
    val reactionTime: Long?,
    val movementTime: Long?,
    val totalResponseTime: Long,
    val movementPath: String,
    val pathLength: Float,
    val averageSpeed: Float,
    val goBeepTimestamp: Long
)
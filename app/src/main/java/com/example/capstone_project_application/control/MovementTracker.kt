package com.example.capstone_project_application.control

import android.graphics.Rect
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Handles movement tracking during a trial.
 * Part of the Control layer in EBC pattern.
 */
class MovementTracker {

    private var originX: Float = 0f
    private var originY: Float = 0f
    private var lastX: Float = 0f
    private var lastY: Float = 0f

    private var goBeepTime: Long = 0
    private var firstMovementTime: Long? = null
    private var targetReachedTime: Long? = null
    private var hasMovementStarted = false

    private val movementPath = mutableListOf<Triple<Float, Float, Long>>()

    private var targetBounds: Rect? = null
    private var correctTargetIndex: Int = -1

    // Store ALL circle bounds for hover detection
    private val allCircleBounds = mutableMapOf<Int, Rect>()

    companion object {
        private const val MOVEMENT_THRESHOLD_PX = 5f
        private const val TAG = "MovementTracker"
    }

    /**
     * Reset tracker for a new trial
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
        correctTargetIndex = -1
        allCircleBounds.clear()
        Log.d(TAG, "Tracker reset for new trial")
    }

    /**
     * Set the correct target bounds for this trial
     */
    fun setTargetBounds(left: Int, top: Int, right: Int, bottom: Int, index: Int) {
        targetBounds = Rect(left, top, right, bottom)
        correctTargetIndex = index
        Log.d(TAG, "Correct target bounds set for index $index: $targetBounds")
    }

    /**
     * Add bounds for a circle (for hover detection)
     */
    fun addCircleBounds(index: Int, left: Int, top: Int, right: Int, bottom: Int) {
        allCircleBounds[index] = Rect(left, top, right, bottom)
    }

    /**
     * Record the origin point (where user first touches)
     */
    fun recordOrigin(x: Float, y: Float) {
        originX = x
        originY = y
        lastX = x
        lastY = y
        Log.d(TAG, "Origin recorded: ($x, $y)")
    }

    /**
     * Record the go beep timestamp
     */
    fun recordGoBeep(timestamp: Long) {
        goBeepTime = timestamp
        Log.d(TAG, "Go beep recorded at: $timestamp")
    }

    /**
     * Process a movement event
     * Returns true if this is significant movement
     */
    fun processMovement(x: Float, y: Float): Boolean {
        if (goBeepTime == 0L) {
            Log.v(TAG, "Movement ignored: goBeepTime not set yet")
            return false
        }

        val currentTime = System.currentTimeMillis()

        // Calculate distance from origin
        val dx = x - originX
        val dy = y - originY
        val distanceFromOrigin = sqrt(dx * dx + dy * dy)

        // Check if movement has started
        if (!hasMovementStarted && distanceFromOrigin > MOVEMENT_THRESHOLD_PX) {
            hasMovementStarted = true
            firstMovementTime = currentTime
            Log.d(TAG, "✓ Movement started: ${currentTime - goBeepTime}ms after go beep, distance=${distanceFromOrigin}px from origin")
        }

        // Record path if movement has started
        if (hasMovementStarted) {
            movementPath.add(Triple(x, y, currentTime))

            // Check if reached CORRECT target area
            if (targetReachedTime == null && targetBounds != null) {
                if (targetBounds!!.contains(x.toInt(), y.toInt())) {
                    targetReachedTime = currentTime
                    Log.d(TAG, "✓ CORRECT target reached: $correctTargetIndex at ${currentTime - goBeepTime}ms")
                }
            }

            lastX = x
            lastY = y
            return true
        } else if (distanceFromOrigin > 5) {
            Log.v(TAG, "Movement detected but below threshold: ${distanceFromOrigin}px (need ${MOVEMENT_THRESHOLD_PX}px)")
        }

        return false
    }

    /**
     * Get the movement data as a TrialMovementData object
     */
    fun getMovementData(responseTimestamp: Long): TrialMovementData {
        val reactionTime = firstMovementTime?.let { it - goBeepTime }
        val movementTime = if (firstMovementTime != null && targetReachedTime != null) {
            targetReachedTime!! - firstMovementTime!!
        } else null
        val totalResponseTime = responseTimestamp - goBeepTime

        val pathLength = calculatePathLength()
        val averageSpeed = if (movementTime != null && movementTime > 0) {
            pathLength / (movementTime / 1000f)
        } else 0f

        val pathJson = convertPathToJson()

        Log.d(TAG, "Movement data: RT=${reactionTime}ms, MT=${movementTime}ms, Path=${pathLength}px, Speed=${averageSpeed}px/s")

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
     * Check if there was significant movement during this trial
     */
    fun hasSignificantMovement(): Boolean {
        return hasMovementStarted && movementPath.size > 2
    }

    /**
     * Check if the correct target has been reached
     */
    fun hasReachedCorrectTarget(): Boolean {
        return targetReachedTime != null
    }

    /**
     * Check which circle (if any) is currently being hovered
     * Returns the circle index, or -1 if no circle is hovered
     */
    fun getHoveredCircle(x: Float, y: Float): Int {
        allCircleBounds.forEach { (index, bounds) ->
            if (bounds.contains(x.toInt(), y.toInt())) {
                return index
            }
        }
        return -1
    }

    private fun calculatePathLength(): Float {
        if (movementPath.size < 2) return 0f

        var totalDistance = 0f
        for (i in 1 until movementPath.size) {
            val (x1, y1, _) = movementPath[i - 1]
            val (x2, y2, _) = movementPath[i]
            val dx = x2 - x1
            val dy = y2 - y1
            totalDistance += sqrt(dx * dx + dy * dy)
        }
        return totalDistance
    }

    private fun convertPathToJson(): String {
        val pathJson = JSONArray()
        for ((x, y, timestamp) in movementPath) {
            val point = JSONObject()
            point.put("x", x)
            point.put("y", y)
            point.put("t", timestamp - goBeepTime)
            pathJson.put(point)
        }
        return pathJson.toString()
    }
}

/**
 * Data class to hold movement metrics
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
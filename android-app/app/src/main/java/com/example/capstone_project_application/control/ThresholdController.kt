package com.example.capstone_project_application.control

import android.util.Log
import kotlin.random.Random

/**
 * Data class holding the state for a single threshold trial.
 *
 * @property trialCount Current trial number (0-indexed for internal tracking)
 * @property totalTrials Total number of trials in the test
 * @property hueA Hue value for patch A
 * @property hueB Hue value for patch B
 * @property isExperimentFinished Whether all trials are complete
 */
data class ThresholdTrialState(
    val trialCount: Int,
    val totalTrials: Int,
    val hueA: Int,
    val hueB: Int,
    val isExperimentFinished: Boolean = false
)

/**
 * Controller for Just Noticeable Difference (JND) threshold determination.
 *
 * This class implements a psychophysical testing algorithm to determine
 * the smallest color difference a participant can reliably detect.
 *
 * ## Algorithm:
 * - Reference hue (n0): 140 (neutral blue)
 * - Test hues: 141, 142, 143, 144, 145, 150, 155, 160, 165, 175
 * - 100 total trials
 * - Each hue appears maximum 10 times
 * - Each hue appears on left maximum 5 times
 * - Threshold = highest hue with ≤50% correct responses
 *
 * ## Rationale:
 * Higher hue numbers represent colors more different from the reference.
 * The threshold represents the boundary where the difference becomes
 * just barely noticeable (~50% discrimination).
 *
 *  Fallback**: If all hues > 50%, use the lowest test hue (141) - participant can discriminate everything
 *  **Error case**: If no trials completed, returns null - participant must restart
 *
 *
 * Part of the Control layer in Entity-Boundary-Control pattern.
 */
class ThresholdController {

    private var trialCount = 0

    // Tracking maps for each test hue
    private val occurrences = mutableMapOf<Int, Int>()
    private val leftCount = mutableMapOf<Int, Int>()
    private val correctAnswers = mutableMapOf<Int, Int>()

    // Current trial state
    private var currentSelectedHue = 0
    private var currentCorrectAnswer = ""

    companion object {
        private const val TAG = "ThresholdController"

        // Test parameters
        private const val TOTAL_TRIALS = 100
        private const val REFERENCE_HUE = 140
        private const val MAX_OCCURRENCES_PER_HUE = 10
        private const val MAX_LEFT_APPEARANCES = 5

        // Test hues (increasing distance from reference)
        private val TEST_HUES = listOf(141, 142, 143, 144, 145, 150, 155, 160, 165, 175)

        // Threshold calculation parameters
        private const val THRESHOLD_PERCENTAGE = 50.0
        private const val MIN_TEST_HUE = 141  // For perfect performers
    }

    init {
        initializeTracking()
    }

    /**
     * Initializes tracking maps for all test hues.
     */
    private fun initializeTracking() {
        for (hue in TEST_HUES) {
            occurrences[hue] = 0
            leftCount[hue] = 0
            correctAnswers[hue] = 0
        }
        Log.d(TAG, "Threshold controller initialized with ${TEST_HUES.size} test hues")
    }

    /**
     * Starts the next trial or signals completion.
     *
     * @return [ThresholdTrialState] with trial configuration
     */
    fun startNextTrial(): ThresholdTrialState {
        if (isTestComplete()) {
            return createFinishedState()
        }

        val availableHues = getAvailableHues()

        if (availableHues.isEmpty()) {
            Log.w(TAG, "No available hues, ending test early")
            return createFinishedState()
        }

        currentSelectedHue = selectRandomHue(availableHues)
        val (hueA, hueB) = determineTrialConfiguration(currentSelectedHue)

        return ThresholdTrialState(trialCount, TOTAL_TRIALS, hueA, hueB)
    }

    /**
     * Checks if the test is complete.
     *
     * @return true if all trials done, false otherwise
     */
    private fun isTestComplete(): Boolean {
        return trialCount >= TOTAL_TRIALS
    }

    /**
     * Creates a finished state for the trial.
     *
     * @return [ThresholdTrialState] with isExperimentFinished = true
     */
    private fun createFinishedState(): ThresholdTrialState {
        return ThresholdTrialState(
            trialCount,
            TOTAL_TRIALS,
            REFERENCE_HUE,
            REFERENCE_HUE,
            isExperimentFinished = true
        )
    }

    /**
     * Gets list of hues that can still be presented.
     *
     * Filters out hues that have reached their occurrence limit.
     *
     * @return List of available test hues
     */
    private fun getAvailableHues(): List<Int> {
        return TEST_HUES.filter { hue ->
            occurrences[hue]!! < MAX_OCCURRENCES_PER_HUE
        }
    }

    /**
     * Selects a random hue from available hues.
     *
     * @param availableHues List of hues that can be selected
     * @return Selected hue value
     */
    private fun selectRandomHue(availableHues: List<Int>): Int {
        return availableHues.random()
    }

    /**
     * Determines the configuration for a trial (which hue goes where).
     *
     * The test hue can appear on left or right, with constraints:
     * - Each hue appears on left max 5 times
     * - 50/50 randomization when left is available
     *
     * @param selectedHue The test hue to present
     * @return Pair of (hueA, hueB) and sets current correct answer
     */
    private fun determineTrialConfiguration(selectedHue: Int): Pair<Int, Int> {
        val canAppearOnLeft = leftCount[selectedHue]!! < MAX_LEFT_APPEARANCES
        val shouldAppearOnLeft = canAppearOnLeft && Random.nextBoolean()

        return if (shouldAppearOnLeft) {
            leftCount[selectedHue] = leftCount[selectedHue]!! + 1
            currentCorrectAnswer = "A"
            Pair(selectedHue, REFERENCE_HUE)
        } else {
            currentCorrectAnswer = "B"
            Pair(REFERENCE_HUE, selectedHue)
        }
    }

    /**
     * Records the user's response and updates statistics.
     *
     * @param response User's answer ("A" or "B")
     */
    fun handleUserResponse(response: String) {
        // Increment occurrence count
        occurrences[currentSelectedHue] = occurrences[currentSelectedHue]!! + 1

        // Record if answer was correct
        if (response == currentCorrectAnswer) {
            correctAnswers[currentSelectedHue] = correctAnswers[currentSelectedHue]!! + 1
        }

        trialCount++

        Log.v(TAG, "Trial $trialCount: hue=$currentSelectedHue, response=$response, " +
                "correct=${response == currentCorrectAnswer}")
    }

    /**
     * Calculates the JND threshold based on participant performance.
     *
     * ## Algorithm:
     * 1. Calculate percentage correct for each hue
     * 2. Find the highest hue where performance ≤ 50%
     *      This represents the boundary where the color difference becomes
     *      just barely noticeable.
     * 3. If all hues > 50% (perfect performance), return MIN_TEST_HUE (141)
     *    - Participant can discriminate even the smallest difference
     * 4. If no trials completed (critical error), return NULL
     *    - Participant MUST restart the test
     *
     *
     * @return JND threshold hue, or null if no valid threshold found
     */
    fun calculateJNDThreshold(): Int? {
        val huePerformance = calculatePerformanceForAllHues()

        if (huePerformance.isEmpty()) {
            Log.e(TAG, "✗ CRITICAL ERROR: No performance data available after 100 trials!")
            Log.e(TAG, "  This should never happen. Participant must restart JND test.")
            return null
        }

        val sortedPerformance = sortHuesByDistance(huePerformance)
        val threshold = findThreshold(sortedPerformance)

        Log.d(TAG, "JND Threshold calculated: $threshold")
        logPerformanceDetails(sortedPerformance)

        return threshold
    }

    /**
     * Calculates performance percentage for all tested hues.
     *
     * @return List of (hue, percentage) pairs
     */
    private fun calculatePerformanceForAllHues(): List<Pair<Int, Double>> {
        val performance = mutableListOf<Pair<Int, Double>>()

        for (hue in TEST_HUES) {
            val correct = correctAnswers[hue]!!
            val total = occurrences[hue]!!

            if (total > 0) {
                val percentage = (correct * 100.0) / total
                performance.add(Pair(hue, percentage))
            }
        }

        return performance
    }

    /**
     * Sorts hues by distance from reference (descending).
     *
     * @param performance List of (hue, percentage) pairs
     * @return Sorted list
     */
    private fun sortHuesByDistance(
        performance: List<Pair<Int, Double>>
    ): List<Pair<Int, Double>> {
        return performance.sortedByDescending { it.first }
    }

    /**
     * Finds the threshold hue (highest hue with ≤50% correct).
     *
     * @param sortedPerformance Performance data sorted by hue (descending)
     * @return Threshold hue or null
     */
    private fun findThreshold(sortedPerformance: List<Pair<Int, Double>>): Int? {
        var threshold: Int? = null

        for ((hue, percentage) in sortedPerformance) {
            if (percentage <= THRESHOLD_PERCENTAGE) {
                // Keep highest hue at or below threshold
                if (threshold == null || hue > threshold) {
                    threshold = hue
                }
            }
        }

        // Handle case where participant got everything correct
        if (threshold == null) {
            Log.d(TAG, "✓ All hues performed > 50% - participant has excellent discrimination")
            Log.d(TAG, "  Using minimum test hue ($MIN_TEST_HUE) as threshold")
            return MIN_TEST_HUE
        }

        return threshold
    }

    /**
     * Logs detailed performance for debugging.
     *
     * @param sortedPerformance Performance data sorted by hue
     */
    private fun logPerformanceDetails(sortedPerformance: List<Pair<Int, Double>>) {
        Log.d(TAG, "Performance by hue (descending):")
        for ((hue, percentage) in sortedPerformance) {
            val total = occurrences[hue]!!
            val correct = correctAnswers[hue]!!
            val marker = if (percentage <= THRESHOLD_PERCENTAGE) "✓ AT/BELOW THRESHOLD" else ""
            Log.d(TAG, "  Hue $hue: $correct/$total correct (${String.format("%.1f", percentage)}%) $marker")
        }
    }
}
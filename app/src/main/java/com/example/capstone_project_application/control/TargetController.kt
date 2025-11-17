package com.example.capstone_project_application.control

import android.util.Log
import kotlin.random.Random

/**
 * Defines the three possible trial type variations.
 *
 * - **PRE_JND**: Target appears with JND threshold color, then changes to obvious color before go beep
 * - **PRE_SUPRA**: Target appears with obvious color before go beep
 * - **CONCURRENT_SUPRA**: Target appears with obvious color at go beep
 */
enum class TrialType {
    PRE_JND,
    PRE_SUPRA,
    CONCURRENT_SUPRA
}

/**
 * Data class holding complete information for a single trial.
 *
 * @property trialCount Current trial number (1-indexed)
 * @property totalTrials Total number of trials in experiment
 * @property targetCircleIndex Index of target circle (0-3): 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right
 * @property trialType The type of trial (timing of target appearance)
 * @property initialHue Initial color hue of the target
 * @property finalHue Final color hue (only for PRE_JND trials, null otherwise)
 * @property isExperimentFinished Whether all trials are complete
 */
data class TargetTrialState(
    val trialCount: Int,
    val totalTrials: Int,
    val targetCircleIndex: Int,
    val trialType: TrialType,
    val initialHue: Int,
    val finalHue: Int?,
    val isExperimentFinished: Boolean = false
)

/**
 * Controller for managing target trial sequence and configuration.
 *
 * This class generates and manages a sequence of 15 target-reaching trials,
 * ensuring proper randomization and balance across trial types and target locations.
 *
 * ## Trial Generation:
 * - 5 trials of each type (PRE_JND, PRE_SUPRA, CONCURRENT_SUPRA)
 * - Random target locations (4 possible circles)
 * - No two consecutive identical trials (same type AND location)
 *
 * ## Color Selection:
 * - JND trials: Use participant's calculated threshold
 * - Supra trials: Use obvious color (hue 999)
 *
 * Part of the Control layer in Entity-Boundary-Control pattern.
 *
 * @property jndThreshold The participant's calculated JND threshold from threshold test
 */
class TargetController(private val jndThreshold: Int) {

    private var currentTrialNumber = 0
    private val trialSequence: List<Pair<TrialType, Int>>

    companion object {
        private const val TAG = "TargetController"
        private const val TOTAL_TRIALS = 15
        private const val TRIALS_PER_TYPE = 5
        private const val NUM_TARGET_CIRCLES = 4
        private const val MAX_GENERATION_ATTEMPTS = 100

        // Special hue identifier for obvious (supra-threshold) color
        const val OBVIOUS_HUE_IDENTIFIER = 999

        // Neutral hue for non-target circles
        private const val NEUTRAL_HUE = 140
    }

    init {
        trialSequence = generateTrialSequence()
        Log.d(TAG, "Trial sequence generated: ${trialSequence.size} trials")
    }


    /**
     * Generates a balanced, randomized sequence of trials.
     *
     * Ensures:
     * 1. Equal distribution of trial types (5 each).
     * 2. Balanced target locations for each type ({2, 1, 1, 1} distribution).
     * 3. No consecutive identical trials (same type AND location).
     *
     * @return List of (TrialType, TargetIndex) pairs
     */
    private fun generateTrialSequence(): List<Pair<TrialType, Int>> {
        val allTrials = mutableListOf<Pair<TrialType, Int>>()

        //Create a balanced master list
        TrialType.values().forEach { trialType ->
            val locations = (0 until NUM_TARGET_CIRCLES).toMutableList()
            locations.add(Random.nextInt(NUM_TARGET_CIRCLES))

            locations.forEach { location ->
                allTrials.add(Pair(trialType, location))
            }
        }

        // Shuffle the master list, avoiding consecutive identicals
        val shuffledSequence = mutableListOf<Pair<TrialType, Int>>()
        var previousTrial: Pair<TrialType, Int>? = null

        while (allTrials.isNotEmpty()) {
            var selectedIndex = Random.nextInt(allTrials.size)
            var selectedTrial = allTrials[selectedIndex]

            if (selectedTrial == previousTrial && allTrials.size > 1) {
                var attempts = 0
                val originalIndex = selectedIndex

                while (selectedTrial == previousTrial && attempts < 10) {
                    selectedIndex = (selectedIndex + 1) % allTrials.size // Try next
                    selectedTrial = allTrials[selectedIndex]
                    attempts++
                }
            }

            shuffledSequence.add(selectedTrial)
            allTrials.removeAt(selectedIndex)

            previousTrial = selectedTrial
        }

        Log.d(TAG, "Balanced trial sequence generated: ${shuffledSequence.size} trials")
        shuffledSequence.forEachIndexed { index, (type, target) ->
            Log.v(TAG, "  Trial ${index + 1}: $type, Target $target")
        }

        return shuffledSequence
    }

    /**
     * Selects a target index that avoids creating an identical consecutive trial.
     *
     * @param trialType Current trial type
     * @param previousTrial Previous trial (type, index) or null
     * @return Selected target index (0-3)
     */
    private fun selectTargetIndexAvoidingDuplicate(
        trialType: TrialType,
        previousTrial: Pair<TrialType, Int>?
    ): Int {
        var targetIndex: Int
        var attempts = 0

        do {
            targetIndex = Random.nextInt(NUM_TARGET_CIRCLES)
            attempts++

            // Safety valve: Accept after too many attempts
            if (attempts > MAX_GENERATION_ATTEMPTS) {
                Log.w(TAG, "Max attempts reached for trial generation, accepting duplicate")
                break
            }

        } while (isIdenticalToPrevious(trialType, targetIndex, previousTrial))

        return targetIndex
    }

    /**
     * Checks if a trial would be identical to the previous trial.
     *
     * @param trialType Current trial type
     * @param targetIndex Current target index
     * @param previousTrial Previous trial or null
     * @return true if identical, false otherwise
     */
    private fun isIdenticalToPrevious(
        trialType: TrialType,
        targetIndex: Int,
        previousTrial: Pair<TrialType, Int>?
    ): Boolean {
        if (previousTrial == null) return false

        val (prevType, prevIndex) = previousTrial
        return trialType == prevType && targetIndex == prevIndex
    }

    /**
     * Starts a new trial and returns its configuration.
     *
     * @return [TargetTrialState] with trial configuration, or finished state if complete
     */
    fun startNewTrial(): TargetTrialState {
        if (currentTrialNumber >= TOTAL_TRIALS) {
            return createFinishedState()
        }

        val (trialType, targetCircleIndex) = trialSequence[currentTrialNumber]
        currentTrialNumber++

        val (initialHue, finalHue) = determineHues(trialType)

        Log.d(TAG, "Trial $currentTrialNumber: type=$trialType, target=$targetCircleIndex, " +
                "initialHue=$initialHue, finalHue=$finalHue")

        return TargetTrialState(
            trialCount = currentTrialNumber,
            totalTrials = TOTAL_TRIALS,
            targetCircleIndex = targetCircleIndex,
            trialType = trialType,
            initialHue = initialHue,
            finalHue = finalHue
        )
    }

    /**
     * Creates a finished state indicating experiment completion.
     *
     * @return [TargetTrialState] with isExperimentFinished = true
     */
    private fun createFinishedState(): TargetTrialState {
        return TargetTrialState(
            trialCount = currentTrialNumber,
            totalTrials = TOTAL_TRIALS,
            targetCircleIndex = -1,
            trialType = TrialType.CONCURRENT_SUPRA,
            initialHue = NEUTRAL_HUE,
            finalHue = null,
            isExperimentFinished = true
        )
    }

    fun getTotalTrials(): Int {
        return TOTAL_TRIALS  // or whatever your constant is
    }

    /**
     * Determines initial and final hues based on trial type.
     *
     * @param trialType The type of trial
     * @return Pair of (initialHue, finalHue), where finalHue may be null
     */
    private fun determineHues(trialType: TrialType): Pair<Int, Int?> {
        return when (trialType) {
            TrialType.PRE_JND -> {
                // Starts with JND threshold, changes to obvious
                Pair(jndThreshold, OBVIOUS_HUE_IDENTIFIER)
            }
            TrialType.PRE_SUPRA, TrialType.CONCURRENT_SUPRA -> {
                // Shows obvious color throughout
                Pair(OBVIOUS_HUE_IDENTIFIER, null)
            }
        }
    }

    /**
     * Validates user response against target location.
     * (Currently unused but kept for potential future validation logic)
     *
     * @param clickedIndex The circle index clicked by user
     * @param targetIndex The correct target index
     * @return true if correct, false otherwise
     */
    @Suppress("unused")
    fun checkUserResponse(clickedIndex: Int, targetIndex: Int): Boolean {
        return clickedIndex == targetIndex
    }
}
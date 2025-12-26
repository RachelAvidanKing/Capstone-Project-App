package com.example.capstone_project_application.control

import android.util.Log
import kotlin.random.Random

/**
 * Defines the three trial type variations.
 *
 * - **PRE_JND**: JND threshold hint shown 350ms before beep for 25ms, then obvious at beep
 * - **PRE_SUPRA**: Obvious color hint shown 350ms before beep for 25ms, then obvious at beep
 * - **CONCURRENT_SUPRA**: Target appears with obvious color at beep
 */
enum class TrialType {
    PRE_JND,
    PRE_SUPRA,
    CONCURRENT_SUPRA
}

/**
 * Complete information for a single trial.
 *
 * @property trialCount Current trial number (1-indexed)
 * @property totalTrials Total number of trials in experiment
 * @property targetCircleIndex Index of target circle (0-3): 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right
 * @property trialType The type of trial (timing of target appearance)
 * @property hintHue Color hue shown briefly before beep (null for CONCURRENT_SUPRA)
 * @property finalHue Color hue shown at beep (always obvious color)
 * @property isExperimentFinished Whether all trials are complete
 */
data class TargetTrialState(
    val trialCount: Int,
    val totalTrials: Int,
    val targetCircleIndex: Int,
    val trialType: TrialType,
    val hintHue: Int?,
    val finalHue: Int,
    val isExperimentFinished: Boolean = false
)

/**
 * Manages target trial sequence and configuration.
 *
 * Generates and manages 15 target-reaching trials with proper randomization
 * and balance across trial types and target locations.
 *
 * ## Trial Generation:
 * - 5 trials of each type (PRE_JND, PRE_SUPRA, CONCURRENT_SUPRA)
 * - Random target locations (4 possible circles)
 * - No two consecutive identical trials (same type AND location)
 *
 * ## Color Timing:
 * - PRE_JND: JND hint for 25ms at -350ms, then neutral, then obvious at 0ms
 * - PRE_SUPRA: Obvious hint for 25ms at -350ms, then neutral, then obvious at 0ms
 * - CONCURRENT_SUPRA: Obvious at 0ms only
 *
 * @property jndThreshold The participant's calculated JND threshold
 */
class TargetController(private val jndThreshold: Int) {

    private var currentTrialNumber = 0
    private val trialSequence: List<Pair<TrialType, Int>>

    companion object {
        private const val TAG = "TargetController"
        private const val TOTAL_TRIALS = 15
        private const val NUM_TARGET_CIRCLES = 4
        private const val MAX_GENERATION_ATTEMPTS = 100

        const val OBVIOUS_HUE_IDENTIFIER = 999
        const val NEUTRAL_HUE = 140
    }

    init {
        trialSequence = generateTrialSequence()
        Log.d(TAG, "Trial sequence generated: ${trialSequence.size} trials")
    }

    /**
     * Generates a balanced, randomized sequence of trials.
     *
     * Ensures:
     * 1. Equal distribution of trial types (5 each)
     * 2. Balanced target locations for each type
     * 3. No consecutive identical trials (same type AND location)
     *
     * @return List of (TrialType, TargetIndex) pairs
     */
    private fun generateTrialSequence(): List<Pair<TrialType, Int>> {
        val allTrials = mutableListOf<Pair<TrialType, Int>>()

        TrialType.values().forEach { trialType ->
            val locations = (0 until NUM_TARGET_CIRCLES).toMutableList()
            locations.add(Random.nextInt(NUM_TARGET_CIRCLES))

            locations.forEach { location ->
                allTrials.add(Pair(trialType, location))
            }
        }

        val shuffledSequence = mutableListOf<Pair<TrialType, Int>>()
        var previousTrial: Pair<TrialType, Int>? = null

        while (allTrials.isNotEmpty()) {
            var selectedIndex = Random.nextInt(allTrials.size)
            var selectedTrial = allTrials[selectedIndex]

            if (selectedTrial == previousTrial && allTrials.size > 1) {
                var attempts = 0

                while (selectedTrial == previousTrial && attempts < 10) {
                    selectedIndex = (selectedIndex + 1) % allTrials.size
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

        val (hintHue, finalHue) = determineHues(trialType)

        Log.d(TAG, "Trial $currentTrialNumber: type=$trialType, target=$targetCircleIndex, " +
                "hintHue=$hintHue, finalHue=$finalHue")

        return TargetTrialState(
            trialCount = currentTrialNumber,
            totalTrials = TOTAL_TRIALS,
            targetCircleIndex = targetCircleIndex,
            trialType = trialType,
            hintHue = hintHue,
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
            hintHue = null,
            finalHue = NEUTRAL_HUE,
            isExperimentFinished = true
        )
    }

    fun getTotalTrials(): Int = TOTAL_TRIALS

    /**
     * Determines hint and final hues based on trial type.
     *
     * @param trialType The type of trial
     * @return Pair of (hintHue, finalHue) where hintHue may be null
     */
    private fun determineHues(trialType: TrialType): Pair<Int?, Int> {
        return when (trialType) {
            TrialType.PRE_JND -> Pair(jndThreshold, OBVIOUS_HUE_IDENTIFIER)
            TrialType.PRE_SUPRA -> Pair(OBVIOUS_HUE_IDENTIFIER, OBVIOUS_HUE_IDENTIFIER)
            TrialType.CONCURRENT_SUPRA -> Pair(null, OBVIOUS_HUE_IDENTIFIER)
        }
    }
}
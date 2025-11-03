package com.example.capstone_project_application.control

import kotlin.random.Random

/**
 * Defines the three possible variations for a trial.
 */
enum class TrialType {
    PRE_JND,
    PRE_SUPRA,
    CONCURRENT_SUPRA
}

/**
 * Updated data class to hold all information for a trial.
 */
data class TargetTrialState(
    val trialCount: Int,
    val totalTrials: Int,
    val targetCircleIndex: Int,
    val trialType: TrialType,
    val initialHue: Int,
    val finalHue: Int?, // Nullable, only used for PRE_JND
    val isExperimentFinished: Boolean = false
)

class TargetController(private val jndThreshold: Int) {

    private val HUES = listOf(141, 142, 143, 144, 145, 150, 155, 160, 165, 175)
    private val TOTAL_TRIALS = 15
    private var currentTrialNumber = 0

    // Using a special, non-conflicting number to represent our new obvious color.
    private val OBVIOUS_HUE_IDENTIFIER = 999

    private val trialSequence: MutableList<Pair<TrialType, Int>> // Pair of (TrialType, TargetIndex)

    init {
        trialSequence = generateTrialSequence()
    }

    /**
     * Generates a sequence of 15 trials ensuring no two consecutive trials are identical.
     * A trial is considered identical if it has the same TrialType AND the same target index.
     */
    private fun generateTrialSequence(): MutableList<Pair<TrialType, Int>> {
        val trials = mutableListOf<Pair<TrialType, Int>>()
        val trialTypes = mutableListOf<TrialType>()

        // Create 5 of each trial type
        repeat(5) { trialTypes.add(TrialType.PRE_JND) }
        repeat(5) { trialTypes.add(TrialType.PRE_SUPRA) }
        repeat(5) { trialTypes.add(TrialType.CONCURRENT_SUPRA) }

        // Shuffle the trial types
        trialTypes.shuffle()

        // Assign random target indices (0-3) while ensuring no consecutive identical trials
        var previousTrial: Pair<TrialType, Int>? = null

        for (trialType in trialTypes) {
            var targetIndex: Int
            var attempts = 0
            val maxAttempts = 100

            do {
                targetIndex = Random.nextInt(4)
                attempts++

                // If we've tried too many times, just accept it (edge case protection)
                if (attempts > maxAttempts) break

            } while (previousTrial != null &&
                previousTrial.first == trialType &&
                previousTrial.second == targetIndex)

            val currentTrial = Pair(trialType, targetIndex)
            trials.add(currentTrial)
            previousTrial = currentTrial
        }

        return trials
    }

    fun startNewTrial(): TargetTrialState {
        if (currentTrialNumber >= TOTAL_TRIALS) {
            return TargetTrialState(
                currentTrialNumber,
                TOTAL_TRIALS,
                -1,
                TrialType.CONCURRENT_SUPRA,
                140,
                null,
                isExperimentFinished = true
            )
        }

        val (trialType, targetCircleIndex) = trialSequence[currentTrialNumber]
        currentTrialNumber++

        val initialHue: Int
        val finalHue: Int?

        when (trialType) {
            TrialType.PRE_JND -> {
                initialHue = jndThreshold // The subtle JND color
                finalHue = OBVIOUS_HUE_IDENTIFIER // The obvious color it changes into
            }
            TrialType.PRE_SUPRA, TrialType.CONCURRENT_SUPRA -> {
                initialHue = OBVIOUS_HUE_IDENTIFIER // The obvious color
                finalHue = null
            }
        }

        return TargetTrialState(
            trialCount = currentTrialNumber,
            totalTrials = TOTAL_TRIALS,
            targetCircleIndex = targetCircleIndex,
            trialType = trialType,
            initialHue = initialHue,
            finalHue = finalHue
        )
    }

    fun checkUserResponse(clickedIndex: Int, targetIndex: Int): Boolean {
        return clickedIndex == targetIndex
    }
}
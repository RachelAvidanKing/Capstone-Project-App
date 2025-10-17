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
    private val TOTAL_TRIALS = 10
    private var currentTrialNumber = 0

    // Using a special, non-conflicting number to represent our new obvious color.
    private val OBVIOUS_HUE_IDENTIFIER = 999

    private val trialSequence: List<TrialType>

    init {
        val trials = mutableListOf<TrialType>()
        repeat(4) { trials.add(TrialType.PRE_JND) }
        repeat(3) { trials.add(TrialType.PRE_SUPRA) }
        repeat(3) { trials.add(TrialType.CONCURRENT_SUPRA) }
        trials.shuffle()
        trialSequence = trials
    }

    fun startNewTrial(): TargetTrialState {
        if (currentTrialNumber >= TOTAL_TRIALS) {
            return TargetTrialState(currentTrialNumber, TOTAL_TRIALS, -1, TrialType.CONCURRENT_SUPRA, 140, null, isExperimentFinished = true)
        }

        val trialType = trialSequence[currentTrialNumber]
        val targetCircleIndex = Random.nextInt(4)
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
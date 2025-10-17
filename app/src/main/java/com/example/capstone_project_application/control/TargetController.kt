package com.example.capstone_project_application.control

import kotlin.random.Random

// Data class to hold the state for the Target trial
data class TargetTrialState(
    val trialCount: Int,
    val totalTrials: Int,
    val targetCircleIndex: Int,
    val targetHue: Int,
    val isExperimentFinished: Boolean = false
)

class TargetController(private val jndThreshold: Int) {

    private val HUES = listOf(141, 142, 143, 144, 145, 150, 155, 160, 165, 175)
    private val TOTAL_TRIALS = 3//10

    private var currentTrialNumber = 0
    private var isJndTrial: Boolean = true
    private var targetCircleIndex: Int = -1
    private var targetHue: Int = 140

    fun startNewTrial(): TargetTrialState {
        if (currentTrialNumber >= TOTAL_TRIALS) {
            return TargetTrialState(currentTrialNumber, TOTAL_TRIALS, -1, 140, isExperimentFinished = true)
        }

        currentTrialNumber++

        isJndTrial = Random.nextBoolean()
        targetHue = if (isJndTrial) {
            jndThreshold
        } else {
            val aboveThresholdHues = HUES.filter { it < jndThreshold }
            if (aboveThresholdHues.isNotEmpty()) {
                aboveThresholdHues.random()
            } else {
                HUES.minOrNull() ?: jndThreshold
            }
        }
        targetCircleIndex = Random.nextInt(4)

        return TargetTrialState(currentTrialNumber, TOTAL_TRIALS, targetCircleIndex, targetHue)
    }

    fun checkUserResponse(clickedIndex: Int): Boolean {
        return clickedIndex == targetCircleIndex
    }
}
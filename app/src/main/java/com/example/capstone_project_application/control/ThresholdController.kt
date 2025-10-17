package com.example.capstone_project_application.control

import kotlin.random.Random

// Data class to hold the state for each trial to be displayed by the Boundary
data class ThresholdTrialState(
    val trialCount: Int,
    val totalTrials: Int,
    val hueA: Int,
    val hueB: Int,
    val isExperimentFinished: Boolean = false
)

class ThresholdController {

    val N_TOTAL = 20
    private val n0 = 140
    private val HUES = listOf(141, 142, 143, 144, 145, 150, 155, 160, 165, 175)
    private val MAX_PER_HUE = 10
    private val MAX_LEFT = 5

    private var trialCount = 0
    private val occurrences = mutableMapOf<Int, Int>()
    private val leftCount = mutableMapOf<Int, Int>()
    private val correctAnswers = mutableMapOf<Int, Int>()

    // Current trial state
    private var currentSelectedHue = 0
    private var currentCorrectAnswer = ""

    init {
        // Initialize tracking for each hue
        for (hue in HUES) {
            occurrences[hue] = 0
            leftCount[hue] = 0
            correctAnswers[hue] = 0
        }
    }

    fun startNextTrial(): ThresholdTrialState {
        if (trialCount >= N_TOTAL) {
            return ThresholdTrialState(trialCount, N_TOTAL, n0, n0, isExperimentFinished = true)
        }

        val validHues = HUES.filter { occurrences[it]!! < MAX_PER_HUE }

        if (validHues.isEmpty()) {
            return ThresholdTrialState(trialCount, N_TOTAL, n0, n0, isExperimentFinished = true)
        }

        currentSelectedHue = validHues.random()
        val canBeLeft = leftCount[currentSelectedHue]!! < MAX_LEFT
        val shouldBeLeft = canBeLeft && Random.nextBoolean()

        val hueA: Int
        val hueB: Int

        if (shouldBeLeft) {
            leftCount[currentSelectedHue] = leftCount[currentSelectedHue]!! + 1
            hueA = currentSelectedHue
            hueB = n0
            currentCorrectAnswer = "A"
        } else {
            hueA = n0
            hueB = currentSelectedHue
            currentCorrectAnswer = "B"
        }

        occurrences[currentSelectedHue] = occurrences[currentSelectedHue]!! + 1

        return ThresholdTrialState(trialCount, N_TOTAL, hueA, hueB)
    }

    fun handleUserResponse(response: String) {
        if (response == currentCorrectAnswer) {
            correctAnswers[currentSelectedHue] = correctAnswers[currentSelectedHue]!! + 1
        }
        trialCount++
    }

    fun calculateJNDThreshold(): Int? {
        val huePerformance = mutableListOf<Pair<Int, Double>>()

        for (hue in HUES) {
            val correct = correctAnswers[hue]!!
            val total = occurrences[hue]!!
            if (total > 0) {
                val percentage = (correct * 100.0) / total
                huePerformance.add(Pair(hue, percentage))
            }
        }

        huePerformance.sortByDescending { it.first }

        var threshold: Int? = null
        for ((hue, percentage) in huePerformance) {
            if (percentage <= 50.0) {
                if (threshold == null || hue > threshold) {
                    threshold = hue
                }
            }
        }
        return threshold
    }
}
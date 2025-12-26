package com.example.capstone_project_application.boundary

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone_project_application.R
import com.example.capstone_project_application.control.DataRepository
import com.example.capstone_project_application.control.InactivityHelper
import com.example.capstone_project_application.entity.AppDatabase

/**
 * Explanation screen for the target-reaching experiment.
 *
 * This activity displays instructions about the main experimental task
 * where participants reach toward colored targets after audio cues.
 *
 * ## Purpose:
 * - Explain the target-reaching procedure
 * - Describe audio cues and timing
 * - Clarify participant's task
 *
 * ## Navigation:
 * - Tap anywhere → [TargetActivity]
 * - Back button → disabled
 *
 * Part of the Boundary layer in Entity-Boundary-Control pattern.
 */
class ExplainTargetActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }
    private lateinit var inactivityHelper: InactivityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.explanation_screen_test)

        initializeInactivityHelper()
    }

    /**
     * Initializes the inactivity helper for automatic logout.
     */
    private fun initializeInactivityHelper() {
        inactivityHelper = InactivityHelper(this, repository)
        inactivityHelper.resetTimer()
    }

    /**
     * Handles touch events for "tap anywhere to continue" functionality.
     * Only triggers on ACTION_UP to ensure intentional taps.
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            handleExplanationDismiss()
            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Disables back button to prevent accidental navigation.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally empty - prevents back navigation
    }

    /**
     * Handles dismissal of explanation screen and navigates to target experiment.
     */
    private fun handleExplanationDismiss() {
        inactivityHelper.resetTimer()

        val intent = Intent(this, TargetActivity::class.java)
        startActivity(intent)
        finish()
    }
}
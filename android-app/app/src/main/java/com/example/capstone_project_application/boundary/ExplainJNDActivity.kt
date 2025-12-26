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
 * Explanation screen for the JND (Just Noticeable Difference) threshold test.
 *
 * This activity displays instructions to the participant about the upcoming
 * threshold test. The user can tap anywhere to proceed to the test.
 *
 * ## Purpose:
 * - Explain the JND test procedure
 * - Prepare participant for threshold determination
 * - Ensure participant understands the task
 *
 * ## Navigation:
 * - Tap anywhere → [ThresholdActivity]
 * - Back button → disabled (prevents accidental navigation)
 *
 * Part of the Boundary layer in Entity-Boundary-Control pattern.
 */
class ExplainJNDActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }
    private lateinit var inactivityHelper: InactivityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.explanation_screen_jnd)

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
     * Handles dismissal of explanation screen and navigates to threshold test.
     */
    private fun handleExplanationDismiss() {
        inactivityHelper.resetTimer()

        val intent = Intent(this, ThresholdActivity::class.java)
        startActivity(intent)
        finish()
    }
}
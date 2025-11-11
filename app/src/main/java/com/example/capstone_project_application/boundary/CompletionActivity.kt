package com.example.capstone_project_application.boundary

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone_project_application.R
import com.example.capstone_project_application.control.DataRepository
import com.example.capstone_project_application.entity.AppDatabase

/**
 * Final screen displayed when the participant completes the entire experiment.
 *
 * This activity shows a completion message and allows the user to tap anywhere
 * to return to the login screen. The back button is disabled to prevent
 * accidental navigation away from this final state.
 *
 * ## Behavior:
 * - Clears the current participant session
 * - Navigates back to [LoginActivity] with a cleared back stack
 * - Prevents back navigation to maintain experiment flow integrity
 *
 * ## User Interaction:
 * - Tap anywhere on the screen to return to login
 */
class CompletionActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_completion)

        setupTouchListeners()
    }

    /**
     * Configures touch listeners on the root view to enable "tap anywhere to continue" functionality.
     */
    private fun setupTouchListeners() {
        findViewById<View>(android.R.id.content).setOnClickListener {
            handleCompletionDismiss()
        }
    }

    /**
     * Handles any touch event on the screen, fulfilling the "tap anywhere" requirement.
     * Only triggers navigation on the ACTION_UP event to ensure intentional taps.
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            handleCompletionDismiss()
            return true
        }
        return super.onTouchEvent(event)
    }

    /**
     * Disables the back button to prevent accidental navigation away from the completion screen.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally empty - prevents back navigation from completion screen
    }

    /**
     * Handles the completion dismissal flow:
     * 1. Clears the current participant session
     * 2. Navigates to [LoginActivity] with a cleared back stack
     * 3. Finishes this activity
     */
    private fun handleCompletionDismiss() {
        repository.clearCurrentParticipant()

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }
}
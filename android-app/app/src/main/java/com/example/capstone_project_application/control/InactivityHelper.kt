package com.example.capstone_project_application.control

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.capstone_project_application.boundary.LoginActivity
import kotlinx.coroutines.launch

/**
 * Helper class for detecting user inactivity and performing automatic logout.
 *
 * This class monitors user interaction and triggers warnings/logout after
 * specified periods of inactivity. This is essential for:
 * - Protecting participant privacy
 * - Ensuring data integrity (incomplete sessions are cleared)
 * - Managing shared device access
 *
 * ## Timing:
 * - Warning shown after 5 minutes of inactivity
 * - Auto-logout after 6 minutes (1 minute after warning)
 *
 * ## Usage:
 * ```kotlin
 * private lateinit var inactivityHelper: InactivityHelper
 *
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     inactivityHelper = InactivityHelper(this, repository)
 *     inactivityHelper.resetTimer()
 * }
 *
 * override fun onResume() {
 *     super.onResume()
 *     inactivityHelper.resetTimer()
 * }
 *
 * override fun onPause() {
 *     super.onPause()
 *     inactivityHelper.stopTimer()
 * }
 *
 * override fun onUserInteraction() {
 *     super.onUserInteraction()
 *     inactivityHelper.resetTimer()
 * }
 * ```
 *
 * @property activity The activity to monitor (must be AppCompatActivity for lifecycleScope)
 * @property repository Data repository for clearing participant session
 */
class InactivityHelper(
    private val activity: AppCompatActivity,
    private val repository: DataRepository
) {

    private val handler = Handler(Looper.getMainLooper())

    // Dialog state
    private var warningDialog: AlertDialog? = null
    private var isWarningShown = false

    companion object {
        private const val TAG = "InactivityHelper"

        // Timing constants (in milliseconds)
        private const val INACTIVITY_WARNING_TIME = 5 * 60 * 1000L // 5 minutes
        private const val AUTO_LOGOUT_TIME = 6 * 60 * 1000L // 6 minutes
        private const val WARNING_DURATION = AUTO_LOGOUT_TIME - INACTIVITY_WARNING_TIME // 1 minute
    }

    // Runnable for showing warning
    private val inactivityWarningRunnable = Runnable {
        showInactivityWarning()
    }

    // Runnable for auto-logout
    private val autoLogoutRunnable = Runnable {
        performAutoLogout()
    }

    /**
     * Resets the inactivity timer.
     *
     * Call this method whenever user interaction is detected:
     * - In onCreate()
     * - In onResume()
     * - In onUserInteraction()
     * - On button clicks
     */
    fun resetTimer() {
        cancelPendingCallbacks()
        dismissWarningDialog()
        scheduleNewCallbacks()

        Log.v(TAG, "Inactivity timer reset")
    }

    /**
     * Cancels all pending callbacks.
     */
    private fun cancelPendingCallbacks() {
        handler.removeCallbacks(inactivityWarningRunnable)
        handler.removeCallbacks(autoLogoutRunnable)
    }

    /**
     * Dismisses the warning dialog if shown.
     */
    private fun dismissWarningDialog() {
        warningDialog?.dismiss()
        isWarningShown = false
    }

    /**
     * Schedules new warning and logout callbacks.
     */
    private fun scheduleNewCallbacks() {
        handler.postDelayed(inactivityWarningRunnable, INACTIVITY_WARNING_TIME)
        handler.postDelayed(autoLogoutRunnable, AUTO_LOGOUT_TIME)
    }

    /**
     * Stops the inactivity timer.
     *
     * Call this method in:
     * - onPause()
     * - onDestroy()
     */
    fun stopTimer() {
        cancelPendingCallbacks()
        dismissWarningDialog()
        Log.v(TAG, "Inactivity timer stopped")
    }

    /**
     * Shows an inactivity warning dialog to the user.
     *
     * User can dismiss the warning to reset the timer, or ignore it
     * and be logged out after 1 minute.
     */
    private fun showInactivityWarning() {
        if (isWarningShown || isActivityFinishing()) return

        isWarningShown = true
        Log.d(TAG, "Showing inactivity warning")

        warningDialog = createWarningDialog()
        warningDialog?.show()
    }

    /**
     * Creates the inactivity warning dialog.
     *
     * @return AlertDialog instance
     */
    private fun createWarningDialog(): AlertDialog {
        return AlertDialog.Builder(activity)
            .setTitle("Are you still there?")
            .setMessage("You will be logged out in 1 minute due to inactivity.")
            .setPositiveButton("I'm here") { dialog, _ ->
                dialog.dismiss()
                resetTimer() // User responded, reset timer
            }
            .setCancelable(false) // Force user to respond
            .create()
    }

    /**
     * Checks if the activity is finishing or destroyed.
     *
     * @return true if activity is finishing, false otherwise
     */
    private fun isActivityFinishing(): Boolean {
        return activity.isFinishing || activity.isDestroyed
    }

    /**
     * Performs automatic logout due to inactivity.
     *
     * This method:
     * 1. Clears incomplete trial data
     * 2. Clears participant session
     * 3. Shows logout notification
     * 4. Returns to login screen
     */
    private fun performAutoLogout() {
        if (isActivityFinishing()) return

        Log.d(TAG, "Performing auto-logout due to inactivity")

        dismissWarningDialog()

        // Clear data using activity's lifecycleScope
        activity.lifecycleScope.launch {
            try {
                clearParticipantData()
                navigateToLoginOnMainThread()
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-logout cleanup", e)
                // Still navigate even if cleanup fails
                navigateToLoginOnMainThread()
            }
        }
    }

    /**
     * Clears participant data from repository.
     */
    private suspend fun clearParticipantData() {
        repository.clearIncompleteTrialData()
        repository.clearCurrentParticipant()
        Log.d(TAG, "Participant data cleared during auto-logout")
    }

    /**
     * Navigates to login screen on the main thread.
     */
    private fun navigateToLoginOnMainThread() {
        activity.runOnUiThread {
            showLogoutMessage()
            navigateToLogin()
        }
    }

    /**
     * Shows a toast message about logout.
     */
    private fun showLogoutMessage() {
        Toast.makeText(
            activity,
            "Logged out due to inactivity",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * Navigates to the login screen with cleared back stack.
     */
    private fun navigateToLogin() {
        val intent = Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        activity.startActivity(intent)
        activity.finish()
    }
}
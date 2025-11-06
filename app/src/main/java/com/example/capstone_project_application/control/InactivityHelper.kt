package com.example.capstone_project_application.control

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.capstone_project_application.boundary.LoginActivity

/**
 * Helper class to handle user inactivity and auto-logout
 */
class InactivityHelper(
    private val activity: Activity,
    private val repository: DataRepository
) {
    private val handler = Handler(Looper.getMainLooper())
    private val INACTIVITY_WARNING_TIME = 5 * 60 * 1000L // 5 minutes
    private val AUTO_LOGOUT_TIME = 6 * 60 * 1000L // 6 minutes (5 + 1)

    private var warningDialog: AlertDialog? = null
    private var isWarningShown = false

    companion object {
        private const val TAG = "InactivityHelper"
    }

    private val inactivityWarningRunnable = Runnable {
        showInactivityWarning()
    }

    private val autoLogoutRunnable = Runnable {
        performAutoLogout()
    }

    /**
     * Reset the inactivity timer (call this on user interaction)
     */
    fun resetTimer() {
        // Remove existing callbacks
        handler.removeCallbacks(inactivityWarningRunnable)
        handler.removeCallbacks(autoLogoutRunnable)

        // Dismiss warning if shown
        warningDialog?.dismiss()
        isWarningShown = false

        // Schedule new callbacks
        handler.postDelayed(inactivityWarningRunnable, INACTIVITY_WARNING_TIME)
        handler.postDelayed(autoLogoutRunnable, AUTO_LOGOUT_TIME)

        Log.d(TAG, "Inactivity timer reset")
    }

    /**
     * Stop the inactivity timer (call this in onPause or onDestroy)
     */
    fun stopTimer() {
        handler.removeCallbacks(inactivityWarningRunnable)
        handler.removeCallbacks(autoLogoutRunnable)
        warningDialog?.dismiss()
        Log.d(TAG, "Inactivity timer stopped")
    }

    private fun showInactivityWarning() {
        if (isWarningShown || activity.isFinishing) return

        isWarningShown = true
        Log.d(TAG, "Showing inactivity warning")

        warningDialog = AlertDialog.Builder(activity)
            .setTitle("Are you still there?")
            .setMessage("You will be logged out in 1 minute due to inactivity.")
            .setPositiveButton("I'm here") { dialog, _ ->
                dialog.dismiss()
                resetTimer() // Reset timer if user responds
            }
            .setCancelable(false)
            .create()

        warningDialog?.show()
    }

    private fun performAutoLogout() {
        if (activity.isFinishing) return

        Log.d(TAG, "Performing auto-logout due to inactivity")

        warningDialog?.dismiss()

        // Clear incomplete data
        kotlinx.coroutines.GlobalScope.launch {
            try {
                repository.clearIncompleteTrialData()
                repository.clearCurrentParticipant()
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-logout cleanup", e)
            }
        }

        Toast.makeText(
            activity,
            "Logged out due to inactivity",
            Toast.LENGTH_LONG
        ).show()

        // Navigate to login
        val intent = Intent(activity, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        activity.startActivity(intent)
        activity.finish()
    }
}
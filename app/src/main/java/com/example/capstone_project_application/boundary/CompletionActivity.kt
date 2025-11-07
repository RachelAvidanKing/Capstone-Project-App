// File: CompletionActivity.kt

package com.example.capstone_project_application.boundary

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.capstone_project_application.R
import com.example.capstone_project_application.control.DataRepository
import com.example.capstone_project_application.entity.AppDatabase

class CompletionActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure you have a simple layout with your message, e.g., activity_completion.xml
        setContentView(R.layout.activity_completion)

        // Make the entire content view listen for any touch
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setOnClickListener {
            handleCompletionDismiss()
        }
    }

    // This handles any touch event on the screen, fulfilling the "tap anywhere" requirement
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            handleCompletionDismiss()
            return true
        }
        return super.onTouchEvent(event)
    }


    // Disable the back button while on this final screen
    override fun onBackPressed() {
        // Do nothing to prevent accidentally going back
    }

    private fun handleCompletionDismiss() {
        repository.clearCurrentParticipant()
        val intent = Intent(this, LoginActivity::class.java)
        // Clear all back stack and start fresh at LoginActivity
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
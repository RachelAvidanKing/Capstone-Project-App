package com.example.capstone_project_application.boundary

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.capstone_project_application.R
import com.example.capstone_project_application.entity.AppDatabase
import com.example.capstone_project_application.control.DataRepository
import com.example.capstone_project_application.control.InactivityHelper
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }
    private lateinit var inactivityHelper: InactivityHelper

    private lateinit var rgGender: RadioGroup
    private lateinit var rgAge: RadioGroup
    private lateinit var rgEye: RadioGroup
    private lateinit var rgAttentionDeficit: RadioGroup
    private lateinit var btnNext: Button
    private lateinit var btnExit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        inactivityHelper = InactivityHelper(this, repository)
        initializeViews()
        inactivityHelper.resetTimer()
    }

    override fun onResume() {
        super.onResume()
        inactivityHelper.resetTimer()
    }

    override fun onPause() {
        super.onPause()
        inactivityHelper.stopTimer()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        inactivityHelper.resetTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        inactivityHelper.stopTimer()
    }

    override fun onBackPressed() {
        showExitConfirmationDialog()
    }

    private fun initializeViews() {
        rgGender = findViewById(R.id.rgGender)
        rgAge = findViewById(R.id.rgAge)
        rgEye = findViewById(R.id.rgEye)
        rgAttentionDeficit = findViewById(R.id.rgAttentionDeficit)
        btnNext = findViewById(R.id.btnNext)
        btnExit = findViewById(R.id.btnExit)

        btnNext.setOnClickListener {
            inactivityHelper.resetTimer()
            handleNextButtonClick()
        }

        btnExit.setOnClickListener {
            inactivityHelper.resetTimer()
            showExitConfirmationDialog()
        }
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Registration?")
            .setMessage("Your progress will not be saved. You will need to start registration again.")
            .setPositiveButton("Exit") { _, _ ->
                handleExit()
            }
            .setNegativeButton("Continue", null)
            .show()
    }

    private fun handleExit() {
        // Clear the participant ID since they didn't complete registration
        repository.clearCurrentParticipant()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun handleNextButtonClick() {
        val selectedGender = getSelectedGender()
        val selectedAge = getSelectedAge()
        val hasGlasses = getSelectedEyeIssue()
        val hasAttentionDeficit = getSelectedAttentionDeficit()

        if (selectedGender == null || selectedAge == null || hasGlasses == null || hasAttentionDeficit == null) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        btnNext.isEnabled = false
        btnExit.isEnabled = false

        lifecycleScope.launch {
            try {
                // Register participant locally
                val participantId = repository.registerParticipant(
                    age = selectedAge,
                    gender = selectedGender,
                    hasGlasses = hasGlasses,
                    hasAttentionDeficit = hasAttentionDeficit,
                    consentGiven = true
                )

                // Upload demographics to Firebase (section complete)
                val uploadSuccess = repository.uploadParticipantDemographics()

                if (uploadSuccess) {
                    Log.d("RegistrationActivity", "✓ Participant registered and uploaded. ID: $participantId")
                    runOnUiThread {
                        Toast.makeText(
                            this@RegistrationActivity,
                            "Registration successful!",
                            Toast.LENGTH_SHORT
                        ).show()

                        val intent = Intent(this@RegistrationActivity, ThresholdActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@RegistrationActivity,
                            "Registration saved locally. Will upload when online.",
                            Toast.LENGTH_LONG
                        ).show()

                        val intent = Intent(this@RegistrationActivity, ThresholdActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }

            } catch (e: Exception) {
                Log.e("RegistrationActivity", "Error registering participant", e)
                runOnUiThread {
                    Toast.makeText(
                        this@RegistrationActivity,
                        "Registration failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    btnNext.isEnabled = true
                    btnExit.isEnabled = true
                }
            }
        }
    }

    private fun getSelectedGender(): String? {
        return when (rgGender.checkedRadioButtonId) {
            R.id.rbMale -> "Male"
            R.id.rbFemale -> "Female"
            else -> null
        }
    }

    private fun getSelectedAge(): Int? {
        return when (rgAge.checkedRadioButtonId) {
            R.id.rbAge1 -> 22
            R.id.rbAge2 -> 30
            R.id.rbAge3 -> 40
            R.id.rbAge4 -> 53
            R.id.rbAge5 -> 65
            else -> null
        }
    }

    private fun getSelectedEyeIssue(): Boolean? {
        return when (rgEye.checkedRadioButtonId) {
            R.id.rbGlasses -> true
            R.id.rbNoGlasses -> false
            else -> null
        }
    }

    private fun getSelectedAttentionDeficit(): Boolean? {
        return when (rgAttentionDeficit.checkedRadioButtonId) {
            R.id.rbAddYes -> true
            R.id.rbAddNo -> false
            else -> null
        }
    }
}
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

/**
 * Activity for collecting participant demographic information and consent.
 *
 * This activity collects:
 * - Age group
 * - Gender
 * - Visual impairment (glasses/contacts)
 * - Attention deficit status
 *
 * ## Data Flow:
 * 1. Participant enters demographics
 * 2. Data saved locally
 * 3. Attempt Firebase upload (with offline fallback)
 * 4. Navigate to JND threshold test
 *
 * ## Exit Behavior:
 * - Warns user that progress will not be saved
 * - Clears participant ID from session
 * - Returns to login screen
 *
 * Part of the Boundary layer in Entity-Boundary-Control pattern.
 */
class RegistrationActivity : AppCompatActivity() {

    // Data Management
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { DataRepository(database, this) }
    private lateinit var inactivityHelper: InactivityHelper

    // UI Components
    private lateinit var genderRadioGroup: RadioGroup
    private lateinit var ageRadioGroup: RadioGroup
    private lateinit var eyeRadioGroup: RadioGroup
    private lateinit var attentionDeficitRadioGroup: RadioGroup
    private lateinit var nextButton: Button
    private lateinit var exitButton: Button

    companion object {
        private const val TAG = "RegistrationActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        setupWindowInsets()
        initializeViews()
        initializeInactivityHelper()
    }

    /**
     * Sets up window insets for edge-to-edge display.
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * Initializes all view references and sets up click listeners.
     */
    private fun initializeViews() {
        genderRadioGroup = findViewById(R.id.rgGender)
        ageRadioGroup = findViewById(R.id.rgAge)
        eyeRadioGroup = findViewById(R.id.rgEye)
        attentionDeficitRadioGroup = findViewById(R.id.rgAttentionDeficit)
        nextButton = findViewById(R.id.btnNext)
        exitButton = findViewById(R.id.btnExit)

        nextButton.setOnClickListener {
            inactivityHelper.resetTimer()
            handleNextButtonClick()
        }

        exitButton.setOnClickListener {
            inactivityHelper.resetTimer()
            showExitConfirmationDialog()
        }
    }

    /**
     * Initializes the inactivity helper for automatic logout.
     */
    private fun initializeInactivityHelper() {
        inactivityHelper = InactivityHelper(this, repository)
        inactivityHelper.resetTimer()
    }

    // ===========================
    // Lifecycle Methods
    // ===========================

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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showExitConfirmationDialog()
    }

    // ===========================
    // Event Handlers
    // ===========================

    /**
     * Handles the next button click by validating and saving registration data.
     */
    private fun handleNextButtonClick() {
        val demographics = collectDemographics()

        if (!demographics.isComplete()) {
            showToast("Please fill in all fields")
            return
        }

        disableButtons()
        saveRegistrationData(demographics)
    }

    /**
     * Collects demographic data from the form.
     */
    private fun collectDemographics(): Demographics {
        return Demographics(
            gender = getSelectedGender(),
            age = getSelectedAge(),
            hasGlasses = getSelectedEyeIssue(),
            hasAttentionDeficit = getSelectedAttentionDeficit()
        )
    }

    /**
     * Disables buttons to prevent double submission.
     */
    private fun disableButtons() {
        nextButton.isEnabled = false
        exitButton.isEnabled = false
    }

    /**
     * Re-enables buttons after error.
     */
    private fun enableButtons() {
        nextButton.isEnabled = true
        exitButton.isEnabled = true
    }

    // ===========================
    // Exit Handling
    // ===========================

    /**
     * Shows confirmation dialog when exiting registration.
     */
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Registration?")
            .setMessage("Your progress will not be saved. " +
                    "You will need to start registration again.")
            .setPositiveButton("Exit") { _, _ ->
                handleExit()
            }
            .setNegativeButton("Continue", null)
            .show()
    }

    /**
     * Handles exit by clearing session and returning to login.
     */
    private fun handleExit() {
        repository.clearCurrentParticipant()

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    // ===========================
    // Registration Logic
    // ===========================

    /**
     * Saves registration data locally.
     */
    private fun saveRegistrationData(demographics: Demographics) {
        lifecycleScope.launch {
            try {
                val participantId = repository.registerParticipant(
                    age = demographics.age!!,
                    gender = demographics.gender!!,
                    hasGlasses = demographics.hasGlasses!!,
                    hasAttentionDeficit = demographics.hasAttentionDeficit!!,
                    consentGiven = true
                )

                handleRegistrationSuccess(participantId, false)

            } catch (e: Exception) {
                handleRegistrationError(e)
            }
        }
    }

    /**
     * Handles successful registration.
     */
    private fun handleRegistrationSuccess(participantId: String, uploadSuccess: Boolean) {
        Log.d(TAG, "✓ Participant registered and uploaded. ID: $participantId")

        runOnUiThread {
            val message = if (uploadSuccess) {
                "Registration successful!"
            } else {
                "Registration saved locally. Will upload when online."
            }

            showToast(message)
            navigateToThresholdTest()
        }
    }

    /**
     * Handles registration error.
     */
    private fun handleRegistrationError(error: Exception) {
        Log.e(TAG, "Error registering participant", error)

        runOnUiThread {
            showToast("Registration failed: ${error.message}", Toast.LENGTH_LONG)
            enableButtons()
        }
    }

    /**
     * Navigates to the JND threshold explanation screen.
     */
    private fun navigateToThresholdTest() {
        val intent = Intent(this, ExplainJNDActivity::class.java)
        startActivity(intent)
        finish()
    }

    // ===========================
    // Form Data Extraction
    // ===========================

    /**
     * Gets the selected gender from the radio group.
     *
     * @return Gender string or null if not selected
     */
    private fun getSelectedGender(): String? {
        return when (genderRadioGroup.checkedRadioButtonId) {
            R.id.rbMale -> "Male"
            R.id.rbFemale -> "Female"
            else -> null
        }
    }

    /**
     * Gets the selected age group from the radio group.
     * Maps radio buttons to representative ages.
     *
     * @return Age integer or null if not selected
     */
    private fun getSelectedAge(): Int? {
        return when (ageRadioGroup.checkedRadioButtonId) {
            R.id.rbAge1 -> 22
            R.id.rbAge2 -> 30
            R.id.rbAge3 -> 40
            R.id.rbAge4 -> 53
            R.id.rbAge5 -> 65
            else -> null
        }
    }

    /**
     * Gets whether participant wears glasses/contacts.
     *
     * @return Boolean or null if not selected
     */
    private fun getSelectedEyeIssue(): Boolean? {
        return when (eyeRadioGroup.checkedRadioButtonId) {
            R.id.rbGlasses -> true
            R.id.rbNoGlasses -> false
            else -> null
        }
    }

    /**
     * Gets whether participant has attention deficit disorder.
     *
     * @return Boolean or null if not selected
     */
    private fun getSelectedAttentionDeficit(): Boolean? {
        return when (attentionDeficitRadioGroup.checkedRadioButtonId) {
            R.id.rbAddYes -> true
            R.id.rbAddNo -> false
            else -> null
        }
    }

    // ===========================
    // Utility Methods
    // ===========================

    /**
     * Shows a toast message.
     */
    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    // ===========================
    // Data Classes
    // ===========================

    /**
     * Data class to hold demographic information.
     *
     * @property gender Participant's gender
     * @property age Participant's age
     * @property hasGlasses Whether participant wears glasses/contacts
     * @property hasAttentionDeficit Whether participant has ADD/ADHD
     */
    private data class Demographics(
        val gender: String?,
        val age: Int?,
        val hasGlasses: Boolean?,
        val hasAttentionDeficit: Boolean?
    ) {
        /**
         * Checks if all demographic fields are filled.
         *
         * @return true if complete, false otherwise
         */
        fun isComplete(): Boolean {
            return gender != null &&
                    age != null &&
                    hasGlasses != null &&
                    hasAttentionDeficit != null
        }
    }
}
package com.example.capstone_project_application.boundary

import androidx.lifecycle.ViewModel
import com.example.capstone_project_application.control.TargetController
import com.example.capstone_project_application.control.TargetTrialState

class TargetViewModel : ViewModel() {
    // We hold the controller here so it survives rotation
    var controller: TargetController? = null

    var currentTrialState: TargetTrialState? = null
    var isPracticeMode = true
    var practiceTrialNumber = 0
    var isTransitioning = false

    fun isInitialized(): Boolean {
        return controller != null
    }
}
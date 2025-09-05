// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false

    // Declare the KSP plugin here so it can be found by sub-modules.
    // Use the apply false keyword since it's applied in the app-level build.gradle.kts.
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("com.google.gms.google-services") version "4.3.15" apply false
}
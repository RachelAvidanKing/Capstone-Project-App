plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // Use KAPT instead of KSP if KSP is causing issues
    kotlin("kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.capstone_project_application"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.capstone_project_application"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Room Database Dependencies (using KAPT instead of KSP)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // Using KAPT instead of KSP for Room annotation processor
    kapt("androidx.room:room-compiler:$roomVersion")

    // Firebase BOM - Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    // Add the dependency for the Firebase SDK for Google Analytics
    implementation("com.google.firebase:firebase-analytics-ktx")
    // Add the dependency for the Cloud Firestore library
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Optional: Firebase Auth if you want user authentication later
    implementation("com.google.firebase:firebase-auth-ktx")
    // Add the core App Check dependency
    implementation("com.google.firebase:firebase-appcheck")
    // Production Provider (for your specific research tablet)
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    // Debug Provider (for your development/virtual device)
    implementation("com.google.firebase:firebase-appcheck-debug")

    // WorkManager for background tasks (data upload)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
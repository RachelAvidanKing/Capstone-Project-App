###Capstone Project: Effect of Subliminal Perception on Motor Planning

**Students:** Rachel Avidan-King & Daniel Shahar

**Department:** Software Engineering

**Submission:** Final Capstone Project (2026)



**Academic Use Notice**

This repository contains an academic research project submitted for grading and evaluation purposes only.
No commercial use is intended. The repository is public solely to allow academic review.



**Credentials & Security**

For security reasons, all Firebase credentials are excluded from this repository.

For graders:
The required credential files are provided separately in the secure submission archive
project_credentials.zip, which includes:

google-services.json

serviceAccountKey.json

Please follow the instructions below to place these files in the correct locations before running the project.



**Quick Start Instructions**
##1. Python Analysis & Web Dashboard

Two options are provided:

Option A – Standalone Executable (Recommended)

Run:
release-builds/Capstone_Analyzer_Tool.exe

No installation or setup required.

Includes the database connection and web dashboard server.

Option B – Run From Source

Copy serviceAccountKey.json into the python-analysis/ directory.

Double-click User_Help.bat in the project root.

This script automatically installs all required Python and Node.js dependencies and starts the server.



##2. Android Application (Pre-built APK)

A compiled release build is provided for immediate testing.

Open Android Studio → Device Manager and launch an emulator.
*** Important: Use a Tablet configuration (e.g., Pixel Tablet or Medium Tablet) to ensure correct UI scaling.***

Drag and drop release-builds/app-release.apk onto the running emulator.

Launch Capstone App from the emulator’s app drawer.



##3. Android Application (Build From Source)

If you wish to compile the Android application manually:

Copy google-services.json (from project_credentials.zip) into:
android-app/app/

Open the android-app directory in Android Studio.

Sync Gradle and run the application on an emulator or physical device.

Repository Structure

android-app/
Native Kotlin application for experimental data collection (ECB architecture).

python-analysis/
Python backend for data processing, statistical analysis (ANOVA, t-tests), and graph generation.

web-interface/
React-based web dashboard for visualizing experimental results.

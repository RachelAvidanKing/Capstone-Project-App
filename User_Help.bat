@echo off
setlocal enabledelayedexpansion
title Research Toolkit: Setup and Launch

:: --- CONFIGURATION ---
set ROOT_DIR=%~dp0
set PY_DIR=%ROOT_DIR%python-analysis
set WEB_DIR=%ROOT_DIR%web-interface
set VENV_DIR=%PY_DIR%\venv
set KEY_FILE=%PY_DIR%\serviceAccountKey.json

echo ============================================================
echo   REACHING MOVEMENT RESEARCH TOOLKIT - SETUP & LAUNCH
echo ============================================================

:: 1. CHECK FOR FIREBASE KEY (CRITICAL)
if not exist "%KEY_FILE%" (
    echo [ERROR] serviceAccountKey.json not found in %PY_DIR%
    echo Please place the credentials file in the folder and restart.
    pause
    exit /b
)

:: 2. CHECK/INSTALL PYTHON
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Python is NOT installed.
    set /p install_py="Would you like to download the Python installer? (y/n): "
    if /i "!install_py!"=="y" (
        echo Downloading Python...
        curl -L https://www.python.org/ftp/python/3.12.1/python-3.12.1-amd64.exe -o py_installer.exe
        echo.
        echo [IMPORTANT] When the installer opens:
        echo 1. CHECK THE BOX: "Add Python to PATH"
        echo 2. Click "Install Now"
        echo 3. Restart this script after installation finishes.
        start /wait py_installer.exe
        del py_installer.exe
        exit /b
    ) else (
        echo Python is required to run the backend. Exiting...
        pause
        exit /b
    )
)

:: 3. CHECK/INSTALL NODE.JS
call npm --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [!] Node.js is NOT installed.
    set /p install_node="Would you like to download the Node.js installer? (y/n): "
    if /i "!install_node!"=="y" (
        echo Downloading Node.js...
        curl -L https://nodejs.org/dist/v20.11.0/node-v20.11.0-x64.msi -o node_installer.msi
        echo.
        echo [IMPORTANT] Follow the installer prompts and restart this script after.
        start /wait node_installer.msi
        del node_installer.msi
        exit /b
    ) else (
        echo Node.js is required to run the web interface. Exiting...
        pause
        exit /b
    )
)

:: 4. SETUP PYTHON VIRTUAL ENVIRONMENT
if not exist "%VENV_DIR%" (
    echo [1/3] Creating Python Virtual Environment...
    python -m venv "%VENV_DIR%"
)

echo [2/3] Syncing Python dependencies...
call "%VENV_DIR%\Scripts\activate"
pip install -q -r "%PY_DIR%\requirements.txt"

:: 5. SETUP REACT
if not exist "%WEB_DIR%\node_modules\" (
    echo [3/3] First-time React setup: This may take 2-3 minutes...
    cd /d "%WEB_DIR%"
    call npm install
)

:: 6. LAUNCH
echo ============================================================
echo   SUCCESS: Starting Servers...
echo ============================================================

:: Start Backend
start "Backend Server" cmd /k "cd /d %PY_DIR% && call venv\Scripts\activate && python backend_api.py"

echo Waiting for backend to warm up...
timeout /t 6

:: Start Frontend
start "Frontend Web App" cmd /k "cd /d %WEB_DIR% && npm start"

:: Wait a moment for React to claim the port, then open browser
timeout /t 5
start http://localhost:3000

echo.
echo Operation Complete. You can now use the Web Interface.
echo Keep the terminal windows open while working.
pause
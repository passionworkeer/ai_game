@echo off
:: setup-phone.bat — Prepare model and APK for real phone testing
:: Steps:
::   1. Compute SHA-256 of the GGUF model
::   2. Update SHA-256 in LlamaEngineImpl.kt
::   3. Push GGUF model to phone via ADB
::   4. Build APK with native libs
::   5. Install APK on phone

cd /d "%~dp0"
set MODEL_FILE=gemma-4-E4B-it-Q4_0.gguf
set MODEL_SIZE_GB=4.6

echo ============================================================
echo   AI Companion — Real Phone Setup Script
echo ============================================================
echo.

:: ── Step 1: Check GGUF model ──────────────────────────────────
echo [1/5] Checking GGUF model...
if not exist "%MODEL_FILE%" (
    echo ERROR: %MODEL_FILE% not found in project root.
    echo Expected path: E:\desktop\ai_game\%MODEL_FILE%
    echo Model size: ~%MODEL_SIZE_GB% GB
    pause
    exit /b 1
)
for %%A in ("%MODEL_FILE%") do set MODEL_ACTUAL_SIZE=%%~zA
set /a MODEL_SIZE_MB=%MODEL_ACTUAL_SIZE% / 1048576
echo       Found: %MODEL_FILE% (!MODEL_SIZE_MB! MB)
echo.

:: ── Step 2: Compute SHA-256 ───────────────────────────────────
echo [2/5] Computing SHA-256 of %MODEL_FILE%...
echo       (This takes ~10-30 seconds for a 4.6 GB file)
for /f "delims=" %%h in ('sha256sum "%MODEL_FILE%" 2^>nul') do set SHA256=%%h
if not defined SHA256 (
    certutil -hashfile "%MODEL_FILE%" SHA256 | findstr /i /v "CertUtil" | findstr /i /v "SHA256" >nul
    for /f "delims=" %%h in ('certutil -hashfile "%MODEL_FILE%" SHA256 ^| findstr /i /v "CertUtil"') do set SHA256=%%h
)
set SHA256=%SHA256: =%
set SHA256=%SHA256:\=%
echo       SHA-256: %SHA256%
echo.

:: ── Step 3: Update LlamaEngineImpl.kt ─────────────────────────
echo [3/5] Updating SHA-256 in LlamaEngineImpl.kt...
set KT_FILE=android\app\src\main\java\com\aiyougame\companion\llm\LlamaEngineImpl.kt
if not exist "%KT_FILE%" (
    echo ERROR: %KT_FILE% not found
    pause
    exit /b 1
)

:: Replace the empty SHA-256 with the computed value
:: Pattern: private const val MODEL_SHA256 = ""
powershell -Command "(Get-Content '%KT_FILE%') -replace 'private const val MODEL_SHA256 = \"\"', 'private const val MODEL_SHA256 = \"%SHA256%\"' | Set-Content '%KT_FILE%'"
echo       Updated MODEL_SHA256 = "%SHA256%"
echo.

:: ── Step 4: Check ADB ─────────────────────────────────────────
echo [4/5] Checking ADB (Android Debug Bridge)...
adb version 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: 'adb' not found in PATH.
    echo Please install Android SDK Platform Tools or Android Studio.
    echo Download: https://developer.android.com/studio/releases/platform-tools
    echo.
    echo IMPORTANT: After installing, add to PATH:
    echo   ^<SDK_PATH^>\platform-tools
    pause
    exit /b 1
)

:: Check for connected devices
echo       Checking for connected Android devices...
adb devices | findstr /i /v "List" | findstr "." >nul
if %ERRORLEVEL% neq 0 (
    echo.
    echo WARNING: No Android device detected via USB/ADB.
    echo.
    echo Please ensure:
    echo   1. USB debugging is enabled on your phone:
    echo      Settings ^> Developer Options ^> USB Debugging = ON
    echo   2. Phone is connected via USB cable
    echo   3. USB driver installed (Windows)
    echo   4. Authorize this computer on your phone (check for popup)
    echo.
    echo Run 'adb devices' to check manually.
    echo.
    choice /c YN /n /t 10 /d N /m "Continue without pushing model to phone now?"
    if errorlevel 2 goto :skip_model_push
)

:: ── Step 5: Push GGUF model to phone ──────────────────────────
echo [5/5] Pushing model to phone (4.6 GB, may take 3-10 minutes)...
echo       Target: /storage/emulated/0/Android/data/com.aiyougame.companion/files/models/

:: Create directory on phone
adb shell mkdir -p "/data/local/tmp/models" 2>nul
adb shell mkdir -p "/sdcard/Android/data/com.aiyougame.companion/files/models" 2>nul

:: Try pushing to accessible location
:: Method 1: /data/local/tmp (works on all rooted/non-rooted without permission issues)
echo       Pushing to /data/local/tmp/models/ (fastest path)...
adb push "%MODEL_FILE%" /data/local/tmp/models/

if %ERRORLEVEL% equ 0 (
    echo       Model pushed successfully!
    echo.
    echo NOTE: The app will download the model from CDN on first launch.
    echo       To use this local copy, modify ModelDownloader to read from:
    echo       /data/local/tmp/models/%MODEL_FILE%
    echo       (requires root or ADB run-as)
    echo.
) else (
    echo WARNING: Could not push model directly.
    echo       App will download model from CDN on first launch.
    echo.
)

:skip_model_push
echo.

:: ── Build APK ─────────────────────────────────────────────────
echo ============================================================
echo   Building APK with native libs + updated SHA-256...
echo ============================================================
cd android
call .\gradlew.bat :app:assembleDebug --no-daemon -q 2>&1 | findstr /i /v "warning" | findstr /i "error Error FAIL"

if %ERRORLEVEL% neq 0 (
    echo.
    echo NOTE: Above may contain warnings (OK to ignore if BUILD SUCCESSFUL below)
)

:: Check if APK was actually built
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do set APK_SIZE=%%~zA
    set /a APK_SIZE_MB=%APK_SIZE% / 1048576
    echo.
    echo [SUCCESS] APK built: app\build\outputs\apk\debug\app-debug.apk
    echo           Size: !APK_SIZE_MB! MB
) else (
    echo.
    echo [ERROR] APK not found after build. Run build-native.bat first.
    cd ..
    pause
    exit /b 1
)

cd ..

:: ── Install on phone ───────────────────────────────────────────
echo.
echo ============================================================
echo   Ready to install on phone
echo ============================================================
echo.
echo APK location:
echo   E:\desktop\ai_game\android\app\build\outputs\apk\debug\app-debug.apk
echo.
echo Options to install:
echo   A. Auto-install via ADB (recommended):
echo        adb install -r android\app\build\outputs\apk\debug\app-debug.apk
echo.
echo   B. Manual install:
echo        Copy APK to phone (email, AirDrop, cable) and open it.
echo        You may need to enable "Install from unknown sources".
echo.
echo After installing, launch the app and:
echo   1. Grant microphone permission (for Whisper voice input)
echo   2. App will download GGUF model (~4.6 GB from CDN)
echo      Or push manually to: /data/local/tmp/models/
echo   3. Chat with a character — Gemma 4 will generate real responses!
echo.
echo Tip: Monitor logs via:
echo   adb logcat -s LlamaJni:V VoiceRecog:V *:S
echo   adb logcat -s LlamaEngineImpl:V *:S
echo.
pause

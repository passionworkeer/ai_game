@echo off
:: ============================================================
::   AI Companion — Real Phone Demo Setup (All-in-One)
:: ============================================================
:: This script prepares everything for testing on a real
:: Android phone with local Gemma 4 inference.
::
:: WHAT IT DOES:
::   Step 1: Install Android NDK (if missing)
::   Step 2: Build native libs (libllama_jni.so + libwhisper_jni.so)
::   Step 3: Compute model SHA-256 + update code
::   Step 4: Build APK with native libs + updated SHA-256
::   Step 5: Push model to phone via ADB (if connected)
::
:: WHAT YOU NEED ON YOUR PHONE:
::   - Developer mode enabled (Settings > About Phone > Tap Build #7)
::   - USB debugging ON (Settings > Developer Options > USB Debugging)
::   - Connect phone to the SAME WiFi network as this PC
::
:: AFTER RUNNING THIS SCRIPT:
::   1. Start the local model server: serve-model.bat
::   2. Install APK on phone: adb install -r android\app\build\outputs\apk\debug\app-debug.apk
::   3. Open app, grant microphone permission
::   4. App downloads model from your PC (~4.6 GB)
::   5. Chat! Gemma 4 runs locally on your phone
::
:: ============================================================

cd /d "%~dp0"

echo.
echo  ======================================================
echo   AI Companion — Real Phone Setup
echo  ======================================================
echo.

:: ── Step 0: Check prerequisites ────────────────────────────
echo [Step 0/5] Checking prerequisites...

:: Check GGUF model
if not exist "gemma-4-E4B-it-Q4_0.gguf" (
    echo  ERROR: gemma-4-E4B-it-Q4_0.gguf not found!
    echo  Expected: E:\desktop\ai_game\gemma-4-E4B-it-Q4_0.gguf
    echo  This file should be ~4.6 GB.
    pause
    exit /b 1
)

:: Check Python (for HTTP server later)
python --version 2>nul
if %ERRORLEVEL% neq 0 (
    echo  WARNING: Python not found. You won't be able to use serve-model.bat
    echo  Install Python: https://www.python.org/downloads
    echo  (Press any key to continue anyway...)
    pause >nul
)
echo   [OK] GGUF model found
echo.

:: ── Step 1: Install NDK ───────────────────────────────────
echo [Step 1/5] Checking Android NDK...

set SDK_ROOT=%LOCALAPPDATA%\Android\Sdk
set NDK_PATH=%SDK_ROOT%\ndk\26.1.10809160
set NDK_FALLBACK=%SDK_ROOT%\ndk\r26b

if exist "%NDK_PATH%\ndk-build" (
    echo   [OK] NDK r26b found at %NDK_PATH%
    set ANDROID_NDK_HOME=%NDK_PATH%
    goto :ndk_done
)
if exist "%NDK_FALLBACK%\ndk-build" (
    echo   [OK] NDK r26b found at %NDK_FALLBACK%
    set ANDROID_NDK_HOME=%NDK_FALLBACK%
    goto :ndk_done
)

echo   [INFO] NDK not found. Installing Android NDK r26b...
echo   This downloads ~140 MB from Google servers...
echo.

:: Download NDK r26b
powershell -command "Write-Host 'Downloading Android NDK r26b (~140 MB)...'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/android-ndk-r26b-windows.zip' -OutFile '%TEMP%\android-ndk-r26b.zip' -UseBasicParsing"

if %ERRORLEVEL% neq 0 (
    echo.
    echo  [ERROR] Download failed. Check your internet connection.
    echo  Manual install: https://developer.android.com/ndk/downloads
    pause
    exit /b 1
)

echo   Extracting NDK (this takes ~1-2 minutes)...
powershell -command "Expand-Archive -Path '%TEMP%\android-ndk-r26b.zip' -DestinationPath '%SDK_ROOT%\ndk' -Force"

:: Rename to expected path
for /d %%d in ("%SDK_ROOT%\ndk\android-ndk-r26b") do (
    move "%%d" "%SDK_ROOT%\ndk\26.1.10809160" >nul 2>&1
)

if exist "%SDK_ROOT%\ndk\26.1.10809160\ndk-build" (
    set ANDROID_NDK_HOME=%SDK_ROOT%\ndk\26.1.10809160
    echo   [OK] NDK installed to %ANDROID_NDK_HOME%
) else (
    echo   [ERROR] NDK install failed.
    echo   Manual install: https://developer.android.com/ndk/downloads
    pause
    exit /b 1
)

:ndk_done
echo.

:: ── Step 2: Compute SHA-256 + Update Code ─────────────────
echo [Step 2/5] Computing model SHA-256...

for /f "delims=" %%h in ('sha256sum "gemma-4-E4B-it-Q4_0.gguf" 2^>nul') do set SHA256=%%h
if not defined SHA256 (
    for /f "tokens=*" %%h in ('certutil -hashfile "gemma-4-E4B-it-Q4_0.gguf" SHA256 ^| findstr /v "CertUtil" ^| findstr ":"') do set SHA256=%%h
)
:: Clean up spaces
set SHA256=%SHA256: =%
set SHA256=%SHA256:  =%
echo   SHA-256: %SHA256%
echo.

:: Update SHA-256 in LlamaEngineImpl.kt
echo [INFO] Updating LlamaEngineImpl.kt with SHA-256...
set KT_FILE=android\app\src\main\java\com\aiyougame\companion\llm\LlamaEngineImpl.kt
powershell -Command "$content = Get-Content '%KT_FILE%' -Raw; $content = $content -replace 'private const val MODEL_SHA256 = \"[^\"]*\"', 'private const val MODEL_SHA256 = \"%SHA256%\"'; Set-Content -Path '%KT_FILE%' -Value $content -NoNewline"

:: Verify update
findstr /C:"MODEL_SHA256 = \"%SHA256%\"" "%KT_FILE%" >nul
if %ERRORLEVEL% equ 0 (
    echo   [OK] SHA-256 updated in LlamaEngineImpl.kt
) else (
    echo   [WARNING] Could not verify SHA-256 update. Please check %KT_FILE%
)
echo.

:: ── Step 3: Build Native Libraries ────────────────────────
echo [Step 3/5] Building native JNI libraries...
echo   ANDROID_NDK_HOME=%ANDROID_NDK_HOME%
echo   This runs CMake + NDK compiler. First time takes 5-15 minutes.
echo.

cd android
call .\gradlew.bat :app:externalNativeBuildDebug --no-daemon 2>&1 | findstr /i /v "warning" | findstr /i "error Error cmake BUILD"

if %ERRORLEVEL% neq 0 (
    echo   [ERROR] Native build failed.
    echo   Check if NDK is properly installed.
    echo   Verify: %ANDROID_NDK_HOME%\ndk-build
    cd ..
    pause
    exit /b 1
) else (
    echo   [OK] Native libraries built successfully!
)
echo.

:: ── Step 4: Build APK ────────────────────────────────────
echo [Step 4/5] Building APK with native libs...
call .\gradlew.bat :app:assembleDebug --no-daemon -q 2>&1 | findstr /i "FAILURE"

if exist "app\build\outputs\apk\debug\app-debug.apk" (
    for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do set APK_SIZE=%%~zA
    set /a APK_MB=%APK_SIZE% / 1048576
    echo   [OK] APK built: app\build\outputs\apk\debug\app-debug.apk
    echo         Size: %APK_MB% MB (includes native .so libs)
) else (
    echo   [ERROR] APK build failed.
    cd ..
    pause
    exit /b 1
)
cd ..
echo.

:: ── Step 5: Push Model to Phone (optional) ───────────────
echo [Step 5/5] Checking phone connection...
adb devices | findstr /v "List" | findstr "." >nul
if %ERRORLEVEL% equ 0 (
    echo   Phone detected! Pushing model (~4.6 GB)...
    echo   This takes 3-10 minutes depending on USB speed.
    echo.
    adb push "gemma-4-E4B-it-Q4_0.gguf" /data/local/tmp/models/
    if %ERRORLEVEL% equ 0 (
        echo   [OK] Model pushed to /data/local/tmp/models/
    ) else (
        echo   [INFO] Could not push model. App will download from your PC instead.
    )
) else (
    echo   [INFO] No phone detected via ADB.
    echo   App will download the model on first launch.
)
echo.

:: ── Find local IP ─────────────────────────────────────────
echo ======================================================
echo   Setup Complete!
echo ======================================================
echo.
echo YOUR LOCAL IP (for model server):
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4" ^| findstr /v "Tunnel"') do (
    echo   %%a | findstr "." >nul && echo   %%a
)
echo.
echo NEXT STEPS:
echo.
echo 1. START MODEL SERVER (new terminal):
echo    serve-model.bat
echo.
echo 2. UPDATE APK to use local URL:
echo    a) Edit: android\app\src\...\llm\LlamaEngineImpl.kt
echo    b) Change CDN_URL from:
echo       https://cdn.aiyougame.com/models/gemma-4-E4B-it-Q4_0.gguf
echo    c) To your local IP, e.g.:
echo       http://192.168.x.x:8765/gemma-4-E4B-it-Q4_0.gguf
echo    d) Rebuild APK: build-apk.bat
echo.
echo 3. INSTALL ON PHONE:
echo    adb install -r android\app\build\outputs\apk\debug\app-debug.apk
echo.
echo 4. LAUNCH APP AND:
echo    - Grant microphone permission (for Whisper voice input)
echo    - App downloads model from your PC (~4.6 GB)
echo    - Chat! Gemma 4 runs LOCALLY on your phone
echo.
echo MONITOR LOGS (another terminal):
echo    adb logcat -s LlamaJni:V LlamaEngineImpl:V *:S
echo.
echo 5. PUSH MODEL MANUALLY (if ADB push failed):
echo    - Email yourself gemma-4-E4B-it-Q4_0.gguf
echo    - Save to: /sdcard/Download/
echo    - Modify ModelDownloaderImpl.kt to read from /sdcard/Download/
echo.
pause

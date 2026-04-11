@echo off
:: build-native.bat — Build libllama_jni.so and libwhisper_jni.so for Android
:: Prerequisites: Android NDK installed (run install-ndk.bat first)
:: Usage: Double-click this file OR run from project root

cd /d "%~dp0"
set SDK_ROOT=%LOCALAPPDATA%\Android\Sdk
set ANDROID_NDK_HOME=%SDK_ROOT%\ndk\26.1.10809160

if not exist "%ANDROID_NDK_HOME%" (
    set ANDROID_NDK_HOME=%SDK_ROOT%\ndk\r26b
)
if not exist "%ANDROID_NDK_HOME%" (
    echo ERROR: ANDROID_NDK_HOME not set and NDK not found at %SDK_ROOT%\ndk\
    echo.
    echo Run install-ndk.bat first to install the Android NDK.
    pause
    exit /b 1
)

echo [INFO] ANDROID_NDK_HOME=%ANDROID_NDK_HOME%
echo [INFO] Building llama.cpp + whisper.cpp JNI libraries...
echo.

:: Clean any stale build artifacts
if exist "android\app\.cxx" rmdir /s /q "android\app\.cxx"
if exist "android\app\build\intermediates\cmake" rmdir /s /q "android\app\build\intermediates\cmake"

:: Run CMake configure + build via Gradle
:: This invokes cmake.exe from the NDK and compiles llama.cpp + whisper.cpp
cd android
call .\gradlew.bat :app:externalNativeBuildDebug --stacktrace 2>&1 | findstr /i /C:"BUILD" /C:"error" /C:"cmake" /C:"ndk" /C:"Compiling"

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Native build failed. Check output above.
    echo Common fixes:
    echo   1. Verify NDK: %ANDROID_NDK_HOME%
    echo   2. Verify NDK has cmake module: dir "%ANDROID_NDK_HOME%\cmake"
    echo   3. Run: install-ndk.bat
    cd ..
    pause
    exit /b 1
)

echo.
echo [OK] Native libraries built successfully!
echo.
echo Next steps:
echo   1. Update SHA-256 in LlamaEngineImpl.kt (already done by setup-phone.bat)
echo   2. Push model to phone: setup-phone.bat
echo   3. Build APK: build-apk.bat
cd ..
pause

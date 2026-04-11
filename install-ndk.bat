@echo off
:: install-ndk.bat — Install Android NDK via sdkmanager or direct download
:: Must be run from project root (E:\desktop\ai_game)

echo [1/2] Checking Android SDK...

set SDK_ROOT=%LOCALAPPDATA%\Android\Sdk
if not exist "%SDK_ROOT%" (
    echo ERROR: Android SDK not found at %SDK_ROOT%
    echo Please install Android Studio from: https://developer.android.com/studio
    pause
    exit /b 1
)

:: Check if sdkmanager is available
set SDKMANAGER=
for /r "%SDK_ROOT%" %%f in (sdkmanager.bat) do (
    set SDKMANAGER=%%f
    goto :found
)
for /r "%SDK_ROOT%" %%f in (sdkmanager) do (
    set SDKMANAGER=%%f
    goto :found
)

:found
if defined SDKMANAGER (
    echo Found sdkmanager: %SDKMANAGER%
) else (
    echo WARNING: sdkmanager not found in SDK.
    echo          Will download NDK standalone directly.
    echo.
    echo Downloading Android NDK r26b...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/android-ndk-r26b-windows.zip' -OutFile '%TEMP%\android-ndk-r26b-windows.zip'"
    echo Extracting to %SDK_ROOT%\ndk\26.1.10809160\ ...
    powershell -Command "Expand-Archive -Path '%TEMP%\android-ndk-r26b-windows.zip' -DestinationPath '%SDK_ROOT%\ndk' -Force"
    :: Rename to expected path
    for /d %%d in ("%SDK_ROOT%\ndk\android-ndk-r26b") do (
        move "%%d" "%SDK_ROOT%\ndk\26.1.10809160" 2>nul
    )
    echo NDK installed.
    echo.
    echo IMPORTANT: Set environment variable:
    echo   ANDROID_NDK_HOME=%SDK_ROOT%\ndk\26.1.10809160
    echo Or in Android Studio: File > Project Structure > SDK Location > Android NDK Location
    echo.
    echo Also add to your system PATH:
    echo   %SDK_ROOT%\ndk\26.1.10809160
    echo.
    echo After setting ANDROID_NDK_HOME, re-run this script or run build-native.bat
    pause
    exit /b 0
)

:: Use sdkmanager to install NDK
echo [2/2] Installing NDK r26b via sdkmanager...
call "%SDKMANAGER%" "ndk;26.1.10809160"

:: Also install cmake and build-tools if missing
call "%SDKMANAGER%" "cmake;3.22.1" 2>nul
call "%SDKMANAGER%" "build-tools;34.0.0" 2>nul

echo.
echo [OK] NDK r26b installed.
echo.
echo Set environment variable (persist via System Properties > Env Vars):
echo   ANDROID_NDK_HOME=%SDK_ROOT%\ndk\26.1.10809160
echo.
echo Then run: build-native.bat
pause

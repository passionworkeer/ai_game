@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo.
echo ======================================================
echo   Setup JDK 17 for Android build (Windows)
echo ======================================================
echo.

set "INSTALL_ROOT=D:\java"
set "JDK_DIR=%INSTALL_ROOT%\jdk-17"
set "ZIP_PATH=%TEMP%\temurin-jdk17.zip"
set "EXTRACT_DIR=%TEMP%\temurin-jdk17-extract"

if not exist "%INSTALL_ROOT%" (
  echo [INFO] Creating %INSTALL_ROOT%
  mkdir "%INSTALL_ROOT%" >nul 2>&1
)

echo [INFO] Downloading Temurin JDK 17 (x64)...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue';" ^
  "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
  "Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile '%ZIP_PATH%' -UseBasicParsing"

if %ERRORLEVEL% neq 0 (
  echo [ERROR] Download failed.
  echo         Please check your network and try again.
  pause
  exit /b 1
)

if exist "%EXTRACT_DIR%" rmdir /s /q "%EXTRACT_DIR%" >nul 2>&1
mkdir "%EXTRACT_DIR%" >nul 2>&1

echo [INFO] Extracting...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "Expand-Archive -Path '%ZIP_PATH%' -DestinationPath '%EXTRACT_DIR%' -Force"

if %ERRORLEVEL% neq 0 (
  echo [ERROR] Extract failed.
  pause
  exit /b 1
)

set "FOUND_JDK="
for /d %%D in ("%EXTRACT_DIR%\*") do (
  if exist "%%D\bin\java.exe" (
    set "FOUND_JDK=%%D"
  )
)

if not defined FOUND_JDK (
  echo [ERROR] Could not find java.exe in extracted archive.
  echo         Folder: %EXTRACT_DIR%
  pause
  exit /b 1
)

if exist "%JDK_DIR%" (
  for /f "tokens=1-3 delims=/- " %%a in ("%date%") do set "TODAY=%%a%%b%%c"
  set "BACKUP_DIR=%INSTALL_ROOT%\jdk-17_old_%RANDOM%"
  echo [INFO] Existing %JDK_DIR% found, moving to %BACKUP_DIR%
  move "%JDK_DIR%" "%BACKUP_DIR%" >nul 2>&1
)

echo [INFO] Installing to %JDK_DIR%
move "%FOUND_JDK%" "%JDK_DIR%" >nul 2>&1
if %ERRORLEVEL% neq 0 (
  echo [ERROR] Install move failed (file lock/permission?).
  echo         Try closing Android Studio / any Gradle daemons and rerun.
  pause
  exit /b 1
)

echo [INFO] Setting JAVA_HOME (User) to %JDK_DIR%
setx JAVA_HOME "%JDK_DIR%" >nul

echo [INFO] Prepending JAVA_HOME\\bin to PATH (User) if missing...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$jdkBin = '%JDK_DIR%\\bin';" ^
  "$p = [Environment]::GetEnvironmentVariable('Path','User');" ^
  "if ($p -notmatch [Regex]::Escape($jdkBin)) { " ^
  "  if ([string]::IsNullOrWhiteSpace($p)) { $p = $jdkBin } else { $p = $jdkBin + ';' + $p }" ^
  "  [Environment]::SetEnvironmentVariable('Path',$p,'User');" ^
  "}"

echo.
echo [OK] JDK 17 installed.
echo.
echo IMPORTANT:
echo   - Close and reopen your terminal so PATH/JAVA_HOME reloads.
echo   - Project is configured to use: android\\gradle.properties ^(org.gradle.java.home=D:/java/jdk-17^)
echo.
echo Verify:
echo   java -version
echo.
pause
exit /b 0


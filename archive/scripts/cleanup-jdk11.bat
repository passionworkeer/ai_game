@echo off
setlocal enabledelayedexpansion

echo.
echo ======================================================
echo   Cleanup old JDK 11 (safe backup rename)
echo ======================================================
echo.

set "OLD_JAVA_HOME=D:\java\jdk-11.0.29_windows-x64_bin\jdk-11.0.29"
set "BACKUP_PARENT=D:\java\_old"

if not exist "%OLD_JAVA_HOME%" (
  echo [INFO] Old JDK 11 folder not found:
  echo        %OLD_JAVA_HOME%
  echo Nothing to do.
  pause
  exit /b 0
)

if not exist "%BACKUP_PARENT%" (
  mkdir "%BACKUP_PARENT%" >nul 2>&1
)

for /f "tokens=1-3 delims=/:. " %%a in ("%date%") do set "STAMP=%%a%%b%%c"
for /f "tokens=1-3 delims=:." %%a in ("%time%") do set "TSTAMP=%%a%%b%%c"
set "DEST=%BACKUP_PARENT%\jdk-11_backup_%RANDOM%"

echo [INFO] Moving:
echo   FROM: %OLD_JAVA_HOME%
echo   TO:   %DEST%
echo.

move "%OLD_JAVA_HOME%" "%DEST%" >nul 2>&1
if %ERRORLEVEL% neq 0 (
  echo [ERROR] Move failed (files in use or permission issue).
  echo         Close Android Studio and any java/gradle processes, then rerun.
  pause
  exit /b 1
)

echo [OK] JDK 11 moved aside.
echo.
echo Note:
echo   - This does NOT delete anything; it only moved the folder.
echo   - Your Android build now uses JDK 17 via android\gradle.properties.
echo.
pause
exit /b 0


@echo off
chcp 65001 >nul 2>&1
setlocal

echo ═══════════════════════════════════════
echo   AI Companion App — 一键测试脚本
echo ═══════════════════════════════════════
echo.

:: ── 1. 检查 APK ──────────────────────────
echo [1/4] 检查 APK...
set APK_DIR=%~dp0android\app\build\outputs\apk\debug
set APK=%APK_DIR%\app-debug.apk

if exist "%APK%" (
    for %%A in ("%APK%") do echo     APK: %%~nxA  ^(%%~zA / 1024 / 1024MB^)
) else (
    echo     [错误] APK 未找到！需要先编译：
    echo     cd android ^&^& ./gradlew assembleDebug
    goto :end
)

:: ── 2. 检查 Ollama ───────────────────────
echo.
echo [2/4] 检查 Ollama 服务...
curl -s --max-time 3 http://127.0.0.1:11434/ >nul 2>&1
if %errorlevel% equ 0 (
    echo     Ollama: ✅ 运行中
    curl -s http://127.0.0.1:11434/api/tags | findstr /i "gemma" >nul 2>&1
    if %errorlevel% equ 0 (
        echo     模型已就绪
    ) else (
        echo     [警告] 未检测到 Gemma 模型
    )
) else (
    echo     [警告] Ollama 未运行！
    echo     请启动 Ollama:
    echo     %USERPROFILE%\AppData\Local\Programs\Ollama\ollama.exe
)

:: ── 3. 启动模拟器 ────────────────────────
echo.
echo [3/4] 启动模拟器...
set ANDROID_SDK_ROOT=D:\android-sdk

if exist "D:\android-sdk\emulator\emulator.exe" (
    start "AI Companion Emulator" "D:\android-sdk\emulator\emulator.exe" -avd aiyougame_test -no-snapshot-load -no-audio
    echo     模拟器窗口已打开，请等待 30 秒启动完成
) else (
    echo     [错误] 未找到模拟器，请安装 Android Studio
)

:: ── 4. 等待 + 安装 ──────────────────────
echo.
echo [4/4] 等待设备连接...
timeout /t 30 /nobreak >nul

D:\android-sdk\platform-tools\adb.exe devices
echo.
echo 安装 APK:
D:\android-sdk\platform-tools\adb.exe install -r "%APK%"
echo.
echo ─────────────────────────
echo 完成！打开 App 开始测试。
echo 模拟器里进入聊天后，Ollama 会接管 AI 推理。
echo ─────────────────────────
goto :end

:end
endlocal
pause

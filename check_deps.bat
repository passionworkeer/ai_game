@echo off
chcp 65001 >nul 2>&1
setlocal

echo ═══════════════════════════════════════
echo   AI Companion — 环境检查
echo ═══════════════════════════════════════
echo.

:: ── APK ──────────────────────────────────
echo [APK]
set APK=%~dp0android\app\build\outputs\apk\debug\app-debug.apk
if exist "%APK%" (
    echo   app-debug.apk  ✅  (已编译)
) else (
    echo   app-debug.apk  ❌  (未找到)
    echo   运行: cd android ^&^& ./gradlew assembleDebug
)
echo.

:: ── Ollama ──────────────────────────────
echo [Ollama 服务]
curl -s --max-time 3 http://127.0.0.1:11434/ >nul 2>&1
if %errorlevel% equ 0 (
    echo   Ollama 运行中  ✅
    for /f "delims=" %%i in ('curl -s http://127.0.0.1:11434/api/tags') do (
        echo   模型列表:
        echo   %%i | findstr /i "model" | findstr /i "gemma"
    )
) else (
    echo   Ollama 未运行  ❌
    echo   启动: %USERPROFILE%\AppData\Local\Programs\Ollama\ollama.exe
)
echo.

:: ── 推理测试 ─────────────────────────────
echo [Ollama 推理测试]
curl -s --max-time 60 -X POST http://127.0.0.1:11434/api/chat ^
  -H "Content-Type: application/json" ^
  -d "{\"model\":\"gemma-4-e2b-uncensored\",\"stream\":false,\"think\":false,\"options\":{\"num_predict\":30},\"messages\":[{\"role\":\"system\",\"content\":\"你叫顾晨，24岁，男。你是一个温柔的人。短句回复。\"},{\"role\":\"user\",\"content\":\"你好\"}]}" >nul 2>&1
if %errorlevel% equ 0 (
    echo   模型推理正常  ✅
) else (
    echo   模型推理失败  ❌
)
echo.

:: ── Android SDK ─────────────────────────
echo [Android SDK]
if exist "D:\android-sdk\platform-tools\adb.exe" (
    echo   SDK 目录: D:\android-sdk  ✅
    echo   ADB 版本:
    D:\android-sdk\platform-tools\adb.exe version
) else (
    echo   SDK 未找到  ❌
)
echo.

:: ── 设备连接 ─────────────────────────────
echo [设备连接]
D:\android-sdk\platform-tools\adb.exe devices 2>nul
echo.

:: ── BuildConfig 验证 ────────────────────
echo [BuildConfig (Ollama 模式)]
findstr /C:"OLLAMA_ENABLED.*true" "%~dp0android\app\src\main\java\com\aiyougame\companion\llm\OllamaEngineImpl.kt" >nul 2>&1
if %errorlevel% equ 0 (
    echo   OllamaEngineImpl: ✅ 已启用
) else (
    echo   OllamaEngineImpl: ❌ 未找到
)
echo.

echo ════════════════════════════════════════
echo 检查完成
echo ════════════════════════════════════════
endlocal
pause

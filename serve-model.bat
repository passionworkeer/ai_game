@echo off
:: serve-model.bat — Start a local HTTP server to serve the GGUF model to the phone
:: The phone will download the model from this PC (no CDN needed!)
::
:: Usage:
::   1. Run this script (keep it running)
::   2. Note your PC's IP address shown below
::   3. On the phone app, go to Settings and set model URL to http://<YOUR_IP>:8765
::      OR: edit the CDN_URL in LlamaEngineImpl.kt to your PC's IP
::   4. The app will download ~4.6 GB from this PC
::
:: Alternative (for tech users):
::   python -m http.server 8765 --directory . (in project root)
::   Then set CDN_URL to http://<YOUR_IP>:8765/gemma-4-E4B-it-Q4_0.gguf

cd /d "%~dp0"

echo ============================================================
echo   Local Model HTTP Server
echo ============================================================
echo.
echo This script serves the GGUF model to your Android phone
echo without needing a CDN server.
echo.

:: Check if model exists
if not exist "gemma-4-E4B-it-Q4_0.gguf" (
    echo ERROR: gemma-4-E4B-it-Q4_0.gguf not found in project root.
    echo Expected: E:\desktop\ai_game\gemma-4-E4B-it-Q4_0.gguf
    pause
    exit /b 1
)

:: Find local IP address
echo Finding your IP address...
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4" ^| findstr /v " Tunnel "') do (
    echo    %%a | findstr "." >nul
    if !errorlevel! equ 0 echo    %%a
)
echo.
echo NOTE: Use the IP shown above (e.g. 192.168.x.x)
echo       NOT 127.0.0.1 (that only works on your PC)
echo.

:: Try Python first (best for large file Range request support)
python --version 2>nul
if %ERRORLEVEL% equ 0 (
    echo Starting Python HTTP server on port 8765...
    echo.
    echo In the app, the model will auto-download from:
    echo   http://<YOUR_IP>:8765/gemma-4-E4B-it-Q4_0.gguf
    echo.
    echo IMPORTANT: Edit LlamaEngineImpl.kt and change:
    echo   private const val CDN_URL = "https://cdn.aiyougame.com/models/..."
    echo   to:
    echo   private const val CDN_URL = "http://YOUR_IP:8765/"
    echo.
    echo OR: Edit CDN_URL directly and rebuild APK.
    echo.
    echo Press Ctrl+C to stop the server when done.
    echo.
    python -m http.server 8765 --directory .
    goto :end
)

:: Fallback: PowerShell HTTP listener (slower, less efficient)
powershell -command "
    Write-Host 'Starting PowerShell HTTP server on port 8765...'
    Write-Host 'This is a fallback - Python is recommended.'
    Write-Host ''
    $listener = New-Object System.Net.HttpListener
    $listener.Prefixes.Add('http://+:8765/')
    try {
        $listener.Start()
        Write-Host 'Server started on http://*:8765'
        Write-Host 'Press Ctrl+C to stop'
        while ($true) {
            $context = $listener.GetContext()
            $response = $context.Response
            $filePath = Join-Path $PWD.Path 'gemma-4-E4B-it-Q4_0.gguf'
            if (Test-Path $filePath) {
                $fileBytes = [System.IO.File]::ReadAllBytes($filePath)
                $response.ContentLength64 = $fileBytes.Length
                $response.ContentType = 'application/octet-stream'
                $response.OutputStream.Write($fileBytes, 0, $fileBytes.Length)
            } else {
                $response.StatusCode = 404
            }
            $response.Close()
        }
    } finally {
        $listener.Stop()
    }
"
if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Could not start HTTP server.
    echo Please install Python: https://www.python.org/downloads
    echo.
    echo Alternative: Use a free tool like HFS (HTTP File Server):
    echo   https://www.rejetto.com/hfs/
)

:end
pause

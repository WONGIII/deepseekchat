@echo off
setlocal EnableExtensions
title DeepSeek Chat + CosyVoice TTS + ASR

set "CHAT_DIR=%~dp0"
set "COSY_DIR=%~dp0..\cosyvoice3"
for %%I in ("%COSY_DIR%") do set "COSY_DIR=%%~fI"
set "LOGS=%CHAT_DIR%logs"
if not exist "%LOGS%" mkdir "%LOGS%"

echo [1/5] Stopping old services on ports 8090 / 8000...
powershell -NoProfile -ExecutionPolicy Bypass -Command "foreach($port in 8090,8000){ Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object { $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue; if($p -and ($p.ProcessName -eq 'node' -or $p.ProcessName -eq 'python' -or $p.ProcessName -eq 'pythonw')) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } } }"

echo [2/5] Starting chat dev server...
start /b /d "%CHAT_DIR%" cmd /c "pnpm dev > %LOGS%\chat.log 2>&1"

echo [3/5] Starting CosyVoice TTS + Whisper ASR adapter on 8000...
start /b /d "%COSY_DIR%" cmd /c ".venv\Scripts\python.exe server.py --host 127.0.0.1 --port 8000 > %LOGS%\tts.log 2>&1"

echo [4/5] Waiting for chat server...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(90); while((Get-Date) -lt $deadline){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8090/' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0} } catch {} ; Start-Sleep -Milliseconds 500 }; exit 1"
if errorlevel 1 (
  echo.
  echo Chat server failed to start. See: %LOGS%\chat.log
  type "%LOGS%\chat.log"
  pause
  exit /b 1
)

echo [5/5] Waiting for CosyVoice and Whisper...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(120); while((Get-Date) -lt $deadline){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8000/health' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0} } catch {} ; Start-Sleep -Milliseconds 500 }; exit 1"
if errorlevel 1 (
  echo.
  echo WARNING: TTS/ASR adapter is not ready. See: %LOGS%\tts.log
  echo Chat is still available.
)

start "" "http://localhost:8090/"
echo.
echo ============================================================
echo   Chat:    http://localhost:8090
echo   TTS:     CosyVoice3 (via http://127.0.0.1:8000)
echo   ASR:     Whisper (http://127.0.0.1:8000)
echo   Logs:    %LOGS%
echo.
echo   Close this window to stop all services.
echo ============================================================
echo.

:wait
ping -n 4 127.0.0.1 >nul
goto wait

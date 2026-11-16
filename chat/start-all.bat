@echo off
setlocal EnableExtensions
title DeepSeek Chat + CosyVoice TTS + ASR

set "CHAT_DIR=%~dp0"
set "COSY_DIR=%~dp0..\cosyvoice3"
for %%I in ("%COSY_DIR%") do set "COSY_DIR=%%~fI"
set "BACKEND_DIR=%CHAT_DIR%server"
set "LOGS=%CHAT_DIR%logs"
if not exist "%LOGS%" mkdir "%LOGS%"

echo [1/6] Stopping old services on ports 8090 / 8098 / 8000 / 8001 / 8002...
powershell -NoProfile -ExecutionPolicy Bypass -Command "foreach($port in 8090,8098,8000,8001,8002){ Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object { $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue; if($p -and ($p.ProcessName -eq 'node' -or $p.ProcessName -eq 'python' -or $p.ProcessName -eq 'pythonw')) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } } }"

echo [2/6] Building frontend...
set NODE_ENV=production
call pnpm exec rspack build -c rspack.config.ts > "%LOGS%\build.log" 2>&1
if errorlevel 1 (
  echo Frontend build failed. See: %LOGS%\build.log
  type "%LOGS%\build.log"
  pause
  exit /b 1
)

echo [3/6] Starting account/chat backend on 8098...
start /b /d "%BACKEND_DIR%" cmd /c "node index.mjs > %LOGS%\backend.log 2>&1"

echo [4/6] Starting CosyVoice TTS + Whisper ASR adapter on 8000...
start /b /d "%COSY_DIR%" cmd /c ".venv\Scripts\python.exe server.py --host 0.0.0.0 --port 8000 > %LOGS%\tts.log 2>&1"

echo [5/6] Waiting for backend...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(90); while((Get-Date) -lt $deadline){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8098/api/health' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0} } catch {} ; Start-Sleep -Milliseconds 500 }; exit 1"
if errorlevel 1 (
  echo.
  echo Backend failed to start. See: %LOGS%\backend.log
  type "%LOGS%\backend.log"
  pause
  exit /b 1
)

echo [6/6] Waiting for CosyVoice and Whisper...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(120); while((Get-Date) -lt $deadline){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8000/health' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0} } catch {} ; Start-Sleep -Milliseconds 500 }; exit 1"
if errorlevel 1 (
  echo.
  echo WARNING: TTS/ASR adapter is not ready. See: %LOGS%\tts.log
  echo Chat is still available.
)

start "" "http://localhost:8098/"
echo.
echo ============================================================
echo   Chat / login: http://localhost:8098
echo   TTS:          CosyVoice3 (via http://127.0.0.1:8000)
echo   ASR:          Whisper (http://127.0.0.1:8000)
echo   Backend:      0.0.0.0:8098
echo   Logs:         %LOGS%
echo.
echo   Close this window to stop all services.
echo ============================================================
echo.

:wait
ping -n 4 127.0.0.1 >nul
goto wait

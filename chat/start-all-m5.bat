@echo off
setlocal EnableExtensions
title DeepSeek Chat + M5-TTS + Whisper ASR

set "CHAT_DIR=%~dp0"
set "COSY_DIR=%~dp0..\cosyvoice3"
set "M5_DIR=%~dp0..\m5-tts"
set "GSV_DIR=%~dp0..\gpt-sovits"
for %%I in ("%COSY_DIR%") do set "COSY_DIR=%%~fI"
for %%I in ("%M5_DIR%") do set "M5_DIR=%%~fI"
for %%I in ("%GSV_DIR%") do set "GSV_DIR=%%~fI"
set "PYTHON=%GSV_DIR%\.venv\Scripts\python.exe"
set "BACKEND_DIR=%CHAT_DIR%server"
set "LOGS=%CHAT_DIR%logs"
if not exist "%LOGS%" mkdir "%LOGS%"

if not exist "%M5_DIR%\m5_server.py" (
  echo M5-TTS not found at: %M5_DIR%
  pause
  exit /b 1
)
if not exist "%PYTHON%" (
  echo GPT-SoVITS Python environment not found at: %PYTHON%
  echo The 8000 ASR adapter reuses the GPT-SoVITS venv; create it first.
  pause
  exit /b 1
)

echo [1/8] Stopping old services on ports 8090 / 8098 / 8000 / 8001 / 8002...
powershell -NoProfile -ExecutionPolicy Bypass -Command "foreach($port in 8090,8098,8000,8001,8002){ Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object { $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue; if($p -and ($p.ProcessName -eq 'node' -or $p.ProcessName -eq 'python' -or $p.ProcessName -eq 'pythonw')) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } } }"

echo [2/8] Building frontend...
set NODE_ENV=production
call pnpm exec rspack build -c rspack.config.ts > "%LOGS%\build.log" 2>&1
if errorlevel 1 (
  echo Frontend build failed. See: %LOGS%\build.log
  type "%LOGS%\build.log"
  pause
  exit /b 1
)

echo [3/8] Starting account/chat backend on 8098...
start /b /d "%BACKEND_DIR%" cmd /c "node index.mjs > %LOGS%\backend.log 2>&1"

echo [4/8] Starting M5-TTS ONNX server on 8001...
start /b /d "%M5_DIR%" cmd /c ".venv\Scripts\python.exe m5_server.py --host 0.0.0.0 --port 8001 --device cuda --threads 4 > %LOGS%\m5.log 2>&1"

echo [5/8] Starting ASR adapter on 8000 (TTS: M5, ASR: Whisper)...
start /b /d "%COSY_DIR%" cmd /c ""%PYTHON%" server.py --host 0.0.0.0 --port 8000 --tts-engine m5 --m5-url http://127.0.0.1:8001 > "%LOGS%\tts.log" 2>&1"

echo [6/8] Waiting for backend...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(90); while((Get-Date) -lt $deadline){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8098/api/health' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0} } catch {} ; Start-Sleep -Milliseconds 500 }; exit 1"
if errorlevel 1 (
  echo Backend failed to start. See: %LOGS%\backend.log
  type "%LOGS%\backend.log"
  pause
  exit /b 1
)

echo [7/8] Waiting for M5-TTS...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(120); while((Get-Date) -lt $deadline){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8001/health' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0} } catch {} ; Start-Sleep -Milliseconds 500 }; exit 1"
if errorlevel 1 (
  echo WARNING: M5-TTS is not ready. See: %LOGS%\m5.log
)

echo [8/8] Waiting for ASR adapter and Whisper...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(120); while((Get-Date) -lt $deadline){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8000/health' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0} } catch {} ; Start-Sleep -Milliseconds 500 }; exit 1"
if errorlevel 1 (
  echo WARNING: TTS/ASR adapter is not ready. See: %LOGS%\tts.log
)

start "" "http://localhost:8098/"
echo.
echo ============================================================
echo   Chat / login: http://localhost:8098
echo   TTS:          M5-TTS-50M-ONNX (via http://127.0.0.1:8000)
echo   ASR:          Whisper (http://127.0.0.1:8000)
echo   Backend:      0.0.0.0:8098
echo   Logs:         %LOGS%
echo.
echo Close this window to stop all services.
echo ============================================================
echo.

:wait
ping -n 4 127.0.0.1 >nul
goto wait

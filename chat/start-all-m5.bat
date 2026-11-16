@echo off
setlocal EnableExtensions
title DeepSeek Chat + M5-TTS + Whisper ASR

set "CHAT_DIR=%~dp0"
set "COSY_DIR=%~dp0..\cosyvoice3"
set "M5_DIR=%~dp0..\m5-tts"
for %%I in ("%COSY_DIR%") do set "COSY_DIR=%%~fI"
for %%I in ("%M5_DIR%") do set "M5_DIR=%%~fI"
set "LOGS=%CHAT_DIR%logs"
if not exist "%LOGS%" mkdir "%LOGS%"

if not exist "%M5_DIR%\m5_server.py" (
  echo M5-TTS not found at: %M5_DIR%
  echo See the M5-TTS setup instructions first.
  pause
  exit /b 1
)

echo [1/7] Stopping old services on ports 8090 / 8000 / 8001...
powershell -NoProfile -ExecutionPolicy Bypass -Command "foreach($port in 8090,8000,8001){ Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object { $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue; if($p -and ($p.ProcessName -eq 'node' -or $p.ProcessName -eq 'python' -or $p.ProcessName -eq 'pythonw')) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } } }"

echo [2/7] Starting chat dev server...
start /b /d "%CHAT_DIR%" cmd /c "pnpm dev > %LOGS%\chat.log 2>&1"

echo [3/7] Starting M5-TTS ONNX server on 8001 (GPU fallback CPU)...
start /b /d "%M5_DIR%" cmd /c ".venv\Scripts\python.exe m5_server.py --host 127.0.0.1 --port 8001 --device cuda --threads 4 > %LOGS%\m5.log 2>&1"

echo [4/7] Starting CosyVoice ASR adapter on 8000 (TTS: M5, ASR: Whisper)...
start /b /d "%COSY_DIR%" cmd /c ".venv\Scripts\python.exe server.py --host 127.0.0.1 --port 8000 --tts-engine m5 --m5-url http://127.0.0.1:8001 > %LOGS%\tts.log 2>&1"

echo [5/7] Waiting for chat server...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(90); while((Get-Date) -lt $deadline){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8090/' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0} } catch {} ; Start-Sleep -Milliseconds 500 }; exit 1"
if errorlevel 1 (
  echo.
  echo Chat server failed to start. See: %LOGS%\chat.log
  type "%LOGS%\chat.log"
  pause
  exit /b 1
)

echo [6/7] Waiting for M5-TTS...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$deadline=(Get-Date).AddSeconds(120); while((Get-Date) -lt $deadline){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8001/health' -TimeoutSec 2; if($r.StatusCode -eq 200){exit 0} } catch {} ; Start-Sleep -Milliseconds 500 }; exit 1"
if errorlevel 1 (
  echo.
  echo WARNING: M5-TTS is not ready. See: %LOGS%\m5.log
)

echo [7/7] Waiting for ASR adapter and Whisper...
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
echo   TTS:     M5-TTS-50M-ONNX (via http://127.0.0.1:8000)
echo   ASR:     Whisper (http://127.0.0.1:8000)
echo   Logs:    %LOGS%
echo.
echo   Close this window to stop all services.
echo ============================================================
echo.

:wait
ping -n 4 127.0.0.1 >nul
goto wait

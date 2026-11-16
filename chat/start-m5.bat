@echo off
setlocal
set "M5_DIR=%~dp0..\m5-tts"
for %%I in ("%M5_DIR%") do set "M5_DIR=%%~fI"
cd /d "%M5_DIR%"
title M5-TTS ONNX (8001)
echo Starting M5-TTS on http://0.0.0.0:8001 ...
.venv\Scripts\python.exe m5_server.py --host 0.0.0.0 --port 8001 --device cuda --threads 4
pause

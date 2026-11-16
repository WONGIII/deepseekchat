@echo off
setlocal
set "COSY_DIR=%~dp0..\cosyvoice3"
for %%I in ("%COSY_DIR%") do set "COSY_DIR=%%~fI"
cd /d "%COSY_DIR%"
title CosyVoice TTS + Whisper ASR (8000)
echo Starting CosyVoice TTS + Whisper ASR on http://0.0.0.0:8000 ...
.venv\Scripts\python.exe server.py --host 0.0.0.0 --port 8000
pause

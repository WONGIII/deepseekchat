@echo off
setlocal
set "GSV_DIR=%~dp0..\gpt-sovits"
for %%I in ("%GSV_DIR%") do set "GSV_DIR=%%~fI"
cd /d "%GSV_DIR%"
title GPT-SoVITS V5 Turbo (8002)
echo Starting GPT-SoVITS V5 Turbo on http://0.0.0.0:8002 ...
.venv\Scripts\python.exe gsv_server.py --host 0.0.0.0 --port 8002 --device cuda
pause

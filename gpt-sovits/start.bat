@echo off
cd /d %~dp0
echo Starting GPT-SoVITS V5 Turbo on http://127.0.0.1:8002 ...
.venv\Scripts\python.exe gsv_server.py --host 0.0.0.0 --port 8002 --device cuda
pause

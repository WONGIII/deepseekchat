@echo off
cd /d %~dp0
echo Starting M5-TTS ONNX on http://127.0.0.1:8001 ...
.venv\Scripts\python.exe m5_server.py --host 127.0.0.1 --port 8001 --device cuda --threads 4
pause

@echo off
cd /d %~dp0
echo Starting CosyVoice3 TTS adapter on http://127.0.0.1:8000 ...
.venv\Scripts\python.exe server.py --host 127.0.0.1 --port 8000 --model-dir "%~dp0repo\pretrained_models\Fun-CosyVoice3-0.5B"
pause

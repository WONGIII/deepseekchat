@echo off
setlocal
cd /d %~dp0
title DeepSeek Chat frontend dev server (8090)
echo Frontend dev server on http://0.0.0.0:8090
echo API calls are proxied to http://127.0.0.1:8098.
echo Start start-backend.bat separately.
pnpm dev
pause

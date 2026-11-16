@echo off
setlocal
cd /d %~dp0
title DeepSeek Chat backend (8098)
set NODE_ENV=production
echo Building frontend...
call pnpm exec rspack build -c rspack.config.ts
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)
cd server
set PORT=8098
set HOST=0.0.0.0
echo Starting account/chat backend on http://0.0.0.0:8098 ...
node index.mjs
pause

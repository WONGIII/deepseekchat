@echo off
setlocal
echo Stopping chat / backend / speech services on 8090, 8098, 8000, 8001, 8002...
powershell -NoProfile -ExecutionPolicy Bypass -Command "foreach($port in 8090,8098,8000,8001,8002){ Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | ForEach-Object { $p = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue; if($p -and ($p.ProcessName -eq 'node' -or $p.ProcessName -eq 'python' -or $p.ProcessName -eq 'pythonw')) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } } }"
echo Done.
pause

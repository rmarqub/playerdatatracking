@echo off
echo Deteniendo PlayerDataTracking...
taskkill /f /fi "WINDOWTITLE eq Backend*" >nul 2>&1
taskkill /f /fi "WINDOWTITLE eq Predict API*" >nul 2>&1
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080"') do taskkill /f /pid %%p >nul 2>&1
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8001"') do taskkill /f /pid %%p >nul 2>&1
echo Servicios detenidos.
pause

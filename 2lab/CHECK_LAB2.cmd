@echo off
REM Quick cycle: reinstall and security check without build.
setlocal
cd /d "%~dp0"
chcp 65001 >nul
title Lab 2 - Install and recheck without rebuilding
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\auto_run_windows.ps1" -NoBuild -SkipInstrumentedTests
set "RESULT=%ERRORLEVEL%"
echo.
echo Logs are in the logs folder. Press any key to close.
pause >nul
exit /b %RESULT%

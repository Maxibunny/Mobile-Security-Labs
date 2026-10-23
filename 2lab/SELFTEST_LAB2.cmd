@echo off
REM Self test for Windows process runner without Android.
setlocal
cd /d "%~dp0"
chcp 65001 >nul
title Lab 2 - verify Windows process runner
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\auto_run_windows.ps1" -SelfTest
set "RESULT=%ERRORLEVEL%"
echo.
if "%RESULT%"=="0" (echo [OK] Process runner works.) else (echo [ERROR] Process runner test failed. Send logs\auto-...\summary.txt)
echo Press any key to close.
pause >nul
exit /b %RESULT%

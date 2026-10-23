@echo off
REM Full cycle: Gradle, emulator, install, security tests and UI.
setlocal
cd /d "%~dp0"
chcp 65001 >nul
title Lab 2 - build, emulator, install, security checks
if not exist "%~dp0scripts\auto_run_windows.ps1" (
  echo ERROR: scripts\auto_run_windows.ps1 is missing.
  pause
  exit /b 2
)
echo ================================================
echo  LAB 2: AUTOMATIC ANDROID LAUNCH AND TEST
 echo ================================================
echo  Project: %~dp0
echo  Please wait; first build and Android boot may take several minutes.
echo  Logs will be saved in the logs folder.
echo ================================================
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\auto_run_windows.ps1" %*
set "RESULT=%ERRORLEVEL%"
echo.
if not "%RESULT%"=="0" (
    echo [ERROR] Some steps failed or could not be verified. Check logs\auto-...\summary.txt
) else (
    echo [OK] Automatic sequence finished. Check logs\auto-...\summary.txt
)
echo.
echo Press any key to close this window.
pause >nul
exit /b %RESULT%

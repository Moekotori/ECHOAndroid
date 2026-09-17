@echo off
cd /d "%~dp0.."
title ECHO hot reload
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0hot-reload.ps1"
set "STATUS=%ERRORLEVEL%"
echo.
if /I "%ECHO_NOPAUSE%"=="1" exit /b %STATUS%
pause
exit /b %STATUS%

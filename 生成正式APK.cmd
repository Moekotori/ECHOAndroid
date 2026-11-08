@echo off
call "%~dp0scripts\build-release.cmd" %*
set "echo_release_exit=%errorlevel%"
echo.
pause
exit /b %echo_release_exit%

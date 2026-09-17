@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Start the Windows Android emulator and launch Echo.
rem App builds run in WSL (FFmpeg/NDK require Linux).
rem
rem Usage:
rem   scripts\run-emulator.cmd
rem   scripts\run-emulator.cmd --avd ECHO_API_36
rem   scripts\run-emulator.cmd --build
rem   scripts\run-emulator.cmd --install
rem   scripts\run-emulator.cmd --launch-only
rem   scripts\run-emulator.cmd --cold

cd /d "%~dp0.."
set "ROOT=%CD%"
set "PACKAGE=app.echo.android"
set "ACTIVITY=app.echo.android/.MainActivity"
set "DEBUG_APK="
set "DEFAULT_AVD=ECHO_API_36"
set "WSL_DISTRO=Ubuntu-24.04"
if not defined BOOT_TIMEOUT_SEC set "BOOT_TIMEOUT_SEC=240"
if not defined ECHO_AVD set "ECHO_AVD="

set "AVD=%ECHO_AVD%"
set "DO_BUILD=0"
set "DO_INSTALL=0"
set "LAUNCH_ONLY=0"
set "COLD_BOOT=0"

:parse_args
if "%~1"=="" goto args_done
if /I "%~1"=="-h" goto show_help
if /I "%~1"=="--help" goto show_help
if /I "%~1"=="--avd" (
  if "%~2"=="" (
    echo error: --avd needs a name 1>&2
    exit /b 2
  )
  set "AVD=%~2"
  shift
  shift
  goto parse_args
)
if /I "%~1"=="--build" (
  set "DO_BUILD=1"
  shift
  goto parse_args
)
if /I "%~1"=="--install" (
  set "DO_INSTALL=1"
  shift
  goto parse_args
)
if /I "%~1"=="--launch-only" (
  set "LAUNCH_ONLY=1"
  shift
  goto parse_args
)
if /I "%~1"=="--cold" (
  set "COLD_BOOT=1"
  shift
  goto parse_args
)
echo error: unknown argument: %~1 1>&2
goto show_help

:show_help
echo Start the Android emulator and launch Echo.
echo.
echo Usage:
echo   scripts\run-emulator.cmd
echo   scripts\run-emulator.cmd --avd ECHO_API_36
echo   scripts\run-emulator.cmd --build
echo   scripts\run-emulator.cmd --install
echo   scripts\run-emulator.cmd --launch-only
echo   scripts\run-emulator.cmd --cold
exit /b 2

:args_done
if "%DO_BUILD%"=="1" if "%LAUNCH_ONLY%"=="1" (
  echo error: --build and --launch-only cannot be used together 1>&2
  exit /b 2
)
if "%DO_INSTALL%"=="1" if "%LAUNCH_ONLY%"=="1" (
  echo error: --install and --launch-only cannot be used together 1>&2
  exit /b 2
)

call :resolve_java || exit /b 1
call :resolve_sdk || exit /b 1
set "ADB=%SDK%\platform-tools\adb.exe"
set "EMULATOR=%SDK%\emulator\emulator.exe"
set "PATH=%SDK%\platform-tools;%SDK%\emulator;%JAVA_HOME%\bin;%PATH%"

"%ADB%" start-server >nul 2>&1

call :online_emulator_serial
set "SERIAL=%ONLINE_SERIAL%"
if defined SERIAL (
  echo Using already-running emulator: %SERIAL%
  call :place_window
  goto wait_boot
)

call :running_emulator_exists
if "!RUNNING_EMU!"=="1" (
  echo Emulator process is already coming up.
) else (
  call :pick_avd || exit /b 1
  call :start_emulator "!CHOSEN_AVD!" || exit /b 1
)

call :wait_for_serial || exit /b 1
set "SERIAL=%FOUND_SERIAL%"

:wait_boot
call :wait_for_boot "%SERIAL%" || exit /b 1
call :place_window
call :unlock_device "%SERIAL%"
call :ensure_app_installed "%SERIAL%" || exit /b 1
call :launch_app "%SERIAL%" || exit /b 1
echo Echo is running on %SERIAL%.
exit /b 0

:resolve_java
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto :eof
for /d %%D in ("C:\Program Files\Microsoft\jdk-21*") do (
  if exist "%%~D\bin\java.exe" (
    set "JAVA_HOME=%%~D"
    goto :eof
  )
)
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do (
  if exist "%%~D\bin\java.exe" (
    set "JAVA_HOME=%%~D"
    goto :eof
  )
)
echo error: JAVA_HOME not set and JDK 21 not found. 1>&2
exit /b 1

:resolve_sdk
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
  set "SDK=%ANDROID_HOME%"
  goto sdk_ok
)
if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" (
  set "SDK=%ANDROID_SDK_ROOT%"
  goto sdk_ok
)
if exist "E:\Android\Sdk\platform-tools\adb.exe" (
  set "SDK=E:\Android\Sdk"
  goto sdk_ok
)
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
  set "SDK=%LOCALAPPDATA%\Android\Sdk"
  goto sdk_ok
)
echo error: Android SDK not found. Set ANDROID_HOME. 1>&2
exit /b 1

:sdk_ok
if not exist "%SDK%\emulator\emulator.exe" (
  echo error: emulator not found at %SDK%\emulator\emulator.exe 1>&2
  exit /b 1
)
set "ANDROID_HOME=%SDK%"
set "ANDROID_SDK_ROOT=%SDK%"
exit /b 0

:list_avds
set "AVD_LIST="
set "FIRST_AVD="
for /f "usebackq delims=" %%A in (`"%EMULATOR%" -list-avds 2^>nul`) do (
  if defined AVD_LIST (
    set "AVD_LIST=!AVD_LIST! %%A"
  ) else (
    set "AVD_LIST=%%A"
  )
  if not defined FIRST_AVD set "FIRST_AVD=%%A"
)
exit /b 0

:pick_avd
call :list_avds
if not defined AVD_LIST (
  echo error: No AVDs found. 1>&2
  exit /b 1
)
if defined AVD (
  echo !AVD_LIST! | findstr /I /C:"%AVD%" >nul
  if errorlevel 1 (
    echo error: AVD '%AVD%' not found. Available: !AVD_LIST! 1>&2
    exit /b 1
  )
  set "CHOSEN_AVD=%AVD%"
  exit /b 0
)
echo !AVD_LIST! | findstr /I /C:"%DEFAULT_AVD%" >nul
if not errorlevel 1 (
  set "CHOSEN_AVD=%DEFAULT_AVD%"
  exit /b 0
)
set "CHOSEN_AVD=%FIRST_AVD%"
exit /b 0

:online_emulator_serial
set "ONLINE_SERIAL="
for /f "tokens=1,2" %%A in ('"%ADB%" devices 2^>nul') do (
  echo %%A | findstr /B "emulator-" >nul
  if not errorlevel 1 if /I "%%B"=="device" (
    set "ONLINE_SERIAL=%%A"
    goto :eof
  )
)
exit /b 0

:running_emulator_exists
set "RUNNING_EMU=0"
for /f "tokens=1,2" %%A in ('"%ADB%" devices 2^>nul') do (
  echo %%A | findstr /B "emulator-" >nul
  if not errorlevel 1 set "RUNNING_EMU=1"
)
exit /b 0

:sleep2
ping 127.0.0.1 -n 3 >nul
exit /b 0

:start_emulator
set "AVD_NAME=%~1"
set "EMU_EXTRA="
if "%COLD_BOOT%"=="1" set "EMU_EXTRA=-no-snapshot-load"
echo Starting emulator: %AVD_NAME%
rem Write bottom-right coordinates before launch so the first paint is not in the sky.
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\place-emulator-window.ps1" -AvdName "%AVD_NAME%" -IniOnly
start "ECHO Emulator" /D "%SDK%\emulator" "%SDK%\emulator\emulator.exe" -avd %AVD_NAME% -netdelay none -netspeed full -no-metrics %EMU_EXTRA%
exit /b 0

:place_window
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\place-emulator-window.ps1" -AvdName "%DEFAULT_AVD%"
exit /b 0

:wait_for_serial
set "FOUND_SERIAL="
set /a "ELAPSED=0"
:wait_serial_loop
call :online_emulator_serial
if defined ONLINE_SERIAL (
  set "FOUND_SERIAL=%ONLINE_SERIAL%"
  exit /b 0
)
if %ELAPSED% GEQ %BOOT_TIMEOUT_SEC% (
  echo error: No emulator appeared within %BOOT_TIMEOUT_SEC%s. 1>&2
  exit /b 1
)
call :sleep2
set /a "ELAPSED+=2"
goto wait_serial_loop

:wait_for_boot
set "BOOT_SERIAL=%~1"
echo Waiting for %BOOT_SERIAL% to finish booting ^(timeout %BOOT_TIMEOUT_SEC%s^)...
"%ADB%" -s "%BOOT_SERIAL%" wait-for-device
set /a "ELAPSED=0"
:wait_boot_loop
"%ADB%" -s "%BOOT_SERIAL%" shell getprop sys.boot_completed 2>nul | findstr /r /c:"^1" >nul
if not errorlevel 1 goto boot_ok
if %ELAPSED% GEQ %BOOT_TIMEOUT_SEC% (
  echo error: %BOOT_SERIAL% did not boot within %BOOT_TIMEOUT_SEC%s 1>&2
  exit /b 1
)
call :sleep2
set /a "ELAPSED+=2"
goto wait_boot_loop
:boot_ok
exit /b 0

:unlock_device
"%ADB%" -s "%~1" shell input keyevent KEYCODE_WAKEUP >nul 2>&1
"%ADB%" -s "%~1" shell wm dismiss-keyguard >nul 2>&1
exit /b 0

:resolve_debug_apk
set "DEBUG_APK="
for %%F in ("%ROOT%\app\build\outputs\apk\debug\ECHOAndroid-*-debug.apk") do (
  if exist "%%~fF" set "DEBUG_APK=%%~fF"
)
if not defined DEBUG_APK if exist "%ROOT%\app\build\outputs\apk\debug\app-debug.apk" (
  set "DEBUG_APK=%ROOT%\app\build\outputs\apk\debug\app-debug.apk"
)
exit /b 0

:package_installed
"%ADB%" -s "%~1" shell pm path "%PACKAGE%" >nul 2>&1
exit /b %ERRORLEVEL%

:install_apk
echo Installing %~nx2...
"%ADB%" -s "%~1" install -r -t -d "%~2"
exit /b %ERRORLEVEL%

:wsl_build_install
echo Building via WSL ^(FFmpeg needs Linux; first run can take 10-30 min^)...
set "ANDROID_SERIAL=%~1"
wsl -d %WSL_DISTRO% -- bash /mnt/e/ECHOandroid/scripts/wsl-build-install.sh
exit /b %ERRORLEVEL%

:ensure_app_installed
set "S=%~1"
call :resolve_debug_apk
if "%LAUNCH_ONLY%"=="1" (
  call :package_installed "%S%"
  if errorlevel 1 (
    echo error: %PACKAGE% is not installed. Re-run without --launch-only. 1>&2
    exit /b 1
  )
  exit /b 0
)
if "%DO_BUILD%"=="1" (
  call :wsl_build_install "%S%"
  exit /b %ERRORLEVEL%
)
if "%DO_INSTALL%"=="1" (
  if not exist "%DEBUG_APK%" (
    echo error: Debug APK missing. Use default or --build. 1>&2
    exit /b 1
  )
  call :install_apk "%S%" "%DEBUG_APK%"
  exit /b %ERRORLEVEL%
)
call :package_installed "%S%"
if not errorlevel 1 (
  echo Echo is already installed.
  exit /b 0
)
if exist "%DEBUG_APK%" (
  call :install_apk "%S%" "%DEBUG_APK%"
  if not errorlevel 1 exit /b 0
)
call :wsl_build_install "%S%"
exit /b %ERRORLEVEL%

:launch_app
echo Launching Echo...
"%ADB%" -s "%~1" shell am start -n "%ACTIVITY%" >nul
exit /b %ERRORLEVEL%

#!/usr/bin/env bash
# Start the Android emulator and launch Echo.
#
# Usage:
#   ./scripts/run-emulator.sh
#   ./scripts/run-emulator.sh --avd ECHO_LYRICS_AUDIT
#   ./scripts/run-emulator.sh --build
#   ./scripts/run-emulator.sh --install
#   ./scripts/run-emulator.sh --launch-only
#
# Env:
#   ECHO_AVD          Default AVD name (otherwise ECHO_API_36, then the first AVD)
#   ANDROID_HOME      SDK root (falls back to ANDROID_SDK_ROOT, then local.properties)
#   BOOT_TIMEOUT_SEC  Seconds to wait for boot (default 180)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PACKAGE="app.echo.android"
ACTIVITY="app.echo.android/.MainActivity"
DEBUG_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
DEFAULT_AVD="ECHO_API_36"
BOOT_TIMEOUT_SEC="${BOOT_TIMEOUT_SEC:-180}"

AVD="${ECHO_AVD:-}"
DO_BUILD=0
DO_INSTALL=0
LAUNCH_ONLY=0
COLD_BOOT=0

usage() {
  sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --avd)
      [[ $# -ge 2 ]] || { echo "error: --avd needs a name" >&2; exit 2; }
      AVD="$2"
      shift 2
      ;;
    --build) DO_BUILD=1; shift ;;
    --install) DO_INSTALL=1; shift ;;
    --launch-only) LAUNCH_ONLY=1; shift ;;
    --cold) COLD_BOOT=1; shift ;;
    *)
      echo "error: unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$DO_BUILD" -eq 1 && "$LAUNCH_ONLY" -eq 1 ]]; then
  echo "error: --build and --launch-only cannot be used together" >&2
  exit 2
fi
if [[ "$DO_INSTALL" -eq 1 && "$LAUNCH_ONLY" -eq 1 ]]; then
  echo "error: --install and --launch-only cannot be used together" >&2
  exit 2
fi

log() { printf '%s\n' "$*"; }
die() { echo "error: $*" >&2; exit 1; }

sdk_from_local_properties() {
  local file="$ROOT/local.properties"
  [[ -f "$file" ]] || return 1
  local value
  value="$(sed -n 's/^sdk.dir=//p' "$file" | tail -n 1 | tr -d '\r')"
  # Gradle local.properties escapes Windows paths as C\:\\...
  value="${value//\\:/:}"
  value="${value//\\\\/\\}"
  [[ -n "$value" ]] || return 1
  printf '%s\n' "$value"
}

resolve_sdk() {
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$sdk" ]]; then
    sdk="$(sdk_from_local_properties || true)"
  fi
  [[ -n "$sdk" ]] || die "Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties."
  [[ -x "$sdk/platform-tools/adb" ]] || die "adb not found at $sdk/platform-tools/adb"
  [[ -x "$sdk/emulator/emulator" ]] || die "emulator not found at $sdk/emulator/emulator"
  printf '%s\n' "$sdk"
}

SDK="$(resolve_sdk)"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
PATH="$SDK/platform-tools:$SDK/emulator:$PATH"

list_avds() {
  "$EMULATOR" -list-avds 2>/dev/null | tr -d '\r' | awk 'NF'
}

pick_avd() {
  local available
  available="$(list_avds)"
  [[ -n "$available" ]] || die "No AVDs found. Create one in Android Studio (Device Manager)."

  if [[ -n "$AVD" ]]; then
    if printf '%s\n' "$available" | grep -Fxq "$AVD"; then
      printf '%s\n' "$AVD"
      return
    fi
    die "AVD '$AVD' not found. Available: $(printf '%s' "$available" | paste -sd, -)"
  fi

  if printf '%s\n' "$available" | grep -Fxq "$DEFAULT_AVD"; then
    printf '%s\n' "$DEFAULT_AVD"
    return
  fi

  printf '%s\n' "$available" | head -n 1
}

running_emulator_serials() {
  "$ADB" devices | awk '/^emulator-/{print $1}'
}

online_emulator_serial() {
  "$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'
}

wait_for_boot() {
  local serial="$1"
  local deadline=$((SECONDS + BOOT_TIMEOUT_SEC))
  local boot=""
  local bootanim=""

  log "Waiting for $serial to finish booting (timeout ${BOOT_TIMEOUT_SEC}s)..."
  "$ADB" -s "$serial" wait-for-device

  while (( SECONDS < deadline )); do
    boot="$("$ADB" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    bootanim="$("$ADB" -s "$serial" shell getprop init.svc.bootanim 2>/dev/null | tr -d '\r')"
    if [[ "$boot" == "1" && ( -z "$bootanim" || "$bootanim" == "stopped" ) ]]; then
      return 0
    fi
    sleep 2
  done

  die "$serial did not boot within ${BOOT_TIMEOUT_SEC}s"
}

unlock_device() {
  local serial="$1"
  "$ADB" -s "$serial" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  "$ADB" -s "$serial" shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

package_installed() {
  local serial="$1"
  "$ADB" -s "$serial" shell pm path "$PACKAGE" >/dev/null 2>&1
}

install_apk() {
  local serial="$1"
  local apk="$2"
  log "Installing $(basename "$apk")..."
  "$ADB" -s "$serial" install -r -t -d "$apk"
}

gradlew_install_debug() {
  local serial="$1"
  log "Building and installing debug via Gradle..."
  ANDROID_SERIAL="$serial" ./gradlew :app:installDebug --quiet
}

ensure_app_installed() {
  local serial="$1"

  if [[ "$LAUNCH_ONLY" -eq 1 ]]; then
    package_installed "$serial" || die "$PACKAGE is not installed. Re-run without --launch-only."
    return
  fi

  if [[ "$DO_BUILD" -eq 1 ]]; then
    gradlew_install_debug "$serial"
    return
  fi

  if [[ "$DO_INSTALL" -eq 1 ]]; then
    [[ -f "$DEBUG_APK" ]] || die "Debug APK missing at $DEBUG_APK. Use --build."
    install_apk "$serial" "$DEBUG_APK"
    return
  fi

  if package_installed "$serial"; then
    log "Echo is already installed."
    return
  fi

  if [[ -f "$DEBUG_APK" ]]; then
    install_apk "$serial" "$DEBUG_APK"
    return
  fi

  gradlew_install_debug "$serial"
}

launch_app() {
  local serial="$1"
  log "Launching Echo..."
  "$ADB" -s "$serial" shell am start -n "$ACTIVITY" >/dev/null
}

start_emulator() {
  local avd="$1"
  # Keep this array non-empty: macOS /bin/bash 3.2 errors on "${arr[@]}" with set -u.
  local emu_args
  emu_args=(-avd "$avd" -netdelay none -netspeed full)
  if [[ "$COLD_BOOT" -eq 1 ]]; then
    emu_args+=(-no-snapshot-load)
  fi
  log "Starting emulator: $avd"
  # Run from the emulator directory so QEMU finds its bundled libs.
  (
    cd "$SDK/emulator"
    nohup ./emulator "${emu_args[@]}" >/tmp/echo-emulator.log 2>&1 &
  )
}

wait_for_serial() {
  local deadline=$((SECONDS + BOOT_TIMEOUT_SEC))
  local serial=""
  while (( SECONDS < deadline )); do
    serial="$(online_emulator_serial || true)"
    if [[ -n "$serial" ]]; then
      printf '%s\n' "$serial"
      return
    fi
    # An emulator may show as "offline" while it is coming up.
    if [[ -n "$(running_emulator_serials || true)" ]]; then
      sleep 2
      continue
    fi
    sleep 2
  done
  die "No emulator appeared within ${BOOT_TIMEOUT_SEC}s. See /tmp/echo-emulator.log"
}

"$ADB" start-server >/dev/null

SERIAL="$(online_emulator_serial || true)"
if [[ -n "$SERIAL" ]]; then
  log "Using already-running emulator: $SERIAL"
else
  if [[ -n "$(running_emulator_serials || true)" ]]; then
    log "Emulator process is already coming up."
  else
    CHOSEN_AVD="$(pick_avd)"
    start_emulator "$CHOSEN_AVD"
  fi
  SERIAL="$(wait_for_serial)"
fi

wait_for_boot "$SERIAL"
unlock_device "$SERIAL"
ensure_app_installed "$SERIAL"
launch_app "$SERIAL"
log "Echo is running on $SERIAL."

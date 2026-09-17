#!/usr/bin/env bash
# Incremental assemble + install for the Windows emulator. Used by hot-reload watch.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PACKAGE="app.echo.android"
ACTIVITY="app.echo.android/.MainActivity"
WIN_SDK="${ECHO_WIN_SDK:-/mnt/e/Android/Sdk}"
LINUX_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk-21}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$LINUX_SDK"
export ANDROID_SDK_ROOT="$LINUX_SDK"

log() { printf '%s\n' "$*"; }
die() { echo "error: $*" >&2; exit 1; }

latest_debug_apk() {
  local dir="$ROOT/app/build/outputs/apk/debug"
  [[ -d "$dir" ]] || return 1
  local files=() newest=""
  shopt -s nullglob
  files=("$dir"/ECHOAndroid-*-debug.apk "$dir"/app-debug.apk)
  shopt -u nullglob
  ((${#files[@]})) || return 1
  newest="${files[0]}"
  local f
  for f in "${files[@]}"; do
    [[ "$f" -nt "$newest" ]] && newest="$f"
  done
  printf '%s\n' "$newest"
}

[[ -x "$JAVA_HOME/bin/java" ]] || die "JDK not found at $JAVA_HOME"
[[ -d "$LINUX_SDK/platforms/android-36" ]] || die "Linux SDK missing. Run scripts/wsl-build-install.sh once first."

if [[ -f "$ROOT/local.properties" ]]; then
  cp "$ROOT/local.properties" "$ROOT/local.properties.winbak"
fi
printf 'sdk.dir=%s\n' "$LINUX_SDK" > "$ROOT/local.properties"
restore_local_properties() {
  if [[ -f "$ROOT/local.properties.winbak" ]]; then
    mv -f "$ROOT/local.properties.winbak" "$ROOT/local.properties"
  else
    printf '%s\n' 'sdk.dir=E\:\\Android\\Sdk' > "$ROOT/local.properties"
  fi
}
trap restore_local_properties EXIT

win_adb() {
  local adb="$WIN_SDK/platform-tools/adb.exe"
  [[ -f "$adb" ]] || die "Windows adb not found at $adb"
  "$adb" "$@"
}

win_path() {
  wslpath -w "$1"
}

SERIAL="${ANDROID_SERIAL:-}"
if [[ -z "$SERIAL" ]]; then
  SERIAL="$(win_adb devices | tr -d '\r' | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"
fi
[[ -n "$SERIAL" ]] || die "No online emulator."

log "Incremental assembleDebug..."
./gradlew :app:assembleDebug --quiet

APK="$(latest_debug_apk || true)"
[[ -n "$APK" && -f "$APK" ]] || die "Debug APK missing after assemble"

log "Installing $(basename "$APK") onto $SERIAL..."
win_adb -s "$SERIAL" install -r -t -d "$(win_path "$APK")"
win_adb -s "$SERIAL" shell am start -n "$ACTIVITY" >/dev/null
log "Hot reload applied on $SERIAL."

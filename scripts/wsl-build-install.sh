#!/usr/bin/env bash
# Build Echo inside WSL (FFmpeg/NDK need Linux) and install onto a Windows emulator.
#
# Env:
#   ANDROID_HOME / ANDROID_SDK_ROOT  Linux-side SDK (default: ~/Android/Sdk)
#   ECHO_WIN_SDK                    Windows SDK path in WSL (default: /mnt/e/Android/Sdk)
#   ANDROID_SERIAL                  Target device (optional)
#   JAVA_HOME                       Default: ~/jdk-21

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PACKAGE="app.echo.android"
ACTIVITY="app.echo.android/.MainActivity"
DEBUG_APK=""
if [[ -d "$ROOT/app/build/outputs/apk/debug" ]]; then
  DEBUG_APK="$(ls -1t "$ROOT/app/build/outputs/apk/debug"/ECHOAndroid-*-debug.apk "$ROOT/app/build/outputs/apk/debug"/app-debug.apk 2>/dev/null | head -n 1 || true)"
fi
WIN_SDK="${ECHO_WIN_SDK:-/mnt/e/Android/Sdk}"
LINUX_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk-21}"
export PATH="$JAVA_HOME/bin:$PATH"

log() { printf '%s\n' "$*"; }
die() { echo "error: $*" >&2; exit 1; }

unzip_to() {
  local zip="$1"
  local dest="$2"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$zip" -d "$dest"
  else
    python3 - "$zip" "$dest" <<'PY'
import sys, zipfile
from pathlib import Path
zip_path, dest = Path(sys.argv[1]), Path(sys.argv[2])
dest.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(zip_path) as zf:
    zf.extractall(dest)
PY
  fi
}

ensure_java() {
  [[ -x "$JAVA_HOME/bin/java" ]] || die "JDK not found at $JAVA_HOME. Run scripts/wsl-bootstrap-jdk.sh first."
  java -version
}

ensure_linux_sdk() {
  export ANDROID_HOME="$LINUX_SDK"
  export ANDROID_SDK_ROOT="$LINUX_SDK"
  mkdir -p "$LINUX_SDK/cmdline-tools"

  if [[ ! -x "$LINUX_SDK/cmdline-tools/latest/bin/sdkmanager" ]]; then
    log "Installing Android cmdline-tools into $LINUX_SDK ..."
    local zip="/tmp/commandlinetools-linux.zip"
    curl -fL --retry 3 -o "$zip" \
      "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
    rm -rf /tmp/cmdline-tools-extract
    mkdir -p /tmp/cmdline-tools-extract
    unzip_to "$zip" /tmp/cmdline-tools-extract
    rm -rf "$LINUX_SDK/cmdline-tools/latest"
    mv /tmp/cmdline-tools-extract/cmdline-tools "$LINUX_SDK/cmdline-tools/latest"
  fi

  local sdkmanager="$LINUX_SDK/cmdline-tools/latest/bin/sdkmanager"
  chmod +x "$LINUX_SDK/cmdline-tools/latest/bin/"* 2>/dev/null || true
  # Accept licenses without leaving a broken stdin for later steps.
  set +o pipefail
  yes 2>/dev/null | bash "$sdkmanager" --sdk_root="$LINUX_SDK" --licenses >/dev/null 2>&1 || true
  set -o pipefail

  local need_install=0
  [[ -d "$LINUX_SDK/ndk/27.2.12479018" ]] || need_install=1
  [[ -d "$LINUX_SDK/platforms/android-36" ]] || need_install=1
  [[ -d "$LINUX_SDK/build-tools/36.0.0" ]] || need_install=1
  [[ -d "$LINUX_SDK/cmake/3.22.1" ]] || need_install=1
  if [[ "$need_install" -eq 1 ]]; then
    log "Ensuring Linux NDK / build-tools (first run downloads a lot)..."
    bash "$sdkmanager" --sdk_root="$LINUX_SDK" \
      "platform-tools" \
      "platforms;android-36" \
      "build-tools;36.0.0" \
      "ndk;27.2.12479018" \
      "cmake;3.22.1" </dev/null
  else
    log "Linux SDK packages already present."
  fi

  # Point Gradle at the Linux SDK for this WSL build; restore Windows path after.
  if [[ -f "$ROOT/local.properties" ]]; then
    cp "$ROOT/local.properties" "$ROOT/local.properties.winbak"
  fi
  printf 'sdk.dir=%s\n' "$LINUX_SDK" > "$ROOT/local.properties"
}

restore_local_properties() {
  if [[ -f "$ROOT/local.properties.winbak" ]]; then
    mv -f "$ROOT/local.properties.winbak" "$ROOT/local.properties"
  else
    printf '%s\n' 'sdk.dir=E\:\\Android\\Sdk' > "$ROOT/local.properties"
  fi
}

win_adb() {
  local adb="$WIN_SDK/platform-tools/adb.exe"
  [[ -f "$adb" ]] || die "Windows adb not found at $adb"
  "$adb" "$@"
}

online_serial() {
  win_adb devices | tr -d '\r' | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'
}

ensure_java
ensure_linux_sdk
trap restore_local_properties EXIT

SERIAL="${ANDROID_SERIAL:-}"
if [[ -z "$SERIAL" ]]; then
  SERIAL="$(online_serial || true)"
fi
[[ -n "$SERIAL" ]] || die "No online emulator. Start the Windows emulator first."

log "Building debug APK in WSL (first run builds FFmpeg; can take 10-30 min)..."
./gradlew :app:assembleDebug --no-daemon

DEBUG_APK="$(ls -1t "$ROOT/app/build/outputs/apk/debug"/ECHOAndroid-*-debug.apk "$ROOT/app/build/outputs/apk/debug"/app-debug.apk 2>/dev/null | head -n 1 || true)"
[[ -n "$DEBUG_APK" && -f "$DEBUG_APK" ]] || die "APK missing after build in app/build/outputs/apk/debug"

log "Installing $(basename "$DEBUG_APK") onto $SERIAL ..."
win_adb -s "$SERIAL" install -r -t -d "$(wslpath -w "$DEBUG_APK")"
log "Launching Echo..."
win_adb -s "$SERIAL" shell am start -n "$ACTIVITY" >/dev/null
log "Echo is running on $SERIAL."

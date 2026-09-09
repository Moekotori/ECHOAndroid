#!/usr/bin/env bash
# Build a double-clickable macOS app that starts the emulator and Echo.
# Installs to ~/Applications and ~/Desktop.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="打开 Echo"
APP_ID="app.echo.android.launcher"
ICON_SRC="$ROOT/scripts/assets/echo-app-icon.png"
DESTS=(
  "$HOME/Applications/${APP_NAME}.app"
  "$HOME/Desktop/${APP_NAME}.app"
)

[[ -x "$ROOT/scripts/run-emulator.sh" ]] || {
  echo "error: missing $ROOT/scripts/run-emulator.sh" >&2
  exit 1
}
[[ -f "$ICON_SRC" ]] || {
  echo "error: missing $ICON_SRC" >&2
  exit 1
}

TMP="$(mktemp -d "${TMPDIR:-/tmp}/echo-launcher.XXXXXX")"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

ICONSET="$TMP/AppIcon.iconset"
mkdir -p "$ICONSET"
for spec in \
  "icon_16x16.png:16" \
  "icon_16x16@2x.png:32" \
  "icon_32x32.png:32" \
  "icon_32x32@2x.png:64" \
  "icon_128x128.png:128" \
  "icon_128x128@2x.png:256" \
  "icon_256x256.png:256" \
  "icon_256x256@2x.png:512" \
  "icon_512x512.png:512" \
  "icon_512x512@2x.png:1024"
do
  name="${spec%%:*}"
  size="${spec##*:}"
  sips -z "$size" "$size" "$ICON_SRC" --out "$ICONSET/$name" >/dev/null
done
iconutil -c icns "$ICONSET" -o "$TMP/AppIcon.icns"

write_app() {
  local app="$1"
  rm -rf "$app"
  mkdir -p "$app/Contents/MacOS" "$app/Contents/Resources"

  cat > "$app/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key>
  <string>zh_CN</string>
  <key>CFBundleDisplayName</key>
  <string>${APP_NAME}</string>
  <key>CFBundleExecutable</key>
  <string>run</string>
  <key>CFBundleIconFile</key>
  <string>AppIcon</string>
  <key>CFBundleIdentifier</key>
  <string>${APP_ID}</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>${APP_NAME}</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0</string>
  <key>CFBundleVersion</key>
  <string>1</string>
  <key>LSMinimumSystemVersion</key>
  <string>13.0</string>
  <key>LSUIElement</key>
  <true/>
  <key>NSHighResolutionCapable</key>
  <true/>
</dict>
</plist>
EOF

  cp "$TMP/AppIcon.icns" "$app/Contents/Resources/AppIcon.icns"
  printf '%s\n' "$ROOT" > "$app/Contents/Resources/repo-path"

  cat > "$app/Contents/MacOS/run" <<'EOF'
#!/bin/bash
set -euo pipefail
APP="$(cd "$(dirname "$0")/../.." && pwd)"
LOG="/tmp/echo-android-launch.log"
notify() {
  /usr/bin/osascript -e "display notification \"$2\" with title \"打开 Echo\" subtitle \"$1\"" >/dev/null 2>&1 || true
}
alert() {
  /usr/bin/osascript -e "display alert \"打开 Echo 失败\" message \"$1\"" >/dev/null 2>&1 || true
}

REPO=""
if [[ -f "$APP/Contents/Resources/repo-path" ]]; then
  REPO="$(/usr/bin/head -n 1 "$APP/Contents/Resources/repo-path" | tr -d '\r')"
fi
if [[ -z "$REPO" || ! -x "$REPO/scripts/run-emulator.sh" ]]; then
  d="$APP"
  i=0
  while [[ "$i" -lt 6 ]]; do
    d="$(/usr/bin/dirname "$d")"
    if [[ -x "$d/scripts/run-emulator.sh" ]]; then
      REPO="$d"
      break
    fi
    i=$((i + 1))
  done
fi
if [[ -z "$REPO" || ! -x "$REPO/scripts/run-emulator.sh" ]]; then
  alert "找不到项目目录。请在仓库里重新运行 scripts/install-macos-launcher.sh"
  exit 1
fi

notify "正在启动" "模拟器和 Echo…"
set +e
"$REPO/scripts/run-emulator.sh" >"$LOG" 2>&1
status=$?
set -e
if [[ "$status" -ne 0 ]]; then
  alert "启动失败，退出码 ${status}。详情见 ${LOG}"
  exit "$status"
fi
notify "已打开" "Echo 正在模拟器中运行"
EOF
  chmod +x "$app/Contents/MacOS/run"
  /usr/bin/xattr -cr "$app" 2>/dev/null || true
}

mkdir -p "$HOME/Applications"
for dest in "${DESTS[@]}"; do
  write_app "$dest"
  echo "Installed $dest"
done
echo "Double-click “${APP_NAME}” on the Desktop, or search it in Spotlight."

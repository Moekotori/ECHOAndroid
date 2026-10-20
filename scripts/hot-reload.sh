#!/usr/bin/env bash
# macOS/Linux: launch Echo and automatically redeploy saved source changes.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Finder's environment often has neither JAVA_HOME nor the Android SDK configured.
if [[ -z "${JAVA_HOME:-}" && -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" && ! -f "$ROOT/local.properties" ]]; then
  if [[ -d "$HOME/Library/Android/sdk" ]]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
  elif [[ -d "$HOME/Android/Sdk" ]]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
  fi
fi
command -v python3 >/dev/null || { echo "需要 Python 3，请先安装。" >&2; exit 1; }
exec python3 "$ROOT/scripts/watch-hot-reload.py" "$@"

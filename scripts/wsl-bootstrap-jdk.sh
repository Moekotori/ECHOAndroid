#!/usr/bin/env bash
set -euo pipefail

if [[ ! -x "$HOME/jdk-21/bin/java" ]]; then
  echo "Downloading JDK 21 into $HOME/jdk-21 ..."
  mkdir -p /tmp
  curl -fL --retry 3 -o /tmp/jdk21.tar.gz \
    "https://aka.ms/download-jdk/microsoft-jdk-21.0.12-linux-x64.tar.gz"
  rm -rf "$HOME/jdk-21-extract"
  mkdir -p "$HOME/jdk-21-extract"
  tar -xzf /tmp/jdk21.tar.gz -C "$HOME/jdk-21-extract"
  DIR="$(find "$HOME/jdk-21-extract" -maxdepth 1 -type d -name 'jdk-21*' | head -1)"
  rm -rf "$HOME/jdk-21"
  mv "$DIR" "$HOME/jdk-21"
fi

"$HOME/jdk-21/bin/java" -version
command -v curl
command -v python3
command -v make
command -v unzip || echo "NO_UNZIP"

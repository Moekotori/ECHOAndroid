#!/usr/bin/env bash
# Windows Gradle delegates only FFmpeg compilation to WSL; JNI/APK stay on Windows.
set -euo pipefail

die() { echo "FFmpeg WSL: $*" >&2; exit 1; }

[[ $# -eq 3 ]] || die "Expected build script, NDK version and Windows work directory."
command -v wslpath >/dev/null || die "Run this launcher inside WSL."
for tool in python3 make flock sha256sum; do
  command -v "$tool" >/dev/null || die "Install $tool in this WSL distribution."
done

script="$(wslpath -u "$1")"
ndk_version="$2"
work="$(wslpath -u "$3")"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ndk="$sdk/ndk/$ndk_version"
[[ -f "$script" ]] || die "Build script not found: $script"
[[ -x "$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]] ||
  die "Install Linux NDK $ndk_version in $sdk (or set ANDROID_HOME inside WSL)."

# Compile on the Linux filesystem: configure/make on /mnt/c or /mnt/d is very slow.
# Keep caches separate for different checkouts, and serialize access to each cache.
cache_key="$(printf '%s' "$script" | sha256sum | cut -c1-16)"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/echo-android/ffmpeg/$cache_key"
mkdir -p "$cache/downloads"
exec 9>"$cache/build.lock"
flock 9

# Reuse an existing download; the Python builder still verifies its pinned checksum.
if [[ -d "$work/downloads" ]]; then
  for archive in "$work"/downloads/ffmpeg-*.tar.xz; do
    [[ -f "$archive" ]] || continue
    if [[ ! -f "$cache/downloads/$(basename "$archive")" ]]; then
      cp "$archive" "$cache/downloads/"
    fi
  done
fi

echo "Building FFmpeg through WSL (Linux cache: $cache)"
python3 "$script" --ndk "$ndk" --work "$cache"

# Never publish a successful stamp until all libraries and headers have been copied.
mkdir -p "$work/native"
rm -f "$work/native/build-id"
for abi in armeabi-v7a arm64-v8a x86_64; do
  cp -R "$cache/native/$abi" "$work/native/"
done
cp "$cache/native/build-id" "$work/native/build-id"
echo "FFmpeg libraries ready for Windows CMake."

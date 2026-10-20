#!/usr/bin/env python3
"""Bounded polling watcher for the native Android development build (no dependencies)."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
IGNORED = {"build", ".gradle", ".cxx", ".kotlin", ".git", ".idea", "__pycache__"}


def snapshot():
    """Watch production sources and build inputs, including additions and deletions."""
    result = {}
    for base in (ROOT / "app", ROOT / "core", ROOT / "feature", ROOT / "gradle"):
        for directory, dirs, files in os.walk(base):
            dirs[:] = [d for d in dirs if d not in IGNORED and d not in {"test", "androidTest", "third_party"}]
            for name in files:
                path = Path(directory) / name
                try:
                    stat = path.stat()
                    result[str(path)] = (stat.st_mtime_ns, stat.st_size)
                except FileNotFoundError:
                    pass  # Editors may atomically replace a file while we scan.
    for name in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "local.properties", "scripts/build-ffmpeg.py"):
        path = ROOT / name
        if path.is_file():
            stat = path.stat()
            result[str(path)] = (stat.st_mtime_ns, stat.st_size)
    return result


def run(*args, capture=False):
    return subprocess.run(args, cwd=ROOT, check=True, text=True,
                          stdout=subprocess.PIPE if capture else None).stdout


def sdk_path():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        local = ROOT / "local.properties"
        if local.exists():
            for line in local.read_text().splitlines():
                if line.startswith("sdk.dir="):
                    sdk = line.split("=", 1)[1].replace(r"\:", ":").replace(r"\\", "\\")
    if not sdk:
        raise RuntimeError("找不到 Android SDK，请设置 ANDROID_HOME。")
    return Path(sdk)


def reload_app(adb, serial):
    run(str(ROOT / "gradlew"), ":app:assembleDebug", "--console=plain")
    output = ROOT / "app/build/outputs/apk/debug"
    metadata = json.loads((output / "output-metadata.json").read_text())
    elements = metadata["elements"]
    if len(elements) != 1:
        raise RuntimeError("当前脚本需要单个 Debug APK，不支持 split APK。")
    apk = output / elements[0]["outputFile"]
    # Pin all deployments to the emulator selected at startup. Never uninstall data.
    run(str(adb), "-s", serial, "install", "-r", "-t", str(apk))
    run(str(adb), "-s", serial, "shell", "am", "force-stop", "app.echo.android")
    run(str(adb), "-s", serial, "shell", "am", "start", "-W", "-n", "app.echo.android/.MainActivity")


def main():
    parser = argparse.ArgumentParser(description="启动 ECHO 模拟器；保存后自动增量构建、覆盖安装并重启（保留应用数据，播放会中断）。")
    parser.add_argument("--avd", help="指定模拟器名称；默认复用已启动的模拟器")
    parser.add_argument("--once", action="store_true", help="只构建、安装并启动一次，不监听")
    args = parser.parse_args()
    # Held for the whole session; repeated double-clicks cannot start parallel builds.
    lock_dir = ROOT / ".gradle"
    lock_dir.mkdir(exist_ok=True)
    with (lock_dir / "echo-hot-reload.lock").open("w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print("ECHO 自动刷新已在运行，请使用原来的终端窗口。", flush=True)
            return
        baseline = snapshot()
        command = ["bash", str(ROOT / "scripts/run-emulator.sh"), "--build"]
        if args.avd:
            command += ["--avd", args.avd]
        run(*command)
        if args.once:
            return
        adb = sdk_path() / "platform-tools/adb"
        devices = run(str(adb), "devices", capture=True)
        serial = next((parts[0] for line in devices.splitlines()
                       if len(parts := line.split()) >= 2
                       and parts[0].startswith("emulator-") and parts[1] == "device"), None)
        if not serial:
            raise RuntimeError("未找到在线模拟器。")
        print(f"\n自动刷新已开启：{serial}。保存代码后自动更新，Ctrl+C 停止。\n应用会重启，播放会中断；应用数据保留。", flush=True)
        pending = False
        due = 0.0
        while True:
            current = snapshot()
            if current != baseline:
                baseline = current
                pending = True
                due = time.monotonic() + 1.5
            if pending and time.monotonic() >= due:
                pending = False
                print("\n检测到改动，正在更新 ECHO…", flush=True)
                try:
                    reload_app(adb, serial)
                    print("ECHO 已更新。", flush=True)
                except (subprocess.CalledProcessError, OSError, ValueError, KeyError, RuntimeError) as error:
                    print(f"更新失败：{error}\n修复后再次保存即可重试。", flush=True)
                # Keep the pre-build snapshot: saves made during a build queue another update.
            time.sleep(1)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n已停止自动刷新。", flush=True)
    except (subprocess.CalledProcessError, OSError, ValueError, KeyError, RuntimeError) as error:
        print(f"启动失败：{error}", flush=True)
        raise SystemExit(1)

#!/bin/bash
# Double-click in Finder to start the emulator and launch Echo.
cd "$(dirname "$0")/.." || exit 1
./scripts/run-emulator.sh
status=$?
echo
if [[ "$status" -eq 0 ]]; then
  echo "Echo 已打开。可以关掉这个窗口。"
else
  echo "启动失败（退出码 $status）。"
fi
exit "$status"

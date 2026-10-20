#!/bin/bash
# Double-click in Finder to launch Echo with automatic development updates.
cd "$(dirname "$0")/.." || exit 1
./scripts/hot-reload.sh "$@"
status=$?
if [[ "$status" -ne 0 ]]; then
  echo "启动失败（退出码 $status）。请查看上面的错误。"
  read -r -p "按回车关闭…" _
fi
exit "$status"

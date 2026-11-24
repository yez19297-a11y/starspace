#!/data/data/com.termux/files/usr/bin/bash
# 启动跑在 Debian 容器里的星屿后端。
# 用法：bash scripts/termux-proot-start.sh
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ ! -f "$ROOT/server/.env" ]; then
  echo "缺少 server/.env，请先运行："
  echo "  bash $ROOT/scripts/termux-proot-setup.sh"
  exit 1
fi

if [ ! -x "$ROOT/server/.venv/bin/python" ]; then
  echo "依赖还没装好，请先运行："
  echo "  bash $ROOT/scripts/termux-proot-setup.sh"
  exit 1
fi

# 持有唤醒锁，避免锁屏后进程被挂起
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true

echo "星屿后端启动中（Debian 容器），监听 0.0.0.0:8000"
echo "同一台手机上，App 里的后端地址填：http://127.0.0.1:8000"
echo "局域网其他设备填：http://<手机IP>:8000"
echo

exec proot-distro login debian --bind "$HOME:$HOME" -- bash -lc \
  "cd '$ROOT/server' && exec '$ROOT/server/.venv/bin/python' -m uvicorn app.main:app --host 0.0.0.0 --port 8000"

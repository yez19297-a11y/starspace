#!/data/data/com.termux/files/usr/bin/bash
# 启动星屿后端，并尽量阻止 Android 休眠 Termux。
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/server"

if [ ! -f .env ]; then
  echo "缺少 server/.env，请先运行 bash scripts/termux-setup.sh"
  exit 1
fi

# 持有唤醒锁，避免锁屏后进程被挂起（需要 Termux 在前台通知里有常驻通知）
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true

echo "星屿后端启动中，监听 0.0.0.0:8000"
echo "同一台手机上，App 里的后端地址填：http://127.0.0.1:8000"
echo "局域网其他设备填：http://<手机IP>:8000"
echo

exec uvicorn app.main:app --host 0.0.0.0 --port 8000

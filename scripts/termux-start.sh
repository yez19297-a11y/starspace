#!/data/data/com.termux/files/usr/bin/bash
# 启动星屿后端，并尽量阻止 Android 休眠 Termux。
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/server"

if [ ! -f .env ]; then
  echo "缺少 server/.env，请先运行 bash scripts/termux-setup.sh"
  exit 1
fi

# uvicorn 没装上时给出可执行的下一步，而不是只报 "not found"
if ! command -v uvicorn >/dev/null 2>&1; then
  echo "找不到 uvicorn，依赖还没装完。"
  echo
  echo "Termux 原生环境编译 pydantic-core 很容易失败，建议直接改用 Debian 容器："
  echo "  bash $ROOT/scripts/termux-proot-setup.sh"
  echo "  bash $ROOT/scripts/termux-proot-start.sh"
  echo
  echo "如果坚持用原生环境，先补工具链再重试："
  echo "  pkg install -y rust maturin binutils clang make"
  echo "  python -m pip install --no-cache-dir --no-build-isolation -r $ROOT/server/requirements-termux.txt"
  exit 1
fi

# 持有唤醒锁，避免锁屏后进程被挂起（需要 Termux 在前台通知里有常驻通知）
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true

echo "星屿后端启动中，监听 0.0.0.0:8000"
echo "同一台手机上，App 里的后端地址填：http://127.0.0.1:8000"
echo "局域网其他设备填：http://<手机IP>:8000"
echo

exec uvicorn app.main:app --host 0.0.0.0 --port 8000

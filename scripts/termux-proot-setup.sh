#!/data/data/com.termux/files/usr/bin/bash
# 在 Termux 里用 proot-distro 跑星屿后端。
#
# 为什么不用 Termux 自带的 Python：
#   Termux 当前是 Python 3.14，pydantic v2 的核心 pydantic-core 是 Rust 写的，
#   官方没有 Android 预编译包，只能现场编译（要 10-20 分钟，还容易内存不足）。
#   Debian 里的 Python 3.11 有现成的 aarch64 wheel，装依赖只要一两分钟。
#
# 用法：bash scripts/termux-proot-setup.sh
set -e

if [ ! -d "/data/data/com.termux" ]; then
  echo "这个脚本要在 Termux 里运行。"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> 安装 proot-distro"
pkg update -y || echo "包索引更新失败，继续使用现有索引"
pkg install -y proot-distro

if proot-distro list --installed 2>/dev/null | grep -qi debian; then
  echo "==> 检测到已安装的 Debian，跳过下载"
else
  echo "==> 安装 Debian 容器（首次约 70MB，保持网络畅通）"
  proot-distro install debian
fi

echo
echo "==> 在 Debian 里安装 Python 依赖（直接用预编译 wheel，不编译 Rust）"
# --bind 把 Termux 的家目录按原路径挂进容器，server/.env 和数据库不用搬家
proot-distro login debian --bind "$HOME:$HOME" -- bash -lc "
set -e
export DEBIAN_FRONTEND=noninteractive
apt-get update -y || {
  echo '--- 官方源连不上，切换到清华镜像 ---'
  sed -i 's|deb.debian.org|mirrors.tuna.tsinghua.edu.cn|g; s|security.debian.org|mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list 2>/dev/null || true
  apt-get update -y
}
apt-get install -y --no-install-recommends python3 python3-venv python3-pip ca-certificates
cd '$ROOT/server'
rm -rf .venv
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
echo '--- 安装依赖 ---'
.venv/bin/pip install --no-cache-dir -r requirements-termux.txt || {
  echo '--- 官方源太慢，改用清华镜像重试 ---'
  .venv/bin/pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements-termux.txt
}
echo '--- 校验 ---'
.venv/bin/python -c 'import fastapi, pydantic, uvicorn; print(\"fastapi\", fastapi.__version__, \"/ pydantic\", pydantic.VERSION, \"/ uvicorn\", uvicorn.__version__)'
"

if [ ! -f server/.env ]; then
  cp server/.env.example server/.env
  echo
  echo "已生成 server/.env，请填入真实密钥："
  echo "  nano $ROOT/server/.env"
fi

echo
echo "安装完成。启动："
echo "  bash $ROOT/scripts/termux-proot-start.sh"

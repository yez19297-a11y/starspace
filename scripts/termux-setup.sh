#!/data/data/com.termux/files/usr/bin/bash
# 在 Termux 里安装星屿后端。用法：bash scripts/termux-setup.sh
set -e

if [ ! -d "/data/data/com.termux" ]; then
  echo "这个脚本要在 Termux 里运行。"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/server"

echo "==> 更新包索引并安装 Python"
pkg update -y
pkg install -y python

echo "==> 升级 pip"
python -m pip install --upgrade pip

echo "==> 安装依赖"
# pydantic v2 的核心是 Rust 写的。Termux 通常拿不到预编译 wheel，
# 先直接装，失败再装 Rust 从源码编译（大约 10-20 分钟）。
if ! python -m pip install -r requirements-termux.txt; then
  echo "==> 需要从源码编译 pydantic-core，正在安装 Rust 工具链"
  pkg install -y rust binutils
  python -m pip install -r requirements-termux.txt
fi

if [ ! -f .env ]; then
  cp .env.example .env
  echo
  echo "已生成 server/.env，请填入真实密钥："
  echo "  nano $ROOT/server/.env"
fi

echo
echo "安装完成。启动："
echo "  bash $ROOT/scripts/termux-start.sh"

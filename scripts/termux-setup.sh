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
# Termux 的仓库偶尔同步失败，不让它中断整个安装
pkg update -y || echo "包索引更新失败，继续使用现有索引"
pkg install -y python

# 不要执行 pip install --upgrade pip：Termux 会拦截它（避免破坏 python-pip 包），
# 返回非零并让 set -e 直接退出，结果后面的依赖一个都装不上。
echo "==> 当前环境"
python -V
python -m pip --version

if python -c 'import sys; raise SystemExit(0 if sys.version_info >= (3, 13) else 1)'; then
  echo
  echo "提示：Termux 的 Python 是 $(python -V 2>&1 | cut -d' ' -f2)，pydantic-core 没有预编译包，"
  echo "      本脚本会现场编译（10-20 分钟，手机可能内存不足）。"
  echo "      更稳妥的是 proot 方案：bash $ROOT/scripts/termux-proot-setup.sh"
fi

echo "==> 安装依赖"
# pydantic v2 的核心由 Rust 编写，Termux 拿不到预编译 wheel，只能从源码编译。
# 关键点：先装好系统级 rust 和 maturin，再用 --no-build-isolation 安装。
# 否则 pip 会在隔离环境里自己下一个 maturin，那个 maturin 找不到 rust 就去装 rustup，
# 而 rustup 不支持 aarch64-linux-android，于是报
# "Target triple not supported by rustup: aarch64-unknown-linux-android"。
if ! python -m pip install --no-cache-dir -r requirements-termux.txt; then
  echo
  echo "==> 需要从源码编译 pydantic-core，正在安装 Rust 工具链（约 300MB）"
  pkg install -y rust maturin binutils clang make
  if ! command -v cargo >/dev/null 2>&1; then
    echo
    echo "Rust 没装上（多半是存储空间或网络问题）。建议改用 proot 方案，不需要编译："
    echo "  bash $ROOT/scripts/termux-proot-setup.sh"
    exit 1
  fi
  python -m pip install --no-cache-dir --no-build-isolation -r requirements-termux.txt
fi

if ! command -v uvicorn >/dev/null 2>&1; then
  echo
  echo "安装似乎没有完成：找不到 uvicorn 命令。"
  echo "请把上面的报错发出来，或手动重试："
  echo "  python -m pip install --no-cache-dir -r $ROOT/server/requirements-termux.txt"
  exit 1
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

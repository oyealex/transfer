#!/usr/bin/env bash
set -euo pipefail

# ============================================================
#  同名 Jar 多版本隔离实验 — 一键构建 & 运行脚本
#
#  核心概念：
#    com.example:a-lib:1.0  ─── 高频版本 (AppClassLoader)
#    com.example:a-lib:2.0  ─── 低频版本 (IsolatedClassLoader)
#    com.example:b-lib:1.0  ─── 大写工具 (a-lib:1.0 的依赖)
#    com.example:b-lib:2.0  ─── 小写工具 (a-lib:2.0 的依赖)
#    com.example:c-lib:1.0  ─── 共享日志 (两版本共用)
#
#  注意: a-lib:2.0 和 b-lib:2.0 与 v1 共享 artifactId 不同版本，
#  Maven reactor 不允许重复 artifactId，因此它们独立构建。
# ============================================================

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

echo "============================================"
echo "  同名 Jar 多版本隔离实验 — 构建"
echo "============================================"

# 第 1 步：构建 reactor（c-lib, b-lib-v1, a-lib-v1, main-app）
#   ★ 使用 install 将 v1 的 jar 安装到本地 Maven 仓库
#   ★ a-lib-v2 构建时会从本地仓库解析 c-lib:1.0
echo ""
echo "[1/4] 构建 v1 模块和主程序（reactor）..."
mvn clean install -q

# 第 2 步：独立构建 b-lib:2.0
#   ★ 使用 -f 指定独立 POM，不在 reactor 中
echo ""
echo "[2/4] 独立构建 b-lib:2.0（小写工具）..."
mvn -f b-lib-v2/pom.xml clean install -q

# 第 3 步：独立构建 a-lib:2.0
#   ★ 依赖 b-lib:2.0（上一步已安装到本地仓库）和 c-lib:1.0（第1步安装）
echo ""
echo "[3/4] 独立构建 a-lib:2.0（小写问候）..."
mvn -f a-lib-v2/pom.xml clean package -q

# 第 4 步：将 v2 的 jar 复制到 main-app/target/lib/（供 IsolatedClassLoader 加载）
echo ""
echo "[4/4] 复制 v2 jar 到 main-app/target/lib/..."
mkdir -p main-app/target/lib
cp a-lib-v2/target/a-lib-2.0.jar main-app/target/lib/
cp b-lib-v2/target/b-lib-2.0.jar main-app/target/lib/
echo "  ✓ a-lib-2.0.jar → main-app/target/lib/"
echo "  ✓ b-lib-2.0.jar → main-app/target/lib/"

# 运行实验
echo ""
echo "============================================"
echo "  同名 Jar 多版本隔离实验 — 运行"
echo "============================================"

# ★ 注意: main-app/target/lib/ 中的 v2 jar 不能放在 -cp 中！
#   否则 AppClassLoader 会先加载到 a-lib:2.0，隔离失效。
java -cp "main-app/target/classes:a-lib-v1/target/a-lib-1.0.jar:b-lib-v1/target/b-lib-1.0.jar:c-lib/target/c-lib-1.0.jar" \
    com.example.main.Runner

echo ""
echo "实验完成。"

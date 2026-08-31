#!/bin/bash
# ============================================================================
# Systar 系统数据库初始化脚本 (MySQL)
#
# 用法:
#   bash sql/mysql/init.sh
#
# 环境变量 (可选):
#   MYSQL_HOST     默认 localhost
#   MYSQL_PORT     默认 3306
#   MYSQL_DB       默认 db_systar
#   MYSQL_USER     默认 systar
#   MYSQL_PASSWORD 默认 123456
#
# 前提: mysql 客户端在 PATH 中，或通过 MYSQL_BIN 指定路径
# ============================================================================

set -e

HOST="${MYSQL_HOST:-localhost}"
PORT="${MYSQL_PORT:-3306}"
DB="${MYSQL_DB:-db_systar}"
USER="${MYSQL_USER:-systar}"
PASS="${MYSQL_PASSWORD:-123456}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DDL_DIR="${SCRIPT_DIR}/ddl"
DATA_DIR="${SCRIPT_DIR}/data"

# Locate mysql client
MYSQL_BIN="${MYSQL_BIN:-}"
if [ -z "${MYSQL_BIN}" ]; then
    if command -v mysql &>/dev/null; then
        MYSQL_BIN="mysql"
    else
        echo "错误: 找不到 mysql 客户端，请将其加入 PATH 或设置 MYSQL_BIN 环境变量"
        exit 1
    fi
fi

echo "=== Systar 数据库初始化 ==="
echo "数据库: ${USER}@${HOST}:${PORT}/${DB}"
echo ""

# 0. Recreate database
echo "[0] 重建数据库..."
"${MYSQL_BIN}" --default-character-set=utf8mb4 -h "${HOST}" -P "${PORT}" -u "${USER}" -p"${PASS}" \
    -e "DROP DATABASE IF EXISTS \`${DB}\`; CREATE DATABASE \`${DB}\` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;" 2>/dev/null

# 1. DDL (按编号顺序执行)
echo "[1] 创建表结构..."
for f in "${DDL_DIR}"/*.sql; do
    echo "  执行: $(basename "$f")"
    "${MYSQL_BIN}" --default-character-set=utf8mb4 -h "${HOST}" -P "${PORT}" -u "${USER}" -p"${PASS}" "${DB}" < "$f"
done

# 2. Seed data
echo "[2] 初始化基础数据..."
for f in "${DATA_DIR}"/*.sql; do
    echo "  执行: $(basename "$f")"
    "${MYSQL_BIN}" --default-character-set=utf8mb4 -h "${HOST}" -P "${PORT}" -u "${USER}" -p"${PASS}" "${DB}" < "$f"
done

echo ""
echo "=== 初始化完成 ==="

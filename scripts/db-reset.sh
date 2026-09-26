#!/usr/bin/env bash
# ============================================================
# db:reset —— 任务卡 T06 第 6 项：清库 + 迁移 + 种子 + 自检，一条命令
#
# 用法（Git Bash）：  bash scripts/db-reset.sh
# 可覆盖：MYSQL_HOST MYSQL_PORT MYSQL_USER MYSQL_PASSWORD MYSQL_DB MYSQL_BIN MVN
#
# 分工：
#   第 1 步用 mysql 客户端 DROP/CREATE 数据库 —— Flyway 的 clean-disabled 默认是 true，
#     而且 clean 只删对象不删 schema，所以清库这一步留在应用外面。
#   第 2 步交给应用自己：启动时 Flyway 跑 V1/V2，--seed 在容器就绪后应用 db/seed.sql，
#     --seed-check 跑自检并按结果退出（绿 0 / 红 1），所以迁移和种子的先后顺序有保障。
#
# ⚠️ 第 1 步会抹掉 MYSQL_DB 里的全部业务数据（含 T04 遗留的审计基线行），不可回退。
# ============================================================
set -euo pipefail

DB_HOST="${MYSQL_HOST:-localhost}"
DB_PORT="${MYSQL_PORT:-3306}"
DB_USER="${MYSQL_USER:-root}"
DB_PASSWORD="${MYSQL_PASSWORD:-123456}"
DB_NAME="${MYSQL_DB:-hospital}"
# 本地 MySQL80 的客户端不在 PATH 里，默认取安装目录，找不到再由下面的检查报错。
MYSQL_BIN="${MYSQL_BIN:-/e/Mysql/Server/bin/mysql.exe}"
MVN="${MVN:-mvn}"

if [ ! -x "$MYSQL_BIN" ] && ! command -v "$MYSQL_BIN" >/dev/null 2>&1; then
    echo "找不到 mysql 客户端：$MYSQL_BIN（可用 MYSQL_BIN=... 覆盖）" >&2
    exit 2
fi

echo "==> [1/2] 清库：${DB_HOST}:${DB_PORT}/${DB_NAME}"
# 字符集与排序规则对齐 docker-compose.yml 声明的 utf8mb4 / utf8mb4_unicode_ci。
SQL="DROP DATABASE IF EXISTS \`${DB_NAME}\`; CREATE DATABASE \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
"$MYSQL_BIN" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" --password="$DB_PASSWORD" \
    --default-character-set=utf8mb4 --batch --execute="$SQL"

echo "==> [2/2] 迁移 + 种子 + 自检"
# server.port=0：随机端口，避免和本机已起的后端抢 8080；自检跑完就退出，不需要常驻。
(cd backend && "$MVN" -q -DskipTests spring-boot:run \
    -Dspring-boot.run.arguments="--seed --seed-check --server.port=0 --spring.main.banner-mode=off")

echo "==> db:reset 完成，库已是带种子数据的干净状态"

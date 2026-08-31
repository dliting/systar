#!/bin/bash
# =====================================================
#  init-database — Drop and recreate the Systar database
#
#  WARNING: This destroys ALL data. Not for production.
#  WARNING: This file MUST use LF line endings.
#  CRLF line endings will cause bash to fail.
# =====================================================

MYSQL_USER=systar
MYSQL_PASS=123456
MYSQL_DB=db_systar
SCHEMA_SQL=extensions/systar-server/src/main/resources/schema/schema-mysql.sql

echo "====================================================="
echo "  Database re-initialization"
echo "  DB: $MYSQL_DB (user: $MYSQL_USER)"
echo "====================================================="
echo ""

if ! command -v mysql &> /dev/null; then
    echo "[ERROR] mysql client not found in PATH"
    exit 1
fi

echo "[1/2] Dropping $MYSQL_DB..."
mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -e "DROP DATABASE IF EXISTS $MYSQL_DB; CREATE DATABASE $MYSQL_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
echo "        OK — database recreated."

echo "[2/2] Running schema DDL..."
mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" < "$SCHEMA_SQL"
echo "        OK — schema applied."

echo ""
echo "====================================================="
echo "  Database ready: $MYSQL_DB"
echo "  You can now run: bin/start-backend.sh"
echo "====================================================="

#!/bin/bash
# =====================================================
#  build-backend — Build the Systar backend jar
#
#  WARNING: This file MUST use LF line endings.
#  CRLF line endings will cause bash to fail.
# =====================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "[Build] Building systar-server..."
mvn clean package -pl extensions/systar-server -am -DskipTests -q
echo "[Build] OK — extensions/systar-server/target/systar-server-1.0.0.jar"

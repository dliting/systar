#!/bin/bash
# =====================================================
#  stop-all — Stop frontend + backend
#
#  WARNING: This file MUST use LF line endings.
#  CRLF line endings will cause bash to fail.
# =====================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Stopping all services..."
echo ""

echo "[Frontend]"
"$SCRIPT_DIR/stop-frontend.sh"

echo ""
echo "[Backend]"
"$SCRIPT_DIR/stop-backend.sh"

echo ""
echo "All services stopped."

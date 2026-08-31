#!/bin/bash
# =====================================================
#  start-all — Start backend + frontend
#
#  Options:
#    --force / -f   Stop existing instances first
#
#  WARNING: This file MUST use LF line endings.
#  CRLF line endings will cause bash to fail.
# =====================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

FORCE=false
for arg in "$@"; do
    case "$arg" in
        --force|-f) FORCE=true ;;
    esac
done

echo "====================================================="
echo "  Systar Full Startup"
echo "====================================================="
echo ""

echo "[1/2] Starting backend..."
"$SCRIPT_DIR/start-backend.sh" "$@"

echo ""
echo "[2/2] Starting frontend..."
if $FORCE; then
    "$SCRIPT_DIR/start-frontend.sh" --force
else
    "$SCRIPT_DIR/start-frontend.sh"
fi

echo ""
echo "====================================================="
echo "  All services started"
echo "  Backend  : http://localhost:8081/api/monitor"
echo "  Frontend : http://localhost:5173"
echo "====================================================="

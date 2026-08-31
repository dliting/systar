#!/bin/bash
# =====================================================
#  stop-backend — Stop the Systar backend (port 8081)
#
#  Tries PID file first, falls back to port scan.
#
#  WARNING: This file MUST use LF line endings.
#  CRLF line endings will cause bash to fail.
# =====================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_PORT=8081
PID_FILE="$PROJECT_DIR/temp/backend.pid"

echo "[Backend] Stopping..."

# --- Try PID file first ---
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE" 2>/dev/null)
    if [ -n "$PID" ]; then
        kill "$PID" 2>/dev/null
        sleep 1
        kill -9 "$PID" 2>/dev/null || true
        echo "[Backend] Stopped PID $PID (PID file)"
        rm -f "$PID_FILE" 2>/dev/null || true
        exit 0
    fi
    rm -f "$PID_FILE" 2>/dev/null || true
fi

# --- Fallback: port scan ---
PID=$(lsof -ti:$BACKEND_PORT 2>/dev/null || fuser -n tcp $BACKEND_PORT 2>/dev/null | awk '{print $1}')
if [ -n "$PID" ]; then
    kill "$PID" 2>/dev/null
    sleep 1
    kill -9 "$PID" 2>/dev/null || true
    echo "[Backend] Stopped PID $PID (port $BACKEND_PORT)"
    exit 0
fi

echo "[Backend] Not running (no PID file, port $BACKEND_PORT free)"

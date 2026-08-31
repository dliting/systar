#!/bin/bash
# =====================================================
#  stop-frontend — Stop the Vite dev server (port 5173)
#
#  Tries PID file first, falls back to port scan.
#
#  WARNING: This file MUST use LF line endings.
#  CRLF line endings will cause bash to fail.
# =====================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_PORT=5173
PID_FILE="$PROJECT_DIR/temp/frontend.pid"

echo "[Frontend] Stopping..."

# --- Try PID file first ---
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE" 2>/dev/null)
    if [ -n "$PID" ]; then
        kill "$PID" 2>/dev/null
        sleep 1
        kill -9 "$PID" 2>/dev/null || true
        echo "[Frontend] Stopped PID $PID (PID file)"
        rm -f "$PID_FILE" 2>/dev/null || true
        exit 0
    fi
    rm -f "$PID_FILE" 2>/dev/null || true
fi

# --- Fallback: port scan ---
PID=$(lsof -ti:$FRONTEND_PORT 2>/dev/null || fuser -n tcp $FRONTEND_PORT 2>/dev/null | awk '{print $1}')
if [ -n "$PID" ]; then
    kill "$PID" 2>/dev/null
    sleep 1
    kill -9 "$PID" 2>/dev/null || true
    echo "[Frontend] Stopped PID $PID (port $FRONTEND_PORT)"
    exit 0
fi

echo "[Frontend] Not running (no PID file, port $FRONTEND_PORT free)"

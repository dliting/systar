#!/bin/bash
# =====================================================
#  start-frontend — Start the Vite dev server (port 5173)
#
#  Options:
#    --force / -f   Stop existing instance first
#
#  WARNING: This file MUST use LF line endings.
#  CRLF line endings will cause bash to fail.
# =====================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
FRONTEND_PORT=5173

FORCE=false

for arg in "$@"; do
    case "$arg" in
        --force|-f)   FORCE=true ;;
    esac
done

cd "$PROJECT_DIR"

# --- Check if already running ---
if netstat -tln 2>/dev/null | grep -q ":$FRONTEND_PORT "; then
    if $FORCE; then
        echo "[Frontend] Already running — stopping first..."
        "$SCRIPT_DIR/stop-frontend.sh" || true
    else
        echo "[Frontend] Already running on :$FRONTEND_PORT."
        echo "          Use --force to restart."
        exit 0
    fi
fi

# --- Check node ---
if ! command -v node &> /dev/null; then
    echo "[Frontend] ERROR: node is not installed or not in PATH"
    exit 1
fi

# --- Check node_modules ---
if [ ! -d "frontend/node_modules" ]; then
    echo "[Frontend] Installing dependencies..."
    cd frontend
    npm install
    cd ..
fi

# --- Start ---
echo "[Frontend] Starting on http://localhost:$FRONTEND_PORT ..."
cd frontend
npm run dev &
cd ..

# --- Wait for health ---
echo -n "[Frontend] Waiting for startup..."
for i in $(seq 1 10); do
    sleep 2
    if curl -s -o /dev/null http://localhost:$FRONTEND_PORT 2>/dev/null; then
        echo " OK — http://localhost:$FRONTEND_PORT"
        break
    fi
    echo -n "."
done

if ! curl -s -o /dev/null http://localhost:$FRONTEND_PORT 2>/dev/null; then
    echo ""
    echo "[Frontend] FAILED — no response after 20s"
    exit 1
fi
echo ""

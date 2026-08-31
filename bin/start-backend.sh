#!/bin/bash
# =====================================================
#  start-backend — Start the Systar backend (port 8081)
#
#  Prerequisites: MySQL 8.x + db_systar database
#  Build first:   bin/build-backend.sh
#
#  Options:
#    --force / -f   Stop existing instance first
#
#  WARNING: This file MUST use LF line endings.
#  CRLF line endings will cause bash to fail.
# =====================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_PORT=8081

FORCE=false

for arg in "$@"; do
    case "$arg" in
        --force|-f) FORCE=true ;;
    esac
done

cd "$PROJECT_DIR"

# --- Check if already running ---
if netstat -tln 2>/dev/null | grep -q ":$BACKEND_PORT "; then
    if $FORCE; then
        echo "[Backend] Already running — stopping first..."
        "$SCRIPT_DIR/stop-backend.sh" || true
    else
        echo "[Backend] Already running on :$BACKEND_PORT."
        echo "         Use --force to restart."
        exit 0
    fi
fi

JAR="extensions/systar-server/target/systar-server-1.0.0.jar"
if [ ! -f "$JAR" ]; then
    echo "[Backend] JAR not found: $JAR"
    echo "         Run bin/build-backend.sh to build first."
    exit 1
fi

# --- Start ---
echo "[Backend] Starting on http://localhost:$BACKEND_PORT ..."
echo ""

java -Xms128m -Xmx256m -jar "$JAR" --spring.profiles.active=mysql &

# --- Wait for startup ---
echo -n "[Backend] Waiting for startup..."
for i in $(seq 1 15); do
    sleep 2
    if [ -f "temp/backend.pid" ]; then
        PID=$(cat "temp/backend.pid")
        echo " OK — PID $PID, http://localhost:$BACKEND_PORT"
        break
    fi
    echo -n "."
done

if [ ! -f "temp/backend.pid" ]; then
    echo ""
    echo "[Backend] FAILED — no response after 30s"
    exit 1
fi
echo ""

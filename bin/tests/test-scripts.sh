#!/bin/bash
# =====================================================
#  test-scripts — Static checks and functional tests
#  for bin/*.bat and bin/*.sh scripts.
#
#  WARNING: This file MUST use LF line endings.
# =====================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BIN_DIR="$(dirname "$SCRIPT_DIR")"
PROJECT_DIR="$(dirname "$BIN_DIR")"
PASS=0
FAIL=0

green() { echo -e "\033[32m$1\033[0m"; }
red()   { echo -e "\033[31m$1\033[0m"; }

pass() { PASS=$((PASS + 1)); green "  PASS: $1"; }
fail() { FAIL=$((FAIL + 1)); red "  FAIL: $1"; }

echo "========================================="
echo "  Script Tests"
echo "========================================="
echo ""

# ======================== file count ========================

echo "--- File count ---"
EXPECTED=16
ACTUAL=$(ls "$BIN_DIR"/*.bat "$BIN_DIR"/*.sh 2>/dev/null | wc -l)
if [ "$ACTUAL" -eq "$EXPECTED" ]; then
    pass "Found $EXPECTED script files"
else
    fail "Expected $EXPECTED script files, found $ACTUAL"
fi
echo ""

# ======================== .bat encoding ========================

echo "--- .bat line endings (must be CRLF) ---"
for f in "$BIN_DIR"/*.bat; do
    name=$(basename "$f")
    if file "$f" | grep -q "CRLF"; then
        pass "$name — CRLF"
    else
        fail "$name — NOT CRLF"
    fi
done
echo ""

echo "--- .bat BOM check (must have NO BOM) ---"
for f in "$BIN_DIR"/*.bat; do
    name=$(basename "$f")
    first3=$(xxd -l 3 -p "$f")
    if [ "$first3" != "efbbbf" ]; then
        pass "$name — no BOM"
    else
        fail "$name — HAS BOM"
    fi
done
echo ""

# ======================== .sh encoding ========================

echo "--- .sh line endings (must be LF, NOT CRLF) ---"
for f in "$BIN_DIR"/*.sh; do
    name=$(basename "$f")
    if file "$f" | grep -q "CRLF"; then
        fail "$name — HAS CRLF"
    else
        pass "$name — LF"
    fi
done
echo ""

echo "--- .sh shebang ---"
for f in "$BIN_DIR"/*.sh; do
    name=$(basename "$f")
    first=$(head -1 "$f")
    if echo "$first" | grep -q "^#!/bin/bash"; then
        pass "$name — shebang OK"
    else
        fail "$name — missing or wrong shebang: $first"
    fi
done
echo ""

echo "--- bash -n syntax check ---"
for f in "$BIN_DIR"/*.sh; do
    name=$(basename "$f")
    if bash -n "$f" 2>&1; then
        pass "$name — syntax OK"
    else
        fail "$name — syntax ERROR"
    fi
done
echo ""

# ======================== functional tests ========================

echo "--- Functional tests (no runtime deps needed) ---"

# Test: stop-backend with no PID file
echo "  [stop-backend no PID]"
rm -f "$PROJECT_DIR/temp/backend.pid"
OUTPUT=$("$BIN_DIR/stop-backend.sh" 2>&1) || true
if echo "$OUTPUT" | grep -qi "Not running"; then
    pass "stop-backend — reports 'Not running' when no PID"
else
    fail "stop-backend — unexpected output: $OUTPUT"
fi

# Test: stop-frontend with no PID file
echo "  [stop-frontend no PID]"
rm -f "$PROJECT_DIR/temp/frontend.pid"
OUTPUT=$("$BIN_DIR/stop-frontend.sh" 2>&1) || true
if echo "$OUTPUT" | grep -qi "Not running"; then
    pass "stop-frontend — reports 'Not running' when no PID"
else
    fail "stop-frontend — unexpected output: $OUTPUT"
fi

# Test: start-backend with no JAR (default: skip build)
echo "  [start-backend (default, no JAR)]"
JAR="$PROJECT_DIR/extensions/systar-server/target/systar-server-1.0.0.jar"
if [ -f "$JAR" ]; then
    mv "$JAR" "$JAR.test-backup"
    OUTPUT=$("$BIN_DIR/start-backend.sh" 2>&1) || true
    mv "$JAR.test-backup" "$JAR"
    if echo "$OUTPUT" | grep -qi "JAR not found"; then
        pass "start-backend — 'JAR not found' when JAR missing"
    else
        fail "start-backend — unexpected output: $OUTPUT"
    fi
else
    OUTPUT=$("$BIN_DIR/start-backend.sh" 2>&1) || true
    if echo "$OUTPUT" | grep -qi "JAR not found"; then
        pass "start-backend — 'JAR not found'"
    else
        fail "start-backend — unexpected output: $OUTPUT"
    fi
fi

# Test: start-frontend without node (will show error)
echo "  [start-frontend]"
if command -v node &>/dev/null; then
    green "  SKIP: node available, skip this test"
else
    OUTPUT=$("$BIN_DIR/start-frontend.sh" 2>&1) || true
    if echo "$OUTPUT" | grep -qi "node is not installed"; then
        pass "start-frontend — reports node not installed"
    else
        fail "start-frontend — unexpected output: $OUTPUT"
    fi
fi

echo ""

# ======================== summary ========================

echo "========================================="
echo "  Results: $PASS passed, $FAIL failed"
echo "========================================="

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi

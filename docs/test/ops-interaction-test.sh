#!/bin/bash
# Systar Ops Module Frontend Interaction Test Script
# Run against: http://localhost:8081 (dev profile, H2)
# Auth: X-Systar-Token header with default secret

AUTH="X-Systar-Token: changeme-default-secret"
BASE="http://localhost:8081"
PASS=0
FAIL=0

assert_status() {
  local name="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "  PASS: $name"
    PASS=$((PASS+1))
  else
    echo "  FAIL: $name (expected=$expected, actual=$actual)"
    FAIL=$((FAIL+1))
  fi
}

assert_contains() {
  local name="$1" needle="$2" haystack="$3"
  if echo "$haystack" | grep -q "$needle"; then
    echo "  PASS: $name"
    PASS=$((PASS+1))
  else
    echo "  FAIL: $name (expected to contain '$needle')"
    FAIL=$((FAIL+1))
  fi
}

echo "========================================================"
echo " Systar Ops Module - Frontend Interaction Test"
echo "========================================================"
echo ""

# -------------------------------------------------------
echo "=== 1. Work Order: Create ==="
RESP=$(curl -s -H "$AUTH" -H "Content-Type: application/json" \
  -X POST "$BASE/api/ops/work-orders" \
  -d '{"title":"AC temp alarm","type":"REPAIR","source":"MANUAL","deviceId":1001,"priority":3,"creatorId":1}')
assert_contains "status=CREATED" '"status":"CREATED"' "$RESP"
assert_contains "orderNo starts WO" '"orderNo":"WO-' "$RESP"
assert_contains "dueTime present" '"dueTime"' "$RESP"
assert_contains "spaceId resolved" '"spaceId":13' "$RESP"
WO_ID=$(echo "$RESP" | sed 's/.*"id":\([0-9]*\).*/\1/')
echo "  (work order id=$WO_ID)"

# -------------------------------------------------------
echo ""
echo "=== 2. Work Order: Assign ==="
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" -H "Content-Type: application/json" \
  -X PUT "$BASE/api/ops/work-orders/$WO_ID/assign" \
  -d '{"operatorId":1,"assigneeId":10}')
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "assign HTTP 200" "200" "$HTTP_CODE"

echo ""
echo "=== 3. Work Order: Process ==="
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" -H "Content-Type: application/json" \
  -X PUT "$BASE/api/ops/work-orders/$WO_ID/process" \
  -d '{"operatorId":1}')
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "process HTTP 200" "200" "$HTTP_CODE"

echo ""
echo "=== 4. Work Order: Close ==="
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" -H "Content-Type: application/json" \
  -X PUT "$BASE/api/ops/work-orders/$WO_ID/close" \
  -d '{"resolution":"Sensor replaced","operatorId":1}')
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "close HTTP 200" "200" "$HTTP_CODE"

echo ""
echo "=== 5. Work Order: Verify Closed ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/work-orders/$WO_ID")
assert_contains "status=CLOSED" '"status":"CLOSED"' "$RESP"
assert_contains "resolution set" '"resolution":"Sensor replaced"' "$RESP"

echo ""
echo "=== 6. Work Order: Stats ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/work-orders/stats")
assert_contains "total count" '"total"' "$RESP"
assert_contains "closed count" '"closed"' "$RESP"

echo ""
echo "=== 7. Work Order: List ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/work-orders?page=1&size=10")
assert_contains "records array" '"records"' "$RESP"

echo ""
echo "=== 8. Work Order: Create + Cancel ==="
RESP=$(curl -s -H "$AUTH" -H "Content-Type: application/json" \
  -X POST "$BASE/api/ops/work-orders" \
  -d '{"title":"Wrong alarm","type":"REPAIR","source":"MANUAL","deviceId":1002,"priority":2,"creatorId":1}')
WO2_ID=$(echo "$RESP" | sed 's/.*"id":\([0-9]*\).*/\1/')
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" -H "Content-Type: application/json" \
  -X PUT "$BASE/api/ops/work-orders/$WO2_ID/cancel" \
  -d '{"comment":"False alarm","operatorId":1}')
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "cancel HTTP 200" "200" "$HTTP_CODE"

# -------------------------------------------------------
echo ""
echo "=== 9. Device Ledger: List ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/device-ledger?page=1&size=5")
assert_contains "records array" '"records"' "$RESP"
assert_contains "device 1001" '"id":1001' "$RESP"

echo ""
echo "=== 10. Device Ledger: Detail ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/device-ledger/1001")
assert_contains "device info" '"device"' "$RESP"
assert_contains "attributes array" '"attributes"' "$RESP"

echo ""
echo "=== 11. Device Ledger: Stats ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/device-ledger/stats")
assert_contains "total count" '"total"' "$RESP"

echo ""
echo "=== 12. Device Ledger: Set Attributes ==="
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" -H "Content-Type: application/json" \
  -X POST "$BASE/api/ops/device-ledger/1001/attributes" \
  -d '[{"attrKey":"location","attrValue":"Building A Floor 3","attrType":"string"}]')
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "set attrs HTTP 200" "200" "$HTTP_CODE"

echo ""
echo "=== 13. Maintenance Record: Create ==="
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" -H "Content-Type: application/json" \
  -X POST "$BASE/api/ops/device-ledger/1001/maintenance" \
  -d '{"type":"MAINTENANCE","title":"Quarterly filter cleaning","performerId":1,"creatorId":1}')
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "create maintenance HTTP 200" "200" "$HTTP_CODE"

# -------------------------------------------------------
echo ""
echo "=== 14. Inspection: Create Plan ==="
RESP=$(curl -s -H "$AUTH" -H "Content-Type: application/json" \
  -X POST "$BASE/api/ops/inspection/plans" \
  -d '{"plan":{"name":"Weekly HVAC check","cronExpression":"0 0 9 ? * MON","enabled":1,"defaultAssigneeId":10,"autoCreateWorkorder":1,"creatorId":1},"deviceIds":[1001,1002],"items":[{"itemName":"Filter status","itemType":"CHECK","expectedValue":"Clean","sortOrder":1},{"itemName":"Temperature reading","itemType":"MEASUREMENT","expectedValue":"18-26","sortOrder":2}]}')
assert_contains "plan created" '"id"' "$RESP"
PLAN_ID=$(echo "$RESP" | sed 's/.*"id":\([0-9]*\).*/\1/')
echo "  (plan id=$PLAN_ID)"

echo ""
echo "=== 15. Inspection: Get Plan ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/inspection/plans/$PLAN_ID")
assert_contains "plan name" '"Weekly HVAC check"' "$RESP"
assert_contains "devices array" '"devices"' "$RESP"
assert_contains "items array" '"items"' "$RESP"

echo ""
echo "=== 16. Inspection: List Plans ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/inspection/plans?page=1&size=10")
assert_contains "records array" '"records"' "$RESP"

echo ""
echo "=== 17. Inspection: Add Device to Plan ==="
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" -H "Content-Type: application/json" \
  -X POST "$BASE/api/ops/inspection/plans/$PLAN_ID/devices" \
  -d '{"deviceId":1003}')
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "add device HTTP 200" "200" "$HTTP_CODE"

echo ""
echo "=== 18. Inspection: Add Template Item ==="
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" -H "Content-Type: application/json" \
  -X POST "$BASE/api/ops/inspection/plans/$PLAN_ID/items" \
  -d '{"itemName":"Noise level","itemType":"MEASUREMENT","expectedValue":"<50dB","sortOrder":3}')
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "add item HTTP 200" "200" "$HTTP_CODE"

echo ""
echo "=== 19. Inspection: Task Stats ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/inspection/task-stats")
assert_contains "stats response" '"pending"' "$RESP"

echo ""
echo "=== 20. Inspection: List Tasks ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/inspection/tasks?page=1&size=10")
assert_contains "task list" '"records"' "$RESP"

echo ""
echo "=== 21. Work Order: 404 for missing ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/work-orders/99999")
assert_contains "WO 404 body" '"code":404' "$RESP"

echo ""
echo "=== 22. Device Ledger: 404 for missing ==="
RESP=$(curl -s -H "$AUTH" "$BASE/api/ops/device-ledger/99999")
assert_contains "Device 404 body" '"code":404' "$RESP"

echo ""
echo "=== 23. Auth: 401 without token ==="
RESP=$(curl -s -w "\n%{http_code}" "$BASE/api/ops/work-orders?page=1")
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "401 without token" "401" "$HTTP_CODE"

echo ""
echo "=== 24. Inspection: Delete Plan ==="
RESP=$(curl -s -w "\n%{http_code}" -H "$AUTH" -X DELETE "$BASE/api/ops/inspection/plans/$PLAN_ID")
HTTP_CODE=$(echo "$RESP" | tail -1)
assert_status "delete plan HTTP 200" "200" "$HTTP_CODE"

echo ""
echo "========================================================"
echo " Results: $PASS passed, $FAIL failed"
echo "========================================================"
exit $FAIL

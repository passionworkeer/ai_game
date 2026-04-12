#!/bin/bash
BASE="http://localhost:3000/api/v1"

# ── Setup ──────────────────────────────────────────────────────────
UUID=$(node -e "console.log(require('crypto').randomUUID())")
echo "Testing with deviceId: $UUID"

# ── T1: Device Registration ────────────────────────────────────────
echo "=== T1: Device Registration ==="
R=$(curl -s -X POST $BASE/auth/device \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$UUID\",\"clientVersion\":\"2.0.0\",\"platform\":\"android\"}")
echo "$R"
TOKEN=$(echo "$R" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
USER_ID=$(echo "$R" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)
echo "TOKEN=$TOKEN"
echo "USER_ID=$USER_ID"
if [ -z "$TOKEN" ]; then echo "T1 FAILED: No token"; exit 1; else echo "T1 PASSED"; fi

# ── T2: Re-registration returns SAME userId ───────────────────────
echo "=== T2: Re-registration idempotency ==="
R2=$(curl -s -X POST $BASE/auth/device \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$UUID\",\"clientVersion\":\"2.0.0\",\"platform\":\"android\"}")
USER_ID2=$(echo "$R2" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)
echo "Original: $USER_ID, Re-reg: $USER_ID2"
if [ "$USER_ID" != "$USER_ID2" ]; then echo "T2 FAILED: Different userId"; exit 1; else echo "T2 PASSED"; fi

# ── T3: Get Characters (authenticated) ───────────────────────────
echo "=== T3: Get Characters (auth) ==="
R3=$(curl -s $BASE/characters -H "Authorization: Bearer $TOKEN")
echo "$R3"
if echo "$R3" | grep -q '"success":true'; then echo "T3 PASSED"; else echo "T3 FAILED"; exit 1; fi

# ── T4: Get Characters (unauthenticated = 401) ────────────────────
echo "=== T4: Get Characters (no auth = 401) ==="
R4=$(curl -s -w "\n%{http_code}" $BASE/characters)
CODE4=$(echo "$R4" | tail -1)
if [ "$CODE4" = "401" ]; then echo "T4 PASSED"; else echo "T4 FAILED (expected 401, got $CODE4)"; fi

# ── T5: Get RSA Public Key ─────────────────────────────────────────
echo "=== T5: RSA Public Key ==="
R5=$(curl -s $BASE/auth/rsa-public-key)
echo "$R5"
if echo "$R5" | grep -q '"publicKey"'; then echo "T5 PASSED"; else echo "T5 FAILED"; exit 1; fi

# ── T6: Verify Purchase ───────────────────────────────────────────
echo "=== T6: Verify Purchase ==="
ORDER_ID="test_$(date +%s)"
CHARACTER_UUID="ade85259-2bd0-44e9-a27b-2c7fdb460524"
PAID_AT=$(date +%s)
R6=$(curl -s -X POST $BASE/purchase/verify \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"characterId\":\"$CHARACTER_UUID\",\"channel\":\"wechat\",\"paidAmount\":6000,\"channelOrderId\":\"$ORDER_ID\",\"paidAt\":$PAID_AT}")
echo "$R6"
if echo "$R6" | grep -q '"success":true'; then echo "T6 PASSED"; else echo "T6 FAILED"; exit 1; fi

# ── T7: Duplicate Purchase = 409 ──────────────────────────────────
echo "=== T7: Duplicate Purchase (409) ==="
R7=$(curl -s -w "\n%{http_code}" -X POST $BASE/purchase/verify \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"characterId\":\"$CHARACTER_UUID\",\"channel\":\"wechat\",\"paidAmount\":6000,\"channelOrderId\":\"${ORDER_ID}_dup\",\"paidAt\":$PAID_AT}")
CODE7=$(echo "$R7" | tail -1)
BODY7=$(echo "$R7" | head -1)
echo "Code: $CODE7, Body: $BODY7"
if echo "$BODY7" | grep -q '"ALREADY_PURCHASED"'; then echo "T7 PASSED"; else echo "T7 FAILED"; fi

# ── T8: Purchase insufficient amount = 422 ─────────────────────────
echo "=== T8: Insufficient Amount (422) ==="
UUID8=$(node -e "console.log(require('crypto').randomUUID())")
R8a=$(curl -s -X POST $BASE/auth/device \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$UUID8\",\"clientVersion\":\"2.0.0\",\"platform\":\"android\"}")
TOKEN8=$(echo "$R8a" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
R8=$(curl -s -w "\n%{http_code}" -X POST $BASE/purchase/verify \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN8" \
  -d "{\"characterId\":\"$CHARACTER_UUID\",\"channel\":\"wechat\",\"paidAmount\":1,\"channelOrderId\":\"low_amount\",\"paidAt\":$PAID_AT}")
CODE8=$(echo "$R8" | tail -1)
BODY8=$(echo "$R8" | head -1)
echo "Code: $CODE8"
if [ "$CODE8" = "422" ]; then echo "T8 PASSED"; else echo "T8 FAILED (expected 422, got $CODE8)"; fi

# ── T9: Get Purchases List ─────────────────────────────────────────
echo "=== T9: Get Purchases List ==="
R9=$(curl -s $BASE/purchases -H "Authorization: Bearer $TOKEN")
echo "$R9"
if echo "$R9" | grep -q '"success":true'; then echo "T9 PASSED"; else echo "T9 FAILED"; exit 1; fi

# ── T10: Sync GET ─────────────────────────────────────────────────
echo "=== T10: Sync GET ==="
R10=$(curl -s "$BASE/sync/$USER_ID" -H "Authorization: Bearer $TOKEN")
echo "$R10"
if echo "$R10" | grep -q '"success":true'; then echo "T10 PASSED"; else echo "T10 FAILED"; exit 1; fi

# ── T11: Sync POST (update profile) ───────────────────────────────
echo "=== T11: Sync POST ==="
R11=$(curl -s -X POST "$BASE/sync/$USER_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"profileJson":{"nickname":"TestUser","likes":["coding","gaming"]}}')
echo "$R11"
if echo "$R11" | grep -q '"success":true'; then echo "T11 PASSED"; else echo "T11 FAILED"; exit 1; fi

# ── T12: Sync GET after update ────────────────────────────────────
echo "=== T12: Sync GET after POST ==="
R12=$(curl -s "$BASE/sync/$USER_ID" -H "Authorization: Bearer $TOKEN")
echo "$R12"
if echo "$R12" | grep -q "TestUser"; then echo "T12 PASSED"; else echo "T12 FAILED"; fi

# ── T13: Sync POST unauthorized (wrong user) ──────────────────────
echo "=== T13: Sync unauthorized (401) ==="
UUID13=$(node -e "console.log(require('crypto').randomUUID())")
R13a=$(curl -s -X POST $BASE/auth/device \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$UUID13\",\"clientVersion\":\"2.0.0\",\"platform\":\"android\"}")
TOKEN13=$(echo "$R13a" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
R13=$(curl -s -w "\n%{http_code}" -X POST "$BASE/sync/$USER_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN13" \
  -d '{"profileJson":{"nickname":"Hacker"}}')
CODE13=$(echo "$R13" | tail -1)
echo "Code: $CODE13"
if [ "$CODE13" = "401" ] || echo "$R13" | grep -q "UNAUTHORIZED"; then echo "T13 PASSED"; else echo "T13 FAILED"; fi

# ── T14: DRM Key Generate ─────────────────────────────────────────
echo "=== T14: DRM Key Generate ==="
PURCHASE_ID=$(echo "$R6" | grep -o '"purchaseId":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "PURCHASE_ID=$PURCHASE_ID"
if [ -n "$PURCHASE_ID" ]; then
  R14=$(curl -s "$BASE/purchase/$PURCHASE_ID/key/generate" \
    -H "Authorization: Bearer $TOKEN")
  echo "$R14"
  if echo "$R14" | grep -q '"success":true'; then echo "T14 PASSED"; else echo "T14 FAILED"; fi
else
  echo "T14 SKIPPED (no purchaseId)"
fi

# ── T15: DRM Key Get ─────────────────────────────────────────────
echo "=== T15: DRM Key Get ==="
if [ -n "$PURCHASE_ID" ]; then
  R15=$(curl -s "$BASE/purchase/$PURCHASE_ID/key" \
    -H "Authorization: Bearer $TOKEN")
  echo "$R15"
  if echo "$R15" | grep -q '"encryptedKey"'; then echo "T15 PASSED"; else echo "T15 FAILED"; fi
else
  echo "T15 SKIPPED"
fi

# ── T16: DRM Key unauthorized ─────────────────────────────────────
echo "=== T16: DRM Key (wrong user = 401) ==="
if [ -n "$PURCHASE_ID" ]; then
  R16=$(curl -s -w "\n%{http_code}" "$BASE/purchase/$PURCHASE_ID/key" \
    -H "Authorization: Bearer $TOKEN13")
  CODE16=$(echo "$R16" | tail -1)
  echo "Code: $CODE16"
  if [ "$CODE16" = "401" ] || echo "$R16" | grep -q "UNAUTHORIZED"; then echo "T16 PASSED"; else echo "T16 FAILED"; fi
else
  echo "T16 SKIPPED"
fi

echo ""
echo "=== ALL E2E TESTS COMPLETED ==="

#!/usr/bin/env bash
# End-to-end smoke test against the running docker compose stack.
# Needs: bash, curl. (On Windows use Git Bash, or run the Postman collection instead.)
#   ./scripts/smoke-test.sh
set -euo pipefail

PAY=${PAY:-http://localhost:8081}
LEDGER=${LEDGER:-http://localhost:8082}
NOTIFY=${NOTIFY:-http://localhost:8083}
PASS=0; FAIL=0

ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1"; FAIL=$((FAIL+1)); }
json() { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p" | head -1; }
uuid() { cat /proc/sys/kernel/random/uuid 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())' 2>/dev/null || powershell -NoProfile -Command "[guid]::NewGuid().ToString()" | tr -d '\r'; }
post_payment() { # $1=key $2=card $3=amount $4=merchant
  curl -s -i -X POST "$PAY/api/v1/payments" -H "Content-Type: application/json" -H "Idempotency-Key: $1" \
    -d "{\"merchantId\":\"$4\",\"cardNumber\":\"$2\",\"expiryMonth\":12,\"expiryYear\":2030,\"amount\":$3,\"currency\":\"INR\"}"
}
wait_status() { # $1=paymentId $2=expected
  for _ in $(seq 1 30); do
    s=$(curl -s "$PAY/api/v1/payments/$1" | json status)
    [ "$s" = "$2" ] && return 0
    sleep 1
  done
  echo "    last status: $s"; return 1
}

echo "== Health"
for u in "$PAY" "$LEDGER" "$NOTIFY"; do
  curl -fs "$u/actuator/health" | grep -q '"UP"' && ok "$u is UP" || bad "$u is not UP"
done

MER="MER-SMOKE-$RANDOM"
echo "== Happy path (merchant $MER)"
KEY=$(uuid)
R=$(post_payment "$KEY" 4111111111111111 2499.00 "$MER")
echo "$R" | head -1 | grep -q " 202" && ok "POST returns 202" || bad "POST did not return 202"
ID=$(echo "$R" | tail -1 | json paymentId)
echo "    paymentId=$ID"
wait_status "$ID" SETTLED && ok "payment reached SETTLED" || bad "payment did not settle"

echo "== Idempotency"
R2=$(post_payment "$KEY" 4111111111111111 2499.00 "$MER")
[ "$(echo "$R2" | tail -1 | json paymentId)" = "$ID" ] && ok "same key returns same paymentId" || bad "same key returned a different payment"
echo "$R2" | grep -qi "Idempotent-Replayed: true" && ok "Idempotent-Replayed header present" || bad "no Idempotent-Replayed header"
R3=$(post_payment "$KEY" 4111111111111111 1.00 "$MER")
echo "$R3" | head -1 | grep -q " 422" && ok "same key + different body = 422" || bad "expected 422"

echo "== Ledger"
E=$(curl -s "$LEDGER/api/v1/ledger/payments/$ID/entries")
[ "$(echo "$E" | grep -o '"direction"' | wc -l | tr -d ' ')" = "2" ] && ok "exactly 2 ledger entries" || bad "expected 2 entries: $E"
echo "$E" | grep -q '"balanced":true' && ok "entries balance" || bad "entries do not balance"
curl -s "$LEDGER/api/v1/ledger/merchants/$MER/balance" | grep -q '"balance":2499.00' && ok "merchant balance = 2499.00" || bad "merchant balance wrong"
curl -s "$LEDGER/api/v1/ledger/trial-balance" | grep -q '"balanced":true' && ok "whole ledger balances" || bad "trial balance not balanced"

echo "== Declines"
ID2=$(post_payment "$(uuid)" 4000000000000002 10.00 "$MER" | tail -1 | json paymentId)
wait_status "$ID2" DECLINED && ok "card ...0002 DECLINED" || bad "card ...0002 not declined"
curl -s "$PAY/api/v1/payments/$ID2" | grep -q INSUFFICIENT_FUNDS && ok "reason INSUFFICIENT_FUNDS" || bad "wrong reason"
ID3=$(post_payment "$(uuid)" 4242424242424242 60000.00 "$MER" | tail -1 | json paymentId)
wait_status "$ID3" DECLINED && ok "60000 DECLINED (LIMIT_EXCEEDED)" || bad "60000 not declined"

echo "== Validation"
curl -s -o /dev/null -w "%{http_code}" -X POST "$PAY/api/v1/payments" -H "Content-Type: application/json" \
  -d '{"merchantId":"M","cardNumber":"4111111111111111","expiryMonth":12,"expiryYear":2030,"amount":1,"currency":"INR"}' | grep -q 400 \
  && ok "missing Idempotency-Key = 400" || bad "missing key not rejected"

echo "== Notifications"
sleep 2
N=$(curl -s "$NOTIFY/api/v1/notifications?merchantId=$MER&size=50" | json totalElements)
[ "${N:-0}" -ge 4 ] && ok "$N notifications for $MER (authorized, settled, 2 declined)" || bad "only ${N:-0} notifications"

echo
echo "Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]

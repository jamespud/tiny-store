#!/usr/bin/env bash
# Deterministic probe of the order-placement idempotency semantics.
#
# Check 1 (C9): after POST /api/order/trades fails with 5xx, may the client retry the
#   identical request with the SAME Idempotency-Key? A client that receives a 5xx is
#   expected to retry; if the failed attempt consumed the key and cached no response,
#   the retry is rejected and the order can never be placed until the key expires.
#
# Check 2 (C11): is the idempotency key bound to the request body? A duplicate key that
#   carries a DIFFERENT payload must be rejected -- never answered with 200 plus the
#   previous order's identifiers. Probed directly against an order replica, because the
#   gateway's 5-minute key TTL would otherwise mask the order-domain behaviour.
#
# The failures are forced deterministically (re-posting an existing tradeId with a new
# key = same failure class as a duplicate-ID collision), so no random collision is needed.
#
# Usage: order-retry-probe.sh <gateway-port> [order-replica-port]
# Exit:  0 = both semantics correct, 2 = at least one defect reproduced, 1 = inconclusive.
set -uo pipefail

GW="${1:?usage: order-retry-probe.sh <gateway-port> [order-replica-port]}"
ORDER_PORT="${2:-}"

redis_get() { docker exec tinystore-redis-test redis-cli get "$1" 2>/dev/null; }
redis_exists() { [ -n "$(redis_get "$1")" ]; }

create_body() {
  printf '{"tradeId":"%s","buyerId":"%s","buyerNick":"probe","addressId":"addr-001","traceId":"probe-%s","orderLines":[{"skuId":"SKU_A","productId":"prod-1","productName":"P","shopId":"SHOP_A","sellerId":"seller-A","quantity":1,"priceCents":1000,"weightGrams":0}]}' "$1" "$2" "$1"
}

# posts and echoes the HTTP status; the response body lands in $PROBE_BODY_FILE
PROBE_BODY_FILE="$(mktemp)"
post_capture() {
  curl -s -o "$PROBE_BODY_FILE" -w '%{http_code}' --max-time 30 \
    -X POST "$1" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $2" \
    -d "$3" || true
}

gw_url="http://localhost:${GW}/api/order/trades"
ts="$(date +%s%N)"
trade="retryprobe-${ts}"
key_ok="k-ok-${ts}"
key_failed="k-fail-${ts}"
body="$(create_body "$trade" "buyer-${ts}")"
retry_blocked=0

echo "=== Check 1: retry after a failed placement (C9) ==="
step1="$(post_capture "$gw_url" "$key_ok" "$body")"
echo "  1) first placement                     : ${step1}"
if [ "$step1" != "200" ]; then
  echo "  VERDICT: inconclusive (baseline placement did not return 200)."
  rm -f "$PROBE_BODY_FILE"
  exit 1
fi

step2="$(post_capture "$gw_url" "$key_failed" "$body")"
echo "  2) forced failure (same tradeId, new key): ${step2}"
if [ "$step2" = "200" ]; then
  echo "  VERDICT: inconclusive (the failure could not be forced)."
  rm -f "$PROBE_BODY_FILE"
  exit 1
fi

same_via_gateway="$(post_capture "$gw_url" "$key_failed" "$body")"
echo "  3) retry with the SAME key, same body  : ${same_via_gateway}"

order_lock="idempotency:trade:create:${key_failed}"
order_response="idempotency:response:trade:create:${key_failed}"
gateway_key="idempotent:order-service:${key_failed}"
order_layer_blocked=0
if redis_exists "$order_lock" && ! redis_exists "$order_response"; then
  order_layer_blocked=1
fi

echo "  Redis left behind: ${gateway_key}=$(redis_get "$gateway_key") | ${order_lock}=$(redis_get "$order_lock") | response=$([ -n "$(redis_get "$order_response")" ] && echo present || echo ABSENT)"
if [ "$same_via_gateway" != "200" ] || [ "$order_layer_blocked" -eq 1 ]; then
  retry_blocked=1
  echo "  --> BLOCKED: the failed attempt consumed the key; a same-key retry cannot succeed."
  [ "$same_via_gateway" != "200" ] && echo "      * gateway layer answered ${same_via_gateway} (key marked COMPLETED despite the 5xx)."
  [ "$order_layer_blocked" -eq 1 ] && echo "      * order layer still holds the lock with NO cached response."
else
  echo "  --> OK: the failed attempt released its idempotency key."
fi

# ---------------------------------------------------------------------------
fingerprint_blocked=0
if [ -n "$ORDER_PORT" ]; then
  ts2="$(date +%s%N)"
  k2="fp-${ts2}"
  ta="fpA-${ts2}"
  tb="fpB-${ts2}"
  direct_url="http://localhost:${ORDER_PORT}/order/trades"
  ca="$(post_capture "$direct_url" "$k2" "$(create_body "$ta" "buyer-${ts2}")")"
  cb="$(post_capture "$direct_url" "$k2" "$(create_body "$tb" "buyer-${ts2}")")"
  served="$(grep -o '"tradeId":"[^"]*"' "$PROBE_BODY_FILE" 2>/dev/null | head -1 | cut -d'"' -f4)"
  created_b="$(docker exec tinystore-postgres-test psql -U postgres -d tinystore -t -A \
    -c "select count(*) from tinystore_order.trade where trade_id='${tb}';" 2>/dev/null | tr -d '[:space:]')"

  echo ""
  echo "=== Check 2: is the key bound to the request body? (C11) ==="
  echo "  key=${k2}  bodyA->${ta}  bodyB->${tb}"
  echo "  1) key + body A            : ${ca}"
  echo "  2) SAME key + body B       : ${cb} (served tradeId=${served:-n/a})"
  echo "  3) trades actually created with tradeId=${tb}: ${created_b:-unknown}"

  if [ "$cb" = "200" ] && [ "${created_b:-1}" = "0" ]; then
    fingerprint_blocked=1
    echo "  --> FALSE SUCCESS: answered 200 while body B was never created;"
    echo "      the request fingerprint is stored but never compared."
  elif [ "$cb" != "200" ]; then
    echo "  --> OK: the duplicate key with a different body was rejected (${cb})."
  else
    echo "  --> OK: the second request was genuinely created."
  fi
fi

rm -f "$PROBE_BODY_FILE"
echo ""
if [ "$retry_blocked" -eq 0 ] && [ "$fingerprint_blocked" -eq 0 ]; then
  echo "VERDICT: order-placement idempotency semantics are correct."
  exit 0
fi
echo "VERDICT: defects reproduced (retry_blocked=${retry_blocked}, fingerprint_blocked=${fingerprint_blocked})."
exit 2

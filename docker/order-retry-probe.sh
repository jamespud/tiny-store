#!/usr/bin/env bash
# Order-placement idempotency acceptance probe (C9 + C11).
#
# The order domain owns business idempotency: an atomic acquire binds the idempotency key to a
# request fingerprint, SUCCEEDED is only written after commit, and a rolled-back attempt releases
# its record. This probe asserts the three externally visible consequences:
#
#   Check 1 — a failed attempt must not burn the key: retrying the SAME key + SAME body must
#             actually re-execute (reach the order domain), not be rejected as a duplicate.
#   Check 2 — the key is bound to the body: same key + DIFFERENT body must be rejected and must
#             NOT create the second trade.
#   Check 3 — a successful request replays deterministically: same key + same body returns the
#             ORIGINAL tradeId / paymentIntentId.
#
# Usage: order-retry-probe.sh <gateway-port>
# Exit:  0 = all three checks pass, 2 = at least one defect reproduced, 1 = could not run.
set -uo pipefail

GW="${1:?usage: order-retry-probe.sh <gateway-port>}"
BASE="http://localhost:${GW}/api/order/trades"
BODY_FILE="$(mktemp)"
FAILED=0

body_for() {
  # $1 = tradeId, $2 = buyerId
  printf '{"tradeId":"%s","buyerId":"%s","buyerNick":"probe","addressId":"addr-001","traceId":"probe-%s","orderLines":[{"skuId":"SKU_A","productId":"prod-1","productName":"P","shopId":"SHOP_A","sellerId":"seller-A","quantity":1,"priceCents":1000,"weightGrams":0}]}' "$1" "$2" "$1"
}

post_create() {
  # $1 = idempotency key, $2 = body -> echoes status, body lands in $BODY_FILE
  curl -s -o "$BODY_FILE" -w '%{http_code}' --max-time 30 -X POST "$BASE" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $1" \
    -d "$2" || true
}

psql_t() { docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -t -A -c "$1" 2>/dev/null | tr -d '[:space:]'; }
field() { grep -o "\"$1\":\"[^\"]*\"" "$BODY_FILE" | head -1 | cut -d'"' -f4; }

ts="$(date +%s%N)"
echo "=== Order-placement idempotency probe ==="

# ---------------------------------------------------------------- Check 1 (C9)
anchor="probe-anchor-${ts}"
anchor_key="k-anchor-${ts}"
failed_key="k-failed-${ts}"
anchor_body="$(body_for "$anchor" "buyer-${ts}")"

first="$(post_create "$anchor_key" "$anchor_body")"
echo ""
echo "Check 1 — a failed attempt must not burn the idempotency key"
echo "  1) baseline placement                         : ${first}"
if [ "$first" != "200" ]; then
  echo "  VERDICT: inconclusive (baseline placement did not succeed)"
  rm -f "$BODY_FILE"; exit 1
fi

forced="$(post_create "$failed_key" "$anchor_body")"   # same tradeId, new key -> business failure
echo "  2) forced failure (same tradeId, new key)     : ${forced}"
retry="$(post_create "$failed_key" "$anchor_body")"    # same key + same body again
retry_body="$(cat "$BODY_FILE")"
echo "  3) SAME key + SAME body retry                 : ${retry}"

if printf '%s' "$retry_body" | grep -qi "idempotent_conflict\|already in progress"; then
  echo "  FAIL: the key was still held after the failed attempt (retry blocked by idempotency)"
  FAILED=1
elif [ "$retry" = "000" ]; then
  echo "  FAIL: retry produced no HTTP response (${retry})"
  FAILED=1
else
  echo "  PASS: the retry re-executed instead of being rejected as a duplicate"
fi

# ---------------------------------------------------------------- Check 2 (C11)
fp_key="k-fingerprint-${ts}"
fp_a="probe-fpA-${ts}"
fp_b="probe-fpB-${ts}"

echo ""
echo "Check 2 — the key is bound to the request body"
status_a="$(post_create "$fp_key" "$(body_for "$fp_a" "buyer-${ts}")")"
status_b="$(post_create "$fp_key" "$(body_for "$fp_b" "buyer-${ts}")")"
served="$(field tradeId)"
created_b="$(psql_t "select count(*) from tinystore_order.trade where trade_id='${fp_b}';")"
echo "  1) key + body A                               : ${status_a}"
echo "  2) SAME key + body B                          : ${status_b} (served tradeId=${served:-n/a})"
echo "  3) trades actually created with body B's id   : ${created_b:-unknown}"

if [ "$status_b" = "200" ] && [ "${created_b:-1}" = "0" ]; then
  echo "  FAIL: false success — answered 200 while body B was never created"
  FAILED=1
elif [ "${created_b:-0}" != "0" ]; then
  echo "  FAIL: the second body was actually created (key not bound to the body)"
  FAILED=1
else
  echo "  PASS: the different body was rejected (${status_b}) and never created"
fi

# ---------------------------------------------------------------- Check 3
replay_key="k-replay-${ts}"
replay_trade="probe-replay-${ts}"
replay_body="$(body_for "$replay_trade" "buyer-${ts}")"

echo ""
echo "Check 3 — a successful request replays deterministically"
ok_first="$(post_create "$replay_key" "$replay_body")"
first_trade="$(field tradeId)"
first_payment="$(field paymentIntentId)"
ok_again="$(post_create "$replay_key" "$replay_body")"
again_trade="$(field tradeId)"
again_payment="$(field paymentIntentId)"
echo "  1) first attempt                              : ${ok_first} tradeId=${first_trade:-n/a}"
echo "  2) same key + same body                       : ${ok_again} tradeId=${again_trade:-n/a}"

if [ "$ok_first" = "200" ] && [ "$ok_again" = "200" ] \
   && [ -n "$first_trade" ] && [ "$first_trade" = "$again_trade" ] \
   && [ "$first_payment" = "$again_payment" ]; then
  echo "  PASS: replayed the original tradeId and paymentIntentId"
else
  echo "  FAIL: replay did not return the original identifiers"
  FAILED=1
fi

rm -f "$BODY_FILE"
echo ""
if [ "$FAILED" -ne 0 ]; then
  echo "VERDICT: order-placement idempotency semantics are NOT satisfied."
  exit 2
fi
echo "VERDICT: order-placement idempotency semantics hold (failed retry re-executes, body is bound, success replays)."
exit 0

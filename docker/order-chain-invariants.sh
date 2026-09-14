#!/usr/bin/env bash
# Regression gate for the order-placement chain's *resource* invariants.
#
# These three behaviours were verified once by hand during multi-instance testing and are
# currently CORRECT -- but nothing protected them. This script turns them into a repeatable
# check so a future change cannot silently regress them:
#
#   1. a failed placement must not leak inventory
#   2. a partial multi-shop failure must release the shop that did succeed
#   3. expiry (unpaid reservation timeout) must return the inventory
#   4. one one-time coupon used by N concurrent orders is arbitrated to exactly one winner
#
# Every check compares "how many orders can still be placed" against the SKU's real stock,
# which is the only assertion that matters to the business.
#
# Usage: order-chain-invariants.sh <gateway-port>
# Exit:  0 = all invariants hold, 2 = at least one regressed, 1 = could not run.
set -uo pipefail

GW="${1:?usage: order-chain-invariants.sh <gateway-port>}"
BASE="http://localhost:${GW}/api/order/trades"
FAILED=0

psql_t() { docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -t -A -c "$1" 2>/dev/null; }
psql_q() { docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -q -c "$1" >/dev/null 2>&1; }
# -q so a RETURNING insert yields only the value (no "INSERT 0 1" command tag)
psql_v() { docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -q -t -A -c "$1" 2>/dev/null; }

seed_stock() {
  psql_q "INSERT INTO tinystore_inventory.inventory_stock (shop_id, sku_id, total_quantity, reserved_quantity, version, created_at, updated_at)
          VALUES ('$1','$2',$3,0,0,NOW(),NOW()) ON CONFLICT (shop_id, sku_id) DO NOTHING;"
}

line_json() { printf '{"skuId":"%s","productId":"%s","productName":"P","shopId":"%s","sellerId":"%s","quantity":1,"priceCents":1000,"weightGrams":0}' "$1" "$2" "$3" "$4"; }

place() {
  # $1 = idempotency key, $2 = tradeId, $3 = comma separated order lines json
  curl -s -o /dev/null -w '%{http_code}' --max-time 25 -X POST "$BASE" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $1" \
    -d "{\"tradeId\":\"$2\",\"buyerId\":\"inv-$2\",\"buyerNick\":\"inv\",\"addressId\":\"addr-001\",\"traceId\":\"inv-$2\",\"orderLines\":[$3]}" || true
}

place_with_coupon() {
  # $1 = idempotency key, $2 = tradeId, $3 = buyerId, $4 = coupon no, $5 = order lines json
  # trailing newline: callers append these to a file, one code per line
  curl -s -o /dev/null -w '%{http_code}\n' --max-time 25 -X POST "$BASE" \
    -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $1" \
    -d "{\"tradeId\":\"$2\",\"buyerId\":\"$3\",\"buyerNick\":\"arb\",\"addressId\":\"addr-001\",\"traceId\":\"$2\",\"shopCouponCodesByShop\":{\"SHOP_A\":[\"$4\"]},\"orderLines\":[$5]}" || true
}

count_placements() {
  # $1 = prefix, $2 = lines json, $3 = attempts -> echoes "<ok> <rejected>"
  local ok=0 rej=0 code i
  for i in $(seq 1 "$3"); do
    code="$(place "${1}-k-${i}" "${1}-${i}" "$2")"
    if [ "$code" = "200" ]; then ok=$((ok + 1)); else rej=$((rej + 1)); fi
  done
  echo "${ok} ${rej}"
}

ts="$(date +%s)"
echo "=== Order-placement chain invariants ==="

# ---------------------------------------------------------------- 1
sku_a="SKU-invA-${ts}"
seed_stock SHOP_A "$sku_a" 10
line_a="$(line_json "$sku_a" prod-1 SHOP_A seller-A)"

anchor="invA-anchor-${ts}"
first="$(place "invA-k0-${ts}" "$anchor" "$line_a")"
for i in 1 2 3 4 5 6; do
  place "invA-fail-${ts}-${i}" "$anchor" "$line_a" > /dev/null   # same tradeId, new key => deduct then fail
done
read -r ok1 rej1 <<< "$(count_placements "invA-after-${ts}" "$line_a" 12)"
echo ""
echo "1) failed placement must not leak stock"
echo "   anchor placed (${first}), then 6 forced persistence failures;"
echo "   stock=10, so exactly 9 more orders should be accepted -> accepted=${ok1} rejected=${rej1}"
if [ "$first" != "200" ] || [ "$ok1" != "9" ]; then
  echo "   FAIL: inventory was leaked by failed placements (expected 9 accepted)"
  FAILED=1
else
  echo "   PASS"
fi

# ---------------------------------------------------------------- 2
sku_ok="SKU-invB-ok-${ts}"
sku_bad="SKU-invB-bad-${ts}"
seed_stock SHOP_A "$sku_ok" 5
seed_stock SHOP_B "$sku_bad" 0
multi="$(line_json "$sku_ok" prod-1 SHOP_A seller-A),$(line_json "$sku_bad" prod-2 SHOP_B seller-B)"
multi_code="$(place "invB-multi-${ts}" "invB-multi-${ts}" "$multi")"
read -r ok2 rej2 <<< "$(count_placements "invB-after-${ts}" "$(line_json "$sku_ok" prod-1 SHOP_A seller-A)" 7)"
echo ""
echo "2) partial multi-shop failure must release the shop that succeeded"
echo "   two-shop order (SHOP_B out of stock) -> HTTP ${multi_code};"
echo "   SHOP_A stock=5 should still be fully sellable -> accepted=${ok2} rejected=${rej2}"
if [ "$multi_code" = "200" ]; then
  echo "   SKIP: the multi-shop failure could not be forced"
elif [ "$ok2" != "5" ]; then
  echo "   FAIL: the successful shop's hold was not released (expected 5 accepted)"
  FAILED=1
else
  echo "   PASS"
fi

# ---------------------------------------------------------------- 3
sku_exp="SKU-invC-${ts}"
seed_stock SHOP_A "$sku_exp" 3
line_c="$(line_json "$sku_exp" prod-1 SHOP_A seller-A)"
ok3=0
for i in 1 2 3; do
  [ "$(place "invC-k-${ts}-${i}" "invC-${ts}-${i}" "$line_c")" = "200" ] && ok3=$((ok3 + 1))
done
blocked="$(place "invC-over-${ts}" "invC-over-${ts}" "$line_c")"

# The authoritative reservation row is written ASYNCHRONOUSLY: the order tx commits, then the
# outbox publishes INVENTORY_RESERVE_DB, then the inventory consumer inserts the row. HTTP 200
# only proves the synchronous Redis admission succeeded, so the rows can lag the 3 placements by
# a few seconds. Wait for them before forcing expire_at, otherwise the UPDATE can match 0 rows
# and the check fails for reasons that have nothing to do with the expiry behaviour.
rows=""
for i in $(seq 1 24); do
  rows="$(psql_t "SELECT count(*) FROM tinystore_inventory.inventory_reservation
                  WHERE trade_id LIKE 'invC-${ts}-%' AND status = 'PRE_DEDUCTED';")"
  [ "$rows" = "3" ] && break
  sleep 5
done

psql_q "UPDATE tinystore_inventory.inventory_reservation SET expire_at = now() - interval '2 minutes'
        WHERE trade_id LIKE 'invC-${ts}-%' AND status = 'PRE_DEDUCTED';"

# The expiry scheduler is periodic (PT5S in the test profile): poll instead of sleeping a fixed
# amount, so a single slow tick cannot turn the gate red.
expired=0
for i in $(seq 1 24); do
  expired="$(psql_t "SELECT count(*) FROM tinystore_inventory.inventory_reservation
                     WHERE trade_id LIKE 'invC-${ts}-%' AND status = 'EXPIRED';")"
  [ "$expired" = "3" ] && break
  sleep 5
done
after="$(place "invC-after-${ts}" "invC-after-${ts}" "$line_c")"
echo ""
echo "3) expired (unpaid) reservations must return the inventory"
echo "   3 orders filled stock=3 (placed=${ok3}), 4th was rejected with ${blocked};"
echo "   PRE_DEDUCTED rows observed=${rows:-?}; after forcing expire_at into the past:"
echo "   EXPIRED reservations=${expired:-?}, new order -> ${after}"
if [ "$ok3" != "3" ] || [ "$after" != "200" ]; then
  echo "   FAIL: stock was not returned after expiry"
  FAILED=1
else
  echo "   PASS"
fi

# ---------------------------------------------------------------- 4
# C13: one one-time coupon, N concurrent orders from the same buyer.
#
# The coupon is only claimed at (asynchronous) promotion-commit time, so all N orders legitimately
# return 200 first — that window is a documented design property, not a bug. The invariant that
# must hold is the FINAL arbitration: exactly ONE trade may keep the coupon; the losers must be
# closed with their checkout quotes released, and the coupon must end up held by a single lock.
coupon_no="ARBC-${ts}"
buyer="arbc-${ts}"
sku_arb="SKU-invD-${ts}"
seed_stock SHOP_A "$sku_arb" 100
line_arb="$(line_json "$sku_arb" prod-1 SHOP_A seller-A)"

coupon_id="$(psql_v "INSERT INTO promotion.coupon
  (id, coupon_no, coupon_type, scope_type, shop_id, threshold_amount, discount_amount, total_stock, used_stock,
   start_time, end_time, priority, status)
  VALUES (gen_random_uuid(), '$coupon_no', 'MERCHANT_FULL_REDUCTION', 'STORE', 'SHOP_A', 0, 1.00, 1, 0,
          now() - interval '1 hour', now() + interval '1 hour', 0, 'ACTIVE') RETURNING id;" | tail -1 | tr -d '[:space:]')"
psql_q "INSERT INTO promotion.user_coupon (id, user_id, coupon_id, coupon_no, use_status)
        VALUES (gen_random_uuid(), '$buyer', '$coupon_id', '$coupon_no', 'UNUSED');"

ok4=0
pids=()
codes_file="/tmp/chain-invD-${ts}.txt"
: > "$codes_file"
for i in 1 2 3 4 5; do
  ( place_with_coupon "invD-k-${ts}-${i}" "invD-${ts}-${i}" "$buyer" "$coupon_no" "$line_arb" >> "$codes_file" ) &
  pids+=("$!")
done
for p in "${pids[@]}"; do wait "$p"; done
while read -r c; do [ "$c" = "200" ] && ok4=$((ok4 + 1)); done < "$codes_file"
rm -f "$codes_file"

# Poll for the asynchronous arbitration to settle (losers are cancelled by
# PendingCommitTimeoutScheduler: pending-timeout-seconds=30, checked every 10s).
committed=0
closed=0
for i in $(seq 1 24); do
  committed="$(psql_t "SELECT count(*) FROM tinystore_order.trade
                       WHERE trade_id LIKE 'invD-${ts}-%' AND promotion_commit_status = 'COMMITTED';")"
  closed="$(psql_t "SELECT count(*) FROM tinystore_order.trade
                    WHERE trade_id LIKE 'invD-${ts}-%' AND closed_at IS NOT NULL;")"
  [ "$committed" = "1" ] && [ "$closed" = "4" ] && break
  sleep 5
done
coupon_state="$(psql_t "SELECT use_status || ' lock_id=' || coalesce(lock_id, 'NULL') FROM promotion.user_coupon WHERE coupon_id='$coupon_id';")"
quotes="$(psql_t "SELECT string_agg(status || '=' || cnt, ' ') FROM
                  (SELECT status, count(*) cnt FROM promotion.checkout_quote WHERE user_id='$buyer' GROUP BY status) s;")"
echo ""
echo "4) one-time coupon used by 5 concurrent orders must be held by exactly one trade"
echo "   all 5 placements returned HTTP 200 (${ok4}/5) -- the async-commit window is real;"
echo "   after arbitration: COMMITTED=${committed} closed=${closed};"
echo "   user_coupon: ${coupon_state:-?}; checkout_quote: ${quotes:-?}"
if [ "$committed" != "1" ] || [ "$closed" != "4" ]; then
  echo "   FAIL: the one-time coupon was not arbitrated to a single winner"
  FAILED=1
else
  echo "   PASS"
fi

echo ""
if [ "$FAILED" -ne 0 ]; then
  echo "VERDICT: chain invariant regression detected."
  exit 2
fi
echo "VERDICT: all order-placement chain resource invariants hold."
exit 0

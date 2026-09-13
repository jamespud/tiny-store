#!/usr/bin/env bash
# Distributed scaling comparison: run the SAME k6 ladder against a 1-replica stack and an
# N-replica stack, then render a side-by-side throughput/latency report.
#
# Why: `make load` / `make load-matrix` measure one stack shape, and `make load-multi` measures
# the N-replica shape. Neither answers the question the distributed case is actually asked —
# "does going from 1 to N replicas buy throughput, and what does it cost in latency?" — because
# the two shapes are measured in different runs with different seeding and warm-up.
#
# Both phases therefore use the identical k6 ladder, identical per-level SKUs, identical stock,
# and a fresh database (`down -v`) so the comparison is like-for-like. Host ports come from the
# multi overlay (38xxx), so this never collides with a single-stack `make load` on :8080.
#
# Exit: 0 = report rendered; 2 = a phase failed to come up / k6 matrix failed.
set -uo pipefail

COMPOSE_TEST=""
COMPOSE_MULTI=""
SERVICES=""
REPLICAS=2
LEVELS="100 300 600"
DURATION="30s"
STOCK="500000"
OUTDIR="perf/reports"
BASELINE_ONLY=0
ORDER="single-first"
WARMUP_VUS=30
WARMUP_DURATION="10s"
WARMUP=1
REPEAT=1

while [ $# -gt 0 ]; do
  case "$1" in
    --compose-test) COMPOSE_TEST="$2"; shift 2 ;;
    --compose-multi) COMPOSE_MULTI="$2"; shift 2 ;;
    --services) SERVICES="$2"; shift 2 ;;
    --replicas) REPLICAS="$2"; shift 2 ;;
    --levels) LEVELS="$2"; shift 2 ;;
    --duration) DURATION="$2"; shift 2 ;;
    --stock) STOCK="$2"; shift 2 ;;
    --outdir) OUTDIR="$2"; shift 2 ;;
    --baseline-only) BASELINE_ONLY=1; shift ;;
    --order) ORDER="$2"; shift 2 ;;
    --warmup-vus) WARMUP_VUS="$2"; shift 2 ;;
    --warmup-duration) WARMUP_DURATION="$2"; shift 2 ;;
    --no-warmup) WARMUP=0; shift ;;
    --repeat) REPEAT="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
done

[ -n "$COMPOSE_TEST" ] && [ -n "$COMPOSE_MULTI" ] && [ -n "$SERVICES" ] || {
  echo "usage: load_compare.sh --compose-test F --compose-multi F --services \"s1 s2 ...\" [--replicas N]" >&2
  exit 1
}

if ! command -v k6 > /dev/null 2>&1; then
  echo "ERROR: k6 not found" >&2
  exit 1
fi

K6_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$K6_DIR/../.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$ROOT_DIR/$OUTDIR"
SINGLE_LOG="$ROOT_DIR/$OUTDIR/load-compare-1x-$STAMP.log"
MULTI_LOG="$ROOT_DIR/$OUTDIR/load-compare-${REPLICAS}x-$STAMP.log"
REPORT="$ROOT_DIR/$OUTDIR/load-compare-$STAMP.md"

compose() { docker compose -f "$ROOT_DIR/$COMPOSE_TEST" -f "$ROOT_DIR/$COMPOSE_MULTI" "$@"; }
replica_port() { compose ps -q "$1" | sed -n "$2p" | xargs -r -I{} docker port {} "$3" | head -1 | sed 's/.*://'; }

cleanup() {
  echo "Cleaning up multi-instance environment..."
  cd "$ROOT_DIR" || return
  compose down -v
}
trap cleanup EXIT

scale_flags() {
  local n="$1" f="" s
  for s in $SERVICES; do f="$f --scale $s=$n"; done
  printf '%s' "$f"
}

duration_seconds() {
  case "$1" in
    *s) printf '%s' "${1%s}" ;;
    *m) printf '%s' "$(( ${1%m} * 60 ))" ;;
    *) printf '%s' "$1" ;;
  esac
}

wait_gateways() {
  local n="$1" i ok idx p
  for i in $(seq 1 60); do
    ok=1
    for idx in $(seq 1 "$n"); do
      p="$(replica_port gateway "$idx" 8080)"
      if [ -z "$p" ] || [ "$(curl -sf -o /dev/null -w '%{http_code}' --max-time 3 "http://localhost:$p/actuator/health" || echo 000)" != "200" ]; then
        ok=0
      fi
    done
    [ "$ok" -eq 1 ] && { echo "  gateways healthy after $((i * 3))s"; return 0; }
    sleep 3
  done
  echo "ERROR: gateways not healthy within 180s" >&2
  return 1
}

wait_topology() {
  local n="$1" i missing svc c
  for i in $(seq 1 96); do
    missing=""
    for svc in tinystore-gateway tinystore-auth tinystore-domain-account product-service promotion-service tinystore-inventory-service order-service pay-service; do
      c="$(curl -s --max-time 3 "http://localhost:8849/nacos/v1/ns/instance/list?serviceName=$svc&healthyOnly=false" | grep -o '"healthy":true' | wc -l)"
      [ "$c" -lt "$n" ] && missing="$missing $svc($c/$n)"
    done
    [ -z "$missing" ] && { echo "  discovery topology complete ($n per service) after $((i * 5))s"; return 0; }
    sleep 5
  done
  echo "ERROR: discovery topology incomplete after 480s:$missing" >&2
  return 1
}

seed_fixture() {
  local lvl
  for lvl in $LEVELS; do
    docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -q -v ON_ERROR_STOP=1 -c \
      "INSERT INTO tinystore_inventory.inventory_stock (shop_id, sku_id, total_quantity, reserved_quantity, version, created_at, updated_at)
       VALUES ('SHOP_A','SKU-matrix-$lvl',$STOCK,0,0,NOW(),NOW()) ON CONFLICT (shop_id, sku_id) DO NOTHING;" > /dev/null
  done
  docker exec -i tinystore-postgres-test psql -U postgres -d tinystore -q -v ON_ERROR_STOP=1 \
    < "$ROOT_DIR/tests/api/src/test/resources/seed/e2e-seed.sql" > /dev/null
}

# Per-order-replica counters: "<idx> <count> <sumSeconds>". Sum lets us report the mean latency
# the replica itself observed (delta_sum/delta_count), not just its share of requests.
snapshot_order() {
  local n="$1" idx p count sum
  for idx in $(seq 1 "$n"); do
    p="$(replica_port order "$idx" 28080)"
    [ -z "$p" ] && continue
    count="$(curl -s --max-time 5 "http://localhost:$p/actuator/prometheus" 2>/dev/null \
      | grep 'http_server_requests_seconds_count' | grep 'uri="/order/trades"' | awk '{s+=$NF} END {print s+0}')"
    sum="$(curl -s --max-time 5 "http://localhost:$p/actuator/prometheus" 2>/dev/null \
      | grep 'http_server_requests_seconds_sum' | grep 'uri="/order/trades"' | awk '{s+=$NF} END {print s+0}')"
    echo "$idx ${count:-0} ${sum:-0}"
  done
}

run_phase() {
  # $1 = replicas, $2 = log file, $3 = 1 to snapshot order replicas
  local n="$1" log="$2" do_snapshot="${3:-0}" before after
  echo "--- phase: $n replica(s) per service ---"
  compose down -v > /dev/null 2>&1 || true
  # Wait until the previous phase's containers are actually gone: on a single host they would
  # otherwise still be competing for CPU/disk while this phase is being measured.
  local wait_i
  for wait_i in $(seq 1 30); do
    [ -z "$(compose ps -q 2>/dev/null)" ] && break
    sleep 2
  done
  # shellcheck disable=SC2046
  compose up -d --build $(scale_flags "$n") > /dev/null || { echo "ERROR: compose up failed for $n replica(s)" >&2; return 1; }
  wait_gateways "$n" || return 1
  wait_topology "$n" || return 1
  seed_fixture

  local gw_port
  gw_port="$(replica_port gateway 1 8080)"
  echo "  seeding done; gateway :$gw_port"

  # JVM warm-up: without it whatever phase runs FIRST is measured cold (class loading + JIT),
  # which biases the comparison against the second phase for reasons unrelated to replica count.
  if [ "$WARMUP" = "1" ]; then
    local first_level warm_sku
    first_level="$(echo "$LEVELS" | awk '{print $1}')"
    warm_sku="SKU-matrix-$first_level"
    echo "  warm-up: ${WARMUP_VUS} VUs for $WARMUP_DURATION (discarded)"
    ( cd "$K6_DIR" && VUS="$WARMUP_VUS" DURATION="$WARMUP_DURATION" SKU_ID="$warm_sku" \
        BASE_URL="http://localhost:$gw_port" k6 run --quiet order_create.js > /dev/null 2>&1 ) || true
  fi
  echo "  running measured k6 ladder (VUS=$LEVELS, DURATION=$DURATION, passes=$REPEAT)"

  if [ "$do_snapshot" = "1" ]; then
    snapshot_order "$n" > "$ROOT_DIR/$OUTDIR/.order-before-$STAMP.txt" 2>/dev/null || true
  fi

  : > "$log"
  local rc=0 pass
  for pass in $(seq 1 "$REPEAT"); do
    [ "$REPEAT" -gt 1 ] && echo "  --- measured pass $pass/$REPEAT ---"
    (
      cd "$K6_DIR" || exit 1
      VUS_LEVELS="$LEVELS" DURATION="$DURATION" BASE_URL="http://localhost:$gw_port" bash run_matrix.sh
    ) 2>&1 | tee -a "$log"
    rc=${PIPESTATUS[0]}
    [ "$rc" -ne 0 ] && break
  done

  if [ "$do_snapshot" = "1" ]; then
    snapshot_order "$n" > "$ROOT_DIR/$OUTDIR/.order-after-$STAMP.txt" 2>/dev/null || true
  fi
  return "$rc"
}

CONTEXT="order=$ORDER levels=$LEVELS duration=$DURATION passes=$REPEAT warmup=$([ "$WARMUP" = 1 ] && echo "${WARMUP_VUS}x$WARMUP_DURATION" || echo off) stock=$STOCK"
echo "=== Distributed scaling comparison: 1x vs ${REPLICAS}x ==="
echo "$CONTEXT"

# Phase order matters on a single host: whichever phase runs first may be measured cold. The
# default warms both phases up explicitly; `--order multi-first` exists to prove the result is
# not an artifact of ordering (if the winner flips, the difference is warm-up, not replica count).
if [ "$ORDER" = "multi-first" ]; then
  run_phase "$REPLICAS" "$MULTI_LOG" 1 || { echo "ERROR: ${REPLICAS}x phase failed" >&2; exit 2; }
  echo "  ${REPLICAS}x matrix log: $MULTI_LOG"
  run_phase 1 "$SINGLE_LOG" 0 || { echo "ERROR: 1x phase failed" >&2; exit 2; }
  echo "  1x matrix log: $SINGLE_LOG"
else
  run_phase 1 "$SINGLE_LOG" 0 || { echo "ERROR: 1x phase failed" >&2; exit 2; }
  echo "  1x matrix log: $SINGLE_LOG"
  if [ "$BASELINE_ONLY" = "1" ]; then
    echo "baseline-only requested; skipping the ${REPLICAS}x phase"
    exit 0
  fi
  run_phase "$REPLICAS" "$MULTI_LOG" 1 || { echo "ERROR: ${REPLICAS}x phase failed" >&2; exit 2; }
  echo "  ${REPLICAS}x matrix log: $MULTI_LOG"
fi

echo ""
echo "=== Per-replica split (order, ${REPLICAS}x phase) ==="
total_secs=$(( $(duration_seconds "$DURATION") * $(echo "$LEVELS" | wc -w) ))
awk -v secs="$total_secs" '
  NR==FNR { bc[$1]=$2; bs[$1]=$3; next }
  {
    d = $2 - bc[$1]; ds = $3 - bs[$1]
    mean = (d > 0 ? ds / d * 1000 : 0)
    printf "  replica %s: %d requests (%.0f rps) | mean=%.0fms\n", $1, d, d / secs, mean
  }
' "$ROOT_DIR/$OUTDIR/.order-before-$STAMP.txt" "$ROOT_DIR/$OUTDIR/.order-after-$STAMP.txt"

echo ""
echo "=== Cross-replica ID collision probe ==="
compose logs order 2>&1 | grep 'duplicate key value violates unique constraint' \
  | grep -oE '\[log-id: [^]]+\].*unique constraint "[^"]+"' \
  | sed -E 's/.*unique constraint "([^"]+)".*/\1/' | sort | uniq -c | sort -rn || echo "  none observed"

rm -f "$ROOT_DIR/$OUTDIR/.order-before-$STAMP.txt" "$ROOT_DIR/$OUTDIR/.order-after-$STAMP.txt"

echo ""
python3 "$K6_DIR/compare_reports.py" --single "$SINGLE_LOG" --multi "$MULTI_LOG" \
  --replicas "$REPLICAS" --context "$CONTEXT" --out "$REPORT"

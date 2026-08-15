#!/usr/bin/env bash
# k6 multi-VUS load matrix: run order_create.js through the gateway at N VU
# levels and print one compact, machine-readable result line per level.
#
#   VUS_LEVELS="500 1000 5000 10000"  levels to scan (default)
#   DURATION="60s"                    per-level run duration
#   BASE_URL="http://localhost:8080"  gateway under test
#   SKU_ID=SKU_A                      single SKU for all levels (defaults to
#                                     per-level SKU-matrix-<vus>; make load-matrix
#                                     seeds those rows before calling this script)
#   SCRIPT=order_create.js            k6 script to run (default)
#
# Why this does not use grep on k6's output: k6 prints its summary as JSON
# where the metric NAME ("http_req_duration{name:create_trade}") and its
# VALUES are on separate lines, so `grep http_req_duration` only matched the
# name header and silently dropped the numbers. Instead we run with --quiet
# (stdout is then pure JSON from handleSummary) and parse it with
# k6_summary.py - the same machinery as `make load-min`.
set -uo pipefail

LEVELS="${VUS_LEVELS:-500 1000 5000 10000}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
DURATION="${DURATION:-60s}"
SCRIPT="${SCRIPT:-order_create.js}"
WORKDIR="${TMPDIR:-/tmp}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tag="tinystore-matrix-$$"

declare -a RESULT_LINES=()
index=0
for vus in $LEVELS; do
  sku="${SKU_ID:-SKU-matrix-${vus}}"
  out="$WORKDIR/$tag-$vus.json"
  err="$WORKDIR/$tag-$vus.err"
  echo "=== k6 VUS=$vus DURATION=$DURATION SKU=$sku BASE_URL=$BASE_URL ==="

  summary=""
  attempt=1
  while [ "$attempt" -le 2 ]; do
    rm -f "$out" "$err"
    VUS=$vus DURATION=$DURATION SKU_ID=$sku BASE_URL=$BASE_URL \
      k6 run --quiet "$SCRIPT" > "$out" 2> "$err"
    rc=$?
    summary="$(python3 "$DIR/k6_summary.py" "$out" "$err" || true)"
    case "$summary" in
      "NO DATA"*)
        echo "  --> no data on attempt $attempt (transient k6 init error?), retrying in 5s..."
        attempt=$((attempt + 1))
        sleep 5
        ;;
      *)
        break
        ;;
    esac
  done

  if [ "$rc" -eq 99 ]; then
    status="THRESHOLD-CROSSED"
  else
    status="thresholds ok"
  fi
  echo "  --> $summary | $status"
  RESULT_LINES[$index]="$summary"
  index=$((index + 1))
  echo "=== end VUS=$vus ==="
done

echo ""
echo "===== LOAD MATRIX SUMMARY ====="
echo "VUS | RPS | avg | p95 | p99 | fail%"
i=0
for vus in $LEVELS; do
  printf "  %-8s %s\n" "$vus" "${RESULT_LINES[$i]:-NO DATA}"
  i=$((i + 1))
done

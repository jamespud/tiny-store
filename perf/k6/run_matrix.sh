#!/usr/bin/env bash
# Loop k6 order_create.js over VU levels, print p95/p99/RPS/error per level.
set -euo pipefail
LEVELS="${VUS_LEVELS:-500 1000 5000 10000}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
DURATION="${DURATION:-60s}"
cd "$(dirname "$0")"
for vus in $LEVELS; do
  echo "=== k6 VUS=$vus DURATION=$DURATION ==="
  VUS=$vus DURATION=$DURATION BASE_URL=$BASE_URL k6 run order_create.js 2>&1 | \
    grep -E "http_req_duration|http_reqs|http_req_failed|iterations" || true
  echo "=== end VUS=$vus ==="
done

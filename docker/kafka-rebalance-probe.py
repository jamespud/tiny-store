#!/usr/bin/env python3
"""Measure the Kafka consumer-group rebalance window on the multi-instance stack (C7).

C7 was recorded in the audit as an *operational* item, not a correctness blocker: losing a group
member (a replica restart, a rolling deploy, a node eviction) stalls consumption while the group
rebalances. What a release needs is not "it never happens" but a number and an SLA, so this probe
records the three quantities the plan names:

  rebalance duration          kill -> group Stable again with the remaining member(s)
  consumer unavailable        the window the group spent NOT Stable (nothing was being consumed)
  lag recovery                first observed lag > 0 -> lag back to 0

Backlog matters: with an empty topic there is no lag to recover, so the probe places a burst of real
orders FIRST (their promotion commits come back as acks on the topic this group consumes) and kills
the replica while those acks are still flowing. Note the order matters for another reason too -- the
business API is a mutation, and mutations are deliberately not retried across replicas (task 11), so
a POST aimed at a replica that is already dead must fail; placing the orders first keeps this probe
about the group, not about gateway failover.

Exit 0 = measured within the SLA, 2 = SLA exceeded (report it; per the plan this is a finding, and a
release blocker only by that explicit SLA), 1 = the run is unusable (no group, no traffic, ...).
"""
import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor


def docker(*args, check=True):
    result = subprocess.run(["docker", *args], capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError("docker %s failed: %s" % (" ".join(args), result.stderr.strip()))
    return result.stdout


def kafka_consumer_groups(args):
    return docker("exec", os.environ.get("KAFKA_CONTAINER", "tinystore-kafka-test"),
                  "/opt/kafka/bin/kafka-consumer-groups.sh",
                  "--bootstrap-server", os.environ.get("KAFKA_BOOTSTRAP", "localhost:9092"), *args)


def rows(output):
    """Data rows of a kafka-consumer-groups table, with the header's column indexes."""
    header = None
    data = []
    for line in output.splitlines():
        if not line.strip() or "Error" in line or line.strip().startswith("Warning"):
            continue
        if line.startswith("GROUP"):
            header = line.split()
            continue
        if header is not None:
            data.append(line.split())
    return header, data


def group_state(group):
    """(state, member_count) for the group, or (None, 0) when it has no members."""
    header, data = rows(kafka_consumer_groups(["--describe", "--group", group, "--state"]))
    if not header or not data:
        return None, 0
    fields = data[-1]
    if len(fields) != len(header):
        return None, 0
    by_name = dict(zip(header, fields))
    try:
        return by_name.get("STATE"), int(by_name.get("#MEMBERS", "0"))
    except ValueError:
        return None, 0


def total_lag(group):
    """Sum of LAG over every partition of the group, or None when the group has no offsets yet."""
    header, data = rows(kafka_consumer_groups(["--describe", "--group", group]))
    if not header or not data:
        return None
    lag_index = header.index("LAG")
    end_index = header.index("LOG-END-OFFSET")
    total = 0
    seen = False
    for fields in data:
        if len(fields) < len(header):
            continue
        lag = fields[lag_index]
        # A "-" lag with an empty partition is simply "nothing has ever been produced"; treat it as 0
        # instead of discarding the sample, otherwise a fresh topic can never be measured.
        if lag == "-":
            total += int(fields[end_index]) if fields[end_index].isdigit() else 0
        else:
            total += max(0, int(lag))
        seen = True
    return total if seen else None


def post_order(gateway_port, trade_id, sku_id):
    payload = json.dumps({
        "tradeId": trade_id,
        "buyerId": "buyer-rebalance",
        "buyerNick": "rebalance",
        "addressId": "addr-001",
        "traceId": "trace-" + trade_id,
        "orderLines": [{
            "skuId": sku_id, "productId": "prod-1", "productName": "Product A",
            "shopId": "SHOP_A", "sellerId": "seller-A",
            "quantity": 1, "priceCents": 1000, "weightGrams": 100,
        }],
    }).encode()
    request = urllib.request.Request("http://localhost:%d/api/order/trades" % gateway_port, data=payload,
                                     method="POST", headers={
                                         "Content-Type": "application/json",
                                         "Idempotency-Key": "rebalance-" + trade_id,
                                     })
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        return {"httpStatus": error.code, "body": error.read().decode()[:200]}
    except Exception as error:                     # noqa: BLE001 - reported as the measurement
        return {"error": "%s: %s" % (type(error).__name__, error)}


def main() -> int:
    parser = argparse.ArgumentParser(description="Kafka rebalance measurement")
    parser.add_argument("--group", default=os.environ.get("KAFKA_GROUP", "order-service"))
    parser.add_argument("--victim", required=False, default=os.environ.get("REBALANCE_VICTIM"))
    parser.add_argument("--gateway-port", type=int, default=int(os.environ.get("REBALANCE_GATEWAY_PORT", "0")))
    parser.add_argument("--sku", default=os.environ.get("REBALANCE_SKU", "SKU_A"),
                        help="a SKU with stock; the probe creates one real order to generate the lag it measures")
    parser.add_argument("--burst", type=int, default=int(os.environ.get("REBALANCE_BURST", "30")),
                        help="orders placed before the kill; their acks are the backlog being measured")
    parser.add_argument("--concurrency", type=int,
                        default=int(os.environ.get("REBALANCE_CONCURRENCY", "10")))
    parser.add_argument("--budget-seconds", type=float,
                        default=float(os.environ.get("REBALANCE_BUDGET_SECONDS", "90")))
    parser.add_argument("--lag-sla-seconds", type=float,
                        default=float(os.environ.get("REBALANCE_LAG_SLA_SECONDS", "60")))
    args = parser.parse_args()
    if not args.victim or not args.gateway_port:
        print("  --victim and --gateway-port are required (or REBALANCE_VICTIM / REBALANCE_GATEWAY_PORT)")
        return 1

    state, members = group_state(args.group)
    if state is None:
        print("  group %s has no members; start the stack first" % args.group)
        return 1
    print("  group=%s state=%s members=%d lag=%s" % (args.group, state, members, total_lag(args.group)))

    # Backlog first: N real orders whose promotion acks are still in flight when the kill lands.
    run = uuid.uuid4().hex[:8]
    trade_ids = ["trade-rebalance-%s-%d" % (run, index) for index in range(1, args.burst + 1)]
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        placed = list(pool.map(lambda trade_id: post_order(args.gateway_port, trade_id, args.sku), trade_ids))
    accepted = [result for result in placed if result.get("code") == 0]
    print("  placed %d/%d orders (%s)" % (len(accepted), args.burst,
                                          placed[0] if not accepted else "all accepted"))
    if not accepted:
        print("  VERDICT: INDETERMINATE -- no order was accepted, so no events are in flight to "
              "interrupt; seed stock for --sku %s first" % args.sku)
        return 1

    kill_at = time.monotonic()
    docker("kill", args.victim)
    print("  killed %s at T+0.0s" % args.victim)

    stable_at = None
    unavailable_seconds = 0.0
    not_stable_since = None
    first_lag_at = None
    recovered_at = None
    samples = []
    deadline = kill_at + args.budget_seconds
    while time.monotonic() < deadline:
        now = time.monotonic()
        state, members = group_state(args.group)
        lag = total_lag(args.group)
        samples.append((now - kill_at, state, members, lag))
        if state and state != "Stable":
            not_stable_since = not_stable_since or now
        elif not_stable_since is not None and stable_at is None:
            stable_at = now
            unavailable_seconds = max(unavailable_seconds, now - not_stable_since)
        if lag and lag > 0 and first_lag_at is None:
            first_lag_at = now
        if first_lag_at is not None and recovered_at is None and lag == 0:
            recovered_at = now
        if stable_at and recovered_at:
            break
        time.sleep(0.5)

    def since(moment):
        return "n/a" if moment is None else "%.1fs" % (moment - kill_at)

    print("")
    print("  rebalance: group Stable again at %s (state samples: %s)"
          % (since(stable_at),
             ", ".join(sorted({sample[1] or "?" for sample in samples}))))
    print("  consumer unavailable (group not Stable): %.1fs" % unavailable_seconds)
    print("  lag: first >0 at %s, back to 0 at %s -> recovery %s"
          % (since(first_lag_at), since(recovered_at),
             "n/a" if (first_lag_at is None or recovered_at is None) else "%.1fs" % (recovered_at - first_lag_at)))
    print("  peak lag: %s" % max([sample[3] or 0 for sample in samples] or [0]))

    if first_lag_at is None:
        print("  VERDICT: INDETERMINATE -- no lag was ever observed (the order's events may not have been")
        print("           produced at all); nothing about recovery can be concluded")
        return 1
    recovery = None if recovered_at is None else recovered_at - first_lag_at
    if recovery is None:
        print("  VERDICT: NOT RECOVERED within %.0fs -- consumption never resumed" % args.budget_seconds)
        return 2
    if recovery > args.lag_sla_seconds:
        print("  VERDICT: RECOVERED BUT SLOW -- %.1fs of lag recovery exceeds the %.0fs SLA; per C7 this is"
              % (recovery, args.lag_sla_seconds))
        print("           an operational finding (rolling deploys/restarts stall the affected consumer)")
        return 2
    print("  VERDICT: rebalance and lag recovery stayed inside the %.0fs SLA." % args.lag_sla_seconds)
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""PostgreSQL connection budget for the multi-instance stack (D3).

Every replica of every DB-backed service owns a Hikari pool, and all of them point at one PostgreSQL.
The budget this probe enforces is the one written down in the plan: the *theoretical* maximum
(pool size x replicas, summed over services) must stay at or below 70% of `max_connections`, so
admin/migration/background work still has room and a load run measures the platform rather than
database starvation.

Subcommands:
  snapshot                    idle state + the budget arithmetic
  watch --seconds N --out F   sample every 250ms (run this while the load generator runs)
  report --out F              peak/median from a watch run, with the verdict

Exit codes: 0 measured within budget, 2 budget exceeded (or the pool actually reached it), 1 unusable.
"""
import argparse
import json
import os
import signal
import subprocess
import sys
import time


def psql(query):
    result = subprocess.run(
        ["docker", "exec", "-i", os.environ.get("POSTGRES_CONTAINER", "tinystore-postgres-test"),
         "psql", "-U", "postgres", "-d", "tinystore", "-t", "-A", "-c", query],
        capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()


def max_connections():
    return int(psql("SHOW max_connections;"))


def current_connections():
    return int(psql("SELECT count(*) FROM pg_stat_activity;"))


def by_application():
    rows = psql("SELECT coalesce(nullif(application_name, ''), 'unknown'), count(*) "
                "FROM pg_stat_activity GROUP BY 1 ORDER BY 2 DESC;")
    counts = {}
    for line in rows.splitlines():
        if "|" in line:
            name, count = line.split("|", 1)
            counts[name] = int(count)
    return counts


def budget(replicas):
    """(per-replica pool sum, theoretical total, allowed maximum)."""
    compose = open(os.environ.get("COMPOSE_FILE", "docker/docker-compose-test.yml")).read()
    pools = [int(value) for value in
             (line.split(":")[1].strip() for line in compose.splitlines()
              if "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE" in line)]
    limit = int(max_connections() * 0.70)
    return sum(pools), sum(pools) * replicas, limit, pools


def snapshot(replicas):
    per_replica, total, limit, pools = budget(replicas)
    print("  pools per replica: %s (sum %d) x %d replicas = %d theoretical connections"
          % (pools, per_replica, replicas, total))
    print("  postgres max_connections=%d, budget=70%%=%d" % (max_connections(), limit))
    print("  current total connections: %d" % current_connections())
    print("  by application_name: %s" % json.dumps(by_application(), sort_keys=True))
    if total > limit:
        print("  VERDICT: BUDGET EXCEEDED -- %d > %d" % (total, limit))
        return 2
    print("  VERDICT: budget holds (%d <= %d, %.0f%% used)" % (total, limit, 100.0 * total / limit))
    return 0


def watch(seconds, out):
    """Append one JSON object per sample, so a SIGTERM still leaves a usable file."""
    stopping = {"stop": False}

    def stop(_signum, _frame):
        stopping["stop"] = True

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    deadline = time.monotonic() + seconds
    count = 0
    with open(out, "w") as handle:
        while time.monotonic() < deadline and not stopping["stop"]:
            try:
                sample = {"t": round(time.monotonic(), 3), "total": current_connections()}
            except Exception as error:              # noqa: BLE001 - a failed sample is not fatal
                sample = {"t": round(time.monotonic(), 3), "error": str(error)}
            handle.write(json.dumps(sample) + "\n")
            handle.flush()
            count += 1
            time.sleep(0.25)
    print("  sampled %d times" % count)
    return 0


def report(out, replicas):
    samples = []
    with open(out) as handle:
        for line in handle:
            line = line.strip()
            if line:
                samples.append(json.loads(line))
    totals = sorted(sample["total"] for sample in samples if "total" in sample)
    if not totals:
        print("  no samples in %s" % out)
        return 1
    peak = totals[-1]
    median = totals[len(totals) // 2]
    per_replica, theoretical, limit, pools = budget(replicas)
    print("  connections during the burst: median=%d peak=%d (theoretical max %d, budget %d)"
          % (median, peak, theoretical, limit))
    print("  pools per replica: %s" % pools)
    if theoretical > limit:
        print("  VERDICT: BUDGET EXCEEDED -- the configured pools sum to %d > %d" % (theoretical, limit))
        return 2
    if peak > limit:
        print("  VERDICT: BUDGET EXCEEDED UNDER LOAD -- %d > %d" % (peak, limit))
        return 2
    print("  VERDICT: stayed inside the budget (peak %d <= %d)" % (peak, limit))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="postgres connection budget")
    parser.add_argument("mode", choices=["snapshot", "watch", "report"])
    parser.add_argument("--seconds", type=float, default=30.0)
    parser.add_argument("--out", default="/tmp/tinystore-pg-connections.json")
    parser.add_argument("--replicas", type=int, default=int(os.environ.get("MULTI_REPLICAS", "2")))
    args = parser.parse_args()
    if args.mode == "snapshot":
        return snapshot(args.replicas)
    if args.mode == "watch":
        return watch(args.seconds, args.out)
    return report(args.out, args.replicas)


if __name__ == "__main__":
    sys.exit(main())

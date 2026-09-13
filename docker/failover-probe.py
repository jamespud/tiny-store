#!/usr/bin/env python3
"""Analyse a replica-failure probe log produced by `make resilience-multi`.

Input: a file of "<start_ms_since_loop_start> <duration_ms> <http_status>" lines,
plus the epoch-ms timestamp at which a replica was killed.

Exit code 0 = every probe was served (clean failover), 2 = requests were routed to
the dead replica and failed instead of being retried on a healthy one.
"""
import os
import sys


def main() -> int:
    path = os.environ["MULTI_PROBE_LOG"]
    kill_at = int(os.environ["MULTI_KILL_AT"])

    rows = []
    with open(path) as handle:
        for line in handle:
            parts = line.split()
            if len(parts) == 3:
                rows.append((int(parts[0]), int(parts[1]), parts[2]))
    if not rows:
        print("  no probes recorded")
        return 1

    start = rows[0][0]
    kill_rel = kill_at - start
    ok = [r for r in rows if r[2] == "404"]
    bad = [r for r in rows if r[2] != "404"]

    print("  probes=%d  ok=%d  failed=%d  (%.1f%% failure)"
          % (len(rows), len(ok), len(bad), 100.0 * len(bad) / max(len(rows), 1)))
    print("  kill happened at T+%.1fs" % (kill_rel / 1000.0))

    if not bad:
        print("  VERDICT: no failed request observed; the gateway failed over cleanly.")
        return 0

    first = (bad[0][0] - start) / 1000.0
    last = (bad[-1][0] - start) / 1000.0
    window = (bad[-1][0] - kill_at) / 1000.0
    slowest = max(r[1] for r in bad)
    median_ok = sorted(r[1] for r in ok)[len(ok) // 2] if ok else -1
    codes = sorted({r[2] for r in bad})

    print("  first failure T+%.1fs  last failure T+%.1fs  -> failures persisted %.1fs after the kill"
          % (first, last, window))
    print("  failure codes: %s" % codes)
    print("  slowest failed request: %.0fms (successful median: %.0fms)" % (slowest, median_ok))
    print("  VERDICT: NO FAILOVER -- requests routed to the dead replica fail instead of being "
          "retried on a healthy one.")
    return 2


if __name__ == "__main__":
    sys.exit(main())

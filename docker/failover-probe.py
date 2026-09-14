#!/usr/bin/env python3
"""Analyse a replica-failure probe log produced by `make resilience-multi`.

Input: a file of "<epoch_ms> <duration_ms> <http_status> [<marker>]" lines, plus the epoch-ms timestamp
at which a replica was killed.

`MULTI_PROBE_OK_STATUS` (default 200) is the status that proves the request was served by a live order
replica, and the optional 4th field is a marker the probe script's caller computes (the resilience target
sets 1 when the response body carries the seeded trade id). Requiring a real order response matters: a
generic `404` -- "the gateway has no such route" -- used to count as "the backend is alive", so deleting the
order route would have kept this gate green while nothing reached order-service at all. `000` (client-side
timeout) and `500` (connect failure surfacing as a gateway error) remain failures.

Default gate (the post-fix contract): every probe is served, and the ones that hit the dead replica
are retried onto a healthy one quickly enough that the caller never notices a hang. Exit codes:

  0  pass
  1  the run itself is unusable (too few probes, or the kill window was never probed)
  2  gate violation (a request was lost, or failover took longer than the bound)

`--expect-failover` inverts the expectation: it is the pre-fix demonstration, where the probe is
*supposed* to observe lost requests and exit 0 because the defect reproduced. Without it a green
run would be indistinguishable from a probe that never actually killed anything.
"""
import argparse
import os
import sys


def percentile(sorted_values, fraction):
    if not sorted_values:
        return None
    index = min(len(sorted_values) - 1, int(round(fraction * (len(sorted_values) - 1))))
    return sorted_values[index]


def read_rows(path):
    rows = []
    with open(path) as handle:
        for line in handle:
            parts = line.split()
            if len(parts) >= 3:
                marker = parts[3] if len(parts) > 3 else None
                rows.append((int(parts[0]), int(parts[1]), parts[2], marker))
    return rows


def main() -> int:
    parser = argparse.ArgumentParser(description="replica-failure probe report")
    parser.add_argument("--expect-failover", action="store_true",
                        default=os.environ.get("MULTI_EXPECT_FAILOVER", "0") == "1",
                        help="pre-fix mode: pass only when requests WERE lost to the dead replica")
    parser.add_argument("--stall-ms", type=int, default=int(os.environ.get("MULTI_STALL_MS", "500")),
                        help="a served request slower than this hit the dead replica first and was "
                             "retried; these are the failover-latency samples (default 500)")
    parser.add_argument("--max-failover-ms", type=int,
                        default=int(os.environ.get("MULTI_MAX_FAILOVER_MS", "5000")),
                        help="upper bound for a retried request: connect timeout + one healthy "
                             "attempt (default 5000)")
    parser.add_argument("--min-probes", type=int, default=int(os.environ.get("MULTI_MIN_PROBES", "20")),
                        help="refuse to judge a run with fewer samples than this")
    parser.add_argument("--min-probes-after-kill", type=int,
                        default=int(os.environ.get("MULTI_MIN_PROBES_AFTER_KILL", "3")),
                        help="the kill must be followed by traffic, otherwise failover was not tested")
    args = parser.parse_args()

    path = os.environ["MULTI_PROBE_LOG"]
    kill_at = int(os.environ["MULTI_KILL_AT"])
    ok_status = os.environ.get("MULTI_PROBE_OK_STATUS", "200")

    rows = read_rows(path)
    if not rows:
        print("  no probes recorded")
        return 1

    def served(row):
        if row[2] != ok_status:
            return False
        # When the caller logged a marker (e.g. "the body carries the seeded trade id"), it must be 1;
        # that is what keeps a gateway-level response from masquerading as a live order replica.
        return row[3] is None or row[3] == "1"

    start = rows[0][0]
    kill_rel = kill_at - start
    ok = [r for r in rows if served(r)]
    bad = [r for r in rows if not served(r)]
    after_kill = [r for r in rows if r[0] >= kill_at]
    # Served requests that took longer than the healthy baseline: the ones that were routed to the
    # dead replica, failed fast (bounded connect timeout) and completed on another instance.
    stalls = sorted(r[1] for r in ok if r[1] > args.stall_ms)

    print("  probes=%d  ok=%d  failed=%d  (%.1f%% failure; ok means status %s%s)"
          % (len(rows), len(ok), len(bad), 100.0 * len(bad) / max(len(rows), 1), ok_status,
             " + body marker" if any(r[3] is not None for r in rows) else ""))
    print("  kill happened at T+%.1fs  probes after the kill=%d" % (kill_rel / 1000.0, len(after_kill)))
    print("  served-but-retried (failover events, >%dms): %d" % (args.stall_ms, len(stalls)))
    if stalls:
        print("  failover latency: p50=%.0fms p99=%.0fms max=%.0fms  (bound %dms)"
              % (percentile(stalls, 0.50), percentile(stalls, 0.99), stalls[-1], args.max_failover_ms))

    if len(rows) < args.min_probes or len(after_kill) < args.min_probes_after_kill:
        print("  VERDICT: INDETERMINATE -- %d probes (%d after the kill) cannot judge a %s"
              % (len(rows), len(after_kill), "failure window" if args.expect_failover else "failover"))
        return 1

    if bad:
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

    if args.expect_failover:
        if not bad:
            print("  VERDICT: NO DEFECT OBSERVED -- --expect-failover demands lost requests and every "
                  "probe was served; that is the fixed behaviour, not a reproduction.")
            return 2
        print("  VERDICT: NO FAILOVER -- requests routed to the dead replica fail instead of being "
              "retried on a healthy one (expected in the pre-fix demonstration).")
        return 0

    if bad:
        print("  VERDICT: NO FAILOVER -- requests routed to the dead replica fail instead of being "
              "retried on a healthy one.")
        return 2

    if stalls and stalls[-1] > args.max_failover_ms:
        print("  VERDICT: FAILOVER TOO SLOW -- the retry worked but took %.0fms, above the %dms bound "
              "(a bounded connect timeout is what keeps a dead replica from black-holing the request)."
              % (stalls[-1], args.max_failover_ms))
        return 2

    print("  VERDICT: no failed request observed; the gateway failed over cleanly.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

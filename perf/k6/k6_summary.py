#!/usr/bin/env python3
"""Extract a compact one-line k6 summary from a handleSummary stdout JSON.

Usage: k6_summary.py <summary.json> [<k6.stderr>]

Reads the JSON that k6 prints to stdout when the script overrides
handleSummary (run with `k6 run --quiet` so stdout is pure JSON).
Time values are milliseconds (k6 native unit). Prints e.g.:
    RPS=1700.0 avg=1500ms p95=2200ms p99=2500ms fail=0.00%
Returns non-zero and prints NO DATA when the run produced no results.
"""
import json
import sys


def stderr_tail(path):
    if not path:
        return ""
    try:
        with open(path) as f:
            tail = f.read().strip().splitlines()[-5:]
        return "; k6 stderr tail: " + " | ".join(tail)
    except Exception:
        return ""


def main():
    if len(sys.argv) < 2:
        print("usage: k6_summary.py <summary.json> [<k6.stderr>]")
        return 1

    try:
        with open(sys.argv[1]) as f:
            data = json.load(f)
    except Exception as e:
        print(f"NO DATA - could not parse k6 summary ({e}){stderr_tail(sys.argv[2] if len(sys.argv) > 2 else '')}")
        return 2

    metrics = data.get("metrics") or {}

    def get(metric, key):
        m = metrics.get(metric)
        if not m:
            return None
        return (m.get("values") or {}).get(key)

    rps = get("http_reqs", "rate")
    if rps is None:
        print(f"NO DATA - k6 produced no results (no http_reqs){stderr_tail(sys.argv[2] if len(sys.argv) > 2 else '')}")
        return 3

    # Prefer the tagged sub-metric (all traffic is tagged name:create_trade),
    # fall back to the aggregate when the tagged one is absent.
    dur = (metrics.get("http_req_duration{name:create_trade}")
           or metrics.get("http_req_duration") or {})
    dv = dur.get("values") or {}

    fail = get("http_req_failed{name:create_trade}", "rate")
    if fail is None:
        fail = get("http_req_failed", "rate")

    print("RPS=%.1f avg=%dms p95=%dms p99=%dms fail=%.2f%%" % (
        rps,
        dv.get("avg") or 0,
        dv.get("p(95)") or 0,
        dv.get("p(99)") or 0,
        (fail or 0) * 100,
    ))
    return 0


if __name__ == "__main__":
    sys.exit(main())

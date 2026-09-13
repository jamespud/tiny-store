#!/usr/bin/env python3
"""Render a 1-replica vs N-replica k6 load-matrix comparison.

Both inputs are the stdout of `perf/k6/run_matrix.sh` (one per stack shape), i.e. they contain
repeated blocks of:

    === k6 VUS=<vus> DURATION=<dur> SKU=<sku> BASE_URL=<url> ===
      --> RPS=... avg=...ms p95=...ms p99=...ms fail=...%

`make load-compare` runs the same ladder against a 1-replica stack and an N-replica stack and
feeds both logs here.

Usage:
    compare_reports.py --single <log> --multi <log> --replicas N [--out <report.md>]

Exit: 0 = comparison rendered, 1 = no comparable levels found.
"""
import argparse
import re
import statistics
import sys

LEVEL_RE = re.compile(r"^===\s*k6 VUS=(\d+)\s")
RESULT_RE = re.compile(
    r"RPS=([0-9.]+)\s+avg=(\d+)ms\s+p95=(\d+)ms\s+p99=(\d+)ms\s+fail=([0-9.]+)%"
)


def parse(path):
    """Return {vus: [ {rps, avg, p95, p99, fail}, ... ]} parsed from a run_matrix.sh log.

    A level appears more than once when the ladder was repeated (`--repeat N`); every pass is
    kept so the renderer can use the median instead of a single noisy sample.
    """
    try:
        with open(path) as f:
            lines = f.read().splitlines()
    except OSError as e:
        print(f"ERROR: cannot read {path}: {e}", file=sys.stderr)
        return {}

    out = {}
    current = None
    for line in lines:
        m = LEVEL_RE.match(line.strip())
        if m:
            current = int(m.group(1))
            continue
        if current is None:
            continue
        r = RESULT_RE.search(line)
        if r:
            out.setdefault(current, []).append({
                "rps": float(r.group(1)),
                "avg": int(r.group(2)),
                "p95": int(r.group(3)),
                "p99": int(r.group(4)),
                "fail": float(r.group(5)),
            })
            current = None
    return out


def median_of(passes, key):
    """Median of one metric across repeated passes (None when there is no data)."""
    if not passes:
        return None
    return statistics.median([p[key] for p in passes])


def spread_of(passes, key):
    """(min, max, max/min) for one metric; ratio is None when min <= 0."""
    if not passes:
        return None, None, None
    vals = [p[key] for p in passes]
    lo, hi = min(vals), max(vals)
    return lo, hi, (hi / lo if lo > 0 else None)


def rps_cell(passes):
    """Median RPS, annotated with the min-max range when the ladder was repeated."""
    med = median_of(passes, "rps")
    if len(passes) < 2:
        return "%.1f" % med
    lo, hi, _ = spread_of(passes, "rps")
    return "%.1f (%.0f-%.0f)" % (med, lo, hi)


def fmt_speedup(single_rps, multi_rps):
    if not single_rps:
        return "n/a"
    return "%.2fx" % (multi_rps / single_rps)


def fmt_delta(single_v, multi_v):
    """Latency delta multi-vs-single; negative is better (faster)."""
    if not single_v:
        return "n/a"
    pct = (multi_v - single_v) / single_v * 100.0
    return "%+.0f%%" % pct


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--single", required=True)
    ap.add_argument("--multi", required=True)
    ap.add_argument("--replicas", type=int, required=True)
    ap.add_argument("--context", default="")
    ap.add_argument("--instability-ratio", type=float, default=1.5,
                    help="flag a level as unstable when max/min RPS exceeds this ratio")
    ap.add_argument("--out")
    args = ap.parse_args()

    one = parse(args.single)
    many = parse(args.multi)
    levels = sorted(set(one) | set(many))
    if not levels:
        print("NO DATA: no k6 matrix levels found in either log", file=sys.stderr)
        return 1

    n = args.replicas
    unstable = []
    lines = []
    lines.append("===== DISTRIBUTED SCALING COMPARISON (1x vs %dx replicas) =====" % n)
    if args.context:
        lines.append("")
        lines.append("Run context: `%s`" % args.context)
    lines.append("")
    lines.append(
        "| VUS | 1x RPS | %dx RPS | throughput | 1x p95 | %dx p95 | 1x p99 | %dx p99 | 1x fail%% | %dx fail%% |"
        % (n, n, n, n)
    )
    lines.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")

    for vus in levels:
        ap, bp = one.get(vus, []), many.get(vus, [])
        a = {k: median_of(ap, k) for k in ("rps", "avg", "p95", "p99", "fail")} if ap else None
        b = {k: median_of(bp, k) for k in ("rps", "avg", "p95", "p99", "fail")} if bp else None

        for label, passes in (("1x", ap), (("%dx" % n), bp)):
            _, _, ratio = spread_of(passes, "rps")
            if ratio is not None and ratio > args.instability_ratio:
                unstable.append("VUS=%d %s: %.0f-%.0f RPS (%.1fx spread)"
                                % (vus, label, min(p["rps"] for p in passes),
                                   max(p["rps"] for p in passes), ratio))
        if not a or not b:
            lines.append(
                "| %d | %s | %s | n/a | %s | %s | %s | %s | %s | %s |"
                % (
                    vus,
                    rps_cell(ap) if a else "n/a",
                    rps_cell(bp) if b else "n/a",
                    "%.0fms" % a["p95"] if a else "n/a",
                    "%.0fms" % b["p95"] if b else "n/a",
                    "%.0fms" % a["p99"] if a else "n/a",
                    "%.0fms" % b["p99"] if b else "n/a",
                    "%.2f" % a["fail"] if a else "n/a",
                    "%.2f" % b["fail"] if b else "n/a",
                )
            )
            continue
        lines.append(
            "| %d | %s | %s | %s | %.0fms | %.0fms (%s) | %.0fms | %.0fms (%s) | %.2f | %.2f |"
            % (
                vus,
                rps_cell(ap),
                rps_cell(bp),
                fmt_speedup(a["rps"], b["rps"]),
                a["p95"],
                b["p95"],
                fmt_delta(a["p95"], b["p95"]),
                a["p99"],
                b["p99"],
                fmt_delta(a["p99"], b["p99"]),
                a["fail"],
                b["fail"],
            )
        )

    lines.append("")
    if unstable:
        lines.append("!! UNSTABLE MEASUREMENT - treat the throughput ratios above as NOT trustworthy:")
        for u in unstable:
            lines.append("- %s" % u)
        lines.append("  On a dev box the usual cause is unrelated host load (other containers) or the")
        lines.append("  previous phase's containers still shutting down while this one runs. Re-run on an")
        lines.append("  idle, dedicated host before concluding anything about replica scaling.")
        lines.append("")
    lines.append("Notes")
    max_passes = max([len(v) for v in list(one.values()) + list(many.values())] or [1])
    if max_passes > 1:
        lines.append("- Each cell is the **median of %d passes** over the ladder, not a single sample." % max_passes)
        lines.append("  Single-pass RPS on one docker host swings widely, so one run is not evidence.")
    lines.append("- `throughput` = %dx RPS / 1x RPS. A stateless path ideally approaches ~%dx;" % (n, n))
    lines.append("  a value near 1.00x means the bottleneck is downstream of the replicas (a shared")
    lines.append("  Postgres/Redis/Kafka link, or one saturating dependency), not the app replicas.")
    lines.append("- p95/p99 parenthesised deltas are %dx minus 1x (negative = faster under distribution)." % n)
    lines.append("- `fail%` is the k6 http_req_failed rate. Any non-zero value is a correctness")
    lines.append("  problem first and a performance problem second: investigate it before reading RPS.")
    lines.append("- Absolute numbers are single-host/docker figures: read the *ratios*, not the absolute")
    lines.append("  RPS, and do not treat them as production capacity.")

    report = "\n".join(lines)
    print(report)
    if args.out:
        with open(args.out, "w") as f:
            f.write(report + "\n")
        print("")
        print("report written to %s" % args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Drive the gateway rate limiter through real replicas and judge the trust boundary (C16).

The gateway keys its token bucket on `routeId + identity`, and the identity used to come straight
from the client's `X-Forwarded-For`. That is testable from outside because the bucket is in Redis and
shared by the replicas:

  A  burst with no header                -> the limiter must reject some requests
  B  burst with a rotating header        -> must STILL reject some requests (the pre-fix defect is
                                            0x429 here: every request is a new identity)
  C  one burst split across both gateway -> must still reject; the Make target additionally reads the
     replicas (alternating ports)          Redis keys back and requires a single shared identity
  D  trusted proxy configured: fixed     -> rejected (the forwarded address is the identity), while a
     header vs rotating header              rotating header is NOT rejected (it really is the key)

Modes: `untrusted` runs A/B/C, `trusted` runs D, `expect-bypass` is the pre-fix demonstration (B must
show zero rejections). Exit 0 = the mode's expectations held, 2 = they did not, 1 = the run itself is
unusable (route unreachable, no replicas).
"""
import argparse
import collections
import os
import sys
import urllib.error
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor


def request_once(port, path, forwarded):
    request = urllib.request.Request("http://localhost:%d%s" % (port, path), method="GET")
    if forwarded:
        request.add_header("X-Forwarded-For", forwarded)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return str(response.status)
    except urllib.error.HTTPError as error:
        return str(error.code)
    except Exception:
        return "000"


def fire(ports, path, count, concurrency, header):
    """Burst `count` requests, split round-robin over `ports`, with a per-request header if given."""

    def one(index):
        return request_once(ports[index % len(ports)], path, header(index) if header else None)

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        return collections.Counter(pool.map(one, range(1, count + 1)))


def report(name, counts):
    print("  %-46s %s" % (name, dict(sorted(counts.items()))))
    return counts.get("429", 0)


def broken(counts):
    """Requests that neither reached the limiter nor a live replica: a stack problem, not a signal."""
    return counts.get("000", 0) + sum(value for status, value in counts.items() if status.startswith("5"))


def main() -> int:
    parser = argparse.ArgumentParser(description="gateway rate-limit trust-boundary probe")
    parser.add_argument("--mode", choices=["untrusted", "trusted", "expect-bypass"], required=True)
    parser.add_argument("--burst", type=int, default=int(os.environ.get("RATE_LIMIT_BURST", "200")))
    parser.add_argument("--concurrency", type=int, default=int(os.environ.get("RATE_LIMIT_CONCURRENCY", "50")))
    parser.add_argument("--settle-seconds", type=float,
                        default=float(os.environ.get("RATE_LIMIT_SETTLE_SECONDS", "3")),
                        help="wait between cases so the token bucket refills; without it case B only "
                             "measures that case A emptied the bucket")
    args = parser.parse_args()

    ports = [int(port) for port in os.environ["RATE_LIMIT_GATEWAY_PORTS"].split(",") if port.strip()]
    # A path the order service answers with 404: the limiter keys on route + identity, so the same
    # path is fine for every request, but it must be a path that is *served* when allowed. The
    # collection path /api/order/trades is not -- it answers 500, which would masquerade as a
    # gateway failure here.
    path = os.environ.get("RATE_LIMIT_PATH", "/api/order/trades/rl-probe")
    if not ports:
        print("  no gateway replica ports")
        return 1
    print("  gateways=%s path=%s burst=%d concurrency=%d" % (ports, path, args.burst, args.concurrency))

    def settle():
        time.sleep(args.settle_seconds)

    def burst(count, header, targets=None):
        settle()
        return collections.Counter(fire(targets or ports[:1], path, count, args.concurrency, header))

    def control(count, header, targets=None, label=""):
        """Burst, and if the limiter rejected nothing, retry once with double pressure.

        The burst has to outrun the token refill (capacity 100, refill 100/s) to produce rejections
        at all, so a cold/slow replica can legitimately answer a first burst without rejecting. That
        is an environment problem, not a pass -- but it is worth one retry before giving up.
        """
        counts = burst(count, header, targets)
        if report(label, counts) == 0 and broken(counts) == 0:
            print("  no rejections in the first burst; retrying with %d requests" % (count * 2))
            counts = burst(count * 2, header, targets)
            report(label + " (retry)", counts)
        return counts

    def too_broken(counts, label):
        failures = broken(counts)
        if failures:
            print("  VERDICT: UNUSABLE -- %s saw %d responses that were neither served nor rate limited, "
                  "so the limiter is not what is being measured" % (label, failures))
        return failures

    # Precondition: the route must be servable. A 500/000 here means the probe (or the stack) is
    # broken, and no conclusion about the limiter can be drawn from it.
    settle()
    probe_status = request_once(ports[0], path, None)
    if probe_status not in ("404", "429"):
        print("  path is not servable through the gateway: single request returned %s" % probe_status)
        print("  VERDICT: INDETERMINATE")
        return 1
    print("  single request through the gateway: %s" % probe_status)

    # Reject inventing an identity: a distinct value per request is exactly what a bypass attempt does.
    rotating = lambda index: "198.51.100.%d" % (index % 250 + 1)

    if args.mode == "trusted":
        fixed_counts = control(args.burst, lambda index: "203.0.113.9", label="D1 trusted proxy, fixed header")
        if too_broken(fixed_counts, "D1"):
            return 1
        fixed = fixed_counts.get("429", 0)
        rotated_counts = burst(args.burst, rotating)
        rotated = report("D2 trusted proxy, rotating header", rotated_counts)
        if too_broken(rotated_counts, "D2"):
            return 1
        if fixed == 0:
            print("  VERDICT: TRUSTED HEADER IGNORED -- a trusted proxy's forwarded address did not become "
                  "the limiter key, so the whole chain shares one bucket")
            return 2
        if rotated > 0:
            print("  VERDICT: UNEXPECTED -- with a trusted proxy the forwarded address IS the key, so "
                  "rotating it must not be limited; saw %d rejections" % rotated)
            return 2
        print("  VERDICT: trusted proxy boundary holds (forwarded address is the identity).")
        return 0

    baseline_counts = control(args.burst, None, label="A no header (control)")
    if too_broken(baseline_counts, "A"):
        return 1
    baseline = baseline_counts.get("429", 0)
    if baseline == 0:
        print("  VERDICT: INDETERMINATE -- the limiter rejected nothing twice, so nothing about the trust "
              "boundary can be concluded on this host")
        return 1

    rotating_counts = burst(args.burst, rotating)
    rotating_untrusted = report("B rotating X-Forwarded-For (untrusted)", rotating_counts)
    if too_broken(rotating_counts, "B"):
        return 1
    if args.mode == "expect-bypass":
        if rotating_untrusted > 0:
            print("  VERDICT: NO BYPASS OBSERVED -- --expect-bypass mode demands 0 rejections behind a "
                  "rotating header; that is the fixed behaviour, not a reproduction.")
            return 2
        print("  VERDICT: BYPASSED -- rotating X-Forwarded-For defeated the limiter (expected in the "
              "pre-fix demonstration).")
        return 0

    shared_counts = burst(args.burst, None, ports)
    shared = report("C one burst split over %d gateways" % len(ports), shared_counts)
    if too_broken(shared_counts, "C"):
        return 1
    print("  rejections: A=%d B(rotating, untrusted)=%d C(split across replicas)=%d"
          % (baseline, rotating_untrusted, shared))
    print("  (counts drift with how long the burst takes vs the 100/s refill; the trust boundary is "
          "judged on B, and the shared bucket on the Redis identity keys read back by the target)")

    # B is judged against the control, not against an absolute number: the question is whether
    # rotating the header changes the answer the limiter gives. C is deliberately lenient here,
    # because how many rejections the burst produces depends on how long it takes relative to the
    # 100/s refill -- `make rate-limit-multi` reads the Redis identity keys back afterwards, which is
    # the timing-free evidence that both replicas share one bucket.
    failures = []
    if rotating_untrusted < max(1, baseline // 2):
        failures.append("B: a rotating X-Forwarded-For got %d rejections where the same burst without a "
                        "header got %d -- the limiter is keyed on a client-controlled value again"
                        % (rotating_untrusted, baseline))
    if shared == 0:
        failures.append("C: a %d-request burst split over %d replicas was not limited at all, so each "
                        "replica looks like it has its own bucket" % (args.burst, len(ports)))
    if failures:
        for failure in failures:
            print("  " + failure)
        print("  VERDICT: TRUST BOUNDARY BROKEN.")
        return 2

    print("  VERDICT: an untrusted caller cannot choose its identity, and the quota stays shared across "
          "replicas.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

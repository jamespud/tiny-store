#!/usr/bin/env bash
#
# `make it` gate wrapper (review P1-4, tightened by round-3 P2).
#
# Discipline: a *test* result is never retried. This branch exists to catch low-probability distributed
# races, and "attempt 1 fails, attempt 2 passes" would erase exactly the signal it is looking for.
#
# What may be retried is one classification of *infrastructure* failure: this host runs rootless Docker and
# Testcontainers intermittently cannot start a container. Two independent checks must agree before a retry:
#
#   1. the log carries a container-startup root cause (RootlessKit port binding, ContainerLaunchException,
#      a Docker daemon/socket failure) -- deliberately NOT a bare "Connection refused", which also shows up
#      when an application inside a test cannot reach something it expected, and
#   2. the Surefire/Failsafe reports contain no assertion failure and no error that is not itself a
#      container-startup error.
#
# Usage:
#   it-gate.sh <maven> <maven args...>          run the gate
#   IT_GATE_SELFTEST=1 it-gate.sh               exercise the classifier against synthetic logs/reports
set -uo pipefail

# The ONLY retryable root causes.
readonly container_start_failure='RootlessKit PortManager|ContainerLaunchException|Container startup failed|Could not start container|Cannot connect to the Docker daemon|DockerException'

# classify <log> <reports-root>
#   prints the reason and returns 0 when the failure may be retried as infrastructure, 1 otherwise.
classify() {
	local log="$1" reports_root="$2"

	if ! grep -qE "$container_start_failure" "$log"; then
		echo "the log shows no container-startup root cause"
		return 1
	fi

	# Independent classification from the test reports: every failure/error marker, paired with the first
	# non-indented line after it (the exception/assertion type).
	local findings
	findings="$(find "$reports_root" -path '*-reports/*.txt' -print0 2>/dev/null \
		| xargs -0 -r awk '
			/<<< (FAILURE|ERROR)!/ { pending = 1; kind = ($0 ~ /FAILURE/) ? "FAILURE" : "ERROR"; next }
			pending && /^[^[:space:]]/ && !/<<< / && !/^Tests run:/ { print kind ": " $0; pending = 0 }
		' | sort -u)"

	if [ -n "$findings" ]; then
		if printf '%s\n' "$findings" | grep -q '^FAILURE: '; then
			echo "the test reports contain an assertion failure"
			return 1
		fi
		if printf '%s\n' "$findings" | grep -vE "^ERROR: .*($container_start_failure)" | grep -q .; then
			echo "the test reports contain an error that is not a container-startup failure"
			return 1
		fi
	fi

	echo "every recorded failure is a container-startup failure"
	return 0
}

selftest() {
	local root rc failures=0
	selftest_tmp="$(mktemp -d)"
	trap 'rm -rf "${selftest_tmp:-}"' EXIT

	report() { # report <root> <contents>
		mkdir -p "$1/surefire-reports"
		printf '%s\n' "$2" >"$1/surefire-reports/TEST-something.txt"
	}

	expect() { # expect <retryable:yes|no> <label> <log-entries> <report-entries|->
		local want="$1" label="$2" log_entries="$3" report_entries="$4"
		root="$selftest_tmp/$(echo "$label" | tr -c 'a-z0-9' '_')"
		mkdir -p "$root"
		printf '%s\n' "$log_entries" >"$root/build.log"
		if [ "$report_entries" != "-" ]; then
			report "$root" "$report_entries"
		fi
		if classify "$root/build.log" "$root" >/dev/null; then rc=yes; else rc=no; fi
		if [ "$rc" = "$want" ]; then
			echo "  ok   [$want] $label"
		else
			echo "  FAIL [wanted $want, got $rc] $label"
			failures=$((failures + 1))
		fi
	}

	container_error='Tests run: 1, Failures: 0, Errors: 1, Skipped: 0 <<< ERROR! -- in com.x.SomeIT
com.x.SomeIT -- Time elapsed: 0.05 s <<< ERROR!
org.testcontainers.containers.ContainerLaunchException: Container startup failed for image testcontainers/ryuk:0.11.0'
	assertion_failure='Tests run: 1, Failures: 1, Errors: 0, Skipped: 0 <<< FAILURE! -- in com.x.SomeIT
com.x.SomeIT -- Time elapsed: 0.05 s <<< FAILURE!
java.lang.AssertionError: expected 1 COMMITTED winner but found 2'
	application_error='Tests run: 1, Failures: 0, Errors: 1, Skipped: 0 <<< ERROR! -- in com.x.SomeIT
com.x.SomeIT -- Time elapsed: 0.05 s <<< ERROR!
org.springframework.dao.DataIntegrityViolationException: could not execute statement'

	echo "it-gate classifier selftest"
	expect yes "rootless port bind + container launch error" \
		'RootlessKit PortManager.AddPort ... address already in use' "$container_error"
	expect no "bare Connection refused with no container root cause" \
		'java.net.ConnectException: Connection refused' "$application_error"
	expect no "container root cause but an assertion failure is present" \
		'RootlessKit PortManager.AddPort ... address already in use' "$assertion_failure"
	expect no "container root cause but an application error is present" \
		'ContainerLaunchException: Container startup failed' "$application_error"
	expect yes "container launch failure with no reports at all" \
		'ContainerLaunchException: Container startup failed for image testcontainers/ryuk' "-"
	expect no "clean log, no container signature" \
		'Tests run: 5, Failures: 1' "$assertion_failure"

	if [ "$failures" -ne 0 ]; then
		echo "selftest FAILED ($failures case(s))"
		return 1
	fi
	echo "selftest passed"
	return 0
}

if [ "${IT_GATE_SELFTEST:-0}" = "1" ]; then
	selftest
	exit $?
fi

maven="$1"
shift

max_attempts="${IT_INFRA_RETRIES:-2}"

attempt=0
while :; do
	attempt=$((attempt + 1))
	log="/tmp/tinystore-it-$$-${attempt}.log"
	verdict="/tmp/tinystore-it-classify-$$-${attempt}.txt"

	if "$maven" "$@" >"$log" 2>&1; then
		cat "$log"
		rm -f "$log" "$verdict"
		exit 0
	fi

	if classify "$log" . >"$verdict" 2>&1 && [ "$attempt" -lt "$max_attempts" ]; then
		cat "$verdict"
		echo "Infrastructure retry $attempt/$max_attempts: a Testcontainers container could not start (rootless Docker), which is not a test result"
		rm -f "$log" "$verdict"
		continue
	fi

	echo "Integration tests failed and the failure is NOT classified as an infrastructure-only container-startup problem:"
	echo "reason: $(cat "$verdict")"
	echo "---- log ----"
	cat "$log"
	rm -f "$log" "$verdict"
	exit 1
done

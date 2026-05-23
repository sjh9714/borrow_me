#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  k6/run-with-evidence.sh <product-listing|search|concurrent-reserve>

Environment:
  BASE_URL  BorrowMe API base URL. Default: http://localhost:5000

Artifacts:
  docs/evidence/k6/<timestamp>-<scenario>/
    metadata.txt
    summary.json
    console.txt

This script records raw k6 evidence for a new local run. It does not make
historical README metrics measured; keep old p95/throughput values labeled as
"원본 README 기록 기준" unless their raw artifacts are present.
USAGE
}

if [[ $# -ne 1 ]]; then
  usage
  exit 64
fi

scenario="$1"
case "$scenario" in
  product-listing)
    script="k6/test-product-listing.js"
    dataset_note="Requires k6/setup-data.sql seeded into the local MySQL shop database."
    ;;
  search)
    script="k6/test-search.js"
    dataset_note="Requires k6/setup-data.sql seeded into the local MySQL shop database."
    ;;
  concurrent-reserve)
    script="k6/test-concurrent-reserve.js"
    dataset_note="Requires k6/setup-data.sql seeded, with k6_test_concurrent_reserve reset to available_quantity=50."
    ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    echo "Unknown scenario: $scenario" >&2
    usage >&2
    exit 64
    ;;
esac

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 is not installed or not on PATH." >&2
  exit 127
fi

base_url="${BASE_URL:-http://localhost:5000}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
artifact_dir="docs/evidence/k6/${timestamp}-${scenario}"
summary_file="${artifact_dir}/summary.json"
console_file="${artifact_dir}/console.txt"
metadata_file="${artifact_dir}/metadata.txt"
summary_trend_stats="avg,min,med,max,p(90),p(95),p(99)"
command_display="BASE_URL=${base_url} k6 run ${script} --summary-export ${summary_file} --summary-trend-stats ${summary_trend_stats}"
git_commit="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
git_status_before_artifact="$(git status --short 2>/dev/null || true)"
if [[ -z "$git_status_before_artifact" ]]; then
  git_clean_before_artifact="true"
  git_status_before_artifact_display="(clean)"
else
  git_clean_before_artifact="false"
  git_status_before_artifact_display="$git_status_before_artifact"
fi

mkdir -p "$artifact_dir"

{
  echo "date_utc=${timestamp}"
  echo "scenario=${scenario}"
  echo "script=${script}"
  echo "base_url=${base_url}"
  echo "command=${command_display}"
  echo "git_commit=${git_commit}"
  echo "git_clean_before_artifact=${git_clean_before_artifact}"
  echo "git_status_before_artifact<<EOF"
  echo "$git_status_before_artifact_display"
  echo "EOF"
  echo "git_status_porcelain<<EOF"
  git status --short 2>/dev/null || true
  echo "EOF"
  echo "k6_version=$(k6 version 2>/dev/null || echo unknown)"
  echo "java_version<<EOF"
  java -version 2>&1 || true
  echo "EOF"
  echo "os<<EOF"
  uname -a || true
  if command -v sw_vers >/dev/null 2>&1; then
    sw_vers || true
  fi
  echo "EOF"
  echo "dataset=${dataset_note}"
  if command -v shasum >/dev/null 2>&1 && [[ -f k6/setup-data.sql ]]; then
    echo "setup_data_sha256=$(shasum -a 256 k6/setup-data.sql | awk '{print $1}')"
  fi
  echo "notes=Attach this directory before changing README or portfolio metrics to measured values."
} > "$metadata_file"

set +e
BASE_URL="$base_url" k6 run "$script" --summary-export "$summary_file" --summary-trend-stats "$summary_trend_stats" 2>&1 | tee "$console_file"
k6_status=${PIPESTATUS[0]}
set -e

echo "Artifacts written to ${artifact_dir}"
exit "$k6_status"

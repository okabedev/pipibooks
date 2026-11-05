#!/usr/bin/env bash
# scripts/tests/*.test.sh를 모두 실행한다. 하나라도 실패하면 exit 1.
set -uo pipefail
dir="$(cd "$(dirname "$0")" && pwd)"
failed=0
for t in "$dir"/*.test.sh; do
  echo "# $(basename "$t")"
  bash "$t" || failed=1
done
if [ "$failed" -ne 0 ]; then echo "SCRIPT TESTS FAILED"; exit 1; fi
echo "ALL SCRIPT TESTS PASSED"

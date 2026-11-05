# 테스트 헬퍼. 각 *.test.sh가 source한다.
# shellcheck shell=bash
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FAILURES=0
PASSES=0

assert_pass() {
  local desc=$1; shift
  if "$@" >/dev/null 2>&1; then PASSES=$((PASSES + 1)); echo "  ok   - $desc"
  else FAILURES=$((FAILURES + 1)); echo "  FAIL - $desc (expected success)"; fi
}

assert_fail() {
  local desc=$1; shift
  if "$@" >/dev/null 2>&1; then FAILURES=$((FAILURES + 1)); echo "  FAIL - $desc (expected failure)"
  else PASSES=$((PASSES + 1)); echo "  ok   - $desc"; fi
}

finish() {
  echo "  -> $PASSES passed, $FAILURES failed"
  [ "$FAILURES" -eq 0 ]
}

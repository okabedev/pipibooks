#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
DEPLOY="$REPO_ROOT/infra/scripts/deploy.sh"

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
# 가짜 docker: 호출을 기록하고 FAKE_PULL_FAIL / FAKE_HEALTH_FAIL에 따라 실패한다.
cat > "$tmp/docker" <<'FAKE'
#!/usr/bin/env bash
echo "${PIPIBOOKS_VERSION:-} $*" >> "$FAKE_LOG"
case "$*" in
  *" pull"*) [ "${FAKE_PULL_FAIL:-0}" = 1 ] && exit 1 ;;
  run\ *) [ "${FAKE_HEALTH_FAIL:-0}" = 1 ] && exit 7; echo '{"status":"UP"}' ;;
esac
exit 0
FAKE
chmod +x "$tmp/docker"

setup() { : > "$tmp/log"; echo v0.0.1 > "$tmp/state"; }
deploy() { DOCKER_BIN="$tmp/docker" FAKE_LOG="$tmp/log" STATE_FILE="$tmp/state" HEALTH_RETRIES=1 bash "$DEPLOY" "$@"; }
state_is() { [ "$(cat "$tmp/state")" = "$1" ]; }
up_called_with() { grep -q "^$1 .* up -d" "$tmp/log"; }
no_up() { ! grep -q " up -d" "$tmp/log"; }
exit_code() { local rc=0; "$@" >/dev/null 2>&1 || rc=$?; echo "$rc"; }

setup
assert_pass "v 없는 버전은 사용법 오류(exit 2)" test "$(exit_code deploy 0.0.1)" = 2

setup
assert_fail "존재하지 않는 버전은 실패" env FAKE_PULL_FAIL=1 bash -c "$(declare -f deploy); DEPLOY='$DEPLOY' tmp='$tmp' deploy v9.9.9"
assert_pass "pull 실패 시 실행 중 버전 유지" state_is v0.0.1
assert_pass "pull 실패 시 up 호출 안 함" no_up

setup
assert_fail "헬스 실패 시 배포 실패" env FAKE_HEALTH_FAIL=1 bash -c "$(declare -f deploy); DEPLOY='$DEPLOY' tmp='$tmp' deploy v0.0.2"
assert_pass "헬스 실패 시 새 버전으로 up 시도" up_called_with 0.0.2
assert_pass "헬스 실패 시 이전 버전으로 롤백" up_called_with 0.0.1
assert_pass "헬스 실패 시 상태 파일 유지" state_is v0.0.1

setup
assert_pass "정상 배포 성공" deploy v0.0.2
assert_pass "정상 배포 후 상태 파일 갱신" state_is v0.0.2
finish

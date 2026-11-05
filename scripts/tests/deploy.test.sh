#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
DEPLOY="$REPO_ROOT/infra/scripts/deploy.sh"

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
# 가짜 docker: 호출을 기록하고, up 시 현재 버전을 $tmp/current에 남긴다.
#   FAKE_PULL_FAIL=1          pull 실패
#   FAKE_UP_FAIL_VERSION=x    해당 버전 up 실패
#   FAKE_BAD_VERSION=x        해당 버전이 떠 있으면 헬스 실패
#   FAKE_HEALTH_FAIL=1        항상 헬스 실패
cat > "$tmp/docker" <<'FAKE'
#!/usr/bin/env bash
echo "${PIPIBOOKS_VERSION:-} $*" >> "$FAKE_LOG"
case "$*" in
  *" pull"*) [ "${FAKE_PULL_FAIL:-0}" = 1 ] && exit 1 ;;
  *" up -d"*)
    [ "${PIPIBOOKS_VERSION}" = "${FAKE_UP_FAIL_VERSION:-}" ] && exit 1
    echo "$PIPIBOOKS_VERSION" > "$FAKE_CURRENT" ;;
  run\ *)
    [ "${FAKE_HEALTH_FAIL:-0}" = 1 ] && exit 7
    [ "$(cat "$FAKE_CURRENT" 2>/dev/null)" = "${FAKE_BAD_VERSION:-none}" ] && exit 7
    echo '{"status":"UP"}' ;;
esac
exit 0
FAKE
chmod +x "$tmp/docker"

setup() {
  : > "$tmp/log"; echo 0.0.1 > "$tmp/current"
  printf 'PIPIBOOKS_VERSION=0.0.1\nTUNNEL_ID=abc\n' > "$tmp/env"
}
# d [VAR=값...] <인자> : 가짜 docker로 deploy.sh를 실행하고 출력은 $tmp/out에 남긴다.
d() {
  local vars=()
  while [[ "${1:-}" == *=* ]]; do vars+=("$1"); shift; done
  env DOCKER_BIN="$tmp/docker" FAKE_LOG="$tmp/log" FAKE_CURRENT="$tmp/current" ENV_FILE="$tmp/env" \
    SKIP_REV_CHECK=1 HEALTH_RETRIES=1 "${vars[@]}" bash "$DEPLOY" "$@" > "$tmp/out" 2>&1
}
code() { local rc=0; d "$@" || rc=$?; echo "$rc"; }
version_is() { grep -qx "PIPIBOOKS_VERSION=$1" "$tmp/env"; }
tunnel_kept() { grep -qx "TUNNEL_ID=abc" "$tmp/env"; }
up_called_with() { grep -q "^$1 .* up -d" "$tmp/log"; }
no_up() { ! grep -q " up -d" "$tmp/log"; }
no_pull() { ! grep -q " pull" "$tmp/log"; }
said() { grep -q "$1" "$tmp/out"; }

setup
assert_pass "v 없는 버전은 사용법 오류(exit 2)" test "$(code 0.0.1)" = 2

setup
assert_fail "존재하지 않는 버전은 실패" d FAKE_PULL_FAIL=1 v9.9.9
assert_pass "pull 실패 시 실행 중 버전 유지" version_is 0.0.1
assert_pass "pull 실패 시 up 호출 안 함" no_up

setup
assert_fail "헬스 실패 시 배포 실패" d FAKE_BAD_VERSION=0.0.2 v0.0.2
assert_pass "헬스 실패 시 새 버전으로 up 시도" up_called_with 0.0.2
assert_pass "헬스 실패 시 이전 버전으로 롤백" up_called_with 0.0.1
assert_pass "롤백 성공을 알림" said "롤백 완료: v0.0.1"
assert_pass "헬스 실패 시 기록 버전 유지" version_is 0.0.1

setup
assert_fail "up 실패 시 배포 실패" d FAKE_UP_FAIL_VERSION=0.0.2 v0.0.2
assert_pass "up 실패 시에도 이전 버전으로 롤백" up_called_with 0.0.1
assert_pass "up 실패 후 롤백 성공을 알림" said "롤백 완료: v0.0.1"
assert_pass "up 실패 시 기록 버전 유지" version_is 0.0.1

setup
assert_fail "롤백도 비정상이면 실패" d FAKE_HEALTH_FAIL=1 v0.0.2
assert_pass "롤백 실패를 명확히 알림" said "ROLLBACK FAILED"

setup; printf 'TUNNEL_ID=abc\n' > "$tmp/env"
assert_fail "이전 버전 없이 헬스 실패" d FAKE_BAD_VERSION=0.0.2 v0.0.2
assert_pass "롤백할 버전이 없음을 알림" said "롤백할 이전 버전이 없습니다"

setup
assert_pass "정상 배포 성공" d v0.0.2
assert_pass "정상 배포 후 기록 버전 갱신" version_is 0.0.2
assert_pass "정상 배포 후 다른 변수 유지" tunnel_kept

setup
assert_fail "체크아웃이 태그와 다르면 실패" d SKIP_REV_CHECK=0 v9.9.8
assert_pass "리비전 불일치 시 pull 안 함" no_pull
assert_pass "리비전 불일치를 알림" said "v9.9.8"
finish

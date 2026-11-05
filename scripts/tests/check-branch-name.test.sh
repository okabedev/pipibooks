#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
check() { bash "$REPO_ROOT/scripts/check-branch-name.sh" "$@"; }

for b in main develop feature/12-landing-page release/0.1.0 hotfix/0.1.1; do
  assert_pass "허용: $b" check "$b"
done
for b in feature/landing feature/12-Landing release/v0.1.0 fix/12-x; do
  assert_fail "거부: $b" check "$b"
done

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
git -C "$tmp" init -q -b wip/bad
git -C "$tmp" -c user.name=t -c user.email=t@t commit -q --allow-empty -m init
assert_fail "현재 브랜치 검사: wip/bad" bash -c "cd '$tmp' && bash '$REPO_ROOT/scripts/check-branch-name.sh'"
git -C "$tmp" checkout -q --detach
assert_pass "분리된 HEAD는 통과" bash -c "cd '$tmp' && bash '$REPO_ROOT/scripts/check-branch-name.sh'"

# 최종 리뷰 Minor: pre-push는 현재 브랜치가 아니라 푸시 대상 ref를 검사한다.
Z=0000000000000000000000000000000000000000
pp() { printf '%s\n' "$1" | bash "$REPO_ROOT/scripts/check-branch-name.sh" --pre-push; }
assert_fail "잘못된 이름으로 푸시 거부" pp "refs/heads/main abc refs/heads/wip-bad $Z"
assert_pass "올바른 이름으로 푸시 허용" pp "refs/heads/x abc refs/heads/feature/2-ok $Z"
assert_pass "태그 푸시는 브랜치 검사 제외" pp "refs/tags/v1.0.0 abc refs/tags/v1.0.0 $Z"
assert_pass "브랜치 삭제는 허용" pp "(delete) $Z refs/heads/wip-bad abc"
assert_pass "잘못된 현재 브랜치에서도 태그 푸시 허용" bash -c "cd '$tmp' && git checkout -q -B wip/bad && printf 'refs/tags/v1 a refs/tags/v1 $Z\n' | bash '$REPO_ROOT/scripts/check-branch-name.sh' --pre-push"
finish

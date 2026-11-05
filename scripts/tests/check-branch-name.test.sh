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
finish

#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
cd "$REPO_ROOT"

lint() { printf '%s\n' "$1" | pnpm exec commitlint; }
subject_of_len() { local s=""; for ((i = 0; i < $1; i++)); do s+="가"; done; printf '%s' "$s"; }

assert_pass "정상: feat(web)" lint "feat(web): 랜딩 페이지 추가"
assert_pass "정상: squash 번호 포함" lint "docs(docs): 계획 추가 (#12)"
assert_pass "제목 50자 한글" lint "feat(web): $(subject_of_len 50)"
assert_fail "scope 없음" lint "feat: 범위 없음"
assert_fail "허용되지 않은 scope" lint "feat(foo): 잘못된 범위"
assert_fail "허용되지 않은 type" lint "wip(web): 잘못된 타입"
assert_fail "마침표로 끝남" lint "feat(web): 마침표."
assert_fail "제목 51자 한글" lint "feat(web): $(subject_of_len 51)"
finish

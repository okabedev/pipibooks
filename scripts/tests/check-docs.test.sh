#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

lines() { local n=$1 f=$2; mkdir -p "$(dirname "$f")"; for ((i = 1; i <= n; i++)); do echo "line $i"; done > "$f"; }
fixture() { tmp="$(mktemp -d)"; }
check() { bash "$REPO_ROOT/scripts/check-docs.sh" "$1"; }

fixture; lines 199 "$tmp/CLAUDE.md"
assert_pass "CLAUDE.md 199줄 통과" check "$tmp"; rm -rf "$tmp"

fixture; lines 200 "$tmp/CLAUDE.md"
assert_fail "CLAUDE.md 200줄 거부" check "$tmp"; rm -rf "$tmp"

fixture; lines 200 "$tmp/sub/CLAUDE.md"; truncate -s -1 "$tmp/sub/CLAUDE.md"
assert_fail "하위 CLAUDE.md 200줄(끝 줄바꿈 없음) 거부" check "$tmp"; rm -rf "$tmp"

fixture; lines 301 "$tmp/docs/a.md"
assert_fail "docs 문서 301줄 거부" check "$tmp"; rm -rf "$tmp"

fixture; lines 300 "$tmp/docs/a.md"
assert_pass "docs 문서 300줄 통과" check "$tmp"; rm -rf "$tmp"

fixture; lines 1000 "$tmp/docs/plans/p.md"
assert_pass "docs/plans 제외" check "$tmp"; rm -rf "$tmp"

fixture; lines 500 "$tmp/node_modules/x/CLAUDE.md"
assert_pass "node_modules 제외" check "$tmp"; rm -rf "$tmp"
finish

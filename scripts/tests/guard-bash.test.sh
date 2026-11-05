#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
GUARD="$REPO_ROOT/.claude/hooks/guard-bash.mjs"

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
git -C "$tmp" init -q -b develop
git -C "$tmp" -c user.name=t -c user.email=t@t commit -q --allow-empty -m init

on() { git -C "$tmp" checkout -q -B "$1"; }
# guard <command> : 훅 JSON을 stdin으로 넣는다. exit 0=허용, 2=차단
guard() {
  local exit_code
  node -e 'process.stdout.write(JSON.stringify({tool_name:"Bash",cwd:process.argv[1],tool_input:{command:process.argv[2]}}))' "$tmp" "$1" \
    | node "$GUARD" >/dev/null 2>&1
  exit_code=$?
  [ "$exit_code" -eq 0 ]
}
blocked() { ! guard "$1" && [ "$(guard_code "$1")" = 2 ]; }
guard_code() {
  node -e 'process.stdout.write(JSON.stringify({tool_name:"Bash",cwd:process.argv[1],tool_input:{command:process.argv[2]}}))' "$tmp" "$1" \
    | node "$GUARD" >/dev/null 2>&1
  echo $?
}

on develop
assert_pass "develop에서 commit -am 차단" blocked 'git commit -am "x"'
on main
assert_pass "main에서 commit 차단" blocked 'git commit -m "x"'
on feature/3-docs
assert_pass "feature 브랜치 커밋 허용" guard 'git commit -m "docs(docs): 추가"'
assert_pass "--no-verify 차단" blocked 'git commit --no-verify -m x'
assert_pass "--force push 차단" blocked 'git push --force origin feature/3-docs'
assert_pass "-f push 차단" blocked 'git push -f'
assert_pass "--force-with-lease 허용" guard 'git push --force-with-lease origin feature/3-docs'
assert_pass "일반 명령 허용" guard 'ls -la'
assert_pass "git -C 경로 commit은 그 저장소 브랜치로 판정" guard "git -C $tmp commit -m x"
on develop
assert_pass "develop에서 git -C 경로 commit 차단" blocked "git -C $tmp commit -m x"
finish

#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
GUARD="$REPO_ROOT/.claude/hooks/guard-bash.mjs"

tmp="$(mktemp -d)"; tmp2="$(mktemp -d)"; trap 'rm -rf "$tmp" "$tmp2"' EXIT
git -C "$tmp" init -q -b develop
git -C "$tmp" -c user.name=t -c user.email=t@t commit -q --allow-empty -m init
git -C "$tmp2" init -q -b feature/9-other
git -C "$tmp2" -c user.name=t -c user.email=t@t commit -q --allow-empty -m init

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

# 최종 리뷰 Important 1: 우회 경로
on feature/3-docs
assert_pass "commit -n(--no-verify 단축) 차단" blocked 'git commit -n -m x'
assert_pass "commit -nm 결합 플래그 차단" blocked 'git commit -nm x'
assert_pass "LEFTHOOK=0 차단" blocked 'LEFTHOOK=0 git commit -m x'
assert_pass "core.hooksPath 변경 차단" blocked 'git -c core.hooksPath=/dev/null commit -m x'
assert_pass "push -uf 결합 플래그 차단" blocked 'git push -uf origin feature/3-docs'
assert_pass "feature 브랜치 merge 허용" guard 'git merge feature/4-x'
assert_pass "git log -n 5 허용" guard 'git log -n 5'
on develop
assert_pass "env git commit 차단" blocked 'env git commit -m x'
assert_pass "bash -c 안의 commit 차단" blocked 'bash -c "git commit -m x"'
assert_pass "서브셸 commit 차단" blocked '(git commit -m x)'
assert_pass "develop에서 merge 차단" blocked 'git merge feature/4-x'
assert_pass "develop에서 cherry-pick 차단" blocked 'git cherry-pick abc123'
assert_pass "-c 옵션 뒤 commit도 develop이면 차단" blocked 'git -c user.name=x commit -m x'

# 최종 리뷰 Important 2: 오탐
assert_pass "cd로 feature 저장소 이동 후 commit 허용" guard "cd $tmp2 && git commit -m x"
assert_pass "따옴표 안의 git commit 문자열 허용" guard 'echo "a && git commit"'
assert_pass "heredoc 본문의 git commit 문자열 허용" guard "$(printf 'cat > f <<EOF\ngit commit -m x\nEOF')"
assert_pass "grep 인자의 --no-verify 허용" guard 'grep -rn -- --no-verify docs'
finish

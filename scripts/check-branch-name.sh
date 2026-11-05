#!/usr/bin/env bash
# Git Flow 브랜치 이름 검사.
#   check-branch-name.sh <브랜치>   : 그 이름을 검사
#   check-branch-name.sh            : 현재 브랜치를 검사(분리된 HEAD면 통과)
#   check-branch-name.sh --pre-push : stdin의 pre-push ref 목록에서 푸시 대상 브랜치를 검사(태그·삭제는 제외)
set -euo pipefail

re='^(main|develop|feature/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*|release/[0-9]+\.[0-9]+\.[0-9]+|hotfix/[0-9]+\.[0-9]+\.[0-9]+)$'
zero='^0+$'

reject() {
  echo "잘못된 브랜치 이름: $1" >&2
  echo "허용: main, develop, feature/<이슈번호>-<slug>, release/x.y.z, hotfix/x.y.z" >&2
  exit 1
}

if [ "${1:-}" = "--pre-push" ]; then
  # 각 줄: <local ref> <local sha> <remote ref> <remote sha>
  while read -r _local_ref local_sha remote_ref _remote_sha; do
    [ -z "${remote_ref:-}" ] && continue
    [[ "$local_sha" =~ $zero ]] && continue # 삭제
    case "$remote_ref" in
      refs/heads/*) name="${remote_ref#refs/heads/}"; [[ "$name" =~ $re ]] || reject "$name" ;;
      *) ;; # 태그 등은 검사하지 않는다
    esac
  done
  exit 0
fi

branch="${1:-$(git symbolic-ref --short -q HEAD || true)}"
[ -z "$branch" ] && exit 0
[[ "$branch" =~ $re ]] || reject "$branch"

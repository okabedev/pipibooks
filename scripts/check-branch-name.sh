#!/usr/bin/env bash
# Git Flow 브랜치 이름 검사. 인자가 없으면 현재 브랜치를 검사하고, 분리된 HEAD면 통과한다.
set -euo pipefail
branch="${1:-$(git symbolic-ref --short -q HEAD || true)}"
[ -z "$branch" ] && exit 0

re='^(main|develop|feature/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*|release/[0-9]+\.[0-9]+\.[0-9]+|hotfix/[0-9]+\.[0-9]+\.[0-9]+)$'
if [[ "$branch" =~ $re ]]; then exit 0; fi

echo "잘못된 브랜치 이름: $branch" >&2
echo "허용: main, develop, feature/<이슈번호>-<slug>, release/x.y.z, hotfix/x.y.z" >&2
exit 1

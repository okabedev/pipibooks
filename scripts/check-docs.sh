#!/usr/bin/env bash
# 문서 줄 수 검사: CLAUDE.md는 199줄 이하, docs/**/*.md는 300줄 이하(docs/plans 제외).
# 사용법: scripts/check-docs.sh [root=.]
set -euo pipefail
root="${1:-.}"
cd "$root"

prune=(-path ./node_modules -o -path '*/node_modules' -o -path ./.git -o -path '*/build' -o -path '*/.next' -o -path ./.superpowers)
status=0

count() { awk 'END{print NR}' "$1"; }

while IFS= read -r -d '' f; do
  n=$(count "$f")
  if [ "$n" -gt 199 ]; then echo "${f#./}: $n lines (max 199)"; status=1; fi
done < <(find . \( "${prune[@]}" \) -prune -o -type f -name CLAUDE.md -print0)

if [ -d docs ]; then
  while IFS= read -r -d '' f; do
    n=$(count "$f")
    if [ "$n" -gt 300 ]; then echo "${f#./}: $n lines (max 300)"; status=1; fi
  done < <(find ./docs -path ./docs/plans -prune -o -type f -name '*.md' -print0)
fi

exit "$status"

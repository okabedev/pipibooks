#!/usr/bin/env bash
# GitHub 저장소 설정(라벨·마일스톤·이슈·룰셋·보안)을 멱등하게 적용한다.
# 사용법: bash scripts/github/setup-repo.sh
set -euo pipefail

REPO="${REPO:-okabedev/pipibooks}"

TYPES=(feat fix docs style refactor perf test build ci chore revert)
SCOPES=(core-api catalog-worker event-collector libs web app shared infra docs repo)
MILESTONES=("0 기반" "1 코어 MVP" "2 독서 기록·통계" "3 모바일 앱" "4 본격 추천")
M0_ISSUES=(
  "루트 도구 체인과 커밋·브랜치·문서 검사"
  "문서 골격과 템플릿"
  "Claude Code 일관성 장치"
  "services 골격 (Gradle 멀티 프로젝트와 세 서비스)"
  "clients/web 골격 (임시 랜딩)"
  "컨테이너 골격 (compose, DB 초기화, 스모크 테스트)"
  "CI와 릴리스 파이프라인"
  "호스트 준비, 배포 스크립트, v0.0.1 공개"
)
# 룰셋 필수 상태 검사. 비어 있으면 상태 검사 규칙을 넣지 않는다.
REQUIRED_CHECKS=()

log() { printf '%s\n' "$*"; }

setup_labels() {
  local existing name
  existing="$(gh label list --repo "$REPO" --limit 200 --json name --jq '.[].name')"
  for t in "${TYPES[@]}"; do
    name="type:$t"
    if grep -qxF "$name" <<<"$existing"; then log "skip label $name"; else
      gh label create "$name" --repo "$REPO" --color 1d76db >/dev/null && log "create label $name"; fi
  done
  for s in "${SCOPES[@]}"; do
    name="scope:$s"
    if grep -qxF "$name" <<<"$existing"; then log "skip label $name"; else
      gh label create "$name" --repo "$REPO" --color 0e8a16 >/dev/null && log "create label $name"; fi
  done
}

setup_milestones() {
  local existing
  existing="$(gh api "repos/$REPO/milestones?state=all&per_page=100" --jq '.[].title')"
  for m in "${MILESTONES[@]}"; do
    if grep -qxF "$m" <<<"$existing"; then log "skip milestone $m"; else
      gh api "repos/$REPO/milestones" -f title="$m" >/dev/null && log "create milestone $m"; fi
  done
}

setup_issues() {
  local existing
  existing="$(gh issue list --repo "$REPO" --state all --limit 500 --json title --jq '.[].title')"
  for i in "${M0_ISSUES[@]}"; do
    if grep -qxF "$i" <<<"$existing"; then log "skip issue $i"; else
      gh issue create --repo "$REPO" --title "$i" --milestone "${MILESTONES[0]}" \
        --body "서브프로젝트 0 구현 계획(docs/plans/2026-11-05-subproject-0-foundation.md)의 Task." >/dev/null
      log "create issue $i"
    fi
  done
}

ruleset_json() {
  local checks=""
  if ((${#REQUIRED_CHECKS[@]})); then
    local ctx=()
    for c in "${REQUIRED_CHECKS[@]}"; do ctx+=("{\"context\":\"$c\"}"); done
    checks=$(IFS=,; printf ',{"type":"required_status_checks","parameters":{"strict_required_status_checks_policy":false,"required_status_checks":[%s]}}' "${ctx[*]}")
  fi
  cat <<JSON
{
  "name": "protect-main-develop",
  "target": "branch",
  "enforcement": "active",
  "bypass_actors": [],
  "conditions": {"ref_name": {"include": ["refs/heads/main", "refs/heads/develop"], "exclude": []}},
  "rules": [
    {"type": "deletion"},
    {"type": "non_fast_forward"},
    {"type": "pull_request", "parameters": {
      "required_approving_review_count": 0,
      "dismiss_stale_reviews_on_push": false,
      "require_code_owner_review": false,
      "require_last_push_approval": false,
      "required_review_thread_resolution": false
    }}${checks}
  ]
}
JSON
}

setup_ruleset() {
  local id
  id="$(gh api "repos/$REPO/rulesets" --jq '.[] | select(.name=="protect-main-develop") | .id')"
  if [[ -n "$id" ]]; then
    ruleset_json | gh api -X PUT "repos/$REPO/rulesets/$id" --input - >/dev/null && log "update ruleset protect-main-develop"
  else
    ruleset_json | gh api -X POST "repos/$REPO/rulesets" --input - >/dev/null && log "create ruleset protect-main-develop"
  fi
}

setup_merge_settings() {
  # squash 커밋 제목 = PR 제목(+ 번호), 본문 = 커밋 메시지 모음
  gh api -X PATCH "repos/$REPO" -f squash_merge_commit_title=PR_TITLE -f squash_merge_commit_message=COMMIT_MESSAGES \
    -F allow_rebase_merge=false -F allow_squash_merge=true -F allow_merge_commit=true -F delete_branch_on_merge=true >/dev/null
  log "apply merge settings"
}

setup_security() {
  gh api -X PATCH "repos/$REPO" --input - >/dev/null <<'JSON'
{"security_and_analysis": {"secret_scanning": {"status": "enabled"}, "secret_scanning_push_protection": {"status": "enabled"}}}
JSON
  log "apply secret scanning"
}

setup_labels
setup_milestones
setup_issues
setup_ruleset
setup_merge_settings
setup_security

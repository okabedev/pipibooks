---
name: start-feature
description: pipibooks에서 새 작업(기능·버그·문서)을 시작할 때 사용. 이슈 확인, develop 최신화, feature 브랜치 생성, 작업 규모별 절차 결정까지를 항상 같은 순서로 수행한다.
---

# 작업 시작 절차

1. **이슈 확인**: `gh issue list --state open`으로 해당 작업의 이슈를 찾는다. 없으면 `gh issue create`로 만든다(기능·버그 템플릿 형식, 마일스톤 지정, `type:`·`scope:` 라벨).
2. **develop 최신화**: `git switch develop && git pull --ff-only`
3. **브랜치 생성**: `git switch -c feature/<이슈번호>-<소문자-slug>` (예: `feature/23-shelf-status-api`)
4. **절차 결정**: 루트 `CLAUDE.md`의 "작업 규모별 절차" 표로 판단한다.
   - 사소함 → 바로 수정
   - 작은 변경 → 채팅으로 설계를 합의한 뒤 TDD
   - 큰 변경 → superpowers:brainstorming으로 스펙(`docs/specs/`), superpowers:writing-plans로 계획(`docs/plans/`)
   - 애매하면 더 무거운 절차
5. **관련 문서 읽기**: `CLAUDE.md` 문서 색인에서 해당 영역 문서만 읽는다.
6. 구현 후: 커밋 규칙(`docs/conventions/git.md`)대로 커밋 → `gh pr create --base develop`(PR 템플릿, `Closes #<번호>`) → CI `ci-ok` 통과 후 squash 머지

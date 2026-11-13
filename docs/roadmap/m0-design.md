# M0 설계

- 상태: 진행
- 브랜치: `feature/m0-design`
- 계획: [2026-11-14-m0-design.md](../superpowers/plans/2026-11-14-m0-design.md)
- 태그: 생략 ([0004](../decisions/0004-m0-release-skipped.md))

## 범위

`docs/` 구성, CLAUDE.md, 스킬, Git 레포와 브랜치

## 완료 기준

- [ ] `docs/` 구성이 [문서 규칙](../conventions/documentation.md)과 같다
- [ ] 원본 설계서의 모든 항목이 주제별 문서로 옮겨졌다
- [ ] CLAUDE.md가 200줄 이하이고 문서 안내를 담는다
- [ ] `pipibooks-milestone` 스킬이 있다
- [ ] GitHub 레포와 `main`·`develop` 브랜치가 있다 (세션 전 완료)

## 인계 메모

### 진행 상황
- 계획의 Task 1~6 완료. 브랜치 전체 리뷰를 받고 지적 사항을 반영함.

### 남은 작업
- 계획의 Task 7: `develop`으로 PR과 병합, 임시 메모 정리 (릴리스·태그는 생략)

### 진행 중에 내린 결정
- M0는 릴리스와 태그를 생략한다 ([0004](../decisions/0004-m0-release-skipped.md))
- 원본 설계서는 기록으로 보존한다 ([0005](../decisions/0005-design-document-preserved.md))

### 주의할 점
- M8·M9의 태그 번호는 정해지지 않았다. M8 시작 전에 사용자 승인이 필요하다.
- ISBN, KDC, JWT, API를 승인 없이 쓸 수 있는지 사용자 확인이 필요하다([축약어 목록](../decisions/abbreviations.md)).

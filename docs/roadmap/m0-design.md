# M0 설계

- 상태: 완료
- 브랜치: `feature/m0-design`
- 계획: [2026-11-14-m0-design.md](../superpowers/plans/2026-11-14-m0-design.md)
- 태그: 생략 ([0004](../decisions/0004-m0-release-skipped.md))

## 범위

`docs/` 구성, CLAUDE.md, 스킬, Git 레포와 브랜치

## 완료 기준

- [x] `docs/` 구성이 [문서 규칙](../conventions/documentation.md)과 같다
- [x] 원본 설계서의 모든 항목이 주제별 문서로 옮겨졌다
- [x] CLAUDE.md가 200줄 이하이고 문서 안내를 담는다
- [x] `pipibooks-milestone` 스킬이 있다
- [x] GitHub 레포와 `main`·`develop` 브랜치가 있다 (세션 전 완료)

## 인계 메모

### 진행 상황
- 계획의 모든 Task 완료. 브랜치 전체 리뷰를 받고 지적 사항을 반영함. `develop`에 PR로 병합함.

### 남은 작업
- 없음. 릴리스와 태그는 생략한다.

### 진행 중에 내린 결정
- M0는 릴리스와 태그를 생략한다 ([0004](../decisions/0004-m0-release-skipped.md))
- 원본 설계서는 기록으로 보존한다 ([0005](../decisions/0005-design-document-preserved.md))
- v1.0.0 뒤의 태그는 M8 `v1.1.0`, M9 `v1.2.0` ([0006](../decisions/0006-tags-after-v1.md))
- API, ISBN, JWT, KDC를 축약어로 승인 ([축약어 목록](../decisions/abbreviations.md))
- 나의 책장은 `bookcase`로 쓴다 ([이름 짓기와 언어](../conventions/naming.md#용어))

### 주의할 점
- 다음은 M1 기반이다. 새 세션에서 `pipibooks-milestone` 스킬로 시작한다.
- M1이 첫 릴리스(`v0.1.0`)다. `docs/conventions/git.md`의 릴리스 절차를 처음 실제로 쓴다.

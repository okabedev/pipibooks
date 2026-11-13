# Git 규칙

Git 규칙과 이름 짓기 규칙은 따로 스킬로 만들지 않는다. `commit-commands`, `superpowers:finishing-a-development-branch`와 역할이 겹치기 때문이다. 대신 이 기존 스킬들이 이 문서를 따른다.

## 원격 저장소

GitHub 공개 레포 `pipibooks`를 원격 저장소로 쓴다.

## 브랜치 (Git Flow)

`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`.

- 작업은 `feature/*` 브랜치에서 하고, PR을 만들어 `develop`에 병합한다.
- 마일스톤 작업 브랜치 이름은 `feature/m<N>-<이름>`이다. 예: `feature/m1-foundation`. 이름은 [로드맵](../roadmap/README.md)의 마일스톤 파일 이름과 같다.
- 마일스톤이 끝나면 `release/*` 브랜치를 거쳐 `main`에 병합하고 태그를 단다.

## 태그

- 마일스톤마다 `v0.N.0`이다. 1차 목표를 마치면 `v1.0.0`이다.
- 예외: M0는 릴리스와 태그를 생략한다. 첫 태그는 M1의 `v0.1.0`이다([0004](../decisions/0004-m0-release-skipped.md)).

## 릴리스 절차

1. `develop`에서 `release/0.N.0` 생성
2. `release/0.N.0` → `main` PR, 병합
3. `main`에 태그 `v0.N.0`을 달고 push
4. `main`을 `develop`에 다시 병합

push, PR 병합, 태그 push는 외부에 공개되는 작업이므로 하기 전에 사용자에게 확인한다.

## 커밋

- Conventional Commits 형식이다. 예: `feat(api): 카카오 로그인 추가`.
- scope는 `api`, `worker`, `core`, `client`, `support`, `web`, `mobile`, `infra`, `docs` 중 하나다.
- 요약은 한국어로 쓴다([이름 짓기와 언어](naming.md#언어)).

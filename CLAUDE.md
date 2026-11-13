# 피피북스(pipibooks)

책을 평가하고 나만의 책장을 꾸리는 포트폴리오용 풀 스택 서비스다. 백엔드(Spring Boot 멀티 모듈), 웹(React), 모바일(Expo)을 한 레포에 둔다. 전체 문서는 [docs/README.md](docs/README.md)에서 찾는다.

## 세션 시작 순서

1. [로드맵](docs/roadmap/README.md)에서 상태가 `진행`인 마일스톤을 찾는다. 없으면 첫 번째 `대기` 마일스톤이다.
2. 그 마일스톤 파일을 읽는다.
3. 마일스톤 파일의 인계 메모를 읽는다.
4. `pipibooks-milestone` 스킬로 마일스톤을 시작하거나 재개한다.

세션을 끝내기 전에는 인계 메모를 갱신한다.

## 규칙 요약

### 승인이 필요한 결정
- 정책이 바뀌는 결정과 새 축약어는 반드시 사용자의 승인을 받는다.
- 진행 중에 생기면 멈추고 묻는다. 임시로 정하고 넘어가지 않는다. 승인을 받으면 `docs/decisions/`에 기록한다.
- 테이블 설계, API 형식, 화면 세부 디자인, 클래스 구성, 타임아웃·토큰 수명·스케줄 주기 값은 구현자가 정한다.
- 원문: [결정 방식](docs/conventions/decision-making.md)

### 이름 짓기와 언어
- 가독성을 최우선한다. 의미를 흐리는 축약은 하지 않는다.
- 축약어는 승인된 것만 쓴다(현재 NLK 하나, ISBN·KDC·JWT·API 같은 고유 명사는 예외).
- 식별자는 영어로, 주석·문서·커밋 요약은 한국어로 쓴다.
- 원문: [이름 짓기와 언어](docs/conventions/naming.md), [축약어 목록](docs/decisions/abbreviations.md)

### 문서
- CLAUDE.md는 200줄 이하, 나머지 문서는 한 파일 한 주제로 300줄 정도까지 쓴다.
- 설계와 결정이 바뀌면 같은 PR 안에서 관련 문서도 고친다.
- 원본 설계서(`docs/superpowers/specs/`)는 기록이므로 고치지 않는다. 기준은 주제별 문서다.
- 원문: [문서 규칙](docs/conventions/documentation.md)

### Git
- Git Flow: `feature/*`에서 작업하고 PR로 `develop`에 병합한다. 마일스톤이 끝나면 `release/*`를 거쳐 `main`에 병합하고 `v0.N.0` 태그를 단다(M0는 생략).
- 커밋은 Conventional Commits, scope는 `api`, `worker`, `core`, `client`, `support`, `web`, `mobile`, `infra`, `docs` 중 하나다.
- push, PR 병합, 태그 push 전에는 사용자에게 확인한다.
- 원문: [Git 규칙](docs/conventions/git.md)

### 비밀 값
- API 키와 토큰은 `.env`로만 다루고 문서나 커밋에 남기지 않는다. `.env.example`만 커밋한다.

### 구조에서 헷갈리기 쉬운 것
- `services/app`, `services/client`는 루트의 `clients/`와 다르다. 문서에서는 항상 경로로 부른다.
- 의존 방향: `app/api`, `app/worker` → `core` → `client` → `support`, 그리고 `core` → `support`.
- 원문: [레포 구조](docs/architecture/repository-layout.md), [백엔드 모듈](docs/architecture/backend-modules.md)

## 문서 안내

| 무엇을 찾을 때 | 문서 |
|---|---|
| 기능과 정책 | [docs/product/](docs/README.md#제품) |
| 구조와 기술 스택 | [docs/architecture/](docs/README.md#구조) |
| 규칙 | [docs/conventions/](docs/README.md#규칙) |
| 결정 기록 | [docs/decisions/README.md](docs/decisions/README.md) |
| 마일스톤과 인계 메모 | [docs/roadmap/README.md](docs/roadmap/README.md) |

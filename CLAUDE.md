# pipibooks

비상업 도서 추천 서비스. 쇼츠·릴스처럼 세로로 넘기는 피드에 정적 캐러셀 카드를 보여 주고, 위시·독서 기록·통계를 제공한다. 웹(Next.js)을 먼저 출시하고 앱(Expo)으로 확장한다. 백엔드는 Kotlin + Spring Boot 모듈러 모놀리스와 분리 컴포넌트 2개, 배포는 로컬 Docker + Cloudflare Tunnel.

## 디렉토리 맵

| 경로 | 내용 |
|---|---|
| `services/` | Gradle 멀티 프로젝트: `core`, `catalog-worker`, `event-collector`, `libs/` |
| `clients/` | pnpm 워크스페이스: `web/`(Next.js), `app/`(Expo), `packages/` |
| `infra/` | compose, cloudflared, Postgres 초기화, 배포 스크립트, env 예시 |
| `docs/` | 스펙, 계획, ADR, 참조 문서 |
| `scripts/` | 저장소 공통 검사 스크립트와 테스트 |
| `.claude/` | Claude 훅과 프로젝트 스킬 |

## 고정 워크플로

모든 세션은 같은 절차를 따른다. 즉흥적으로 다른 방식을 쓰지 않는다.

- **기능 개발**: 이슈 → `develop`에서 `feature/<이슈번호>-<slug>` → (필요 시) 스펙·계획 → TDD 구현 → 셀프 리뷰 → PR → `develop`에 squash 머지
- **버그 수정**: 재현 테스트 먼저 → 수정 → PR
- **문서 변경**: 설계가 바뀌면 같은 PR에서 `docs/` 또는 ADR을 갱신한다
- **새 패턴 도입**: 컨벤션에 없는 라이브러리·구조·규칙은 ADR을 먼저 쓴다

## 작업 규모별 절차

| 규모 | 예 | 절차 |
|---|---|---|
| 사소함 | 문서 오탈자, 설정 값 변경 | 브랜치 → 바로 PR |
| 작은 변경 | 기존 흐름 안의 필드 추가, 작은 엔드포인트 | 채팅으로 설계 합의 → TDD → PR |
| 큰 변경 | 새 기능, 새 모듈, 구조·인터페이스 변경 | 스펙(`docs/specs/`) → 계획(`docs/plans/`) → 구현 → PR |

판단이 애매하면 더 무거운 절차를 택한다.

## 커밋·브랜치 규칙 (상세: `docs/conventions/git.md`)

- 커밋·PR 제목: `<type>(<scope>): <한국어 제목>` — 제목 50자 이내, 마침표 없음, scope 필수
- type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- scope: `core` `catalog-worker` `event-collector` `libs` `web` `app` `shared` `infra` `docs` `repo`
- 본문에 무엇을·왜를 쓰고, 푸터에 `Refs: #<이슈번호>`
- 브랜치: `feature/<번호>-<slug>`, `release/x.y.z`, `hotfix/x.y.z`
- PR 제목이 squash 커밋 메시지가 되므로 PR 제목도 같은 규칙을 따른다
- 커밋 작성자는 저장소 로컬 설정(noreply 주소)을 그대로 쓴다

## 자주 쓰는 명령

| 명령 | 용도 |
|---|---|
| `pnpm install` | 루트 도구 설치(lefthook 훅 자동 설치) |
| `pnpm test:scripts` | `scripts/` 검사 스크립트 테스트 |
| `pnpm check:docs` | 문서 줄 수 검사 |
| `pnpm format:check` | Prettier 검사(CI와 동일) |
| `bash scripts/github/setup-repo.sh` | GitHub 라벨·마일스톤·룰셋 재적용 |
| `cd services && ./gradlew build` | 백엔드 빌드·테스트·정적 분석 (상세: `services/CLAUDE.md`) |
| `pnpm --filter web test` | 웹 단위 테스트 (상세: `clients/CLAUDE.md`) |
| `bash infra/scripts/build-local.sh && bash infra/scripts/smoke-test.sh` | 컨테이너 통합 스모크 테스트 (상세: `infra/CLAUDE.md`) |

## 문서 색인 (필요할 때만 읽는다)

| 문서 | 언제 읽는가 |
|---|---|
| `docs/specs/2026-11-05-architecture-design.md` | 전체 아키텍처·결정의 근거가 필요할 때 |
| `docs/specs/2026-11-06-subproject-1-core-mvp-design.md` | 서브프로젝트 1(코어 MVP) 기능을 설계·구현할 때 |
| `docs/plans/` | 진행 중인 서브프로젝트의 Task를 실행할 때 |
| `docs/architecture/adr/README.md` | 결정의 이유를 확인하거나 새 결정을 기록할 때 |
| `docs/architecture/overview.md` | 컴포넌트 책임·통신·스키마 소유권을 확인할 때 |
| `docs/domain/model.md` | 엔티티·테이블·공통 데이터 규칙이 필요할 때 |
| `docs/domain/feed.md` | 피드 점수·이벤트 수집을 다룰 때 |
| `docs/conventions/git.md` | 브랜치·커밋·PR·릴리스 절차가 필요할 때 |
| `docs/conventions/api.md` | REST API를 추가·변경할 때 |
| `docs/ops/deploy.md` | 호스트 준비·배포·롤백·운영 확인이 필요할 때 |
| `.claude/skills/start-feature/` | 새 작업을 시작할 때(스킬 `start-feature`) |
| `.claude/skills/release/` | 릴리스할 때(스킬 `release`) |

## 문서 규칙

- `CLAUDE.md`(루트와 하위)는 199줄 이하, `docs/` 참조 문서는 300줄 이하(`docs/plans/` 제외). `pnpm check:docs`와 pre-commit 훅이 검사한다.
- `CLAUDE.md`에는 참조 문서 내용을 복사하지 않고 색인만 둔다.
- 결정의 이유는 ADR에, 규칙은 지침 문서에 쓴다.

## 금지 사항

- `main`·`develop`에 직접 커밋·푸시
- `--no-verify`·`-n`·`LEFTHOOK=0`·`core.hooksPath` 등 훅 우회, 강제 푸시, `main`·`develop`에서의 commit·merge (`.claude/hooks/guard-bash.mjs`가 차단. `main`·`develop` 직접 푸시는 GitHub 룰셋이 막는다)
- 시크릿(`*.env`, 인증 정보 JSON)을 저장소에 커밋
- 다른 컴포넌트가 소유한 DB 스키마에 쓰기
- Flyway 마이그레이션 없이 DB 스키마 변경

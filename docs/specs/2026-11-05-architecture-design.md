# pipibooks 전체 아키텍처 설계

- 작성일: 2026-11-05
- 상태: 검토 대기
- 관련 ADR: `docs/architecture/adr/0001` ~ `0010`

## 1. 목표와 범위

**서비스**: 비상업 도서 추천 서비스. 쇼츠·릴스처럼 세로로 넘기는 피드에 정적 캐러셀 카드(가로 슬라이드)를 보여 주고, 위시·독서 기록·통계 기능을 제공한다.

**목표**
- 1인이 운영할 수 있는 웹 서비스를 `pipibooks.com`에 공개하고, 이후 모바일 앱으로 확장한다.
- 세션(작업자)이 바뀌어도 개발 방식과 컨벤션이 달라지지 않는다.

**비목표 (지금은 하지 않음)**
- 영상 콘텐츠, 사용자 생성 카드(UGC), LLM 문구 생성, 협업 필터링 추천, 스테이징 환경, 메트릭 대시보드

## 2. 결정 요약

| 항목 | 결정 | ADR |
|---|---|---|
| 아키텍처 | 모듈러 모놀리스(core-api)와 분리 컴포넌트 2개(catalog-worker, event-collector), 공유 Postgres(스키마별 소유권) | 0001 |
| 백엔드 | 코어: Kotlin + Spring MVC + JPA(가상 스레드). 분리 컴포넌트: Kotlin + 코루틴 | 0002 |
| 클라이언트 | Next.js(웹)와 Expo(앱), pnpm 워크스페이스 공유 패키지 | 0003 |
| 인증 | 카카오·구글 소셜 로그인(앱 출시 때 Apple 추가), 자체 JWT | 0004 |
| 콘텐츠 | 국립중앙도서관 ISBN 서지정보 API **하나만** 사용. 규칙 기반 자동 카드 + 관리자 큐레이션 | 0005 |
| 추천 | 규칙 기반 개인화로 시작, MVP부터 이벤트 적재, 데이터가 쌓이면 본격 추천으로 확장 | 0006 |
| 배포 | 로컬 PC(WSL2 + Docker) + Cloudflare Tunnel, 이미지를 당겨오는 수동 배포 | 0007 |
| 서재 모델 | 위시와 독서 상태를 `shelf_item` 하나로 통합 | 0008 |
| 일관성 | 문서로 알리고 도구로 강제(훅, 린터, ArchUnit, CI, Claude 훅·스킬) | 0009 |
| 형상 관리 | Git Flow, Conventional Commits, GitHub Public 저장소 | 0010 |

## 3. 시스템 구성

```
Cloudflare (DNS, WAF, Access) ── Tunnel ── cloudflared (컨테이너, 유일한 진입점)
  pipibooks.com                  → web (Next.js)            :3000
  api.pipibooks.com ^/v1/events  → event-collector          :8081
  api.pipibooks.com              → core-api                 :8080
  그 외                           → 404

core-api ──(HTTP, 내부망)── catalog-worker ── 국립중앙도서관 API
   │  └──(XADD)──┐                │
   │        Redis (캐시 / 피드 캐시 / Streams)
   │             └──(XREADGROUP, 배치)── event-collector
PostgreSQL: core(core-api) / catalog(catalog-worker) / events(event-collector)
```

| 컴포넌트 | 책임 | 분리 이유 |
|---|---|---|
| core-api | 회원·인증, 카드 조회·발행, 피드, 서재, 통계, 관리자 API | - (코어 모놀리스) |
| catalog-worker | 국립중앙도서관 API 수집, ISBN 동기 import(내부 API), 카드 초안 생성 | 외부 API 호출 제한·재시도·장시간 배치가 API 응답 지연에 영향을 주지 않게 |
| event-collector | 행동 이벤트 수신, Redis Streams 버퍼링, `events`에 배치 적재 | 요청 빈도가 코어 API보다 훨씬 높음 |
| web | 웹 클라이언트, `/admin` 관리자 화면 | - |

core-api 내부 모듈은 `member`, `card`, `feed`, `library`, `stats`, `admin`이다. 모듈끼리는 서로의 공개 서비스 인터페이스만 호출하며, 이 규칙은 ArchUnit으로 검증한다.

## 4. 도메인·데이터 모델

### 4.1 스키마 소유권
| 스키마 | 쓰기 | 읽기 | DB 계정 |
|---|---|---|---|
| `catalog` | catalog-worker | core-api | `catalog_app`(catalog 쓰기), `core_app`(catalog 읽기) |
| `core` | core-api | - | `core_app` |
| `events` | event-collector | (나중에) 추천 워커 | `events_app` |

Flyway 마이그레이션은 스키마별로 위치를 나누고, 소유 컴포넌트만 실행한다.

### 4.2 엔티티
**catalog**
- `book`: `isbn13`(UNIQUE), 제목, 저자, 출판사, 출간일, KDC, 쪽수, 표지 URL, 소개, 목차, `fetched_at`. ISBN이 없는 도서는 수집하지 않는다.
- `card_draft`: `book_id`, 슬라이드 묶음(jsonb), 생성기 이름과 버전, 생성 시각

**core**
- `member`: 닉네임, 프로필 이미지, 역할(`USER`/`ADMIN`), 상태, `deleted_at`
- `social_account`: `member_id`, 제공자(`KAKAO`/`GOOGLE`/`APPLE`), 제공자 측 사용자 ID. (제공자, 사용자 ID)에 UNIQUE
- `refresh_token`: `member_id`, 토큰 해시, 기기 정보, `expires_at`, `revoked_at`
- `member_interest`: `member_id`, KDC 코드
- `card`: `book_id`, 출처(`AUTO`/`CURATED`), 상태(`DRAFT`/`PUBLISHED`/`HIDDEN`), `published_at`
- `card_slide`: `card_id`, 순서, 타입(`COVER`/`HOOK`/`QUOTE`/`INFO`/`FOR_WHOM`/`KEYWORDS`), `payload`(jsonb)
- `shelf_item`: `member_id`, `book_id`(같은 조합은 UNIQUE), 상태(`WISH`/`READING`/`FINISHED`/`DROPPED`), 시작일, 완독일, 별점(1~5), 한줄평
- `reading_log`: `shelf_item_id`, 날짜(`date`), 읽은 페이지(진척), 메모
- `quote`: `shelf_item_id`, 페이지, 본문

**events** (`occurred_at` 기준 월별 파티션)
- `user_event`: `member_id`(없을 수 있음), `anonymous_id`, `session_id`, `type`, `card_id`, `book_id`, `slide_index`, `dwell_ms`, `platform`, `app_version`, `occurred_at`, `received_at`
- 클라이언트가 보내는 이벤트: `CARD_IMPRESSION`, `SLIDE_VIEW`, `CARD_DWELL`, `CARD_SKIP`, `CARD_SHARE`
- 서버(core-api)가 보내는 이벤트: `SHELF_ADD`, `STATUS_CHANGE`, `IDENTITY_LINK`(로그인 시 `anonymous_id`와 회원을 연결)
- 이벤트 페이로드와 Stream 키는 `services/libs/event-schema`에서 한 곳에 정의한다.

### 4.3 공통 규칙
- PK는 TSID(64비트, 시간순 정렬)를 쓰고, API에서는 문자열로 노출한다.
- 시각은 `timestamptz`(UTC)로 저장한다. 독서 기록의 날짜만 `date` 타입이다.
- 회원 탈퇴는 soft delete 후 개인정보를 익명화한다. 이벤트에서는 `member_id`를 끊는다.

## 5. 주요 흐름

### 5.1 로그인
1. 클라이언트가 OAuth 인가 코드를 받아 `POST /v1/auth/{provider}`로 보낸다.
2. core-api가 제공자에게 사용자 정보를 확인하고, 회원을 찾거나 새로 만든다.
3. Access 토큰(15분, JWT)과 Refresh 토큰(30일, 불투명 토큰이며 DB에는 해시만 저장)을 발급한다.
   - 웹은 Refresh 토큰을 `HttpOnly`, `Secure`, `SameSite=Lax` 쿠키로 받는다(도메인 `.pipibooks.com`).
   - 앱은 응답 본문으로 받아 SecureStore에 저장한다.
4. Refresh 토큰은 재발급할 때마다 교체한다. 이미 쓴 토큰이 다시 들어오면 해당 기기의 토큰을 모두 폐기한다.

### 5.2 도서 수집과 카드 생성 (catalog-worker)
**수집 대상을 고르는 경로**
- 신간 수집: 출간일 범위로 주기적으로 조회한다.
- 운영자 시드: 관리자가 ISBN이나 키워드로 등록한다.
- 사용자 유입: 사용자가 서재에 추가한 책

**카드 초안 생성**
- `CardDraftGenerator` 인터페이스로 만든다. 지금은 규칙 기반 구현을 쓰고, 나중에 LLM 구현으로 교체한다.
- 규칙 기반 구현이 만드는 슬라이드: `COVER`(표지·제목·저자), `HOOK`(소개글 핵심 문장), `INFO`(서지), `KEYWORDS`(목차·소개 키워드), `FOR_WHOM`(KDC별 템플릿)
- 소개글이 없는 도서는 카드 후보에서 제외한다. `QUOTE` 슬라이드는 큐레이션 전용이다.

**발행**
- 관리자가 `/admin`에서 초안을 검수·편집하고 발행한다. 발행하면 core-api가 초안을 `core.card`와 `core.card_slide`로 복사한다.

**외부 API 호출 규칙**
- 호출 제한을 지키고, 지수 백오프로 재시도한다. 실패한 항목은 재처리 큐에 넣는다.
- API 키는 worker만 가진다.

### 5.3 도서 검색과 서재 추가
```
GET  /v1/books/search?q=  core-api → catalog-worker 내부 API /internal/books/search (Redis 캐시 10분)
POST /v1/shelf {isbn}     core-api → catalog-worker POST /internal/books/import (동기, upsert)
                          → book_id 반환 → shelf_item 생성 → SHELF_ADD 이벤트 발행
```
- 국립중앙도서관 API 호출은 **catalog-worker만** 한다.
- 내부 API는 `internal` 네트워크에만 열고, 터널로는 노출하지 않는다.

### 5.4 피드 (`GET /v1/feed?cursor=`)
1. 후보: 발행된 카드 중 최근 노출했거나 이미 서재에 있는 책은 제외한다.
2. 점수: 관심 KDC 일치도 + 서재 도서 KDC 유사도 + 인기도(최근 7일 `SHELF_ADD` 수) + 신선도 + 무작위 항
   - 비로그인 사용자는 인기도, 신선도, 무작위 항만 쓴다.
3. 피드 세션별로 정렬된 ID 목록을 Redis에 30분 캐시하고, 20개씩 커서 페이지네이션으로 응답한다.
4. 점수 계산은 `FeedRanker` 인터페이스 뒤에 둔다. 본격 추천 단계에서는 미리 계산한 결과를 읽는 구현으로 교체한다.

### 5.5 이벤트 수집 (`POST /v1/events`, 최대 50건 배치)
1. 클라이언트가 이벤트를 모아서 보내고, 페이지를 벗어날 때는 `sendBeacon`으로 보낸다.
2. collector가 스키마를 검증한 뒤 `XADD`로 Redis Stream에 넣고, 바로 `202`로 응답한다.
3. 컨슈머 그룹이 최대 500건 또는 1초 단위로 모아 `events.user_event`에 배치 INSERT한다. 처리 후 ACK한다.
4. 실패한 이벤트는 Pending 목록에 남아 재처리된다.

### 5.6 통계
- MVP에서는 요청할 때 집계 쿼리를 실행하고 결과를 캐시한다.
- 지표: 월별·연도별 완독 수, 읽은 페이지, KDC 분포, 연속 기록일, 평균 별점, 위시에서 완독까지 걸린 기간
- 데이터가 많아지면 일 단위 집계 테이블로 전환한다.

## 6. API 공통 규칙
- 경로에 버전을 넣는다(`/v1/...`). 응답은 JSON이고, 필드는 camelCase로 쓴다.
- 에러는 RFC 9457 Problem Details 형식에 `code` 필드(예: `SHELF_ITEM_NOT_FOUND`)를 더한다.
- 목록은 커서 페이지네이션(`cursor`, `size`, `nextCursor`)을 쓴다.
- core-api는 springdoc으로 OpenAPI를 생성하고, `clients/packages/api-client`가 이 명세에서 TS 클라이언트를 생성한다. CI는 생성물이 최신인지 검사한다.

## 7. 배포·운영

### 7.1 호스트
- WSL2 배포판 안에 Docker 엔진을 직접 설치하고 systemd로 관리한다.
- 재부팅 후 자동 복구: Windows 작업 스케줄러가 부팅 시 WSL을 기동 → systemd가 Docker 서비스 시작 → compose `restart: unless-stopped`로 컨테이너 복구
- `.wslconfig`의 `vmIdleTimeout`으로 WSL 유휴 종료를 막는다. Windows 절전을 끄고, 업데이트 재부팅 시각을 새벽으로 지정한다.

### 7.2 컨테이너 (`infra/compose/compose.prod.yml`)
- 서비스: `cloudflared`, `web`, `core-api`, `event-collector`, `catalog-worker`, `postgres:18`, `redis`(AOF `everysec`), `backup`
- 네트워크는 `edge`(cloudflared와 공개 서비스)와 `internal`(DB, Redis, worker)로 나눈다. 호스트 포트는 열지 않는다. Postgres 관리 접속만 `127.0.0.1`에 바인딩한다.
- 컨테이너마다 `mem_limit`을 걸고, JVM에는 `-XX:MaxRAMPercentage`를 설정한다. 로그는 `json-file` 드라이버로 로테이션한다.
- 개발용 `compose.dev.yml`은 Postgres와 Redis만 띄운다.

### 7.3 Cloudflare
- 도메인은 Cloudflare Registrar에서 구매하는 것을 권장한다.
- 터널은 로컬 설정 방식(`infra/cloudflared/config.yml`을 저장소에서 관리)으로 운영한다. 인증 정보 파일은 저장소 밖에 둔다.
- `www`는 루트 도메인으로 리다이렉트한다. `/admin*`은 Cloudflare Access로 보호한다.
- 무료 WAF 요청 빈도 제한을 `/v1/events`와 `/v1/auth/*`에 건다. `/_next/static`은 엣지에 캐시한다.

### 7.4 빌드·배포
- `release/*`를 `main`에 머지하고 `vX.Y.Z` 태그를 찍으면, GitHub Actions가 이미지를 빌드해 `ghcr.io/okabedev/pipibooks-<컴포넌트>:X.Y.Z`로 푸시한다.
- 호스트에서 `infra/scripts/deploy.sh vX.Y.Z`를 실행한다: 이미지 pull → DB 백업 → `compose up -d` → 헬스체크. 헬스체크가 실패하면 직전 버전으로 자동 롤백한다.
- self-hosted runner는 쓰지 않는다. Public 저장소를 통해 집 PC가 원격 조작될 위험이 있기 때문이다.

### 7.5 시크릿
- `infra/env/*.env`는 gitignore 대상이고, `*.env.example`만 커밋한다.
- 시크릿 목록: OAuth 클라이언트 시크릿, JWT 서명 키, 국립중앙도서관 API 키, DB 계정별 비밀번호, R2 키, 백업 암호화 키

### 7.6 백업과 모니터링
- 매일 `pg_dump`(custom 포맷)를 실행하고 age로 암호화해 로컬과 Cloudflare R2에 저장한다. 보관 정책은 일간 7개, 주간 4개, 월간 6개다.
- 매월 임시 컨테이너에 복원하는 리허설을 한다.
- Redis는 백업하지 않는다.
- 외부 가동 확인은 UptimeRobot이나 Healthchecks.io로 한다(헬스 엔드포인트, 백업 하트비트).
- 각 서비스는 Actuator와 JSON 구조화 로그를 쓴다.

## 8. 저장소 구조
```
CLAUDE.md  .claude/(settings.json, skills/)  .github/(workflows/, 템플릿)
lefthook.yml  package.json  pnpm-workspace.yaml  .tool-versions
docs/  architecture/(overview.md, adr/)  domain/(model.md, feed.md)
       conventions/(git.md, api.md)  ops/(deploy.md, backup.md)  specs/  plans/
scripts/  check-docs.sh
services/  build-logic/  gradle/libs.versions.toml
           core-api/  catalog-worker/  event-collector/  libs/(common/, event-schema/)
clients/   web/  app/  packages/(api-client/, shared/)
infra/     compose/  cloudflared/  postgres/init/  backup/  scripts/  env/
```

## 9. 문서 체계와 세션 간 일관성

### 9.1 문서 체계
- **매 세션 로드하는 문서**: 루트 `CLAUDE.md`와 `services/`·`clients/`·`infra/`의 하위 `CLAUDE.md`. 모두 **200줄 미만**으로 둔다.
- **필요할 때 읽는 참조 문서**: `docs/` 아래 문서. 파일 하나가 300줄을 넘으면 주제별로 나눈다.
- `CLAUDE.md`에는 참조 문서의 내용을 복사하지 않고, "언제 읽을지"를 한 줄로 적은 색인만 둔다.
- 결정의 이유는 ADR에 남기고, 지침 문서에는 규칙만 적는다. 설계가 바뀌면 같은 PR에서 문서를 갱신한다.

### 9.2 고정된 개발 워크플로 (루트 `CLAUDE.md`에 명시)
- **기능 개발**: 이슈 → `feature/<번호>-<slug>` → (필요 시) 스펙·계획 → TDD → 셀프 리뷰 → PR → `develop`
- **버그 수정**: 재현 테스트 먼저 → 수정 → PR
- **금지**: `main`·`develop` 직접 커밋, 훅 우회(`--no-verify`), 컨벤션에 없는 새 패턴 도입(필요하면 ADR 먼저)

### 9.3 도구로 강제하는 규칙
| 규칙 | 도구 | 강제 시점 |
|---|---|---|
| 커밋 메시지 | commitlint | lefthook `commit-msg` 훅, CI |
| 브랜치 이름 | 훅 스크립트 | lefthook `pre-push` 훅 |
| Kotlin 스타일 | ktlint, detekt | `pre-commit` 훅, CI |
| TS 스타일 | ESLint, Prettier | `pre-commit` 훅, CI |
| 모듈 경계 | ArchUnit | 테스트, CI |
| 스키마 소유권 | DB 계정 권한 | 런타임 |
| 문서 줄 수 | `scripts/check-docs.sh` | `pre-commit` 훅, CI |
| 버전 고정 | 버전 카탈로그, lockfile, `.tool-versions` | 빌드 |

**Claude Code 전용 장치**
- `.claude/settings.json` 훅: 파일 수정 후 포매터를 자동 실행하고, `--no-verify` 같은 우회 명령을 차단한다.
- `.claude/skills/`: `start-feature`, `add-api-endpoint`, `add-migration`, `release`
- 템플릿: PR, 이슈, ADR, 스펙
- 첫 기능(위시리스트)을 "표준 예시"로 완성하고, `services/CLAUDE.md`에서 이 코드를 가리킨다.

## 10. 형상 관리

### 10.1 원격 저장소
- GitHub `okabedev/pipibooks`(Public). 기본 브랜치는 `develop`이다.
- 머지 방식은 squash와 merge commit만 허용한다. 머지된 브랜치는 자동 삭제한다.
- `main`·`develop` 보호: PR 필수, CI 통과 필수(CI 도입 후), 강제 푸시 금지
- 마일스톤은 서브프로젝트 0~4이고, 라벨은 type과 scope 기준이다.
- 커밋 작성자 정보는 저장소 로컬 설정으로 둔다: `okabedev <81132541+okabedev@users.noreply.github.com>`

### 10.2 Git Flow
| 브랜치 | 분기 원천 | 머지 대상 | 머지 방식 |
|---|---|---|---|
| `feature/<번호>-<slug>` | `develop` | `develop` | squash |
| `release/x.y.z` | `develop` | `main`, 이후 `develop` | `--no-ff`, 태그 `vX.Y.Z` |
| `hotfix/x.y.z` | `main` | `main`, 이후 `develop` | `--no-ff`, 태그 |

- 서버와 웹은 SemVer 단일 버전으로 함께 릴리스한다. 앱은 `app-vX.Y.Z` 태그를 별도로 쓴다.
- `CHANGELOG.md`는 git-cliff로 생성한다.

### 10.3 Commit Convention
```
<type>(<scope>): <한국어 제목, 50자 이내, 마침표 없음>

<본문: 무엇을, 왜>

Refs: #<이슈번호>
```
- **type**: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- **scope**: `core-api` `catalog-worker` `event-collector` `libs` `web` `app` `shared` `infra` `docs` `repo`
- 호환성이 깨지는 변경은 `BREAKING CHANGE:` 푸터로 표시한다.

## 11. 테스트 전략
| 계층 | 도구 |
|---|---|
| 백엔드 단위 | JUnit 5, MockK, Kotest assertions |
| 백엔드 통합 | Spring Boot Test, Testcontainers(Postgres, Redis) |
| 외부 API | WireMock + 녹화한 국립중앙도서관 응답 |
| 아키텍처 | ArchUnit |
| 프론트 단위 | Vitest, Testing Library |
| 웹 E2E | Playwright(모의 OAuth) |
| 앱 | Jest, React Native Testing Library (서브프로젝트 3) |

- 백엔드는 TDD로 개발한다.
- 커버리지 기준선은 표준 예시 기능이 완성된 뒤 Kover로 정한다.
- CI는 경로별로 나눠 실행한다(`services/**`, `clients/**`, `infra/**`, `docs/**`).

## 12. 로드맵
| # | 서브프로젝트 | 완료 기준 |
|---|---|---|
| 0 | 기반 | 저장소·도구·훅·CI·문서 골격·compose 골격, 터널로 `pipibooks.com`에 임시 페이지 공개 |
| 1 | 코어 MVP | 소셜 로그인, 카탈로그 수집, 카드 생성·검수, 피드, 서재(위시), 이벤트 수집. `v0.1.0` 릴리스 |
| 2 | 독서 기록·통계 | 기록, 인상 깊은 문장, 통계 화면 |
| 3 | 모바일 앱 | Expo 앱, Apple 로그인, 스토어 출시 |
| 4 | 본격 추천 | 추천 워커 추가. 착수 기준은 서브프로젝트 2가 끝날 때 정한다 |

서브프로젝트마다 별도의 스펙(필요 시)과 구현 계획을 `docs/specs`, `docs/plans`에 작성한다.

## 13. 구현 전에 확인할 사항
- **국립중앙도서관 ISBN 서지정보 API**: 제목·저자 검색, 출간일 범위 조회, 표지 URL 제공 여부, 호출 제한. 서브프로젝트 1 계획 전에 확인한다. 기능이 부족하면 이 스펙을 다시 논의한다.
- **도구 버전**: Spring Boot, Kotlin, Next.js, Expo 등의 최신 안정 버전을 각 계획 단계에서 확인해 고정한다(JDK는 설치된 25 LTS).
- **Docker 환경**: 현재 Docker가 Docker Desktop인지 WSL 안의 엔진인지 서브프로젝트 0에서 확인한다.
- **도메인**: `pipibooks.com` 구매 여부. 터널 공개 단계 전까지 필요하다.

# 서브프로젝트 1: 코어 MVP 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 비로그인 피드, 소셜 로그인·온보딩, 위시·검색, 카드 자동 발행과 관리자 사후 점검, 이벤트 수집, 백업·모니터링을 갖춘 웹을 `v0.1.0`으로 제한 공개하고, 신고·약관·탈퇴를 더해 `v0.2.0`으로 일반 공개한다.

**Architecture:** 얇은 수직 관통부터 만든다(수집 → 초안 → 자동 발행 → 비로그인 피드 → 웹). 그 위에 로그인, 위시, 이벤트, 관리자, 운영을 차례로 얹는다. 스키마 소유권은 DB 계정으로 강제한다. 컴포넌트 사이 공유 계약(이벤트·Redis 키·오류 모델)은 `services/libs/*`, 클라이언트 공유 계약은 `clients/packages/*`에 둔다.

**Tech Stack:** 기존(Kotlin 2.4.21, Spring Boot 4.1.2, JDK 25, Next.js 16.4.5, PostgreSQL 18.6, Redis 8.10.3)에 추가: Flyway·Testcontainers 2.0.5·PostgreSQL JDBC·R2DBC PostgreSQL 1.1.3·Spring Security 7.1.2(Nimbus JOSE)는 Boot BOM 관리 버전, WireMock 3.13.2(`org.wiremock:wiremock-standalone`), tsid-creator 5.2.6(`com.github.f4b6a3:tsid-creator`), springdoc 3.2.0(`org.springdoc:springdoc-openapi-starter-webmvc-ui`), openapi-typescript 7.13.0, openapi-fetch 0.17.0

**Spec:** `docs/specs/2026-11-06-subproject-1-core-mvp-design.md` (상위: `docs/specs/2026-11-05-architecture-design.md`, ADR 0011)

## Global Constraints

- 문서: `**/CLAUDE.md` 199줄 이하, `docs/` 참조 문서 300줄 이하(`docs/plans/` 제외). 설계가 바뀌면 같은 PR에서 문서를 갱신한다.
- 커밋·PR 제목: `<type>(<scope>): <한국어 제목>`, 50자 이내, 마침표 없음. scope: `core` `catalog-worker` `event-collector` `libs` `web` `app` `shared` `infra` `docs` `repo`. 푸터 `Refs: #<이슈>`.
- 버전: 위 Tech Stack 값. Spring 관리 의존성은 버전 없이 선언하고, 나머지는 `services/gradle/libs.versions.toml`에만 쓴다. npm 패키지는 정확한 버전으로 고정한다.
- 포트: web 3000, core 8080, event-collector 8081, catalog-worker 8082. 패키지: `com.pipibooks.core`, `com.pipibooks.catalog`, `com.pipibooks.event`, `com.pipibooks.common`, `com.pipibooks.eventschema`.
- 스키마 쓰기: `catalog`=catalog-worker(`catalog_app`), `core`=core(`core_app`), `events`=event-collector(`events_app`). core는 `catalog`를 읽기만 한다. 스키마를 넘는 외래 키는 걸지 않는다.
- PK는 TSID(`Long`), API에서는 문자열. 시각은 `timestamptz`(UTC). 스케줄과 발행예정일 해석은 `Asia/Seoul`.
- API 규칙: `docs/conventions/api.md`(Problem Details + `code`, 커서 `{items, nextCursor}`, `size` 기본 20·최대 50).
- 테스트 이름은 한국어 백틱 함수명. 백엔드는 TDD(실패 확인 후 구현). DB·Redis는 Testcontainers, 외부 API는 WireMock.
- 시크릿은 `infra/env/*.env`(gitignore)와 GitHub Secrets에만 둔다. 예시 파일에는 키 이름만 쓴다.
- 이벤트 타입·Stream 키·Redis 키는 `services/libs/event-schema`에만 정의한다. 슬라이드 페이로드 스키마는 서버 `com.pipibooks.common.slide`와 `clients/packages/shared`가 같은 모양을 가진다.

## Review Focus

1. 국립중앙도서관 응답의 빈 문자열(`""`)·공백·`http://` 표지·하이픈 섞인 ISBN이 그대로 저장되면 안 된다. 빈 값은 `null`, ISBN은 숫자 13자리로 정규화되어야 한다. → Task 2 테스트
2. 14일 구간 재수집으로 같은 책을 다시 받아도, 카드 필드가 그대로면 초안이 새로 생기지 않아야 한다. → Task 3 테스트
3. 시계가 틀린 기기의 이벤트(`occurredAt` 1970년·2099년) 한 건이 배치 500건을 막으면 안 된다. 그 건만 수신 시각으로 보정되어 저장되어야 한다. → Task 12 테스트
4. 두 탭이 같은 Refresh 토큰으로 30초 안에 동시에 재발급하면 둘 다 성공해야 한다. 30초가 지난 재사용은 계열 전체를 폐기해야 한다. → Task 8 테스트
5. 발행된 카드가 0장이거나 20장보다 적을 때, 그리고 `X-Anonymous-Id` 헤더가 없을 때도 피드가 오류 없이 응답해야 한다. → Task 5 테스트

## 작업 진행 방식 (모든 Task 공통)

1. 계획 끝 "이슈 번호" 표의 이슈 `N`으로 `develop`에서 `feature/N-<slug>`를 만든다(start-feature 스킬).
2. Task를 끝내면 `gh pr create --base develop`(PR 템플릿, `Closes #N`) → `ci-ok` 통과 → `gh pr merge --squash`.
3. 새 테이블·컬럼은 그 Task에서 Flyway 마이그레이션과 `docs/domain/model.md`를 함께 바꾼다(Task 2 이후는 `add-migration` 스킬). 새 REST API는 `docs/conventions/api.md`를 따른다(Task 10 이후는 `add-api-endpoint` 스킬).
4. 사용자 결정이나 외부 계정이 필요한 Step(표시: **[사용자]**)은 멈추고 요청한다.

## 파일 구조

| 경로 | 책임 | Task |
|---|---|---|
| `services/libs/common/` | Problem Details 예외, TSID, 커서 코덱, 슬라이드 페이로드 타입 | 1 |
| `services/*/src/main/resources/db/migration/<schema>/` | 스키마별 Flyway 마이그레이션 | 1~ |
| `services/catalog-worker/.../nlk/` | 국립중앙도서관 클라이언트, 호출 한도, 정규화 | 2 |
| `services/catalog-worker/.../book/`, `.../collect/`, `.../internal/` | 도서 저장, 수집 실행, 내부 API | 2 |
| `services/catalog-worker/.../draft/` | 카드 초안 생성기, 자동 발행 판정 | 3 |
| `services/core/.../card/` | 초안 동기화(자동 발행), 카드 조회, `BookQuery` | 4 |
| `services/libs/event-schema/` | 이벤트 타입·페이로드, Stream·Redis 키 | 5 |
| `services/core/.../feed/` | `FeedRanker`, 피드 세션, `GET /v1/feed` | 5 |
| `clients/packages/shared/`, `clients/packages/api-client/` | 슬라이드 스키마·관심 그룹·트래커, OpenAPI 생성 클라이언트 | 6, 13 |
| `clients/web/src/features/feed/`, `src/app/cards/[id]/` | 피드 화면, 공유 페이지 | 7 |
| `services/core/.../member/` | OAuth, JWT, Refresh 회전, `/v1/me` | 8 |
| `clients/web/src/features/auth/`, `src/app/{login,auth,onboarding,me}/` | 로그인·온보딩 화면 | 9 |
| `services/core/.../library/` | 검색, 위시(표준 예시) | 10 |
| `clients/web/src/app/{search,shelf}/` | 검색·위시 화면 | 11 |
| `services/event-collector/.../` | 수신, Stream, 배치 적재, 파티션 | 12 |
| `services/core/.../admin/` | 관리자 API | 14 |
| `clients/web/src/app/admin/` | 관리자 화면 | 15 |
| `infra/backup/`, `infra/scripts/restore-rehearsal.sh`, `docs/ops/{backup,monitoring}.md` | 백업·모니터링·WAF | 16 |
| `.claude/skills/{add-migration,add-api-endpoint}/SKILL.md` | 프로젝트 스킬 | 2, 10 |

---

### Task 1: 공통 기반 (libs/common, 영속 계층, ADR 0012)

**Files:**
- Create: `docs/architecture/adr/0012-webflux-db-access.md`, `services/libs/common/build.gradle.kts`, `services/libs/common/src/main/kotlin/com/pipibooks/common/{ApiException.kt,Tsid.kt,CursorCodec.kt,slide/SlidePayload.kt}`, 각 서비스 `src/test/kotlin/.../support/DbTestSupport.kt`
- Modify: `services/settings.gradle.kts`, `services/gradle/libs.versions.toml`, 세 서비스 `build.gradle.kts`·`application.yml`, `infra/compose/compose.prod.yml`(서비스별 `env_file`, Redis `maxmemory 200mb`·`volatile-lru`), `infra/env/*.env.example`(서비스별 DB 접속 정보), `infra/scripts/smoke-test.sh`, `services/CLAUDE.md`, `docs/architecture/adr/README.md`

**Interfaces:**
- Produces:
  - `class ApiException(val status: HttpStatus, val code: String, override val message: String) : RuntimeException` — core의 `@RestControllerAdvice`가 Problem Details(`application/problem+json`, `code` 필드)로 바꾼다.
  - `object Tsid { fun next(): Long; fun format(id: Long): String; fun parse(s: String): Long }` — 문자열은 10진수 문자열.
  - `object CursorCodec { fun encode(fields: Map<String, String>): String; fun decode(cursor: String): Map<String, String> }` — URL-safe Base64(JSON). 잘못된 값은 `ApiException(400, "INVALID_CURSOR")`.
  - `sealed interface SlidePayload` 구현: `Cover(title, author, coverUrl: String?)`, `Hook(sentences: List<String>)`, `Info(author, publisher, publishedOn: LocalDate?, pages: Int?, kdcLabel: String?, source: String = "국립중앙도서관")`, `Keywords(keywords: List<String>)`, `ForWhom(lines: List<String>)`, `Quote(text, page: Int?)`. 직렬화 시 `type` 판별 필드는 `COVER`/`HOOK`/`INFO`/`KEYWORDS`/`FOR_WHOM`/`QUOTE`.
  - core: JPA + Flyway(`spring.flyway.schemas=core`, 위치 `classpath:db/migration/core`). catalog-worker·event-collector: R2DBC(`DatabaseClient`) + Flyway(JDBC, 기동 시만, 각각 `catalog`·`events`).
  - `DbTestSupport`: Testcontainers `postgres:18.6-alpine` + `infra/postgres/init/01-init.sh`로 계정을 만들고, 서비스 계정으로 접속하게 `@DynamicPropertySource`를 설정하는 테스트 기반 클래스. core·event-collector는 `redis:8.10.3-alpine`도 띄운다.

- [ ] **Step 1: ADR 0012 작성** — 결정: WebFlux 컴포넌트는 Spring Data R2DBC `DatabaseClient`(SQL 직접 작성), 마이그레이션만 Flyway+JDBC. 대안: JDBC를 `Dispatchers.IO`로 감싸기(코루틴 모델과 어긋남), Exposed(새 학습 비용). 상태 `승인`, README 표에 추가.
- [ ] **Step 2: 실패하는 테스트 작성**
  - `libs/common`: `` `TSID는 시간순으로 증가한다` `` (연속 1000개가 정렬 상태), `` `TSID 문자열은 왕복 변환된다` ``, `` `커서는 왕복 변환된다` ``, `` `깨진 커서는 INVALID_CURSOR로 거부된다` `` (`"%%%"` → `ApiException.code == "INVALID_CURSOR"`), `` `슬라이드 페이로드는 type 필드로 직렬화된다` `` (`Hook(listOf("a"))` → `{"type":"HOOK","sentences":["a"]}`)
  - 각 서비스: `` `서비스 계정으로 접속하고 자기 스키마에 마이그레이션한다` `` — `SELECT current_user` = `core_app`/`catalog_app`/`events_app`, `flyway_schema_history`가 자기 스키마에 존재
  - core: `` `ApiException은 Problem Details로 응답한다` `` — 테스트 전용 컨트롤러가 `ApiException(404, "X_NOT_FOUND", "없음")` → 404, `Content-Type: application/problem+json`, `$.code == "X_NOT_FOUND"`
- [ ] **Step 3: 실패 확인** — Run: `cd services && ./gradlew test` → FAIL(클래스·의존성 없음)
- [ ] **Step 4: 구현** — 위 Interfaces대로 만든다. 각 서비스 `application.yml`에 `spring.datasource`/`spring.r2dbc`와 `spring.flyway`를 환경 변수(`DB_URL`, `DB_USER`, `DB_PASSWORD`)로 받는다. 빈 마이그레이션 디렉토리로 기동되어야 한다(`spring.flyway.fail-on-missing-locations=false`).
- [ ] **Step 5: 통과 확인** — Run: `cd services && ./gradlew build` → BUILD SUCCESSFUL
- [ ] **Step 6: 스모크 테스트 확장** — `smoke-test.sh`가 임시 env 파일에 서비스별 DB 접속 정보를 넣고, 세 서비스 헬스가 UP인지 확인한다(Flyway가 기동 시 실패하면 DOWN). Run: `bash infra/scripts/build-local.sh && bash infra/scripts/smoke-test.sh` → `SMOKE TEST PASSED`
- [ ] **Step 7: 문서·커밋** — `services/CLAUDE.md` 모듈 표에 `libs/common`, DB 접근 규칙(ADR 0012), `DbTestSupport` 사용법 한 줄. 커밋 `feat(libs): 공통 라이브러리와 영속 계층 기반 추가`

### Task 2: 국립중앙도서관 수집과 도서 저장 (catalog-worker)

**Files:**
- Create: `catalog-worker/src/main/kotlin/com/pipibooks/catalog/nlk/{NlkClient.kt,NlkRateLimiter.kt,NlkModels.kt,BookNormalizer.kt}`, `.../book/BookRepository.kt`, `.../collect/{CollectService.kt,CollectScheduler.kt,CollectRunRepository.kt}`, `.../internal/InternalBookController.kt`, `.../health/Heartbeat.kt`, `resources/db/migration/catalog/V1__book_and_collect_run.sql`, `src/test/resources/nlk/*.json`(공개 문서 필드로 만든 가짜 응답), `.claude/skills/add-migration/SKILL.md`
- Modify: `docs/domain/model.md`(`book` 추가 컬럼, `collect_run`), `infra/env/catalog-worker.env.example`(`NLK_CERT_KEY`, `HEALTHCHECK_COLLECT_URL`), `CLAUDE.md`(문서 색인에 스킬)

**Interfaces:**
- Consumes: Task 1 `Tsid`, `DbTestSupport`
- Produces:
  - `enum class Priority { USER, BATCH }`; `class NlkRateLimiter(permitsPerSecond: Double) { suspend fun <T> withPermit(priority: Priority, block: suspend () -> T): T }` — `USER` 대기자가 있으면 `BATCH`보다 먼저 허가.
  - `class NlkClient { suspend fun byPublishDate(date: LocalDate, page: Int): NlkPage; suspend fun byTitle(q: String, page: Int): NlkPage; suspend fun byIsbn(isbn13: String): NlkRecord? }` — `NlkPage(totalCount: Int, records: List<NlkRecord>)`. 오류 코드 `010`·`011`은 `NlkAuthException`, `000`·5xx·타임아웃은 3회 지수 백오프(200ms, 400ms, 800ms) 후 `NlkUnavailableException`.
  - `object BookNormalizer { fun normalize(r: NlkRecord): NormalizedBook? }` — ISBN 검증 실패면 `null`.
  - `class BookRepository { suspend fun upsert(b: NormalizedBook): UpsertResult; suspend fun findByIsbn(isbn13: String): Book? }` — `UpsertResult(bookId: Long, created: Boolean, cardFieldsChanged: Boolean, coverUrlChanged: Boolean)`. 카드 필드: 제목·저자·출판사·발행예정일·KDC·쪽수·표지·소개·목차·`ea_add_code`·`form`·`ebook_yn`·`set_isbn`.
  - `class CoverChecker { suspend fun verify(url: String): Boolean }` — GET 한 번, `200`이고 `Content-Type`이 `image/`로 시작하면 `true`. `coverUrlChanged`일 때만 호출하고, 실패하면 `cover_url = null`.
  - 내부 API: `GET /internal/books/search?q=&page=` → `{items:[{isbn13,title,author,publisher,coverUrl}], totalCount}`; `POST /internal/books/import {isbn}` → `200 {bookId}` / 국립중앙도서관에 없으면 `404 BOOK_NOT_FOUND` / 장애면 `503 CATALOG_UNAVAILABLE`; `POST /internal/collect-runs {type: "NEW_BOOKS"|"SEED_ISBN"|"SEED_KEYWORD", isbns?, keyword?}` → `202 {runId}`; `GET /internal/collect-runs?size=` → 최근 실행 목록.
  - 이벤트 훅: `interface BookUpsertListener { suspend fun onUpsert(book: Book, result: UpsertResult) }` — Task 3이 구현한다(이 Task에서는 빈 기본 구현).

- [ ] **Step 1: 마이그레이션 `V1__book_and_collect_run.sql`** — `catalog.book`(`id bigint PK`, `isbn13 char(13) UNIQUE NOT NULL`, `title`, `author`, `publisher`, `publish_predate date`, `kdc varchar`, `pages int`, `cover_url`, `introduction text`, `table_of_contents text`, `ea_add_code varchar(5)`, `form varchar`, `ebook_yn boolean`, `set_isbn varchar(13)`, `fetched_at timestamptz`), `catalog.collect_run`(`id`, `type`, `params jsonb`, `status` `RUNNING`/`SUCCEEDED`/`FAILED`, `fetched_count`, `saved_count`, `draft_count`, `error`, `started_at`, `finished_at`).
- [ ] **Step 2: 실패하는 테스트 작성**
  - `BookNormalizerTest`: `` `빈 문자열과 공백 필드는 null이 된다` ``, `` `하이픈이 섞인 ISBN은 숫자 13자리로 정규화된다` `` (`"978-89-123-4567-8"` 형태, 유효 검증숫자), `` `검증숫자가 틀린 ISBN은 버린다` ``, `` `http 표지는 https로 바뀐다` ``, `` `저자 역할어를 정리한다` `` (`"김하늘 지음 ; 이바다 옮김"` → `"김하늘, 이바다"`), `` `발행예정일 yyyymmdd를 날짜로 바꾼다` ``, `` `부가기호와 전자책 여부를 읽는다` ``
  - `NlkClientTest`(WireMock): `` `발행예정일 하루를 페이지 단위로 조회한다` `` (요청 파라미터 `start_publish_date=end_publish_date=20261105`, `page_size=50`, `result_style=json`), `` `시스템 오류는 3회 재시도 후 실패한다` `` (4번째 요청 없음), `` `인증 오류는 재시도하지 않는다` ``, `` `한글 검색어는 URL 인코딩된다` `` (`"바다 & 섬"`)
  - `NlkRateLimiterTest`: `` `사용자 요청이 대기 중인 배치보다 먼저 허가된다` ``
  - `BookRepositoryTest`: `` `같은 ISBN을 다시 저장하면 갱신되고 created는 false다` ``, `` `카드 필드가 같으면 cardFieldsChanged는 false다` ``, `` `fetched_at만 바뀌어도 cardFieldsChanged는 false다` ``
  - `CollectServiceTest`: `` `최근 14일을 하루씩 조회하고 실행 이력을 남긴다` `` (14일 × 요청, `collect_run.status == SUCCEEDED`), `` `한 날짜가 실패해도 나머지는 수집하고 FAILED와 오류를 기록한다` ``, `` `하루 결과가 500건을 넘으면 경고 로그를 남긴다` ``
  - `InternalBookControllerTest`: `` `이미 있는 도서는 외부 호출 없이 import된다` `` (WireMock 요청 0회), `` `없는 도서는 국립중앙도서관에서 가져와 저장한다` ``, `` `국립중앙도서관 장애면 503 CATALOG_UNAVAILABLE` ``
- [ ] **Step 3: 실패 확인** — Run: `cd services && ./gradlew :catalog-worker:test` → FAIL
- [ ] **Step 4: 구현** — `CollectScheduler`는 `@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")`. 설정값: `nlk.base-url`(기본 `https://www.nl.go.kr`), `nlk.cert-key`, `nlk.permits-per-second`(1.0), `nlk.page-size`(50), `collect.window-days`(14). 끝나면 `HEALTHCHECK_COLLECT_URL`이 있을 때만 GET(성공 `…`, 실패 `…/fail`).
- [ ] **Step 5: 통과 확인** — Run: `cd services && ./gradlew :catalog-worker:build` → BUILD SUCCESSFUL
- [ ] **Step 6: `add-migration` 스킬 작성** — 이 Task의 마이그레이션을 예시로: 파일 위치·이름 규칙(`V<n>__<snake_case>.sql`, 스키마별 디렉토리), 소유 컴포넌트에서만 작성, 스키마를 넘는 FK 금지, TSID PK, `timestamptz`, `docs/domain/model.md` 동시 갱신, `DbTestSupport` 테스트 필수. 루트 `CLAUDE.md` 색인에 한 줄.
- [ ] **Step 7: 커밋** — `feat(catalog-worker): 국립중앙도서관 수집과 도서 저장 추가`

### Task 3: 카드 초안 생성과 자동 발행 판정 (catalog-worker)

**Files:**
- Create: `catalog-worker/.../draft/{CardDraftGenerator.kt,RuleBasedCardDraftGenerator.kt,AutoPublishPolicy.kt,KdcTemplates.kt,KeywordExtractor.kt,CardDraftRepository.kt,DraftOnUpsertListener.kt}`, `resources/db/migration/catalog/V2__card_draft.sql`
- Modify: `docs/domain/model.md`(`card_draft`)

**Interfaces:**
- Consumes: Task 2 `Book`, `UpsertResult`, `BookUpsertListener`; Task 1 `SlidePayload`
- Produces:
  - `interface CardDraftGenerator { val name: String; val version: Int; fun generate(book: Book): List<SlidePayload>? }` — 소개가 없으면 `null`. 규칙 기반 구현은 `name = "rule-based"`, `version = 1`, 슬라이드 순서 COVER → HOOK → INFO → KEYWORDS → FOR_WHOM.
  - `enum class BlockedReason { SHORT_INTRODUCTION, NOT_PRINT_SINGLE, STUDY_REFERENCE, NO_COVER }`; `object AutoPublishPolicy { fun evaluate(book: Book): List<BlockedReason> }` — 빈 목록이면 자동 발행 가능.
  - `catalog.card_draft`(`id`, `book_id`, `slides jsonb`, `generator_name`, `generator_version`, `auto_publishable boolean`, `blocked_reasons text[]`, `created_at`). core가 `id > cursor` 순으로 읽는다.

- [ ] **Step 1: 마이그레이션 `V2__card_draft.sql`** — 위 컬럼, `(book_id, created_at)` 인덱스.
- [ ] **Step 2: 실패하는 테스트 작성**
  - `AutoPublishPolicyTest`: `` `소개 99자는 SHORT_INTRODUCTION, 100자는 통과한다` ``, `` `전자책과 세트 ISBN은 NOT_PRINT_SINGLE` ``, `` `부가기호 첫 자리 5와 6은 STUDY_REFERENCE, 4는 통과한다` ``, `` `표지가 없으면 NO_COVER` ``, `` `모두 만족하면 빈 목록` ``
  - `RuleBasedCardDraftGeneratorTest`: `` `소개가 없으면 초안을 만들지 않는다` ``, `` `다섯 슬라이드를 정해진 순서로 만든다` ``, `` `HOOK은 소개에서 문장 1~2개를 고른다` `` (각 문장 120자 이하), `` `KEYWORDS는 3~6개다` ``, `` `FOR_WHOM은 KDC 대분류 템플릿을 쓰고, KDC가 없으면 기본 템플릿을 쓴다` ``, `` `표지가 없으면 COVER의 coverUrl은 null이다` ``
  - `DraftOnUpsertListenerTest`: `` `새 도서는 초안을 만든다` ``, `` `카드 필드가 바뀌지 않은 재수집은 초안을 만들지 않는다` `` (Review Focus 2), `` `카드 필드가 바뀌면 새 초안을 만든다` ``, `` `생성기 버전이 오르면 기존 도서도 새 초안을 만든다` ``, `` `판정 결과와 사유를 저장한다` ``
- [ ] **Step 3: 실패 확인** — Run: `cd services && ./gradlew :catalog-worker:test` → FAIL
- [ ] **Step 4: 구현** — HOOK은 소개를 문장 단위(`. ! ? 。` 뒤 공백)로 나눠 20~120자 문장 중 앞쪽 1~2개. KEYWORDS는 목차 항목 머리말과 소개 명사 빈도(2자 이상 한글 단어, 불용어 목록 내장)에서 상위 3~6개. FOR_WHOM 템플릿은 `KdcTemplates`에 대분류 0~9와 기본값으로 각 2문장. `collect_run.draft_count`를 갱신한다.
- [ ] **Step 5: 통과 확인** — Run: `cd services && ./gradlew :catalog-worker:build` → BUILD SUCCESSFUL
- [ ] **Step 6: 커밋** — `feat(catalog-worker): 카드 초안 생성과 자동 발행 판정 추가`

### Task 4: 카드 자동 발행과 카드 조회 (core `card`)

**Files:**
- Create: `core/.../card/{CardEntity.kt,CardSlideEntity.kt,CardRepository.kt,BookQuery.kt,CardDraftReader.kt,CardSyncJob.kt,CardService.kt,CardController.kt,CardDtos.kt}`, `core/.../support/{OpenApiConfig.kt,ProblemHandler.kt}`, `resources/db/migration/core/V1__card.sql`
- Modify: `core/build.gradle.kts`(springdoc), `docs/domain/model.md`, `ArchitectureTest.kt`

**Interfaces:**
- Consumes: Task 3 `catalog.card_draft`·`catalog.book`(읽기), Task 1 `ApiException`, `Tsid`
- Produces:
  - `interface BookQuery { fun find(bookId: Long): BookSummary?; fun findAll(ids: Collection<Long>): Map<Long, BookSummary> }` — `BookSummary(bookId, isbn13, title, author, publisher, coverUrl, kdcMajor: Int?)`. 다른 모듈(`feed`, `library`, `admin`)이 쓰는 유일한 catalog 읽기 경로.
  - `interface CardQuery { fun candidates(limit: Int): List<CardCandidate>; fun publishedViews(ids: List<Long>): List<CardView> }` — `CardCandidate(cardId, bookId, kdcMajor: Int?, publishedAt: Instant)`, `CardView(id: String, isbn13, title, author, slides: List<SlidePayload>)`. `publishedViews`는 `PUBLISHED`가 아닌 카드를 빼고 입력 순서를 유지한다.
  - `class CardService { fun syncDrafts(): Int; fun publishDraft(draftId: Long): Long; fun reapplyLatestDraft(cardId: Long) }` — 동기화는 `core.sync_cursor`(`name='card_draft'`, `last_id`)부터 `auto_publishable`인 초안만, 책에 카드가 없을 때만 `AUTO`·`PUBLISHED`로 복사.
  - `GET /v1/cards/{id}` → `200 CardView`, 없거나 `DRAFT`·`HIDDEN`이면 `404 CARD_NOT_FOUND`. OpenAPI: `/v3/api-docs`(운영에서도 켠다, 문서 UI는 끈다).

- [ ] **Step 1: 마이그레이션 `V1__card.sql`** — `core.card`(`id`, `book_id bigint UNIQUE NOT NULL`(FK 없음), `source`, `status`, `published_at`, `edited_at`, `created_at`), `core.card_slide`(`id`, `card_id` FK, `position`, `type`, `payload jsonb`, UNIQUE(`card_id`,`position`)), `core.sync_cursor`(`name PK`, `last_id bigint`).
- [ ] **Step 2: 실패하는 테스트 작성**
  - `CardServiceTest`: `` `자동 발행 가능한 새 초안은 AUTO PUBLISHED 카드가 된다` ``, `` `자동 발행 불가 초안은 카드를 만들지 않는다` ``, `` `이미 카드가 있는 책의 새 초안은 카드를 바꾸지 않는다` ``, `` `동기화 커서 이후의 초안만 읽는다` `` (두 번 연속 실행 시 두 번째는 0), `` `최신 초안 다시 적용은 슬라이드를 교체하고 edited_at을 남긴다` ``
  - `CardControllerTest`: `` `발행된 카드를 조회한다` `` (`$.id`는 문자열, `$.slides[0].type == "COVER"`, `$.isbn13`), `` `숨김 카드는 404 CARD_NOT_FOUND` ``, `` `숫자가 아닌 ID는 404` ``
  - `ArchitectureTest`: 기존 순환 금지에 더해 `` `모듈은 다른 모듈의 공개 인터페이스만 참조한다` `` — `com.pipibooks.core.<a>..`가 `com.pipibooks.core.<b>..`의 `*Entity`·`*Repository`를 참조하지 않음
- [ ] **Step 3: 실패 확인** — Run: `cd services && ./gradlew :core:test` → FAIL
- [ ] **Step 4: 구현** — `CardDraftReader`는 `JdbcClient`로 `catalog.card_draft`를 `id > ? ORDER BY id LIMIT 500` 조회. `CardSyncJob`은 `@Scheduled(fixedDelay = 300000)`. catalog 테이블이 아직 없으면(첫 기동 순서) 경고 로그 후 다음 주기에 다시 시도한다.
- [ ] **Step 5: 통과 확인** — Run: `cd services && ./gradlew :core:build` → BUILD SUCCESSFUL
- [ ] **Step 6: 커밋** — `feat(core): 카드 자동 발행과 카드 조회 API 추가`

### Task 5: 비로그인 피드 (core `feed`, libs/event-schema)

**Files:**
- Create: `services/libs/event-schema/build.gradle.kts`, `.../eventschema/{EventType.kt,UserEventMessage.kt,RedisKeys.kt}`, `core/.../feed/{FeedRanker.kt,RuleBasedFeedRanker.kt,FeedSessionStore.kt,SeenCardReader.kt,FeedService.kt,FeedController.kt,FeedProperties.kt}`
- Modify: `services/settings.gradle.kts`, `core/build.gradle.kts`(Redis), `docs/domain/feed.md`

**Interfaces:**
- Consumes: Task 4 `CardQuery`, `BookQuery`; Task 1 `CursorCodec`
- Produces:
  - `enum class EventType { CARD_IMPRESSION, SLIDE_VIEW, CARD_DWELL, CARD_SKIP, CARD_SHARE, SHELF_ADD, STATUS_CHANGE, IDENTITY_LINK, MEMBER_DELETED }`
  - `data class UserEventMessage(eventId: UUID, type: EventType, memberId: Long?, anonymousId: String?, sessionId: String?, cardId: Long?, bookId: Long?, slideIndex: Int?, dwellMs: Long?, platform: String?, appVersion: String?, occurredAt: Instant, receivedAt: Instant)` — Stream 필드 맵 변환 `toFields(): Map<String,String>` / `fromFields(...)`.
  - `object RedisKeys { const val EVENTS_STREAM = "events:user"; const val DEAD_STREAM = "events:dead"; const val CONSUMER_GROUP = "collector"; fun seenCards(anonymousId: String) = "feed:seen:$anonymousId"; fun cardCounters(day: LocalDate) = "card:counters:${day}"; fun feedSession(sessionId: String) = "feed:session:$sessionId"; fun searchCache(q: String, page: Int) = "search:${sha256(q)}:$page" }` — 카운터 해시 필드는 `"$cardId:imp"`, `"$cardId:skip"`, `"$cardId:dwell"`.
  - `interface FeedRanker { fun rank(candidates: List<CardCandidate>, ctx: RankContext, seed: Long): List<Long> }` — 반환은 카드 ID 순서. `RankContext(interestKdc: Set<Int>, shelfKdcRatio: Map<Int, Double>, popularity: Map<Long, Int>, now: Instant)`, `popularity`의 키는 `bookId`. 비로그인은 앞의 두 값이 비어 있다.
  - `data class Viewer(memberId: Long?, anonymousId: String?)`; `class FeedService { fun page(viewer: Viewer, cursor: String?): FeedPage }` — `FeedPage(items: List<CardView>, nextCursor: String?)`.
  - `GET /v1/feed?cursor=` → `{items, nextCursor}`, 헤더 `X-Anonymous-Id`(선택).
  - 설정 `feed.weights.{interest,shelf,popularity,freshness,random}`(기본 3.0, 2.0, 1.0, 1.5, 0.5), `feed.freshness-half-life-days=14`, `feed.candidate-limit=2000`, `feed.session-size=500`, `feed.page-size=20`, `feed.session-ttl=30m`.
  - 이 Task에서 `popularity`는 빈 맵이다(Task 10이 `ShelfQuery`로 채운다). 관심·서재 항은 Task 8·10에서 채운다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `RuleBasedFeedRankerTest`: `` `같은 시드면 같은 순서다` ``, `` `다른 조건이 같으면 최근 발행 카드가 앞선다` ``, `` `관심 KDC 카드가 가점을 받는다` ``, `` `반감기 14일이 지나면 신선도 점수가 절반이다` ``
  - `FeedServiceTest`(Testcontainers Redis): `` `발행 카드가 0장이면 빈 목록과 nextCursor null` ``, `` `카드가 20장보다 적으면 한 페이지로 끝난다` `` (Review Focus 5), `` `커서로 다음 20장을 이어 준다` `` (중복 없음), `` `본 카드 집합에 있는 카드는 후보에서 빠진다` ``, `` `응답만 한 카드는 다음 세션에서도 빠지지 않는다` ``, `` `세션 중 숨겨진 카드는 다음 페이지에서 빠진다` ``, `` `만료된 세션의 커서는 새 세션으로 이어진다` ``
  - `FeedControllerTest`: `` `X-Anonymous-Id 없이도 피드를 준다` `` (Review Focus 5), `` `깨진 커서는 400 INVALID_CURSOR` ``
- [ ] **Step 2: 실패 확인** — Run: `cd services && ./gradlew :core:test :libs:event-schema:test` → FAIL
- [ ] **Step 3: 구현** — 점수 = Σ 가중치 × 항. 신선도 `0.5^(경과일/14)`, 인기도 `ln(1+n)/ln(1+max)`, 무작위 `Random(seed xor cardId).nextDouble()`. 세션 ID는 `Tsid.next()`, 커서 필드 `{s: 세션ID, o: offset}`.
- [ ] **Step 4: 통과 확인** — Run: `cd services && ./gradlew build` → BUILD SUCCESSFUL
- [ ] **Step 5: 문서·커밋** — `docs/domain/feed.md`에 점수 항·기본 가중치·본 카드 기준·이벤트 정의(스펙 §8 표) 반영. 커밋 `feat(core): 비로그인 피드 API 추가`

### Task 6: 클라이언트 공유 패키지와 API 클라이언트 (ADR 0013)

**Files:**
- Create: `docs/architecture/adr/0013-openapi-client-generation.md`, `services/core/src/test/kotlin/com/pipibooks/core/OpenApiDumpTest.kt`, `clients/packages/shared/{package.json,src/slides.ts,src/interests.ts,src/anonymous-id.ts,src/index.ts}`, `clients/packages/api-client/{package.json,openapi.json,src/schema.d.ts,src/index.ts}`, `scripts/generate-api-client.sh`
- Modify: `.github/workflows/ci.yml`(api-client 최신 검사), `clients/CLAUDE.md`, `docs/architecture/adr/README.md`

**Interfaces:**
- Consumes: Task 4·5 core OpenAPI(`/v3/api-docs`)
- Produces:
  - `@pipibooks/shared`: `type SlidePayload`(Task 1 서버 타입과 같은 모양, `type` 판별), `INTEREST_GROUPS: ReadonlyArray<{id: 'LITERATURE'|'HUMANITIES'|'SOCIETY'|'SCIENCE'|'TECH_LIFE'|'ARTS'|'HISTORY', label: string, kdc: number[]}>`(스펙 §6 표), `kdcColor(kdcMajor: number | null): string`, `getAnonymousId(storage: Storage): string`(UUID v4, 없으면 생성·저장).
  - `@pipibooks/api-client`: `createApiClient(baseUrl: string, getToken?: () => string | null)` → `openapi-fetch` 클라이언트(`paths` 타입). `X-Anonymous-Id`는 호출부에서 헤더로 넣는다.
  - `scripts/generate-api-client.sh`: `cd services && ./gradlew :core:test --tests '*OpenApiDumpTest'`(MockMvc로 `/v3/api-docs`를 받아 `clients/packages/api-client/openapi.json`에 정렬된 JSON으로 쓴다) → `pnpm --filter @pipibooks/api-client exec openapi-typescript openapi.json -o src/schema.d.ts`.

- [ ] **Step 1: ADR 0013 작성** — 결정: `openapi-typescript`(타입 생성) + `openapi-fetch`(런타임 6KB 미만). 명세는 core 테스트가 덤프한 `openapi.json`을 커밋하고, CI가 다시 생성해 차이가 있으면 실패. 대안: openapi-generator(Java 런타임·생성 코드가 큼), orval(설정이 많음).
- [ ] **Step 2: 실패하는 테스트 작성** (Vitest)
  - `interests.test.ts`: `` 관심 그룹 7개가 KDC 0과 7을 포함하지 않는다 ``, `` 각 KDC 대분류는 최대 한 그룹에 속한다 ``
  - `anonymous-id.test.ts`: `` 저장된 값이 없으면 UUID를 만들어 저장한다 ``, `` 저장된 값이 있으면 그대로 쓴다 ``, `` 저장소 접근이 예외를 던지면 메모리 값으로 대신한다 ``
  - `slides.test.ts`: `` 서버 예시 JSON 6종을 SlidePayload로 판별한다 `` (`services/libs/common` 테스트가 쓰는 같은 픽스처 파일 `clients/packages/shared/test/slides.fixture.json`)
- [ ] **Step 3: 실패 확인** — Run: `pnpm --filter @pipibooks/shared test` → FAIL
- [ ] **Step 4: 구현·생성** — `bash scripts/generate-api-client.sh` 후 `git diff --exit-code clients/packages/api-client`가 깨끗해야 한다. CI `web` 잡에 같은 검사를 추가한다(경로 필터에 `services/core/**` 포함).
- [ ] **Step 5: 통과 확인** — Run: `pnpm --filter @pipibooks/shared test && pnpm --filter @pipibooks/api-client exec tsc --noEmit` → PASS
- [ ] **Step 6: 커밋** — `feat(shared): 공유 패키지와 API 클라이언트 생성 추가`

### Task 7: 웹 피드 화면과 공유 페이지

**Files:**
- Create: `clients/web/src/features/feed/{FeedView.tsx,CardItem.tsx,SlideCarousel.tsx,slides/*.tsx,ActionRail.tsx,useFeed.ts,mock-cards.ts}`, `clients/web/src/app/cards/[id]/page.tsx`, `clients/web/src/components/TabBar.tsx`, `clients/web/src/lib/api.ts`
- Modify: `clients/web/src/app/page.tsx`, `clients/web/next.config.ts`(`images.remotePatterns`: `www.nl.go.kr`, `nl.go.kr`), `clients/web/package.json`, `clients/web/e2e/`, `clients/web/Dockerfile`·`.github/workflows/release.yml`(`NEXT_PUBLIC_*`는 빌드 시점에 박히므로 build arg로 운영 값을 넘긴다), `infra/compose/compose.prod.yml`(web에 `CORE_INTERNAL_URL`)

**Interfaces:**
- Consumes: Task 6 `createApiClient`, `SlidePayload`, `kdcColor`, `getAnonymousId`; Task 5 `GET /v1/feed`; Task 4 `GET /v1/cards/{id}`
- Produces:
  - `<SlideCarousel slides onSlideChange(index)>`, `<CardItem card onActive onLeave(dwellMs, lastSlideIndex)>` — Task 13이 이벤트를 붙이는 콜백.
  - 환경 변수: `NEXT_PUBLIC_API_BASE_URL`(브라우저, 운영 `https://api.pipibooks.com`), `CORE_INTERNAL_URL`(서버 렌더링, 운영 `http://core:8080`).

- [ ] **Step 1: 목 데이터 화면 체크포인트 [사용자]** — `mock-cards.ts`(가상 도서 5권, 표지 있음 4권·없음 1권)로 `/`에 피드를 그린다. 모바일(390×844)과 데스크톱(1440×900) Playwright 스크린샷을 PR 초안에 올리고 사용자 확인을 받는다. 확인 전에는 API를 연결하지 않는다.
- [ ] **Step 2: 실패하는 테스트 작성**
  - Vitest: `` 슬라이드는 COVER부터 순서대로 그린다 ``, `` 표지가 없으면 타이포 표지를 그린다 ``, `` 오른쪽 화살표와 → 키로 다음 슬라이드로 간다 ``, `` INFO 슬라이드에 '자료: 국립중앙도서관'을 표시한다 ``, `` 피드가 비면 끝 화면을 보여 준다 ``
  - Playwright(`e2e/feed.spec.ts`, API는 `page.route`로 목): `피드를 위로 넘기면 다음 카드가 활성화된다`, `/cards/<id>는 og:title과 og:image를 가진다`, `숨김 카드 공유 링크는 404 페이지를 보여 준다`
- [ ] **Step 3: 실패 확인** — Run: `pnpm --filter web test` → FAIL
- [ ] **Step 4: 구현** — 세로 스크롤은 CSS `scroll-snap-type: y mandatory`, 슬라이드는 `x mandatory`. 활성 카드는 `IntersectionObserver`(임계값 0.6). 공유 페이지는 서버 컴포넌트에서 `CORE_INTERNAL_URL`로 조회하고 `generateMetadata`로 OG를 만든다(Next 16 문서 확인 후, `clients/CLAUDE.md` 주의 사항 준수).
- [ ] **Step 5: 통과 확인** — Run: `pnpm --filter web lint && pnpm --filter web test && pnpm test:e2e:docker` → PASS
- [ ] **Step 6: 커밋** — `feat(web): 피드 화면과 카드 공유 페이지 추가`

### Task 8: 소셜 로그인·토큰·회원 (core `member`)

**Files:**
- Create: `core/.../member/{MemberEntity.kt,SocialAccountEntity.kt,RefreshTokenEntity.kt,MemberInterestEntity.kt,repositories,OAuthProviderClient.kt,KakaoClient.kt,GoogleClient.kt,JwtIssuer.kt,AuthService.kt,AuthController.kt,MeController.kt,MemberQuery.kt,AdminAccounts.kt}`, `core/.../support/{SecurityConfig.kt,CorsConfig.kt,ServerEventPublisher.kt}`, `resources/db/migration/core/V2__member.sql`
- Modify: `core/build.gradle.kts`(spring-boot-starter-security, oauth2-resource-server), `infra/env/core.env.example`(`KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `OAUTH_REDIRECT_URIS`, `JWT_PRIVATE_KEY_PEM`, `JWT_PUBLIC_KEY_PEM`, `ADMIN_SOCIAL_ACCOUNTS`, `CORS_ALLOWED_ORIGIN`), `docs/domain/model.md`, `core/.../feed/FeedService.kt`(관심 KDC)

**Interfaces:**
- Consumes: Task 5 `UserEventMessage`, `RedisKeys.EVENTS_STREAM`, `RankContext`
- Produces:
  - `interface OAuthProviderClient { val provider: Provider; fun exchange(code: String, redirectUri: String): OAuthProfile }` — `OAuthProfile(providerUserId: String, nickname: String, profileImageUrl: String?)`, `enum Provider { KAKAO, GOOGLE }`.
  - `POST /v1/auth/{provider}` `{code, redirectUri, anonymousId?}` → `200 {accessToken, member: MeResponse}` + `Set-Cookie: pb_rt=…; Domain=.pipibooks.com; Path=/v1/auth; HttpOnly; Secure; SameSite=Lax; Max-Age=2592000`. 허용 목록에 없는 `redirectUri`는 `400 INVALID_REDIRECT_URI`.
  - `POST /v1/auth/refresh`(쿠키) → `200 {accessToken}` + 새 쿠키 / `401 REFRESH_INVALID`. `POST /v1/auth/logout` → `204`, 쿠키 삭제.
  - `GET /v1/me` → `MeResponse(id, nickname, profileImageUrl, role, onboarded: Boolean, interests: List<String>)`; `PUT /v1/me/interests {groups: List<String>, skip: Boolean}` → `204` (그룹 1~3개, 아니면 `400 INVALID_INTERESTS`; `skip=true`면 관심 없이 온보딩 완료).
  - JWT: ES256, `sub`=회원 ID 문자열, `role`, `exp`=15분. 공개키 PEM은 event-collector가 쓴다(Task 12).
  - `interface MemberQuery { fun interestKdc(memberId: Long): Set<Int> }`; `class ServerEventPublisher { fun publish(msg: UserEventMessage) }` — `eventId`는 여기서 생성.

- [ ] **Step 1: 마이그레이션 `V2__member.sql`** — `member`(`onboarded_at` 포함), `social_account`(UNIQUE `provider, provider_user_id`), `refresh_token`(`token_hash char(64) UNIQUE`, `family_id bigint`, `device_info`, `expires_at`, `rotated_at`, `revoked_at`), `member_interest`(`member_id`, `kdc_major smallint`, PK 둘).
- [ ] **Step 2: 실패하는 테스트 작성**
  - `AuthServiceTest`(OAuth는 WireMock): `` `처음 로그인하면 회원과 소셜 계정을 만든다` ``, `` `같은 소셜 계정으로 다시 로그인하면 같은 회원이다` ``, `` `허용되지 않은 redirectUri는 INVALID_REDIRECT_URI` ``, `` `ADMIN_SOCIAL_ACCOUNTS에 있으면 ADMIN, 빠지면 다음 로그인에 USER` ``, `` `로그인하면 IDENTITY_LINK 이벤트를 Stream에 넣는다` ``
  - `RefreshTokenTest`: `` `재발급하면 새 토큰을 주고 이전 토큰은 교체됨으로 표시한다` ``, `` `교체 후 30초 안의 재사용은 같은 계열의 새 토큰을 준다` `` (Review Focus 4), `` `교체 후 30초가 지난 재사용은 계열 전체를 폐기하고 401` ``, `` `만료된 토큰은 401 REFRESH_INVALID` ``, `` `DB에는 토큰 원문이 아닌 SHA-256 해시만 있다` ``
  - `AuthControllerTest`: `` `로그인 응답은 정해진 속성의 Refresh 쿠키를 준다` `` (속성 7개 모두 검사), `` `CORS는 pipibooks.com만 credentials와 함께 허용한다` `` (`Origin: https://evil.example` → `Access-Control-Allow-Origin` 없음), `` `허용 헤더에 Authorization과 X-Anonymous-Id가 있다` ``
  - `MeControllerTest`: `` `토큰 없이 /v1/me는 401` ``, `` `관심 그룹 4개는 400 INVALID_INTERESTS` ``, `` `건너뛰기도 onboarded를 true로 만든다` ``, `` `관심 그룹은 KDC 대분류로 저장된다` `` (`HUMANITIES` → 1, 2)
  - `FeedServiceTest`에 추가: `` `로그인 회원은 관심 KDC가 점수에 반영된다` ``
- [ ] **Step 3: 실패 확인** — Run: `cd services && ./gradlew :core:test` → FAIL
- [ ] **Step 4: 구현** — 카카오: `https://kauth.kakao.com/oauth/token` → `https://kapi.kakao.com/v2/user/me`(닉네임·프로필 이미지만). 구글: `https://oauth2.googleapis.com/token` → `id_token` 검증 후 `sub`, `name`, `picture`(scope `openid profile`). 기본 URL은 설정값으로 두어 WireMock으로 바꾼다. `/v1/admin/**`은 `hasRole('ADMIN')`, 나머지 공개 경로는 `permitAll`.
- [ ] **Step 5: 통과 확인** — Run: `cd services && ./gradlew :core:build` → BUILD SUCCESSFUL
- [ ] **Step 6: 커밋** — `feat(core): 소셜 로그인과 토큰 재발급 추가`

### Task 9: 웹 로그인·온보딩·내 정보

**Files:**
- Create: `clients/web/src/features/auth/{AuthProvider.tsx,useAuth.ts,oauth.ts,RequireAuth.tsx}`, `clients/web/src/app/{login/page.tsx,auth/callback/[provider]/page.tsx,onboarding/page.tsx,me/page.tsx}`
- Modify: `clients/web/src/lib/api.ts`(토큰 주입·401 시 1회 재발급), `clients/web/e2e/`

**Interfaces:**
- Consumes: Task 8 API, Task 6 `INTEREST_GROUPS`
- Produces: `useAuth(): {status: 'loading'|'anonymous'|'authenticated', member, accessToken, login(provider, returnTo), logout()}`; `<RequireAuth>`는 미로그인 시 `/login?returnTo=<현재 경로>`로 보낸다. 재발급은 `navigator.locks.request('pb-refresh', …)`로 탭 사이 한 번만 진행한다. 환경 변수 `NEXT_PUBLIC_KAKAO_CLIENT_ID`, `NEXT_PUBLIC_GOOGLE_CLIENT_ID`.

- [ ] **Step 1: 실패하는 테스트 작성**
  - Vitest: `` state가 다르면 콜백은 로그인 요청을 보내지 않는다 ``, `` 401이면 한 번 재발급 후 원래 요청을 다시 보낸다 ``, `` 재발급도 401이면 anonymous가 된다 ``, `` 온보딩은 4번째 그룹을 고를 수 없다 ``
  - Playwright(가짜 OAuth: 테스트용 `/auth/callback/kakao?code=test&state=<저장값>` 직접 진입, API 목): `로그인 후 onboarded가 false면 /onboarding으로 간다`, `returnTo가 있으면 로그인 후 그 경로로 돌아간다`, `로그아웃하면 /me 접근 시 /login으로 간다`
- [ ] **Step 2: 실패 확인** — Run: `pnpm --filter web test` → FAIL
- [ ] **Step 3: 구현** — `returnTo`는 같은 출처의 경로(`/`로 시작, `//` 아님)만 허용한다.
- [ ] **Step 4: 통과 확인** — Run: `pnpm --filter web lint && pnpm --filter web test && pnpm test:e2e:docker` → PASS
- [ ] **Step 5: 커밋** — `feat(web): 소셜 로그인과 온보딩 화면 추가`

### Task 10: 도서 검색과 위시 (core `library`, 표준 예시)

**Files:**
- Create: `core/.../library/{ShelfItemEntity.kt,ShelfItemRepository.kt,CatalogClient.kt,BookSearchService.kt,ShelfService.kt,ShelfQuery.kt,BookSearchController.kt,ShelfController.kt,LibraryDtos.kt}`, `resources/db/migration/core/V3__shelf_item.sql`, `.claude/skills/add-api-endpoint/SKILL.md`
- Modify: `services/CLAUDE.md`(표준 예시 지정), `CLAUDE.md`(색인), `core/.../feed/FeedService.kt`(서재 유사도·인기도·서재 제외), `infra/env/core.env.example`(`CATALOG_WORKER_URL`)

**Interfaces:**
- Consumes: Task 2 내부 API, Task 4 `BookQuery`, Task 8 인증·`ServerEventPublisher`
- Produces:
  - `interface CatalogClient { fun search(q: String, page: Int): CatalogSearchResult; fun import(isbn13: String): Long }` — 실패 시 `ApiException(503, "CATALOG_UNAVAILABLE")`, 없는 ISBN은 `ApiException(404, "BOOK_NOT_FOUND")`.
  - `interface ShelfQuery { fun bookIdsOf(memberId: Long): Set<Long>; fun recentAddCounts(since: Instant): Map<Long, Int>; fun kdcRatioOf(memberId: Long): Map<Int, Double> }`
  - `GET /v1/books/search?q=&page=` → `{items:[{isbn13,title,author,publisher,coverUrl,inShelf}], nextPage: Int?}` (`q` 1~100자, 아니면 `400 INVALID_QUERY`; Redis 10분 캐시, 로그인 시에만 `inShelf` 계산).
  - `POST /v1/shelf {isbn}` → 새로 만들면 `201`, 이미 있으면 `200`, 본문 `ShelfItemResponse(id, status, book: BookSummaryResponse, createdAt)`. `GET /v1/shelf?cursor=&size=` → `{items, nextCursor}`. `DELETE /v1/shelf/{id}` → `204`, 남의 것·없음은 `404 SHELF_ITEM_NOT_FOUND`.

- [ ] **Step 1: 마이그레이션 `V3__shelf_item.sql`** — `docs/domain/model.md`의 전체 컬럼(`status`, `started_on`, `finished_on`, `rating`, `review`, `created_at`, `updated_at`), UNIQUE(`member_id`, `book_id`), `book_id` FK 없음, `(created_at)` 인덱스.
- [ ] **Step 2: 실패하는 테스트 작성**
  - `ShelfServiceTest`: `` `위시에 담으면 WISH 항목과 SHELF_ADD 이벤트가 생긴다` ``, `` `같은 책을 다시 담으면 기존 항목을 돌려주고 이벤트를 다시 보내지 않는다` ``, `` `카탈로그 장애면 CATALOG_UNAVAILABLE이고 항목을 만들지 않는다` ``
  - `ShelfControllerTest`: `` `새로 담으면 201, 다시 담으면 200` ``, `` `남의 항목 삭제는 404 SHELF_ITEM_NOT_FOUND` ``, `` `목록은 최신순 커서 페이지네이션이다` `` (size 2로 3건 → 2+1), `` `size 51은 400` ``
  - `BookSearchControllerTest`(WireMock catalog-worker): `` `같은 검색어는 10분 안에 catalog-worker를 다시 부르지 않는다` ``, `` `빈 검색어와 101자는 400 INVALID_QUERY` ``, `` `로그인하면 서재에 있는 책에 inShelf true` ``
  - `FeedServiceTest`에 추가: `` `서재에 있는 책의 카드는 피드에서 빠진다` ``, `` `최근 7일 위시가 많은 카드가 앞선다` ``
- [ ] **Step 3: 실패 확인** — Run: `cd services && ./gradlew :core:test` → FAIL
- [ ] **Step 4: 구현** — `CatalogClient`는 `RestClient`(연결 1초, 읽기 5초 타임아웃).
- [ ] **Step 5: 통과 확인** — Run: `cd services && ./gradlew build` → BUILD SUCCESSFUL
- [ ] **Step 6: `add-api-endpoint` 스킬 작성** — 이 모듈을 기준으로: 패키지 배치(엔티티·리포지토리는 모듈 내부, 다른 모듈에는 `*Query`/`*Service` 인터페이스만), DTO 이름(`*Request`/`*Response`), `ApiException` 코드 규칙, 커서 페이지네이션, MockMvc 테스트 목록(성공·검증 실패·권한·없음), `scripts/generate-api-client.sh` 재생성. `services/CLAUDE.md`의 "표준 예시" 줄을 `com.pipibooks.core.library`로 바꾼다.
- [ ] **Step 7: 커밋** — `feat(core): 도서 검색과 위시 API 추가`

### Task 11: 웹 검색·위시 화면

**Files:**
- Create: `clients/web/src/app/{search/page.tsx,shelf/page.tsx}`, `clients/web/src/features/library/{useWish.ts,SearchResults.tsx,ShelfList.tsx}`
- Modify: `clients/web/src/features/feed/ActionRail.tsx`(위시 버튼), `clients/web/src/features/auth/oauth.ts`(대기 중인 위시)

**Interfaces:**
- Consumes: Task 10 API, Task 9 `useAuth`
- Produces: `useWish(): {add(isbn): Promise<void>, pending}` — 비로그인이면 `sessionStorage['pb_pending_wish']={isbn, returnTo}`를 남기고 로그인으로 보낸다. 로그인 완료 후 이 값을 처리하고 지운다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - Vitest: `` 비로그인 위시는 대기 값을 남기고 로그인으로 보낸다 ``, `` 로그인 후 대기 위시를 한 번만 처리한다 ``, `` 503이면 '잠시 후 다시 시도해 주세요'를 보여 준다 ``
  - Playwright: `검색 결과에서 위시에 담으면 서재에 나타난다`, `비로그인으로 피드 카드의 위시를 누르면 로그인 후 같은 카드로 돌아와 담긴다`
- [ ] **Step 2: 실패 확인** — Run: `pnpm --filter web test` → FAIL
- [ ] **Step 3: 구현** — 검색 입력은 300ms 디바운스, 빈 문자열은 요청하지 않는다.
- [ ] **Step 4: 통과 확인** — Run: `pnpm --filter web lint && pnpm --filter web test && pnpm test:e2e:docker` → PASS
- [ ] **Step 5: 커밋** — `feat(web): 도서 검색과 위시 화면 추가`

### Task 12: 이벤트 수집 (event-collector)

**Files:**
- Create: `event-collector/src/main/kotlin/com/pipibooks/event/{ingest/EventController.kt,ingest/EventRequest.kt,ingest/EventValidator.kt,ingest/TokenVerifier.kt,stream/StreamWriter.kt,stream/EventConsumer.kt,stream/EventBatchWriter.kt,stream/CardCounterWriter.kt,partition/PartitionMaintainer.kt,config/CorsConfig.kt}`, `resources/db/migration/events/V1__user_event.sql`
- Modify: `event-collector/build.gradle.kts`(event-schema, Redis reactive, nimbus), `infra/env/event-collector.env.example`(`JWT_PUBLIC_KEY_PEM`, `CORS_ALLOWED_ORIGIN`), `docs/domain/model.md`, `infra/scripts/smoke-test.sh`(`POST /v1/events` → 202)

**Interfaces:**
- Consumes: Task 5 `UserEventMessage`, `RedisKeys`, `EventType`; Task 8 JWT 공개키
- Produces:
  - `POST /v1/events` — `Content-Type` `application/json` 또는 `text/plain`(JSON 본문), 본문 스펙 §8. 1~50건이 아니면 `400 INVALID_BATCH`, 클라이언트가 보낼 수 없는 타입(`SHELF_ADD`·`STATUS_CHANGE`·`IDENTITY_LINK`·`MEMBER_DELETED`)은 `400 INVALID_EVENT_TYPE`, 성공 `202`.
  - `events.user_event`: 스펙 §4.2 컬럼 + `event_id uuid`, `PARTITION BY RANGE (occurred_at)`, UNIQUE(`event_id`, `occurred_at`). 파티션 이름 `user_event_yyyymm`.
  - `EventConsumer`: 그룹 `collector`, 500건 또는 1초, `XAUTOCLAIM` 최소 대기 60초, 전달 3회 초과 시 `events:dead`. 처리 시 `CARD_IMPRESSION`은 `SADD feed:seen:<anonymousId>`(TTL 7일), 카드 이벤트는 카운터 해시 증가(TTL 30일).

- [ ] **Step 1: 마이그레이션 `V1__user_event.sql`** — 파티션 부모 테이블과 `events.ensure_partitions(from_month date, months int)` 함수.
- [ ] **Step 2: 실패하는 테스트 작성**
  - `EventControllerTest`: `` `정상 배치는 202이고 Stream에 넣는다` ``, `` `text/plain 본문도 받는다` ``, `` `51건은 400 INVALID_BATCH` ``, `` `서버 전용 타입은 400 INVALID_EVENT_TYPE` ``, `` `유효한 토큰이면 memberId를 붙인다` ``, `` `무효한 토큰이면 거부하지 않고 익명으로 넣는다` ``, `` `occurredAt이 1970년이나 2099년이면 수신 시각으로 바꾼다` `` (Review Focus 3), `` `CORS는 pipibooks.com만 허용한다` ``
  - `EventConsumerTest`: `` `500건 또는 1초가 되면 배치 저장 후 ACK한다` ``, `` `같은 eventId는 한 번만 저장된다` ``, `` `배치 중 한 건이 실패하면 나머지는 저장되고 그 건만 남는다` `` (Review Focus 3), `` `세 번 실패한 메시지는 events:dead로 간다` ``, `` `CARD_IMPRESSION은 본 카드 집합에 들어간다` ``, `` `CARD_SKIP은 카운터를 올린다` ``
  - `PartitionMaintainerTest`: `` `기동하면 이번 달부터 2개월 뒤까지 파티션이 있다` ``, `` `두 번 실행해도 오류가 없다` ``
- [ ] **Step 3: 실패 확인** — Run: `cd services && ./gradlew :event-collector:test` → FAIL
- [ ] **Step 4: 구현** — Stream `XADD MAXLEN ~ 100000`. 파티션 유지는 기동 시와 `@Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")`.
- [ ] **Step 5: 통과 확인** — Run: `cd services && ./gradlew build && bash infra/scripts/build-local.sh && bash infra/scripts/smoke-test.sh` → BUILD SUCCESSFUL, `SMOKE TEST PASSED`
- [ ] **Step 6: 커밋** — `feat(event-collector): 이벤트 수신과 배치 적재 추가`

### Task 13: 웹 이벤트 트래커와 공유 버튼

**Files:**
- Create: `clients/packages/shared/src/tracker.ts`, `clients/web/src/features/feed/useCardTracking.ts`, `clients/web/src/features/feed/ShareButton.tsx`
- Modify: `clients/web/src/features/feed/{FeedView.tsx,CardItem.tsx}`, `clients/web/src/lib/api.ts`(피드 요청에 `X-Anonymous-Id`)

**Interfaces:**
- Consumes: Task 12 `POST /v1/events`, Task 7 `CardItem` 콜백, Task 6 `getAnonymousId`
- Produces: `createTracker({endpoint, getToken, anonymousId, sessionId, platform: 'web', appVersion, flushIntervalMs = 5000, maxBatch = 50})` → `{track(event), flush(), flushOnHide()}`. `track`은 `eventId = crypto.randomUUID()`, `occurredAt = new Date().toISOString()`를 채운다. `flushOnHide`는 `visibilitychange`(hidden)에서 `navigator.sendBeacon(endpoint, new Blob([json], {type: 'text/plain'}))`.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `tracker.test.ts`(가짜 타이머): `` 50건이 모이면 바로 보낸다 ``, `` 5초가 지나면 모인 만큼 보낸다 ``, `` 페이지가 숨겨지면 text/plain Beacon으로 보낸다 ``, `` 전송이 실패하면 다음 전송에 다시 넣되 50건을 넘기지 않는다 ``
  - `useCardTracking.test.tsx`: `` 카드가 활성화되면 CARD_IMPRESSION ``, `` 2초 안에 첫 슬라이드에서 떠나면 CARD_SKIP과 CARD_DWELL ``, `` 2초가 지나 떠나면 CARD_DWELL만 ``, `` 슬라이드가 바뀌면 SLIDE_VIEW ``
  - `ShareButton.test.tsx`: `` Web Share API가 있으면 공유 시트를 연다 ``, `` 없으면 링크를 복사하고 '링크를 복사했어요'를 보여 준다 ``, `` 누르면 CARD_SHARE를 보낸다 ``
- [ ] **Step 2: 실패 확인** — Run: `pnpm --filter @pipibooks/shared test && pnpm --filter web test` → FAIL
- [ ] **Step 3: 구현** — `NEXT_PUBLIC_EVENTS_URL`(운영 `https://api.pipibooks.com/v1/events`).
- [ ] **Step 4: 통과 확인** — Run: `pnpm --filter @pipibooks/shared test && pnpm --filter web test && pnpm test:e2e:docker` → PASS
- [ ] **Step 5: 커밋** — `feat(web): 이벤트 트래커와 공유 버튼 추가`

### Task 14: 관리자 API (core `admin`)

**Files:**
- Create: `core/.../admin/{AdminCardController.kt,AdminDraftController.kt,AdminCollectController.kt,CardReviewQuery.kt,AdminDtos.kt}`
- Modify: `core/.../card/CardService.kt`(편집·숨김·다시 발행), `core/.../library/CatalogClient.kt`(수집 실행 API)

**Interfaces:**
- Consumes: Task 4 `CardService`, `BookQuery`; Task 2 `/internal/collect-runs`; Task 12 카운터 키
- Produces (모두 `ADMIN` 필요, 아니면 `403`):
  - `GET /v1/admin/drafts?cursor=` — 카드가 없는 책의 최신 초안 중 `auto_publishable=false`, 응답에 `blockedReasons`. `POST /v1/admin/drafts/{id}/publish` → `201 {cardId}`(타이포 표지 허용). `POST /v1/admin/drafts/{id}/edit` → `DRAFT` 카드 생성 `201 {cardId}`.
  - `GET /v1/admin/cards?filter=RECENT_AUTO|NEEDS_REVIEW&cursor=` — `NEEDS_REVIEW`는 최근 7일 노출 30회 이상, 넘김 비율 내림차순, 각 항목에 `impressions`, `skipRate`.
  - `PUT /v1/admin/cards/{id}/slides {slides: List<SlidePayload>}`(1~10장, `QUOTE` 허용) → `204`, `edited_at` 기록. `POST /v1/admin/cards/{id}/{hide|publish|reapply-draft}` → `204`.
  - `GET /v1/admin/collect-runs`, `POST /v1/admin/collect-runs {type, isbns?, keyword?}`(ISBN 최대 100개) → catalog-worker로 전달.

- [ ] **Step 1: 실패하는 테스트 작성** — `` `USER 토큰은 403` ``, `` `대기열에는 카드 없는 책의 차단 초안만 나온다` ``, `` `대기열 초안을 발행하면 피드 후보가 된다` ``, `` `노출 29회 카드는 점검 목록에 없다` ``, `` `넘김 비율이 높은 순으로 나온다` ``, `` `슬라이드 11장은 400 INVALID_SLIDES` ``, `` `숨긴 카드는 GET /v1/cards에서 404` ``, `` `ISBN 101개 시드는 400` ``
- [ ] **Step 2: 실패 확인** — Run: `cd services && ./gradlew :core:test` → FAIL
- [ ] **Step 3: 구현** — 카운터는 최근 7일 키를 `HMGET`으로 합산한다.
- [ ] **Step 4: 통과 확인** — Run: `cd services && ./gradlew build` → BUILD SUCCESSFUL. 이어서 `bash scripts/generate-api-client.sh`로 생성물을 갱신해 같은 커밋에 넣는다(CI가 최신 여부를 검사).
- [ ] **Step 5: 커밋** — `feat(core): 관리자 카드·초안·수집 API 추가`

### Task 15: 관리자 화면 (web `/admin`)

**Files:**
- Create: `clients/web/src/app/admin/{layout.tsx,page.tsx,drafts/page.tsx,cards/page.tsx,cards/[id]/page.tsx,collect/page.tsx}`, `clients/web/src/features/admin/{SlideEditor.tsx,CardPreview.tsx}`

**Interfaces:**
- Consumes: Task 14 API, Task 7 `SlideCarousel`(미리보기), Task 9 `RequireAuth`
- Produces: `/admin`(요약: 대기열 수·점검 필요 수·최근 수집), `/admin/drafts`, `/admin/cards?filter=`, `/admin/cards/[id]`(슬라이드 편집·QUOTE 추가·숨김·다시 발행·최신 초안 적용), `/admin/collect`(이력·ISBN/키워드 시드·수동 실행). `role !== 'ADMIN'`이면 "권한이 없습니다".

- [ ] **Step 1: 실패하는 테스트 작성** — Vitest: `` USER는 권한 없음 화면을 본다 ``, `` 슬라이드 편집기는 10장을 넘게 추가할 수 없다 ``, `` 저장 전 미리보기가 편집 내용을 반영한다 ``; Playwright: `대기열 초안을 미리 보고 발행하면 목록에서 사라진다`
- [ ] **Step 2: 실패 확인** — Run: `pnpm --filter web test` → FAIL
- [ ] **Step 3: 구현** — 서버 렌더링 없이 클라이언트 컴포넌트로 만든다(스펙 §9).
- [ ] **Step 4: 통과 확인** — Run: `pnpm --filter web lint && pnpm --filter web test && pnpm test:e2e:docker` → PASS
- [ ] **Step 5: 커밋** — `feat(web): 관리자 화면 추가`

### Task 16: 백업·모니터링·WAF·운영 설정

**Files:**
- Create: `infra/backup/{Dockerfile,backup.sh,prune.sh}`, `infra/scripts/restore-rehearsal.sh`, `scripts/tests/backup-prune.test.sh`, `docs/ops/backup.md`, `docs/ops/monitoring.md`, `infra/env/backup.env.example`(`R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`, `R2_BUCKET`, `AGE_RECIPIENT`, `HEALTHCHECK_BACKUP_URL`, `PGPASSWORD`)
- Modify: `infra/compose/compose.prod.yml`(`backup` 서비스, 볼륨 `backups`), `infra/scripts/deploy.sh`(73행 TODO: up 전에 `docker compose … run --rm backup backup.sh pre-deploy`), `.github/workflows/release.yml`(`pipibooks-backup` 이미지 추가, 이미지 수 6개), `docs/ops/deploy.md`, `docs/conventions/git.md`(이미지 수), `infra/CLAUDE.md`, `CLAUDE.md`(색인)

**Interfaces:**
- Produces: `backup.sh [daily|pre-deploy]` — `pg_dump -Fc` → `age -r $AGE_RECIPIENT` → `/backups/<종류>/pipibooks-<UTC타임스탬프>.dump.age` → `rclone copy`로 R2 → 하트비트. `prune.sh <dir>` — 일간 7·주간(일요일분) 4·월간(1일분) 6만 남김. 컨테이너는 `crond`로 03:00 KST에 `daily` 실행.

- [ ] **Step 1: 실패하는 테스트 작성** — `backup-prune.test.sh`: 가짜 파일 120일치로 `prune.sh`를 돌리면 정확히 7+4+6개 이하가 남고 가장 최근 파일은 항상 남는다. `deploy.test.sh`(기존)에 `` 백업이 실패하면 배포를 중단하고 실행 중인 버전을 유지한다 `` 추가.
- [ ] **Step 2: 실패 확인** — Run: `pnpm test:scripts` → FAIL
- [ ] **Step 3: 구현** — 이미지는 `postgres:18.6-alpine` 기반에 `age`, `rclone`, `curl`을 버전 고정으로 설치한다. `restore-rehearsal.sh <백업 파일> <age 개인키 파일>`은 임시 `postgres:18.6-alpine` 컨테이너에 복원하고 `core.card`, `catalog.book`, `events.user_event` 행 수를 출력한 뒤 컨테이너를 지운다.
- [ ] **Step 4: 통과 확인** — Run: `pnpm test:scripts && bash infra/scripts/build-local.sh && bash infra/scripts/smoke-test.sh` → PASS(스모크 테스트는 `backup.sh daily`를 R2 없이(`R2_BUCKET` 비움) 실행해 로컬 파일 생성까지 확인)
- [ ] **Step 5: 운영 문서** — `backup.md`(복원 절차, 월 1회 리허설 체크리스트, age 개인키 보관), `monitoring.md`(UptimeRobot 2개 모니터·5분, Healthchecks.io 체크 2개: 백업 일 1회·유예 2시간, 수집 일 1회·유예 2시간, WAF 규칙 표현식 `(http.host eq "api.pipibooks.com" and (starts_with(http.request.uri.path, "/v1/auth/") or http.request.uri.path eq "/v1/events"))`, 초기값 IP당 10초 60회 → 차단 10초)
- [ ] **Step 6: 계정 준비 [사용자]** — R2 버킷·토큰, Healthchecks.io 체크 2개, UptimeRobot 모니터 2개, WAF 규칙을 `docs/ops/monitoring.md`대로 만든다. age 키 쌍은 `age-keygen`으로 만들어 공개키만 `backup.env`에 넣는다.
- [ ] **Step 7: 커밋** — `feat(infra): 백업과 외부 모니터링 구성 추가`

### Task 17: 국립중앙도서관 실측 [인증키 필요]

**Files:**
- Modify: `services/catalog-worker/src/test/resources/nlk/*.json`(실제 응답 녹화로 교체, 키 값 제거), `services/catalog-worker/src/main/resources/application.yml`(`nlk.page-size`, `nlk.permits-per-second`), `docs/specs/2026-11-06-subproject-1-core-mvp-design.md` §3·§15

**Interfaces:**
- Consumes: Task 2 `NlkClient`

- [ ] **Step 1: 키 준비 [사용자]** — 승인된 `cert_key`를 `$CLAUDE_JOB_DIR/tmp/nlk.env` 또는 운영 `infra/env/catalog-worker.env`에 둔다(저장소에 넣지 않음).
- [ ] **Step 2: 시험 호출(10회 안팎)** — `page_size` 50·100·500(`013` 여부), 최근 7일 하루씩 `TOTAL_COUNT`, 같은 표본 200건의 표지·소개(100자 이상) 채움률, 1초 간격 연속 10회 응답 시간. 결과를 스펙 §3 표에 "실측(날짜)"으로 추가하고 §15 항목을 지운다.
- [ ] **Step 3: 반영** — 실제 응답 3종(기간 조회, 제목 검색, ISBN 조회)을 녹화해 픽스처를 교체한다. 필드 이름이나 형식이 문서와 다르면 `BookNormalizerTest`에 재현 테스트를 먼저 넣고 고친다. 채움률이 매우 낮으면(표지·소개 동시 충족 20% 미만) 멈추고 자동 발행 기준을 사용자와 다시 논의한다.
- [ ] **Step 4: 확인·커밋** — Run: `cd services && ./gradlew :catalog-worker:build` → BUILD SUCCESSFUL. 커밋 `test(catalog-worker): 국립중앙도서관 실제 응답으로 픽스처 교체`

### Task 18: v0.1.0 제한 공개

**Files:**
- Modify: `infra/env/*.env`(운영, 저장소 밖), `docs/ops/deploy.md`(OAuth 앱 설정 절차), `infra/cloudflared/config.yml`(변경 없음 확인)

- [ ] **Step 1: OAuth 앱 [사용자]** — 카카오: 앱 생성, 카카오 로그인 활성화, 동의 항목(닉네임·프로필 사진), Redirect URI `https://pipibooks.com/auth/callback/kakao`, 팀원으로 테스트 사용자 등록. 구글: OAuth 동의 화면 "테스트", scope `openid profile`, 테스트 사용자 등록, Redirect URI `https://pipibooks.com/auth/callback/google`. 절차를 `docs/ops/deploy.md`에 적는다.
- [ ] **Step 2: 운영 시크릿 [사용자]** — `~/pipibooks-prod/infra/env/`에 core·catalog-worker·event-collector·backup env를 채운다. JWT 키: `openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt`(개인키), `openssl ec -pubout`(공개키).
- [ ] **Step 3: 릴리스** — `release` 스킬로 `v0.1.0`. 첫 푸시 후 GHCR 새 패키지(`pipibooks-core`, `pipibooks-backup`)를 Public으로 바꾼다 **[사용자]**. 옛 `pipibooks-core-api`는 배포 확인 후 정리한다.
- [ ] **Step 4: 배포·확인** — `bash infra/scripts/deploy.sh v0.1.0` → 헬스 OK. 수동 수집 1회(`/admin/collect`) 후 `/`에 카드가 보이는지, 카카오·구글 로그인 → 온보딩 → 위시 → 서재, `events.user_event` 행 증가, 다음 날 백업 파일·R2·Healthchecks 하트비트를 확인한다.
- [ ] **Step 5: 기록** — 메모리 `subproject-0-status`를 서브프로젝트 1 진행 상태로 갱신(제한 공개 시작일). 1~2주 사용 후 피드 가중치·WAF 값 조정 사항을 Task 20 이슈에 적는다.

### Task 19: 카드 신고 (v0.2.0 범위)

**Files:**
- Create: `core/.../card/{CardReportEntity.kt,CardReportService.kt,CardReportController.kt}`, `core/.../admin/AdminReportController.kt`, `resources/db/migration/core/V4__card_report.sql`, `clients/web/src/features/feed/ReportSheet.tsx`, `clients/web/src/app/admin/reports/page.tsx`
- Modify: `clients/web/src/features/feed/ActionRail.tsx`(더보기 → 신고), `docs/domain/model.md`

**Interfaces:**
- Produces: `POST /v1/cards/{id}/reports {reason: INAPPROPRIATE|WRONG_INFO|INSUFFICIENT|OTHER, comment?: String(최대 300자)}` → `201`, 같은 회원 두 번째는 `409 ALREADY_REPORTED`, 비로그인 `401`. `GET /v1/admin/reports?status=OPEN&cursor=`(신고 수 내림차순, 카드별 묶음), `POST /v1/admin/reports/{cardId}/{dismiss|hide}` → `204`(해당 카드의 OPEN 신고를 `DISMISSED`/`ACTIONED`로).

- [ ] **Step 1: 실패하는 테스트 작성** — `` `같은 회원이 같은 카드를 두 번 신고하면 409` ``, `` `신고가 쌓여도 카드는 자동으로 숨겨지지 않는다` ``, `` `숨김 처리하면 신고가 ACTIONED가 되고 카드는 HIDDEN` ``, `` `설명 301자는 400` ``; Vitest `` 사유를 고르지 않으면 보낼 수 없다 ``
- [ ] **Step 2: 실패 확인 → 구현 → 통과 확인** — Run: `cd services && ./gradlew build && pnpm --filter web test` → PASS
- [ ] **Step 3: 커밋** — `feat(core): 카드 신고와 관리자 신고 목록 추가`

### Task 20: 약관·탈퇴·일반 공개 (v0.2.0)

**Files:**
- Create: `clients/web/src/app/{terms/page.tsx,privacy/page.tsx}`, `core/.../member/WithdrawService.kt`, `event-collector/.../stream/MemberDeletedHandler.kt`
- Modify: `core/.../member/MeController.kt`(`DELETE /v1/me`), `clients/web/src/app/me/page.tsx`(탈퇴), `clients/web/src/app/login/page.tsx`(약관 링크), `docs/ops/monitoring.md`(WAF 값)

**Interfaces:**
- Produces: `DELETE /v1/me` → `204` — `member.deleted_at`·`status=DELETED`, 닉네임 `탈퇴한 회원`, 프로필 이미지 `null`, `social_account`·`refresh_token`·`member_interest` 삭제, 쿠키 삭제, `MEMBER_DELETED` 발행. collector는 `UPDATE events.user_event SET member_id = NULL WHERE member_id = ?`. 같은 소셜 계정으로 다시 로그인하면 새 회원이 된다.

- [ ] **Step 1: 약관 문안 [사용자]** — 이용약관·개인정보처리방침 초안(수집 항목: 소셜 제공자 ID·닉네임·프로필 이미지, 행동 이벤트, `anonymous_id`; 보관·파기; 탈퇴 방법)을 작성해 사용자 확인을 받는다.
- [ ] **Step 2: 실패하는 테스트 작성** — `` `탈퇴하면 개인정보가 익명화되고 토큰이 무효가 된다` ``, `` `탈퇴 후 같은 소셜 계정 로그인은 새 회원이다` ``, `` `MEMBER_DELETED를 받으면 그 회원 이벤트의 member_id가 null이 된다` ``; Playwright `로그인 화면에 약관과 개인정보처리방침 링크가 있다`
- [ ] **Step 3: 실패 확인 → 구현 → 통과 확인** — Run: `cd services && ./gradlew build && pnpm --filter web test && pnpm test:e2e:docker` → PASS
- [ ] **Step 4: 공개 전환 [사용자]** — 구글 OAuth 동의 화면 "프로덕션 게시"(개인정보처리방침 URL `https://pipibooks.com/privacy`), 카카오 앱 설정, 제한 공개 기간 데이터로 WAF 값·피드 가중치 조정(`docs/ops/monitoring.md`, `application.yml`).
- [ ] **Step 5: 릴리스·마무리** — `release` 스킬로 `v0.2.0` → 배포·확인. 아키텍처 스펙 §12 서브프로젝트 1 완료 표시, 마일스톤 `1 코어 MVP` 종료, 메모리를 "서브프로젝트 1 완료, 다음은 2"로 갱신. 커밋 `feat(core): 회원 탈퇴와 약관 페이지 추가`

---

## 스펙 범위 중 이 계획에서 미루는 것 (의도적)

| 항목 | 미루는 곳 | 이유 |
|---|---|---|
| 서재 상태 전이(`READING`·`FINISHED`·`DROPPED`), 별점, 한줄평, `reading_log`, `quote`, `STATUS_CHANGE` 발행 | 서브프로젝트 2 | 기록·통계와 한 흐름으로 설계 |
| 통계(`stats` 모듈) | 서브프로젝트 2 | 기록 데이터가 먼저 필요 |
| 앱, Apple 로그인, 앱용 Refresh 토큰 본문 전달 | 서브프로젝트 3 | 앱 출시와 함께 |
| 신고 누적 자동 숨김 | 일반 공개 후 재검토 | 기준값을 정할 데이터가 없음 |
| Kover 커버리지 기준선 | 서브프로젝트 2 시작 시 | 표준 예시(Task 10) 이후 측정 |

## 이슈 번호 (계획 PR에서 기록)

| Task | 이슈 | 브랜치 |
|---|---|---|
| 1~20 | (계획 승인 후 생성) | |

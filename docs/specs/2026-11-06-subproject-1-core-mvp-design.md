# 서브프로젝트 1: 코어 MVP 설계

- 작성일: 2026-11-06
- 상태: 검토 대기
- 관련 ADR: 0004, 0005(일부 대체), 0006, 0008, 0011(신규: 카드 자동 발행)
- 상위 스펙: `docs/specs/2026-11-05-architecture-design.md` §12 서브프로젝트 1
- 이슈: #32

## 1. 목표와 범위

**목표**: 비로그인으로 피드를 넘겨 보고, 소셜 로그인 후 관심 분야에 맞춘 피드와 위시를 쓸 수 있는 웹 서비스를 제한 공개(`v0.1.0`)한 뒤 일반 공개(`v0.1.x`)로 전환한다. 카드는 사람 손을 거치지 않고도 계속 공급된다.

**완료 기준**
1. `v0.1.0` 제한 공개: 소셜 로그인(카카오·구글, 테스트 사용자), 관심 분야 온보딩, 신간 수집·운영자 시드, 카드 자동 발행과 관리자 사후 점검, 비로그인·로그인 피드, 위시·도서 검색, 카드 공유 페이지, 이벤트 수집, 백업·외부 모니터링·WAF
2. `v0.1.x` 일반 공개 전환: 사용자 카드 신고, 이용약관·개인정보처리방침, 회원 탈퇴(익명화), OAuth 앱 프로덕션 전환

**비목표**: 서재 상태 전이(`READING` 등)·별점·한줄평·독서 기록·통계(서브프로젝트 2), 앱·Apple 로그인(3), 학습 기반 추천(4), 신고 누적 자동 숨김, 이메일 수집

## 2. 결정 요약

| 항목 | 결정 | 근거 |
|---|---|---|
| 공개 단계 | 제한 공개 `v0.1.0` → 지인 사용 1~2주 → 일반 공개 `v0.1.x` | 공개 전환 작업이 작고, 실사용 데이터로 피드 가중치를 조정 |
| 카드 발행 | 기준을 통과한 초안은 자동 발행, 나머지는 관리자 대기열. 검수는 사후 점검으로 | ADR 0011 |
| 사후 점검 | 최근 자동 발행 목록, 넘김 비율 점검 목록, 일반 공개 시 사용자 신고 | 1인 운영에서 피드가 끊기지 않게 |
| 서재 범위 | 위시(`WISH`)만. 테이블은 `docs/domain/model.md` 전체 모양으로 생성 | 서브프로젝트별 세션 경계와 로드맵 일치 |
| 온보딩 | 첫 로그인 직후 관심 분야 1~3개(건너뛰기 가능) | 첫 피드부터 개인화 |
| 공유 | `/cards/{id}` 서버 렌더링 + OG 메타, Web Share API·링크 복사 | 일반 공개 후 유입 경로 |
| 피드 화면 | 세로 9:16 전체 화면, 슬라이드는 좌우, 카드는 상하. 데스크톱은 가운데 9:16 열 | 브레인스토밍에서 선택 |
| 구현 순서 | 얇은 수직 관통(수집 → 초안 → 발행 → 비로그인 피드)부터 | 위험한 부분을 먼저 화면으로 확인, 키 대기 중에도 진행 |

## 3. 국립중앙도서관 ISBN 서지정보 API 확인 결과 (상위 스펙 §13)

공개 문서 기준(2026-11-06). 인증키 승인 대기 중이라 실측은 하지 않았다.

| 항목 | 결과 |
|---|---|
| 요청 | `GET https://www.nl.go.kr/seoji/SearchApi.do`, 필수 `cert_key`·`result_style=json`·`page_no`·`page_size` |
| 검색 | `title`·`author`·`publisher`(형태소+ngram), `isbn`(우절단), `ebook_yn`, `form`, `sort`(`PUBLISH_PREDATE` 등)·`order_by` |
| 기간 조회 | `start_publish_date`·`end_publish_date`(yyyymmdd). **발행예정일** 기준이라 출간 전 도서가 섞일 수 있다 |
| 카드용 필드 | `BOOK_INTRODUCTION`(소개), `BOOK_TB_CNT`(목차), `KDC`, `PAGE`, `EA_ADD_CODE`(부가기호), `FORM`, `EBOOK_YN`, `SET_ISBN`, `TITLE_URL`(표지). `*_URL` 파일 필드는 2021년 2월까지만 있어 쓰지 않는다 |
| 표지 | 전체 약 48%만 존재(외부 실측 사례). `http://`가 섞여 https로 바꿔야 한다 |
| 소개 | 2020년 이후 도서 약 75~80%, 2014년 이전은 거의 없음(외부 실측 사례) |
| 호출 제한 | 공식 일일 한도 미기재. 오류 `013`(쪽당 건수 초과)이 있으나 최대값 미기재. 검색식당 500건 제한이 있다는 비공식 기록 |
| 오류 코드 | `000` 시스템, `010` 키 누락, `011` 키 무효, `012` 필수 파라미터 누락, `013` 쪽당 건수 초과 |

**결론**: 상위 스펙이 요구한 기능(검색, 기간 조회, 표지)은 모두 있어 ADR 0005의 단일 API 결정을 유지한다. 표지·소개 누락은 자동 발행 기준과 타이포 표지로 다룬다.

## 4. 카탈로그 수집과 카드 초안 (catalog-worker)

**클라이언트**: 코루틴 `WebClient`. 초당 호출 수(기본 1)와 `page_size`(기본 50)는 설정값. `000`·5xx·타임아웃은 지수 백오프로 3회 재시도, `010`·`011`은 즉시 실패와 오류 로그.

**수집 경로**
- 신간: 매일 새벽 발행예정일 최근 14일을 하루 단위로 나눠 조회한다. 구간이 겹치므로 실패한 날짜는 다음 실행에서 다시 수집된다(상위 스펙의 재처리 큐를 이것으로 갈음). 하루 결과가 500건을 넘으면 경고 로그를 남긴다.
- 운영자 시드: 관리자가 ISBN 목록 또는 키워드로 요청한다.
- 사용자 유입: `POST /internal/books/import {isbn}`로 동기 upsert한다.
- 실행 이력은 `catalog.collect_run`(종류, 구간·검색어, 상태, 조회·저장·초안 건수, 시작·종료 시각)에 남긴다. 끝나면 Healthchecks.io 하트비트를 보낸다.

**정규화**: ISBN13 검증숫자 확인(실패 시 버림), 표지 URL https 변환(없으면 `null`), 저자 역할어("지음", "옮김" 등) 정리, 발행예정일을 `date`로 변환.

**`catalog.book` 추가 컬럼**: `ea_add_code`, `form`, `ebook_yn`, `set_isbn`, `publish_predate`. `docs/domain/model.md`를 같은 PR에서 갱신한다.

**내부 API** (`internal` 네트워크 전용)
- `GET /internal/books/search?q=&page=`: 제목 검색 결과(저장하지 않음)
- `POST /internal/books/import {isbn}`: upsert 후 `bookId` 반환
- `POST /internal/collect-runs {type, isbns|keyword}`: 운영자 시드·수동 실행

**카드 초안**: 소개가 있는 도서에 `RuleBasedCardDraftGenerator` v1을 적용해 `catalog.card_draft`를 만든다.

| 슬라이드 | 내용 |
|---|---|
| `COVER` | 표지·제목·저자. 표지가 없으면 제목을 크게 쓰는 타이포 표지 |
| `HOOK` | 소개에서 고른 핵심 문장 1~2개 |
| `INFO` | 저자, 출판사, 출간, 쪽수, 분류 |
| `KEYWORDS` | 목차·소개에서 뽑은 키워드 3~6개 |
| `FOR_WHOM` | KDC 대분류별 템플릿 문장 |

**자동 발행 판정**: 초안에 `auto_publishable`과 `blocked_reasons`(배열)를 저장한다. 모두 만족해야 자동 발행한다.
- 소개 100자 이상
- 종이책 단권(`ebook_yn=N`, 세트 ISBN 아님)
- 부가기호 첫 자리가 5·6(학습참고서)이 아님
- 표지 URL 있음

## 5. 카드 발행과 피드 (core `card`·`feed`)

**자동 발행**: core 스케줄러가 5분마다 `core.sync_cursor`에 저장한 마지막 초안 ID 이후의 `catalog.card_draft`를 읽는다(읽기 전용, 스키마 소유권 유지). `auto_publishable`이면 `core.card`(`AUTO`, `PUBLISHED`)와 `card_slide`로 복사한다.
- 카드는 책당 하나(`card.book_id` UNIQUE). 새 초안이 와도 기존 카드를 덮어쓰지 않는다. 관리자가 "최신 초안 다시 적용"으로 바꾼다.
- 대기열 초안을 관리자가 편집하면 `DRAFT` 카드가 생기고, 발행하면 `PUBLISHED`가 된다. 편집하면 `card.edited_at`을 기록한다.

**피드 (`GET /v1/feed?cursor=`)**: `FeedRanker` 인터페이스, MVP는 `RuleBasedFeedRanker`. 가중치는 설정값.
- 관심 일치: `member_interest`의 KDC 대분류와 같으면 가점
- 서재 유사도: 내 위시 중 같은 KDC 대분류의 비율
- 인기도: 최근 7일 `shelf_item` 생성 수의 `log1p`(core 데이터만 사용)
- 신선도: `published_at` 기준 지수 감쇠(반감기 14일)
- 무작위 항: 피드 세션 ID를 시드로 사용
- 비로그인: 인기도·신선도·무작위 항만

**후보와 세션**
- 후보는 최근 발행 카드 최대 2,000개에서 내 서재 도서와 최근 7일 노출 카드를 뺀다. 노출 기록은 Redis 집합(회원 ID 또는 `anonymous_id`별, 7일 TTL)에 core가 응답할 때 쓴다.
- 커서 없는 요청은 상위 500개를 정렬해 Redis에 30분 캐시하고 20개씩 응답한다. 커서는 세션 ID와 위치를 담은 불투명 문자열이다.
- 세션이 만료되면 오류 없이 새 세션으로 이어 준다. 후보가 바닥나면 빈 목록과 `nextCursor: null`을 준다.
- 비로그인 클라이언트는 `anonymous_id`(UUID, localStorage)를 `X-Anonymous-Id` 헤더로 보낸다.

**카드 조회**: `GET /v1/cards/{id}`는 공개 API이며 공유 페이지가 쓴다. `HIDDEN`·`DRAFT`는 404 `CARD_NOT_FOUND`. 카드 응답에는 위시 담기용 `isbn`을 넣는다.

**넘김 비율 점검**: event-collector가 Redis 해시에 카드별 일 단위 카운터(노출·넘김·체류 합)를 올린다(30일 TTL, 키는 `libs/event-schema`에 정의). core 관리자 API가 이를 읽어 최근 7일 노출 30회 이상 카드를 넘김 비율 순으로 보여 준다. core는 `events` 스키마를 읽지 않는다.

## 6. 인증·회원·온보딩 (core `member`)

**로그인 흐름(웹)**
1. 브라우저가 `state`(CSRF)와 함께 제공자 인가 화면으로 이동한다. 돌아올 주소는 `https://pipibooks.com/auth/callback/{provider}`.
2. 콜백 페이지가 `{code, redirectUri, anonymousId}`를 `POST /v1/auth/{provider}`로 보낸다.
3. core가 코드를 교환하고(클라이언트 시크릿은 서버만 보유) `social_account`로 회원을 찾거나 만든다.
4. Access JWT(15분)는 응답 본문, Refresh 토큰은 쿠키 `pb_rt`(`Domain=.pipibooks.com`, `Path=/v1/auth`, `HttpOnly`·`Secure`·`SameSite=Lax`).
5. `POST /v1/auth/refresh`는 토큰을 교체한다. 이미 쓴 토큰이 오면 같은 계열(`family_id`)의 토큰을 모두 폐기한다. `POST /v1/auth/logout`은 현재 토큰을 폐기한다.
6. 로그인 시 `IDENTITY_LINK` 이벤트를 보낸다.

**보안·설정**
- JWT는 ES256. core만 개인키를 갖고, event-collector는 공개키로 검증한다.
- CORS는 `https://pipibooks.com`만 credentials와 함께 허용한다.
- 제공자에게서 닉네임·프로필 이미지만 받고 이메일은 받지 않는다.
- 관리자: 환경 변수 `ADMIN_SOCIAL_ACCOUNTS=KAKAO:<id>,GOOGLE:<id>`에 있는 계정은 로그인 때 `ADMIN`이 된다. `/v1/admin/**`은 `ADMIN` 필요, 웹 `/admin`은 Cloudflare Access로도 막는다.
- `local` 프로필과 E2E는 WireMock 가짜 OAuth 제공자를 쓴다.

**온보딩**: `member.onboarded_at`이 비어 있으면 로그인 직후 `/onboarding`으로 보낸다. 1~3개 선택 또는 건너뛰기 후 `onboarded_at`을 기록한다. `GET /v1/me`, `PUT /v1/me/interests`.

| 그룹 | 문학 | 인문·철학 | 사회·경제 | 자연과학 | 기술·생활 | 예술 | 역사·지리 |
|---|---|---|---|---|---|---|---|
| KDC 대분류 | 8 | 1·2 | 3 | 4 | 5 | 6 | 9 |

총류(0)·언어(7)는 그룹으로 보여 주지 않는다. 그룹 정의는 서버 상수와 `clients/packages/shared`에 함께 둔다.

## 7. 위시·검색 (core `library`, 표준 예시)

- `GET /v1/books/search?q=`: catalog-worker 제목 검색을 호출하고 Redis에 10분 캐시한다. 결과에 `inShelf`를 붙인다.
- `POST /v1/shelf {isbn}`: import → `WISH` 생성 → `SHELF_ADD` 발행. 이미 있으면 기존 항목을 200으로 돌려준다(멱등). import 실패는 503 `CATALOG_UNAVAILABLE`.
- `GET /v1/shelf?cursor=&size=`, `DELETE /v1/shelf/{id}`(남의 항목은 404).
- 이 모듈을 표준 예시로 지정하고 `services/CLAUDE.md`에서 가리킨다. `add-api-endpoint` 스킬의 기준 코드다.

## 8. 이벤트 수집 (event-collector)

- `POST /v1/events`(최대 50건): `{anonymousId, sessionId, platform, appVersion, events:[{type, cardId, bookId, slideIndex, dwellMs, occurredAt}]}`. 유효한 `Authorization`이 있으면 `member_id`를 붙이고, 무효해도 거부하지 않고 익명으로 처리한다.
- `libs/event-schema`로 검증 후 Stream `events:user`에 `XADD`(근사 MAXLEN 1,000,000)하고 `202`.
- 컨슈머 그룹: 500건 또는 1초 단위로 `events.user_event`에 배치 INSERT, 카드 카운터 갱신, ACK. 60초 이상 멈춘 메시지는 `XAUTOCLAIM`, 3회 실패하면 `events:dead`로 옮긴다.
- 월별 파티션은 기동 시와 매일 현재 달부터 2개월 뒤까지 만든다.
- core의 서버 이벤트(`SHELF_ADD`, `IDENTITY_LINK`)도 같은 Stream에 넣는다.

**클라이언트 트래커**(`clients/packages/shared`): 50건 또는 5초마다 전송, 페이지가 숨겨질 때 `sendBeacon`(인증 없이 익명).

| 이벤트 | 발생 조건 |
|---|---|
| `CARD_IMPRESSION` | 카드가 화면에 멈춰 활성화됨 |
| `SLIDE_VIEW` | 슬라이드가 바뀜(`slideIndex`) |
| `CARD_DWELL` | 카드를 떠날 때 체류 시간(`dwellMs`) |
| `CARD_SKIP` | 2초 안에 첫 슬라이드에서 다음 카드로 넘김 |
| `CARD_SHARE` | 공유 버튼을 누름 |

`docs/domain/feed.md`에 이 정의를 옮긴다.

## 9. 웹 (Next.js)

| 경로 | 내용 |
|---|---|
| `/` | 피드. 하단 탭 바(피드·검색·서재·내 정보), 오른쪽 아래 세로 버튼(위시·공유·더보기) |
| `/cards/[id]` | 공유 페이지(서버 렌더링, OG 제목·표지·소개 첫 문장) → "피드에서 더 보기" |
| `/login`, `/auth/callback/[provider]` | 소셜 로그인 |
| `/onboarding` | 관심 분야 선택 |
| `/search` | 도서 검색, 위시 담기 |
| `/shelf` | 내 위시 목록 |
| `/me` | 관심 분야 변경, 로그아웃 |

- 데스크톱: 가운데 9:16 열, 슬라이드는 화살표 버튼과 ←→, 카드는 휠과 ↑↓.
- 슬라이드 배경색은 KDC 대분류별로 다르게 한다.
- 비로그인 사용자가 위시를 누르면 로그인 후 원래 카드로 돌아와 위시 담기를 마저 끝낸다.
- API 호출은 `clients/packages/api-client`(OpenAPI 생성물)로만 한다. 생성 도구(후보: `openapi-typescript` + `openapi-fetch`)는 ADR을 먼저 쓴다.
- 웹 피드 Task 첫 단계에서 목 데이터로 피드 화면을 확인받은 뒤 API를 연결한다.

**관리자 (`/admin`)**
- 미발행 초안: 걸린 사유와 함께 표시, 미리보기 → 편집 → 발행
- 최근 자동 발행: 숨김·편집
- 점검 필요: 넘김 비율 상위 카드
- 카드 편집: 슬라이드 문구 수정, `QUOTE` 추가, 숨김·다시 발행, 최신 초안 다시 적용
- 수집: 실행 이력, ISBN·키워드 시드, 수동 실행

## 10. 운영

- **백업**: `backup` 컨테이너가 매일 `pg_dump -Fc` → age 암호화 → 로컬 볼륨과 Cloudflare R2. 보관 일간 7·주간 4·월간 6. 성공 시 Healthchecks.io 하트비트. `deploy.sh`의 마이그레이션 전 백업 TODO를 채운다. `infra/scripts/restore-rehearsal.sh`로 월 1회 임시 컨테이너 복원 리허설. 절차는 `docs/ops/backup.md`.
- **외부 모니터링**: UptimeRobot(무료)으로 `https://pipibooks.com/healthz`, `https://api.pipibooks.com/actuator/health`를 5분 간격 확인. 백업·수집은 Healthchecks.io 하트비트.
- **WAF**: Cloudflare 무료 요청 빈도 제한 규칙 1개에 `/v1/auth/*`와 `/v1/events`를 묶는다. 값은 일반 공개 전에 실제 트래픽으로 조정한다. 설정값과 절차는 `docs/ops/`에 둔다.
- **시크릿 추가**: 국립중앙도서관 키, 카카오·구글 클라이언트 시크릿, JWT ES256 키 쌍, `ADMIN_SOCIAL_ACCOUNTS`, R2 키, age 키, Healthchecks.io URL. `infra/env/*.env.example`에 항목을 추가한다.
- **스모크 테스트**: `smoke-test.sh`에 피드 조회와 이벤트 전송(202)을 추가한다.

## 11. 공유 코드와 스킬

| 위치 | 내용 |
|---|---|
| `services/libs/common` | Problem Details 오류 모델, TSID, 커서 인코딩 |
| `services/libs/event-schema` | 이벤트 타입·페이로드, Stream 키, 카드 카운터 키 |
| `clients/packages/shared` | 슬라이드 타입 스키마, 관심 분야 그룹, 이벤트 트래커 |
| `clients/packages/api-client` | core OpenAPI 생성물. CI가 최신 여부를 검사 |

- `add-migration` 스킬: 첫 Flyway 마이그레이션을 만든 직후 작성한다.
- `add-api-endpoint` 스킬: 위시 API 완성 직후 그 코드를 기준으로 작성한다.

## 12. 일반 공개 전환 (`v0.1.x`)

- 카드 신고: `core.card_report`(`card_id`, `member_id`, 사유 `INAPPROPRIATE`/`WRONG_INFO`/`INSUFFICIENT`/`OTHER`, 설명, 처리 상태). (카드, 회원) UNIQUE, 로그인 필수, 자동 숨김 없음. 관리자 신고 목록(신고 수 순)에서 편집·숨김·기각.
- 이용약관·개인정보처리방침 페이지(`/terms`, `/privacy`)
- 회원 탈퇴 `DELETE /v1/me`: soft delete, 닉네임·프로필 익명화, `social_account`·`refresh_token` 삭제, 서버 이벤트 `MEMBER_DELETED` 발행 → event-collector가 해당 회원 이벤트의 `member_id`를 끊는다.
- 구글 OAuth 앱 게시, 카카오 앱 설정, WAF 값 재점검

## 13. 테스트

- 백엔드: TDD. Testcontainers(Postgres, Redis), WireMock(국립중앙도서관, OAuth). 국립중앙도서관 응답은 공개 문서 필드로 만든 가짜 응답으로 시작하고, 키를 받으면 실제 응답을 녹화해 교체한다.
- ArchUnit: core 모듈 경계(공개 서비스 인터페이스만 호출) 규칙을 모듈 추가 때마다 검증한다.
- 웹: Vitest(컴포넌트·트래커), Playwright E2E(비로그인 피드 → 가짜 OAuth 로그인 → 온보딩 → 위시 담기 → 서재 확인).
- 컨테이너 스모크 테스트(§10).

## 14. 구현 전에 확인할 사항

- **국립중앙도서관 키(승인 대기)**: 최대 `page_size`, 실제 호출 제한, 최근 도서의 표지·소개 채움률, 하루 500건 초과 시 처리. 키를 받으면 catalog-worker Task의 첫 단계로 10회 안팎 시험 호출한다. 채움률이 매우 낮으면 자동 발행 기준을 다시 논의한다.
- **OAuth**: 구글 브랜드 확인 절차와 소요 기간, 카카오 로그인 동의 항목과 테스트 사용자 등록 방법
- **사용자가 만들 계정**: Cloudflare R2 버킷·키, Healthchecks.io, UptimeRobot
- **도구 버전**: Flyway, Testcontainers, WireMock, JWT 라이브러리, OpenAPI 생성 도구의 최신 안정 버전을 계획 단계에서 고정한다.

# 도메인 모델

근거: 스펙 §4.2·§4.3, ADR 0008

## catalog (catalog-worker 소유)
- `book`: `isbn13`(UNIQUE), 제목, 저자, 출판사, 발행예정일(`publish_predate`), KDC(API의 `KDC`가 비면 `SUBJECT` 대분류 한 자리), 쪽수, 표지 URL, 소개, 목차, 부가기호(`ea_add_code`), 형태(`form`), 전자책 여부(`ebook_yn`), 세트 ISBN(`set_isbn`), `fetched_at`. ISBN이 없거나 검증숫자가 틀린 도서는 수집하지 않는다. 표지는 원본 URL(`cover_source_url`, https)과 확인을 통과한 `cover_url`로 나눠 저장한다. 확인에 실패하면 `cover_url`만 `NULL`이고, 변경 감지는 원본 URL로 하므로 깨진 표지를 다시 받아도 재확인·초안 재생성을 하지 않는다.
- `collect_run`: 수집 실행 이력. 종류(`NEW_BOOKS`/`SEED_ISBN`/`SEED_KEYWORD`), `params`(jsonb), 상태(`RUNNING`/`SUCCEEDED`/`FAILED`), 조회·저장·초안 건수, 오류, 시작·종료 시각
- `card_draft`: `book_id`(같은 스키마 FK), 슬라이드 묶음(`slides` jsonb, `type` 필드 포함), 생성기 이름·버전(`generator_name`/`generator_version`), `auto_publishable`, 걸린 사유(`blocked_reasons` text[], enum 이름: `SHORT_INTRODUCTION`/`NOT_PRINT_SINGLE`/`STUDY_REFERENCE`/`NO_COVER`), 생성 시각. `auto_publishable`은 사유가 없을 때만 true. core가 `id` 순으로 읽는다.

## core (core 모듈 소유)
- `member`: 닉네임, 프로필 이미지, 역할(`USER`/`ADMIN`), 상태, `deleted_at`
- `social_account`: `member_id`, 제공자(`KAKAO`/`GOOGLE`/`APPLE`), 제공자 사용자 ID. (제공자, 사용자 ID) UNIQUE
- `refresh_token`: `member_id`, 토큰 해시, 기기 정보, `expires_at`, `revoked_at`
- `member_interest`: `member_id`, KDC 코드
- `card`: `book_id`(UNIQUE, catalog.book ID 값만 저장·FK 없음), 출처(`AUTO`/`CURATED`), 상태(`DRAFT`/`PUBLISHED`/`HIDDEN`), `published_at`, `edited_at`(관리자가 편집·재적용한 시각), `created_at`
- `card_slide`: `card_id`, 순서(`position`), 타입(`COVER`/`HOOK`/`QUOTE`/`INFO`/`FOR_WHOM`/`KEYWORDS`), `payload`(jsonb, `type` 필드 포함). (`card_id`, `position`) UNIQUE
- `sync_cursor`: `name`(PK), `last_id`. 다른 스키마를 읽어 동기화하는 작업의 마지막 처리 ID(카드 동기화는 `name='card_draft'`)
- `shelf_item`: `member_id`, `book_id`((회원, 도서) UNIQUE), 상태(`WISH`/`READING`/`FINISHED`/`DROPPED`), 시작일, 완독일, 별점(1~5), 한줄평
- `reading_log`: `shelf_item_id`, 날짜(`date`), 읽은 페이지, 메모
- `quote`: `shelf_item_id`, 페이지, 본문

## events (event-collector 소유)
- `user_event`(`occurred_at` 기준 월별 파티션): `member_id`(nullable), `anonymous_id`, `session_id`, `type`, `card_id`, `book_id`, `slide_index`, `dwell_ms`, `platform`, `app_version`, `occurred_at`, `received_at`
- 타입과 페이로드는 `services/libs/event-schema`에서 정의한다. 상세: `docs/domain/feed.md`

## 공통 규칙
- PK는 TSID(64비트, 시간순). API에서는 문자열로 노출한다.
- 시각은 `timestamptz`(UTC)로 저장한다. 독서 기록의 날짜만 `date`.
- 회원 탈퇴: soft delete 후 개인정보 익명화, 이벤트의 `member_id` 연결을 끊는다.
- 위시는 별도 테이블 없이 `shelf_item`의 `WISH` 상태다.
- 슬라이드는 타입별 렌더러로 그린다. 새 슬라이드 타입은 서버 enum, `clients/packages/shared` 스키마, 웹·앱 렌더러를 함께 추가한다.

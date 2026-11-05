# 피드와 이벤트

근거: 스펙 §5.4·§5.5, ADR 0006

## 피드 (`GET /v1/feed?cursor=`)
1. 후보: 발행된 카드 중 최근 노출했거나 이미 서재에 있는 책은 제외한다.
2. 점수: 관심 KDC 일치도 + 서재 도서 KDC 유사도 + 인기도(최근 7일 `SHELF_ADD` 수) + 신선도 + 무작위 항
   - 비로그인: 인기도 + 신선도 + 무작위 항
3. 피드 세션별 정렬 ID 목록을 Redis에 30분 캐시하고, 20개씩 커서 페이지네이션으로 응답한다.
4. 점수 계산은 `FeedRanker` 인터페이스 뒤에 둔다. 본격 추천 단계에서 구현을 교체한다.

## 이벤트 타입
| 타입 | 보내는 쪽 | 의미 |
|---|---|---|
| `CARD_IMPRESSION` | 클라이언트 | 카드가 화면에 표시됨 |
| `SLIDE_VIEW` | 클라이언트 | 카드의 특정 슬라이드를 봄(`slide_index`) |
| `CARD_DWELL` | 클라이언트 | 카드 체류 시간(`dwell_ms`) |
| `CARD_SKIP` | 클라이언트 | 카드를 넘김 |
| `CARD_SHARE` | 클라이언트 | 카드 공유 |
| `SHELF_ADD` | core-api | 서재에 추가 |
| `STATUS_CHANGE` | core-api | 서재 상태 전이 |
| `IDENTITY_LINK` | core-api | 로그인 시 `anonymous_id`와 회원 연결 |

## 수집 흐름 (`POST /v1/events`)
1. 클라이언트는 최대 50건을 모아 보낸다. 페이지를 벗어날 때는 `sendBeacon`.
2. collector가 스키마 검증 후 Redis Stream에 `XADD`하고 즉시 `202`로 응답한다.
3. 컨슈머 그룹이 최대 500건 또는 1초 단위로 `events.user_event`에 배치 INSERT 후 ACK한다.
4. 실패한 이벤트는 Pending 목록에 남아 재처리된다.
5. core-api의 서버 이벤트도 같은 Stream에 발행한다.

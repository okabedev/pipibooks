# 피드와 이벤트

근거: 스펙 §5.4·§5.5, ADR 0006

## 피드 (`GET /v1/feed?cursor=`)
1. 후보: 최근 발행 카드 최대 2,000개(`feed.candidate-limit`)에서 본 카드를 뺀다. 서재 도서 제외는 로그인 피드에서 적용한다.
2. 점수 = Σ 가중치 × 항. 각 항은 0~1이다.

| 항 | 계산 | 기본 가중치 |
|---|---|---|
| 관심 | 책의 KDC 대분류가 `member_interest`에 있으면 1 | 3.0 |
| 서재 | 내 서재에서 같은 KDC 대분류가 차지하는 비율 | 2.0 |
| 인기도 | `ln(1+n) / ln(1+max)` (n = 최근 7일 `SHELF_ADD` 수, 키는 `bookId`) | 1.0 |
| 신선도 | `0.5^(경과일/14)` (`published_at` 기준, 반감기 `feed.freshness-half-life-days`) | 1.5 |
| 무작위 | `Random(세션ID xor cardId).nextDouble()` | 0.5 |

   - 가중치는 `feed.weights.{interest,shelf,popularity,freshness,random}` 설정이다.
   - 비로그인: 관심·서재 맵이 비어 항이 0이다. 인기도도 `ShelfQuery`가 연결되기 전에는 빈 맵이다.
3. 점수 계산은 `FeedRanker` 인터페이스 뒤에 둔다(MVP는 `RuleBasedFeedRanker`). 본격 추천 단계에서 구현을 교체한다.

### 본 카드
- 기준은 최근 7일 안의 `CARD_IMPRESSION`뿐이다. 응답만 하고 화면에 나오지 않은 카드는 빼지 않는다.
- Redis ZSET `RedisKeys.seenCards(anonymousId)`: 멤버 = 카드 ID(10진수), 점수 = 노출 시각(epoch ms).
- 쓰기(event-collector): `ZADD`(점수 = `occurredAt` ms), `ZREMRANGEBYSCORE -inf now-7d`, `EXPIRE 7d`. 단순 집합 + 키 TTL은 매일 오는 사용자의 키가 만료되지 않아 쓰지 않는다.
- 읽기(core): `ZRANGEBYSCORE now-7d +inf`. `X-Anonymous-Id`가 UUID가 아니면 없는 것으로 보고 아무것도 빼지 않는다.

### 세션과 커서
- 커서 없는 요청은 후보를 정렬해 상위 500개(`feed.session-size`)를 Redis(`RedisKeys.feedSession`)에 30분(`feed.session-ttl`) 보관한다. 시드는 세션 ID(`Tsid`).
- 페이지는 세션 목록의 `[offset, offset+20)` 구간 중 아직 `PUBLISHED`인 카드다. 숨겨진 카드가 빠지면 20개보다 적을 수 있다.
- 커서는 `{s: 세션ID, o: offset}`의 불투명 문자열이다. `nextCursor`는 `offset+20 < 목록 크기`일 때만 준다.
- `o`가 숫자가 아니거나 음수, 커서가 깨졌으면 400 `INVALID_CURSOR`. 세션이 없거나 만료되었으면 오류 없이 새 세션(offset 0)을 만들어 그 커서를 돌려 준다.
- 발행 카드가 없으면 빈 목록과 `nextCursor: null`.

## 이벤트 타입
| 타입 | 보내는 쪽 | 발생 조건 |
|---|---|---|
| `CARD_IMPRESSION` | 클라이언트 | 카드가 화면에 멈춰 활성화됨 |
| `SLIDE_VIEW` | 클라이언트 | 슬라이드가 바뀜(`slideIndex`) |
| `CARD_DWELL` | 클라이언트 | 카드를 떠날 때 체류 시간(`dwellMs`) |
| `CARD_SKIP` | 클라이언트 | 2초 안에 첫 슬라이드에서 다음 카드로 넘김 |
| `CARD_SHARE` | 클라이언트 | 공유 버튼을 누름 |
| `SHELF_ADD` | core | 서재에 추가 |
| `STATUS_CHANGE` | core | 서재 상태 전이 |
| `IDENTITY_LINK` | core | 로그인 시 `anonymous_id`와 회원 연결 |
| `MEMBER_DELETED` | core | 회원 탈퇴(일반 공개 단계) |

## 수집 흐름 (`POST /v1/events`)
1. 클라이언트는 최대 50건을 모아 보낸다. 페이지를 벗어날 때는 `sendBeacon`.
2. collector가 스키마 검증 후 Redis Stream에 `XADD`하고 즉시 `202`로 응답한다.
3. 컨슈머 그룹이 최대 500건 또는 1초 단위로 `events.user_event`에 배치 INSERT 후 ACK한다.
4. 실패한 이벤트는 Pending 목록에 남아 재처리된다.
5. core의 서버 이벤트도 같은 Stream에 발행한다.

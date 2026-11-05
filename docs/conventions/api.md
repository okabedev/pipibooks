# REST API 규칙

근거: 스펙 §6

- 경로에 버전을 넣는다: `/v1/...`. 리소스는 복수형 명사(`/v1/books`, `/v1/shelf`는 예외적으로 단수 컬렉션).
- 요청·응답은 JSON, 필드는 camelCase. ID는 문자열(TSID).
- 시각은 ISO 8601 UTC(`2026-11-05T12:00:00Z`), 날짜는 `YYYY-MM-DD`.
- 인증: `Authorization: Bearer <access token>`. 웹의 Refresh 토큰은 `HttpOnly` 쿠키.
- 목록은 커서 페이지네이션: 요청 `cursor`, `size`(기본 20, 최대 50) / 응답 `{ "items": [...], "nextCursor": "..." | null }`
- 에러는 RFC 9457 Problem Details(`application/problem+json`)에 `code`를 더한다.
  ```json
  { "type": "about:blank", "title": "Not Found", "status": 404,
    "detail": "서재 항목을 찾을 수 없습니다.", "code": "SHELF_ITEM_NOT_FOUND" }
  ```
- `code`는 `SCREAMING_SNAKE_CASE`이고 클라이언트 분기에 쓴다. `detail`은 사람이 읽는 메시지다.
- 상태 코드: 생성 `201`, 비동기 수락 `202`, 본문 없음 `204`, 검증 실패 `400`, 인증 없음 `401`, 권한 없음 `403`, 없음 `404`, 충돌 `409`, 의존 서비스 실패 `503`
- core는 springdoc으로 OpenAPI를 생성하고, `clients/packages/api-client`가 이를 기반으로 TS 클라이언트를 생성한다. 생성물은 CI에서 최신 여부를 검사한다.
- 내부 API(`/internal/**`)는 `internal` 네트워크 전용이며 공개 라우팅에 넣지 않는다.

# 아키텍처 개요

근거: 스펙 §3·§4.1, ADR 0001·0002·0007

## 구성
```
Cloudflare (DNS, WAF, Access) ── Tunnel ── cloudflared (유일한 진입점)
  pipibooks.com                  → web (Next.js)            :3000
  api.pipibooks.com ^/v1/events  → event-collector          :8081
  api.pipibooks.com              → core-api                 :8080
  그 외                           → 404

core-api ──(HTTP, internal 네트워크)── catalog-worker :8082 ── 국립중앙도서관 API
core-api ──(XADD)── Redis Streams ──(XREADGROUP, 배치)── event-collector
PostgreSQL: core / catalog / events 스키마
```

## 컴포넌트 책임
| 컴포넌트 | 책임 | 실행 모델 |
|---|---|---|
| core-api | 회원·인증, 카드 조회·발행, 피드, 서재, 통계, 관리자 API | Spring MVC + JPA + 가상 스레드 |
| catalog-worker | 국립중앙도서관 API 수집·검색·ISBN import(내부 API), 카드 초안 생성 | WebFlux + 코루틴 |
| event-collector | 행동 이벤트 수신, Redis Streams 버퍼링, `events` 배치 적재 | WebFlux + 코루틴 |
| web | 웹 클라이언트, `/admin` 관리자 화면 | Next.js |

## core-api 내부 모듈
- `member`, `card`, `feed`, `library`, `stats`, `admin`
- 모듈끼리는 서로의 공개 서비스 인터페이스만 호출한다. 다른 모듈의 내부 패키지·리포지토리를 참조하지 않는다.
- 모듈 간 순환 의존 금지. ArchUnit 테스트(`ArchitectureTest`)가 검사한다.

## 스키마 소유권
| 스키마 | 쓰기 | 읽기 | DB 계정 |
|---|---|---|---|
| `catalog` | catalog-worker | core-api | `catalog_app`(쓰기), `core_app`(읽기) |
| `core` | core-api | - | `core_app` |
| `events` | event-collector | (나중에) 추천 워커 | `events_app` |

규칙:
- 각 스키마의 Flyway 마이그레이션은 소유 컴포넌트만 실행한다.
- 국립중앙도서관 API 호출과 API 키는 catalog-worker만 가진다.
- catalog-worker의 내부 API는 `internal` 네트워크에만 열고 터널로 노출하지 않는다.

## 네트워크
- `edge`: cloudflared, web, core-api, event-collector
- `internal`: core-api, event-collector, catalog-worker, postgres, redis
- 호스트 포트는 열지 않는다. 운영 Postgres만 `127.0.0.1:5432`에 바인딩한다.

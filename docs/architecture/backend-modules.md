# 백엔드 모듈

`services/`는 Gradle 멀티 모듈 루트다. 모듈은 역할별로 나눈다. 이렇게 정한 이유는 [0003. 역할별 모듈 구성](../decisions/0003-role-based-modules.md)에 있다.

```
services/
  app/
    api/      ← 실행 앱: REST API, 인증
    worker/   ← 실행 앱: 신간 수집, 데이터 보충, 순위 집계
  core/       ← 엔티티, 리포지토리, 비즈니스 로직
  client/     ← 외부 API 클라이언트 (nlk, kakao 패키지)
  support/    ← 공통 예외, 유틸리티
```

## 의존 방향

```
app/api, app/worker → core → client → support
                      core → support
```

- `client`는 외부 응답을 자기만의 DTO로 돌려준다. `core`가 이를 도메인 객체로 바꾼다.
- 이 규모에서는 의존성 역전(`core`에 인터페이스를 두는 방식)을 쓰지 않는다.

## 패키지

- `core` 안은 기능별로 패키지를 나눈다: `book`, `review`, `wish`, `user`, `ranking`, `statistics`.
- 한 패키지에 클래스가 많아지면 하위 패키지로 나눈다.
- 패키지 내부의 클래스 구성은 구현자에게 맡긴다([결정 방식](../conventions/decision-making.md)).

## 데이터베이스

데이터베이스 스키마는 하나이고, API 서버(`services/app/api`)와 워커(`services/app/worker`)가 함께 쓴다.

## 관련 문서

- 외부 API를 API 서버와 워커 중 어디서 호출하는지: [도서 데이터](book-data.md), [0002. 외부 API 호출 위치](../decisions/0002-external-api-call-location.md)
- 워커 작업: [워커](worker.md)
- 인증: [인증](authentication.md)

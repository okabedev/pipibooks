# 0012. WebFlux 컴포넌트의 DB 접근

- 상태: 승인 · 2026-11-06

## 맥락
event-collector와 catalog-worker는 WebFlux + 코루틴이다(ADR 0001·0002). 이 컴포넌트가 Postgres에 접근하는 방식이 필요하다. 이벤트 수집은 짧은 쓰기가 많아 이벤트 루프를 막으면 안 되고, 카탈로그 워커는 배치 성격이지만 같은 실행 모델을 쓴다. core는 Spring MVC + 가상 스레드라 JPA(JDBC)를 쓴다.

## 결정
- WebFlux 컴포넌트는 Spring Data R2DBC의 `DatabaseClient`로 SQL을 직접 작성해 접근한다. 엔티티 매핑·리포지토리 추상화는 쓰지 않는다.
- 스키마 마이그레이션만 Flyway(JDBC)로 한다. 기동 시 한 번 서비스 계정으로 자기 스키마에 적용한다. 앱 런타임 쿼리는 JDBC를 쓰지 않는다.
- 접속 정보는 `DB_HOST`·`DB_PORT`·`DB_NAME`·`DB_USER`·`DB_PASSWORD`로 받는다. R2DBC(`r2dbc:postgresql://`)와 JDBC(`jdbc:postgresql://`)의 URL 스킴이 달라 단일 URL 변수를 쓰지 않고 `application.yml`에서 조합한다.
- core는 JPA + Flyway를 쓰고, `ddl-auto`는 `none`이다. 스키마는 Flyway로만 바꾼다.

## 결과
- 장점: 이벤트 루프를 막지 않는다. SQL이 그대로 보여 쿼리 튜닝이 쉽다. 새 학습 비용이 거의 없다.
- 단점: 매핑 코드를 직접 쓴다. JDBC(Flyway)와 R2DBC 두 드라이버가 클래스패스에 공존한다.

## 검토한 대안
- JDBC를 `Dispatchers.IO`로 감싸기: 코루틴·논블로킹 모델과 어긋나고 스레드 풀 크기가 동시성의 상한이 된다.
- Exposed: 코루틴 지원이 있으나 새 라이브러리 학습 비용이 크고, 이 규모에서 `DatabaseClient`로 충분하다.

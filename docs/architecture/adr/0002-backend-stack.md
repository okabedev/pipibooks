# 0002. 백엔드 스택

- 상태: 승인 · 2026-11-05

## 맥락
주력은 Java/Spring Boot이고, Kotlin + WebFlux + 코루틴 경험이 있다. 코어 도메인은 관계형 모델링과 집계가 중심이다.

## 결정
- 언어는 전 컴포넌트 Kotlin으로 하고, Gradle Kotlin DSL 멀티 프로젝트와 버전 카탈로그를 쓴다. JDK는 25 LTS다.
- `core-api`: Spring MVC + JPA + 가상 스레드
- `catalog-worker`, `event-collector`: Kotlin 코루틴(collector는 WebFlux)
- DB 변경은 Flyway만 허용한다.

## 결과
- 장점: 코어는 JPA로 생산적이고, I/O가 많은 컴포넌트는 코루틴으로 효율적이다. 빌드 설정을 공유한다.
- 단점: 두 실행 모델이 공존하므로 컴포넌트별 규칙을 `services/CLAUDE.md`에 명시해야 한다.

## 검토한 대안
- 전부 WebFlux + R2DBC: 집계 쿼리와 연관관계 처리 부담이 크다.
- 전부 Java MVC: 코루틴 컴포넌트의 이점을 잃는다.

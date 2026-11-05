# services/ — 백엔드 (Kotlin + Spring Boot)

근거: 스펙 §3·§4, ADR 0001·0002. 아키텍처 개요: `docs/architecture/overview.md`

## 모듈
| 모듈 | 패키지 | 포트 | 실행 모델 |
|---|---|---|---|
| `core` | `com.pipibooks.core` | 8080 | Spring MVC + 가상 스레드, JPA + Flyway(`core` 스키마), Redis |
| `event-collector` | `com.pipibooks.event` | 8081 | WebFlux + 코루틴, R2DBC + Flyway(`events`), Redis |
| `catalog-worker` | `com.pipibooks.catalog` | 8082 | WebFlux + 코루틴 (내부 API만), R2DBC + Flyway(`catalog`) |
| `libs/common` | `com.pipibooks.common` | - | 공유 라이브러리: `ApiException`, `Tsid`, `CursorCodec`, `SlidePayload` |

## 빌드 구조
- `build-logic/`의 컨벤션 플러그인만 쓴다. 모듈 `build.gradle.kts`에는 플러그인 ID와 의존성만 둔다.
  - `pipibooks.kotlin-jvm`: JDK 25 툴체인, `-Xjsr305=strict`, ktlint, detekt(2.0 alpha), JUnit
  - `pipibooks.spring-boot-app`: 위 + Spring Boot, BOM(platform), Jib
- 버전은 `gradle/libs.versions.toml`에만 쓴다. Spring 관리 의존성은 버전 없이 선언한다.
- `io.spring.dependency-management` 플러그인은 쓰지 않는다(ktlint·detekt의 Kotlin 버전을 덮어씀).

## 코드 규칙
- core 내부 모듈(`member`, `card`, `feed`, `library`, `stats`, `admin`)은 `com.pipibooks.core.<module>` 패키지. 다른 모듈은 공개 서비스 인터페이스로만 호출한다. `ArchitectureTest`가 순환 의존을 막는다.
- 블로킹 코드는 core에서만. WebFlux 모듈에서는 `suspend` 함수와 코루틴을 쓴다.
- DB 접근(ADR 0012): core는 JPA, WebFlux 모듈은 R2DBC `DatabaseClient`(SQL 직접 작성). Flyway(JDBC)는 기동 시 자기 스키마(`db/migration/<스키마>`)에만 적용한다. 접속 정보는 `DB_HOST`·`DB_PORT`·`DB_NAME`·`DB_USER`·`DB_PASSWORD`(core·event-collector는 `REDIS_HOST`·`REDIS_PORT`도).
- 설정은 `application.yml`. 시크릿은 환경 변수로 받는다(값을 커밋하지 않는다).
- 로그는 구조화 로그(`logging.structured.format.console: ecs`). Actuator는 `health`만 노출한다.
- 표준 예시 코드(새 기능이 따를 구조)는 서브프로젝트 1의 위시리스트 구현에서 지정한다.

## 테스트 규칙
- TDD: 실패하는 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.
- 테스트 이름은 한국어 백틱 함수명(`fun \`헬스 엔드포인트는 UP을 반환한다\`()`).
- MVC는 MockMvc(`spring-boot-starter-webmvc-test`), WebFlux는 WebTestClient(`spring-boot-starter-webflux-test`).
- DB·Redis 통합 테스트는 Testcontainers. `@SpringBootTest` 클래스는 모듈의 `support/DbTestSupport`를 상속한다(운영 `01-init.sh`로 만든 서비스 계정으로 접속).

## 명령 (`services/`에서 실행)
| 명령 | 용도 |
|---|---|
| `./gradlew build` | 컴파일 + 테스트 + ktlint + detekt |
| `./gradlew test` | 테스트만 |
| `./gradlew ktlintFormat` | Kotlin 포맷 자동 수정 |
| `./gradlew :core:bootRun` | 로컬 실행 |
| `./gradlew jibDockerBuild -Pversion=local` | 로컬 Docker 이미지 빌드 |

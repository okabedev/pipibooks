# 기술 스택

## 백엔드

- Java 25, Spring Boot 4.x(작업 시점의 최신 안정 버전)
- Gradle Kotlin DSL과 버전 카탈로그
- PostgreSQL, Spring Data JPA, Flyway
- springdoc OpenAPI
- 복잡한 집계는 JPQL이나 네이티브 쿼리로 작성하고, QueryDSL은 쓰지 않는다.
- 테스트는 JUnit 5와 Testcontainers로 한다.

## 웹

- TypeScript, Vite, React Router, TanStack Query, Tailwind CSS
- 모바일 화면에서도 쓸 수 있게 반응형으로 만든다.

## 모바일

- Expo(React Native)와 TypeScript
- 카카오 네이티브 SDK를 쓰려면 Expo Go가 아니라 개발 빌드가 필요하다.
- 배포는 APK나 개발 빌드로 한다. 스토어에는 출시하지 않는다.

## API 타입

- 각 클라이언트가 OpenAPI 문서에서 타입을 생성한다(openapi-typescript).
- 공유 패키지 폴더는 두지 않는다.

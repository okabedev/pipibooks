# 인프라

로컬 개발 환경과 운영 환경(1차 목표)의 구성이다. 파일은 `infra/`에 둔다.

## 로컬 개발

Compose로 PostgreSQL만 띄우고, 애플리케이션은 IDE나 Gradle로 실행한다.

## 운영 (1차 목표)

Compose 하나로 `postgres`, `api`, `worker`, `web`(nginx), `cloudflared`를 띄운다.

- nginx는 `/`에서 웹 정적 파일을 제공하고, `/api`는 API 서버로 넘긴다. 도메인이 하나라서 CORS 설정이 필요 없다.
- Cloudflare Tunnel은 토큰 방식(원격 관리 터널)을 쓰고, `pipibooks.com`을 `web:80`으로 연결한다.
- 서비스를 24시간 운영할 필요는 없다. 꺼졌다 켜져도 문제없이 동작해야 한다([서비스 개요](../product/overview.md)).

## 비밀 값

- `.env` 파일로 관리하고 커밋하지 않는다. 대신 `.env.example`을 커밋한다.
- 키 값은 문서나 커밋에 남기지 않는다.

## 백업

`pg_dump` 스크립트를 `infra/`에 둔다.

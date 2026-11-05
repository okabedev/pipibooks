# infra/ — 컨테이너·배포

근거: 스펙 §7, ADR 0007. 네트워크·스키마 소유권: `docs/architecture/overview.md`

## 구성
| 파일 | 용도 |
|---|---|
| `compose/compose.prod.yml` | 운영: web, core, event-collector, catalog-worker, postgres, redis, cloudflared(`tunnel` 프로필) |
| `compose/compose.dev.yml` | 개발: postgres(`127.0.0.1:5433`), redis(`127.0.0.1:6380`)만 |
| `compose/.env` | 운영 변수 `PIPIBOOKS_VERSION`(배포된 버전의 단일 기준, `deploy.sh`가 갱신), `TUNNEL_ID` (gitignore) |
| `env/postgres.env` | DB 비밀번호 (gitignore, 예시: `postgres.env.example`) |
| `postgres/init/01-init.sh` | 최초 기동 시 계정(`core_app`·`catalog_app`·`events_app`)과 스키마·권한 생성 |
| `cloudflared/config.yml`, `cloudflared/Dockerfile` | 터널 라우팅. `pipibooks-cloudflared` 이미지에 포함 |
| `env/cloudflared.env` | 터널 인증 정보 `TUNNEL_CRED_CONTENTS` (gitignore, 예시: `cloudflared.env.example`) |
| `scripts/build-local.sh` | 네 이미지를 `:local`로 빌드 |
| `scripts/smoke-test.sh` | 운영 compose를 `:local`로 띄워 헬스·DB 권한 검증 후 정리(Postgres는 임의 포트) |
| `scripts/deploy.sh` | 운영 배포·자동 롤백. 운영 체크아웃이 배포 태그를 가리켜야 함 |

## 규칙
- 이미지는 버전 고정 태그만 쓴다(`latest` 금지). 앱 이미지는 `ghcr.io/okabedev/pipibooks-<서비스>:<버전>`.
- 모든 서비스에 `restart: unless-stopped`, json-file 로그 로테이션, `mem_limit`.
- 네트워크: `edge`(cloudflared, web, core, event-collector) / `internal`(core, event-collector, catalog-worker, postgres, redis).
- 호스트 포트를 새로 열지 않는다. 운영 Postgres만 `127.0.0.1:5432`(`POSTGRES_PUBLISH`로 변경 가능, 스모크 테스트가 사용).
- 운영 스택의 기동·버전 변경은 `deploy.sh`로만 한다. 운영은 개발 작업 트리가 아닌 별도 체크아웃(`~/pipibooks-prod`)에서 돌린다.
- 새 공개 경로는 `cloudflared/config.yml`의 ingress에 추가하고 `ingress validate`로 확인한다. 마지막 규칙은 항상 `http_status:404`.
- 시크릿은 `env/*.env`에만 두고 커밋하지 않는다. 예시 파일(`*.example`)에는 값을 비워 둔다.
- `postgres/init`은 빈 볼륨에서만 실행된다. 이후 스키마 변경은 각 서비스의 Flyway로 한다.
- init이 실패하면(예: `postgres.env`의 비밀번호 누락) 계정 없는 DB가 남는다. 데이터가 없는 최초 기동이라면 `docker compose -p <프로젝트> -f infra/compose/compose.prod.yml down -v`로 볼륨을 지우고 다시 기동한다.
- 운영 서비스는 호스트 파일을 바인드 마운트하지 않는다(postgres init 디렉토리만 예외). Docker Desktop은 재부팅 직후 WSL 파일시스템보다 먼저 컨테이너를 띄워 바인드 마운트가 실패하고 재시도하지 않는다. 설정은 이미지에, 시크릿은 env 파일에 둔다. `scripts/tests/compose.test.sh`가 검사한다.
- `deploy.sh`는 `TUNNEL_CRED_CONTENTS`와 `TUNNEL_ID`가 없으면 시작하지 않는다.

## 명령 (루트에서 실행)
| 명령 | 용도 |
|---|---|
| `bash infra/scripts/build-local.sh && bash infra/scripts/smoke-test.sh` | 로컬 이미지 빌드 후 통합 스모크 테스트 |
| `docker compose -f infra/compose/compose.dev.yml up -d` | 개발용 DB·Redis |
| `docker run --rm -v $PWD/infra/cloudflared:/c cloudflare/cloudflared:2026.10.7 tunnel --config /c/config.yml ingress validate` | 터널 라우팅 검증 |

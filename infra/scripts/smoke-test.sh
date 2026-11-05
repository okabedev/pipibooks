#!/usr/bin/env bash
# 운영 compose를 로컬 이미지(:local)로 띄워 헬스와 DB 스키마 권한을 검증하고 정리한다.
# 선행: infra/scripts/build-local.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PROJECT=pipibooks-smoke
CURL_IMAGE=curlimages/curl:8.23.0
tmp="$(mktemp -d)"
# POSTGRES_PUBLISH: 같은 호스트의 운영 Postgres(127.0.0.1:5432)와 충돌하지 않도록 임의 포트에 바인딩한다.
export PIPIBOOKS_VERSION=local TUNNEL_ID=smoke POSTGRES_ENV_FILE="$tmp/postgres.env" POSTGRES_PUBLISH=127.0.0.1: CLOUDFLARED_ENV_FILE="$tmp/cloudflared.env"
export CORE_ENV_FILE="$tmp/core.env" CATALOG_WORKER_ENV_FILE="$tmp/catalog-worker.env" EVENT_COLLECTOR_ENV_FILE="$tmp/event-collector.env"
compose() { docker compose -p "$PROJECT" -f infra/compose/compose.prod.yml "$@"; }

cleanup() { compose down -v --remove-orphans >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

pw() { openssl rand -hex 16; }
core_pw="$(pw)"; catalog_pw="$(pw)"; events_pw="$(pw)"
cat > "$POSTGRES_ENV_FILE" <<ENV
POSTGRES_DB=pipibooks
POSTGRES_PASSWORD=$(pw)
CORE_APP_PASSWORD=$core_pw
CATALOG_APP_PASSWORD=$catalog_pw
EVENTS_APP_PASSWORD=$events_pw
ENV

# 서비스별 접속 정보. Flyway가 기동 시 자기 스키마에 접속하지 못하면 헬스가 UP이 되지 않는다.
svc_env() { # <파일> <계정> <비밀번호> [redis]
  printf 'DB_HOST=postgres\nDB_PORT=5432\nDB_NAME=pipibooks\nDB_USER=%s\nDB_PASSWORD=%s\n' "$2" "$3" > "$1"
  if [ "${4:-}" = redis ]; then printf 'REDIS_HOST=redis\nREDIS_PORT=6379\n' >> "$1"; fi
}
svc_env "$CORE_ENV_FILE" core_app "$core_pw" redis
svc_env "$CATALOG_WORKER_ENV_FILE" catalog_app "$catalog_pw"
svc_env "$EVENT_COLLECTOR_ENV_FILE" events_app "$events_pw" redis

printf 'TUNNEL_CRED_CONTENTS={}\n' > "$CLOUDFLARED_ENV_FILE"

compose up -d --quiet-pull
failures=0
ok() { echo "  ok   - $1"; }
fail() { echo "  FAIL - $1"; failures=$((failures + 1)); }

health() { # <name> <network> <url>
  if docker run --rm --network "${PROJECT}_$2" "$CURL_IMAGE" -fsS --connect-timeout 3 --max-time 5 --retry 40 --retry-delay 2 --retry-all-errors "$3" 2>/dev/null | grep -q '"UP"'; then
    ok "health $1"
  else fail "health $1 ($3)"; fi
}
health core internal http://core:8080/actuator/health
health catalog-worker internal http://catalog-worker:8082/actuator/health
health event-collector internal http://event-collector:8081/actuator/health
health web edge http://web:3000/healthz

for _ in $(seq 1 30); do compose exec -T postgres pg_isready -U postgres -d pipibooks >/dev/null 2>&1 && break; sleep 1; done
priv() { # <expected t|f> <sql>
  local got
  got="$(compose exec -T postgres psql -U postgres -d pipibooks -tAc "SELECT $2" 2>/dev/null | tr -d '[:space:]')"
  if [ "$got" = "$1" ]; then ok "$2 = $1"; else fail "$2 = $1 (got '$got')"; fi
}
priv t "has_schema_privilege('core_app','catalog','USAGE')"
priv f "has_schema_privilege('core_app','catalog','CREATE')"
priv t "has_schema_privilege('catalog_app','catalog','CREATE')"
priv f "has_schema_privilege('events_app','core','USAGE')"
priv f "has_schema_privilege('core_app','public','CREATE')"

# catalog_app이 만든 테이블은 core_app이 읽기만 할 수 있어야 한다(ALTER DEFAULT PRIVILEGES).
compose exec -T postgres psql -U postgres -d pipibooks -qc "SET ROLE catalog_app; CREATE TABLE catalog.smoke_probe(id int);" >/dev/null
priv t "has_table_privilege('core_app','catalog.smoke_probe','SELECT')"
priv f "has_table_privilege('core_app','catalog.smoke_probe','INSERT')"
# 각 계정의 기본 search_path는 자기 스키마다.
priv t "(SELECT 'search_path=core' = ANY(rolconfig) FROM pg_roles WHERE rolname='core_app')"
priv t "(SELECT 'search_path=catalog' = ANY(rolconfig) FROM pg_roles WHERE rolname='catalog_app')"
priv t "(SELECT 'search_path=events' = ANY(rolconfig) FROM pg_roles WHERE rolname='events_app')"

# cloudflared 이미지에 들어간 터널 설정이 유효해야 한다(터널은 연결하지 않음).
if docker run --rm ghcr.io/okabedev/pipibooks-cloudflared:local tunnel --config /etc/cloudflared/config.yml ingress validate >/dev/null 2>&1; then
  ok "cloudflared 이미지의 ingress 설정 유효"
else fail "cloudflared 이미지의 ingress 설정 유효"; fi

if [ "$failures" -ne 0 ]; then echo "SMOKE TEST FAILED ($failures)"; compose logs --tail 30 >&2 || true; exit 1; fi
echo "SMOKE TEST PASSED"

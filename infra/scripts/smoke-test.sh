#!/usr/bin/env bash
# 운영 compose를 로컬 이미지(:local)로 띄워 헬스와 DB 스키마 권한을 검증하고 정리한다.
# 선행: infra/scripts/build-local.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PROJECT=pipibooks-smoke
CURL_IMAGE=curlimages/curl:8.23.0
tmp="$(mktemp -d)"
export PIPIBOOKS_VERSION=local TUNNEL_ID=smoke POSTGRES_ENV_FILE="$tmp/postgres.env"
compose() { docker compose -p "$PROJECT" -f infra/compose/compose.prod.yml "$@"; }

cleanup() { compose down -v --remove-orphans >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT

pw() { openssl rand -hex 16; }
cat > "$POSTGRES_ENV_FILE" <<ENV
POSTGRES_DB=pipibooks
POSTGRES_PASSWORD=$(pw)
CORE_APP_PASSWORD=$(pw)
CATALOG_APP_PASSWORD=$(pw)
EVENTS_APP_PASSWORD=$(pw)
ENV

compose up -d --quiet-pull
failures=0
ok() { echo "  ok   - $1"; }
fail() { echo "  FAIL - $1"; failures=$((failures + 1)); }

health() { # <name> <network> <url>
  if docker run --rm --network "${PROJECT}_$2" "$CURL_IMAGE" -fsS --retry 40 --retry-delay 2 --retry-all-errors "$3" 2>/dev/null | grep -q '"UP"'; then
    ok "health $1"
  else fail "health $1 ($3)"; fi
}
health core-api internal http://core-api:8080/actuator/health
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

if [ "$failures" -ne 0 ]; then echo "SMOKE TEST FAILED ($failures)"; compose logs --tail 30 >&2 || true; exit 1; fi
echo "SMOKE TEST PASSED"

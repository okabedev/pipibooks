#!/usr/bin/env bash
# 최초 기동 시 1회 실행: 컴포넌트별 계정과 스키마를 만들고 소유권·권한을 분리한다(docs/architecture/overview.md).
set -euo pipefail

# 비밀번호가 없거나 비어 있으면 아무것도 만들지 않고 실패한다.
# (init은 빈 볼륨에서 한 번만 돈다. 실패 후 복구: docker compose ... down -v 로 볼륨을 지우고 다시 기동)
for v in CORE_APP_PASSWORD CATALOG_APP_PASSWORD EVENTS_APP_PASSWORD; do
  if [ -z "${!v:-}" ]; then
    echo "01-init.sh: $v 가 비어 있다. infra/env/postgres.env 를 확인한다." >&2
    exit 1
  fi
done

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v core_pw="$CORE_APP_PASSWORD" -v catalog_pw="$CATALOG_APP_PASSWORD" -v events_pw="$EVENTS_APP_PASSWORD" <<'SQL'
CREATE ROLE core_app LOGIN PASSWORD :'core_pw';
CREATE ROLE catalog_app LOGIN PASSWORD :'catalog_pw';
CREATE ROLE events_app LOGIN PASSWORD :'events_pw';

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE :"DBNAME" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"DBNAME" TO core_app, catalog_app, events_app;

CREATE SCHEMA core AUTHORIZATION core_app;
CREATE SCHEMA catalog AUTHORIZATION catalog_app;
CREATE SCHEMA events AUTHORIZATION events_app;

-- 각 계정의 기본 search_path는 자기 스키마다(다른 스키마는 이름을 붙여 접근).
ALTER ROLE core_app SET search_path = core;
ALTER ROLE catalog_app SET search_path = catalog;
ALTER ROLE events_app SET search_path = events;

-- core는 catalog를 읽기만 한다.
GRANT USAGE ON SCHEMA catalog TO core_app;
ALTER DEFAULT PRIVILEGES FOR ROLE catalog_app IN SCHEMA catalog GRANT SELECT ON TABLES TO core_app;
SQL

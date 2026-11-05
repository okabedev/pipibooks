#!/usr/bin/env bash
# 최초 기동 시 1회 실행: 컴포넌트별 계정과 스키마를 만들고 소유권·권한을 분리한다(docs/architecture/overview.md).
set -euo pipefail

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

-- core-api는 catalog를 읽기만 한다.
GRANT USAGE ON SCHEMA catalog TO core_app;
ALTER DEFAULT PRIVILEGES FOR ROLE catalog_app IN SCHEMA catalog GRANT SELECT ON TABLES TO core_app;
SQL

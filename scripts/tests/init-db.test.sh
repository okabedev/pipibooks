#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
INIT="$REPO_ROOT/infra/postgres/init/01-init.sh"

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
# 가짜 psql: 호출되면 기록만 한다.
printf '#!/usr/bin/env bash\necho called >> "%s/psql.log"\ncat >/dev/null\n' "$tmp" > "$tmp/psql"
chmod +x "$tmp/psql"

run() { # [VAR=값...]
  : > "$tmp/psql.log"
  env -i PATH="$tmp:/usr/bin:/bin" POSTGRES_USER=postgres POSTGRES_DB=pipibooks "$@" bash "$INIT" >/dev/null 2>&1
}
psql_called() { [ -s "$tmp/psql.log" ]; }
psql_not_called() { ! psql_called; }

assert_fail "비밀번호 변수가 없으면 실패" run CATALOG_APP_PASSWORD=x EVENTS_APP_PASSWORD=x
assert_pass "변수가 없으면 psql을 호출하지 않음" psql_not_called
assert_fail "비밀번호가 비어 있으면 실패" run CORE_APP_PASSWORD= CATALOG_APP_PASSWORD=x EVENTS_APP_PASSWORD=x
assert_pass "빈 값이면 psql을 호출하지 않음" psql_not_called
assert_pass "모두 있으면 성공" run CORE_APP_PASSWORD=a CATALOG_APP_PASSWORD=b EVENTS_APP_PASSWORD=c
assert_pass "모두 있으면 psql 호출" psql_called
finish

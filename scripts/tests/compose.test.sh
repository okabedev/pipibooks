#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
cd "$REPO_ROOT"

# 운영 compose를 렌더링한다(변수·env 파일은 테스트용 값).
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
printf 'POSTGRES_DB=x\n' > "$tmp/postgres.env"
printf 'TUNNEL_CRED_CONTENTS={}\n' > "$tmp/cloudflared.env"
for s in core catalog-worker event-collector; do : > "$tmp/$s.env"; done
render() {
  PIPIBOOKS_VERSION=0.0.0 TUNNEL_ID=t POSTGRES_ENV_FILE="$tmp/postgres.env" CLOUDFLARED_ENV_FILE="$tmp/cloudflared.env" \
    CORE_ENV_FILE="$tmp/core.env" CATALOG_WORKER_ENV_FILE="$tmp/catalog-worker.env" EVENT_COLLECTOR_ENV_FILE="$tmp/event-collector.env" \
    docker compose -f infra/compose/compose.prod.yml --profile tunnel config --format json 2>/dev/null
}
render > "$tmp/config.json"

# Docker Desktop은 재부팅 직후 WSL 파일시스템보다 먼저 컨테이너를 띄우므로, 호스트 바인드 마운트가 있으면
# 컨테이너 생성이 실패하고 재시도되지 않는다. 바인드 마운트는 postgres의 init 디렉토리(최초 1회용)만 허용한다.
binds_of() {
  node -e '
    const c = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
    const v = (c.services[process.argv[2]] || {}).volumes || [];
    console.log(v.filter((m) => m.type === "bind").map((m) => m.target).join(","));
  ' "$tmp/config.json" "$1"
}
assert_pass "운영 compose 렌더링 성공" test -s "$tmp/config.json"
for s in cloudflared web core event-collector catalog-worker redis; do
  assert_pass "$s: 호스트 바인드 마운트 없음" test -z "$(binds_of "$s")"
done
assert_pass "postgres: 바인드는 init 디렉토리뿐" test "$(binds_of postgres)" = "/docker-entrypoint-initdb.d"
image_of() { node -e 'const c=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));console.log(c.services[process.argv[2]].image)' "$tmp/config.json" "$1"; }
assert_pass "cloudflared는 설정을 담은 자체 이미지" test "$(image_of cloudflared)" = "ghcr.io/okabedev/pipibooks-cloudflared:0.0.0"
finish

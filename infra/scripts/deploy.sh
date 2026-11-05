#!/usr/bin/env bash
# 운영 배포: 지정 버전 이미지를 당겨와 기동하고 헬스체크한다. up이나 헬스체크가 실패하면 이전 버전으로 되돌린다.
# 사용법: infra/scripts/deploy.sh vX.Y.Z   (운영 체크아웃이 그 태그를 가리켜야 한다. docs/ops/deploy.md)
# 배포된 버전의 단일 기준은 infra/compose/.env의 PIPIBOOKS_VERSION이다(이 스크립트가 갱신).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="$ROOT/infra/compose/compose.prod.yml"
ENV_FILE="${ENV_FILE:-$ROOT/infra/compose/.env}"
CREDENTIALS_FILE="${CREDENTIALS_FILE:-$ROOT/infra/cloudflared/credentials.json}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
SKIP_REV_CHECK="${SKIP_REV_CHECK:-0}"
CURL_IMAGE=curlimages/curl:8.23.0
PROJECT=pipibooks

usage() { echo "사용법: $0 vX.Y.Z" >&2; exit 2; }
[ $# -eq 1 ] && [[ "$1" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || usage
tag="$1"

# compose 파일·터널 설정·init 스크립트가 이미지와 같은 리비전이어야 한다.
if [ "$SKIP_REV_CHECK" != 1 ]; then
  head_tag="$(git -C "$ROOT" describe --tags --exact-match HEAD 2>/dev/null || true)"
  if [ "$head_tag" != "$tag" ]; then
    echo "운영 체크아웃(${head_tag:-태그 아님})이 $tag 가 아니다. 먼저 'git -C $ROOT checkout $tag'" >&2
    exit 1
  fi
fi

# 터널 준비 확인: 인증 정보 파일이 없으면 Docker가 그 경로에 빈 디렉토리를 만들어 버린다.
if [ ! -f "$CREDENTIALS_FILE" ]; then
  echo "터널 credentials 파일이 없다: $CREDENTIALS_FILE (docs/ops/deploy.md 1장)" >&2
  exit 1
fi
if [ -z "$(sed -n 's/^TUNNEL_ID=//p' "$ENV_FILE" 2>/dev/null | tail -n 1)" ]; then
  echo "TUNNEL_ID가 비어 있다: $ENV_FILE" >&2
  exit 1
fi

prev="$(sed -n 's/^PIPIBOOKS_VERSION=//p' "$ENV_FILE" 2>/dev/null | tail -n 1)"

compose() { # <X.Y.Z> <args...>
  local v="$1"; shift
  PIPIBOOKS_VERSION="$v" "$DOCKER_BIN" compose -p "$PROJECT" -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile tunnel "$@"
}

check() { # <network> <url>
  "$DOCKER_BIN" run --rm --network "${PROJECT}_$1" "$CURL_IMAGE" -fsS --connect-timeout 3 --max-time 5 \
    --retry "$HEALTH_RETRIES" --retry-delay 2 --retry-all-errors "$2" | grep -q '"UP"'
}

healthy() {
  check internal http://core-api:8080/actuator/health &&
    check internal http://catalog-worker:8082/actuator/health &&
    check internal http://event-collector:8081/actuator/health &&
    check edge http://web:3000/healthz
}

record() { # <X.Y.Z>
  if grep -q '^PIPIBOOKS_VERSION=' "$ENV_FILE" 2>/dev/null; then
    sed -i "s/^PIPIBOOKS_VERSION=.*/PIPIBOOKS_VERSION=$1/" "$ENV_FILE"
  else
    echo "PIPIBOOKS_VERSION=$1" >> "$ENV_FILE"
  fi
}

new="${tag#v}"
echo "==> $tag 이미지 pull"
if ! compose "$new" pull; then
  echo "pull 실패: $tag. 실행 중인 버전(${prev:+v}${prev:-없음})을 유지한다." >&2
  exit 1
fi

# TODO(서브프로젝트 1): 마이그레이션 전 pg_dump 백업을 여기서 수행한다.

echo "==> $tag 기동 및 헬스체크"
if compose "$new" up -d --remove-orphans && healthy; then
  record "$new"
  echo "배포 완료: $tag"
  exit 0
fi

echo "배포 실패: $tag" >&2
if [ -z "$prev" ]; then
  echo "롤백할 이전 버전이 없습니다. $tag 상태 그대로 남아 있다. 수동 조치 필요." >&2
  exit 1
fi
echo "==> 이전 버전 v$prev 로 롤백" >&2
if compose "$prev" up -d --remove-orphans && healthy; then
  record "$prev"
  echo "롤백 완료: v$prev" >&2
else
  echo "ROLLBACK FAILED: v$prev 도 정상 기동하지 않았다. 수동 조치 필요." >&2
fi
exit 1

#!/usr/bin/env bash
# 운영 배포: 지정 버전 이미지를 당겨와 기동하고 헬스체크한다. 실패하면 이전 버전으로 되돌린다.
# 사용법: infra/scripts/deploy.sh vX.Y.Z
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE_FILE="$ROOT/infra/compose/compose.prod.yml"
STATE_FILE="${STATE_FILE:-$ROOT/infra/compose/.deployed-version}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
CURL_IMAGE=curlimages/curl:8.23.0
PROJECT=pipibooks

usage() { echo "사용법: $0 vX.Y.Z" >&2; exit 2; }
[ $# -eq 1 ] && [[ "$1" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || usage

tag="$1"
prev="$(cat "$STATE_FILE" 2>/dev/null || true)"

compose() { # <vX.Y.Z> <args...>
  local v="${1#v}"; shift
  PIPIBOOKS_VERSION="$v" "$DOCKER_BIN" compose -p "$PROJECT" -f "$COMPOSE_FILE" --profile tunnel "$@"
}

check() { # <network> <url>
  "$DOCKER_BIN" run --rm --network "${PROJECT}_$1" "$CURL_IMAGE" -fsS \
    --retry "$HEALTH_RETRIES" --retry-delay 2 --retry-all-errors "$2" | grep -q '"UP"'
}

healthy() {
  check internal http://core-api:8080/actuator/health &&
    check internal http://catalog-worker:8082/actuator/health &&
    check internal http://event-collector:8081/actuator/health &&
    check edge http://web:3000/healthz
}

echo "==> $tag 이미지 pull"
if ! compose "$tag" pull; then
  echo "pull 실패: $tag. 실행 중인 버전(${prev:-없음})을 유지한다." >&2
  exit 1
fi

# TODO(서브프로젝트 1): 마이그레이션 전 pg_dump 백업을 여기서 수행한다.

echo "==> $tag 기동"
compose "$tag" up -d --remove-orphans

echo "==> 헬스체크"
if healthy; then
  echo "$tag" > "$STATE_FILE"
  echo "배포 완료: $tag"
  exit 0
fi

echo "헬스체크 실패: $tag" >&2
if [ -n "$prev" ]; then
  echo "==> 이전 버전 $prev 로 롤백" >&2
  compose "$prev" up -d --remove-orphans || true
fi
exit 1

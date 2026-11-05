#!/usr/bin/env bash
# 네 서비스 이미지를 :local 태그로 빌드한다.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
(cd "$ROOT/services" && ./gradlew jibDockerBuild -Pversion=local --console=plain -q)
docker build -q -f "$ROOT/clients/web/Dockerfile" -t ghcr.io/okabedev/pipibooks-web:local "$ROOT" >/dev/null
docker build -q -t ghcr.io/okabedev/pipibooks-cloudflared:local "$ROOT/infra/cloudflared" >/dev/null
echo "built: pipibooks-{core,catalog-worker,event-collector,web,cloudflared}:local"

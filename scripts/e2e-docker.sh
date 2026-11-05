#!/usr/bin/env bash
# 웹 E2E를 컨테이너에서 실행한다(호스트에 브라우저 의존 라이브러리가 없을 때).
# 저장소는 읽기 전용으로 마운트하고 컨테이너 안 복사본에 의존성을 설치하므로 호스트 node_modules를 건드리지 않는다.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
docker run --rm -e CI=1 -v "$ROOT":/src:ro node:26.13.0-bookworm bash -c '
  set -euo pipefail
  mkdir /work
  tar -C /src --exclude=node_modules --exclude=.next --exclude=.git --exclude=services -cf - . | tar -C /work -xf -
  npm i -g pnpm@12.16.1 >/dev/null 2>&1
  cd /work && pnpm install --frozen-lockfile --ignore-scripts >/dev/null
  cd clients/web && pnpm exec playwright install --with-deps chromium >/dev/null 2>&1
  pnpm test:e2e
'

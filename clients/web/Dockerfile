# 빌드 컨텍스트: 저장소 루트 (docker build -f clients/web/Dockerfile .)
FROM node:26.13.0-alpine AS base
RUN npm i -g pnpm@12.16.1
WORKDIR /repo

FROM base AS deps
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY clients/web/package.json clients/web/
RUN pnpm install --frozen-lockfile --ignore-scripts --filter web...

FROM deps AS build
COPY clients/web clients/web
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter web build

FROM node:26.13.0-alpine AS runtime
LABEL org.opencontainers.image.source="https://github.com/okabedev/pipibooks"
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
COPY --from=build --chown=node:node /repo/clients/web/.next/standalone ./
COPY --from=build --chown=node:node /repo/clients/web/.next/static ./clients/web/.next/static
COPY --from=build --chown=node:node /repo/clients/web/public ./clients/web/public
USER node
EXPOSE 3000
CMD ["node", "clients/web/server.js"]

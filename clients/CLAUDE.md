# clients/ — 프론트엔드 (pnpm 워크스페이스)

근거: 스펙 §3·§6, ADR 0003

## 구조
| 경로 | 내용 | 상태 |
|---|---|---|
| `web/` | Next.js 16(App Router, `src/`, Tailwind 4), 웹과 `/admin` | 골격(임시 랜딩) |
| `app/` | Expo(React Native) 앱 | 서브프로젝트 3에서 생성 |
| `packages/api-client/` | core-api OpenAPI에서 생성한 TS 클라이언트 | 서브프로젝트 1 |
| `packages/shared/` | 슬라이드 타입 스키마, 도메인 상수 | 서브프로젝트 1 |

- 워크스페이스 정의와 빌드 스크립트 허용 목록(`allowBuilds`)은 루트 `pnpm-workspace.yaml`에만 둔다. 하위에 `pnpm-workspace.yaml`을 만들지 않는다.
- 의존성은 루트에서 `pnpm --filter <패키지> add <dep>`로 추가한다.

## Next.js 16 주의
- 이 버전은 학습 데이터와 API가 다를 수 있다. 코드를 쓰기 전에 `web/AGENTS.md`의 안내대로 `node_modules/next/dist/docs/`의 해당 문서를 확인한다.
- `cacheComponents: true`다. `export const dynamic = ...` 같은 세그먼트 설정을 쓰지 않는다. 요청 시점 실행이 필요하면 `await connection()`(`next/server`)을 쓰고, 캐시가 필요하면 `use cache`를 쓴다.
- `output: 'standalone'`, `outputFileTracingRoot`는 저장소 루트다(Docker 이미지용).

## 코드 규칙
- TypeScript strict. API 호출은 `packages/api-client`를 통해서만(서브프로젝트 1부터).
- 서버 응답 타입을 손으로 쓰지 않는다(OpenAPI 생성 타입 사용).
- 포맷은 Prettier(루트 `.prettierrc.json`), 린트는 `web/eslint.config.mjs`.

## 테스트 규칙
- TDD: 실패하는 테스트 먼저.
- 단위: Vitest + Testing Library(`src/**/*.test.tsx`, jsdom).
- E2E: Playwright(`web/e2e/`, chromium). 호스트에 브라우저 의존 라이브러리가 없으면 `pnpm test:e2e:docker`(루트)로 컨테이너에서 실행한다. 저장소를 읽기 전용으로 마운트하므로 호스트 `node_modules`를 바꾸지 않는다. 저장소를 쓰기 가능하게 마운트한 채 컨테이너에서 pnpm을 실행하지 않는다.

## 명령 (루트에서 실행)
| 명령 | 용도 |
|---|---|
| `pnpm --filter web dev` | 개발 서버(3000) |
| `pnpm --filter web lint` | ESLint |
| `pnpm --filter web test` | 단위 테스트 |
| `pnpm --filter web test:e2e` | E2E(빌드 후 실행) |
| `docker build -f clients/web/Dockerfile -t ghcr.io/okabedev/pipibooks-web:local .` | 이미지 빌드 |

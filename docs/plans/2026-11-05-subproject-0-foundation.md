# 서브프로젝트 0: 기반 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 저장소·도구 체인·훅·CI·문서 골격·컨테이너 골격을 갖추고, `v0.0.1`을 Git Flow로 릴리스해 Cloudflare Tunnel로 `pipibooks.com`에 임시 페이지를 공개한다.

**Architecture:** 모노레포 하나에 Gradle 멀티 프로젝트(`services/`), pnpm 워크스페이스(`clients/`), compose 기반 인프라(`infra/`)를 둔다. 세 JVM 서비스와 웹은 헬스 엔드포인트만 가진 골격이며, 도메인 기능은 서브프로젝트 1에서 추가한다. 규칙은 lefthook·commitlint·ktlint·detekt·ESLint·ArchUnit·CI·Claude 훅으로 강제한다.

**Tech Stack:** Kotlin 2.4.21, Spring Boot 4.1.2, JDK 25, Gradle 9.8.2, Jib 3.5.4, Next.js 16.4.5, pnpm 12.16.1, Node 26.13.0, PostgreSQL 18.6, Redis 8.10.3, cloudflared 2026.10.7, GitHub Actions

**Spec:** `docs/specs/2026-11-05-architecture-design.md` (ADR: `docs/architecture/adr/`)

## Global Constraints

- 매 세션 로드 문서(`**/CLAUDE.md`)는 **200줄 미만**(199줄 이하). `docs/` 참조 문서는 300줄 이하(`docs/plans/` 제외).
- 커밋: `<type>(<scope>): <한국어 제목>`. 제목 50자 이내, 마침표 없음. scope 필수.
  - type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
  - scope: `core-api` `catalog-worker` `event-collector` `libs` `web` `app` `shared` `infra` `docs` `repo`
- 브랜치: `main`, `develop`, `feature/<이슈번호>-<소문자-slug>`, `release/x.y.z`, `hotfix/x.y.z`. 모든 작업은 feature 브랜치 → PR → `develop`(squash). `main`·`develop` 직접 커밋 금지, `--no-verify` 금지.
- 커밋 작성자: 저장소 로컬 설정 `okabedev <81132541+okabedev@users.noreply.github.com>`(이미 설정됨).
- 버전은 위 Tech Stack 값으로 고정한다. 이미지 태그: `eclipse-temurin:25.0.5_8-jre`, `node:26.13.0-alpine`, `postgres:18.6-alpine`, `redis:8.10.3-alpine`, `cloudflare/cloudflared:2026.10.7`, `curlimages/curl:8.23.0`.
- 이미지 이름: `ghcr.io/okabedev/pipibooks-<web|core-api|catalog-worker|event-collector>:<버전>`.
- 포트: web 3000, core-api 8080, event-collector 8081, catalog-worker 8082. 호스트 포트는 열지 않는다(운영 Postgres만 `127.0.0.1:5432`).
- 패키지: `com.pipibooks.core`, `com.pipibooks.catalog`, `com.pipibooks.event`.
- 시크릿은 저장소에 넣지 않는다(`*.env`, `infra/cloudflared/credentials.json`은 gitignore).

## Review Focus

1. 한글 커밋 제목의 길이 경계: 50자는 통과, 51자는 거부되어야 한다. → Task 2 테스트
2. 분리된 HEAD(rebase 중)나 태그 push에서 브랜치 검사가 작업을 막으면 안 된다. → Task 2 테스트
3. `wc -l`은 마지막 줄바꿈이 없는 파일을 한 줄 적게 센다. 200줄 경계는 실제 줄 수로 판정해야 한다. → Task 2 테스트
4. Claude 가드 훅이 feature 브랜치의 정상 커밋까지 막으면 작업이 멈춘다. `develop`에서의 커밋은 막아야 한다. → Task 4 테스트
5. 존재하지 않는 버전으로 배포하면, 실행 중인 버전이 그대로 유지되어야 한다. → Task 9 테스트

## 작업 진행 방식 (모든 Task 공통)

1. 해당 Task의 이슈 번호 `N`으로 `develop`에서 `feature/N-<slug>`를 만든다(이슈 번호는 Task 1이 생성한 표 참조).
2. Task를 끝내면 `gh pr create --base develop`으로 PR을 연다. 제목은 Task의 커밋 형식을 따르고, 본문에 `Closes #N`을 넣는다.
3. CI 도입(Task 8) 이후에는 `ci-ok` 통과를 확인한 뒤 `gh pr merge --squash`로 머지한다.

## 파일 구조

| 경로 | 책임 | Task |
|---|---|---|
| `scripts/github/setup-repo.sh` | 라벨·마일스톤·이슈·룰셋을 멱등하게 설정 | 1 |
| `package.json`, `pnpm-workspace.yaml`, `.tool-versions`, `.nvmrc`, `.editorconfig`, `.gitattributes`, `.prettierrc.json` | 루트 도구 체인 | 2 |
| `commitlint.config.mjs`, `lefthook.yml` | 커밋·브랜치·스타일 강제 | 2 (이후 Task에서 확장) |
| `scripts/check-docs.sh`, `scripts/check-branch-name.sh`, `scripts/tests/*.test.sh`, `scripts/tests/run.sh` | 검사 스크립트와 테스트 | 2 |
| `CLAUDE.md`, `docs/architecture/overview.md`, `docs/domain/{model,feed}.md`, `docs/conventions/{git,api}.md`, 템플릿 | 문서 골격 | 3 |
| `.claude/settings.json`, `.claude/hooks/*`, `.claude/skills/{start-feature,release}/SKILL.md` | Claude 일관성 장치 | 4 |
| `services/**`, `services/CLAUDE.md` | Gradle 멀티 프로젝트와 세 서비스 골격 | 5 |
| `clients/web/**`, `clients/CLAUDE.md` | Next.js 골격(임시 랜딩) | 6 |
| `infra/compose/*`, `infra/postgres/init/*`, `infra/env/*.example`, `infra/cloudflared/config.yml`, `infra/scripts/{build-local,smoke-test}.sh`, `infra/CLAUDE.md` | 컨테이너 골격 | 7 |
| `.github/workflows/{ci,release}.yml` | CI·릴리스 파이프라인 | 8 |
| `infra/scripts/deploy.sh`, `cliff.toml`, `CHANGELOG.md`, `docs/ops/deploy.md` | 배포·릴리스 | 9 |

---

### Task 1: GitHub 저장소 설정

**Files:**
- Create: `scripts/github/setup-repo.sh`

**Interfaces:**
- Produces: 이슈 번호 표(Task 2~9). 아래 Step 3의 출력을 이 계획 맨 끝 "이슈 번호" 표에 기록하고 같은 PR로 커밋한다.

- [ ] **Step 1: `scripts/github/setup-repo.sh` 작성** — `gh` CLI를 쓰고, 이미 있는 항목은 건너뛰거나 갱신하도록 멱등하게 만든다.
  - 라벨: `type:<type>`(커밋 type 11개), `scope:<scope>`(scope 10개)
  - 마일스톤: `0 기반`, `1 코어 MVP`, `2 독서 기록·통계`, `3 모바일 앱`, `4 본격 추천`
  - 이슈(마일스톤 `0 기반`): Task 2~9의 제목(각 Task 헤더의 한국어 이름)으로 만들고, 같은 제목의 이슈가 있으면 건너뛴다.
  - 룰셋 `protect-main-develop`(`gh api repos/okabedev/pipibooks/rulesets`): 대상 `refs/heads/main`, `refs/heads/develop`. 규칙은 `deletion`, `non_fast_forward`, `pull_request`(`required_approving_review_count: 0`). 우회 대상은 두지 않는다.
  - 보안: `gh api -X PATCH repos/okabedev/pipibooks`로 `security_and_analysis`의 `secret_scanning`과 `secret_scanning_push_protection`을 `enabled`로 설정한다.
- [ ] **Step 2: 실행** — `bash scripts/github/setup-repo.sh`를 두 번 실행한다. 두 번째 실행은 오류 없이 "skip"만 출력해야 한다.
- [ ] **Step 3: 검증**
  - Run: `gh api repos/okabedev/pipibooks/rulesets --jq '.[].name'` → `protect-main-develop`
  - Run: `gh issue list --milestone "0 기반" --json number,title` → 8건. 이 결과를 계획 끝 표에 기록한다.
  - Run: `gh api repos/okabedev/pipibooks/rules/branches/develop --jq '.[].type'` → `deletion`, `non_fast_forward`, `pull_request` 포함
- [ ] **Step 4: 커밋·PR** — `chore(repo): GitHub 저장소 설정 스크립트 추가`, 브랜치 `feature/1-repo-setup`, `Refs: #1`

### Task 2: 루트 도구 체인과 커밋·브랜치·문서 검사

**Files:**
- Create: `package.json`, `pnpm-workspace.yaml`, `.tool-versions`, `.nvmrc`, `.editorconfig`, `.gitattributes`, `.prettierrc.json`, `.prettierignore`, `commitlint.config.mjs`, `lefthook.yml`, `scripts/check-docs.sh`, `scripts/check-branch-name.sh`, `scripts/tests/run.sh`, `scripts/tests/lib.sh`, `scripts/tests/check-docs.test.sh`, `scripts/tests/check-branch-name.test.sh`, `scripts/tests/commitlint.test.sh`
- Modify: `.gitignore` (`node_modules/`, `.gradle/`, `build/`, `.next/`, `test-results/`, `playwright-report/`, `infra/cloudflared/credentials.json`, `infra/compose/.env` 추가)

**Interfaces:**
- Produces:
  - `scripts/check-docs.sh [root=.]` → 위반 시 `<path>: <n> lines (max <m>)`를 출력하고 exit 1
  - `scripts/check-branch-name.sh [branch]` → 인자가 없으면 현재 브랜치 검사, 분리된 HEAD면 exit 0
  - `pnpm test:scripts` → `scripts/tests/run.sh`(모든 `*.test.sh` 실행)
  - `lefthook.yml`: `commit-msg`(commitlint), `pre-push`(브랜치 검사), `pre-commit`(문서 검사). 이후 Task가 `pre-commit` 명령을 추가한다.

- [ ] **Step 1: 루트 설정 파일 작성**
  - `package.json`: `"private": true`, `"packageManager": "pnpm@12.16.1"`, `"engines": {"node": ">=26.13.0"}`
    - 스크립트: `"prepare": "lefthook install"`, `"test:scripts": "bash scripts/tests/run.sh"`, `"check:docs": "bash scripts/check-docs.sh"`
    - devDependencies: `@commitlint/cli@21.2.4`, `@commitlint/config-conventional@21.2.4`, `lefthook@2.2.2`, `prettier@3.9.10`
  - `pnpm-workspace.yaml`의 packages: `clients/web`, `clients/app`, `clients/packages/*`
  - `.tool-versions`: `java temurin-25.0.5+8`, `nodejs 26.13.0`, `pnpm 12.16.1`
  - `.nvmrc`: `26.13.0`
  - `.editorconfig`: UTF-8, LF, 들여쓰기 2칸. `*.{kt,kts}`만 4칸.
  - `.gitattributes`: `* text=auto eol=lf`, `*.sh text eol=lf`
- [ ] **Step 2: 실패하는 테스트 작성** — 각 테스트 파일은 `assert_pass "<설명>" <명령>`과 `assert_fail "<설명>" <명령>` 헬퍼를 쓴다(`run.sh`가 source하는 `scripts/tests/lib.sh`에 정의). 실패가 하나라도 있으면 exit 1.
  - `commitlint.test.sh`: 메시지를 `pnpm exec commitlint`의 stdin으로 넣는다.
    - 통과: `feat(web): 랜딩 페이지 추가`, `docs(docs): 계획 추가 (#12)`, 제목이 정확히 50자인 한글 메시지
    - 거부: `feat: 범위 없음`, `feat(foo): 잘못된 범위`, `wip(web): 잘못된 타입`, `feat(web): 마침표.`, 제목이 51자인 한글 메시지
  - `check-branch-name.test.sh`:
    - 통과: `main`, `develop`, `feature/12-landing-page`, `release/0.1.0`, `hotfix/0.1.1`
    - 거부: `feature/landing`, `feature/12-Landing`, `release/v0.1.0`, `fix/12-x`
    - 분리된 HEAD: 임시 저장소에서 `git checkout --detach` 후 인자 없이 실행하면 통과
  - `check-docs.test.sh`: `mktemp -d` 안에 픽스처를 만든다.
    - `CLAUDE.md` 199줄 → 통과
    - `CLAUDE.md` 200줄 → 거부
    - `sub/CLAUDE.md` 200줄이고 마지막 줄바꿈 없음 → 거부(Review Focus 3)
    - `docs/a.md` 301줄 → 거부
    - `docs/plans/p.md` 1000줄 → 통과
    - `node_modules/x/CLAUDE.md` 500줄 → 통과(제외 대상)
- [ ] **Step 3: 실패 확인** — Run: `pnpm install && pnpm test:scripts` → Expected: commitlint 설정과 스크립트가 없어 FAIL
- [ ] **Step 4: 구현**
  - `commitlint.config.mjs`: `extends: ['@commitlint/config-conventional']`. 규칙은 `type-enum`, `scope-enum`(Global Constraints 값), `scope-empty: [2,'never']`, `subject-max-length: [2,'always',50]`, `subject-full-stop: [2,'never','.']`, `subject-case: [0]`.
  - `check-docs.sh`: 줄 수는 `awk 'END{print NR}'`로 센다(마지막 줄바꿈이 없어도 정확함). 대상은 `CLAUDE.md`(최대 199줄)와 `docs/**/*.md`(최대 300줄, `docs/plans/` 제외). `node_modules`, `.git`, `build`, `.next`는 제외한다.
  - `check-branch-name.sh`: 정규식 `^(main|develop|feature/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*|release/[0-9]+\.[0-9]+\.[0-9]+|hotfix/[0-9]+\.[0-9]+\.[0-9]+)$`. 현재 브랜치는 `git symbolic-ref --short -q HEAD`로 구하고, 비어 있으면 exit 0.
  - `lefthook.yml`: `commit-msg`는 `pnpm exec commitlint --edit {1}`, `pre-push`는 `bash scripts/check-branch-name.sh`, `pre-commit`은 `docs` 명령(glob `*.md`, `bash scripts/check-docs.sh`)과 `prettier` 명령(glob `*.{json,yml,yaml,mjs,js,ts,tsx,css}`, `pnpm exec prettier --check {staged_files}`)
- [ ] **Step 5: 통과 확인** — Run: `pnpm test:scripts` → Expected: 모든 assert PASS, exit 0. Run: `bash scripts/check-docs.sh` → exit 0.
- [ ] **Step 6: 훅 동작 확인** — Run: `git commit --allow-empty -m "feat: 범위 없음"` → Expected: commitlint가 거부. 확인 후 정상 메시지로 커밋한다.
- [ ] **Step 7: 커밋·PR** — `build(repo): 루트 도구 체인과 커밋·브랜치·문서 검사 추가`

### Task 3: 문서 골격과 템플릿

**Files:**
- Create: `CLAUDE.md`, `docs/architecture/overview.md`, `docs/domain/model.md`, `docs/domain/feed.md`, `docs/conventions/git.md`, `docs/conventions/api.md`, `docs/architecture/adr/template.md`, `docs/specs/template.md`, `.github/PULL_REQUEST_TEMPLATE.md`, `.github/ISSUE_TEMPLATE/{feature,bug}.md`, `.github/ISSUE_TEMPLATE/config.yml`

**Interfaces:**
- Consumes: `scripts/check-docs.sh`(Task 2)
- Produces: 루트 `CLAUDE.md`의 "문서 색인"과 "작업 규모별 절차" 표. 이후 Task가 하위 `CLAUDE.md`를 추가할 때 이 색인에 한 줄씩 덧붙인다.

- [ ] **Step 1: 루트 `CLAUDE.md` 작성** (목표 120줄 이하). 섹션 순서는 다음과 같다.
  1. 프로젝트 한 단락 요약
  2. 디렉토리 맵(스펙 §8)
  3. **고정 워크플로**(스펙 §9.2)
  4. **작업 규모별 절차** 표
     - 문서 오탈자, 설정 값 변경 → 바로 PR
     - 기존 흐름 안의 작은 변경 → 채팅으로 설계 합의 후 TDD
     - 새 기능이나 구조 변경 → 스펙과 계획 작성 후 구현
  5. 커밋·브랜치 규칙 요약(상세는 `docs/conventions/git.md`)
  6. 자주 쓰는 명령(`pnpm test:scripts`, `pnpm check:docs`. 이후 Task가 추가)
  7. 문서 색인: "문서 경로 — 언제 읽는가" 한 줄씩. 스펙, 계획, ADR, overview, domain, conventions를 포함한다.
  8. 금지 사항
- [ ] **Step 2: 참조 문서 작성** — 스펙 내용을 규칙 중심으로 옮긴다. 이유는 ADR에 링크만 건다.
  - `overview.md` ← 스펙 §3, §4.1
  - `model.md` ← §4.2, §4.3
  - `feed.md` ← §5.4, §5.5
  - `git.md` ← §10 전체와 Task 진행 방식, PR 제목 규칙(squash 커밋 메시지가 되므로 커밋 규칙과 같음), 릴리스 절차(Task 9에서 보강)
  - `api.md` ← §6
- [ ] **Step 3: 템플릿 작성**
  - PR 템플릿: `## 변경 내용` / `## 이유` / `## 테스트` / `## 체크리스트`(문서 갱신, 테스트 추가, 커밋 규칙) / `Closes #`
  - 이슈 템플릿: 기능용(배경, 완료 기준, 범위 밖)과 버그용(재현 절차, 기대 동작, 실제 동작). `config.yml`은 `blank_issues_enabled: false`.
  - ADR 템플릿: `docs/architecture/adr/README.md`의 형식 줄을 그대로 펼친다.
  - 스펙 템플릿: 스펙 문서의 장 제목 구조(목표와 범위, 결정 요약, 설계, 구현 전에 확인할 사항)
- [ ] **Step 4: 검증** — Run: `bash scripts/check-docs.sh` → exit 0. Run: `awk 'END{print NR}' CLAUDE.md` → 199 이하.
- [ ] **Step 5: 커밋·PR** — `docs(docs): 루트 CLAUDE.md와 참조 문서·템플릿 추가`

### Task 4: Claude Code 일관성 장치

**Files:**
- Create: `.claude/settings.json`, `.claude/hooks/guard-bash.mjs`, `.claude/hooks/format-file.mjs`, `.claude/skills/start-feature/SKILL.md`, `.claude/skills/release/SKILL.md`, `scripts/tests/guard-bash.test.sh`
- Modify: `CLAUDE.md`(색인에 스킬 두 줄 추가)

**Interfaces:**
- Consumes: `scripts/tests/lib.sh`(Task 2), Prettier(Task 2)
- Produces: `guard-bash.mjs`는 stdin으로 Claude 훅 JSON(`.tool_input.command`, `.cwd`)을 받는다. 차단하면 stderr에 이유를 쓰고 exit 2, 허용하면 exit 0.

- [ ] **Step 1: 실패하는 테스트 작성** (`guard-bash.test.sh`) — 임시 git 저장소를 만들고, 브랜치를 바꿔 가며 JSON을 넣는다.
  - `develop`에서 `git commit -am "x"` → 차단(exit 2)
  - `main`에서 `git commit -m "x"` → 차단
  - `feature/3-docs`에서 `git commit -m "docs(docs): 추가"` → 허용(Review Focus 4)
  - 아무 브랜치에서 `git commit --no-verify -m x` → 차단
  - `git push --force origin feature/3-docs` → 차단
  - `git push -f` → 차단
  - `git push --force-with-lease origin feature/3-docs` → 허용
  - `ls -la` → 허용
- [ ] **Step 2: 실패 확인** — Run: `pnpm test:scripts` → guard 테스트 FAIL
- [ ] **Step 3: 구현**
  - `guard-bash.mjs`(Node, 의존성 없음): stdin JSON에서 명령과 cwd를 읽는다. 브랜치는 `child_process.execFileSync('git', ['-C', cwd, 'symbolic-ref', '--short', '-q', 'HEAD'])`로 구한다.
    - 차단 규칙 1: `--no-verify`가 포함된 명령
    - 차단 규칙 2: `git push` 명령에 `--force`(`--force-with-lease` 제외)나 독립 토큰 `-f`가 있음
    - 차단 규칙 3: `git commit`이고, `git -C <cwd> symbolic-ref --short -q HEAD`가 `main`이나 `develop`
  - `format-file.mjs`(Node): PostToolUse JSON의 `.tool_input.file_path`를 읽는다. 확장자가 `ts|tsx|js|mjs|cjs|json|css|yml|yaml`이고 `pnpm-lock.yaml`이 아니면 `pnpm exec prettier --write <file>`를 실행한다. 실패해도 exit 0(편집 흐름을 막지 않음).
  - `settings.json`: `PreToolUse` matcher `Bash` → `node .claude/hooks/guard-bash.mjs`, `PostToolUse` matcher `Edit|Write` → `node .claude/hooks/format-file.mjs`
  - `start-feature/SKILL.md`: 이슈 확인(없으면 생성) → `develop` 최신화 → `feature/N-slug` 생성 → CLAUDE.md의 작업 규모별 절차 표로 절차 결정 → 필요하면 superpowers brainstorming이나 writing-plans 사용
  - `release/SKILL.md`: `docs/conventions/git.md`의 릴리스 절차를 실행하는 단계 목록(Task 9에서 보강)
- [ ] **Step 4: 통과 확인** — Run: `pnpm test:scripts` → 모든 assert PASS
- [ ] **Step 5: 커밋·PR** — `chore(repo): Claude 가드 훅·포매터 훅·프로젝트 스킬 추가`

### Task 5: services 골격 (Gradle 멀티 프로젝트와 세 서비스)

**Files:**
- Create:
  - 빌드 설정: `services/settings.gradle.kts`, `services/build.gradle.kts`, `services/gradle.properties`, `services/gradle/libs.versions.toml`, `services/gradlew*`, `services/gradle/wrapper/*`
  - 컨벤션 플러그인: `services/build-logic/settings.gradle.kts`, `services/build-logic/build.gradle.kts`, `services/build-logic/src/main/kotlin/pipibooks.kotlin-jvm.gradle.kts`, `services/build-logic/src/main/kotlin/pipibooks.spring-boot-app.gradle.kts`
  - 정적 분석 설정: `services/config/detekt/detekt.yml`
  - 각 서비스 `<svc>`(`core-api`, `catalog-worker`, `event-collector`): `build.gradle.kts`, `src/main/kotlin/.../<Name>Application.kt`, `src/main/resources/application.yml`, `src/test/kotlin/.../HealthEndpointTest.kt`
  - core-api 아키텍처 테스트: `services/core-api/src/test/kotlin/com/pipibooks/core/ArchitectureTest.kt`
  - 하위 문서: `services/CLAUDE.md`
- Modify: `lefthook.yml`(pre-commit에 `ktlint` 명령 추가), `CLAUDE.md`(명령과 색인)

**Interfaces:**
- Produces:
  - 플러그인 ID `pipibooks.kotlin-jvm`, `pipibooks.spring-boot-app`
  - Gradle 태스크 `./gradlew build`, `./gradlew jibDockerBuild -Pversion=<v>`, `./gradlew jib -Pversion=<v>`
  - 이미지 `ghcr.io/okabedev/pipibooks-<project.name>:<version>`
  - 메인 클래스 `com.pipibooks.core.CoreApiApplication`, `com.pipibooks.catalog.CatalogWorkerApplication`, `com.pipibooks.event.EventCollectorApplication`

- [ ] **Step 1: Gradle 래퍼와 버전 카탈로그**
  - Gradle 9.8.2 래퍼(`gradle wrapper --gradle-version 9.8.2`. 로컬에 gradle이 없으면 임시 컨테이너 `gradle:9.8.2-jdk25`에서 생성)
  - `libs.versions.toml`: kotlin `2.4.21`, spring-boot `4.1.2`, spring-dependency-management `1.1.7`, ktlint-gradle `14.2.0`, detekt `1.23.8`, jib `3.5.4`, archunit `1.5.1`
  - `gradle.properties`의 `version=0.0.0-SNAPSHOT`. 릴리스 때는 `-Pversion`으로 덮어쓴다.
- [ ] **Step 2: 컨벤션 플러그인**
  - `pipibooks.kotlin-jvm`:
    - 기본: kotlin jvm, JVM 툴체인 25, 컴파일러 옵션 `-Xjsr305=strict`, JUnit Platform
    - ktlint 적용
    - detekt 적용: 설정 파일 `services/config/detekt/detekt.yml`, `buildUponDefaultConfig = true`
    - detekt 설정의 `detekt` 구성에서는 `org.jetbrains.kotlin` 의존성을 `2.0.21`로 고정한다. detekt 1.23.8이 Kotlin 2.0.21로 빌드되었기 때문이다.
    - 같은 이유로 `Detekt`·`DetektCreateBaselineTask` 태스크의 `jvmTarget`은 `"22"`로 둔다(내장 컴파일러가 25를 모름). 컴파일 대상은 툴체인 25 그대로다.
  - `pipibooks.spring-boot-app`:
    - 기본: 위 플러그인, `org.springframework.boot`, `io.spring.dependency-management`, `kotlin("plugin.spring")`, Jib
    - Jib `from.image = eclipse-temurin:25.0.5_8-jre`
    - Jib `to.image = ghcr.io/okabedev/pipibooks-${project.name}`, `to.tags = setOf(version)`
    - Jib `container.jvmFlags = -XX:MaxRAMPercentage=75`, `container.user = 1000`
    - Jib 이미지 라벨 `org.opencontainers.image.source=https://github.com/okabedev/pipibooks`
- [ ] **Step 3: 실패하는 테스트 작성**
  - 각 서비스의 `HealthEndpointTest`: `GET /actuator/health`가 200이고 본문 `status`가 `"UP"`인지 확인한다.
    - core-api는 MockMvc로 테스트한다(`spring-boot-starter-webmvc-test`).
    - catalog-worker와 event-collector는 WebTestClient로 테스트한다(`spring-boot-starter-webflux-test`).
  - core-api의 `ArchitectureTest`: ArchUnit `slices().matching("com.pipibooks.core.(*)..").should().beFreeOfCycles()`. 아직 모듈이 없으므로 `allowEmptyShould(true)`를 건다. 이후 모듈이 추가되면 이 규칙이 그대로 적용된다.
- [ ] **Step 4: 실패 확인** — Run: `cd services && ./gradlew test` → Expected: 애플리케이션 클래스가 없어 FAIL
- [ ] **Step 5: 구현**
  - core-api: `spring-boot-starter-webmvc`, `spring-boot-starter-actuator`, `tools.jackson.module:jackson-module-kotlin`, `kotlin-reflect`
  - catalog-worker, event-collector: `spring-boot-starter-webflux`, actuator, `kotlinx-coroutines-reactor`
  - 각 `application.yml`:
    - `spring.application.name`
    - `server.port`: Global Constraints의 포트
    - `spring.threads.virtual.enabled: true`(core-api만)
    - `management.endpoints.web.exposure.include: health`
    - `management.endpoint.health.probes.enabled: true`
    - `logging.structured.format.console: ecs`
  - DB 의존성은 넣지 않는다(서브프로젝트 1에서 추가).
- [ ] **Step 6: 통과 확인** — Run: `cd services && ./gradlew build` → Expected: BUILD SUCCESSFUL(테스트, ktlintCheck, detekt 포함)
- [ ] **Step 7: 이미지 확인** — Run: `./gradlew jibDockerBuild -Pversion=local && docker images 'ghcr.io/okabedev/pipibooks-*'` → Expected: 이미지 3개, 태그 `local`
- [ ] **Step 8: 문서와 훅**
  - `services/CLAUDE.md`(목표 80줄 이하): 모듈과 포트, 실행 모델(core는 MVC와 가상 스레드, 나머지는 코루틴과 WebFlux), 패키지 규칙, 테스트 규칙(TDD, Testcontainers는 서브프로젝트 1부터), 명령. "표준 예시 코드는 서브프로젝트 1의 위시리스트에서 지정한다"는 문장을 넣는다.
  - `lefthook.yml`의 pre-commit에 `ktlint` 명령을 추가한다: glob `services/**/*.{kt,kts}`, `cd services && ./gradlew ktlintCheck --quiet`
- [ ] **Step 9: 커밋·PR** — `build(core-api): services Gradle 멀티 프로젝트와 서비스 골격 추가`

### Task 6: clients/web 골격 (임시 랜딩)

**Files:**
- Create: `clients/web/**`(create-next-app 생성물), `clients/web/src/app/page.tsx`, `clients/web/src/app/healthz/route.ts`, `clients/web/src/app/page.test.tsx`, `clients/web/vitest.config.ts`, `clients/web/playwright.config.ts`, `clients/web/e2e/landing.spec.ts`, `clients/web/Dockerfile`, `.dockerignore`, `clients/CLAUDE.md`
- Modify: `lefthook.yml`(pre-commit에 `eslint` 명령 추가), `CLAUDE.md`

**Interfaces:**
- Produces:
  - `GET /` → `<h1>pipibooks</h1>`
  - `GET /healthz` → 200 `{"status":"UP"}`
  - pnpm 스크립트(`--filter web`): `dev`, `build`, `start`, `lint`, `test`(vitest run), `test:e2e`(playwright)
  - 이미지 `ghcr.io/okabedev/pipibooks-web:<버전>`(루트를 빌드 컨텍스트로 사용)

- [ ] **Step 1: 생성** — Run: `pnpm create next-app@16.4.5 clients/web --ts --eslint --tailwind --app --src-dir --import-alias "@/*" --use-pnpm --skip-install`. 생성된 의존성 버전은 그대로 쓴다. `package.json`의 `name`은 `web`으로 바꾸고, 하위에 생긴 `.git`이 있으면 삭제한다. 그다음 루트에서 `pnpm install`을 실행한다.
- [ ] **Step 2: 실패하는 테스트 작성**
  - `page.test.tsx`(Vitest, Testing Library, jsdom): `getByRole('heading', {level: 1, name: 'pipibooks'})`가 존재하는지 확인한다.
  - `e2e/landing.spec.ts`:
    - `/`의 h1이 `pipibooks`이고, `<html lang="ko">`이다.
    - `/healthz` 응답이 200이고, JSON `status`가 `UP`이다.
  - `playwright.config.ts`: `webServer`는 `pnpm build && pnpm start`(포트 3000), 브라우저는 chromium만 쓴다.
- [ ] **Step 3: 실패 확인** — Run: `pnpm --filter web test` → Expected: FAIL(기본 페이지에 h1 `pipibooks`가 없음)
- [ ] **Step 4: 구현**
  - `layout.tsx`: `lang="ko"`. metadata는 title `pipibooks`, description `넘겨보는 도서 추천, pipibooks`, openGraph(siteName `pipibooks`, locale `ko_KR`).
  - `page.tsx`: h1 `pipibooks`, 문단 `책을 넘겨보는 새로운 방법, 곧 찾아올게요.`
  - `healthz/route.ts`: `GET`에서 `Response.json({status: 'UP'})`를 반환하고, `dynamic = 'force-dynamic'`으로 둔다.
  - `next.config.ts`: `output: 'standalone'`, `outputFileTracingRoot`는 저장소 루트(`path.join(__dirname, '../..')`).
- [ ] **Step 5: 통과 확인** — Run: `pnpm --filter web lint && pnpm --filter web test && pnpm --filter web exec playwright install chromium && pnpm --filter web test:e2e` → Expected: 모두 PASS
- [ ] **Step 6: Dockerfile**
  - 기본: 다단계 빌드, 베이스 `node:26.13.0-alpine`, `npm i -g pnpm@12.16.1`
  - 의존성 단계: 루트의 `package.json`, `pnpm-lock.yaml`, `pnpm-workspace.yaml`, `clients/web/package.json`을 복사하고 `pnpm install --frozen-lockfile --filter web...`
  - 빌드 단계: `pnpm --filter web build`
  - 런타임 단계: `clients/web/.next/standalone/`을 `/app`에, `.next/static`과 `public`을 standalone 안의 `clients/web/` 아래에 복사한다.
  - 실행: `USER node`, `ENV PORT=3000 HOSTNAME=0.0.0.0`, `CMD ["node","clients/web/server.js"]`
  - 라벨: `org.opencontainers.image.source`
  - 루트 `.dockerignore`: `node_modules`, `**/.next`, `services`, `.git`, `infra/env`
- [ ] **Step 7: 이미지 확인** — Run: `docker build -f clients/web/Dockerfile -t ghcr.io/okabedev/pipibooks-web:local . && docker run --rm -d -p 13000:3000 --name web-check ghcr.io/okabedev/pipibooks-web:local && sleep 3 && curl -fsS localhost:13000/healthz; docker rm -f web-check` → Expected: `{"status":"UP"}`
- [ ] **Step 8: 문서와 훅**
  - `clients/CLAUDE.md`(목표 60줄 이하): 워크스페이스 구조, 공유 패키지 계획(`api-client`, `shared`는 서브프로젝트 1에서 추가), 명령, 테스트 규칙
  - `lefthook.yml`의 pre-commit에 `eslint` 명령을 추가한다: `root: clients/web/`, glob `*.{ts,tsx}`, `pnpm exec eslint {staged_files}`
- [ ] **Step 9: 커밋·PR** — `feat(web): Next.js 골격과 임시 랜딩 페이지 추가`

### Task 7: 컨테이너 골격 (compose, DB 초기화, 스모크 테스트)

**Files:**
- Create: `infra/compose/compose.dev.yml`, `infra/compose/compose.prod.yml`, `infra/compose/.env.example`, `infra/env/postgres.env.example`, `infra/postgres/init/01-init.sh`, `infra/cloudflared/config.yml`, `infra/scripts/build-local.sh`, `infra/scripts/smoke-test.sh`, `infra/CLAUDE.md`
- Modify: `CLAUDE.md`

**Interfaces:**
- Consumes: Task 5·6의 이미지와 헬스 엔드포인트
- Produces:
  - compose 프로젝트 이름 `pipibooks`(운영)와 `pipibooks-dev`(개발). 네트워크 `pipibooks_edge`, `pipibooks_internal`.
  - `infra/compose/.env`의 변수: `PIPIBOOKS_VERSION`, `TUNNEL_ID`
  - `infra/scripts/build-local.sh`: 네 이미지를 `:local` 태그로 빌드
  - `infra/scripts/smoke-test.sh`: 운영 compose를 `PIPIBOOKS_VERSION=local`로 띄워 검증하고 정리. 통과하면 exit 0.

- [ ] **Step 1: 실패하는 스모크 테스트 작성** (`smoke-test.sh`)
  - 기동: `docker compose -p pipibooks-smoke -f infra/compose/compose.prod.yml up -d`. 스모크 전용 env는 임시 파일로 만들어 쓴다.
  - 헬스 확인: `docker run --rm --network pipibooks-smoke_internal curlimages/curl:8.23.0 -fsS --retry 30 --retry-delay 2 --retry-all-errors <url>`
    - `http://core-api:8080/actuator/health`
    - `http://catalog-worker:8082/actuator/health`
    - `http://event-collector:8081/actuator/health`
    - web은 `pipibooks-smoke_edge` 네트워크에서 `http://web:3000/healthz`
    - 응답에 `"UP"`이 있어야 한다.
  - 스키마 권한 확인: `docker compose exec postgres psql -U postgres -d pipibooks -tAc "<SQL>"`의 결과가 각각 다음과 같아야 한다.
    - `has_schema_privilege('core_app','catalog','USAGE')` = `t`
    - `has_schema_privilege('core_app','catalog','CREATE')` = `f`
    - `has_schema_privilege('catalog_app','catalog','CREATE')` = `t`
    - `has_schema_privilege('events_app','core','USAGE')` = `f`
    - `has_schema_privilege('core_app','public','CREATE')` = `f`
  - 정리: `trap`으로 `down -v`를 실행한다.
  - cloudflared는 `profiles: ["tunnel"]`로 빼서 스모크에서는 기동하지 않는다.
- [ ] **Step 2: 실패 확인** — Run: `bash infra/scripts/build-local.sh && bash infra/scripts/smoke-test.sh` → Expected: compose 파일이 없어 FAIL
- [ ] **Step 3: 구현**
  - `compose.prod.yml`:
    - 서비스 `web`, `core-api`, `catalog-worker`, `event-collector`, `postgres`, `redis`, `cloudflared`. 이미지는 Global Constraints와 `${PIPIBOOKS_VERSION}`을 쓴다.
    - 네트워크: web과 cloudflared는 `edge`, core-api와 event-collector는 `edge`와 `internal`, 나머지는 `internal`
    - 모든 서비스에 `restart: unless-stopped`, logging `json-file`(`max-size: 10m`, `max-file: 5`)
    - `mem_limit`: JVM 서비스 768m, web 384m, postgres 1g, redis 256m, cloudflared 128m
    - postgres: `127.0.0.1:5432:5432`, `env_file: ../env/postgres.env`, 볼륨 `pgdata`, init 디렉토리 마운트, `pg_isready` 헬스체크
    - redis: `--appendonly yes --appendfsync everysec`, 볼륨 `redisdata`
    - cloudflared: `tunnel --no-autoupdate --config /etc/cloudflared/config.yml run ${TUNNEL_ID}`. `config.yml`과 `credentials.json`을 읽기 전용으로 마운트한다.
  - `compose.dev.yml`: postgres(`127.0.0.1:5433`), redis(`127.0.0.1:6380`)만. 같은 init 스크립트를 쓰고, `env_file: ../env/postgres.env`.
  - `01-init.sh`(`set -euo pipefail`, `psql -v ON_ERROR_STOP=1`):
    - 역할 `core_app`, `catalog_app`, `events_app`을 LOGIN으로 만든다. 비밀번호는 `CORE_APP_PASSWORD`, `CATALOG_APP_PASSWORD`, `EVENTS_APP_PASSWORD`에서 읽는다.
    - 스키마 `core`(소유 core_app), `catalog`(소유 catalog_app), `events`(소유 events_app)를 만든다.
    - `REVOKE CREATE ON SCHEMA public FROM PUBLIC`
    - `GRANT USAGE ON SCHEMA catalog TO core_app`
    - `ALTER DEFAULT PRIVILEGES FOR ROLE catalog_app IN SCHEMA catalog GRANT SELECT ON TABLES TO core_app`
  - `postgres.env.example`: `POSTGRES_DB=pipibooks`, `POSTGRES_PASSWORD=`, `CORE_APP_PASSWORD=`, `CATALOG_APP_PASSWORD=`, `EVENTS_APP_PASSWORD=`
  - `.env.example`: `PIPIBOOKS_VERSION=0.0.1`, `TUNNEL_ID=`
  - `config.yml`: `credentials-file: /etc/cloudflared/credentials.json`. ingress는 스펙 §7.3 순서 그대로 쓴다.
    - `hostname: pipibooks.com` → `http://web:3000`
    - `hostname: api.pipibooks.com`, `path: ^/v1/events` → `http://event-collector:8081`
    - `hostname: api.pipibooks.com` → `http://core-api:8080`
    - 마지막: `service: http_status:404`
  - `build-local.sh`: `(cd services && ./gradlew jibDockerBuild -Pversion=local)`, 그리고 `docker build -f clients/web/Dockerfile -t ghcr.io/okabedev/pipibooks-web:local .`
- [ ] **Step 4: 통과 확인** — Run: `bash infra/scripts/smoke-test.sh` → Expected: 헬스 4건, 권한 5건 모두 OK, exit 0. Run: `docker compose -f infra/compose/compose.prod.yml config -q` → exit 0. Run: `docker run --rm -v $PWD/infra/cloudflared:/c cloudflare/cloudflared:2026.10.7 tunnel --config /c/config.yml ingress validate` → `OK`
- [ ] **Step 5: 문서** — `infra/CLAUDE.md`(목표 60줄 이하): compose 구성, 네트워크, 포트, env 파일 규칙, 스모크 테스트와 빌드 명령, 시크릿 금지 규칙
- [ ] **Step 6: 커밋·PR** — `build(infra): compose 골격과 DB 권한 초기화·스모크 테스트 추가`

### Task 8: CI와 릴리스 파이프라인

**Files:**
- Create: `.github/workflows/ci.yml`, `.github/workflows/release.yml`
- Modify: `scripts/github/setup-repo.sh`(룰셋에 필수 상태 검사 `ci-ok` 추가), `docs/conventions/git.md`

**Interfaces:**
- Consumes: `pnpm test:scripts`, `scripts/check-docs.sh`(Task 2), `./gradlew build`, `jib`(Task 5), web 스크립트·Dockerfile(Task 6), `smoke-test.sh`(Task 7)
- Produces: 필수 상태 검사 이름 `ci-ok`. 릴리스 트리거는 태그 `v*.*.*` push.

- [ ] **Step 1: `ci.yml`** (트리거: `pull_request`, `push` to `develop`/`main`)
  - `changes` 잡: `dorny/paths-filter@v4.0.3`로 `services`, `clients`, `infra`, `docs`(`**/*.md`, `scripts/**`, `.claude/**`) 필터
  - `pr-title` 잡(PR에서만): `amannn/action-semantic-pull-request@v6.1.1`. types와 scopes는 Global Constraints 값이고 `requireScope: true`. squash 커밋 메시지가 PR 제목이 되므로 이 검사가 필요하다.
  - `scripts` 잡(항상): pnpm 설치 후 `pnpm test:scripts && pnpm check:docs`
  - `services` 잡: `actions/setup-java@v6.2.0`(temurin 25), `gradle/actions/setup-gradle@v6.4.1`, `./gradlew build`
  - `web` 잡: `pnpm/action-setup@v6.1.1`, `actions/setup-node@v7.1.0`(`.nvmrc`), `pnpm install --frozen-lockfile`, lint, test, playwright chromium 설치 후 test:e2e
  - `infra` 잡: `build-local.sh`와 `smoke-test.sh`(services·clients·infra 중 하나라도 바뀌면 실행). 이 잡을 위해 `smoke-test.sh`는 env 파일이 없을 때 임시 비밀번호를 생성하도록 한다.
  - `ci-ok` 잡: `needs`에 위 잡 전부, `if: always()`. 의존 잡 중 `failure`나 `cancelled`가 있으면 exit 1이다. `skipped`는 통과로 본다.
  - `actions/checkout@v7.0.1`을 쓴다. 권한은 `contents: read`.
- [ ] **Step 2: `release.yml`** (트리거: 태그 `v*.*.*`, 권한 `packages: write`)
  - 버전 = 태그에서 `v`를 뗀 값
  - `docker/login-action@v4.7.0`으로 ghcr.io에 `GITHUB_TOKEN`으로 로그인
  - `./gradlew jib -Pversion=$VERSION`. Jib의 `to.auth`는 `GITHUB_ACTOR`와 `GITHUB_TOKEN` 환경변수를 쓴다.
  - `docker/build-push-action@v7.4.0`으로 web을 빌드한다(context `.`, file `clients/web/Dockerfile`, tag `ghcr.io/okabedev/pipibooks-web:$VERSION`).
  - `gh release create v$VERSION --notes-file`: 노트는 `CHANGELOG.md`에서 해당 버전 구간을 추출한다(Task 9).
- [ ] **Step 3: 필수 검사 추가** — `setup-repo.sh`의 룰셋에 `required_status_checks: [{context: "ci-ok"}]`를 추가하고 다시 실행한다.
- [ ] **Step 4: 검증** — 이 Task의 PR을 연다. Expected: `pr-title`, `scripts`, `ci-ok` 성공, 경로가 바뀌지 않은 잡은 skipped. 머지 버튼이 `ci-ok` 성공 전에는 비활성인지 `gh pr view --json mergeStateStatus`로 확인한다.
- [ ] **Step 5: 의도적 실패 확인** — 같은 PR에서 제목을 `update ci`로 바꾸면 `pr-title`이 실패하고 `ci-ok`도 실패해야 한다. 확인한 뒤 제목을 되돌린다.
- [ ] **Step 6: 머지** — `ci(repo): CI와 릴리스 워크플로 추가`

### Task 9: 호스트 준비, 배포 스크립트, v0.0.1 공개

**Files:**
- Create: `infra/scripts/deploy.sh`, `scripts/tests/deploy.test.sh`, `cliff.toml`, `CHANGELOG.md`, `docs/ops/deploy.md`
- Modify: `.claude/skills/release/SKILL.md`, `docs/conventions/git.md`(릴리스 절차), `CLAUDE.md`(색인)

**Interfaces:**
- Consumes: `compose.prod.yml`, 이미지 이름(Task 7), `release.yml`(Task 8)
- Produces: `infra/scripts/deploy.sh <vX.Y.Z>`. 배포에 성공하면 `infra/compose/.deployed-version`(gitignore)에 버전을 기록한다.

- [ ] **Step 1: 실패하는 배포 테스트 작성** (`deploy.test.sh`) — `docker`를 가짜 함수로 바꿔 끼우기 위해 `DOCKER_BIN` 환경변수로 실행 파일을 주입한다.
  - 존재하지 않는 버전 `v9.9.9`: pull이 실패하면 exit ≠ 0, `.deployed-version` 불변, `up`이 호출되지 않아야 한다(Review Focus 5).
  - 헬스체크 실패: 이전 버전으로 `up`을 다시 호출하고, exit ≠ 0, `.deployed-version` 불변
  - 성공: `.deployed-version` = 새 버전
  - 인자가 `0.0.1`처럼 `v`로 시작하지 않으면 사용법을 출력하고 exit 2
- [ ] **Step 2: 실패 확인** — Run: `pnpm test:scripts` → deploy 테스트 FAIL
- [ ] **Step 3: `deploy.sh` 구현**
  - 순서: 인자 검증 → `PIPIBOOKS_VERSION=<버전> compose pull` → (`pg_dump` 백업은 서브프로젝트 1에서 추가. 이 위치에 주석으로 표시) → `compose --profile tunnel up -d` → 헬스체크 → `.deployed-version` 기록
  - 헬스체크: Task 7의 curl 컨테이너 방식으로 4개 서비스를 확인하고, 최대 60초 기다린다.
  - 헬스체크가 실패하면 이전 버전으로 `up -d`를 다시 실행한다.
- [ ] **Step 4: 통과 확인** — Run: `pnpm test:scripts` → 모든 assert PASS
- [ ] **Step 5: 호스트 준비** (사용자가 직접 실행, `sudo` 필요) — `docs/ops/deploy.md`에 체크리스트로 쓰고, 그 순서대로 진행한다.
  1. Docker Desktop의 이 배포판 WSL 통합을 끄고, WSL 안에 Docker Engine(docker-ce, compose 플러그인)을 설치한다. `sudo systemctl enable --now docker`, `sudo usermod -aG docker okabedev`. (현재는 Docker Desktop이고 `systemd=true`는 이미 설정되어 있음)
  2. `%UserProfile%\.wslconfig`에 `[wsl2]` 섹션과 `vmIdleTimeout=-1`
  3. Windows 작업 스케줄러: 시스템 시작 시 `wsl.exe -d <배포판> --exec /bin/true`. 사용자 로그온 여부와 상관없이 실행되도록 설정한다.
  4. 전원 옵션에서 절전을 끄고, 활성 시간(업데이트 재부팅 회피)을 설정한다.
  5. Cloudflare: 도메인 `pipibooks.com` 등록 → `cloudflared tunnel login` → `cloudflared tunnel create pipibooks` → 자격 증명 JSON을 `infra/cloudflared/credentials.json`으로 복사 → `cloudflared tunnel route dns pipibooks pipibooks.com`, `... api.pipibooks.com`
  6. Cloudflare 대시보드: `www`를 루트로 리다이렉트하고, Access 애플리케이션 `pipibooks.com/admin*`을 만든다(운영자 이메일만 허용).
  7. `infra/env/postgres.env`와 `infra/compose/.env`를 만든다. 비밀번호는 `openssl rand -base64 32`로 생성한다.
  - 검증: `wsl --shutdown`으로 재시작한 뒤 로그인 없이 `docker ps`가 동작하는지 확인한다(다음 Step의 배포 후 재확인).
- [ ] **Step 6: 릴리스 도구** — `cliff.toml`: Conventional Commits 파서, 그룹(`feat` 기능, `fix` 수정, `perf` 성능, `refactor` 리팩터링, `docs` 문서, 나머지 기타), 태그 패턴 `v[0-9]*`. `git.md`와 `release/SKILL.md`에 릴리스 절차를 적는다.
  1. `release/x.y.z` 브랜치를 만든다.
  2. `git cliff --tag vX.Y.Z -o CHANGELOG.md`(`docker run --rm -v $PWD:/app -w /app orhunp/git-cliff:2.14.2`)
  3. 커밋 `chore(repo): vX.Y.Z 릴리스 준비`
  4. `main`으로 PR을 열고 merge commit으로 머지한다.
  5. `main`에 태그 `vX.Y.Z`를 찍고 push한다.
  6. `main` → `develop` 백머지 PR을 열고 merge commit으로 머지한다.
  7. 호스트에서 `deploy.sh`를 실행한다.
  - 이 Step은 feature 브랜치에서 커밋한다: `feat(infra): 배포 스크립트와 릴리스 절차 추가`
- [ ] **Step 7: v0.0.1 릴리스** — Step 6의 절차를 그대로 실행한다. 태그를 push한 뒤 `release.yml`이 성공하는지 확인한다(`gh run watch`). 첫 푸시 후 GHCR 패키지 4개의 공개 여부를 Public으로 바꾼다(GitHub 웹 → Packages → Settings. 사용자가 직접 수행).
- [ ] **Step 8: 배포·공개 확인**
  - Run: `bash infra/scripts/deploy.sh v0.0.1` → Expected: 헬스체크 OK, `.deployed-version` = `v0.0.1`
  - Run: `curl -fsS https://pipibooks.com | grep -o '<h1[^>]*>pipibooks</h1>'` → 일치
  - Run: `curl -fsS https://api.pipibooks.com/actuator/health` → `{"status":"UP"...}`
  - Run: `curl -s -o /dev/null -w '%{http_code}' https://pipibooks.com/admin` → Access 로그인으로 리다이렉트(302) 또는 403
  - 재부팅 검증: Windows를 재부팅하고 로그인 없이 5분을 기다린 뒤, 다른 기기에서 `https://pipibooks.com`이 열리는지 확인한다.

---

## 스펙 범위 중 이 계획에서 미루는 것 (의도적)

| 항목 | 미루는 곳 | 이유 |
|---|---|---|
| `libs/common`, `libs/event-schema`, `clients/packages/{api-client,shared}` | 서브프로젝트 1 | 공유할 코드가 아직 없다(YAGNI) |
| 스킬 `add-api-endpoint`, `add-migration` | 서브프로젝트 1 | 기준이 될 표준 예시 코드(위시리스트)가 먼저 필요하다 |
| 백업 컨테이너, `docs/ops/backup.md`, 외부 가동 모니터링 | 서브프로젝트 1 | 보존할 데이터가 생기는 시점 |
| WAF 요청 빈도 제한 | 서브프로젝트 1 | 대상 엔드포인트(`/v1/events`, `/v1/auth/*`)가 아직 없다 |
| Flyway, JPA, Testcontainers | 서브프로젝트 1 | 첫 테이블과 함께 도입 |

## 이슈 번호 (Task 1에서 기록)

| Task | 이슈 | 브랜치 |
|---|---|---|
| 1 | #1 | `feature/1-repo-setup` |
| 2 | | `feature/<N>-root-toolchain` |
| 3 | | `feature/<N>-docs-skeleton` |
| 4 | | `feature/<N>-claude-guards` |
| 5 | | `feature/<N>-services-skeleton` |
| 6 | | `feature/<N>-web-skeleton` |
| 7 | | `feature/<N>-compose-skeleton` |
| 8 | | `feature/<N>-ci-pipeline` |
| 9 | | `feature/<N>-deploy-release` |

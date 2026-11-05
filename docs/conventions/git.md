# Git 규칙

근거: 스펙 §10, ADR 0010

## 브랜치 (Git Flow)
| 브랜치 | 분기 원천 | 머지 대상 | 머지 방식 |
|---|---|---|---|
| `main` | - | - | 운영 기준, 버전 태그만 |
| `develop` | `main` | - | 통합 브랜치(기본 브랜치) |
| `feature/<이슈번호>-<slug>` | `develop` | `develop` | squash |
| `release/x.y.z` | `develop` | `main`, 이후 `develop` | merge commit, 태그 `vX.Y.Z` |
| `hotfix/x.y.z` | `main` | `main`, 이후 `develop` | merge commit, 태그 |

- slug는 소문자·숫자·하이픈. `scripts/check-branch-name.sh`(pre-push)가 검사한다.
- `main`·`develop`은 룰셋 `protect-main-develop`으로 보호한다: 삭제·강제 푸시 금지, PR 필수.

## 작업 흐름
1. 이슈를 확인하거나 만든다(마일스톤 지정).
2. `git switch develop && git pull` 후 `git switch -c feature/<번호>-<slug>`
3. 커밋은 작은 단위로, 훅을 통과시킨다.
4. `gh pr create --base develop` — 제목은 커밋 규칙, 본문은 PR 템플릿과 `Closes #<번호>`
5. CI(`ci-ok`) 통과 후 `gh pr merge --squash`

## CI (`.github/workflows/ci.yml`)
- 잡: `pr-title`(PR 제목 규칙), `scripts`(스크립트 테스트·문서 검사, 항상), `services`·`web`·`infra`(경로가 바뀐 경우만), `ci-ok`(집계)
- 룰셋의 필수 상태 검사는 `ci-ok` 하나다. 경로 필터로 건너뛴 잡은 통과로 본다.
- 새 잡을 추가하면 `ci-ok`의 `needs`에도 추가한다.

## 커밋 메시지 (Conventional Commits)
```
<type>(<scope>): <한국어 제목>

<본문: 무엇을, 왜>

Refs: #<이슈번호>
```
- type: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`
- scope(필수): `core-api` `catalog-worker` `event-collector` `libs` `web` `app` `shared` `infra` `docs` `repo`
- 제목 50자 이내, 마침표 없음, "~ 추가"·"~ 수정" 같은 명사형
- 호환성을 깨는 변경은 `BREAKING CHANGE:` 푸터
- squash 머지 시 PR 제목이 커밋 제목이 된다. PR 제목도 같은 규칙(CI가 검사).
- commitlint(`commitlint.config.mjs`)가 commit-msg 훅에서 검사한다.

## 버전
- 서버·웹: SemVer 단일 버전 `vX.Y.Z`. 앱: `app-vX.Y.Z`.
- `CHANGELOG.md`는 git-cliff로 생성한다.

## 릴리스 절차 (스킬 `release`)
버전 `X.Y.Z`는 SemVer로 정한다(1.0 전에는 기능 추가 시 Y, 수정만 있으면 Z).
1. `git switch develop && git pull --ff-only && git switch -c release/X.Y.Z`
2. CHANGELOG 생성:
   `docker run --rm -u "$(id -u):$(id -g)" -v "$PWD":/app -w /app orhunp/git-cliff:2.14.2 --tag vX.Y.Z -o CHANGELOG.md`
3. 커밋 `chore(repo): vX.Y.Z 릴리스 준비` → `git push -u origin release/X.Y.Z`
4. `gh pr create --base main --title "chore(repo): vX.Y.Z 릴리스"` → CI 통과 후 `gh pr merge --merge`
5. 태그: `git switch main && git pull --ff-only && git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z`
6. 릴리스 워크플로(`release.yml`)가 GHCR 이미지 4개와 GitHub 릴리스를 만든다: `gh run watch`로 확인
7. 백머지: `gh pr create --base develop --head main --title "chore(repo): vX.Y.Z 백머지"` → `gh pr merge --merge`
8. 배포(호스트): `bash infra/scripts/deploy.sh vX.Y.Z` (상세: `docs/ops/deploy.md`)

핫픽스는 `hotfix/X.Y.Z`를 `main`에서 만들고 4~8단계를 같은 방식으로 진행한다.

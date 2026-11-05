---
name: release
description: pipibooks 서버·웹 버전을 Git Flow로 릴리스할 때 사용(release 브랜치, CHANGELOG, main 머지, 태그, develop 백머지, 배포).
---

# 릴리스 절차

`docs/conventions/git.md`의 "릴리스 절차" 1~8단계를 순서대로 실행한다. 단계를 건너뛰거나 순서를 바꾸지 않는다.

확인 사항:
- 시작 전 `develop`의 최신 CI(`ci-ok`)가 통과했는지 `gh run list --branch develop --limit 1`로 확인한다.
- 버전은 마지막 태그(`git describe --tags --abbrev=0`)와 이후 커밋 type으로 정한다.
- `main` 머지와 백머지는 squash가 아닌 merge commit(`gh pr merge --merge`)이다.
- 태그를 push한 뒤 `release.yml` 실행이 성공했는지 확인하고, 실패하면 배포하지 않는다.
- 배포(8단계)는 운영 호스트에서 사용자가 승인한 뒤에만 실행한다. 실패 시 `deploy.sh`가 이전 버전으로 되돌린다.

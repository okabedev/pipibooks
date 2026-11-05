# 0010. Git Flow와 Public 저장소

- 상태: 승인 · 2026-11-05

## 결정
- GitHub `okabedev/pipibooks`를 Public으로 둔다. 기본 브랜치는 `develop`이다.
- Git Flow를 따른다: `feature/<번호>-<slug>`는 squash로 `develop`에 머지한다. `release/*`와 `hotfix/*`는 `--no-ff`로 `main`에 머지하고 태그를 찍은 뒤 `develop`에 다시 머지한다.
- Conventional Commits를 따른다. type과 scope는 영어, 제목은 한국어로 쓴다. commitlint로 강제한다.
- 서버와 웹은 SemVer 단일 버전(`vX.Y.Z`)을 쓰고, 앱은 `app-vX.Y.Z`를 쓴다.
- 커밋 작성자 이메일은 GitHub noreply 주소를 쓴다.

## 이유
GitHub Free의 비공개 저장소에서는 브랜치 보호를 쓸 수 없다. Public이면 CI 사용 시간도 무제한이다. 시크릿은 처음부터 저장소 밖에 두도록 설계했다.

## 결과
저장소의 모든 내용이 공개된다. 시크릿 스캐닝을 켜고, `.env`는 gitignore로 차단한다.

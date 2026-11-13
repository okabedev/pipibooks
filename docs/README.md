# 피피북스 문서 목차

문서 작성 규칙은 [문서 규칙](conventions/documentation.md)에 있다. 새 문서를 만들면 이 목차에도 추가한다.

## 제품

- [서비스 개요](product/overview.md): 의도, 목표, 비목표, 반목표
- [도서 정보](product/books.md): 신간, TOP 100, 주간·월간 인기, 카테고리
- [도서 평가](product/reviews.md): 평점, 코멘트, 독서 기간
- [나의 책장](product/bookcase.md): 위시, 평가한 책, 통계
- [회원](product/members.md): 카카오 로그인, 닉네임, 탈퇴

## 구조

- [레포 구조](architecture/repository-layout.md): 최상위 폴더 배치
- [백엔드 모듈](architecture/backend-modules.md): 모듈과 의존 방향, `core` 패키지
- [도서 데이터](architecture/book-data.md): 식별, 저장 시점, 검색, 필드 병합
- [워커](architecture/worker.md): 실행 방식과 작업 목록
- [인증](architecture/authentication.md): 카카오 로그인과 JWT
- [인프라](architecture/infrastructure.md): 로컬과 운영 Compose, 비밀 값, 백업
- [기술 스택](architecture/tech-stack.md)

## 규칙

- [이름 짓기와 언어](conventions/naming.md)
- [문서 규칙](conventions/documentation.md)
- [Git 규칙](conventions/git.md): 브랜치, 태그, 릴리스, 커밋
- [결정 방식](conventions/decision-making.md): 승인이 필요한 결정과 구현자에게 맡기는 것

## 결정

- [결정 기록](decisions/README.md): 기록 방법과 결정 목록
- [축약어 목록](decisions/abbreviations.md)

## 로드맵

- [로드맵](roadmap/README.md): 마일스톤 표, 세션 흐름, 세션 인계

## 설계서와 계획

- [원본 설계서 (2026-11-14)](superpowers/specs/2026-11-14-pipibooks-design.md): 승인 당시 기록, 고치지 않음
- [구현 계획](superpowers/plans/): 마일스톤별 계획

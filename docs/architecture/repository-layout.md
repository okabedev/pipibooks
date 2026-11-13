# 레포 구조

하나의 레포에 백엔드, 웹, 모바일, 인프라, 문서를 함께 둔다.

```
pipibooks/
  clients/
    mobile/     ← React Native (Expo), 2차 목표
    web/        ← React
  services/     ← Gradle 멀티 모듈 루트
  infra/        ← Docker Compose, nginx, cloudflared, 백업 스크립트
  docs/
  .claude/skills/
  CLAUDE.md
```

## 이름이 비슷한 폴더

`services/app`과 `services/client`는 루트의 `clients/`와 이름이 비슷하다. 그래서 문서에서는 항상 경로로 부른다. 예를 들어 "client 모듈"이 아니라 `services/client`라고 쓴다.

## 관련 문서

- `services/` 안의 모듈: [백엔드 모듈](backend-modules.md)
- `infra/`: [인프라](infrastructure.md)
- `docs/`: [문서 규칙](../conventions/documentation.md)
- `.claude/skills/`: [로드맵](../roadmap/README.md)의 세션 흐름

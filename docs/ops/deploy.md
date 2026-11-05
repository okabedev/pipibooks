# 배포·호스트 운영

근거: 스펙 §7, ADR 0007

## 1. 호스트 준비 (최초 1회, 사용자가 직접 수행)
운영 호스트는 이 Windows PC(WSL2 + Docker Desktop)다.

**Docker Desktop**
- [ ] Settings → General: "Start Docker Desktop when you sign in" 켜기
- [ ] Settings → Software updates: 자동 확인·다운로드 끄기(업데이트는 계획된 시각에 수동으로)
- [ ] Settings → Resources: Resource Saver 끄기

**Windows**
- [ ] 자동 로그인: Sysinternals Autologon으로 설정(비밀번호는 LSA에 암호화 저장)
- [ ] 자동 로그인 직후 화면 잠금: 작업 스케줄러 → 작업 만들기 → 트리거 "로그온할 때" → 동작 `rundll32.exe user32.dll,LockWorkStation`
- [ ] 전원: 절전 "안 함", 활성 시간(업데이트 재부팅 회피) 설정, 업데이트 재부팅은 새벽

**Cloudflare**
- [ ] 도메인 `pipibooks.com` 등록(Cloudflare Registrar 권장)
- [ ] `cloudflared` 설치 후 로그인: `docker run --rm -it -v ~/.cloudflared:/home/nonroot/.cloudflared cloudflare/cloudflared:2026.10.7 tunnel login`
- [ ] 터널 생성: `... tunnel create pipibooks` → 출력된 터널 ID와 `~/.cloudflared/<ID>.json` 확인
- [ ] 인증 정보 복사: `cp ~/.cloudflared/<ID>.json infra/cloudflared/credentials.json` (gitignore 대상)
- [ ] DNS 연결: `... tunnel route dns pipibooks pipibooks.com`, `... tunnel route dns pipibooks api.pipibooks.com`
- [ ] 대시보드: `www.pipibooks.com` → `https://pipibooks.com` 리다이렉트 규칙
- [ ] 대시보드 Zero Trust → Access: 애플리케이션 `pipibooks.com/admin*`, 정책은 운영자 이메일만 허용

**GHCR**
- [ ] 이미지가 로그인 없이 pull 되는지 확인: `docker logout ghcr.io; docker pull ghcr.io/okabedev/pipibooks-web:<버전>`. 패키지는 Public 저장소의 공개 범위를 이어받는다(v0.0.1에서 확인). 실패하면 GitHub → Packages에서 Public으로 바꾼다.

**운영 체크아웃** (개발 작업 트리와 분리)
- [ ] `git -C ~/projects/pipibooks worktree add ~/pipibooks-prod v0.0.1` — 운영은 항상 이 디렉토리에서 배포 태그를 체크아웃해 실행한다. compose 파일·터널 설정·init 스크립트가 이미지와 같은 리비전이어야 하기 때문이다.
- 이후 시크릿 파일과 `credentials.json`은 `~/pipibooks-prod` 아래에 둔다.

**시크릿 파일** (`~/pipibooks-prod`에서)
- [ ] `cp infra/env/postgres.env.example infra/env/postgres.env` 후 값을 `openssl rand -base64 32`로 채움
- [ ] `cp infra/compose/.env.example infra/compose/.env` 후 `TUNNEL_ID` 입력(`PIPIBOOKS_VERSION`은 비워 둔다)

## 2. 배포
```bash
cd ~/pipibooks-prod
git fetch --tags && git checkout vX.Y.Z
bash infra/scripts/deploy.sh vX.Y.Z
```
1. 체크아웃이 `vX.Y.Z` 태그인지 확인 — 아니면 아무것도 바꾸지 않고 종료
2. 이미지 pull — 실패하면 아무것도 바꾸지 않고 종료
3. (서브프로젝트 1부터) DB 백업
4. `compose --profile tunnel up -d` 후 헬스체크(core-api, catalog-worker, event-collector, web)
5. 성공하면 `infra/compose/.env`의 `PIPIBOOKS_VERSION`을 갱신한다. 이 값이 배포된 버전의 단일 기준이다.
6. up 또는 헬스체크가 실패하면 기록된 이전 버전으로 다시 기동하고 헬스체크한다. 결과는 `롤백 완료` / `ROLLBACK FAILED` / `롤백할 이전 버전이 없습니다` 중 하나로 출력하고 exit 1

수동 롤백: `git checkout v<이전 버전> && bash infra/scripts/deploy.sh v<이전 버전>` (자동 롤백은 새 리비전의 compose 파일로 이전 이미지를 띄우므로, 구성 변경이 있었다면 수동 롤백으로 리비전까지 맞춘다)

## 3. 배포 확인
- `curl -fsS https://pipibooks.com | grep -o '<h1[^>]*>pipibooks</h1>'`
- `curl -fsS https://api.pipibooks.com/actuator/health`
- `curl -s -o /dev/null -w '%{http_code}' https://pipibooks.com/admin` → 302 또는 403(Access)
- 재부팅 검증: Windows 재부팅 후 손대지 않고 5분 → 화면 잠김 상태에서 다른 기기로 `https://pipibooks.com` 접속

## 4. 운영 명령
| 명령 | 용도 |
|---|---|
| `docker compose -p pipibooks -f infra/compose/compose.prod.yml --profile tunnel ps` | 상태 |
| `docker compose -p pipibooks -f infra/compose/compose.prod.yml logs -f --tail 100 <서비스>` | 로그 |
| `grep PIPIBOOKS_VERSION infra/compose/.env` | 배포된 버전 |

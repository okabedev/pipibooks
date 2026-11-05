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
- [ ] 첫 릴리스 후 GitHub → Packages에서 `pipibooks-web`, `pipibooks-core-api`, `pipibooks-catalog-worker`, `pipibooks-event-collector`를 Public으로 변경(호스트가 로그인 없이 pull)

**시크릿 파일**
- [ ] `cp infra/env/postgres.env.example infra/env/postgres.env` 후 값을 `openssl rand -base64 32`로 채움
- [ ] `cp infra/compose/.env.example infra/compose/.env` 후 `TUNNEL_ID` 입력

## 2. 배포
```bash
bash infra/scripts/deploy.sh vX.Y.Z
```
1. 이미지 pull — 실패하면 아무것도 바꾸지 않고 종료
2. (서브프로젝트 1부터) DB 백업
3. `compose --profile tunnel up -d`
4. 헬스체크(core-api, catalog-worker, event-collector, web) — 성공하면 `infra/compose/.deployed-version`에 기록
5. 헬스체크 실패 시 `.deployed-version`의 이전 버전으로 다시 기동하고 exit 1

수동 롤백: `bash infra/scripts/deploy.sh <이전 버전>`

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
| `cat infra/compose/.deployed-version` | 배포된 버전 |

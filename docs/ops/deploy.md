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
- [ ] DNS 연결: `... tunnel route dns pipibooks pipibooks.com`, `... tunnel route dns pipibooks api.pipibooks.com`
- [ ] `www` 리다이렉트 (터널과 무관)
  1. DNS → Records → Add record: Type `CNAME`, Name `www`, Target `pipibooks.com`, **Proxied**. 레코드가 없으면 요청이 Cloudflare에 오지 않아 규칙이 실행되지 않는다.
  2. Rules → Redirect Rules(또는 템플릿 "Redirect from WWW to root"): 조건 `Hostname equals www.pipibooks.com`, Type **Dynamic**, Expression `concat("https://pipibooks.com", http.request.uri.path)`, 상태 `301`, Preserve query string 체크
  3. 확인: `curl -sI "https://www.pipibooks.com/books/1?x=1"` → `301`, `location: https://pipibooks.com/books/1?x=1`
- [ ] Access로 `/admin` 보호 (DNS 연결 후)
  1. Zero Trust(one.dash.cloudflare.com) 최초 진입 시 팀 이름 지정, Free 플랜 선택
  2. 로그인 방식은 기본값 **Cloudflare identity provider**(Cloudflare 계정 로그인)를 쓴다. 설정 위치: Zero Trust → Integrations → Identity providers
  3. Access → Applications → Add an application → **Self-hosted**: 이름 `pipibooks admin`, 세션 24시간, Public hostname `pipibooks.com`, Path `admin*`
  4. 정책: Action **Allow**, Include → **Emails** = Cloudflare 계정 로그인 이메일
  5. 확인: `curl -s -o /dev/null -w '%{http_code} %{redirect_url}' https://pipibooks.com/admin` → `302 https://<팀>.cloudflareaccess.com/...`. 브라우저로 로그인해 통과하는지도 확인

**GHCR**
- [ ] 이미지가 로그인 없이 pull 되는지 확인: `docker logout ghcr.io; docker pull ghcr.io/okabedev/pipibooks-web:<버전>`. 패키지는 Public 저장소의 공개 범위를 이어받는다(v0.0.1에서 확인). 실패하면 GitHub → Packages에서 Public으로 바꾼다.

**운영 체크아웃** (개발 작업 트리와 분리)
- [ ] `git -C ~/projects/pipibooks worktree add ~/pipibooks-prod v0.0.1` — 운영은 항상 이 디렉토리에서 배포 태그를 체크아웃해 실행한다. compose 파일·터널 설정·init 스크립트가 이미지와 같은 리비전이어야 하기 때문이다.
- 이후 시크릿 파일은 모두 `~/pipibooks-prod` 아래에 둔다(git 추적 제외).

**시크릿 파일** (`~/pipibooks-prod`에서)
- [ ] `cp infra/env/postgres.env.example infra/env/postgres.env` 후 값을 `openssl rand -base64 32`로 채움
- [ ] `cp infra/compose/.env.example infra/compose/.env` 후 `TUNNEL_ID` 입력(`PIPIBOOKS_VERSION`은 비워 둔다)
- [ ] 터널 인증 정보: `printf 'TUNNEL_CRED_CONTENTS=%s\n' "$(tr -d '\n' < ~/.cloudflared/<ID>.json)" > infra/env/cloudflared.env && chmod 600 infra/env/cloudflared.env`
  - 파일 바인드 마운트 대신 환경 변수로 넘긴다. Docker Desktop은 재부팅 직후 WSL 파일시스템이 준비되기 전에 컨테이너를 띄우므로, 바인드 마운트가 있으면 기동에 실패하고 재시도하지 않는다(이슈 #28).

## 2. 배포
```bash
cd ~/pipibooks-prod
git fetch --tags && git checkout vX.Y.Z
bash infra/scripts/deploy.sh vX.Y.Z
```
1. 체크아웃이 `vX.Y.Z` 태그인지 확인 — 아니면 아무것도 바꾸지 않고 종료
2. 이미지 pull — 실패하면 아무것도 바꾸지 않고 종료
3. (서브프로젝트 1부터) DB 백업
4. `compose --profile tunnel up -d` 후 헬스체크(core, catalog-worker, event-collector, web)
5. 성공하면 `infra/compose/.env`의 `PIPIBOOKS_VERSION`을 갱신한다. 이 값이 배포된 버전의 단일 기준이다.
6. up 또는 헬스체크가 실패하면 기록된 이전 버전으로 다시 기동하고 헬스체크한다. 결과는 `롤백 완료` / `ROLLBACK FAILED` / `롤백할 이전 버전이 없습니다` 중 하나로 출력하고 exit 1

수동 롤백: `git checkout v<이전 버전> && bash infra/scripts/deploy.sh v<이전 버전>` (자동 롤백은 새 리비전의 compose 파일로 이전 이미지를 띄우므로, 구성 변경이 있었다면 수동 롤백으로 리비전까지 맞춘다)

## 3. 배포 확인
- `curl -fsS https://pipibooks.com | grep -o '<h1[^>]*>pipibooks</h1>'`
- `curl -fsS https://api.pipibooks.com/actuator/health`
- `curl -s -o /dev/null -w '%{http_code}' https://pipibooks.com/admin` → 302 또는 403(Access)
- `curl -sI https://www.pipibooks.com` → `301`
- 재부팅 검증: Windows 재부팅 후 손대지 않고 5분 → 화면 잠김 상태에서 다른 기기로 `https://pipibooks.com` 접속. `docker ps -a`에 `Exited` 컨테이너가 없어야 한다.

## 4. 운영 명령
| 명령 | 용도 |
|---|---|
| `docker compose -p pipibooks -f infra/compose/compose.prod.yml --profile tunnel ps` | 상태 |
| `docker compose -p pipibooks -f infra/compose/compose.prod.yml logs -f --tail 100 <서비스>` | 로그 |
| `grep PIPIBOOKS_VERSION infra/compose/.env` | 배포된 버전 |
| `docker start <컨테이너>` | 재부팅 후 `Exited`로 남은 컨테이너 수동 복구 |

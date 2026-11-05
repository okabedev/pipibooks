# 0007. 로컬 Docker와 Cloudflare Tunnel 배포

- 상태: 승인 · 2026-11-05

## 맥락
현재 Windows PC(WSL2)에서 Docker로 운영하고, 포트를 열지 않고 외부에 공개하려 한다.

## 결정
- 이미 설치된 Docker Desktop(WSL2 백엔드)을 쓴다. Windows 자동 로그인과 Docker Desktop 자동 시작으로 재부팅 후 복구하고, compose `restart: unless-stopped`로 컨테이너를 되살린다.
- 자동 로그인의 물리 접근 위험은 로그인 직후 자동 화면 잠금으로 완화한다. Docker Desktop 자동 업데이트와 Resource Saver는 끈다.
- cloudflared 컨테이너가 유일한 진입점이다. 터널 설정 파일은 저장소에서 관리한다. `/admin`은 Cloudflare Access로 보호한다.
- 릴리스 태그를 찍으면 CI가 GHCR에 이미지를 푸시하고, 호스트에서 `deploy.sh`로 이미지를 당겨와 배포한다. 배포 전에 백업하고, 헬스체크가 실패하면 롤백한다.
- 매일 암호화 백업을 Cloudflare R2에 저장한다. 외부 가동 확인 서비스를 쓴다.

## 결과
- 장점: 비용이 0이고 공개 포트가 없다. 다른 머신으로 옮기기 쉽다.
- 단점: PC 재부팅, 절전, 정전에 취약하다. 자동 기동과 외부 모니터링으로 완화한다. 자동 로그인은 보안상 약점이다.

## 검토한 대안
- self-hosted runner로 자동 배포: Public 저장소에서는 보안 위험이 있어 제외했다.
- WSL 안의 Docker Engine(systemd): 로그인 없이 기동되어 더 견고하지만, 기존 Desktop 환경을 바꿔야 한다. 사용자가 Desktop 유지를 선택했고(2026-11-05), 별도 머신으로 이전할 때 다시 검토한다.

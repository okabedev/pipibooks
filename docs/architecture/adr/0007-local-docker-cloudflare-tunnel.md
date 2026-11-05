# 0007. 로컬 Docker와 Cloudflare Tunnel 배포

- 상태: 승인 · 2026-11-05

## 맥락
현재 Windows PC(WSL2)에서 Docker로 운영하고, 포트를 열지 않고 외부에 공개하려 한다.

## 결정
- Docker 엔진은 WSL2 안에서 systemd로 관리한다. 부팅 시 자동 기동하고 compose `restart: unless-stopped`로 복구한다.
- cloudflared 컨테이너가 유일한 진입점이다. 터널 설정 파일은 저장소에서 관리한다. `/admin`은 Cloudflare Access로 보호한다.
- 릴리스 태그를 찍으면 CI가 GHCR에 이미지를 푸시하고, 호스트에서 `deploy.sh`로 이미지를 당겨와 배포한다. 배포 전에 백업하고, 헬스체크가 실패하면 롤백한다.
- 매일 암호화 백업을 Cloudflare R2에 저장한다. 외부 가동 확인 서비스를 쓴다.

## 결과
- 장점: 비용이 0이고 공개 포트가 없다. 다른 머신으로 옮기기 쉽다.
- 단점: PC 재부팅, 절전, 정전에 취약하다. 자동 기동과 외부 모니터링으로 완화한다.

## 검토한 대안
- self-hosted runner로 자동 배포: Public 저장소에서는 보안 위험이 있어 제외했다.
- Docker Desktop: 로그인 전에는 기동되지 않는다.

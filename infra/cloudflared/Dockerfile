# 터널 설정(config.yml)을 이미지에 넣는다. 호스트 바인드 마운트를 쓰지 않아야 재부팅 직후
# (WSL 파일시스템이 준비되기 전) Docker Desktop이 컨테이너를 다시 띄울 때 실패하지 않는다.
# 인증 정보는 이미지에 넣지 않고 런타임 환경 변수 TUNNEL_CRED_CONTENTS로 받는다.
FROM cloudflare/cloudflared:2026.10.7
LABEL org.opencontainers.image.source="https://github.com/okabedev/pipibooks"
COPY config.yml /etc/cloudflared/config.yml

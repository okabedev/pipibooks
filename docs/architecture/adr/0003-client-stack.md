# 0003. 클라이언트 스택

- 상태: 승인 · 2026-11-05

## 맥락
웹을 먼저 출시하고 앱으로 확장한다. 모바일 경험은 거의 없다. 도서 카드 공유 링크의 OG 미리보기와 SEO가 필요하고, 앱에서는 스와이프 UX가 핵심이다.

## 결정
- 웹: Next.js / 앱: Expo(React Native) / 언어: TypeScript
- `clients/`를 pnpm 워크스페이스로 묶는다: `packages/api-client`(OpenAPI 생성), `packages/shared`(슬라이드 스키마 등)
- 카드 슬라이드는 타입별 렌더러로 그린다. 웹과 앱은 데이터 구조를 공유하고 UI는 따로 만든다.

## 결과
- 장점: 웹 SEO와 네이티브 스와이프 품질을 함께 얻는다. React 지식을 재사용한다.
- 단점: UI 컴포넌트를 두 번 구현해야 한다.

## 검토한 대안
- Expo 단일(react-native-web): 웹 SEO와 UX가 약하다.
- Capacitor 래핑: 네이티브 느낌이 부족하고 스토어 리젝 위험이 있다.
- Flutter: 새 언어를 배워야 하고 웹과 코드를 공유할 수 없다.

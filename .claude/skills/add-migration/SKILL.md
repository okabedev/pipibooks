---
name: add-migration
description: pipibooks에서 Flyway DB 마이그레이션을 추가할 때 사용. 파일 위치·이름, 소유 컴포넌트, 컬럼 규칙, 도메인 문서 갱신, 테스트까지 항상 같은 순서로 수행한다.
---

# 마이그레이션 추가 절차

예시: `services/catalog-worker/src/main/resources/db/migration/catalog/V1__book_and_collect_run.sql`

1. **소유 확인**: 스키마를 소유한 컴포넌트에서만 쓴다(`core`→`core`, `catalog-worker`→`catalog`, `event-collector`→`events`). 다른 컴포넌트의 스키마는 건드리지 않는다.
2. **파일 위치·이름**: `src/main/resources/db/migration/<스키마>/V<n>__<snake_case>.sql`. `<n>`은 그 스키마의 마지막 번호 + 1. 적용된 파일은 수정하지 않고 새 버전을 추가한다.
3. **작성 규칙**
   - 테이블은 스키마를 붙여 쓴다(`catalog.book`).
   - PK는 `bigint`(TSID, 앱에서 `Tsid.next()`로 생성), 시각은 `timestamptz`.
   - 스키마를 넘는 외래 키는 만들지 않는다. 다른 스키마의 ID는 값으로만 저장한다.
   - 상태·종류 컬럼은 `CHECK`로 허용 값을 제한한다.
4. **문서 갱신**: 같은 PR에서 `docs/domain/model.md`를 고친다. 설계가 바뀌면 스펙·ADR도 갱신한다.
5. **테스트**: `DbTestSupport`를 상속한 통합 테스트로 새 테이블을 실제로 읽고 쓴다(Testcontainers, 서비스 계정 접속). 마이그레이션만 있고 테스트가 없으면 완료가 아니다.
6. **확인**: `cd services && ./gradlew :<모듈>:test`

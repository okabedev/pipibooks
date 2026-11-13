# 문서 규칙

## 크기와 주제

- `CLAUDE.md`는 200줄 이하로 쓰고, 규칙 요약과 문서 안내만 담는다.
- 나머지 문서는 한 파일에 한 주제만 담고 300줄 정도까지 쓴다.
- 길어지거나 따로 읽힐 주제가 섞이면 하위 문서로 나누고 링크한다.

## `docs/` 구성

| 경로 | 내용 |
|---|---|
| `README.md` | 목차 |
| `product/` | 기능과 정책 |
| `architecture/` | 구조 |
| `conventions/` | 규칙 |
| `decisions/` | 결정 기록과 승인된 축약어 목록 |
| `roadmap/` | 로드맵과 마일스톤 파일 |
| `superpowers/specs`, `superpowers/plans` | 설계서와 구현 계획 |

새 문서를 만들면 [목차](../README.md)에도 추가한다.

## 문서 갱신

- 설계와 결정이 바뀌면 같은 PR 안에서 관련 문서도 함께 고친다.
- 결정은 [결정 기록](../decisions/README.md)에 남긴다.
- `superpowers/specs`의 원본 설계서는 승인 당시의 기록이므로 고치지 않는다([0005](../decisions/0005-design-document-preserved.md)).

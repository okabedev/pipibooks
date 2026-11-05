// Global Constraints(docs/plans/2026-11-05-subproject-0-foundation.md)의 커밋 규칙.
export const types = [
  "feat",
  "fix",
  "docs",
  "style",
  "refactor",
  "perf",
  "test",
  "build",
  "ci",
  "chore",
  "revert",
];
export const scopes = [
  "core",
  "catalog-worker",
  "event-collector",
  "libs",
  "web",
  "app",
  "shared",
  "infra",
  "docs",
  "repo",
];

export default {
  extends: ["@commitlint/config-conventional"],
  rules: {
    "type-enum": [2, "always", types],
    "scope-enum": [2, "always", scopes],
    "scope-empty": [2, "never"],
    // PR 제목 검사(ci.yml pr-title)와 같은 50자. squash 시 붙는 " (#N)"은 GitHub가 만든 커밋이라 검사 대상이 아니다.
    "subject-max-length": [2, "always", 50],
    "subject-full-stop": [2, "never", "."],
    "subject-case": [0],
  },
};

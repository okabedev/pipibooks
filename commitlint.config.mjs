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
  "core-api",
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
    "subject-max-length": [2, "always", 50],
    "subject-full-stop": [2, "never", "."],
    "subject-case": [0],
  },
};

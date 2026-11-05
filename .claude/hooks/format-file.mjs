// PostToolUse(Edit|Write): 수정된 파일을 Prettier로 포맷한다. 실패해도 편집 흐름을 막지 않는다.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { basename } from "node:path";

try {
  const input = JSON.parse(readFileSync(0, "utf8") || "{}");
  const file = input.tool_input?.file_path ?? input.tool_response?.filePath;
  if (
    file &&
    /\.(ts|tsx|js|mjs|cjs|json|css|ya?ml)$/.test(file) &&
    basename(file) !== "pnpm-lock.yaml"
  ) {
    execFileSync(
      "pnpm",
      ["exec", "prettier", "--write", "--ignore-unknown", file],
      {
        cwd: input.cwd ?? process.cwd(),
        stdio: "ignore",
      },
    );
  }
} catch {
  // 포맷 실패는 무시한다.
}
process.exit(0);

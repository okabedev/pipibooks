// PreToolUse(Bash) 가드: 프로젝트 규칙을 우회하는 git 명령을 차단한다.
// 입력: stdin의 Claude 훅 JSON. 차단 시 stderr에 이유를 쓰고 exit 2.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";

const input = JSON.parse(readFileSync(0, "utf8") || "{}");
const command = input.tool_input?.command ?? "";
const cwd = input.cwd ?? process.cwd();

const block = (reason) => {
  process.stderr.write(`차단됨: ${reason} (규칙: CLAUDE.md 금지 사항)\n`);
  process.exit(2);
};

// 명령을 ; && || | 단위로 나눠 각 조각을 따로 검사한다.
const segments = command
  .split(/&&|\|\||;|\|/)
  .map((s) => s.trim())
  .filter(Boolean);

const currentBranch = (dir) => {
  try {
    return execFileSync(
      "git",
      ["-C", dir, "symbolic-ref", "--short", "-q", "HEAD"],
      {
        encoding: "utf8",
        stdio: ["ignore", "pipe", "ignore"],
      },
    ).trim();
  } catch {
    return "";
  }
};

for (const seg of segments) {
  const tokens = seg.split(/\s+/);
  if (tokens.includes("--no-verify"))
    block("--no-verify로 훅을 우회할 수 없습니다");
  if (tokens[0] !== "git") continue;

  // git -C <path> <subcommand> 형태 처리
  let i = 1;
  let dir = cwd;
  while (tokens[i]?.startsWith("-")) {
    if (tokens[i] === "-C") {
      dir = tokens[i + 1];
      i += 2;
    } else i += 1;
  }
  const sub = tokens[i];

  if (sub === "push") {
    const args = tokens.slice(i + 1);
    if (
      args.includes("--force") ||
      args.includes("-f") ||
      args.some((a) => a.startsWith("+"))
    ) {
      block("강제 푸시는 금지입니다(--force-with-lease만 허용)");
    }
  }
  if (sub === "commit") {
    const branch = currentBranch(dir);
    if (branch === "main" || branch === "develop") {
      block(`${branch}에 직접 커밋할 수 없습니다. feature 브랜치를 쓰세요`);
    }
  }
}
process.exit(0);

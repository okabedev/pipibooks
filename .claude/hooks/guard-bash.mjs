// PreToolUse(Bash) 가드: 프로젝트 규칙을 우회하는 git 명령을 차단한다.
// 입력: stdin의 Claude 훅 JSON. 차단 시 stderr에 이유를 쓰고 exit 2.
// 규칙: main·develop 직접 커밋/병합 금지, 훅 우회(--no-verify, -n, LEFTHOOK=0, core.hooksPath) 금지, 강제 푸시 금지.
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { resolve } from "node:path";

// 입력을 해석할 수 없으면 검사도 할 수 없으므로 차단한다(fail-closed).
let input;
try {
  input = JSON.parse(readFileSync(0, "utf8") || "{}");
} catch {
  process.stderr.write(
    "차단됨: 가드 훅이 입력을 해석하지 못했습니다(.claude/hooks/guard-bash.mjs 확인)\n",
  );
  process.exit(2);
}
const command = input.tool_input?.command ?? "";
const cwd = input.cwd ?? process.cwd();

const block = (reason) => {
  process.stderr.write(`차단됨: ${reason} (규칙: CLAUDE.md 금지 사항)\n`);
  process.exit(2);
};

const PROTECTED = new Set(["main", "develop"]);
// 현재 브랜치에 커밋을 만드는 하위 명령
const WRITES_BRANCH = new Set([
  "commit",
  "merge",
  "cherry-pick",
  "revert",
  "am",
]);
const WRAPPERS = new Set(["env", "command", "exec", "nohup", "time", "sudo"]);
const SHELLS = new Set(["bash", "sh", "zsh"]);

// heredoc 본문은 명령이 아니라 데이터이므로 제거한다.
const stripHeredocs = (src) => {
  const lines = src.split("\n");
  const out = [];
  let delim = null;
  for (const line of lines) {
    if (delim !== null) {
      if (line.trim() === delim) delim = null;
      continue;
    }
    out.push(line);
    const m = line.match(/<<-?\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\1/);
    if (m) delim = m[2];
  }
  return out.join("\n");
};

// 따옴표를 인식해 명령을 조각(단어 배열)으로 나눈다. 구분자: ; & | && || 줄바꿈 ( )
const tokenize = (src) => {
  const segments = [];
  let words = [];
  let word = "";
  let inWord = false;
  const endWord = () => {
    if (inWord) words.push(word);
    word = "";
    inWord = false;
  };
  const endSegment = () => {
    endWord();
    if (words.length) segments.push(words);
    words = [];
  };
  for (let i = 0; i < src.length; i++) {
    const c = src[i];
    if (c === "'") {
      const j = src.indexOf("'", i + 1);
      word += src.slice(i + 1, j === -1 ? src.length : j);
      inWord = true;
      i = j === -1 ? src.length : j;
    } else if (c === '"') {
      inWord = true;
      for (i++; i < src.length && src[i] !== '"'; i++) {
        if (src[i] === "\\" && i + 1 < src.length) i++;
        word += src[i];
      }
    } else if (c === "\\" && i + 1 < src.length) {
      word += src[++i];
      inWord = true;
    } else if (";&|\n()".includes(c)) {
      endSegment();
    } else if (/\s/.test(c)) {
      endWord();
    } else {
      word += c;
      inWord = true;
    }
  }
  endSegment();
  return segments;
};

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

const isAssignment = (w) => /^[A-Za-z_][A-Za-z0-9_]*=/.test(w);
const checkAssignment = (w) => {
  if (/^LEFTHOOK(_SKIP|_EXCLUDE)?=/.test(w) && !/^LEFTHOOK=1$/.test(w)) {
    block("LEFTHOOK 환경 변수로 훅을 끌 수 없습니다");
  }
};

// commit의 단축 플래그 묶음에서 -n(--no-verify)을 찾는다. 값을 받는 플래그 뒤는 값이다.
const commitShortFlagsHaveN = (arg) => {
  for (const ch of arg.slice(1)) {
    if (ch === "n") return true;
    if ("mFcCt".includes(ch)) return false;
  }
  return false;
};

const checkGit = (words, dir) => {
  let i = 1;
  while (i < words.length && words[i].startsWith("-")) {
    const opt = words[i];
    if (opt === "-C") {
      dir = resolve(dir, words[i + 1] ?? ".");
      i += 2;
    } else if (opt === "-c") {
      if (/^core\.hookspath=/i.test(words[i + 1] ?? ""))
        block("core.hooksPath로 훅을 바꿀 수 없습니다");
      i += 2;
    } else if (["--git-dir", "--work-tree", "--namespace"].includes(opt)) {
      i += 2;
    } else i += 1;
  }
  const sub = words[i];
  const args = words.slice(i + 1);

  if (args.includes("--no-verify"))
    block("--no-verify로 훅을 우회할 수 없습니다");
  if (sub === "config" && args.some((a) => /^core\.hookspath$/i.test(a))) {
    block("core.hooksPath를 바꿀 수 없습니다");
  }
  if (
    sub === "commit" &&
    args.some((a) => /^-[A-Za-z]+$/.test(a) && commitShortFlagsHaveN(a))
  ) {
    block("commit -n(--no-verify)으로 훅을 우회할 수 없습니다");
  }
  if (sub === "push") {
    const forced =
      args.includes("--force") ||
      args.some((a) => /^-[A-Za-z]*f[A-Za-z]*$/.test(a)) ||
      args.some((a) => a.startsWith("+"));
    if (forced) block("강제 푸시는 금지입니다(--force-with-lease만 허용)");
  }
  if (WRITES_BRANCH.has(sub)) {
    const branch = currentBranch(dir);
    if (PROTECTED.has(branch))
      block(
        `${branch}에서 git ${sub}를 할 수 없습니다. feature 브랜치를 쓰세요`,
      );
  }
};

const analyze = (src, startDir) => {
  let dir = startDir;
  for (let words of tokenize(stripHeredocs(src))) {
    // 앞쪽 환경 변수 대입과 래퍼(env, command 등)를 걷어 낸다.
    for (;;) {
      while (words.length && isAssignment(words[0])) {
        checkAssignment(words[0]);
        words = words.slice(1);
      }
      if (words.length && WRAPPERS.has(words[0])) {
        words = words.slice(1);
        while (words.length && words[0].startsWith("-")) words = words.slice(1);
        continue;
      }
      break;
    }
    if (!words.length) continue;
    const [cmd] = words;
    if (cmd === "cd") {
      const target = words[1];
      dir =
        !target || target === "~"
          ? homedir()
          : resolve(dir, target.replace(/^~(?=\/)/, homedir()));
    } else if (SHELLS.has(cmd)) {
      const c = words.indexOf("-c");
      if (c !== -1 && words[c + 1] !== undefined) analyze(words[c + 1], dir);
    } else if (cmd === "git") {
      checkGit(words, dir);
    }
  }
};

analyze(command, cwd);
process.exit(0);

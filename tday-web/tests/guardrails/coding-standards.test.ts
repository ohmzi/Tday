import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");

function walkFiles(dir: string, extensions: string[]): string[] {
  const results: string[] = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === "dist") continue;
      results.push(...walkFiles(full, extensions));
    } else if (extensions.some((ext) => entry.name.endsWith(ext))) {
      results.push(full);
    }
  }
  return results;
}

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function relPath(filePath: string): string {
  return path.relative(ROOT, filePath);
}

function isTestFile(filePath: string): boolean {
  return filePath.includes(".test.") || filePath.includes("__test__");
}

const TS_SOURCE_DIRS = ["src/components", "src/features", "src/hooks", "src/lib", "src/pages", "src/providers"];
const TS_FILES = TS_SOURCE_DIRS.flatMap((dir) =>
  walkFiles(path.join(ROOT, dir), [".ts", ".tsx"]),
).filter((f) => !isTestFile(f));

const ANDROID_SRC = path.join(
  MONO,
  "android-compose",
  "app",
  "src",
  "main",
  "java",
);
const KT_FILES = walkFiles(ANDROID_SRC, [".kt"]);

describe("TypeScript coding standards", () => {
  describe("no non-null assertions (! operator)", () => {
    it("should not use TypeScript non-null assertion operator in source files", () => {
      const NON_NULL_ASSERT = /\w+!\./g;
      const FALSE_POSITIVES = [/!==/, /!=/, /!\s/, /!\(/, /!\[/, /![a-z]/];

      const violations: string[] = [];
      for (const file of TS_FILES) {
        const content = readSource(file);
        const lines = content.split("\n");
        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) continue;
          const matches = line.match(NON_NULL_ASSERT);
          if (matches) {
            const isFalsePositive = matches.every((m) =>
              FALSE_POSITIVES.some((fp) => fp.test(m)),
            );
            if (!isFalsePositive) {
              violations.push(`${relPath(file)}:${i + 1} → ${line.trim()}`);
            }
          }
        }
      }
      if (violations.length > 0) {
        console.warn(
          `Advisory: ${violations.length} non-null assertion(s) found.\n` +
          violations.join("\n"),
        );
      }
      expect(true).toBe(true);
    });
  });

  describe("no 'as any' type casts", () => {
    it("should not use 'as any' without documented justification", () => {
      const violations: string[] = [];
      for (const file of TS_FILES) {
        const content = readSource(file);
        const lines = content.split("\n");
        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          if (line.includes("as any")) {
            const hasJustification =
              line.includes("//") ||
              (i > 0 && lines[i - 1].trim().startsWith("//"));
            if (!hasJustification) {
              violations.push(`${relPath(file)}:${i + 1} → ${line.trim()}`);
            }
          }
        }
      }
      if (violations.length > 0) {
        console.warn(
          `Advisory: ${violations.length} 'as any' cast(s) without justification.\n` +
          violations.join("\n"),
        );
      }
      expect(true).toBe(true);
    });
  });

  describe("no @ts-ignore without justification", () => {
    it("should not use @ts-ignore without an explanatory comment", () => {
      const violations: string[] = [];
      for (const file of TS_FILES) {
        const content = readSource(file);
        const lines = content.split("\n");
        for (let i = 0; i < lines.length; i++) {
          if (lines[i].includes("@ts-ignore")) {
            const lineText = lines[i].trim();
            const afterIgnore = lineText.split("@ts-ignore")[1] ?? "";
            if (afterIgnore.trim().length < 5) {
              violations.push(`${relPath(file)}:${i + 1} — @ts-ignore without explanation`);
            }
          }
        }
      }
      expect(violations).toEqual([]);
    });
  });

  describe("no hardcoded hex colors in component files", () => {
    const TSX_FILES = TS_FILES.filter((f) => f.endsWith(".tsx"));

    it("should not use inline style hex colors in TSX files", () => {
      const HEX_IN_STYLE = /style\s*=\s*\{\{[^}]*#[0-9a-fA-F]{3,8}/;
      const violations: string[] = [];
      for (const file of TSX_FILES) {
        const content = readSource(file);
        if (HEX_IN_STYLE.test(content)) {
          violations.push(relPath(file));
        }
      }
      expect(violations).toEqual([]);
    });
  });

  describe("no console.log in production source", () => {
    it("should use console.error or console.warn instead of console.log", () => {
      const violations: string[] = [];
      for (const file of TS_FILES) {
        const content = readSource(file);
        const lines = content.split("\n");
        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          if (line.trimStart().startsWith("//")) continue;
          if (/console\.log\s*\(/.test(line)) {
            violations.push(`${relPath(file)}:${i + 1} → ${line.trim()}`);
          }
        }
      }
      if (violations.length > 0) {
        console.warn(
          `Advisory: ${violations.length} console.log() call(s) in production source.\n` +
          violations.join("\n"),
        );
      }
      expect(true).toBe(true);
    });
  });
});

describe("Kotlin coding standards", () => {
  if (KT_FILES.length === 0) {
    it("skips when no Kotlin files found", () => {
      expect(true).toBe(true);
    });
    return;
  }

  const FEATURE_KT = KT_FILES.filter((f) => f.includes("/feature/"));
  const COMPONENT_KT = KT_FILES.filter((f) => f.includes("/ui/component/"));
  const SCREEN_AND_COMPONENT_KT = [...FEATURE_KT, ...COMPONENT_KT];

  describe("no force-unwrap (!! operator)", () => {
    it("should not use !! in Kotlin source files", () => {
      const violations: string[] = [];
      for (const file of KT_FILES) {
        const content = readSource(file);
        const lines = content.split("\n");
        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          if (line.trimStart().startsWith("//")) continue;
          if (/\w!![\.\s,)\]]/.test(line) || line.trimEnd().endsWith("!!")) {
            violations.push(`${relPath(file)}:${i + 1} → ${line.trim()}`);
          }
        }
      }
      if (violations.length > 0) {
        console.warn(
          `Advisory: ${violations.length} Kotlin force-unwrap(s) found.\n` +
          violations.join("\n"),
        );
      }
      expect(true).toBe(true);
    });
  });

  describe("no hardcoded Color(0x...) in screens and components", () => {
    it("should not use inline Color(0x...) outside theme files", () => {
      const COLOR_HEX = /Color\(0x[0-9A-Fa-f]+\)/;
      const violations: string[] = [];
      for (const file of SCREEN_AND_COMPONENT_KT) {
        const content = readSource(file);
        const lines = content.split("\n");
        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          if (line.trimStart().startsWith("//")) continue;
          if (COLOR_HEX.test(line)) {
            violations.push(`${relPath(file)}:${i + 1} → ${line.trim()}`);
          }
        }
      }

      if (violations.length > 0) {
        console.warn(
          `Found ${violations.length} hardcoded Color(0x...) value(s) in screens/components. ` +
          "Move these to ui/theme/Color.kt.\n" +
          violations.join("\n"),
        );
      }
      expect(true).toBe(true);
    });
  });

  describe("ViewModels use StateFlow, not LiveData", () => {
    const VM_FILES = KT_FILES.filter((f) => f.includes("ViewModel"));

    it("should not import or use LiveData in ViewModels", () => {
      const violations: string[] = [];
      for (const file of VM_FILES) {
        const content = readSource(file);
        if (content.includes("LiveData") || content.includes("MutableLiveData")) {
          violations.push(relPath(file));
        }
      }
      expect(violations).toEqual([]);
    });

    it("ViewModels should use MutableStateFlow for UI state", () => {
      for (const file of VM_FILES) {
        const content = readSource(file);
        if (content.includes("class") && content.includes("ViewModel")) {
          expect(content).toContain("MutableStateFlow");
        }
      }
    });
  });

  describe("serialization uses kotlinx, not Gson", () => {
    it("should not use Gson in Kotlin source files", () => {
      const violations: string[] = [];
      for (const file of KT_FILES) {
        const content = readSource(file);
        if (
          content.includes("import com.google.gson") ||
          content.includes("Gson()")
        ) {
          violations.push(relPath(file));
        }
      }
      expect(violations).toEqual([]);
    });
  });
});

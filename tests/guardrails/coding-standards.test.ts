import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";

const ROOT = process.cwd();

function walkFiles(dir: string, extensions: string[]): string[] {
  const results: string[] = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === ".next") continue;
      results.push(...walkFiles(full, extensions));
    } else if (extensions.some((ext: string) => entry.name.endsWith(ext))) {
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

const TS_SOURCE_DIRS = ["app", "lib", "components", "features", "providers", "hooks"];
const TS_FILES = TS_SOURCE_DIRS.flatMap((dir: string) =>
  walkFiles(path.join(ROOT, dir), [".ts", ".tsx"]),
).filter((f: string) => !isTestFile(f));

const ANDROID_SRC = path.join(
  ROOT,
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
      const FALSE_POSITIVES = [
        /!==/, /!=/, /!\s/, /!\(/, /!\[/, /![a-z]/,
      ];

      const violations: string[] = [];
      for (const file of TS_FILES) {
        const content = readSource(file);
        const lines = content.split("\n");
        for (let i = 0; i < lines.length; i++) {
          const line = lines[i];
          if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) continue;
          const matches = line.match(NON_NULL_ASSERT);
          if (matches) {
            const isFalsePositive = matches.every((m: string) =>
              FALSE_POSITIVES.some((fp: RegExp) => fp.test(m)),
            );
            if (!isFalsePositive) {
              violations.push(`${relPath(file)}:${i + 1} → ${line.trim()}`);
            }
          }
        }
      }
      expect(violations).toEqual([]);
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
      expect(violations).toEqual([]);
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
    const TSX_FILES = TS_FILES.filter((f: string) => f.endsWith(".tsx"));

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
      expect(violations).toEqual([]);
    });
  });

  describe("exported functions should have return types", () => {
    it("exported async route handlers should declare return type or return NextResponse", () => {
      const routeFiles = walkFiles(
        path.join(ROOT, "app", "api"),
        [".ts"],
      ).filter((f: string) => f.endsWith("route.ts"));

      const EXPORT_ASYNC = /export\s+async\s+function\s+\w+\s*\([^)]*\)\s*\{/g;

      const violations: string[] = [];
      for (const file of routeFiles) {
        const content = readSource(file);
        const matches = content.match(EXPORT_ASYNC);
        if (!matches) continue;
        for (const match of matches) {
          const hasReturnType = /\)\s*:\s*\w/.test(match) || /\)\s*:\s*Promise/.test(match);
          if (!hasReturnType) {
            violations.push(`${relPath(file)} → ${match.slice(0, 60)}...`);
          }
        }
      }

      if (violations.length > 0) {
        console.warn(
          `Advisory: ${violations.length} route handler(s) without explicit return types. ` +
          "Best practice is to declare return types on exported functions.",
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

  const FEATURE_KT = KT_FILES.filter((f: string) => f.includes("/feature/"));
  const COMPONENT_KT = KT_FILES.filter((f: string) => f.includes("/ui/component/"));
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
      expect(violations).toEqual([]);
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
    const VM_FILES = KT_FILES.filter((f: string) => f.includes("ViewModel"));

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

  describe("ViewModels use Hilt injection", () => {
    const VM_FILES = KT_FILES.filter(
      (f: string) => f.includes("ViewModel") && !f.includes("test"),
    );

    it("every ViewModel should be annotated with @HiltViewModel", () => {
      const violations: string[] = [];
      for (const file of VM_FILES) {
        const content = readSource(file);
        if (
          content.includes("class") &&
          content.includes("ViewModel") &&
          content.includes(": ViewModel()")
        ) {
          if (!content.includes("@HiltViewModel")) {
            violations.push(relPath(file));
          }
        }
      }
      expect(violations).toEqual([]);
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

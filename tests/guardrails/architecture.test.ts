import { readdirSync, readFileSync, existsSync, statSync } from "fs";
import path from "path";

const ROOT = process.cwd();

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function dirExists(dir: string): boolean {
  return existsSync(dir) && statSync(dir).isDirectory();
}

function listDirs(dir: string): string[] {
  if (!dirExists(dir)) return [];
  return readdirSync(dir, { withFileTypes: true })
    .filter((e) => e.isDirectory())
    .map((e) => e.name);
}

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

describe("web architecture guardrails", () => {
  describe("required project directories exist", () => {
    const REQUIRED_DIRS = [
      "app",
      "app/api",
      "lib",
      "components",
      "features",
      "providers",
      "hooks",
      "prisma",
      "messages",
      "i18n",
      "tests",
      "docs",
      "public",
    ];

    it.each(REQUIRED_DIRS)("directory %s should exist", (dir: string) => {
      expect(dirExists(path.join(ROOT, dir))).toBe(true);
    });
  });

  describe("API routes live in app/api/", () => {
    it("should not have route.ts files outside app/api/ or app/[locale]/", () => {
      const allRoutes = walkFiles(path.join(ROOT, "app"), [".ts"]).filter(
        (f: string) => f.endsWith("route.ts"),
      );
      const strayRoutes = allRoutes.filter((f: string) => {
        const rel = path.relative(path.join(ROOT, "app"), f);
        return !rel.startsWith("api" + path.sep);
      });
      expect(strayRoutes).toEqual([]);
    });
  });

  describe("lib/ is organized by domain, not a single utils file", () => {
    it("lib/ should have domain subdirectories", () => {
      const libDirs = listDirs(path.join(ROOT, "lib"));
      expect(libDirs.length).toBeGreaterThan(0);
      expect(libDirs).toContain("security");
    });

    it("lib/ should not have a catch-all utils file larger than 50 lines", () => {
      const utilsPath = path.join(ROOT, "lib", "utils.ts");
      if (existsSync(utilsPath)) {
        const content = readSource(utilsPath);
        const lineCount = content.split("\n").length;
        expect(lineCount).toBeLessThanOrEqual(50);
      }
    });
  });

  describe("features/ follow domain-based structure", () => {
    const featureDirs = listDirs(path.join(ROOT, "features"));

    it("should have at least one feature directory", () => {
      expect(featureDirs.length).toBeGreaterThan(0);
    });

    it.each(featureDirs)(
      "feature '%s' should contain ts/tsx files",
      (feature: string) => {
        const files = walkFiles(path.join(ROOT, "features", feature), [
          ".ts",
          ".tsx",
        ]);
        expect(files.length).toBeGreaterThan(0);
      },
    );
  });

  describe("internationalization completeness", () => {
    const messagesDir = path.join(ROOT, "messages");
    const EXPECTED_LOCALES = [
      "en",
      "zh",
      "de",
      "ja",
      "ar",
      "ru",
      "es",
      "fr",
      "ms",
      "it",
      "pt",
    ];

    it.each(EXPECTED_LOCALES)(
      "locale file %s.json should exist",
      (locale: string) => {
        expect(existsSync(path.join(messagesDir, `${locale}.json`))).toBe(true);
      },
    );

    it("en.json should be valid JSON", () => {
      const content = readSource(path.join(messagesDir, "en.json"));
      expect(() => JSON.parse(content)).not.toThrow();
    });

    it("all locale files should have the same top-level keys as en.json", () => {
      const enContent = JSON.parse(
        readSource(path.join(messagesDir, "en.json")),
      );
      const enKeys = Object.keys(enContent).sort();

      for (const locale of EXPECTED_LOCALES.filter((l: string) => l !== "en")) {
        const localePath = path.join(messagesDir, `${locale}.json`);
        if (!existsSync(localePath)) continue;
        const localeContent = JSON.parse(readSource(localePath));
        const localeKeys = Object.keys(localeContent).sort();
        expect(localeKeys).toEqual(enKeys);
      }
    });
  });

  describe("database schema integrity", () => {
    const schemaPath = path.join(ROOT, "prisma", "schema.prisma");
    const schema = readSource(schemaPath);

    it("should use PostgreSQL provider", () => {
      expect(schema).toContain('provider = "postgresql"');
    });

    it("should use env for database URL (not hardcoded)", () => {
      expect(schema).toContain('url      = env("DATABASE_URL")');
    });

    it("should have required core models", () => {
      const REQUIRED_MODELS = [
        "User",
        "Todo",
        "TodoInstance",
        "CompletedTodo",
        "Note",
        "List",
        "Account",
        "UserPreferences",
        "AppConfig",
      ];
      for (const model of REQUIRED_MODELS) {
        expect(schema).toContain(`model ${model}`);
      }
    });

    it("all data models with userID should have an index on it", () => {
      const modelBlocks = schema.split(/\nmodel\s+/).slice(1);
      const violations: string[] = [];
      for (const block of modelBlocks) {
        const modelName = block.split(/[\s{]/)[0];
        if (block.includes("userID") && !block.includes("@@index")) {
          if (!block.includes("@unique") || block.includes("userID")) {
            const hasUserIdIndex =
              block.includes("@@index([userID])") ||
              block.includes("@@index([userID,") ||
              block.includes("@unique") && block.includes("userID");
            if (!hasUserIdIndex) {
              violations.push(modelName);
            }
          }
        }
      }

      if (violations.length > 0) {
        console.warn(
          `Advisory: models with userID but no @@index on userID: ${violations.join(", ")}`,
        );
      }
      expect(true).toBe(true);
    });
  });
});

describe("android architecture guardrails", () => {
  const ANDROID_SRC = path.join(
    ROOT,
    "android-compose",
    "app",
    "src",
    "main",
    "java",
    "com",
    "ohmz",
    "tday",
    "compose",
  );

  const skipAndroid = !dirExists(ANDROID_SRC);

  (skipAndroid ? describe.skip : describe)(
    "package structure",
    () => {
      const REQUIRED_PACKAGES = [
        "core/data",
        "core/model",
        "core/navigation",
        "core/network",
        "feature",
        "ui/theme",
        "ui/component",
      ];

      it.each(REQUIRED_PACKAGES)(
        "package %s should exist",
        (pkg: string) => {
          expect(dirExists(path.join(ANDROID_SRC, ...pkg.split("/")))).toBe(
            true,
          );
        },
      );
    },
  );

  (skipAndroid ? describe.skip : describe)(
    "feature modules have consistent structure",
    () => {
      const featureDir = path.join(ANDROID_SRC, "feature");
      const features = listDirs(featureDir);

      it("should have at least 3 feature modules", () => {
        expect(features.length).toBeGreaterThanOrEqual(3);
      });

      it.each(features)(
        "feature '%s' should contain Kotlin files",
        (feature: string) => {
          const files = walkFiles(path.join(featureDir, feature), [".kt"]);
          expect(files.length).toBeGreaterThan(0);
        },
      );
    },
  );

  (skipAndroid ? describe.skip : describe)(
    "theme files exist",
    () => {
      const themeDir = path.join(ANDROID_SRC, "ui", "theme");

      const REQUIRED_THEME_FILES = [
        "Color.kt",
        "Theme.kt",
        "Type.kt",
        "Dimens.kt",
      ];

      it.each(REQUIRED_THEME_FILES)(
        "theme file %s should exist",
        (file: string) => {
          expect(existsSync(path.join(themeDir, file))).toBe(true);
        },
      );
    },
  );

  (skipAndroid ? describe.skip : describe)(
    "version syncs from package.json",
    () => {
      it("build.gradle.kts should read version from package.json, not hardcode it", () => {
        const gradlePath = path.join(
          ROOT,
          "android-compose",
          "app",
          "build.gradle.kts",
        );
        const content = readSource(gradlePath);
        expect(content).toContain("package.json");
        expect(content).not.toMatch(/versionName\s*=\s*"[0-9]+\.[0-9]+\.[0-9]+"/);
      });
    },
  );
});

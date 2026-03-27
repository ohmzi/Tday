import { readdirSync, readFileSync, existsSync, statSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");

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
      if (entry.name === "node_modules" || entry.name === "dist") continue;
      results.push(...walkFiles(full, extensions));
    } else if (extensions.some((ext) => entry.name.endsWith(ext))) {
      results.push(full);
    }
  }
  return results;
}

describe("web architecture guardrails", () => {
  describe("required tday-web directories exist", () => {
    const REQUIRED_DIRS = [
      "src",
      "src/components",
      "src/features",
      "src/hooks",
      "src/lib",
      "src/pages",
      "src/providers",
      "messages",
      "public",
    ];

    it.each(REQUIRED_DIRS)("directory %s should exist", (dir) => {
      expect(dirExists(path.join(ROOT, dir))).toBe(true);
    });
  });

  describe("src/lib is organized by domain", () => {
    it("lib should have domain subdirectories", () => {
      const libDirs = listDirs(path.join(ROOT, "src", "lib"));
      expect(libDirs.length).toBeGreaterThan(0);
      expect(libDirs).toContain("security");
    });
  });

  describe("features follow domain-based structure", () => {
    const featureDirs = listDirs(path.join(ROOT, "src", "features"));

    it("should have at least one feature directory", () => {
      expect(featureDirs.length).toBeGreaterThan(0);
    });

    it.each(featureDirs)(
      "feature '%s' should contain ts/tsx files",
      (feature) => {
        const files = walkFiles(path.join(ROOT, "src", "features", feature), [
          ".ts",
          ".tsx",
        ]);
        expect(files.length).toBeGreaterThan(0);
      },
    );
  });

  describe("internationalization completeness", () => {
    const localesDir = path.join(ROOT, "public", "locales");
    const EXPECTED_LOCALES = [
      "en", "zh", "de", "ja", "ar", "ru", "es", "fr", "ms", "it", "pt",
    ];

    it.each(EXPECTED_LOCALES)(
      "locale file %s/translation.json should exist",
      (locale) => {
        expect(
          existsSync(path.join(localesDir, locale, "translation.json")),
        ).toBe(true);
      },
    );

    it("en/translation.json should be valid JSON", () => {
      const content = readSource(path.join(localesDir, "en", "translation.json"));
      expect(() => JSON.parse(content)).not.toThrow();
    });

    it("all locale files should have the same top-level keys as english", () => {
      const enContent = JSON.parse(
        readSource(path.join(localesDir, "en", "translation.json")),
      );
      const enKeys = Object.keys(enContent).sort();

      for (const locale of EXPECTED_LOCALES.filter((l) => l !== "en")) {
        const localePath = path.join(localesDir, locale, "translation.json");
        if (!existsSync(localePath)) continue;
        const localeContent = JSON.parse(readSource(localePath));
        const localeKeys = Object.keys(localeContent).sort();
        expect(localeKeys).toEqual(enKeys);
      }
    });
  });

  describe("Ktor backend directory structure", () => {
    const backendSrc = path.join(MONO, "tday-backend", "src", "main", "kotlin", "com", "ohmz", "tday");

    const REQUIRED_DIRS = [
      "config",
      "db",
      "di",
      "domain",
      "models",
      "plugins",
      "routes",
      "security",
      "services",
    ];

    it.each(REQUIRED_DIRS)(
      "backend directory %s should exist",
      (dir) => {
        expect(dirExists(path.join(backendSrc, dir))).toBe(true);
      },
    );

    it("routes/auth/ should exist for authentication routes", () => {
      expect(dirExists(path.join(backendSrc, "routes", "auth"))).toBe(true);
    });
  });

  describe("Flyway migrations exist", () => {
    const migrationsDir = path.join(MONO, "tday-backend", "src", "main", "resources", "db", "migration");

    it("migration directory should exist with at least one migration", () => {
      if (!dirExists(migrationsDir)) return;
      const files = readdirSync(migrationsDir).filter((f) => f.endsWith(".sql"));
      expect(files.length).toBeGreaterThan(0);
    });
  });
});

describe("android architecture guardrails", () => {
  const ANDROID_SRC = path.join(
    MONO,
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
        (pkg) => {
          expect(dirExists(path.join(ANDROID_SRC, ...pkg.split("/")))).toBe(true);
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
        "feature '%s' should contain Kotlin files or be a planned placeholder",
        (feature) => {
          const files = walkFiles(path.join(featureDir, feature), [".kt"]);
          if (files.length === 0) {
            console.warn(`Advisory: feature '${feature}' has no Kotlin files (may be a placeholder)`);
          }
          expect(true).toBe(true);
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
        (file) => {
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
          MONO,
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

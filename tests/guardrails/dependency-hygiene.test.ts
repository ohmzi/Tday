import { readFileSync, existsSync } from "fs";
import path from "path";

const ROOT = process.cwd();

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function readJSON(filePath: string): Record<string, unknown> {
  return JSON.parse(readSource(filePath));
}

describe("dependency and configuration hygiene", () => {
  describe("package.json structure", () => {
    const pkg = readJSON(path.join(ROOT, "package.json")) as {
      version?: string;
      scripts?: Record<string, string>;
      dependencies?: Record<string, string>;
      devDependencies?: Record<string, string>;
      private?: boolean;
    };

    it("should have a valid semver version", () => {
      expect(pkg.version).toMatch(/^\d+\.\d+\.\d+$/);
    });

    it("should be marked as private", () => {
      expect(pkg.private).toBe(true);
    });

    it("should have required scripts", () => {
      const REQUIRED_SCRIPTS = ["dev", "build", "start", "lint", "test"];
      for (const script of REQUIRED_SCRIPTS) {
        expect(pkg.scripts).toHaveProperty(script);
      }
    });

    it("build script should run prisma generate and migrate", () => {
      expect(pkg.scripts?.build).toContain("prisma generate");
      expect(pkg.scripts?.build).toContain("prisma migrate deploy");
    });

    it("test script should run in UTC timezone", () => {
      expect(pkg.scripts?.test).toContain("TZ=UTC");
    });

    it("postinstall should generate Prisma client", () => {
      expect(pkg.scripts?.postinstall).toContain("prisma generate");
    });
  });

  describe("TypeScript configuration", () => {
    const tsconfig = readJSON(path.join(ROOT, "tsconfig.json")) as {
      compilerOptions?: {
        strict?: boolean;
        paths?: Record<string, string[]>;
      };
    };

    it("strict mode should be enabled", () => {
      expect(tsconfig.compilerOptions?.strict).toBe(true);
    });

    it("should have @/* path alias configured", () => {
      expect(tsconfig.compilerOptions?.paths).toHaveProperty("@/*");
    });
  });

  describe("ESLint configuration", () => {
    it("eslint config file should exist", () => {
      expect(existsSync(path.join(ROOT, "eslint.config.mjs"))).toBe(true);
    });

    it("should extend next/core-web-vitals and next/typescript", () => {
      const content = readSource(path.join(ROOT, "eslint.config.mjs"));
      expect(content).toContain("next/core-web-vitals");
      expect(content).toContain("next/typescript");
    });
  });

  describe("Git configuration", () => {
    it(".gitignore should exist", () => {
      expect(existsSync(path.join(ROOT, ".gitignore"))).toBe(true);
    });

    it(".gitignore should exclude node_modules, .next, and .env", () => {
      const gitignore = readSource(path.join(ROOT, ".gitignore"));
      expect(gitignore).toContain("node_modules");
      expect(gitignore).toContain(".next");
      expect(gitignore).toContain(".env");
    });
  });

  describe("environment configuration", () => {
    it(".env.example should exist as a setup reference", () => {
      expect(existsSync(path.join(ROOT, ".env.example"))).toBe(true);
    });

    it(".env.example should document all critical variables", () => {
      const envExample = readSource(path.join(ROOT, ".env.example"));
      const REQUIRED_VARS = [
        "DATABASE_URL",
        "AUTH_SECRET",
        "CRONJOB_SECRET",
        "OLLAMA_URL",
        "OLLAMA_MODEL",
        "AUTH_PBKDF2_ITERATIONS",
        "AUTH_SESSION_MAX_AGE_SEC",
      ];
      for (const v of REQUIRED_VARS) {
        expect(envExample).toContain(v);
      }
    });

    it(".env.example should not contain real secrets", () => {
      const envExample = readSource(path.join(ROOT, ".env.example"));
      const lines = envExample.split("\n");
      for (const line of lines) {
        if (line.startsWith("#") || !line.includes("=")) continue;
        const value = line.split("=").slice(1).join("=").trim();
        if (
          value.length > 40 &&
          !value.startsWith("postgresql://") &&
          !value.startsWith("http") &&
          !value.startsWith("/")
        ) {
          expect(value).toMatch(/CHANGE_ME|example|placeholder|dummy/i);
        }
      }
    });
  });

  describe("Docker configuration", () => {
    it("docker-compose.yaml should exist", () => {
      expect(existsSync(path.join(ROOT, "docker-compose.yaml"))).toBe(true);
    });

    it("Dockerfile should exist", () => {
      expect(existsSync(path.join(ROOT, "dockerfile"))).toBe(true);
    });

    it("Docker compose should define required services", () => {
      const compose = readSource(path.join(ROOT, "docker-compose.yaml"));
      expect(compose).toContain("database:");
      expect(compose).toContain("tday:");
      expect(compose).toContain("ollama:");
    });

    it("tday container should drop all capabilities", () => {
      const compose = readSource(path.join(ROOT, "docker-compose.yaml"));
      expect(compose).toContain("cap_drop");
      expect(compose).toContain("ALL");
    });

    it("tday container should prevent privilege escalation", () => {
      const compose = readSource(path.join(ROOT, "docker-compose.yaml"));
      expect(compose).toContain("no-new-privileges");
    });

    it("docker entrypoint script should exist", () => {
      expect(
        existsSync(path.join(ROOT, "scripts", "docker-entrypoint.sh")),
      ).toBe(true);
    });
  });

  describe("CI/CD workflows", () => {
    const workflowDir = path.join(ROOT, ".github", "workflows");

    it("PR gate workflow should exist", () => {
      expect(existsSync(path.join(workflowDir, "pr-gate.yml"))).toBe(true);
    });

    it("release workflow should exist", () => {
      expect(existsSync(path.join(workflowDir, "release.yml"))).toBe(true);
    });

    it("PR gate should enforce develop-only merges to master", () => {
      const content = readSource(path.join(workflowDir, "pr-gate.yml"));
      expect(content).toContain("develop");
      expect(content).toContain("master");
    });

    it("PR gate should run lint and tests", () => {
      const content = readSource(path.join(workflowDir, "pr-gate.yml"));
      expect(content).toContain("npm run lint");
      expect(content).toContain("npm run test");
    });

    it("release workflow should run tests before building Docker image", () => {
      const content = readSource(path.join(workflowDir, "release.yml"));
      expect(content).toContain("lint-and-test");
      expect(content).toContain("needs: lint-and-test");
    });

    it("release workflow should read version from package.json", () => {
      const content = readSource(path.join(workflowDir, "release.yml"));
      expect(content).toContain("package.json");
      expect(content).toContain("version");
    });
  });

  describe("commit and PR hygiene", () => {
    it("PR template should include no-AI-attribution checklist item", () => {
      const template = readSource(
        path.join(ROOT, ".github", "PULL_REQUEST_TEMPLATE.md"),
      );
      expect(template).toMatch(/[Nn]o AI tool attribution/);
    });

    it("CONTRIBUTING.md should document the no-AI-attribution rule", () => {
      const contributing = readSource(path.join(ROOT, "CONTRIBUTING.md"));
      expect(contributing).toMatch(/[Nn]o AI attribution/);
      expect(contributing).toMatch(/[Cc]ursor|[Cc]odex|[Cc]opilot/);
    });
  });

  describe("documentation completeness", () => {
    const REQUIRED_DOCS = [
      "README.md",
      "CONTRIBUTING.md",
      "SECURITY.md",
      "docs/ARCHITECTURE.md",
      "docs/CODING_STANDARDS.md",
      "docs/API_GUIDELINES.md",
      "docs/TESTING.md",
      "docs/DEPLOYMENT.md",
      ".github/PULL_REQUEST_TEMPLATE.md",
    ];

    it.each(REQUIRED_DOCS)("%s should exist", (docPath: string) => {
      expect(existsSync(path.join(ROOT, docPath))).toBe(true);
    });

    it("should have at least one Architecture Decision Record", () => {
      const adrDir = path.join(ROOT, "docs", "adr");
      expect(existsSync(adrDir)).toBe(true);
      const adrs = existsSync(adrDir)
        ? readFileSync
          ? require("fs")
              .readdirSync(adrDir)
              .filter((f: string) => f.endsWith(".md"))
          : []
        : [];
      expect(adrs.length).toBeGreaterThan(0);
    });
  });

  describe("version synchronization", () => {
    it("package.json version should be a valid semver", () => {
      const pkg = readJSON(path.join(ROOT, "package.json")) as {
        version: string;
      };
      expect(pkg.version).toMatch(/^\d+\.\d+\.\d+$/);
    });

    it("Android build.gradle.kts should derive version from package.json", () => {
      const gradlePath = path.join(
        ROOT,
        "android-compose",
        "app",
        "build.gradle.kts",
      );
      if (existsSync(gradlePath)) {
        const content = readSource(gradlePath);
        expect(content).toContain("package.json");
        expect(content).toContain("projectVersion");
      }
    });
  });
});

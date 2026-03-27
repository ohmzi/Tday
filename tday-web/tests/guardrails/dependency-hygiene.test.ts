import { readFileSync, existsSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function readJSON(filePath: string): Record<string, unknown> {
  return JSON.parse(readSource(filePath));
}

describe("dependency and configuration hygiene", () => {
  describe("tday-web package.json structure", () => {
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
      const REQUIRED_SCRIPTS = ["dev", "build", "lint", "test"];
      for (const script of REQUIRED_SCRIPTS) {
        expect(pkg.scripts).toHaveProperty(script);
      }
    });
  });

  describe("Git configuration", () => {
    it(".gitignore should exist", () => {
      expect(existsSync(path.join(MONO, ".gitignore"))).toBe(true);
    });

    it(".gitignore should exclude node_modules and .env", () => {
      const gitignore = readSource(path.join(MONO, ".gitignore"));
      expect(gitignore).toContain("node_modules");
      expect(gitignore).toContain(".env");
    });

    it(".gitignore should exclude tday-web/dist", () => {
      const gitignore = readSource(path.join(MONO, ".gitignore"));
      expect(gitignore).toContain("tday-web/dist");
    });
  });

  describe("environment configuration", () => {
    it(".env.example should exist as a setup reference", () => {
      expect(existsSync(path.join(MONO, ".env.example"))).toBe(true);
    });

    it(".env.example should document all critical variables", () => {
      const envExample = readSource(path.join(MONO, ".env.example"));
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
      const envExample = readSource(path.join(MONO, ".env.example"));
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
      expect(existsSync(path.join(MONO, "docker-compose.yaml"))).toBe(true);
    });

    it("Dockerfile.backend should exist", () => {
      expect(existsSync(path.join(MONO, "Dockerfile.backend"))).toBe(true);
    });

    it("Docker compose should define required services", () => {
      const compose = readSource(path.join(MONO, "docker-compose.yaml"));
      expect(compose).toContain("database:");
      expect(compose).toContain("tday-backend:");
      expect(compose).toContain("ollama:");
    });

    it("tday container should drop all capabilities", () => {
      const compose = readSource(path.join(MONO, "docker-compose.yaml"));
      expect(compose).toContain("cap_drop");
      expect(compose).toContain("ALL");
    });

    it("tday container should prevent privilege escalation", () => {
      const compose = readSource(path.join(MONO, "docker-compose.yaml"));
      expect(compose).toContain("no-new-privileges");
    });
  });

  describe("CI/CD workflows", () => {
    const workflowDir = path.join(MONO, ".github", "workflows");

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

    it("CI should run lint and tests in tday-web context", () => {
      const content = readSource(path.join(workflowDir, "pr-gate.yml"));
      expect(content).toContain("npm run lint");
      expect(content).toContain("npm run test");
      expect(content).toContain("tday-web");
    });

    it("release workflow should run tests before building Docker image", () => {
      const content = readSource(path.join(workflowDir, "release.yml"));
      expect(content).toContain("lint-and-test");
      expect(content).toContain("needs: lint-and-test");
    });

    it("release workflow should read version from tday-web/package.json", () => {
      const content = readSource(path.join(workflowDir, "release.yml"));
      expect(content).toContain("tday-web/package.json");
    });

    it("release workflow should build from Dockerfile.backend", () => {
      const content = readSource(path.join(workflowDir, "release.yml"));
      expect(content).toContain("Dockerfile.backend");
    });
  });

  describe("commit and PR hygiene", () => {
    it("PR template should include no-AI-attribution checklist item", () => {
      const template = readSource(
        path.join(MONO, ".github", "PULL_REQUEST_TEMPLATE.md"),
      );
      expect(template).toMatch(/[Nn]o AI tool attribution/);
    });

    it("commit-msg hook script should exist in scripts/", () => {
      expect(existsSync(path.join(MONO, "scripts", "commit-msg"))).toBe(true);
    });

    it("commit-msg hook should strip Made-with trailers", () => {
      const hook = readSource(path.join(MONO, "scripts", "commit-msg"));
      expect(hook).toMatch(/Made-with/);
    });

    it("install-hooks.sh script should exist", () => {
      expect(existsSync(path.join(MONO, "scripts", "install-hooks.sh"))).toBe(true);
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

    it.each(REQUIRED_DOCS)("%s should exist", (docPath) => {
      expect(existsSync(path.join(MONO, docPath))).toBe(true);
    });
  });

  describe("version synchronization", () => {
    it("tday-web package.json version should be a valid semver", () => {
      const pkg = readJSON(path.join(ROOT, "package.json")) as {
        version: string;
      };
      expect(pkg.version).toMatch(/^\d+\.\d+\.\d+$/);
    });

    it("Android build.gradle.kts should derive version from package.json", () => {
      const gradlePath = path.join(
        MONO,
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

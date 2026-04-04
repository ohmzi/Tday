import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");
const BACKEND_SRC = path.join(MONO, "tday-backend", "src", "main", "kotlin", "com", "ohmz", "tday");
const ROUTES_DIR = path.join(BACKEND_SRC, "routes");

function walkFiles(dir: string, ext: string): string[] {
  const results: string[] = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...walkFiles(full, ext));
    } else if (entry.name.endsWith(ext)) {
      results.push(full);
    }
  }
  return results;
}

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function relPath(filePath: string): string {
  return path.relative(MONO, filePath);
}

const ALL_ROUTE_FILES = walkFiles(ROUTES_DIR, ".kt");
const AUTH_ROUTE_DIR = path.join(ROUTES_DIR, "auth");
const AUTH_ROUTES = existsSync(AUTH_ROUTE_DIR) ? walkFiles(AUTH_ROUTE_DIR, ".kt") : [];
const NON_AUTH_ROUTES = ALL_ROUTE_FILES.filter((f) => !f.startsWith(AUTH_ROUTE_DIR));

describe("Ktor API route conventions", () => {
  describe("route files use Ktor routing DSL", () => {
    it.each(ALL_ROUTE_FILES.map((f) => [relPath(f), f]))(
      "%s should use Route extension function pattern",
      (_label, filePath) => {
        const content = readSource(filePath);
        expect(content).toMatch(/fun\s+Route\./);
      },
    );
  });

  describe("authenticated routes check auth context", () => {
    const dataRoutes = NON_AUTH_ROUTES.filter((f) => {
      const name = path.basename(f, ".kt");
      return !["MobileProbeRoutes", "TimezoneRoutes", "AppSettingsRoutes"].includes(name);
    });

    it.each(dataRoutes.map((f) => [relPath(f), f]))(
      "%s should verify authentication",
      (_label, filePath) => {
        const content = readSource(filePath);
        const hasAuth =
          content.includes("withAuth") ||
          content.includes("authUser") ||
          content.includes("requireAdmin");
        expect(hasAuth).toBe(true);
      },
    );
  });

  describe("auth routes exist for complete auth flow", () => {
    const expectedRouteFiles = [
      "CsrfRoutes.kt",
      "RegisterRoutes.kt",
      "CredentialsCallbackRoutes.kt",
      "SessionRoutes.kt",
      "LogoutRoutes.kt",
    ];

    it.each(expectedRouteFiles)(
      "auth route file %s should exist",
      (filename) => {
        expect(existsSync(path.join(AUTH_ROUTE_DIR, filename))).toBe(true);
      },
    );
  });

  describe("routing plugin mounts all route groups", () => {
    it("Routing.kt should mount all major route groups", () => {
      const routingFile = readSource(path.join(BACKEND_SRC, "plugins", "Routing.kt"));
      expect(routingFile).toContain("todoRoutes()");
      expect(routingFile).toContain("listRoutes()");
      expect(routingFile).toContain("userRoutes()");
      expect(routingFile).toContain("mobileProbeRoutes(config)");
      expect(routingFile).toContain("csrfRoutes()");
      expect(routingFile).toContain("registerRoutes()");
      expect(routingFile).toContain("credentialsCallbackRoutes()");
      expect(routingFile).toContain("sessionRoutes()");
      expect(routingFile).toContain("logoutRoutes()");
    });
  });

  describe("status pages plugin provides structured error handling", () => {
    it("StatusPages.kt should handle common error types", () => {
      const statusPages = readSource(path.join(BACKEND_SRC, "plugins", "StatusPages.kt"));
      expect(statusPages).toContain("StatusPages");
      expect(statusPages).toContain("exception");
    });
  });
});

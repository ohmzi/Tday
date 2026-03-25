import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";

const ROOT = process.cwd();

function walkFiles(dir: string, ext: string): string[] {
  const results: string[] = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === ".next") continue;
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
  return path.relative(ROOT, filePath);
}

const API_ROUTE_DIR = path.join(ROOT, "app", "api");
const PUBLIC_PREFIXES = ["auth", "mobile"];

function discoverPrivateRouteFiles(): string[] {
  return walkFiles(API_ROUTE_DIR, ".ts")
    .filter((f: string) => f.endsWith("route.ts"))
    .filter((f: string) => {
      const rel = path.relative(API_ROUTE_DIR, f);
      return !PUBLIC_PREFIXES.some((prefix: string) =>
        rel.startsWith(prefix + path.sep),
      );
    });
}

describe("security guardrails", () => {
  describe("no secrets in source code", () => {
    const sourceFiles = [
      ...walkFiles(path.join(ROOT, "app"), ".ts"),
      ...walkFiles(path.join(ROOT, "app"), ".tsx"),
      ...walkFiles(path.join(ROOT, "lib"), ".ts"),
      ...walkFiles(path.join(ROOT, "components"), ".tsx"),
      ...walkFiles(path.join(ROOT, "features"), ".ts"),
      ...walkFiles(path.join(ROOT, "features"), ".tsx"),
    ];

    const SECRET_PATTERNS = [
      /(?:password|secret|token|apiKey|api_key)\s*[:=]\s*["'][^"']{8,}["']/i,
      /-----BEGIN\s+(RSA\s+)?PRIVATE\s+KEY-----/,
      /ghp_[A-Za-z0-9]{36}/,
      /sk-[A-Za-z0-9]{32,}/,
    ];

    it("should not contain hardcoded secrets or private keys", () => {
      const violations: string[] = [];
      for (const file of sourceFiles) {
        if (file.includes(".test.") || file.includes("__test__")) continue;
        const content = readSource(file);
        for (const pattern of SECRET_PATTERNS) {
          if (pattern.test(content)) {
            violations.push(`${relPath(file)} matches ${pattern.source}`);
          }
        }
      }
      expect(violations).toEqual([]);
    });
  });

  describe("private API routes use authentication", () => {
    const privateRoutes = discoverPrivateRouteFiles();

    it("should have at least one private route to validate", () => {
      expect(privateRoutes.length).toBeGreaterThan(0);
    });

    it.each(privateRoutes.map((f: string) => [relPath(f), f]))(
      "%s should call auth() or requireAdmin()",
      (_label: string, filePath: string) => {
        const content = readSource(filePath);
        const callsAuth =
          content.includes("await auth()") ||
          content.includes("await requireAdmin()");
        expect(callsAuth).toBe(true);
      },
    );
  });

  describe("private API routes use centralized error handling", () => {
    const privateRoutes = discoverPrivateRouteFiles();

    it.each(privateRoutes.map((f: string) => [relPath(f), f]))(
      "%s should use errorHandler for catch blocks",
      (_label: string, filePath: string) => {
        const content = readSource(filePath);
        const hasTryCatch = content.includes("try {") || content.includes("try{");
        if (hasTryCatch) {
          expect(content).toContain("errorHandler");
        }
      },
    );
  });

  describe("security headers in middleware", () => {
    const middlewarePath = path.join(ROOT, "middleware.ts");
    const content = readSource(middlewarePath);

    const REQUIRED_HEADERS = [
      "X-Content-Type-Options",
      "X-Frame-Options",
      "Referrer-Policy",
      "Cross-Origin-Resource-Policy",
      "Cross-Origin-Opener-Policy",
      "Content-Security-Policy",
      "Permissions-Policy",
    ];

    it.each(REQUIRED_HEADERS)(
      "middleware should set %s header",
      (header: string) => {
        expect(content).toContain(header);
      },
    );

    it("should set Strict-Transport-Security for production", () => {
      expect(content).toContain("Strict-Transport-Security");
    });
  });

  describe("no sensitive data in logs", () => {
    const libFiles = walkFiles(path.join(ROOT, "lib"), ".ts");
    const apiFiles = walkFiles(API_ROUTE_DIR, ".ts");
    const allFiles = [...libFiles, ...apiFiles];

    it("should not log full request bodies or auth tokens", () => {
      const violations: string[] = [];
      const DANGEROUS_LOG_PATTERNS = [
        /console\.(log|info|debug)\s*\(\s*["'].*password/i,
        /console\.(log|info|debug)\s*\(\s*["'].*secret/i,
        /console\.(log|info|debug)\s*\(\s*["'].*token/i,
        /console\.(log|info)\s*\(\s*req\.body\s*\)/,
        /console\.(log|info)\s*\(\s*JSON\.stringify\(\s*req/,
      ];

      for (const file of allFiles) {
        const content = readSource(file);
        for (const pattern of DANGEROUS_LOG_PATTERNS) {
          if (pattern.test(content)) {
            violations.push(`${relPath(file)} matches ${pattern.source}`);
          }
        }
      }
      expect(violations).toEqual([]);
    });
  });

  describe("error responses do not leak internals", () => {
    it("errorHandler should return generic message for unknown errors", () => {
      const handlerPath = path.join(ROOT, "lib", "errorHandler.ts");
      const content = readSource(handlerPath);
      expect(content).toContain("An unexpected error occurred");
      expect(content).not.toMatch(/stack|stackTrace|\.stack/);
    });
  });

  describe("CSRF and rate limit configuration", () => {
    const envExample = readSource(path.join(ROOT, ".env.example"));

    it(".env.example should document rate limit variables", () => {
      expect(envExample).toContain("AUTH_LIMIT_CSRF_WINDOW_SEC");
      expect(envExample).toContain("AUTH_LIMIT_CREDENTIALS_WINDOW_SEC");
      expect(envExample).toContain("AUTH_LIMIT_REGISTER_WINDOW_SEC");
    });

    it(".env.example should document lockout variables", () => {
      expect(envExample).toContain("AUTH_LOCKOUT_FAIL_THRESHOLD");
      expect(envExample).toContain("AUTH_LOCKOUT_BASE_SEC");
      expect(envExample).toContain("AUTH_LOCKOUT_MAX_SEC");
    });

    it(".env.example should document CAPTCHA variables", () => {
      expect(envExample).toContain("AUTH_CAPTCHA_TRIGGER_FAILURES");
      expect(envExample).toContain("AUTH_CAPTCHA_SECRET");
    });
  });

  describe("session revocation support", () => {
    const schemaPath = path.join(ROOT, "prisma", "schema.prisma");
    const schema = readSource(schemaPath);

    it("User model should have tokenVersion for session revocation", () => {
      expect(schema).toContain("tokenVersion");
    });

    it("should have AuthThrottle model for rate limiting", () => {
      expect(schema).toContain("model AuthThrottle");
    });

    it("should have eventLog model for security event auditing", () => {
      expect(schema).toContain("model eventLog");
    });
  });
});

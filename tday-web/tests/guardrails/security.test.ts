import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");
const BACKEND_SRC = path.join(MONO, "tday-backend", "src", "main", "kotlin", "com", "ohmz", "tday");

function walkFiles(dir: string, ext: string): string[] {
  const results: string[] = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name === "dist") continue;
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

describe("security guardrails", () => {
  describe("no secrets in frontend source code", () => {
    const sourceFiles = [
      ...walkFiles(path.join(ROOT, "src"), ".ts"),
      ...walkFiles(path.join(ROOT, "src"), ".tsx"),
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

  describe("no secrets in backend source code", () => {
    const backendFiles = walkFiles(BACKEND_SRC, ".kt");

    const SECRET_PATTERNS = [
      /(?:password|secret|token|apiKey)\s*=\s*"[^"]{8,}"/i,
      /-----BEGIN\s+(RSA\s+)?PRIVATE\s+KEY-----/,
    ];

    it("should not contain hardcoded secrets in Kotlin files", () => {
      const PEM_PARSERS = ["CredentialEnvelope.kt"];
      const violations: string[] = [];
      for (const file of backendFiles) {
        if (file.includes("Test")) continue;
        const content = readSource(file);
        const basename = path.basename(file);
        for (const pattern of SECRET_PATTERNS) {
          if (PEM_PARSERS.includes(basename) && pattern.source.includes("PRIVATE")) continue;
          if (pattern.test(content)) {
            violations.push(`${relPath(file)} matches ${pattern.source}`);
          }
        }
      }
      expect(violations).toEqual([]);
    });
  });

  describe("Ktor security headers plugin", () => {
    it("SecurityHeaders.kt should exist", () => {
      expect(existsSync(path.join(BACKEND_SRC, "plugins", "SecurityHeaders.kt"))).toBe(true);
    });

    it("SecurityHeaders should set required security headers", () => {
      const content = readSource(path.join(BACKEND_SRC, "plugins", "SecurityHeaders.kt"));
      expect(content).toContain("X-Content-Type-Options");
      expect(content).toContain("X-Frame-Options");
      expect(content).toContain("Referrer-Policy");
    });
  });

  describe("backend security services exist", () => {
    const securityDir = path.join(BACKEND_SRC, "security");

    const REQUIRED_SECURITY_FILES = [
      "PasswordService.kt",
      "CredentialEnvelope.kt",
      "PasswordProof.kt",
      "FieldEncryption.kt",
      "AuthThrottle.kt",
      "JwtService.kt",
      "SecurityEventLogger.kt",
      "SessionControl.kt",
    ];

    it.each(REQUIRED_SECURITY_FILES)(
      "security file %s should exist",
      (filename) => {
        expect(existsSync(path.join(securityDir, filename))).toBe(true);
      },
    );
  });

  describe("password hashing uses PBKDF2", () => {
    it("PasswordService should use PBKDF2WithHmacSHA256", () => {
      const content = readSource(path.join(BACKEND_SRC, "security", "PasswordService.kt"));
      expect(content).toContain("PBKDF2WithHmacSHA256");
    });

    it("PasswordService should use timing-safe comparison", () => {
      const content = readSource(path.join(BACKEND_SRC, "security", "PasswordService.kt"));
      expect(content).toMatch(/timingSafe|MessageDigest\.isEqual/);
    });
  });

  describe("field encryption uses AES-GCM", () => {
    it("FieldEncryption should use AES/GCM/NoPadding", () => {
      const content = readSource(path.join(BACKEND_SRC, "security", "FieldEncryption.kt"));
      expect(content).toContain("AES/GCM/NoPadding");
    });
  });

  describe("credential envelope uses RSA-OAEP + AES-GCM", () => {
    it("CredentialEnvelope should use RSA-OAEP for key unwrapping", () => {
      const content = readSource(path.join(BACKEND_SRC, "security", "CredentialEnvelope.kt"));
      expect(content).toContain("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    });

    it("CredentialEnvelope should use AES-GCM for payload decryption", () => {
      const content = readSource(path.join(BACKEND_SRC, "security", "CredentialEnvelope.kt"));
      expect(content).toContain("AES/GCM/NoPadding");
    });
  });

  describe("CSRF and rate limit configuration", () => {
    const envExample = readSource(path.join(MONO, ".env.example"));

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

  describe("no sensitive data in logs", () => {
    const backendFiles = walkFiles(BACKEND_SRC, ".kt");

    it("should not log raw passwords or secrets", () => {
      const violations: string[] = [];
      const DANGEROUS_LOG = /logger\.\w+\s*\(.*password.*\)/i;

      for (const file of backendFiles) {
        const content = readSource(file);
        if (DANGEROUS_LOG.test(content)) {
          violations.push(relPath(file));
        }
      }
      expect(violations).toEqual([]);
    });
  });
});

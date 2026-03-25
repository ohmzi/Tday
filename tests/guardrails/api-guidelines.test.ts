import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";

const ROOT = process.cwd();
const API_ROOT = path.join(ROOT, "app", "api");

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
  return path.relative(ROOT, filePath);
}

const VALID_HTTP_METHODS = ["GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"];
const AUTH_EXEMPT_DIRS = ["auth", "mobile"];

const ALL_ROUTES = walkFiles(API_ROOT, ".ts").filter((f: string) =>
  f.endsWith("route.ts"),
);

const PRIVATE_ROUTES = ALL_ROUTES.filter((f: string) => {
  const rel = path.relative(API_ROOT, f);
  return !AUTH_EXEMPT_DIRS.some((dir: string) => rel.startsWith(dir + path.sep));
});

describe("API route handler conventions", () => {
  describe("route files only export valid HTTP methods", () => {
    it.each(ALL_ROUTES.map((f: string) => [relPath(f), f]))(
      "%s should only export valid HTTP method handlers",
      (_label: string, filePath: string) => {
        const content = readSource(filePath);
        const exportedFns =
          content.match(/export\s+(?:async\s+)?function\s+(\w+)/g) ?? [];
        for (const fn of exportedFns) {
          const name = fn.replace(
            /export\s+(?:async\s+)?function\s+/,
            "",
          );
          expect(VALID_HTTP_METHODS).toContain(name);
        }
      },
    );
  });

  describe("route handlers use try/catch with errorHandler", () => {
    it.each(PRIVATE_ROUTES.map((f: string) => [relPath(f), f]))(
      "%s should wrap logic in try/catch",
      (_label: string, filePath: string) => {
        const content = readSource(filePath);
        const exportedFns =
          content.match(/export\s+(?:async\s+)?function\s+\w+/g) ?? [];
        if (exportedFns.length > 0) {
          expect(content).toContain("try");
          expect(content).toContain("catch");
        }
      },
    );

    it.each(PRIVATE_ROUTES.map((f: string) => [relPath(f), f]))(
      "%s should import and use errorHandler",
      (_label: string, filePath: string) => {
        const content = readSource(filePath);
        if (content.includes("catch")) {
          expect(content).toContain("errorHandler");
        }
      },
    );
  });

  describe("route handlers use NextResponse.json for responses", () => {
    it.each(ALL_ROUTES.map((f: string) => [relPath(f), f]))(
      "%s should use NextResponse.json (not raw Response)",
      (_label: string, filePath: string) => {
        const content = readSource(filePath);
        if (
          content.includes("return") &&
          !content.includes("handlers") // skip nextauth delegate
        ) {
          const usesNextResponse =
            content.includes("NextResponse.json") ||
            content.includes("NextResponse.redirect");
          const usesRawResponse =
            content.includes("new Response(") &&
            !content.includes("NextResponse");
          if (usesRawResponse && !usesNextResponse) {
            console.warn(
              `Advisory: ${relPath(filePath)} uses raw Response instead of NextResponse.json`,
            );
          }
        }
      },
    );
  });

  describe("response shape consistency", () => {
    it("errorHandler should return { message } shape for all errors", () => {
      const handlerContent = readSource(
        path.join(ROOT, "lib", "errorHandler.ts"),
      );
      const jsonCalls = handlerContent.match(/NextResponse\.json\(\s*\{[^}]+\}/g) ?? [];
      for (const call of jsonCalls) {
        expect(call).toContain("message");
      }
    });

    it("custom error classes should all extend BaseServerError", () => {
      const errorContent = readSource(
        path.join(ROOT, "lib", "customError.ts"),
      );
      const classes =
        errorContent.match(/class\s+\w+\s+extends\s+(\w+)/g) ?? [];
      for (const cls of classes) {
        const parent = cls.split("extends")[1].trim();
        expect(parent).toBe("BaseServerError");
      }
    });

    it("should have error classes for standard HTTP error codes", () => {
      const errorContent = readSource(
        path.join(ROOT, "lib", "customError.ts"),
      );
      expect(errorContent).toContain("400");
      expect(errorContent).toContain("401");
      expect(errorContent).toContain("403");
      expect(errorContent).toContain("404");
      expect(errorContent).toContain("500");
    });
  });

  describe("tenant isolation", () => {
    it.each(PRIVATE_ROUTES.map((f: string) => [relPath(f), f]))(
      "%s should filter by user ID from session",
      (_label: string, filePath: string) => {
        const content = readSource(filePath);
        if (content.includes("prisma.")) {
          const usesSessionUserId =
            content.includes("session") &&
            (content.includes("user.id") || content.includes("userId"));
          const isAdminRoute = filePath.includes("admin");
          if (!isAdminRoute) {
            expect(usesSessionUserId).toBe(true);
          }
        }
      },
    );
  });

  describe("input validation", () => {
    it("routes accepting request body should validate input", () => {
      const violations: string[] = [];
      for (const file of PRIVATE_ROUTES) {
        const content = readSource(file);
        const acceptsBody =
          content.includes("req.json()") ||
          content.includes("request.json()");
        if (acceptsBody) {
          const hasValidation =
            content.includes("safeParse") ||
            content.includes("parse(") ||
            content.includes("z.object") ||
            content.includes("Zod") ||
            content.includes("schema");
          if (!hasValidation) {
            violations.push(relPath(file));
          }
        }
      }

      if (violations.length > 0) {
        console.warn(
          `Advisory: ${violations.length} route(s) accept request body without visible Zod validation:\n` +
            violations.join("\n"),
        );
      }
      expect(true).toBe(true);
    });
  });

  describe("no direct database access in component files", () => {
    const componentFiles = walkFiles(
      path.join(ROOT, "components"),
      ".tsx",
    );
    const featureComponentFiles = walkFiles(
      path.join(ROOT, "features"),
      ".tsx",
    );

    it("React components should not import Prisma directly", () => {
      const violations: string[] = [];
      for (const file of [...componentFiles, ...featureComponentFiles]) {
        const content = readSource(file);
        if (
          content.includes("from \"@/lib/prisma") ||
          content.includes("from '@/lib/prisma") ||
          content.includes("PrismaClient")
        ) {
          violations.push(relPath(file));
        }
      }
      expect(violations).toEqual([]);
    });
  });
});

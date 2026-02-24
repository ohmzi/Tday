import { readdirSync } from "fs";
import path from "path";
import { NextRequest } from "next/server";

jest.mock("next-intl/middleware", () => ({
  __esModule: true,
  default: () => () => new Response(null, { status: 200 }),
}));

jest.mock("next-intl/routing", () => ({
  defineRouting: (config: unknown) => config,
}));

jest.mock("next-auth/jwt", () => ({
  getToken: jest.fn().mockResolvedValue(null),
}));

import middleware from "@/middleware";

const PUBLIC_API_PREFIXES = ["/api/auth", "/api/mobile/probe"];

function discoverApiPaths(): string[] {
  const apiRoot = path.join(process.cwd(), "app", "api");
  const paths: string[] = [];

  const walk = (dirPath: string) => {
    for (const entry of readdirSync(dirPath, { withFileTypes: true })) {
      const absolutePath = path.join(dirPath, entry.name);
      if (entry.isDirectory()) {
        walk(absolutePath);
        continue;
      }
      if (entry.name !== "route.ts") continue;

      const relative = path.relative(apiRoot, absolutePath).replace(/\\/g, "/");
      const withoutRouteSuffix = relative.replace(/\/route\.ts$/, "");
      const withDynamicParams = withoutRouteSuffix
        .split("/")
        .map((segment) => {
          if (segment.startsWith("[...")) return "dynamic";
          if (segment.startsWith("[") && segment.endsWith("]")) {
            return "dynamic-id";
          }
          return segment;
        })
        .join("/");

      const apiPath = `/api/${withDynamicParams}`.replace(/\/+/g, "/");
      paths.push(apiPath.endsWith("/") ? apiPath.slice(0, -1) : apiPath);
    }
  };

  walk(apiRoot);
  return Array.from(new Set(paths)).sort();
}

describe("middleware private API authorization", () => {
  test("returns 401 for every discovered private API endpoint when unauthenticated", async () => {
    const discoveredPaths = discoverApiPaths();
    const privateApiPaths = discoveredPaths.filter(
      (apiPath) =>
        !PUBLIC_API_PREFIXES.some(
          (publicPrefix) =>
            apiPath === publicPrefix || apiPath.startsWith(`${publicPrefix}/`),
        ),
    );

    expect(privateApiPaths.length).toBeGreaterThan(0);

    for (const apiPath of privateApiPaths) {
      const req = new NextRequest(`http://localhost${apiPath}`);
      const response = await middleware(req);
      expect(response.status).toBe(401);
    }
  });
});

describe("middleware secure transport handling", () => {
  const originalNodeEnv = process.env.NODE_ENV;

  beforeAll(() => {
    process.env.NODE_ENV = "production";
  });

  afterAll(() => {
    process.env.NODE_ENV = originalNodeEnv;
  });

  test("does not force redirect when Cloudflare reports https visitor scheme", async () => {
    const req = new NextRequest("http://tday.ohmz.cloud/", {
      headers: {
        host: "tday.ohmz.cloud",
        "x-forwarded-proto": "http",
        "cf-visitor": "{\"scheme\":\"https\"}",
      },
    });

    const response = await middleware(req);
    expect(response.status).toBe(200);
  });

  test("forces redirect to https for non-local production hosts over plain http", async () => {
    const req = new NextRequest("http://tday.ohmz.cloud/", {
      headers: {
        host: "tday.ohmz.cloud",
        "x-forwarded-proto": "http",
      },
    });

    const response = await middleware(req);
    expect(response.status).toBe(308);
    expect(response.headers.get("location")).toBe("https://tday.ohmz.cloud/");
  });

  test("uses forwarded host when building https redirect location", async () => {
    const req = new NextRequest("http://localhost:3000/", {
      headers: {
        host: "localhost:3000",
        "x-forwarded-host": "tday.ohmz.cloud",
        "x-forwarded-proto": "http",
      },
    });

    const response = await middleware(req);
    expect(response.status).toBe(308);
    expect(response.headers.get("location")).toBe("https://tday.ohmz.cloud/");
  });
});

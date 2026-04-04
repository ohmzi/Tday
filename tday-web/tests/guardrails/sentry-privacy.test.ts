import { readFileSync, existsSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");

const BACKEND_SRC = path.join(
  MONO, "tday-backend", "src", "main", "kotlin", "com", "ohmz", "tday",
);
const ANDROID_SRC = path.join(
  MONO, "android-compose", "app", "src", "main",
);
const IOS_SRC = path.join(MONO, "ios-swiftUI", "Tday");

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

// ─── Backend Sentry paths ───────────────────────────────────────────
const backendApp = path.join(BACKEND_SRC, "Application.kt");
const backendStatusPages = path.join(BACKEND_SRC, "plugins", "StatusPages.kt");
const backendSentryPlugin = path.join(BACKEND_SRC, "plugins", "SentryPlugin.kt");
const backendRouting = path.join(BACKEND_SRC, "plugins", "Routing.kt");
const backendGradle = path.join(MONO, "tday-backend", "build.gradle.kts");
const backendLogback = path.join(
  MONO, "tday-backend", "src", "main", "resources", "logback.xml",
);

// ─── Web Sentry paths ──────────────────────────────────────────────
const webMain = path.join(ROOT, "src", "main.tsx");
const webRouter = path.join(ROOT, "src", "router.tsx");
const webApiClient = path.join(ROOT, "src", "lib", "api-client.ts");
const webErrorBoundary = path.join(ROOT, "src", "components", "ErrorBoundary.tsx");
const webPackageJson = path.join(ROOT, "package.json");

// ─── Android Sentry paths ──────────────────────────────────────────
const androidApplication = path.join(
  ANDROID_SRC, "java", "com", "ohmz", "tday", "compose", "TdayApplication.kt",
);
const androidManifest = path.join(ANDROID_SRC, "AndroidManifest.xml");
const androidGradle = path.join(MONO, "android-compose", "app", "build.gradle.kts");
const androidNetworkModule = path.join(
  ANDROID_SRC, "java", "com", "ohmz", "tday", "compose", "core", "network", "NetworkModule.kt",
);

// ─── iOS Sentry paths ──────────────────────────────────────────────
const iosSentryConfig = path.join(IOS_SRC, "Core", "SentryConfiguration.swift");
const iosApp = path.join(IOS_SRC, "TdayApp.swift");

// ─── Documentation ─────────────────────────────────────────────────
const telemetryDoc = path.join(MONO, "docs", "TELEMETRY.md");

describe("sentry integration guardrails", () => {
  describe("SDK dependencies are declared", () => {
    it("backend build.gradle.kts includes sentry dependencies", () => {
      const content = readSource(backendGradle);
      expect(content).toContain("io.sentry:sentry:");
      expect(content).toContain("io.sentry.jvm.gradle");
    });

    it("web package.json includes @sentry/react", () => {
      const pkg = JSON.parse(readSource(webPackageJson));
      const allDeps = { ...pkg.dependencies, ...pkg.devDependencies };
      expect(allDeps).toHaveProperty("@sentry/react");
    });

    it("android build.gradle.kts includes sentry plugin and dependencies", () => {
      const content = readSource(androidGradle);
      expect(content).toContain("io.sentry.android.gradle");
      expect(content).toContain("io.sentry:sentry-okhttp:");
    });

    it("iOS Package.swift or SentryConfiguration.swift references Sentry SDK", () => {
      expect(existsSync(iosSentryConfig)).toBe(true);
      const content = readSource(iosSentryConfig);
      expect(content).toContain("import Sentry");
    });
  });

  describe("Sentry initialization exists on every platform", () => {
    it("backend Application.kt calls Sentry.init", () => {
      const content = readSource(backendApp);
      expect(content).toContain("Sentry.init");
    });

    it("web main.tsx calls Sentry.init", () => {
      const content = readSource(webMain);
      expect(content).toContain("Sentry.init");
    });

    it("android TdayApplication.kt calls SentryAndroid.init", () => {
      const content = readSource(androidApplication);
      expect(content).toContain("SentryAndroid.init");
    });

    it("iOS TdayApp.swift triggers SentryConfiguration.start()", () => {
      const app = readSource(iosApp);
      expect(app).toContain("SentryConfiguration.start()");
      const config = readSource(iosSentryConfig);
      expect(config).toContain("SentrySDK.start");
    });
  });
});

describe("sentry privacy guardrails", () => {
  describe("sendDefaultPii is disabled on every platform", () => {
    it("backend sets isSendDefaultPii = false", () => {
      const content = readSource(backendApp);
      expect(content).toContain("isSendDefaultPii = false");
    });

    it("web sets sendDefaultPii: false", () => {
      const content = readSource(webMain);
      expect(content).toContain("sendDefaultPii: false");
    });

    it("android sets isSendDefaultPii = false", () => {
      const content = readSource(androidApplication);
      expect(content).toContain("isSendDefaultPii = false");
    });

    it("iOS sets sendDefaultPii = false", () => {
      const content = readSource(iosSentryConfig);
      expect(content).toContain("sendDefaultPii = false");
    });
  });

  describe("IP address is stripped in beforeSend on every platform", () => {
    it("backend strips ipAddress in setBeforeSend", () => {
      const content = readSource(backendApp);
      expect(content).toContain("setBeforeSend");
      expect(content).toMatch(/ipAddress\s*=\s*null/);
    });

    it("web strips ip_address in beforeSend", () => {
      const content = readSource(webMain);
      expect(content).toContain("beforeSend");
      expect(content).toContain("ip_address");
    });

    it("android strips ipAddress in setBeforeSend", () => {
      const content = readSource(androidApplication);
      expect(content).toContain("setBeforeSend");
      expect(content).toMatch(/ipAddress\s*=\s*null/);
    });

    it("iOS strips ipAddress in beforeSend", () => {
      const content = readSource(iosSentryConfig);
      expect(content).toContain("beforeSend");
      expect(content).toMatch(/ipAddress\s*=\s*nil/);
    });
  });

  describe("session replays are disabled on web", () => {
    it("replaysSessionSampleRate is 0", () => {
      const content = readSource(webMain);
      expect(content).toMatch(/replaysSessionSampleRate\s*:\s*0/);
    });

    it("replaysOnErrorSampleRate is 0", () => {
      const content = readSource(webMain);
      expect(content).toMatch(/replaysOnErrorSampleRate\s*:\s*0/);
    });
  });

  describe("backend serverName is generic, not a real hostname", () => {
    it("serverName is set to a hardcoded string, not a system call", () => {
      const content = readSource(backendApp);
      expect(content).toMatch(/serverName\s*=\s*"tday-backend"/);
    });
  });

  describe("DSNs come from environment variables, never hardcoded in source", () => {
    it("backend reads DSN from AppConfig (env-backed)", () => {
      const content = readSource(backendApp);
      expect(content).toContain("config.sentryDsn");
      expect(content).not.toMatch(/options\.dsn\s*=\s*"https:\/\//);
    });

    it("web reads DSN from VITE_SENTRY_DSN env", () => {
      const content = readSource(webMain);
      expect(content).toContain("import.meta.env.VITE_SENTRY_DSN");
      expect(content).not.toMatch(/dsn\s*:\s*"https:\/\//);
    });

    it("android reads DSN from BuildConfig (gradle property / env)", () => {
      const content = readSource(androidApplication);
      expect(content).toContain("BuildConfig.SENTRY_DSN");
      const gradle = readSource(androidGradle);
      expect(gradle).toMatch(/sentryDsn.*System\.getenv/s);
    });

    it("iOS reads DSN from Info.plist bundle key", () => {
      const content = readSource(iosSentryConfig);
      expect(content).toMatch(/Bundle\.main.*SENTRY_DSN/);
    });
  });

  describe("no Sentry DSN strings appear in committed source files", () => {
    const SENTRY_DSN_PATTERN = /https:\/\/[a-f0-9]{32}@[a-z0-9.]+\.sentry\.io\/\d+/;

    const filesToCheck = [
      backendApp, webMain, androidApplication, iosSentryConfig,
      backendRouting, webRouter, webApiClient,
    ];

    it.each(filesToCheck.filter(existsSync))(
      "%s should not contain a hardcoded Sentry DSN",
      (file) => {
        const content = readSource(file);
        expect(content).not.toMatch(SENTRY_DSN_PATTERN);
      },
    );
  });
});

describe("sentry exception capture coverage", () => {
  it("backend StatusPages captures exceptions to Sentry", () => {
    const content = readSource(backendStatusPages);
    expect(content).toContain("Sentry.captureException");
  });

  it("backend SentryRequestPlugin exists for transaction tracing", () => {
    expect(existsSync(backendSentryPlugin)).toBe(true);
    const content = readSource(backendSentryPlugin);
    expect(content).toContain("SentryRequestPlugin");
    expect(content).toContain("startTransaction");
  });

  it("backend Application.kt installs SentryRequestPlugin", () => {
    const content = readSource(backendApp);
    expect(content).toContain("SentryRequestPlugin");
  });

  it("backend logback.xml includes Sentry appender", () => {
    if (!existsSync(backendLogback)) return;
    const content = readSource(backendLogback);
    expect(content).toMatch(/[Ss]entry/i);
  });

  it("web ErrorBoundary captures exceptions to Sentry", () => {
    const content = readSource(webErrorBoundary);
    expect(content).toContain("Sentry.captureException");
  });

  it("web router is wrapped with Sentry instrumentation", () => {
    const content = readSource(webRouter);
    expect(content).toContain("wrapCreateBrowserRouterV7");
  });

  it("web API client adds Sentry breadcrumbs on errors", () => {
    const content = readSource(webApiClient);
    expect(content).toContain("Sentry.addBreadcrumb");
  });

  it("android uses SentryOkHttpInterceptor for HTTP tracing", () => {
    if (!existsSync(androidNetworkModule)) return;
    const content = readSource(androidNetworkModule);
    expect(content).toContain("SentryOkHttpInterceptor");
  });

  it("android disables Sentry auto-init to prevent DSN-less crash", () => {
    const content = readSource(androidManifest);
    expect(content).toContain('io.sentry.auto-init');
    expect(content).toContain('android:value="false"');
  });
});

describe("sentry conditional upload in CI", () => {
  it("backend Sentry source upload is conditional on SENTRY_AUTH_TOKEN", () => {
    const content = readSource(backendGradle);
    expect(content).toContain("SENTRY_AUTH_TOKEN");
    expect(content).toMatch(/includeSourceContext\s*=.*SENTRY_AUTH_TOKEN/);
  });

  it("android Sentry uploads are conditional on SENTRY_AUTH_TOKEN", () => {
    const content = readSource(androidGradle);
    expect(content).toContain("SENTRY_AUTH_TOKEN");
    expect(content).toMatch(/autoUploadProguardMapping\s*=.*hasSentryAuth/);
  });
});

describe("sentry documentation", () => {
  it("docs/TELEMETRY.md exists", () => {
    expect(existsSync(telemetryDoc)).toBe(true);
  });

  it("TELEMETRY.md documents what is collected", () => {
    const content = readSource(telemetryDoc);
    expect(content).toContain("What Is Collected");
    expect(content).toContain("Stack trace");
  });

  it("TELEMETRY.md documents what is NOT collected", () => {
    const content = readSource(telemetryDoc);
    expect(content).toContain("What Is NOT Collected");
    expect(content).toContain("sendDefaultPii = false");
  });

  it("TELEMETRY.md documents self-hosted no-op behavior", () => {
    const content = readSource(telemetryDoc);
    expect(content).toContain("Self-Hosted");
    expect(content).toContain("SENTRY_DSN");
  });

  it("TELEMETRY.md lists all four platform init locations", () => {
    const content = readSource(telemetryDoc);
    expect(content).toContain("Application.kt");
    expect(content).toContain("TdayApplication.kt");
    expect(content).toContain("main.tsx");
    expect(content).toContain("SentryConfiguration.swift");
  });
});

describe("no debug/test Sentry endpoints in production code", () => {
  it("backend routing should not contain debug-sentry endpoint", () => {
    const content = readSource(backendRouting);
    expect(content).not.toContain("debug-sentry");
  });
});

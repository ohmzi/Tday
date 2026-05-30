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
const backendObservability = path.join(BACKEND_SRC, "observability", "TdayObservability.kt");
const backendRouting = path.join(BACKEND_SRC, "plugins", "Routing.kt");
const backendGradle = path.join(MONO, "tday-backend", "build.gradle.kts");
const backendLogback = path.join(
  MONO, "tday-backend", "src", "main", "resources", "logback.xml",
);

// ─── Web Sentry paths ──────────────────────────────────────────────
const webMain = path.join(ROOT, "src", "main.tsx");
const webRouter = path.join(ROOT, "src", "router.tsx");
const webApiClient = path.join(ROOT, "src", "lib", "api-client.ts");
const webObservability = path.join(ROOT, "src", "lib", "observability", "sentry.ts");
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
const androidTelemetry = path.join(
  ANDROID_SRC, "java", "com", "ohmz", "tday", "compose", "core", "observability", "TdayTelemetry.kt",
);
const androidTodoListViewModel = path.join(
  ANDROID_SRC, "java", "com", "ohmz", "tday", "compose", "feature", "todos", "TodoListViewModel.kt",
);
const androidCalendarViewModel = path.join(
  ANDROID_SRC, "java", "com", "ohmz", "tday", "compose", "feature", "calendar", "CalendarViewModel.kt",
);
const androidCalendarScreen = path.join(
  ANDROID_SRC, "java", "com", "ohmz", "tday", "compose", "feature", "calendar", "CalendarScreen.kt",
);
const androidCredentialService = path.join(
  ANDROID_SRC, "java", "com", "ohmz", "tday", "compose", "core", "data", "auth", "SystemCredentialService.kt",
);

// ─── iOS Sentry paths ──────────────────────────────────────────────
const iosSentryConfig = path.join(IOS_SRC, "Core", "SentryConfiguration.swift");
const iosApp = path.join(IOS_SRC, "TdayApp.swift");
const iosInfoPlist = path.join(IOS_SRC, "Info.plist");
const iosProject = path.join(MONO, "ios-swiftUI", "project.yml");
const iosTodoListViewModel = path.join(IOS_SRC, "Feature", "Todos", "TodoListViewModel.swift");
const iosCalendarViewModel = path.join(IOS_SRC, "Feature", "Calendar", "CalendarViewModel.swift");
const iosCalendarScreen = path.join(IOS_SRC, "Feature", "Calendar", "CalendarScreen.swift");
const iosCredentialService = path.join(IOS_SRC, "Core", "Data", "Auth", "SystemCredentialService.swift");

// ─── Documentation ─────────────────────────────────────────────────
const telemetryDoc = path.join(MONO, "docs", "TELEMETRY.md");
const codingStandardsDoc = path.join(MONO, "docs", "CODING_STANDARDS.md");
const agentsDoc = path.join(MONO, "AGENTS.md");

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
      expect(readSource(webMain)).toContain("beforeSend: scrubSentryEvent");
      const helper = readSource(webObservability);
      expect(helper).toContain("ip_address");
      expect(helper).toContain("scrubSentryEvent");
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

  describe("web automatic breadcrumbs are privacy filtered", () => {
    it("disables console and DOM breadcrumbs and sanitizes remaining breadcrumbs", () => {
      const main = readSource(webMain);
      expect(main).toContain("beforeBreadcrumb: scrubSentryBreadcrumb");
      expect(main).toContain("breadcrumbsIntegration");
      expect(main).toContain("console: false");
      expect(main).toContain("dom: false");

      const helper = readSource(webObservability);
      expect(helper).toContain("scrubSentryBreadcrumb");
      expect(helper).toContain('breadcrumb.category === "console"');
      expect(helper).toContain('breadcrumb.category?.startsWith("ui.")');
      expect(helper).toContain("SENSITIVE_LABEL_PATTERN");
    });

    it("sanitizes browser transaction names before sending", () => {
      expect(readSource(webMain)).toContain("beforeSendTransaction: scrubSentryTransaction");
      expect(readSource(webObservability)).toContain("scrubSentryTransaction");
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
      expect(content).toContain('bundleString("SENTRY_DSN")');
      expect(readSource(iosInfoPlist)).toContain("<key>SENTRY_DSN</key>");
      expect(readSource(iosProject)).toContain("SENTRY_DSN");
    });
  });

  describe("no Sentry DSN strings appear in committed source files", () => {
    const SENTRY_DSN_PATTERN = /https:\/\/[a-f0-9]{32}@[a-z0-9.]+\.sentry\.io\/\d+/;

    const filesToCheck = [
      backendApp, webMain, androidApplication, iosSentryConfig,
      backendRouting, webRouter, webApiClient, backendObservability,
      webObservability, androidTelemetry,
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
    expect(content).toContain("TdayObservability.captureException");
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
    expect(readSource(webErrorBoundary)).toContain("captureUiException");
    expect(readSource(webObservability)).toContain("Sentry.captureException");
  });

  it("web router is wrapped with Sentry instrumentation", () => {
    const content = readSource(webRouter);
    expect(content).toContain("wrapCreateBrowserRouterV7");
  });

  it("web API client adds Sentry breadcrumbs on errors", () => {
    const content = readSource(webApiClient);
    expect(content).toContain("addApiErrorBreadcrumb");
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

  it("platform observability helpers exist", () => {
    expect(existsSync(backendObservability)).toBe(true);
    expect(existsSync(webObservability)).toBe(true);
    expect(existsSync(androidTelemetry)).toBe(true);
    expect(readSource(iosSentryConfig)).toContain("enum TdayTelemetry");
  });
});

describe("sentry sampling and route sanitization", () => {
  it("trace sample rates are environment configurable", () => {
    expect(readSource(backendApp)).toContain("sentryTracesSampleRate");
    expect(readSource(webMain)).toContain("VITE_SENTRY_TRACES_SAMPLE_RATE");
    expect(readSource(androidApplication)).toContain("SENTRY_TRACES_SAMPLE_RATE");
    expect(readSource(iosSentryConfig)).toContain("SENTRY_TRACES_SAMPLE_RATE");
  });

  it("web restricts trace propagation to API routes", () => {
    const content = readSource(webMain);
    expect(content).toContain("tracePropagationTargets");
    expect(content).toContain("/^\\/api");
  });

  it("breadcrumbs and transaction names use sanitized route helpers", () => {
    expect(readSource(backendSentryPlugin)).toContain("routeTemplate");
    expect(readSource(webObservability)).toContain("sanitizeTelemetryUrl");
    expect(readSource(webObservability)).toContain("sanitizeTelemetryLabel");
    expect(readSource(androidTelemetry)).toContain("sanitizePath");
    expect(readSource(iosSentryConfig)).toContain("sanitizePath");
  });
});

describe("no product analytics vendor SDKs", () => {
  it("web dependencies do not include analytics SDKs", () => {
    const pkg = readSource(webPackageJson);
    expect(pkg).not.toMatch(/google-analytics|gtag|@analytics|mixpanel|amplitude|dynatrace/i);
  });

  it("native/backend manifests do not include GA or Dynatrace SDKs", () => {
    const files = [
      backendGradle,
      androidGradle,
      path.join(MONO, "ios-swiftUI", "Package.swift"),
    ];
    for (const file of files.filter(existsSync)) {
      const content = readSource(file);
      expect(content).not.toMatch(/google-analytics|firebase-analytics|dynatrace|mixpanel|amplitude/i);
    }
  });
});

describe("post-Sentry diagnostic coverage is structural", () => {
  it("mobile task and list operations use structural breadcrumb names", () => {
    for (const file of [androidTodoListViewModel, iosTodoListViewModel]) {
      const content = readSource(file);
      expect(content).toContain("task.create");
      expect(content).toContain("task.reschedule");
      expect(content).toContain("list.update");
      expect(content).toContain("has_description");
      expect(content).not.toMatch(/addBreadcrumb\([^)]*title/i);
      expect(content).not.toMatch(/addBreadcrumb\([^)]*description/i);
    }
  });

  it("mobile calendar paging and drag-reschedule use structural breadcrumbs", () => {
    for (const file of [androidCalendarScreen, iosCalendarScreen]) {
      const content = readSource(file);
      expect(content).toContain("calendar.page");
      expect(content).toContain("calendar.mode");
      expect(content).toContain("calendar.drag_reschedule");
      expect(content).toContain("direction");
      expect(content).not.toMatch(/addBreadcrumb\([^)]*selectedDate/i);
      expect(content).not.toMatch(/addBreadcrumb\([^)]*targetDate/i);
    }

    for (const file of [androidCalendarViewModel, iosCalendarViewModel]) {
      const content = readSource(file);
      expect(content).toContain("calendar.task.create");
      expect(content).toContain("calendar.task.reschedule");
      expect(content).toContain("scheduled_items");
    }
  });

  it("mobile credential manager diagnostics avoid credential values", () => {
    for (const file of [androidCredentialService, iosCredentialService]) {
      const content = readSource(file);
      expect(content).toContain("credential.request");
      expect(content).toContain("credential.save");
      expect(content).toContain("server_url");
      expect(content).not.toMatch(/addBreadcrumb\([^)]*email/i);
      expect(content).not.toMatch(/addBreadcrumb\([^)]*password/i);
      expect(content).not.toMatch(/addBreadcrumb\([^)]*serverUrl/i);
      expect(content).not.toMatch(/addBreadcrumb\([^)]*rawURL/i);
    }
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

  it("docs require the new feature observability checklist", () => {
    expect(readSource(telemetryDoc)).toContain("New Feature Observability Checklist");
    expect(readSource(codingStandardsDoc)).toContain("New Feature Observability Checklist");
    expect(readSource(agentsDoc)).toContain("New Feature Observability Checklist");
  });

  it("TELEMETRY.md documents industry reference baselines", () => {
    const content = readSource(telemetryDoc);
    expect(content).toContain("Industry Reference Baseline");
    expect(content).toContain("docs.sentry.io");
    expect(content).toContain("support.google.com/analytics");
    expect(content).toContain("docs.dynatrace.com");
    expect(content).toContain("opentelemetry.io");
  });

  it("TELEMETRY.md documents post-Sentry feature coverage", () => {
    const content = readSource(telemetryDoc);
    for (const expected of [
      "Local Mode",
      "Floater / Anytime tasks",
      "Offline sync replay",
      "Credential manager / password autofill",
      "Mobile probe and version gate",
      "Realtime reconnect",
      "Calendar paging",
      "Task/list drag-reschedule",
      "security.event",
    ]) {
      expect(content).toContain(expected);
    }
  });
});

describe("no debug/test Sentry endpoints in production code", () => {
  it("backend routing should not contain debug-sentry endpoint", () => {
    const content = readSource(backendRouting);
    expect(content).not.toContain("debug-sentry");
  });
});

import { readFileSync, readdirSync, existsSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");

// Directory and extension names this file joins or filters by repeatedly, named so the file holds
// one occurrence of each instead of one per call site.
const SRC_DIR = "src";
const LIB_DIR = "lib";
const TS_EXT = ".ts";
const TSX_EXT = ".tsx";
const BACKEND_DIR = "tday-backend";
const MAIN_DIR = "main";
const COM_DIR = "com";
const OHMZ_DIR = "ohmz";
const TDAY_DIR = "tday";
const ANDROID_DIR = "android-compose";
const OBSERVABILITY_DIR = "observability";
const KT_EXT = ".kt";
const IOS_DIR = "ios-swiftUI";
const BUILD_GRADLE = "build.gradle.kts";
const JAVA_DIR = "java";
const COMPOSE_DIR = "compose";
const ANDROID_CORE_DIR = "core";
const IOS_CORE_DIR = "Core";
const SWIFT_EXT = ".swift";

const BACKEND_SRC = path.join(
  MONO, BACKEND_DIR, SRC_DIR, MAIN_DIR, "kotlin", COM_DIR, OHMZ_DIR, TDAY_DIR,
);
const ANDROID_SRC = path.join(
  MONO, ANDROID_DIR, "app", SRC_DIR, MAIN_DIR,
);
const IOS_SRC = path.join(MONO, IOS_DIR, "Tday");

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

// Directories that hold build output or dependencies, never source a guardrail should read.
const SKIPPED_DIRS = new Set(["node_modules", "build", "dist", ".gradle", "DerivedData", ".build"]);

function listSources(dir: string, extensions: string[]): string[] {
  if (!existsSync(dir)) return [];
  const files: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (SKIPPED_DIRS.has(entry.name)) continue;
    const child = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...listSources(child, extensions));
    } else if (extensions.some((extension) => entry.name.endsWith(extension))) {
      files.push(child);
    }
  }
  return files;
}

// Source with its comments removed, so a doc comment that merely names a call ("manual
// `SentryAndroid.init`") is not counted as the call. A `//` right after a colon, a quote or a
// backslash is part of a URL, a string or a regex literal and is left alone.
function withoutComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/(^|[^:"'\\])\/\/.*$/gm, "$1");
}

function repoPath(file: string): string {
  return path.relative(MONO, file).split(path.sep).join("/");
}

// Every match of `pattern` in the comment-free source of `dirs`, one entry per match, as repo
// relative paths. A file that calls twice is listed twice, which is what "exactly once" needs.
function callSites(dirs: string[], extensions: string[], pattern: RegExp): string[] {
  const global = new RegExp(pattern.source, "g");
  const sites: string[] = [];
  for (const file of dirs.flatMap((dir) => listSources(dir, extensions))) {
    const matches = withoutComments(readSource(file)).match(global) ?? [];
    for (let i = 0; i < matches.length; i += 1) sites.push(repoPath(file));
  }
  return sites.sort();
}

// The text from `from` up to the next `to` after it. It throws when either anchor is missing, so a
// renamed anchor fails the test instead of leaving an empty slice to satisfy a `not` assertion.
function between(content: string, from: string, to: string): string {
  const start = content.indexOf(from);
  if (start === -1) throw new Error(`"${from}" not found`);
  const end = content.indexOf(to, start + from.length);
  if (end === -1) throw new Error(`"${to}" not found after "${from}"`);
  return content.slice(start, end);
}

// ─── Backend Sentry paths ───────────────────────────────────────────
const backendApp = path.join(BACKEND_SRC, "Application.kt");
const backendSentry = path.join(BACKEND_SRC, OBSERVABILITY_DIR, "BackendSentry.kt");
const backendGate = path.join(BACKEND_SRC, OBSERVABILITY_DIR, "TelemetryGate.kt");
const backendGatedTransport = path.join(BACKEND_SRC, OBSERVABILITY_DIR, "GatedTransport.kt");
const backendScrubber = path.join(BACKEND_SRC, OBSERVABILITY_DIR, "TelemetryScrubber.kt");
const backendStatusPages = path.join(BACKEND_SRC, "plugins", "StatusPages.kt");
const backendSentryPlugin = path.join(BACKEND_SRC, "plugins", "SentryPlugin.kt");
const backendObservability = path.join(BACKEND_SRC, OBSERVABILITY_DIR, "TdayObservability.kt");
const backendRouting = path.join(BACKEND_SRC, "plugins", "Routing.kt");
const backendAdminRoutes = path.join(BACKEND_SRC, "routes", "AdminRoutes.kt");
const backendInstanceSettings = path.join(BACKEND_SRC, "services", "InstanceSettingsService.kt");
const backendAppConfig = path.join(BACKEND_SRC, "config", "AppConfig.kt");
const backendMigration = path.join(
  MONO, BACKEND_DIR, SRC_DIR, MAIN_DIR, "resources", "db", "migration", "V32__instance_settings.sql",
);
const backendGradle = path.join(MONO, BACKEND_DIR, BUILD_GRADLE);
const backendLogback = path.join(
  MONO, BACKEND_DIR, SRC_DIR, MAIN_DIR, "resources", "logback.xml",
);

// ─── Web Sentry paths ──────────────────────────────────────────────
const webSrc = path.join(ROOT, SRC_DIR);
const webMain = path.join(ROOT, SRC_DIR, "main.tsx");
const webRouter = path.join(ROOT, SRC_DIR, "router.tsx");
const webApiClient = path.join(ROOT, SRC_DIR, LIB_DIR, "api-client.ts");
const webObservability = path.join(ROOT, SRC_DIR, LIB_DIR, OBSERVABILITY_DIR, "sentry.ts");
const webSentryInit = path.join(ROOT, SRC_DIR, LIB_DIR, OBSERVABILITY_DIR, "sentryInit.ts");
const webScrub = path.join(ROOT, SRC_DIR, LIB_DIR, OBSERVABILITY_DIR, "webScrub.ts");
const webConsent = path.join(ROOT, SRC_DIR, LIB_DIR, "privacy", "instanceTelemetry.ts");
const webAuthProvider = path.join(ROOT, SRC_DIR, "providers", "AuthProvider.tsx");
const webErrorBoundary = path.join(ROOT, SRC_DIR, "components", "ErrorBoundary.tsx");
const webViteConfig = path.join(ROOT, "vite.config.ts");
const webPackageJson = path.join(ROOT, "package.json");
const webPackageLock = path.join(ROOT, "package-lock.json");

// ─── Android Sentry paths ──────────────────────────────────────────
const ANDROID_DEBUG_SRC = path.join(MONO, ANDROID_DIR, "app", SRC_DIR, "debug");
const ANDROID_COMPOSE = path.join(ANDROID_SRC, JAVA_DIR, COM_DIR, OHMZ_DIR, TDAY_DIR, COMPOSE_DIR);
const androidObservability = path.join(ANDROID_COMPOSE, ANDROID_CORE_DIR, OBSERVABILITY_DIR);
const androidApplication = path.join(ANDROID_COMPOSE, "TdayApplication.kt");
const androidBootstrap = path.join(androidObservability, "TelemetryBootstrap.kt");
const androidOptions = path.join(androidObservability, "TelemetryOptions.kt");
const androidScrubber = path.join(androidObservability, "TelemetryScrubber.kt");
const androidConsentStore = path.join(androidObservability, "TelemetryConsentStore.kt");
const androidGatedTransport = path.join(androidObservability, "GatedTransport.kt");
const androidManifest = path.join(ANDROID_SRC, "AndroidManifest.xml");
const androidGradle = path.join(MONO, ANDROID_DIR, "app", BUILD_GRADLE);
const androidNetworkModule = path.join(ANDROID_COMPOSE, ANDROID_CORE_DIR, "network", "NetworkModule.kt");
const androidTelemetry = path.join(androidObservability, "TdayTelemetry.kt");
const androidOfflineCacheManager = path.join(
  ANDROID_COMPOSE, ANDROID_CORE_DIR, "data", "cache", "OfflineCacheManager.kt",
);
const androidSecureConfigStore = path.join(ANDROID_COMPOSE, ANDROID_CORE_DIR, "data", "SecureConfigStore.kt");
const androidTodoListViewModel = path.join(
  ANDROID_SRC, JAVA_DIR, COM_DIR, OHMZ_DIR, TDAY_DIR, COMPOSE_DIR, "feature", "todos", "TodoListViewModel.kt",
);
const androidCalendarViewModel = path.join(
  ANDROID_SRC, JAVA_DIR, COM_DIR, OHMZ_DIR, TDAY_DIR, COMPOSE_DIR, "feature", "calendar", "CalendarViewModel.kt",
);
const androidCalendarScreen = path.join(
  ANDROID_SRC, JAVA_DIR, COM_DIR, OHMZ_DIR, TDAY_DIR, COMPOSE_DIR, "feature", "calendar", "CalendarScreen.kt",
);
const androidCredentialService = path.join(
  ANDROID_SRC, JAVA_DIR, COM_DIR, OHMZ_DIR, TDAY_DIR, COMPOSE_DIR, ANDROID_CORE_DIR, "data", "auth", "SystemCredentialService.kt",
);

// ─── iOS Sentry paths ──────────────────────────────────────────────
// Every app, extension and widget target. A second target that linked Sentry would need its own
// start call, and that call has to be found here.
const IOS_TARGET_DIRS = ["Tday", "TdayShareExtension", "TdayWatch", "TdayWatchWidget", "TdayWidget"].map(
  (dir) => path.join(MONO, IOS_DIR, dir),
);
const iosSentryConfig = path.join(IOS_SRC, IOS_CORE_DIR, "SentryConfiguration.swift");
const iosApp = path.join(IOS_SRC, "TdayApp.swift");
const iosInfoPlist = path.join(IOS_SRC, "Info.plist");
const iosProject = path.join(MONO, IOS_DIR, "project.yml");
const iosPbxproj = path.join(MONO, IOS_DIR, "TdayApp.xcodeproj", "project.pbxproj");
const iosPackage = path.join(MONO, IOS_DIR, "Package.swift");
const iosPackageResolved = path.join(MONO, IOS_DIR, "Package.resolved");
const iosScrubber = path.join(IOS_SRC, IOS_CORE_DIR, "Telemetry", "TelemetryScrubber.swift");
const iosConsentStore = path.join(IOS_SRC, IOS_CORE_DIR, "Telemetry", "TelemetryConsentStore.swift");
const iosAuthRepository = path.join(IOS_SRC, IOS_CORE_DIR, "Data", "Auth", "AuthRepository.swift");
const iosTodoListViewModel = path.join(IOS_SRC, "Feature", "Todos", "TodoListViewModel.swift");
const iosCalendarViewModel = path.join(IOS_SRC, "Feature", "Calendar", "CalendarViewModel.swift");
const iosCalendarScreen = path.join(IOS_SRC, "Feature", "Calendar", "CalendarScreen.swift");
const iosCredentialService = path.join(IOS_SRC, IOS_CORE_DIR, "Data", "Auth", "SystemCredentialService.swift");

// ─── Documentation ─────────────────────────────────────────────────
const telemetryDoc = path.join(MONO, "docs", "TELEMETRY.md");
const codingStandardsDoc = path.join(MONO, "docs", "CODING_STANDARDS.md");
const agentsDoc = path.join(MONO, "AGENTS.md");

// ─── Product analytics and competing crash reporters ───────────────
// Kept in step with `scripts/observability-smoke.mjs`. Exact npm package names and whole scopes,
// because the lockfile lists every transitive package and a substring match flags
// `es-set-tostringtag` for "gtag" and `workbox-google-analytics` (a service-worker helper that
// talks to nothing unless someone calls it) for "google-analytics".
const ANALYTICS_PACKAGE_NAMES = new Set([
  "analytics", "analytics-node", "ga-gtag", "gtag", "gtag.js", "google-analytics",
  "universal-analytics", "react-ga", "react-ga4", "react-gtm-module", "vue-gtag",
  "mixpanel", "mixpanel-browser", "amplitude-js", "posthog-js", "posthog-node",
  "logrocket", "dynatrace", "firebase", "bugsnag-js", "rollbar", "raygun4js", "trackjs",
  "@types/gtag.js", "@types/google.analytics",
]);
const ANALYTICS_PACKAGE_SCOPES = [
  "@analytics/", "@amplitude/", "@bugsnag/", "@datadog/", "@dynatrace/", "@dynatrace-sdk/",
  "@firebase/", "@fullstory/", "@google-analytics/", "@highlight-run/", "@honeybadger-io/",
  "@mixpanel/", "@posthog/", "@rollbar/", "@segment/",
];

function isAnalyticsPackage(name: string): boolean {
  return (
    ANALYTICS_PACKAGE_NAMES.has(name) ||
    ANALYTICS_PACKAGE_SCOPES.some((scope) => name.startsWith(scope))
  );
}

// A lockfile key is `node_modules/<name>`, nested as `node_modules/a/node_modules/<name>`.
function lockfilePackageNames(lockfile: { packages?: Record<string, unknown> }): string[] {
  const marker = "node_modules/";
  return Object.keys(lockfile.packages ?? {})
    .filter((key) => key !== "")
    .map((key) => {
      const index = key.lastIndexOf(marker);
      return index === -1 ? key : key.slice(index + marker.length);
    });
}

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

    it("iOS stays on the sentry-cocoa 9.x line", () => {
      // App hang tracking is deprecated in 9.29 and gone in v10, where the hang and swizzling
      // options change shape. A major bump has to be a reviewed change to
      // `SentryConfiguration.makeOptions`, not a resolved-version drift.
      expect(readSource(iosPackage)).toMatch(/sentry-cocoa",\s*from:\s*"9\./);
      const resolved = JSON.parse(readSource(iosPackageResolved)) as {
        pins: { identity: string; state: { version: string } }[];
      };
      const pin = resolved.pins.find((candidate) => candidate.identity === "sentry-cocoa");
      expect(pin?.state.version).toMatch(/^9\./);
    });
  });

  describe("Sentry initialization exists on every platform", () => {
    it("backend Application.kt hands the gate to BackendSentry.init", () => {
      expect(readSource(backendApp)).toContain("BackendSentry.init(");
      expect(readSource(backendSentry)).toContain("Sentry.init");
    });

    it("web main.tsx starts Sentry through initSentryIfConsented", () => {
      expect(readSource(webMain)).toContain("initSentryIfConsented()");
      expect(readSource(webSentryInit)).toContain("Sentry.init(");
    });

    it("android TdayApplication.kt starts TelemetryBootstrap, which calls SentryAndroid.init", () => {
      expect(readSource(androidApplication)).toContain("TelemetryBootstrap.shared(this).start()");
      expect(readSource(androidBootstrap)).toContain("SentryAndroid.init");
    });

    it("iOS TdayApp.swift triggers SentryConfiguration.start()", () => {
      const app = readSource(iosApp);
      expect(app).toContain("SentryConfiguration.start()");
      const config = readSource(iosSentryConfig);
      expect(config).toContain("SentrySDK.start");
    });
  });
});

describe("sentry starts from exactly one consent-gated file per platform", () => {
  describe("web", () => {
    it("calls Sentry.init once under src, in sentryInit.ts", () => {
      expect(callSites([webSrc], [TS_EXT, TSX_EXT], /\bSentry\.init\(/)).toEqual([
        "tday-web/src/lib/observability/sentryInit.ts",
      ]);
    });

    it("has no other way to initialise the SDK", () => {
      // `import { init } from "@sentry/react"` starts it without ever saying `Sentry.init(`.
      expect(
        callSites([webSrc], [TS_EXT, TSX_EXT], /import\s*\{[^}]*\binit(?:AndBind)?\b[^}]*\}\s*from\s*["']@sentry\//),
      ).toEqual([]);
      expect(
        callSites([webSrc], [TS_EXT, TSX_EXT], /\bSentry\.initAndBind\(|\bnew\s+BrowserClient\(/),
      ).toEqual([]);
    });

    it("asks for consent and a configured DSN before it initialises", () => {
      const content = withoutComments(readSource(webSentryInit));
      const guard = between(content, "export function startSentry", "Sentry.init(");
      expect(guard).toContain("isTelemetryGranted()");
      expect(guard).toContain("isCrashReportingConfigured()");
    });

    it("main.tsx starts it only through initSentryIfConsented, before React renders", () => {
      const main = withoutComments(readSource(webMain));
      expect(main).toContain("initSentryIfConsented()");
      expect(main.indexOf("initSentryIfConsented()")).toBeLessThan(main.indexOf("createRoot("));
    });

    it("gates the transport, beforeSend and beforeBreadcrumb on consent", () => {
      const content = withoutComments(readSource(webSentryInit));
      expect(content).toMatch(/transport:\s*makeGatedTransport\(/);
      expect(content).toMatch(/isOpen\(\)\s*\?\s*transport\.send\(envelope\)/);
      expect(between(content, "beforeBreadcrumb:", "beforeSend:")).toContain("deps.isGranted()");
      expect(between(content, "beforeSend:", "scrubWebEvent")).toContain("!deps.isGranted()");
      expect(content).toContain("predatesConsent(event, deps.consentAt())");
    });

    it("asks the server for the instance answer before it starts anything", () => {
      const init = withoutComments(readSource(webSentryInit));
      const boot = between(init, "export function initSentryIfConsented", "return unsubscribe");
      expect(boot).toContain("refreshInstanceTelemetry()");
      // A change the admin makes in another browser reaches this page when it returns to view.
      expect(boot).toContain("watchInstanceTelemetry()");

      const consent = withoutComments(readSource(webConsent));
      expect(consent).toContain('INSTANCE_TELEMETRY_URL = "/api/instance/telemetry"');
      // The foreground re-read is a no-op in Local Mode: there is no server being talked to. And it
      // only re-reads — an answer is the only thing that can open the gate.
      const watcher = between(consent, "export function watchInstanceTelemetry", "addEventListener");
      expect(watcher).toContain("isLocalMode()");
      expect(watcher).toContain("refreshInstanceTelemetry()");
      expect(watcher).not.toContain("applyInstanceTelemetry(");
    });

    it("has no per-user consent surface left", () => {
      // One admin answer for the instance: no card, no browser switch, no browser store.
      for (const removed of [
        path.join(webSrc, "components", "privacy", "CrashReportsConsentGate.tsx"),
        path.join(webSrc, "hooks", "useTelemetryConsent.ts"),
        path.join(webSrc, LIB_DIR, "privacy", "telemetryConsent.ts"),
      ]) {
        expect(existsSync(removed), `${repoPath(removed)} should be gone`).toBe(false);
      }
      expect(
        callSites([webSrc], [TS_EXT, TSX_EXT], /CrashReportsConsentGate|useTelemetryConsent/),
      ).toEqual([]);
    });
  });

  describe("android", () => {
    it("calls SentryAndroid.init once, in TelemetryBootstrap.kt", () => {
      expect(
        callSites([ANDROID_SRC, ANDROID_DEBUG_SRC], [KT_EXT, ".java"], /\b(?:SentryAndroid|Sentry)\.init\(/),
      ).toEqual([
        "android-compose/app/src/main/java/com/ohmz/tday/compose/core/observability/TelemetryBootstrap.kt",
      ]);
    });

    it("starts the SDK only for a granted answer on a build with a DSN", () => {
      const content = withoutComments(readSource(androidBootstrap));
      const start = between(content, "fun start()", "fun apply(");
      expect(start).toContain("isAvailable");
      expect(start).toContain("TelemetryConsentState.GRANTED");
      // The gate opens before the SDK starts: it files events of its own while starting up.
      expect(between(content, "private fun startSdk", "runCatching { sdk.start(")).toContain("gate.open()");
    });

    it("gates the transport, beforeSend and beforeBreadcrumb on the in-memory gate", () => {
      const content = withoutComments(readSource(androidOptions));
      expect(content).toContain("setTransportFactory(GatedTransportFactory(gate))");
      expect(between(content, "setBeforeSend {", "setBeforeBreadcrumb {")).toContain("!gate.isOpen -> null");
      expect(between(content, "setBeforeSend {", "setBeforeBreadcrumb {")).toContain(
        "predatesConsent(event.timestamp.time, grantedAtMs) -> null",
      );
      expect(between(content, "setBeforeBreadcrumb {", "setBeforeSendTransaction")).toContain("gate.isOpen");
    });

    it("spells out every ITransport method, so no default method can go around the gate", () => {
      // Kotlin `by` delegation would forward the interface's default `send(envelope)` straight to
      // the wrapped transport.
      const content = withoutComments(readSource(androidGatedTransport));
      expect(content).not.toMatch(/ITransport\s+by\b/);
      expect(content).toMatch(/override fun send\(envelope: SentryEnvelope, hint: Hint\)/);
      expect(content).toMatch(/override fun send\(envelope: SentryEnvelope\)/);
    });
  });

  describe("iOS", () => {
    it("calls SentrySDK.start once, in SentryConfiguration.swift", () => {
      expect(callSites(IOS_TARGET_DIRS, [SWIFT_EXT], /\bSentrySDK\.start\(/)).toEqual([
        "ios-swiftUI/Tday/Core/SentryConfiguration.swift",
      ]);
    });

    it("starts the SDK only for a granted answer on a build with a DSN", () => {
      const content = withoutComments(readSource(iosSentryConfig));
      const guard = between(content, "static func start(", "SentrySDK.start(");
      expect(guard).toContain("guard isConfigured, consent.isGranted");
      expect(guard).toContain("TelemetryLifecycle.purge()");
      expect(guard).toContain("TelemetryGate.shared.open()");
    });

    it("checks the gate first in beforeBreadcrumb and beforeSend", () => {
      const content = withoutComments(readSource(iosSentryConfig));
      const callbacks = between(content, "options.beforeBreadcrumb", "return options");
      expect(callbacks.match(/guard gate\.isOpen/g)).toHaveLength(2);
    });
  });

  describe("backend", () => {
    it("calls Sentry.init once, in BackendSentry.kt", () => {
      expect(callSites([path.join(MONO, BACKEND_DIR, SRC_DIR, MAIN_DIR)], [KT_EXT], /\bSentry\.init\s*[({]/)).toEqual([
        "tday-backend/src/main/kotlin/com/ohmz/tday/observability/BackendSentry.kt",
      ]);
    });

    it("hands BackendSentry a gate that starts closed", () => {
      expect(withoutComments(readSource(backendApp))).toMatch(/BackendSentry\.init\(config,\s*telemetryGate\)/);
      expect(withoutComments(readSource(backendGate))).toContain("AtomicBoolean(false)");
    });

    it("keeps the log appender from starting a second, ungated client", () => {
      // Left to its own devices SentryAppender calls Sentry.init from SENTRY_DSN while logging
      // starts, before main() builds the gated options. The sentinel is its own "skip" signal.
      const logback = readSource(backendLogback).replace(/<!--[\s\S]*?-->/g, "");
      expect(logback).toMatch(/<dsn>\s*SENTRY_DSN_IS_UNDEFINED\s*<\/dsn>/);
      expect(logback).toMatch(/<minimumBreadcrumbLevel>\s*ERROR\s*<\/minimumBreadcrumbLevel>/);
    });

    it("loads the admin's answer only after the database is initialised", () => {
      const app = withoutComments(readSource(backendApp));
      expect(app.indexOf("dbConfig.init()")).toBeGreaterThan(-1);
      expect(app.indexOf("loadTelemetryGate()")).toBeGreaterThan(app.indexOf("dbConfig.init()"));
    });
  });
});

describe("sentry privacy guardrails", () => {
  describe("sendDefaultPii is disabled on every platform", () => {
    it("backend sets isSendDefaultPii = false and userInfo = false", () => {
      const content = readSource(backendSentry);
      expect(content).toContain("isSendDefaultPii = false");
      // Setting any one dataCollection field moves the SDK off the legacy sendDefaultPii rules,
      // so userInfo has to be spelled out.
      expect(content).toMatch(/\buserInfo\s*=\s*false/);
    });

    it("web turns user info, cookies, query strings and bodies off in dataCollection", () => {
      // Sentry 11 removed `sendDefaultPii`; its defaults collect all of these, so the
      // privacy-first posture has to be spelled out in `dataCollection`.
      const content = readSource(webSentryInit);
      expect(content).toContain("dataCollection:");
      expect(content).toContain("userInfo: false");
      expect(content).toContain("cookies: false");
      expect(content).toContain("urlQueryParams: false");
      expect(content).toContain("httpBodies: []");
    });

    it("android sets isSendDefaultPii = false and dataCollection.userInfo = false", () => {
      const content = readSource(androidOptions);
      expect(content).toContain("isSendDefaultPii = false");
      // Any other dataCollection field flips an unset userInfo to true, and with it the SDK
      // stamps an install id and `ip_address = {{auto}}` on every event.
      expect(content).toMatch(/dataCollection\.userInfo\s*=\s*false/);
    });

    it("iOS sets sendDefaultPii = false", () => {
      const content = readSource(iosSentryConfig);
      expect(content).toContain("sendDefaultPii = false");
    });
  });

  describe("user and IP address are stripped before an event is sent, on every platform", () => {
    it("backend strips ipAddress and the user in setBeforeSend", () => {
      expect(readSource(backendSentry)).toContain("setBeforeSend");
      expect(readSource(backendSentry)).toContain("TelemetryScrubber.scrubEvent");
      const scrubber = readSource(backendScrubber);
      expect(scrubber).toMatch(/ipAddress\s*=\s*null/);
      expect(scrubber).toMatch(/event\.user\s*=\s*null/);
    });

    it("web strips ip_address and the user in beforeSend", () => {
      expect(readSource(webSentryInit)).toContain("scrubWebEvent(event");
      const scrub = readSource(webScrub);
      expect(scrub).toContain("scrubSentryEvent(event)");
      expect(scrub).toContain("delete event.user");
      const helper = readSource(webObservability);
      expect(helper).toContain("ip_address");
      expect(helper).toContain("scrubSentryEvent");
    });

    it("android drops the whole user in setBeforeSend", () => {
      expect(readSource(androidOptions)).toContain("setBeforeSend");
      expect(readSource(androidOptions)).toContain("TelemetryScrubber.scrub(event");
      expect(readSource(androidScrubber)).toMatch(/event\.user\s*=\s*null/);
    });

    it("iOS strips ipAddress and drops the whole user in beforeSend", () => {
      const content = readSource(iosSentryConfig);
      expect(content).toContain("beforeSend");
      expect(content).toMatch(/ipAddress\s*=\s*nil/);
      expect(content).toContain("TelemetryScrubber.scrub(event");
      expect(readSource(iosScrubber)).toMatch(/event\.user\s*=\s*nil/);
    });

    it("web removes the Referer by rebuilding the request from the route and the User-Agent", () => {
      const scrub = readSource(webScrub);
      expect(scrub).toContain("function scrubRequest");
      expect(scrub).toContain('headers: { "User-Agent": userAgent }');
    });

    it("web sends reports with referrerPolicy no-referrer", () => {
      expect(readSource(webSentryInit)).toMatch(/referrerPolicy\s*:\s*"no-referrer"/);
    });
  });

  describe("session replays are disabled on every client", () => {
    it("web replaysSessionSampleRate is 0", () => {
      const content = readSource(webSentryInit);
      expect(content).toMatch(/replaysSessionSampleRate\s*:\s*0/);
    });

    it("web replaysOnErrorSampleRate is 0", () => {
      const content = readSource(webSentryInit);
      expect(content).toMatch(/replaysOnErrorSampleRate\s*:\s*0/);
    });

    it("android session replay sample rates are 0.0", () => {
      const content = readSource(androidOptions);
      expect(content).toMatch(/sessionReplay\.sessionSampleRate\s*=\s*0\.0/);
      expect(content).toMatch(/sessionReplay\.onErrorSampleRate\s*=\s*0\.0/);
    });

    it("iOS session replay sample rates are 0", () => {
      const content = readSource(iosSentryConfig);
      expect(content).toMatch(/sessionReplay\.sessionSampleRate\s*=\s*0\b/);
      expect(content).toMatch(/sessionReplay\.onErrorSampleRate\s*=\s*0\b/);
    });
  });

  describe("web automatic breadcrumbs are privacy filtered", () => {
    it("installs no console or DOM breadcrumbs and sanitizes the remaining ones", () => {
      const init = readSource(webSentryInit);
      expect(init).toMatch(/beforeBreadcrumb:[^\n]*scrubWebBreadcrumb/);
      expect(init).toContain("breadcrumbsIntegration");
      // Sentry 11 moved console breadcrumbs out of `breadcrumbsIntegration` into the separate
      // `Console` integration. Nothing installs it: the default list is off and the explicit one
      // (pinned in "web installs exactly the integrations it names") does not list it.
      expect(init).toMatch(/defaultIntegrations:\s*false/);
      expect(withoutComments(init)).not.toMatch(/\bSentry\.(?:console|captureConsole)\w*Integration\(/);
      expect(init).toContain("dom: false");

      const scrub = readSource(webScrub);
      expect(scrub).toContain("scrubSentryBreadcrumb");
      expect(scrub).toContain('new Set(["api", "fetch", "xhr", "navigation", "tday"])');

      const helper = readSource(webObservability);
      expect(helper).toContain("scrubSentryBreadcrumb");
      expect(helper).toContain('breadcrumb.category === "console"');
      expect(helper).toContain('breadcrumb.category?.startsWith("ui.")');
      expect(helper).toContain("SENSITIVE_LABEL_PATTERN");
    });

    it("installs no tracing, so there are no browser transaction names to sanitize", () => {
      // The old guardrail pinned `beforeSendTransaction: scrubSentryTransaction`; with no tracing
      // integration there is no transaction to send, which is the stronger invariant.
      expect(
        callSites(
          [webSrc],
          [TS_EXT, TSX_EXT],
          /\b(?:browserTracingIntegration|startBrowserTracing\w*|wrapCreateBrowserRouter\w*|reactRouterV\d\w*Instrumentation|browserProfilingIntegration)\b/,
        ),
      ).toEqual([]);
      expect(withoutComments(readSource(webRouter))).not.toContain("Sentry");
      expect(readSource(webSentryInit)).toMatch(/tracesSampleRate:\s*0,/);
    });
  });

  describe("backend serverName is generic, not a real hostname", () => {
    it("serverName is set to a hardcoded string, not a system call", () => {
      const content = readSource(backendSentry);
      expect(content).toMatch(/serverName\s*=\s*"tday-backend"/);
    });
  });

  describe("DSNs come from environment variables, never hardcoded in source", () => {
    it("backend reads DSN from AppConfig (env-backed)", () => {
      const content = readSource(backendSentry);
      expect(content).toContain("config.sentryDsn");
      expect(content).not.toMatch(/options\.dsn\s*=\s*"https:\/\//);
    });

    it("web reads DSN from VITE_SENTRY_DSN env", () => {
      const content = readSource(webConsent);
      expect(content).toContain("import.meta.env.VITE_SENTRY_DSN");
      expect(readSource(webSentryInit)).toContain("dsn: clientSentryDsn()");
      for (const file of [webConsent, webSentryInit]) {
        expect(readSource(file)).not.toMatch(/dsn\s*:\s*"https:\/\//);
      }
    });

    it("android reads DSN from BuildConfig (gradle property / env)", () => {
      const content = readSource(androidOptions);
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
      backendApp, backendSentry, webMain, webSentryInit, webConsent, androidApplication,
      androidBootstrap, androidOptions, iosSentryConfig,
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

    it("no source file in any of the four trees contains one", () => {
      const offenders = [
        ...listSources(webSrc, [TS_EXT, TSX_EXT]),
        ...listSources(path.join(MONO, BACKEND_DIR, SRC_DIR, MAIN_DIR), [KT_EXT, ".xml"]),
        ...listSources(ANDROID_SRC, [KT_EXT, ".xml"]),
        ...IOS_TARGET_DIRS.flatMap((dir) => listSources(dir, [SWIFT_EXT, ".plist"])),
      ].filter((file) => SENTRY_DSN_PATTERN.test(readSource(file)));
      expect(offenders.map(repoPath)).toEqual([]);
    });
  });
});

describe("sentry reports are failures only", () => {
  // The opt-in contract is "a report is sent at the moment something fails, and nothing else":
  // no session pings, no sampled traces, no client reports, no trace headers sent to anyone's
  // server, no capture of every failed request. Each client states these outright rather than
  // inheriting an SDK default that a later release could change.
  describe("web", () => {
    it("installs exactly the integrations it names", () => {
      const content = withoutComments(readSource(webSentryInit));
      expect(content).toMatch(/defaultIntegrations:\s*false/);
      const list = between(content, "integrations: [", "],");
      const names = [...list.matchAll(/Sentry\.(\w+)\(/g)].map((match) => match[1]).sort();
      expect(names).toEqual([
        "breadcrumbsIntegration",
        "browserApiErrorsIntegration",
        "dedupeIntegration",
        "eventFiltersIntegration",
        "functionToStringIntegration",
        "globalHandlersIntegration",
        "httpContextIntegration",
        "linkedErrorsIntegration",
      ]);
    });

    it("never installs session, tracing, replay, feedback, failed-request or console capture", () => {
      expect(
        callSites(
          [webSrc],
          [TS_EXT, TSX_EXT],
          /\b(?:browserSessionIntegration|replayIntegration|replayCanvasIntegration|feedbackIntegration|httpClientIntegration|captureConsoleIntegration|consoleLoggingIntegration|browserTracingIntegration)\b/,
        ),
      ).toEqual([]);
    });

    it("samples no traces, propagates no trace headers and sends no client reports", () => {
      const content = readSource(webSentryInit);
      expect(content).toMatch(/tracesSampleRate:\s*0,/);
      expect(content).toContain('traceLifecycle: "static"');
      expect(content).toMatch(/tracePropagationTargets:\s*\[\s*\]/);
      expect(content).toMatch(/sendClientReports:\s*false/);
    });

    it("reports only errors thrown by scripts served from its own origin", () => {
      const content = withoutComments(readSource(webSentryInit));
      expect(content).toContain("allowUrls: [env.origin]");
      expect(content).toContain("denyUrls: DENIED_URLS");
    });
  });

  describe("android", () => {
    const options = () => withoutComments(readSource(androidOptions));

    it("sends no sessions, no client reports and samples no traces", () => {
      const content = options();
      expect(content).toMatch(/isEnableAutoSessionTracking\s*=\s*false/);
      expect(content).toMatch(/isSendClientReports\s*=\s*false/);
      expect(content).toMatch(/tracesSampleRate\s*=\s*0\.0/);
    });

    it("propagates no trace headers: an empty list, not an unset one", () => {
      // Unset means "every host", which would send `sentry-trace`, `baggage` and the DSN's public
      // key to every user's own server.
      const content = options();
      expect(content).toMatch(/setTracePropagationTargets\(\s*emptyList\(\)\s*\)/);
      expect(content).toMatch(/isPropagateTraceparent\s*=\s*false/);
    });

    it("hard-drops transactions, logs and metrics", () => {
      const content = options();
      expect(content).toMatch(/setBeforeSendTransaction\s*\{\s*_,\s*_\s*->\s*null\s*\}/);
      expect(content).toMatch(/logs\.isEnabled\s*=\s*false/);
      expect(content).toMatch(/metrics\.isEnabled\s*=\s*false/);
    });

    it("attaches no screenshot or view hierarchy and replays no history", () => {
      const content = options();
      expect(content).toMatch(/isAttachScreenshot\s*=\s*false/);
      expect(content).toMatch(/isAttachViewHierarchy\s*=\s*false/);
      expect(content).toMatch(/isReportHistoricalAnrs\s*=\s*false/);
    });

    it("keeps the failure detectors on", () => {
      const content = options();
      expect(content).toMatch(/isEnableUncaughtExceptionHandler\s*=\s*true/);
      expect(content).toMatch(/isAnrEnabled\s*=\s*true/);
      expect(content).toMatch(/isEnableNdk\s*=\s*true/);
    });

    it("captures no failed HTTP request", () => {
      expect(withoutComments(readSource(androidNetworkModule))).toMatch(
        /SentryOkHttpInterceptor\(\s*captureFailedRequests\s*=\s*false\s*\)/,
      );
    });

    it("disables every Gradle plugin instrumentation, logcat included", () => {
      // Logcat instrumentation would turn every `Log.w`/`Log.e` line into a breadcrumb.
      const gradle = readSource(androidGradle);
      expect(gradle).toMatch(/tracingInstrumentation\s*\{\s*enabled\s*=\s*false/);
      expect(gradle).toMatch(/logcat\s*\{\s*enabled\s*=\s*false/);
    });

    it("keeps Sentry's content providers out of the merged manifest", () => {
      // They would run in every process before Application.onCreate, consent or not.
      const manifest = readSource(androidManifest);
      for (const provider of [
        "io.sentry.android.core.SentryInitProvider",
        "io.sentry.android.core.SentryPerformanceProvider",
        "io.sentry.ndk.SentryNdkPreloadProvider",
      ]) {
        expect(manifest).toMatch(
          new RegExp(`<provider\\s+android:name="${provider.replace(/\./g, "\\.")}"[^>]*tools:node="remove"`),
        );
      }
    });

    it("reports route patterns, never the navigation arguments", () => {
      // SentryNavigationListener attaches the destination's arguments, which here are list ids
      // and list names.
      expect(
        callSites([ANDROID_SRC, ANDROID_DEBUG_SRC], [KT_EXT], /\bSentryNavigationListener\b/),
      ).toEqual([]);
      expect(readSource(androidGradle)).not.toContain("sentry-android-navigation");
      expect(readSource(androidTelemetry)).toContain("fun navigationTemplate(");
    });
  });

  describe("iOS", () => {
    const options = () => withoutComments(readSource(iosSentryConfig));

    it("sends no sessions, no client reports and samples no traces", () => {
      const content = options();
      expect(content).toMatch(/enableAutoSessionTracking\s*=\s*false/);
      expect(content).toMatch(/sendClientReports\s*=\s*false/);
      expect(content).toMatch(/tracesSampleRate\s*=\s*0\s*\n/);
      expect(content).toMatch(/enableAutoPerformanceTracing\s*=\s*false/);
    });

    it("swizzles nothing: no request is read, no breadcrumb names the server", () => {
      const content = options();
      expect(content).toMatch(/enableSwizzling\s*=\s*false/);
      expect(content).toMatch(/enableNetworkTracking\s*=\s*false/);
      expect(content).toMatch(/enableNetworkBreadcrumbs\s*=\s*false/);
    });

    it("captures no failed HTTP request and propagates no trace headers", () => {
      const content = options();
      expect(content).toMatch(/enableCaptureFailedRequests\s*=\s*false/);
      expect(content).toMatch(/tracePropagationTargets\s*=\s*\[\]/);
      expect(content).toMatch(/enablePropagateTraceparent\s*=\s*false/);
    });

    it("attaches no screenshot or view hierarchy", () => {
      const content = options();
      expect(content).toMatch(/attachScreenshot\s*=\s*false/);
      expect(content).toMatch(/attachViewHierarchy\s*=\s*false/);
    });

    it("keeps the failure detectors on", () => {
      const content = options();
      expect(content).toMatch(/enableCrashHandler\s*=\s*true/);
      expect(content).toMatch(/enableAppHangTracking\s*=\s*true/);
      expect(content).toMatch(/enableWatchdogTerminationTracking\s*=\s*true/);
    });
  });

  describe("backend", () => {
    it("sends no client reports and routes every transport through the gate", () => {
      const content = withoutComments(readSource(backendSentry));
      expect(content).toMatch(/isSendClientReports\s*=\s*false/);
      expect(content).toContain("setTransportFactory(GatedTransportFactory(gate, transportFactory))");
      // `transportGate` is a connectivity check: closed means "cache and resend later", and a
      // closed admin switch has to mean the report is gone.
      expect(content).not.toMatch(/\bsetTransportGate\b|\btransportGate\b/);
    });

    it("checks the gate in the sampler, beforeSend, beforeSendTransaction and beforeBreadcrumb", () => {
      const content = withoutComments(readSource(backendSentry));
      expect(content).toContain("setTracesSampler");
      expect(content).toContain("gate.isOpen, config.sentryTracesSampleRate");
      expect(between(content, "setBeforeSend {", "setBeforeSendTransaction")).toContain("!gate.isOpen");
      expect(between(content, "setBeforeSendTransaction {", "setBeforeBreadcrumb")).toContain("gate.isOpen");
      expect(content).toMatch(/setBeforeBreadcrumb\s*\{[^}]*gate\.isOpen/);
    });

    it("never traces the probes, the socket or the calendar feed", () => {
      const content = readSource(backendSentry);
      for (const untraced of ['"/health"', '"/api/mobile/probe"', '"/ws"', '"/calendar/"']) {
        expect(content).toContain(untraced);
      }
    });

    it("spells out every ITransport method, so no default method can go around the gate", () => {
      const content = withoutComments(readSource(backendGatedTransport));
      expect(content).not.toMatch(/ITransport\s+by\b/);
      expect(content).toMatch(/override fun send\(envelope: SentryEnvelope, hint: Hint\)/);
      expect(content).toMatch(/override fun send\(envelope: SentryEnvelope\)/);
    });
  });

  describe("only the backend samples traces", () => {
    it("the backend sampler reads the env-configured rate", () => {
      expect(readSource(backendSentry)).toContain("config.sentryTracesSampleRate");
      expect(readSource(backendAppConfig)).toContain("SENTRY_TRACES_SAMPLE_RATE");
    });

    it("no client has a trace sample rate setting left", () => {
      expect(callSites([webSrc], [TS_EXT, TSX_EXT], /SENTRY_TRACES_SAMPLE_RATE/)).toEqual([]);
      expect(
        callSites([ANDROID_SRC, ANDROID_DEBUG_SRC], [KT_EXT], /SENTRY_TRACES_SAMPLE_RATE/),
      ).toEqual([]);
      expect(callSites(IOS_TARGET_DIRS, [SWIFT_EXT], /SENTRY_TRACES_SAMPLE_RATE/)).toEqual([]);
      for (const file of [androidGradle, iosInfoPlist, iosProject, iosPbxproj, webViteConfig]) {
        expect(readSource(file), `${repoPath(file)} must not carry a client trace rate`).not.toMatch(
          /SENTRY_TRACES_SAMPLE_RATE|sentryTracesSampleRate/,
        );
      }
    });
  });
});

describe("the crash-report answer survives sign-out and clearing local data", () => {
  // On mobile the answer is about the device, not the account: wiping it would put the consent card
  // back in front of someone who said no, or quietly switch reports off for someone who said yes.
  it("web keeps no per-browser crash-report answer to preserve, and cannot read the old keys", () => {
    const auth = readSource(webAuthProvider);
    const preserved = between(auth, "const PRESERVED_STORAGE_KEYS = [", "];");
    expect(preserved).not.toContain("TELEMETRY_CONSENT");
    expect(preserved).not.toContain("telemetry");

    // The answer is the server's now, so the browser has no storage path for it at all: a value an
    // older build wrote under either key is never read. (Doc comments may still name them, which
    // `callSites` strips.)
    const consent = readSource(webConsent);
    expect(consent).toContain('INSTANCE_TELEMETRY_URL = "/api/instance/telemetry"');
    expect(consent).not.toMatch(/localStorage/);
    expect(callSites([webSrc], [TS_EXT, TSX_EXT], /tday\.telemetry\.consent(?:At)?/)).toEqual([]);
  });

  it("web clears client data only through calls that pass the preserved list", () => {
    const auth = withoutComments(readSource(webAuthProvider));
    const calls = auth.match(/clearClientUserData\(\{/g) ?? [];
    expect(calls.length).toBeGreaterThan(0);
    expect(auth.match(/preserveLocalStorageKeys:\s*PRESERVED_STORAGE_KEYS/g)).toHaveLength(calls.length);
    // No other caller, because a caller that forgets the list wipes the answer.
    expect(
      callSites([webSrc], [TS_EXT, TSX_EXT], /(?<!function\s)\bclearClientUserData\(/).filter(
        (site) => site !== repoPath(webAuthProvider),
      ),
    ).toEqual([]);
  });

  it("android keeps the answer in plain prefs of its own, away from the stores sign-out clears", () => {
    const store = withoutComments(readSource(androidConsentStore));
    expect(store).toContain('PREF_NAME = "telemetry_consent_prefs"');
    expect(store).toMatch(/getSharedPreferences\(PREF_NAME/);
    expect(store).not.toContain("EncryptedSharedPreferences");
    for (const file of [androidOfflineCacheManager, androidSecureConfigStore]) {
      const content = withoutComments(readSource(file));
      expect(content, `${repoPath(file)} must not touch the consent store`).not.toMatch(
        /TelemetryConsent|telemetry_consent_prefs|TelemetryBootstrap/,
      );
    }
    expect(
      callSites([ANDROID_SRC], [KT_EXT], /telemetry_consent_prefs/),
    ).toEqual([repoPath(androidConsentStore)]);
  });

  it("iOS keeps the answer in UserDefaults keys that only the consent store touches", () => {
    const store = withoutComments(readSource(iosConsentStore));
    expect(store).toContain('consentKey = "telemetry.consent"');
    expect(store).toContain('consentedAtKey = "telemetry.consentAt"');
    expect(callSites(IOS_TARGET_DIRS, [SWIFT_EXT], /"telemetry\.consent(?:At)?"/)).toEqual([
      repoPath(iosConsentStore),
      repoPath(iosConsentStore),
    ]);
  });

  it("iOS clear paths neither name the consent keys nor wipe UserDefaults wholesale", () => {
    const auth = withoutComments(readSource(iosAuthRepository));
    expect(auth).not.toMatch(/telemetry|TelemetryConsent/i);
    expect(callSites(IOS_TARGET_DIRS, [SWIFT_EXT], /removePersistentDomain|dictionaryRepresentation\(\)/)).toEqual(
      [],
    );
  });
});

describe("server error reports are an admin switch, off by default", () => {
  it("BackendSentry wires the gate into the transport, the sampler and every callback", () => {
    const content = withoutComments(readSource(backendSentry));
    expect(content).toContain("GatedTransportFactory(");
    expect(content).toContain("setTracesSampler");
    expect(content).toContain("setBeforeSend");
    expect(content).toContain("setBeforeSendTransaction");
    expect(content).toContain("setBeforeBreadcrumb");
    expect(content).toMatch(/\buserInfo\s*=\s*false/);
  });

  it("AdminRoutes serves /telemetry, and the service checks for an admin on read and write", () => {
    const routes = withoutComments(readSource(backendAdminRoutes));
    const telemetry = between(routes, 'route("/telemetry")', 'route("/users")');
    expect(telemetry).toMatch(/\bget\s*\{/);
    expect(telemetry).toMatch(/\bpatch\s*\{/);
    expect(telemetry).toContain("instanceSettingsService.serverTelemetry(user)");
    expect(telemetry).toContain("instanceSettingsService.setServerTelemetry(");

    const service = withoutComments(readSource(backendInstanceSettings));
    expect(service.match(/admin\.requireAdminAccess\(\)\.bind\(\)/g)).toHaveLength(2);
  });

  it("the setting is stored in instance_settings, and a missing row means off", () => {
    expect(existsSync(backendMigration)).toBe(true);
    const migration = readSource(backendMigration);
    expect(migration).toContain("CREATE TABLE IF NOT EXISTS instance_settings");
    expect(migration).toContain("telemetry.sentry.enabled");

    const service = readSource(backendInstanceSettings);
    expect(service).toContain('TELEMETRY_ENABLED_KEY = "telemetry.sentry.enabled"');
    expect(service).toContain('row?.get(InstanceSettings.settingValue) == "true"');
  });

  it("an unreadable setting leaves the gate closed", () => {
    const service = withoutComments(readSource(backendInstanceSettings));
    const load = between(service, "override fun loadTelemetryGate()", "private fun readTelemetry()");
    expect(load).toContain("telemetryGate.set(");
    expect(load).toContain("catch (e: Exception)");
    expect(load).not.toMatch(/telemetryGate\.set\(true\)/);
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

  it("web reports errors React raises outside any boundary through the root handlers", () => {
    // Replaces the old `wrapCreateBrowserRouterV7` pin: that wrapper only fed route tracing,
    // which is gone. These are the errors it never saw.
    const init = readSource(webSentryInit);
    expect(init).toContain("Sentry.reactErrorHandler()");
    expect(init).toContain("onUncaughtError: reportRootError");
    expect(init).toContain("onRecoverableError: reportRootError");
    // Asked before the handler runs: it decorates the error object before it looks for a client.
    expect(between(init, "function reportRootError", "console.error(error)")).toContain(
      "if (isTelemetryGranted()) captureReactError(error, errorInfo)",
    );
    expect(readSource(webMain)).toContain("createRoot(rootElement, reactRootErrorHandlers)");
  });

  it("web API client adds Sentry breadcrumbs on errors", () => {
    const content = readSource(webApiClient);
    expect(content).toContain("addApiErrorBreadcrumb");
  });

  it("android uses SentryOkHttpInterceptor for HTTP breadcrumbs", () => {
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

  it("the web lockfile resolves no analytics SDK or competing crash reporter", () => {
    const lockfile = JSON.parse(readSource(webPackageLock));
    expect(lockfilePackageNames(lockfile).filter(isAnalyticsPackage)).toEqual([]);
    // The check has to be able to see a real one, or a regression in it would pass silently.
    expect(isAnalyticsPackage("@analytics/core")).toBe(true);
    expect(isAnalyticsPackage("react-ga4")).toBe(true);
    expect(isAnalyticsPackage("es-set-tostringtag")).toBe(false);
    expect(isAnalyticsPackage("workbox-google-analytics")).toBe(false);
  });

  it("native/backend manifests do not include GA or Dynatrace SDKs", () => {
    const files = [
      backendGradle,
      androidGradle,
      path.join(MONO, IOS_DIR, "Package.swift"),
    ];
    for (const file of files.filter(existsSync)) {
      const content = readSource(file);
      expect(content).not.toMatch(/google-analytics|firebase-analytics|dynatrace|mixpanel|amplitude/i);
    }
  });

  it("no manifest adds a second crash reporter beside Sentry", () => {
    // The consent card and the FAQ promise that reports go to one place. Crashlytics is the model
    // the copy compares itself to, not something to link.
    const files = [
      backendGradle,
      path.join(MONO, "shared", BUILD_GRADLE),
      path.join(MONO, BUILD_GRADLE),
      path.join(MONO, "settings.gradle.kts"),
      path.join(MONO, ANDROID_DIR, BUILD_GRADLE),
      path.join(MONO, ANDROID_DIR, "settings.gradle.kts"),
      androidGradle,
      iosPackage,
      iosPackageResolved,
      path.join(MONO, IOS_DIR, "TdayApp.xcodeproj", "project.xcworkspace", "xcshareddata", "swiftpm", "Package.resolved"),
      iosProject,
      iosPbxproj,
    ];
    for (const file of files.filter(existsSync)) {
      expect(readSource(file), `${repoPath(file)} must not link another reporter`).not.toMatch(
        /firebase|crashlytics|bugsnag|appcenter|datadog|posthog|segment\.|fullstory|logrocket|instabug|raygun|rollbar|newrelic|countly|matomo|appsflyer/i,
      );
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

  it("web source maps are uploaded and then deleted only when a token exists", () => {
    // The plugin deletes these files whether or not an upload happened, so without the guard a
    // local build would lose its maps. With one, the public copies are removed: they would hand
    // every visitor the unminified source.
    const content = readSource(webViteConfig);
    expect(content).toMatch(/authToken:\s*process\.env\.SENTRY_AUTH_TOKEN/);
    expect(content).toMatch(
      /filesToDeleteAfterUpload:\s*process\.env\.SENTRY_AUTH_TOKEN\s*\?\s*\["dist\/\*\*\/\*\.map"\]\s*:\s*undefined/,
    );
    expect(content).toContain("release: { name: `tday-web@${APP_VERSION}` }");
    expect(content).toMatch(/telemetry:\s*false/);
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

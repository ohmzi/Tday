#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const failures = [];

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), "utf8");
}

function exists(relativePath) {
  return fs.existsSync(path.join(repoRoot, relativePath));
}

function assert(condition, message) {
  if (!condition) failures.push(message);
}

function contains(relativePath, pattern, message) {
  const content = read(relativePath);
  assert(pattern.test(content), message);
}

function notContains(relativePath, pattern, message) {
  const content = read(relativePath);
  assert(!pattern.test(content), message);
}

function walk(relativePath, result = []) {
  const absolutePath = path.join(repoRoot, relativePath);
  if (!fs.existsSync(absolutePath)) return result;

  for (const entry of fs.readdirSync(absolutePath, { withFileTypes: true })) {
    if ([".git", "node_modules", "dist", "build", ".gradle", "DerivedData"].includes(entry.name)) {
      continue;
    }

    const childRelative = path.join(relativePath, entry.name);
    if (entry.isDirectory()) {
      walk(childRelative, result);
    } else {
      result.push(childRelative);
    }
  }
  return result;
}

const sourceFiles = [
  ...walk("tday-web/src"),
  ...walk("tday-backend/src/main"),
  ...walk("android-compose/app/src/main"),
  ...walk("ios-swiftUI/Tday"),
].filter((file) => /\.(kt|kts|swift|ts|tsx|js|mjs)$/.test(file));

const manifestFiles = [
  "tday-web/package.json",
  "tday-web/package-lock.json",
  "tday-backend/build.gradle.kts",
  "android-compose/app/build.gradle.kts",
  "ios-swiftUI/TdayApp.xcodeproj/project.pbxproj",
].filter(exists);

// Exact npm package names and whole scopes. The lockfile lists every transitive package, so a
// substring match flags `es-set-tostringtag` for "gtag" and `workbox-google-analytics` for
// "google-analytics". Kept in step with tests/guardrails/sentry-privacy.test.ts.
const analyticsPackageNames = new Set([
  "analytics", "analytics-node", "ga-gtag", "gtag", "gtag.js", "google-analytics",
  "universal-analytics", "react-ga", "react-ga4", "react-gtm-module", "vue-gtag",
  "mixpanel", "mixpanel-browser", "amplitude-js", "posthog-js", "posthog-node",
  "logrocket", "dynatrace", "firebase", "bugsnag-js", "rollbar", "raygun4js", "trackjs",
  "@types/gtag.js", "@types/google.analytics",
]);
const analyticsPackageScopes = [
  "@analytics/", "@amplitude/", "@bugsnag/", "@datadog/", "@dynatrace/", "@dynatrace-sdk/",
  "@firebase/", "@fullstory/", "@google-analytics/", "@highlight-run/", "@honeybadger-io/",
  "@mixpanel/", "@posthog/", "@rollbar/", "@segment/",
];

function isAnalyticsPackage(name) {
  return analyticsPackageNames.has(name) || analyticsPackageScopes.some((scope) => name.startsWith(scope));
}

const lockfile = JSON.parse(read("tday-web/package-lock.json"));
const lockedAnalytics = Object.keys(lockfile.packages ?? {})
  .filter((key) => key !== "")
  .map((key) => key.slice(key.lastIndexOf("node_modules/") + "node_modules/".length))
  .filter(isAnalyticsPackage);
assert(
  lockedAnalytics.length === 0,
  `tday-web/package-lock.json must not add product analytics SDKs (${lockedAnalytics.join(", ")})`,
);
assert(
  isAnalyticsPackage("@analytics/core") && !isAnalyticsPackage("es-set-tostringtag"),
  "the analytics package matcher must match whole package names only",
);

for (const file of manifestFiles.filter((f) => !f.endsWith("package-lock.json"))) {
  notContains(
    file,
    /google-analytics|gtag|@analytics|mixpanel|amplitude|dynatrace|dtrum/i,
    `${file} must not add product analytics SDKs`,
  );
}

const webInit = "tday-web/src/lib/observability/sentryInit.ts";
const webScrub = "tday-web/src/lib/observability/webScrub.ts";
const consentStore = "tday-web/src/lib/privacy/telemetryConsent.ts";

assert(
  walk("tday-web/src").filter(
    (f) => /\.(ts|tsx)$/.test(f) && /\bSentry\.init\(/.test(read(f)),
  ).join() === path.join("tday-web/src/lib/observability/sentryInit.ts"),
  "web Sentry.init( must appear exactly once under tday-web/src, in sentryInit.ts",
);
contains(consentStore, /VITE_SENTRY_DSN/, "web must read its DSN from VITE_SENTRY_DSN, in the consent store");
contains(webInit, /beforeBreadcrumb:/, "web Sentry must scrub automatic breadcrumbs");
contains(webScrub, /scrubSentryBreadcrumb/, "web breadcrumb scrubbing must reuse the shared scrubber");
contains(webInit, /defaultIntegrations:\s*false/, "web Sentry must install only its named integrations");
contains(webInit, /dom:\s*false/, "web Sentry must disable automatic DOM breadcrumbs");
notContains(webInit, /\bconsoleIntegration\b|\bbrowserTracingIntegration\b|\breplayIntegration\b/, "web Sentry must not install console, tracing or replay integrations");
contains(webInit, /tracesSampleRate:\s*0\b/, "web Sentry must sample no traces");
contains(webInit, /tracePropagationTargets:\s*\[\s*\]/, "web trace propagation must stay off");
contains(webInit, /sendClientReports:\s*false/, "web Sentry must send no client reports");
contains(webInit, /replaysSessionSampleRate:\s*0/, "web session replay must stay disabled");
contains(webInit, /replaysOnErrorSampleRate:\s*0/, "web error replay must stay disabled");

contains(
  "tday-web/src/lib/observability/sentry.ts",
  /SENSITIVE_LABEL_PATTERN/,
  "web observability helper must redact sensitive labels",
);
contains(
  "tday-web/src/lib/observability/sentry.ts",
  /SENSITIVE_DATA_KEY_PATTERN/,
  "web observability helper must redact sensitive data keys",
);

contains(
  "tday-backend/src/main/kotlin/com/ohmz/tday/observability/TdayObservability.kt",
  /routeLikeDataKeys/,
  "backend observability helper must sanitize route-like data by key",
);
contains(
  "android-compose/app/src/main/java/com/ohmz/tday/compose/core/observability/TdayTelemetry.kt",
  /routeLikeDataKeys/,
  "Android observability helper must sanitize route-like data by key",
);
contains(
  "ios-swiftUI/Tday/Core/SentryConfiguration.swift",
  /routeLikeDataKeys/,
  "iOS observability helper must sanitize route-like data by key",
);

contains(
  "tday-backend/src/main/kotlin/com/ohmz/tday/observability/BackendSentry.kt",
  /isSendDefaultPii\s*=\s*false/,
  "backend Sentry must keep sendDefaultPii disabled",
);
contains(
  "android-compose/app/src/main/java/com/ohmz/tday/compose/core/observability/TelemetryOptions.kt",
  /isSendDefaultPii\s*=\s*false/,
  "Android Sentry must keep sendDefaultPii disabled",
);
contains(
  "ios-swiftUI/Tday/Core/SentryConfiguration.swift",
  /sendDefaultPii\s*=\s*false/,
  "iOS Sentry must keep sendDefaultPii disabled",
);

for (const file of sourceFiles) {
  const content = read(file);
  const isHelper =
    file.endsWith("tday-web/src/lib/observability/sentry.ts") ||
    file.endsWith("tday-backend/src/main/kotlin/com/ohmz/tday/observability/TdayObservability.kt") ||
    file.endsWith("android-compose/app/src/main/java/com/ohmz/tday/compose/core/observability/TdayTelemetry.kt") ||
    file.endsWith("ios-swiftUI/Tday/Core/SentryConfiguration.swift") ||
    file.endsWith("tday-web/src/main.tsx") ||
    file.endsWith("tday-web/src/router.tsx") ||
    file.endsWith("tday-backend/src/main/kotlin/com/ohmz/tday/Application.kt") ||
    file.endsWith("tday-backend/src/main/kotlin/com/ohmz/tday/plugins/SentryPlugin.kt") ||
    file.endsWith("android-compose/app/src/main/java/com/ohmz/tday/compose/TdayApplication.kt");

  if (!isHelper && /Sentry(?:SDK)?\.addBreadcrumb|Sentry\.captureException|SentrySDK\.capture/.test(content)) {
    failures.push(`${file} should use the platform observability helper instead of direct Sentry calls`);
  }

  if (/sendDefaultPii\s*=\s*true|isSendDefaultPii\s*=\s*true|replaysSessionSampleRate:\s*[1-9]|replaysOnErrorSampleRate:\s*[1-9]/.test(content)) {
    failures.push(`${file} enables a privacy-sensitive Sentry option`);
  }
}

const requiredDocs = [
  "docs/TELEMETRY.md",
  "docs/SENTRY_RUNBOOK.md",
  "docs/CODING_STANDARDS.md",
  "docs/TESTING.md",
  "docs/DEPLOYMENT.md",
  "AGENTS.md",
];
for (const file of requiredDocs) {
  assert(exists(file), `${file} must exist`);
}

contains(
  "docs/SENTRY_RUNBOOK.md",
  /Failure Triage/,
  "Sentry runbook must document failure triage",
);
contains(
  "docs/SENTRY_RUNBOOK.md",
  /Do not paste passwords/,
  "Sentry runbook must document credential handling",
);
contains(
  "docs/TELEMETRY.md",
  /New Feature Observability Checklist/,
  "telemetry docs must include the new feature checklist",
);

if (failures.length > 0) {
  console.error("Observability smoke check failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Observability smoke check passed.");

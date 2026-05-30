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

for (const file of manifestFiles) {
  notContains(
    file,
    /google-analytics|gtag|@analytics|mixpanel|amplitude|dynatrace|dtrum/i,
    `${file} must not add product analytics SDKs`,
  );
}

contains(
  "tday-web/src/main.tsx",
  /beforeBreadcrumb:\s*scrubSentryBreadcrumb/,
  "web Sentry must scrub automatic breadcrumbs",
);
contains(
  "tday-web/src/main.tsx",
  /beforeSendTransaction:\s*scrubSentryTransaction/,
  "web Sentry must scrub transaction names",
);
contains(
  "tday-web/src/main.tsx",
  /console:\s*false/,
  "web Sentry must disable automatic console breadcrumbs",
);
contains(
  "tday-web/src/main.tsx",
  /dom:\s*false/,
  "web Sentry must disable automatic DOM breadcrumbs",
);
contains(
  "tday-web/src/main.tsx",
  /tracePropagationTargets:\s*\[\s*\/\^\\\/api/,
  "web trace propagation must stay restricted to T'Day API routes",
);
contains(
  "tday-web/src/main.tsx",
  /replaysSessionSampleRate:\s*0/,
  "web session replay must stay disabled",
);
contains(
  "tday-web/src/main.tsx",
  /replaysOnErrorSampleRate:\s*0/,
  "web error replay must stay disabled",
);

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
  "tday-backend/src/main/kotlin/com/ohmz/tday/Application.kt",
  /isSendDefaultPii\s*=\s*false/,
  "backend Sentry must keep sendDefaultPii disabled",
);
contains(
  "android-compose/app/src/main/java/com/ohmz/tday/compose/TdayApplication.kt",
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

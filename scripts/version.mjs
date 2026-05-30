#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..");
const manifestPath = path.join(repoRoot, "version.json");
const semverPattern = /^\d+\.\d+\.\d+$/;

function readText(filePath) {
  return fs.readFileSync(filePath, "utf8");
}

function writeText(filePath, value) {
  fs.writeFileSync(filePath, value);
}

function readJson(filePath) {
  return JSON.parse(readText(filePath));
}

function writeJson(filePath, value) {
  writeText(filePath, `${JSON.stringify(value, null, 2)}\n`);
}

function loadManifest() {
  const manifest = readJson(manifestPath);
  validateManifest(manifest);
  return manifest;
}

function validateManifest(manifest) {
  if (!semverPattern.test(manifest.version ?? "")) {
    throw new Error("version.json version must be x.y.z semver");
  }
  if (manifest.compatibility?.mode !== "exact") {
    throw new Error('version.json compatibility.mode must be "exact"');
  }
  if (typeof manifest.compatibility?.updateRequired !== "boolean") {
    throw new Error("version.json compatibility.updateRequired must be boolean");
  }
  if (!Number.isInteger(manifest.ios?.buildNumber) || manifest.ios.buildNumber < 1) {
    throw new Error("version.json ios.buildNumber must be a positive integer");
  }
  if (typeof manifest.ios?.updateUrl !== "string") {
    throw new Error("version.json ios.updateUrl must be a string");
  }
}

function replaceRequired(text, pattern, replacement, label) {
  if (!pattern.test(text)) {
    throw new Error(`Could not find ${label}`);
  }
  return text.replace(pattern, replacement);
}

function syncPackageJson(manifest) {
  const packagePath = path.join(repoRoot, "tday-web", "package.json");
  const pkg = readJson(packagePath);
  pkg.version = manifest.version;
  writeJson(packagePath, pkg);
}

function syncPackageLock(manifest) {
  const lockPath = path.join(repoRoot, "tday-web", "package-lock.json");
  if (!fs.existsSync(lockPath)) return;

  const lock = readJson(lockPath);
  lock.version = manifest.version;
  if (lock.packages?.[""]) {
    lock.packages[""].version = manifest.version;
  }
  writeJson(lockPath, lock);
}

function syncInfoPlist(manifest) {
  const plistPath = path.join(repoRoot, "ios-swiftUI", "Tday", "Info.plist");
  let text = readText(plistPath);
  text = replaceRequired(
    text,
    /(<key>CFBundleShortVersionString<\/key>\s*<string>)[^<]*(<\/string>)/,
    `$1${manifest.version}$2`,
    "CFBundleShortVersionString",
  );

  const updateUrlValue = escapeXml(manifest.ios.updateUrl);
  if (/<key>TdayUpdateURL<\/key>/.test(text)) {
    text = replaceRequired(
      text,
      /(<key>TdayUpdateURL<\/key>\s*<string>)[^<]*(<\/string>)/,
      `$1${updateUrlValue}$2`,
      "TdayUpdateURL",
    );
  } else {
    text = replaceRequired(
      text,
      /(\s*<key>TdayProbeEncryptionKey<\/key>\s*<string>[^<]*<\/string>)/,
      `$1\n\t<key>TdayUpdateURL</key>\n\t<string>${updateUrlValue}</string>`,
      "TdayProbeEncryptionKey",
    );
  }
  writeText(plistPath, text);
}

function syncXcodeProject(manifest) {
  const replacements = [
    {
      filePath: path.join(repoRoot, "ios-swiftUI", "project.yml"),
      edits: [
        [/MARKETING_VERSION: [0-9]+\.[0-9]+\.[0-9]+/g, `MARKETING_VERSION: ${manifest.version}`, "project.yml MARKETING_VERSION"],
        [/CURRENT_PROJECT_VERSION: \d+/g, `CURRENT_PROJECT_VERSION: ${manifest.ios.buildNumber}`, "project.yml CURRENT_PROJECT_VERSION"],
      ],
    },
    {
      filePath: path.join(repoRoot, "ios-swiftUI", "TdayApp.xcodeproj", "project.pbxproj"),
      edits: [
        [/MARKETING_VERSION = [0-9]+\.[0-9]+\.[0-9]+;/g, `MARKETING_VERSION = ${manifest.version};`, "pbxproj MARKETING_VERSION"],
        [/CURRENT_PROJECT_VERSION = \d+;/g, `CURRENT_PROJECT_VERSION = ${manifest.ios.buildNumber};`, "pbxproj CURRENT_PROJECT_VERSION"],
      ],
    },
  ];

  for (const { filePath, edits } of replacements) {
    if (!fs.existsSync(filePath)) continue;
    let text = readText(filePath);
    for (const [pattern, replacement, label] of edits) {
      text = replaceRequired(text, pattern, replacement, label);
    }
    writeText(filePath, text);
  }
}

function syncEnvExamples(manifest) {
  for (const filePath of [
    path.join(repoRoot, ".env.example"),
    path.join(repoRoot, "tday-backend", ".env.example"),
  ]) {
    if (!fs.existsSync(filePath)) continue;
    let text = readText(filePath);
    text = replaceRequired(
      text,
      /^TDAY_APP_VERSION=.*$/m,
      `TDAY_APP_VERSION=${manifest.version}`,
      `${path.relative(repoRoot, filePath)} TDAY_APP_VERSION`,
    );
    text = replaceRequired(
      text,
      /^TDAY_UPDATE_REQUIRED=.*$/m,
      `TDAY_UPDATE_REQUIRED=${manifest.compatibility.updateRequired}`,
      `${path.relative(repoRoot, filePath)} TDAY_UPDATE_REQUIRED`,
    );
    writeText(filePath, text);
  }
}

function escapeXml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll('"', "&quot;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

function sync() {
  const manifest = loadManifest();
  syncPackageJson(manifest);
  syncPackageLock(manifest);
  syncInfoPlist(manifest);
  syncXcodeProject(manifest);
  syncEnvExamples(manifest);
  console.log(`Version mirrors synced to ${manifest.version}`);
}

function check() {
  const before = new Map();
  for (const filePath of mirrorPaths()) {
    if (fs.existsSync(filePath)) {
      before.set(filePath, readText(filePath));
    }
  }

  sync();

  const changed = [];
  for (const [filePath, content] of before.entries()) {
    if (readText(filePath) !== content) {
      changed.push(path.relative(repoRoot, filePath));
      writeText(filePath, content);
    }
  }

  if (changed.length > 0) {
    throw new Error(`Version mirrors are out of sync: ${changed.join(", ")}`);
  }
  console.log("Version mirrors are in sync");
}

function mirrorPaths() {
  return [
    manifestPath,
    path.join(repoRoot, "tday-web", "package.json"),
    path.join(repoRoot, "tday-web", "package-lock.json"),
    path.join(repoRoot, "ios-swiftUI", "Tday", "Info.plist"),
    path.join(repoRoot, "ios-swiftUI", "project.yml"),
    path.join(repoRoot, "ios-swiftUI", "TdayApp.xcodeproj", "project.pbxproj"),
    path.join(repoRoot, ".env.example"),
    path.join(repoRoot, "tday-backend", ".env.example"),
  ];
}

function bump(kind) {
  if (!["patch", "minor", "major"].includes(kind)) {
    throw new Error("Usage: node scripts/version.mjs bump patch|minor|major");
  }

  const manifest = loadManifest();
  const [major, minor, patch] = manifest.version.split(".").map(Number);
  manifest.version = {
    patch: `${major}.${minor}.${patch + 1}`,
    minor: `${major}.${minor + 1}.0`,
    major: `${major + 1}.0.0`,
  }[kind];
  manifest.ios.buildNumber += 1;
  writeJson(manifestPath, manifest);
  sync();
}

const [command, argument] = process.argv.slice(2);

try {
  if (command === "sync") {
    sync();
  } else if (command === "check") {
    check();
  } else if (command === "bump") {
    bump(argument);
  } else {
    throw new Error("Usage: node scripts/version.mjs sync|check|bump patch|minor|major");
  }
} catch (error) {
  console.error(error.message);
  process.exit(1);
}

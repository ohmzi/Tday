#!/usr/bin/env node
/**
 * Decides whether an iOS TestFlight build is warranted between two refs.
 *
 * Usage: node scripts/ios-release-changed.mjs <base-ref> <head-ref>
 *
 * Why this is not a plain `paths:` filter
 * ---------------------------------------
 * `.github/workflows/ios-testflight.yml` fires on tag pushes, and `on: push: tags:` accepts no
 * `paths:` key at all — GitHub rejects the combination. The comparison therefore has to be an
 * explicit tag-to-tag diff.
 *
 * More importantly, a naive "did anything under ios-swiftUI/ change" test can never answer no.
 * Every release commit runs `scripts/version.mjs sync` plus `:shared:exportGuideContent`, and
 * those always rewrite:
 *
 *   ios-swiftUI/Tday/Info.plist                     CFBundleShortVersionString
 *   ios-swiftUI/project.yml                         MARKETING_VERSION / CURRENT_PROJECT_VERSION
 *   ios-swiftUI/TdayApp.xcodeproj/project.pbxproj   MARKETING_VERSION / CURRENT_PROJECT_VERSION
 *   ios-swiftUI/Tday/Resources/Guide/guide.*.json   currentVersion
 *
 * (Verified against the real v0.7.2 release commit — those are exactly the ios-swiftUI/ paths
 * it touched.) Excluding those paths wholesale would be wrong in the other direction: it would
 * hide a genuine Info.plist edit such as a new usage-description string, or a new source file
 * added to the pbxproj. So each one is compared with the version tokens normalised away — a
 * file whose *only* difference is the release bump does not count, while any other edit to that
 * very same file does.
 *
 * `version.json` is deliberately absent from the relevant set entirely: `ios.buildNumber`
 * increments on every single release, so counting it would make the filter a permanent no-op.
 * Its `version` DOES count, but only under the compatibility policy that makes a version
 * mismatch fatal — see [requiresVersionParity] for why, and for what that policy cost when
 * this filter answered "no" to a release that still moved the version.
 *
 * The normalisation patterns intentionally mirror `scripts/version.mjs` (`syncInfoPlist` /
 * `syncXcodeProject`) and the guide exporter's `currentVersion` field. If a new version mirror
 * ever lands under ios-swiftUI/, add it here too or the filter silently starts building on
 * every release again.
 */
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import process from "node:process";

const VERSION_PLACEHOLDER = "__TDAY_VERSION__";
const BUILD_PLACEHOLDER = "__TDAY_BUILD__";

const GUIDE_JSON = /^ios-swiftUI\/Tday\/Resources\/Guide\/guide\.[a-z-]+\.json$/;

/** Paths whose changes can affect the shipped iOS binary or the pipeline that builds it. */
function isIosRelevantPath(filePath) {
  // The workflow and this script decide what ships, so a change to either warrants a build.
  if (filePath === ".github/workflows/ios-testflight.yml") return true;
  if (filePath === "scripts/ios-release-changed.mjs") return true;
  // Everything the app, its four embedded bundles, and the fastlane lane are built from.
  if (!filePath.startsWith("ios-swiftUI/")) return false;
  // Prose under ios-swiftUI/ (README and friends) never reaches the app bundle.
  return !filePath.endsWith(".md");
}

/** The only paths a release bump can rewrite on its own. Everything else counts as-is. */
function hasVersionMirror(filePath) {
  return (
    filePath === "ios-swiftUI/Tday/Info.plist" ||
    filePath === "ios-swiftUI/project.yml" ||
    filePath === "ios-swiftUI/TdayApp.xcodeproj/project.pbxproj" ||
    GUIDE_JSON.test(filePath)
  );
}

/**
 * Strips the tokens that `scripts/version.mjs sync` and the guide exporter rewrite on every
 * release, so a release-only diff normalises to an identical blob.
 */
function normalize(filePath, text) {
  if (filePath === "ios-swiftUI/Tday/Info.plist") {
    return text.replace(
      /(<key>CFBundleShortVersionString<\/key>\s*<string>)[^<]*(<\/string>)/,
      `$1${VERSION_PLACEHOLDER}$2`,
    );
  }
  if (filePath === "ios-swiftUI/project.yml") {
    return text
      .replace(/MARKETING_VERSION: [0-9]+\.[0-9]+\.[0-9]+/g, `MARKETING_VERSION: ${VERSION_PLACEHOLDER}`)
      .replace(/CURRENT_PROJECT_VERSION: \d+/g, `CURRENT_PROJECT_VERSION: ${BUILD_PLACEHOLDER}`);
  }
  if (filePath === "ios-swiftUI/TdayApp.xcodeproj/project.pbxproj") {
    return text
      .replace(/MARKETING_VERSION = [0-9]+\.[0-9]+\.[0-9]+;/g, `MARKETING_VERSION = ${VERSION_PLACEHOLDER};`)
      .replace(/CURRENT_PROJECT_VERSION = \d+;/g, `CURRENT_PROJECT_VERSION = ${BUILD_PLACEHOLDER};`);
  }
  if (GUIDE_JSON.test(filePath)) {
    return text.replace(/("currentVersion"\s*:\s*")[^"]*(")/, `$1${VERSION_PLACEHOLDER}$2`);
  }
  return text;
}

/**
 * Runs git and returns its stdout. The buffer is generous because `diff --name-only` across
 * several releases of a monorepo can be long.
 */
function git(args) {
  return execFileSync("git", args, { encoding: "utf8", maxBuffer: 256 * 1024 * 1024 });
}

/** Returns the blob at `ref:filePath`, or null when the path does not exist at that ref. */
function readBlob(ref, filePath) {
  try {
    // stderr is discarded: "path does not exist in <ref>" is an expected answer here, not a
    // fault, and letting git narrate it would fill the CI log with scary-looking fatals.
    return execFileSync("git", ["show", `${ref}:${filePath}`], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    });
  } catch {
    return null;
  }
}

/** True when the only difference between the two blobs is the release version bump. */
function isReleaseNoiseOnly(filePath, base, head) {
  if (!hasVersionMirror(filePath)) return false;
  const before = readBlob(base, filePath);
  const after = readBlob(head, filePath);
  // Added or deleted outright — that is a real change whatever the contents say.
  if (before === null || after === null) return false;
  return normalize(filePath, before) === normalize(filePath, after);
}

/**
 * True when the compatibility policy in force at `head` makes an iOS version mismatch fatal.
 *
 * `compatibility.mode: "exact"` with `updateRequired: true` is what the server hands the mobile
 * clients through the probe (`{"appVersion":…,"updateRequired":true,"compatibilityMode":"exact"}`),
 * and both mobile clients answer ANY version difference with "update required" — the app's own
 * update when it is older, the server's when it is newer. So under that policy the iOS build and
 * the server must ship the same version: a release that moves the version without shipping iOS
 * points every iOS user at a TestFlight build that does not exist, and the one thing they can do
 * about it is nothing.
 *
 * That is not hypothetical. v0.7.30 was the first release this filter ever answered "no" to —
 * nothing under ios-swiftUI/ changed but the version tokens it normalises away, so the build was
 * skipped, TestFlight stayed at v0.7.29, and the iOS app was left telling its owner to update a
 * server that had already moved past them.
 *
 * Read at `head` rather than at `base`: what matters is the policy the release being evaluated
 * will enforce. A policy relaxed since the base is exactly the case where no build is needed.
 */
function requiresVersionParity(head) {
  const raw = readBlob(head, "version.json");
  if (raw === null) return false;
  try {
    const compatibility = JSON.parse(raw).compatibility ?? {};
    return (
      String(compatibility.mode ?? "exact").toLowerCase() === "exact" &&
      compatibility.updateRequired === true
    );
  } catch {
    // An unreadable manifest is not this script's to diagnose — `version.mjs check` owns that,
    // and guessing "parity required" here would spend a macOS run on every malformed manifest.
    return false;
  }
}

/** True when the release version itself moved between the two refs. */
function versionMoved(base, head) {
  const versionOf = (ref) => {
    const raw = readBlob(ref, "version.json");
    if (raw === null) return null;
    try {
      return String(JSON.parse(raw).version ?? "");
    } catch {
      return null;
    }
  };
  const before = versionOf(base);
  const after = versionOf(head);
  if (after === null) return false;
  // No manifest at the base (a tag from before version.json existed) counts as moved.
  return before !== after;
}

/** Writes a line to stdout. `console` is not used: DeepSource's JS-0002 forbids it. */
function log(line) {
  process.stdout.write(`${line}\n`);
}

/**
 * Diffs the two refs, reports which iOS-relevant files changed, and writes `should_build`
 * to GITHUB_OUTPUT. Always exits 0 unless a ref is unusable.
 */
function main() {
  const [base, head] = process.argv.slice(2);
  if (!base || !head) {
    console.error("Usage: node scripts/ios-release-changed.mjs <base-ref> <head-ref>");
    process.exit(2);
  }

  const changed = git(["diff", "--name-only", base, head])
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const relevant = changed
    .filter(isIosRelevantPath)
    .filter((filePath) => !isReleaseNoiseOnly(filePath, base, head));

  // Asked before the path result is trusted: under an exact + updateRequired policy the version
  // moving is itself a reason to build, because skipping it is what strands iOS users.
  const parityForcesBuild = requiresVersionParity(head) && versionMoved(base, head);

  const shouldBuild = relevant.length > 0 || parityForcesBuild;

  log(`Comparing ${base}...${head}`);
  log(`${changed.length} file(s) changed in total.`);
  if (relevant.length > 0) {
    log(`${relevant.length} iOS-relevant file(s):`);
    for (const filePath of relevant.slice(0, 40)) log(`  ${filePath}`);
    if (relevant.length > 40) log(`  ... and ${relevant.length - 40} more`);
  }
  if (parityForcesBuild) {
    log(
      "The version moved under compatibility exact + updateRequired, which both mobile clients " +
        "read as a hard mismatch in either direction — building so iOS ships the same version.",
    );
  }
  if (!shouldBuild) {
    log("No iOS-relevant changes — only release version mirrors, if anything.");
  }

  log(`should_build=${shouldBuild}`);

  if (process.env.GITHUB_OUTPUT) {
    fs.appendFileSync(process.env.GITHUB_OUTPUT, `should_build=${shouldBuild}\n`);
    fs.appendFileSync(process.env.GITHUB_OUTPUT, `relevant_count=${relevant.length}\n`);
  }

  // Always exits 0. "Nothing to build" is a normal answer, not a step failure — only a real
  // fault (bad ref, unreadable repo) should fail the job, and those throw on their own.
}

main();

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

  const shouldBuild = relevant.length > 0;

  log(`Comparing ${base}...${head}`);
  log(`${changed.length} file(s) changed in total.`);
  if (shouldBuild) {
    log(`${relevant.length} iOS-relevant file(s):`);
    for (const filePath of relevant.slice(0, 40)) log(`  ${filePath}`);
    if (relevant.length > 40) log(`  ... and ${relevant.length - 40} more`);
  } else {
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

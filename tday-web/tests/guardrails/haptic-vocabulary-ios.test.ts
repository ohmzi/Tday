import { existsSync, readdirSync, readFileSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

/**
 * iOS's haptic vocabulary, guarded from the two things that will undo it: the next call
 * site, and Android drifting away from it.
 *
 * `Core/UI/HapticManager.swift` used to be eleven functions named after the *place* they
 * were called from — `buttonTap`, `gentleTap`, `sheetConfirm`, `sheetDismiss` — four of
 * which had no call site at all, while eleven other sites bypassed the file entirely and
 * built a `UIImpactFeedbackGenerator(style: .light)` inline. Both halves of that are
 * invisible to everything else the repository runs: `xctest` compiles a raw generator
 * perfectly happily, a haptic leaves no trace in a snapshot, and there is no local iOS
 * compile on the development machine at all. So it is a source scanner, in the suite that
 * already reads Swift from vitest (`motion-reachability-ios.test.ts`,
 * `ios-empty-state-presence.test.ts`) and runs on every PR without a Mac.
 *
 * The parity rule at the bottom is the other half of the job. The point of the vocabulary
 * is that the two clients can be read side by side; that only holds while the two name
 * lists are the same list.
 */

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");
const IOS_SRC = path.join(MONO, "ios-swiftUI", "Tday");
const VOCABULARY_FILE = path.join(IOS_SRC, "Core", "UI", "HapticManager.swift");
const ANDROID_VOCABULARY_FILE = path.join(
  MONO,
  "android-compose",
  "app",
  "src",
  "main",
  "java",
  "com",
  "ohmz",
  "tday",
  "compose",
  "core",
  "ui",
  "TdayHaptics.kt",
);

/**
 * The eight events, in declaration order. This list is the contract, not a summary of the
 * file: it is Android's `TdayHaptics` order, and both clients are asserted against it.
 */
const VOCABULARY = [
  "buttonPress",
  "selection",
  "toggle",
  "completion",
  "destructive",
  "dragPickUp",
  "dragDrop",
  "reveal",
];

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
  return path.relative(MONO, filePath);
}

const skipIOS = !existsSync(IOS_SRC);
const describeIOS = skipIOS ? describe.skip : describe;

const ALL_SWIFT = skipIOS ? [] : walkFiles(IOS_SRC, ".swift");
const CALLER_SWIFT = ALL_SWIFT.filter((f) => f !== VOCABULARY_FILE);

/**
 * The UIKit haptic APIs, by the fragment that gives each of them away. The three generator
 * classes are how every bypassed call site was written; `sensoryFeedback` is SwiftUI's own
 * route, which the app does not use today and must not start using outside the vocabulary
 * either, or it ends up with two answers to the same question.
 */
const RAW_HAPTIC_MARKERS = [
  "UIImpactFeedbackGenerator",
  "UINotificationFeedbackGenerator",
  "UISelectionFeedbackGenerator",
  "sensoryFeedback",
];

/** Names declared as `static func` on `enum HapticManager`, in declaration order. */
function declaredVocabulary(source: string): string[] {
  return [
    ...source.matchAll(/^ {4}static func\s+([A-Za-z][A-Za-z0-9]*)\s*\(/gm),
  ].map((m) => m[1]);
}

/** Names declared as `fun` on `object TdayHaptics`, in declaration order. */
function declaredAndroidVocabulary(source: string): string[] {
  return [...source.matchAll(/^ {4}fun\s+([A-Za-z][A-Za-z0-9]*)\s*\(/gm)].map(
    (m) => m[1],
  );
}

/** The generator call one vocabulary function resolves to, style and intensity included. */
function recipeFor(source: string, name: string): string {
  const start = source.search(
    new RegExp(`^ {4}static func\\s+${name}\\s*\\(`, "m"),
  );
  if (start === -1) return "";
  const rest = source.slice(start + 1);
  const nextFunc = rest.search(/^ {4}static func\s+/m);
  const body = nextFunc === -1 ? rest : rest.slice(0, nextFunc);
  return [
    ...body.matchAll(
      /(UI\w*FeedbackGenerator\([^)]*\))|(\.(?:impactOccurred|notificationOccurred|selectionChanged)\([^)]*\))/g,
    ),
  ]
    .map((m) => m[0])
    .join(" ");
}

describeIOS("iOS haptic vocabulary", () => {
  it("declares the vocabulary in exactly one file", () => {
    expect(
      existsSync(VOCABULARY_FILE),
      `${relPath(VOCABULARY_FILE)} is the single source of the iOS haptic vocabulary`,
    ).toBe(true);
    expect(readSource(VOCABULARY_FILE)).toContain("enum HapticManager {");
  });

  it("declares the eight events, and only those", () => {
    expect(
      declaredVocabulary(readSource(VOCABULARY_FILE)),
      "The vocabulary is a fixed list of events. Adding a ninth name means adding it on " +
        "Android too, in the same order, and this list with it.",
    ).toEqual(VOCABULARY);
  });

  it("has no raw UIKit haptic call outside the vocabulary file", () => {
    const violations: string[] = [];
    for (const file of CALLER_SWIFT) {
      const lines = readSource(file).split("\n");
      for (let i = 0; i < lines.length; i++) {
        const code = lines[i].trimStart();
        if (
          code.startsWith("//") ||
          code.startsWith("*") ||
          code.startsWith("/*")
        )
          continue;
        if (RAW_HAPTIC_MARKERS.some((marker) => lines[i].includes(marker))) {
          violations.push(`${relPath(file)}:${i + 1} → ${code}`);
        }
      }
    }

    expect(
      violations,
      "Call sites must name the event, not the generator. Use HapticManager.<event>() from " +
        "Core/UI/HapticManager.swift — add a name there, and on Android, if none of them " +
        `fits:\n${violations.join("\n")}`,
    ).toEqual([]);
  });

  it("gives every vocabulary name at least one call site", () => {
    const callers = CALLER_SWIFT.map(readSource).join("\n");
    const unused = VOCABULARY.filter(
      (name) => !callers.includes(`HapticManager.${name}(`),
    );

    expect(
      unused,
      "A haptic nothing calls reads as coverage and provides none — four of them sat in this " +
        "file unreferenced while raw generators fired the same events two directories away. " +
        `Delete the name or route the site that needs it: ${unused.join(", ")}`,
    ).toEqual([]);
  });

  it("keeps a tap, a completion and a deletion feeling different from each other", () => {
    const source = readSource(VOCABULARY_FILE);
    const distinct = ["buttonPress", "completion", "destructive"];
    const resolved = distinct.map((name) => ({
      name,
      recipe: recipeFor(source, name),
    }));

    for (const { name, recipe } of resolved) {
      expect(
        recipe,
        `${name}() must resolve to a concrete generator call`,
      ).not.toBe("");
    }

    const recipes = resolved.map(({ recipe }) => recipe);
    expect(
      new Set(recipes).size,
      `Tapping a button, finishing a task and deleting one are three different events and must not collapse back onto one generator: ${resolved
        .map(({ name, recipe }) => `${name}=${recipe}`)
        .join(", ")}`,
    ).toBe(recipes.length);
  });

  it("mirrors Android's vocabulary name for name", () => {
    if (!existsSync(ANDROID_VOCABULARY_FILE)) {
      // Android's half of this work lands in its own PR. Until it does there is nothing
      // to compare against, and the iOS list is already pinned by the test above.
      expect(VOCABULARY.length).toBe(8);
      return;
    }

    expect(
      declaredAndroidVocabulary(readSource(ANDROID_VOCABULARY_FILE)),
      "The two clients exist to be read side by side. A name that exists on one and not " +
        "the other, or in a different order, is how the last five divergences started.",
    ).toEqual(VOCABULARY);
  });
});

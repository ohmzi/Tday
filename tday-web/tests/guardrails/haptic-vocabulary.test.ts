import { existsSync, readdirSync, readFileSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

/**
 * Android's haptic vocabulary, guarded from the one thing that will undo it: the next
 * call site.
 *
 * The app used to fire `HapticFeedbackConstantsCompat.CLOCK_TICK` at 55 of its 64 haptic
 * sites, so deleting a task, ticking one off, lifting a row under the finger and tapping a
 * bar button were the same buzz. That was not a decision anybody made; it was what happens
 * when every new site copies the nearest existing one and the nearest existing one is a
 * tick. `core/ui/TdayHaptics.kt` replaces the constant with an event name, which only works
 * for as long as call sites keep going through it — one raw `performHapticFeedback` and the
 * vocabulary is back to being a suggestion.
 *
 * Nothing else in the repo can see this. `:app:testDebugUnitTest` compiles a raw constant
 * perfectly happily, there is no Compose UI test on CI, and a haptic is invisible to a
 * screenshot even if there were one. So it is a source scanner, in the suite that already
 * reads Kotlin from vitest (`android-standards.test.ts`,
 * `motion-reachability-android.test.ts`) and runs on every PR without a Mac, a Gradle
 * daemon or an emulator.
 */

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");
const ANDROID_SRC = path.join(
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
);
const VOCABULARY_FILE = path.join(ANDROID_SRC, "core", "ui", "TdayHaptics.kt");

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

const skipAndroid = !existsSync(ANDROID_SRC);
const describeAndroid = skipAndroid ? describe.skip : describe;

const ALL_KT = skipAndroid ? [] : walkFiles(ANDROID_SRC, ".kt");
const CALLER_KT = ALL_KT.filter((f) => f !== VOCABULARY_FILE);

/**
 * The platform haptic APIs, by the fragment that gives each of them away. `ViewCompat`
 * carries the current call shape; the `LocalHapticFeedback` / `HapticFeedbackType` pair is
 * the Compose-native route the app does not use today and must not start using outside the
 * vocabulary either, or it ends up with two answers to the same question.
 */
const RAW_HAPTIC_MARKERS = [
  "performHapticFeedback",
  "HapticFeedbackConstantsCompat.",
  "HapticFeedbackConstants.",
  "LocalHapticFeedback",
  "HapticFeedbackType",
];

/** Names declared on `object TdayHaptics`, in declaration order. */
function declaredVocabulary(source: string): string[] {
  return [...source.matchAll(/^ {4}fun\s+([A-Za-z][A-Za-z0-9]*)\s*\(/gm)].map(
    (m) => m[1],
  );
}

/** The `HapticFeedbackConstantsCompat` constants one vocabulary function resolves to. */
function constantsFor(source: string, name: string): string[] {
  const start = source.search(new RegExp(`^ {4}fun\\s+${name}\\s*\\(`, "m"));
  if (start === -1) return [];
  const rest = source.slice(start + 1);
  const nextFun = rest.search(/^ {4}fun\s+/m);
  const body = nextFun === -1 ? rest : rest.slice(0, nextFun);
  return [...body.matchAll(/HapticFeedbackConstantsCompat\.([A-Z_]+)/g)].map(
    (m) => m[1],
  );
}

describeAndroid("Android haptic vocabulary", () => {
  it("declares the vocabulary in exactly one file", () => {
    expect(
      existsSync(VOCABULARY_FILE),
      `${relPath(VOCABULARY_FILE)} is the single source of the haptic vocabulary`,
    ).toBe(true);
    expect(readSource(VOCABULARY_FILE)).toContain("object TdayHaptics {");
  });

  it("has no raw platform haptic call outside the vocabulary file", () => {
    const violations: string[] = [];
    for (const file of CALLER_KT) {
      const lines = readSource(file).split("\n");
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const code = line.trimStart();
        if (code.startsWith("//") || code.startsWith("*")) continue;
        if (RAW_HAPTIC_MARKERS.some((marker) => line.includes(marker))) {
          violations.push(`${relPath(file)}:${i + 1} → ${code}`);
        }
      }
    }

    expect(
      violations,
      `Call sites must name the event, not the waveform. Use TdayHaptics.<event>(view) from core/ui/TdayHaptics.kt — add a name there if none of them fits:\n${violations.join("\n")}`,
    ).toEqual([]);
  });

  it("gives every vocabulary name at least one call site", () => {
    const names = declaredVocabulary(readSource(VOCABULARY_FILE));
    expect(names.length).toBeGreaterThan(0);

    const callers = CALLER_KT.map(readSource).join("\n");
    const unused = names.filter(
      (name) => !callers.includes(`TdayHaptics.${name}(`),
    );

    expect(
      unused,
      "A haptic nothing calls reads as coverage and provides none — it is the exact defect " +
        "this work fixed on iOS, where four documented haptics had zero call sites. Delete " +
        `the name or route the site that needs it: ${unused.join(", ")}`,
    ).toEqual([]);
  });

  it("keeps a tap, a completion and a deletion feeling different from each other", () => {
    const source = readSource(VOCABULARY_FILE);
    const distinct = ["buttonPress", "completion", "destructive"];
    const resolved = distinct.map((name) => ({
      name,
      constants: constantsFor(source, name),
    }));

    for (const { name, constants } of resolved) {
      expect(
        constants.length,
        `${name}() must resolve to a concrete constant`,
      ).toBeGreaterThan(0);
    }

    const flat = resolved.flatMap(({ constants }) => constants);
    expect(
      new Set(flat).size,
      `Tapping a button, finishing a task and deleting one are three different events and must not collapse back onto one constant: ${resolved
        .map(({ name, constants }) => `${name}=${constants.join("/")}`)
        .join(", ")}`,
    ).toBe(flat.length);
  });
});

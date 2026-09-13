import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

/**
 * MOTION TOKEN PARITY, AND THE MIGRATION RATCHET
 *
 * `MotionTokens.kt` is hand-written and `:shared:exportMotionTokens` writes four
 * artifacts from it — one Kotlin object, one Swift enum, one stylesheet, one TS
 * module. `./gradlew :shared:verifyMotionTokens` already proves the four are what
 * the exporter would write today. It cannot prove the four say the same thing,
 * because a bug in the exporter is equally present in the file it verifies and in
 * the verification: both read the same code path. This file is the second opinion.
 * It never imports a token. It re-reads the five files as text, off disk, and
 * compares each one only ever to the other four.
 *
 * That rule is not ours. `EarlierIllustrationMotionTest.kt` pins a literal `150L`
 * rather than re-deriving it from the constant that defines it, and says why:
 * "comparing it back against its own definition would pass no matter what either
 * one changed to." The same trap is wider here. `import { DURATIONS } from
 * "@/generated/motion-tokens"` and then asserting `DURATIONS.quick === 150` would
 * be a test of nothing but the exporter's last run — and it would say nothing at
 * all about Android or iOS. So: five parsers, five independent reads, no imports.
 *
 * What this buys, which is the point of writing it here and not on each client:
 * iOS parity checked on Linux, in CI, in well under a second, with no Mac and no
 * simulator in the loop. `TdayMotionGenerated.swift` is text like any other file.
 *
 * Two things the parsers have to get right, both of which have already bitten:
 *
 *   A. iOS carries durations in SECONDS, as `static let quick: TimeInterval = 0.15`.
 *      There is no `duration:` on that line — the grep everyone reaches for when
 *      auditing SwiftUI motion (`.easeOut(duration: 0.22)`) misses every one of
 *      them. The parser keys off `static let` inside the `Durations` enum instead,
 *      and converts to milliseconds before comparing.
 *   B. An easing is four numbers and two of them can be swapped with no compile
 *      error, no type error and no visible diff in review. That is the failure
 *      this file exists for above all the others, so control points are compared
 *      position by position and the failure message names the index.
 *
 * The second half of the file is the ratchet. `motion-budget.json` records how many
 * hand-written motion literals each client still carries, counted in the tree as it
 * stood when the token layer landed, and the assertion is `actual <= budget`. It
 * does not ask anyone to migrate; it asks that nobody add. Deliberately NOT modelled
 * on `coding-standards.test.ts:210-234`, which counts hardcoded colours, prints them
 * and then asserts `expect(true).toBe(true)` — a check that has never once failed and
 * never can.
 */

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");

// ─── The five files ──────────────────────────────────────────────────
const SOURCE_KT = path.join(
  MONO, "shared", "src", "commonMain", "kotlin", "com", "ohmz", "tday", "shared",
  "motion", "MotionTokens.kt",
);
const ANDROID_KT = path.join(
  MONO, "android-compose", "app", "src", "main", "java", "com", "ohmz", "tday",
  "compose", "core", "ui", "TdayMotionTokensGenerated.kt",
);
const IOS_SWIFT = path.join(
  MONO, "ios-swiftUI", "Tday", "UI", "Theme", "TdayMotionGenerated.swift",
);
const WEB_CSS = path.join(ROOT, "src", "generated", "motion-tokens.css");
const WEB_TS = path.join(ROOT, "src", "generated", "motion-tokens.ts");

function read(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

/**
 * Token names arrive in four casings — `PlacementLead`, `placementLead`,
 * `placement-lead`, `PLACEMENT_LEAD` — and the comparison is about the token, not
 * the casing each language happens to want.
 */
function key(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]/g, "");
}

/**
 * The body of `object Foo {` / `enum Foo {` / `const FOO = {`, brace-matched.
 *
 * Section-scoped parsing is not tidiness: `Enter` is both a duration and an easing,
 * and `Gesture` is both an easing and a spring. A file-wide regex for `const val
 * Enter` would cheerfully conflate the two and the conflation would look like
 * agreement.
 */
function blockAfter(source: string, opener: RegExp, file: string): string {
  const match = opener.exec(source);
  if (!match) {
    throw new Error(`${path.relative(MONO, file)}: no block matching ${opener}`);
  }
  const start = source.indexOf("{", match.index + match[0].length - 1);
  let depth = 0;
  for (let i = start; i < source.length; i++) {
    if (source[i] === "{") depth++;
    else if (source[i] === "}" && --depth === 0) return source.slice(start + 1, i);
  }
  throw new Error(`${path.relative(MONO, file)}: unterminated block for ${opener}`);
}

type Curve = [number, number, number, number];
type Scalars = Record<string, number>;
type Curves = Record<string, Curve>;

function matchAll(body: string, re: RegExp): RegExpExecArray[] {
  return [...body.matchAll(new RegExp(re.source, re.flags.includes("g") ? re.flags : re.flags + "g"))];
}

// ─── 1. The source of truth, in Kotlin ───────────────────────────────
function parseSource() {
  const src = read(SOURCE_KT);

  const durations: Scalars = {};
  for (const m of matchAll(src, /\bDuration\(\s*"(\w+)"\s*,\s*(\d+)\s*,/g)) {
    durations[key(m[1])] = Number(m[2]);
  }

  /**
   * `PlacementLead` is `EMPHASIS_MS`, not a literal — the source derives it from
   * the Emphasis rung on purpose, so that it cannot drift from the placement tween
   * it is defined to match. Resolving the alias rather than special-casing 320
   * keeps that derivation under test: rename or re-point `EMPHASIS_MS` and this
   * throws instead of quietly agreeing.
   */
  const delays: Scalars = {};
  for (const m of matchAll(src, /\bDelay\(\s*"(\w+)"\s*,\s*(\w+)\s*,/g)) {
    const raw = m[2];
    if (/^\d+$/.test(raw)) {
      delays[key(m[1])] = Number(raw);
      continue;
    }
    const alias = new RegExp(
      `private val ${raw}\\s*:\\s*Int\\s*=\\s*durations\\.first\\s*\\{[^}]*it\\.name\\s*==\\s*"(\\w+)"[^}]*\\}\\.ms`,
    ).exec(src);
    if (!alias || durations[key(alias[1])] === undefined) {
      throw new Error(`MotionTokens.kt: delay ${m[1]} reads ${raw}, which resolves to no duration`);
    }
    delays[key(m[1])] = durations[key(alias[1])];
  }

  const easings: Curves = {};
  const num = "(-?\\d+(?:\\.\\d+)?)";
  for (const m of matchAll(src, new RegExp(
    `\\bEasing\\(\\s*"(\\w+)"\\s*,\\s*${num}\\s*,\\s*${num}\\s*,\\s*${num}\\s*,\\s*${num}\\s*,`, "g",
  ))) {
    easings[key(m[1])] = [Number(m[2]), Number(m[3]), Number(m[4]), Number(m[5])];
  }

  const springResponse: Scalars = {};
  const springDamping: Scalars = {};
  const springStiffness: Scalars = {};
  for (const m of matchAll(src, new RegExp(
    `\\bSpring\\(\\s*"(\\w+)"\\s*,\\s*${num}\\s*,\\s*${num}\\s*,\\s*(\\d+)\\s*,`, "g",
  ))) {
    springResponse[key(m[1])] = Number(m[2]);
    springDamping[key(m[1])] = Number(m[3]);
    springStiffness[key(m[1])] = Number(m[4]);
  }

  const pressScales: Scalars = {};
  for (const m of matchAll(src, new RegExp(`\\bPressScale\\(\\s*"(\\w+)"\\s*,\\s*${num}\\s*,`, "g"))) {
    pressScales[key(m[1])] = Number(m[2]);
  }

  const tolerance = /const val SPRING_TOLERANCE\s*:\s*Double\s*=\s*([\d.]+)/.exec(src);
  if (!tolerance) throw new Error("MotionTokens.kt: no SPRING_TOLERANCE");

  return {
    durations, delays, easings, springResponse, springDamping, springStiffness,
    pressScales, tolerance: Number(tolerance[1]),
  };
}

// ─── 2. Android ──────────────────────────────────────────────────────
function parseAndroid() {
  const src = read(ANDROID_KT);
  const consts = (blockName: string): Scalars => {
    const body = blockAfter(src, new RegExp(`object ${blockName}\\s*\\{`), ANDROID_KT);
    const out: Scalars = {};
    for (const m of matchAll(body, /const val (\w+)\s*:\s*\w+\s*=\s*(-?\d+(?:\.\d+)?)f?/g)) {
      out[m[1]] = Number(m[2]);
    }
    return out;
  };

  const durations: Scalars = {};
  for (const [name, value] of Object.entries(consts("Durations"))) durations[key(name)] = value;
  const delays: Scalars = {};
  for (const [name, value] of Object.entries(consts("Delays"))) delays[key(name)] = value;
  const pressScales: Scalars = {};
  for (const [name, value] of Object.entries(consts("PressScales"))) pressScales[key(name)] = value;

  // `StandardX1` → curve `standard`, slot 0. Reassembled rather than read as a
  // four-element array because Compose takes the points positionally too.
  const SLOTS = ["X1", "Y1", "X2", "Y2"];
  const easings: Curves = {};
  for (const [name, value] of Object.entries(consts("Easings"))) {
    const slot = SLOTS.findIndex((s) => name.endsWith(s));
    if (slot < 0) throw new Error(`TdayMotionTokensGenerated.kt: ${name} is not an X1/Y1/X2/Y2 point`);
    const curve = key(name.slice(0, -2));
    easings[curve] ??= [NaN, NaN, NaN, NaN];
    easings[curve][slot] = value;
  }

  const springDamping: Scalars = {};
  const springStiffness: Scalars = {};
  for (const [name, value] of Object.entries(consts("Springs"))) {
    if (name.endsWith("Damping")) springDamping[key(name.slice(0, -"Damping".length))] = value;
    else if (name.endsWith("Stiffness")) springStiffness[key(name.slice(0, -"Stiffness".length))] = value;
    else throw new Error(`TdayMotionTokensGenerated.kt: ${name} is neither a damping nor a stiffness`);
  }

  return { durations, delays, easings, springDamping, springStiffness, pressScales };
}

// ─── 3. iOS ──────────────────────────────────────────────────────────
function parseIos() {
  const src = read(IOS_SWIFT);
  const lets = (blockName: string): Scalars => {
    const body = blockAfter(src, new RegExp(`enum ${blockName}\\s*\\{`), IOS_SWIFT);
    const out: Scalars = {};
    // `static let quick: TimeInterval = 0.15` — the type annotation is optional and
    // there is no `duration:` anywhere on the line. See note A at the top.
    for (const m of matchAll(body, /static let (\w+)(?:\s*:\s*[\w.]+)?\s*=\s*(-?\d+(?:\.\d+)?)\s*$/gm)) {
      out[m[1]] = Number(m[2]);
    }
    return out;
  };

  /** Seconds to milliseconds. 0.15 * 1000 is not exactly 150 in IEEE 754. */
  const toMs = (seconds: number, name: string): number => {
    const ms = seconds * 1000;
    const rounded = Math.round(ms);
    if (Math.abs(ms - rounded) > 1e-6) {
      throw new Error(`TdayMotionGenerated.swift: ${name} is ${seconds}s, not a whole millisecond`);
    }
    return rounded;
  };

  const durations: Scalars = {};
  for (const [name, value] of Object.entries(lets("Durations"))) durations[key(name)] = toMs(value, name);
  const delays: Scalars = {};
  for (const [name, value] of Object.entries(lets("Delays"))) delays[key(name)] = toMs(value, name);
  const pressScales: Scalars = {};
  for (const [name, value] of Object.entries(lets("PressScales"))) pressScales[key(name)] = value;

  const springResponse: Scalars = {};
  const springDamping: Scalars = {};
  for (const [name, value] of Object.entries(lets("Springs"))) {
    if (name.endsWith("Response")) springResponse[key(name.slice(0, -"Response".length))] = value;
    else if (name.endsWith("Damping")) springDamping[key(name.slice(0, -"Damping".length))] = value;
    else throw new Error(`TdayMotionGenerated.swift: ${name} is neither a response nor a damping`);
  }

  // Read by label, not by position: a `Bezier(y1:…, x1:…)` reordering would fail to
  // match at all and surface as a missing curve rather than as a silent swap.
  const num = "(-?\\d+(?:\\.\\d+)?)";
  const easings: Curves = {};
  const easingBody = blockAfter(src, /enum Easings\s*\{/, IOS_SWIFT);
  for (const m of matchAll(easingBody, new RegExp(
    `static let (\\w+)\\s*=\\s*Bezier\\(x1:\\s*${num},\\s*y1:\\s*${num},\\s*x2:\\s*${num},\\s*y2:\\s*${num}\\)`, "g",
  ))) {
    easings[key(m[1])] = [Number(m[2]), Number(m[3]), Number(m[4]), Number(m[5])];
  }

  return { durations, delays, easings, springResponse, springDamping, pressScales, raw: src };
}

// ─── 4. Web, the stylesheet ──────────────────────────────────────────
function parseCss() {
  const src = read(WEB_CSS);
  const num = "(-?\\d+(?:\\.\\d+)?)";

  const scalars = (prefix: string, suffix: string): Scalars => {
    const out: Scalars = {};
    for (const m of matchAll(src, new RegExp(`--tday-${prefix}-([a-z-]+)\\s*:\\s*${num}${suffix}\\s*;`, "g"))) {
      out[key(m[1])] = Number(m[2]);
    }
    return out;
  };

  const easings: Curves = {};
  for (const m of matchAll(src, new RegExp(
    `--tday-ease-([a-z-]+)\\s*:\\s*cubic-bezier\\(\\s*${num}\\s*,\\s*${num}\\s*,\\s*${num}\\s*,\\s*${num}\\s*\\)\\s*;`, "g",
  ))) {
    easings[key(m[1])] = [Number(m[2]), Number(m[3]), Number(m[4]), Number(m[5])];
  }

  return {
    durations: scalars("duration", "ms"),
    delays: scalars("delay", "ms"),
    pressScales: scalars("press", ""),
    easings,
    raw: src,
  };
}

// ─── 5. Web, the module ──────────────────────────────────────────────
function parseTs() {
  const src = read(WEB_TS);
  const num = "(-?\\d+(?:\\.\\d+)?)";
  const entries = (name: string): Scalars => {
    const body = blockAfter(src, new RegExp(`export const ${name}\\s*=\\s*\\{`), WEB_TS);
    const out: Scalars = {};
    for (const m of matchAll(body, new RegExp(`(\\w+)\\s*:\\s*${num}\\s*,`, "g"))) out[key(m[1])] = Number(m[2]);
    return out;
  };

  const easings: Curves = {};
  const easingBody = blockAfter(src, /export const EASINGS\s*=\s*\{/, WEB_TS);
  for (const m of matchAll(easingBody, new RegExp(
    `(\\w+)\\s*:\\s*\\[\\s*${num}\\s*,\\s*${num}\\s*,\\s*${num}\\s*,\\s*${num}\\s*\\]`, "g",
  ))) {
    easings[key(m[1])] = [Number(m[2]), Number(m[3]), Number(m[4]), Number(m[5])];
  }

  const springResponse: Scalars = {};
  const springDamping: Scalars = {};
  const springStiffness: Scalars = {};
  const springBody = blockAfter(src, /export const SPRINGS\s*=\s*\{/, WEB_TS);
  for (const m of matchAll(springBody, new RegExp(
    `(\\w+)\\s*:\\s*\\{\\s*response:\\s*${num}\\s*,\\s*damping:\\s*${num}\\s*,\\s*stiffness:\\s*${num}\\s*\\}`, "g",
  ))) {
    springResponse[key(m[1])] = Number(m[2]);
    springDamping[key(m[1])] = Number(m[3]);
    springStiffness[key(m[1])] = Number(m[4]);
  }

  return {
    durations: entries("DURATIONS"),
    delays: entries("DELAYS"),
    pressScales: entries("PRESS_SCALES"),
    easings, springResponse, springDamping, springStiffness,
  };
}

const FILES_PRESENT = [SOURCE_KT, ANDROID_KT, IOS_SWIFT, WEB_CSS, WEB_TS].every(existsSync);
const describeParity = FILES_PRESENT ? describe : describe.skip;

describeParity("motion token parity across the five files", () => {
  const source = parseSource();
  const android = parseAndroid();
  const ios = parseIos();
  const css = parseCss();
  const ts = parseTs();

  const scalarSets = (category: "durations" | "delays" | "pressScales"): Record<string, Scalars> => ({
    "MotionTokens.kt": source[category],
    "TdayMotionTokensGenerated.kt": android[category],
    "TdayMotionGenerated.swift": ios[category],
    "motion-tokens.css": css[category],
    "motion-tokens.ts": ts[category],
  });

  const curveSets = (): Record<string, Curves> => ({
    "MotionTokens.kt": source.easings,
    "TdayMotionTokensGenerated.kt": android.easings,
    "TdayMotionGenerated.swift": ios.easings,
    "motion-tokens.css": css.easings,
    "motion-tokens.ts": ts.easings,
  });

  /** Every file's key set, as one comparable object, so one `expect` names them all. */
  function names(sets: Record<string, Record<string, unknown>>): Record<string, string[]> {
    return Object.fromEntries(
      Object.entries(sets).map(([file, map]) => [file, Object.keys(map).sort()]),
    );
  }
  function agreed(sets: Record<string, Record<string, unknown>>): Record<string, string[]> {
    const reference = Object.keys(Object.values(sets)[0]).sort();
    return Object.fromEntries(Object.keys(sets).map((file) => [file, reference]));
  }

  describe("durations", () => {
    it("names the same five rungs in all five files", () => {
      expect(names(scalarSets("durations"))).toEqual(agreed(scalarSets("durations")));
    });

    it("gives every rung the same length in all five files, iOS seconds converted", () => {
      const sets = scalarSets("durations");
      for (const name of Object.keys(source.durations)) {
        const perFile = Object.fromEntries(
          Object.entries(sets).map(([file, map]) => [file, map[name]]),
        );
        const expected = Object.fromEntries(
          Object.keys(sets).map((file) => [file, source.durations[name]]),
        );
        expect({ [name]: perFile }).toEqual({ [name]: expected });
      }
    });

    it("holds the five rungs the vocabulary is locked to", () => {
      // Pinned literals, not a re-derivation. Every other assertion in this file
      // compares the five files to each other, and five files re-exported together
      // agree perfectly on a number nobody voted for — which is exactly what a
      // well-meaning "just bump Enter to 190" PR looks like from inside the codegen.
      // This block and its four siblings below — delays, easings, springs, press
      // scales — are the whole locked vocabulary written out once, and they are the
      // only assertions here that can notice all five files moving as one.
      expect(source.durations).toEqual({
        quick: 150, enter: 200, change: 260, emphasis: 320, scene: 520,
      });
    });
  });

  describe("delays", () => {
    it("names the same delays in all five files", () => {
      expect(names(scalarSets("delays"))).toEqual(agreed(scalarSets("delays")));
    });

    it("gives every delay the same length in all five files", () => {
      const sets = scalarSets("delays");
      for (const name of Object.keys(source.delays)) {
        const perFile = Object.fromEntries(Object.entries(sets).map(([f, m]) => [f, m[name]]));
        const expected = Object.fromEntries(Object.keys(sets).map((f) => [f, source.delays[name]]));
        expect({ [name]: perFile }).toEqual({ [name]: expected });
      }
    });

    it("keeps the delays in a namespace of their own on every client", () => {
      // PlacementLead and CelebrationLead are both 320, and so is the Emphasis
      // duration. If any client ever emitted them into the duration namespace the
      // three would become one interchangeable utility and no later guardrail could
      // tell a celebration from a row placement.
      for (const name of Object.keys(source.delays)) {
        expect(android.durations[name]).toBeUndefined();
        expect(ios.durations[name]).toBeUndefined();
        expect(css.durations[name]).toBeUndefined();
        expect(ts.durations[name]).toBeUndefined();
      }
      expect(css.raw).toMatch(/--tday-delay-placement-lead/);
    });

    it("holds the two delays the vocabulary is locked to", () => {
      // PlacementLead being Emphasis by construction is checked by the parser above;
      // it is checked there as a derivation, not as a value. Re-point EMPHASIS_MS at
      // Change and every cross-file comparison still agrees — on 260.
      expect(source.delays).toEqual({ placementlead: 320, celebrationlead: 320 });
    });
  });

  describe("easings", () => {
    it("names the same curves in all five files", () => {
      expect(names(curveSets())).toEqual(agreed(curveSets()));
    });

    it("gives every curve the same four control points, in the same order", () => {
      // Point by point, with the index in the message. A transposed pair is the
      // failure mode here — it compiles, it renders, and it reads fine in a diff.
      const sets = curveSets();
      const AXES = ["x1", "y1", "x2", "y2"] as const;
      for (const name of Object.keys(source.easings)) {
        for (let i = 0; i < 4; i++) {
          const perFile = Object.fromEntries(
            Object.entries(sets).map(([file, map]) => [file, map[name]?.[i]]),
          );
          const expected = Object.fromEntries(
            Object.keys(sets).map((file) => [file, source.easings[name][i]]),
          );
          expect({ [`${name}.${AXES[i]}`]: perFile }).toEqual({ [`${name}.${AXES[i]}`]: expected });
        }
      }
    });

    it("keeps Gesture, the web-only curve, out of nobody's file", () => {
      // Gesture is documented as WEB ONLY as an idiom rule — web call sites may name
      // it, Android and iOS reach for the Gesture spring instead. It is still
      // exported everywhere, and that is deliberate: an idiom rule kept by omitting
      // the value is one a later exporter change silently repeals.
      for (const map of Object.values(curveSets())) expect(map.gesture).toBeDefined();
    });

    it("holds the five curves the vocabulary is locked to", () => {
      // Control points pinned, in CSS order. Three of these five are byte-identical
      // to a Compose built-in (Standard is FastOutSlowInEasing, Enter is
      // LinearOutSlowInEasing, Exit is FastOutLinearInEasing), which is the property
      // that makes migrating an Android site onto them cost no pixels. Editing a
      // control point here and re-exporting would quietly retire that property.
      expect(source.easings).toEqual({
        standard: [0.4, 0, 0.2, 1],
        enter: [0, 0, 0.2, 1],
        exit: [0.4, 0, 1, 1],
        scene: [0.05, 0.7, 0.1, 1],
        gesture: [0.2, 0.8, 0.2, 1],
      });
    });
  });

  describe("springs", () => {
    it("names the same springs wherever springs are carried", () => {
      const sets = {
        "MotionTokens.kt": source.springDamping,
        "TdayMotionTokensGenerated.kt": android.springDamping,
        "TdayMotionGenerated.swift": ios.springDamping,
        "motion-tokens.ts": ts.springDamping,
      };
      expect(names(sets)).toEqual(agreed(sets));
    });

    it("gives every spring the same damping in all four files that carry one", () => {
      // Compose calls it dampingRatio and SwiftUI calls it dampingFraction; they are
      // the same dimensionless zeta, which is why this number crosses unconverted.
      for (const name of Object.keys(source.springDamping)) {
        const perFile = {
          "MotionTokens.kt": source.springDamping[name],
          "TdayMotionTokensGenerated.kt": android.springDamping[name],
          "TdayMotionGenerated.swift": ios.springDamping[name],
          "motion-tokens.ts": ts.springDamping[name],
        };
        const expected = Object.fromEntries(
          Object.keys(perFile).map((f) => [f, source.springDamping[name]]),
        );
        expect({ [name]: perFile }).toEqual({ [name]: expected });
      }
    });

    it("gives every spring the same stiffness in the three files that carry one", () => {
      // Compose's half of the pair. Without this the only check on a stiffness is the
      // identity below, and that has 2% of slack in it — enough to let 504 drift to
      // 500 in one file and stay green.
      for (const name of Object.keys(source.springStiffness)) {
        const perFile = {
          "MotionTokens.kt": source.springStiffness[name],
          "TdayMotionTokensGenerated.kt": android.springStiffness[name],
          "motion-tokens.ts": ts.springStiffness[name],
        };
        const expected = Object.fromEntries(
          Object.keys(perFile).map((f) => [f, source.springStiffness[name]]),
        );
        expect({ [name]: perFile }).toEqual({ [name]: expected });
      }
    });

    it("gives every spring the same response in the three files that carry one", () => {
      // SwiftUI's half, and the same argument.
      for (const name of Object.keys(source.springResponse)) {
        const perFile = {
          "MotionTokens.kt": source.springResponse[name],
          "TdayMotionGenerated.swift": ios.springResponse[name],
          "motion-tokens.ts": ts.springResponse[name],
        };
        const expected = Object.fromEntries(
          Object.keys(perFile).map((f) => [f, source.springResponse[name]]),
        );
        expect({ [name]: perFile }).toEqual({ [name]: expected });
      }
    });

    it("describes ONE spring per name across two platforms' parameter sets", () => {
      // The only assertion in this file that crosses a unit boundary rather than a
      // file boundary, and the only one the exporter could not have got right by
      // copying. Compose fixes mass = 1, so its natural frequency is sqrt(stiffness);
      // SwiftUI's is 2*PI/response. Same spring iff stiffness == (2*PI/response)^2.
      //
      // The response is read from the SWIFT file and the stiffness from the KOTLIN
      // one, on purpose: neither client's file contains both numbers, so nothing
      // here can be satisfied by one file agreeing with itself.
      const TOLERANCE = 0.02;
      expect(source.tolerance).toBe(TOLERANCE); // pinned, per EarlierIllustrationMotionTest

      for (const name of Object.keys(ios.springResponse)) {
        const response = ios.springResponse[name];
        const stiffness = android.springStiffness[name];
        expect(response, `${name}: no response in TdayMotionGenerated.swift`).toBeGreaterThan(0);
        expect(stiffness, `${name}: no stiffness in TdayMotionTokensGenerated.kt`).toBeGreaterThan(0);

        const implied = ((2 * Math.PI) / response) ** 2;
        const drift = Math.abs(implied - stiffness) / implied;
        expect(
          drift,
          `${name}: Swift response ${response} implies stiffness ${implied.toFixed(1)}, ` +
            `but Kotlin carries ${stiffness} — ${(drift * 100).toFixed(1)}% apart`,
        ).toBeLessThanOrEqual(TOLERANCE);
      }
    });

    it("carries no spring in the stylesheet, and does not pretend to", () => {
      // Web has no spring runtime. The stylesheet omitting them is a decision, so it
      // is asserted rather than left as an absence nobody would notice reversing.
      expect(css.raw).not.toMatch(/--tday-spring/);
    });

    it("holds the three springs the vocabulary is locked to", () => {
      // All three parameters, because each of the checks above leaves a way through:
      // the identity has 2% of slack in it, it says nothing at all about damping, and
      // the file-to-file comparisons are satisfied by a coordinated re-export. Snappy
      // in particular is not a preference — 0.28/0.86 is the pair hand-written at
      // nineteen iOS sites, and a Snappy that drifts off it stops being byte-identical
      // to the thing it was measured from.
      expect({
        response: source.springResponse,
        damping: source.springDamping,
        stiffness: source.springStiffness,
      }).toEqual({
        response: { snappy: 0.28, gesture: 0.34, settle: 0.4 },
        damping: { snappy: 0.86, gesture: 0.82, settle: 0.86 },
        stiffness: { snappy: 504, gesture: 340, settle: 250 },
      });
    });
  });

  describe("press scales", () => {
    it("names the same surface classes in all five files", () => {
      expect(names(scalarSets("pressScales"))).toEqual(agreed(scalarSets("pressScales")));
    });

    it("gives every surface class the same scale in all five files", () => {
      const sets = scalarSets("pressScales");
      for (const name of Object.keys(source.pressScales)) {
        const perFile = Object.fromEntries(Object.entries(sets).map(([f, m]) => [f, m[name]]));
        const expected = Object.fromEntries(Object.keys(sets).map((f) => [f, source.pressScales[name]]));
        expect({ [name]: perFile }).toEqual({ [name]: expected });
      }
    });

    it("holds the three press scales the vocabulary is locked to", () => {
      // The narrowest part of the vocabulary and the easiest to nudge: a press scale
      // has no unit, no second parameter and no identity relating it to anything, so
      // nothing but a pinned literal can tell 0.94 from 0.90.
      expect(source.pressScales).toEqual({ bar: 0.94, card: 0.97, row: 0.985 });
    });
  });

  it("reads iOS durations that a `duration:` grep cannot see", () => {
    // Note A at the top, asserted rather than left as a comment: if the exporter ever
    // switches the Swift artifact to some other shape, the parser above would return
    // an empty map and every duration comparison would compare undefined to undefined
    // in four files at once. This is the check that the parser found anything at all.
    expect(Object.keys(ios.durations).length).toBe(5);
    expect(ios.raw).toMatch(/static let quick: TimeInterval = 0\.15/);
    expect(ios.raw).not.toMatch(/Durations[\s\S]{0,200}duration:/);
  });
});

// ─────────────────────────────────────────────────────────────────────
// THE RATCHET
// ─────────────────────────────────────────────────────────────────────

/**
 * Source text with comments removed.
 *
 * Not optional, and not a nicety. This PR's own call-site comments argue in place
 * for why a value is NOT a token — "Snappy's response (0.28) with two hundredths
 * more damping", "an un-overridden 150ms" — and one of them quotes a literal
 * `response: 0.28,` inside a doc comment. Counting comment text would make every
 * such argument raise the client's own budget, which is precisely backwards.
 */
function stripComments(source: string): string {
  let out = "";
  let i = 0;
  let quote: string | null = null;
  while (i < source.length) {
    const c = source[i];
    const next = source[i + 1];
    if (quote) {
      if (c === "\\") { out += "  "; i += 2; continue; }
      if (c === quote) quote = null;
      out += c; i++; continue;
    }
    if (c === '"' || c === "'" || c === "`") { quote = c; out += c; i++; continue; }
    if (c === "/" && next === "*") {
      const end = source.indexOf("*/", i + 2);
      const skipped = source.slice(i, end < 0 ? source.length : end + 2);
      out += skipped.replace(/[^\n]/g, " ");
      i += skipped.length; continue;
    }
    // `:` guard so that an unquoted `https://…` in a CSS `url()` is not a comment.
    if (c === "/" && next === "/" && source[i - 1] !== ":") {
      const end = source.indexOf("\n", i);
      i = end < 0 ? source.length : end; continue;
    }
    out += c; i++;
  }
  return out;
}

function walk(dir: string, exts: string[], skip: (p: string) => boolean): string[] {
  const out: string[] = [];
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (skip(full)) continue;
    if (entry.isDirectory()) out.push(...walk(full, exts, skip));
    else if (exts.some((e) => entry.name.endsWith(e))) out.push(full);
  }
  return out;
}

function countMatches(files: string[], re: RegExp): number {
  let total = 0;
  for (const file of files) total += matchAll(stripComments(read(file)), re).length;
  return total;
}

const ANDROID_SRC = path.join(
  MONO, "android-compose", "app", "src", "main", "java", "com", "ohmz", "tday", "compose",
);
const IOS_SRC = path.join(MONO, "ios-swiftUI", "Tday");
const WEB_SRC = path.join(ROOT, "src");

/**
 * What the counts leave out, and why.
 *
 * Every excluded path is a file whose whole job is to hold these numbers once. A
 * budget that counted the token layer would rise by the size of the token layer the
 * day it landed, and each later migration would move a literal from a counted file
 * into an excluded one with no net change — a ratchet that measures nothing. What
 * is counted is what has NOT been migrated.
 */
const EXCLUDED = [
  path.join(ANDROID_SRC, "core", "ui", "TdayMotionTokensGenerated.kt"),
  path.join(ANDROID_SRC, "core", "ui", "TdayMotionTokens.kt"),
  path.join(IOS_SRC, "UI", "Theme", "TdayMotionGenerated.swift"),
  path.join(IOS_SRC, "UI", "Theme", "TdayMotion.swift"),
  path.join(WEB_SRC, "generated"),
  path.join(WEB_SRC, "lib", "motion.ts"),
];
const excluded = (p: string) => EXCLUDED.some((e) => p === e || p.startsWith(e + path.sep));

const KT = walk(ANDROID_SRC, [".kt"], excluded);
const SWIFT = walk(IOS_SRC, [".swift"], excluded);
const WEB = walk(WEB_SRC, [".ts", ".tsx", ".css"], excluded);

/**
 * The counting rules, in one place because the numbers in `motion-budget.json` are
 * only meaningful as "what these rules returned on the day they were written".
 *
 * `android.spring` counts written dampingRatio and stiffness literals only. Compose's
 * `Spring.StiffnessMediumLow` and friends are library defaults rather than decisions —
 * the audit turned up Android's 400 as exactly that — and a ratchet that pushed people
 * off a default onto a hand-typed number would be pointing the wrong way.
 *
 * `android.pressScale` is a scale literal in [0.90, 1.0) on a line that also says
 * scale or press. That catches a handful of enter-scales alongside the press ones
 * (`EnterStartScale = 0.92f`); they are in the same family and the same PRs will
 * touch them, so the slight over-count is honest rather than convenient.
 *
 * `web.durationUtility` counts Tailwind's `duration-<n>` only. The ~159 bare
 * `transition-*` utilities riding Tailwind's un-overridden 150ms
 * `--default-transition-duration` are NOT counted and must never be: that default is
 * already exactly Quick, so those sites are on the vocabulary for free and rebinding
 * it would be a visible change to every one of them.
 */
const COUNTERS: Record<string, Record<string, () => number>> = {
  android: {
    tween: () => countMatches(KT, /(?:durationMillis\s*=\s*\d+|tween\(\s*\d+)/g),
    spring: () => countMatches(KT, /(?:dampingRatio|stiffness)\s*=\s*\d*\.?\d+f?/g),
    pressScale: () => countMatches(
      KT,
      /^.*(?:[Ss]cale|[Pp]ress).*?\b0\.9\d*f\b.*$|^.*\b0\.9\d*f\b.*?(?:[Ss]cale|[Pp]ress).*$/gm,
    ),
  },
  ios: {
    spring: () => countMatches(SWIFT, /(?:response|dampingFraction)\s*:\s*\d*\.?\d+/g),
    easeDuration: () => countMatches(SWIFT, /\bduration\s*:\s*\d*\.?\d+/g),
  },
  web: {
    durationUtility: () => countMatches(WEB, /\bduration-\d+\b/g),
    rawCubicBezier: () => countMatches(WEB, /cubic-bezier\(/g),
    cssMsLiteral: () => countMatches(WEB, /\b\d+ms\b/g),
  },
};

/**
 * The ladder, carried in the failure message.
 *
 * This message is where most people will meet the vocabulary for the first time,
 * and a ratchet that says only "you added one" sends them to the one instruction it
 * gave, which is to raise the ceiling. Naming three wrapper files does not help
 * either: knowing where the tokens live says nothing about which one to reach for.
 * So the rungs travel with the failure, along with the single line that decides the
 * call people actually get wrong — Change against Emphasis — and a pointer to
 * `docs/motion.md`, which `MotionTokens.kt` calls the normative table.
 */
const RUNG_GUIDE =
  "The ladder, from docs/motion.md, which is the normative table: " +
  "Quick 150 — the app answering a finger that is on it, or something leaving that " +
  "nobody is meant to watch go. " +
  "Enter 200 — one element arriving, and the rung to reach for when a motion has no " +
  "reason to be another length. " +
  "Change 260 — the user's own edit replayed back to them in place, with nothing " +
  "changing position. " +
  "Emphasis 320 — anything that changes where or how big it is; the boundary with " +
  "Change is geometry, not importance. " +
  "Scene 520 — the empty-state illustration rising or sinking, and nothing else. " +
  "Easings, springs and press scales have rows in the same table.";

const BUDGET_FILE = path.join(__dirname, "..", "fixtures", "motion-budget.json");
const describeBudget = existsSync(BUDGET_FILE) ? describe : describe.skip;

describeBudget("hand-written motion literals stay under budget", () => {
  const budget = JSON.parse(read(BUDGET_FILE)) as Record<string, Record<string, number>>;
  const clients = Object.keys(COUNTERS);

  it("measures the clients the budget file names, and only those", () => {
    // A key that drifts out of the fixture would otherwise be a counter nobody
    // checks, which is the quiet way this file stops being a ratchet.
    expect(Object.keys(budget).filter((k) => !k.startsWith("_")).sort()).toEqual(clients.sort());
    for (const client of clients) {
      expect(Object.keys(budget[client]).filter((k) => !k.startsWith("_")).sort())
        .toEqual(Object.keys(COUNTERS[client]).sort());
    }
  });

  it("still excludes the token layer it thinks it is excluding", () => {
    // The exclusions are paths, and a renamed or relocated wrapper would turn into a
    // counted file silently — raising the client's own budget by the size of the file
    // whose whole job is to hold these numbers. A missing exclusion is a bug in this
    // list, so it fails here rather than inflating a count nobody re-reads.
    for (const entry of EXCLUDED) {
      expect(existsSync(entry), `${path.relative(MONO, entry)} no longer exists — fix EXCLUDED`)
        .toBe(true);
    }
  });

  it("found the source trees it is meant to be counting", () => {
    // An empty walk would report zero literals everywhere and pass every budget,
    // which is the one way this check can be green and worthless.
    expect(KT.length).toBeGreaterThan(50);
    expect(SWIFT.length).toBeGreaterThan(50);
    expect(WEB.length).toBeGreaterThan(50);
  });

  for (const client of Object.keys(COUNTERS)) {
    for (const metric of Object.keys(COUNTERS[client])) {
      it(`${client}.${metric} does not grow`, () => {
        const actual = COUNTERS[client][metric]();
        const ceiling = budget[client]?.[metric];
        expect(ceiling, `motion-budget.json has no ceiling for ${client}.${metric}`)
          .toBeTypeOf("number");
        expect(
          actual,
          `${client}.${metric}: ${actual} hand-written literals, ceiling ${ceiling}. ` +
            "Put the new motion on a token from TdayMotionTokens / TdayMotion / " +
            "src/lib/motion.ts. " +
            RUNG_GUIDE +
            " If the value is deliberately not a token, say so at the call site in a " +
            "comment — docs/motion.md's deliberate non-tokens are the form that argument " +
            "takes — and raise the ceiling in tests/fixtures/motion-budget.json in the " +
            "same commit.",
        ).toBeLessThanOrEqual(ceiling);
      });
    }
  }
});

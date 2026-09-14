import { existsSync, readdirSync, readFileSync, statSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

/**
 * THE WEB REDUCED-MOTION FLOOR
 *
 * Phase 5 found a reduced-motion user waiting 520 ms in front of a static
 * illustration: the animation had been switched off and the `setTimeout` in
 * front of it had not, so the preference bought the wait without the motion.
 * That is the fifth idiom rule of `docs/motion.md` failing in the one direction
 * it is written to catch, and it survived review because every guard in the app
 * was local — each animation answering for itself, and the ones nobody thought
 * about answering for nothing.
 *
 * The three rules below are the non-local half: a floor under every animation
 * whether or not its author remembered the preference, a single module owning
 * the one piece of motion CSS cannot reach, and the whole-file question asked of
 * every stylesheet rather than of the animations somebody happened to list.
 *
 * Static reads of source text, like the motion-reachability suites next door:
 * the defect is in what the stylesheet says, and a rendered DOM in jsdom has no
 * cascade to ask.
 */

const ROOT = path.resolve(__dirname, "..", "..");
const SRC = path.join(ROOT, "src");
const GLOBALS_CSS = path.join(SRC, "globals.css");
const IOS_SRC = path.resolve(ROOT, "..", "ios-swiftUI", "Tday");
const IOS_GATE = path.join(IOS_SRC, "UI", "Theme", "TdayMotionEnvironment.swift");

/** Blanks comments, preserving length, so prose about a defect never reads as one. */
function stripComments(css: string): string {
  return css.replace(/\/\*[\s\S]*?\*\//g, (match) => " ".repeat(match.length));
}

function cssFiles(dir: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) {
      // `generated/` is written by `:shared:exportMotionTokens` and declares
      // custom properties only — there is no motion in it to guard.
      if (entry === "generated") continue;
      found.push(...cssFiles(full));
    } else if (entry.endsWith(".css")) {
      found.push(full);
    }
  }
  return found;
}

const GLOBALS = stripComments(readFileSync(GLOBALS_CSS, "utf-8"));

describe("reduced motion A — the blanket floor", () => {
  const floorAt = GLOBALS.indexOf("@media (prefers-reduced-motion: reduce)");

  it("declares a floor at all", () => {
    expect(floorAt, "globals.css declares no prefers-reduced-motion block").toBeGreaterThan(-1);
  });

  /**
   * Position is load-bearing twice over. An `@import` that follows any other
   * rule is discarded silently, so nothing may come between the imports and the
   * floor; and everything the floor exists to override — Tailwind's utilities,
   * `tw-animate-css`'s enters — has to be in the cascade before it.
   */
  it("is the first rule in the file, directly under the imports", () => {
    const lastImport = GLOBALS.lastIndexOf("@import");
    const gap = GLOBALS.slice(GLOBALS.indexOf(";", lastImport) + 1, floorAt);
    expect(gap.trim(), `globals.css: ${gap.trim().split("\n")[0]} sits above the floor`).toBe("");
  });

  /**
   * A floor, not an off switch. `animation: none` takes the fill mode with it,
   * and any element whose resting CSS is its own start frame would then be held
   * there for good — the fifth idiom rule inverted, which is the exact defect
   * this whole phase exists to remove. The delays go to zero for the other half
   * of that rule: removing the motion must not mean keeping the wait.
   */
  it("floors the duration and zeroes the delay, on animations and transitions alike", () => {
    const floor = GLOBALS.slice(floorAt, GLOBALS.indexOf("\n}", floorAt));
    for (const required of [
      /animation-duration:[^;]+!important/,
      /animation-iteration-count:\s*1\s*!important/,
      /animation-delay:\s*0s\s*!important/,
      /transition-duration:[^;]+!important/,
      /transition-delay:\s*0s\s*!important/,
    ]) {
      expect(floor, `the floor is missing ${required}`).toMatch(required);
    }
    expect(floor, "the floor switches animation off instead of flooring it").not.toMatch(
      /\*[^{]*\{[^}]*animation:\s*none/,
    );
  });
});

describe("reduced motion B — one module owns programmatic scrolling", () => {
  /**
   * `scrollIntoView({ behavior: "smooth" })` is documented to override the
   * `scroll-behavior` property rather than defer to it, so the floor above
   * cannot reach a single one of these: a script that asks for smooth gets
   * smooth whatever the user has asked for. `src/lib/scroll.ts` is where the
   * preference is read, and a call site that spells the behavior out for itself
   * has stepped around it.
   */
  it("nothing outside src/lib/scroll.ts asks for a smooth scroll", () => {
    const offenders: string[] = [];
    const walk = (dir: string) => {
      for (const entry of readdirSync(dir)) {
        const full = path.join(dir, entry);
        if (statSync(full).isDirectory()) {
          walk(full);
        } else if (/\.tsx?$/.test(entry) && full !== path.join(SRC, "lib", "scroll.ts")) {
          const source = readFileSync(full, "utf-8").replace(/\/\/[^\n]*|\/\*[\s\S]*?\*\//g, "");
          if (/behavior:\s*["']smooth["']/.test(source)) {
            offenders.push(path.relative(ROOT, full));
          }
        }
      }
    };
    walk(SRC);
    expect(offenders, "route these through src/lib/scroll.ts").toEqual([]);
  });
});

describe("reduced motion C — every stylesheet answers the question", () => {
  /**
   * The whole-file form of the check, because the ledger row that opened this
   * was written against a file with animations and no block at all. A stylesheet
   * that declares motion and never mentions the preference has not decided
   * against guarding it — nobody asked.
   */
  it("every stylesheet that declares motion carries a reduced-motion block", () => {
    const violations: string[] = [];
    for (const file of cssFiles(SRC)) {
      const css = stripComments(readFileSync(file, "utf-8"));
      const declaresMotion = /(^|[\s;{])(animation|transition)(-[a-z-]+)?\s*:/m.test(css);
      if (!declaresMotion) continue;
      if (css.includes("prefers-reduced-motion")) continue;
      violations.push(path.relative(ROOT, file));
    }
    expect(violations).toEqual([]);
  });
});

describe("reduced motion D — one module owns the iOS accessibility read", () => {
  /**
   * The iOS half of rule B, and it was filed for the same reason: seven views
   * each read `accessibilityReduceMotion` and each spelled `reduceMotion ? nil :
   * x` in their own hand, so the app's answer lived in seven places and could be
   * forgotten in an eighth with nothing to notice. `TdayMotionEnvironment.swift`
   * is where the setting is read now, and `\.tdayAnimation` is what every
   * animated surface asks.
   *
   * A static read of source text rather than a compile, because there is no Swift
   * toolchain on this machine — the same reason the `motion-reachability-ios`
   * suite next door reads text. What it can still see is a second answer being
   * minted, which is the whole defect.
   */
  const swiftFiles = (dir: string): string[] => {
    const found: string[] = [];
    for (const entry of readdirSync(dir)) {
      const full = path.join(dir, entry);
      if (statSync(full).isDirectory()) found.push(...swiftFiles(full));
      else if (entry.endsWith(".swift")) found.push(full);
    }
    return found;
  };

  /** Blanks comments, preserving length, so prose naming the key never reads as a read. */
  const stripSwiftComments = (source: string): string =>
    source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, (match) => " ".repeat(match.length));

  /**
   * The one read outside a `View`. `TodoListViewModel.hydrateFromExternalCacheChange`
   * opens the feed's travel from a cache notification, where there is no environment
   * to read and no view to read it in — its own comment says so. It is listed here
   * rather than pattern-matched because "is this a View" is not a question a text
   * scan should be answering.
   */
  const UIKIT_READ_ALLOWED = path.join(IOS_SRC, "Feature", "Todos", "TodoListViewModel.swift");

  it("still sees the module it is about", () => {
    // A renamed or relocated gate would turn this rule into one that passes by
    // finding nothing, which is the quiet way a ratchet stops being one.
    expect(existsSync(IOS_GATE), "TdayMotionEnvironment.swift has moved — fix IOS_GATE").toBe(true);
    expect(
      stripSwiftComments(readFileSync(IOS_GATE, "utf-8")),
      "the gate no longer reads the setting it exists to read",
    ).toMatch(/accessibilityReduceMotion/);
    expect(swiftFiles(IOS_SRC).length, "iOS .swift files").toBeGreaterThan(80);
  });

  /**
   * Both places the gate is installed, because one of them is not obvious and the
   * rule above cannot see either.
   *
   * `AppRootView` reads `\.tdayAnimation` as its own property while applying
   * `tdayAppTheme` — which carries the provider — to its own body, and a property
   * wrapper resolves against the environment the view was *placed* in. So a
   * provider a view installs reaches its children and never itself: every
   * descendant was live and the three animations nearest the root ran on the
   * accessor's fallback, which is right at first draw and silent about the flip.
   * `TdayApp`'s scene is the one place that cannot be got wrong that way, and the
   * theme keeps its own copy for `AppLockWindowHost`'s separate window, which
   * inherits nothing from the scene.
   *
   * What this can see is the call going missing from either file. What it cannot
   * see is the call being applied to the wrong thing inside one — that is the
   * device row's half (`docs/verification/phase-8-device-pass.md`, PR 35a).
   */
  it("installs the gate above the root view and again for the lock window", () => {
    const installs = (file: string) =>
      /\.tdayResolvedMotion\(\)/.test(stripSwiftComments(readFileSync(file, "utf-8")));
    expect(
      installs(path.join(IOS_SRC, "TdayApp.swift")),
      "TdayApp's scene must install .tdayResolvedMotion() ABOVE AppRootView — a provider " +
        "AppRootView installs on its own body never reaches AppRootView's own @Environment",
    ).toBe(true);
    expect(
      installs(path.join(IOS_SRC, "UI", "Theme", "TdayTheme.swift")),
      "tdayAppTheme must keep its .tdayResolvedMotion() — AppLockWindowHost renders into a " +
        "separate window and inherits nothing from the scene",
    ).toBe(true);
  });

  it("nothing outside it reads the accessibility setting for itself", () => {
    const offenders: string[] = [];
    for (const file of swiftFiles(IOS_SRC)) {
      if (file === IOS_GATE) continue;
      const source = stripSwiftComments(readFileSync(file, "utf-8"));
      if (/accessibilityReduceMotion/.test(source)) {
        offenders.push(`${path.relative(ROOT, file)} (read \\.tdayAnimation instead)`);
      }
      if (file !== UIKIT_READ_ALLOWED && /UIAccessibility\.isReduceMotionEnabled/.test(source)) {
        offenders.push(`${path.relative(ROOT, file)} (read \\.tdayAnimation instead)`);
      }
    }
    expect(offenders).toEqual([]);
  });
});

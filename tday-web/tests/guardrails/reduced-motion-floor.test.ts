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

describe("reduced motion E — the pan coordinator is handed the answer it cannot read", () => {
  /**
   * Rule D proves nobody mints a second answer. This proves the one answer
   * REACHES the row, and it is filed because it did not.
   *
   * A task row has two closes. `closeActions` is the one review reads — a
   * SwiftUI method, gated once, with a long comment saying why gating it per
   * caller would be indefensible. The other is the pan's own `.ended` settle,
   * which shuts the row whenever a thumb drags it back toward home and lets go
   * under the detent: the "slide the row back to the right" half of what this
   * feature was asked for. That one lives in a `UIGestureRecognizer` callback on
   * a `UIViewRepresentable.Coordinator`, which is not a view body, has no
   * `@Environment` to read, and so ran a bare 0.34 s spring for every user
   * including the ones who had asked for no motion. It could not fall through to
   * `closeActions` either: `.ended` writes `openRowID = nil` before it touches
   * `offsetX`, so the `.onChange` that turns a freed slot into a close sees a row
   * already reading closed and declines.
   *
   * So the answer has to be PASSED, and what this rule watches is the passing:
   * the property exists on both halves of the representable, the view hands it
   * the environment value, and nothing inside the coordinator animates around it.
   * Android gates the same drag-back release inside
   * `animateTaskSwipeOffsetAsState` and web inside `useSwipeRow`'s `settle`; on
   * those two it is one expression a reader trips over. Here it is a wire, and a
   * wire is the kind of thing that gets removed by someone simplifying a
   * signature.
   *
   * Scoped to this one coordinator on purpose. "Every UIKit coordinator that
   * animates must be handed the resolution" is the general rule and it is the
   * right one, but the general form would have to decide by text scan which of a
   * mixed file's `withAnimation` calls sit in a view body and which do not, and a
   * ratchet that guesses is a ratchet that gets exemptions bolted onto it. This
   * is the coordinator the defect was found in and the only one in the app that
   * animates; when a second one appears, generalise then.
   */
  const SWIPE_ACTIONS = path.join(IOS_SRC, "UI", "Component", "SwipeActions.swift");

  /** The `final class Coordinator { … }` body, brace-matched from its header. */
  function coordinatorBody(source: string): string {
    const header = /final\s+class\s+Coordinator\b[^{]*\{/.exec(source);
    if (!header) return "";
    let depth = 0;
    for (let i = header.index + header[0].length - 1; i < source.length; i += 1) {
      if (source[i] === "{") depth += 1;
      else if (source[i] === "}") {
        depth -= 1;
        if (depth === 0) return source.slice(header.index, i + 1);
      }
    }
    return "";
  }

  const stripSwiftComments = (source: string): string =>
    source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, (match) => " ".repeat(match.length));

  it("still sees the file it is about", () => {
    // The quiet way a ratchet stops being one: the file moves, the scan finds
    // nothing, and finding nothing reads as passing.
    expect(existsSync(SWIPE_ACTIONS), "SwipeActions.swift has moved — fix SWIPE_ACTIONS").toBe(
      true,
    );
    expect(
      coordinatorBody(stripSwiftComments(readFileSync(SWIPE_ACTIONS, "utf-8"))),
      "no `final class Coordinator` in SwipeActions.swift — this rule is scanning nothing",
    ).not.toBe("");
  });

  it("carries the resolution across the SwiftUI/UIKit line", () => {
    const source = stripSwiftComments(readFileSync(SWIPE_ACTIONS, "utf-8"));
    expect(
      /let\s+motion:\s*TdayMotionResolution/.test(source),
      "HorizontalSwipePanObserver must take the resolution as a property — a coordinator " +
        "cannot read \\.tdayAnimation for itself",
    ).toBe(true);
    expect(
      /motion:\s*tdayAnimation/.test(source),
      "the row must hand the observer its own \\.tdayAnimation — the environment value is " +
        "only live on the SwiftUI side",
    ).toBe(true);
    expect(
      /coordinator\.motion\s*=\s*motion/.test(source),
      "updateUIView must copy the resolution onto the coordinator, or the flip never " +
        "reaches the recognizer",
    ).toBe(true);
  });

  it("animates nothing inside the coordinator around it", () => {
    const body = coordinatorBody(stripSwiftComments(readFileSync(SWIPE_ACTIONS, "utf-8")));
    const bare = [...body.matchAll(/withAnimation\(\s*(?!motion\()/g)];
    expect(
      bare.length,
      "a `withAnimation` in the pan coordinator that does not go through `motion(...)`: the " +
        "drag-back close would spring for a reader who asked for no motion, while the same " +
        "row shut by a pill is drawn home in one frame",
    ).toBe(0);
    // And the rule is reading something: the settle this was filed for is still here.
    expect(
      /withAnimation\(motion\(/.test(body),
      "the coordinator no longer animates at all — if the settle moved, move this rule",
    ).toBe(true);
  });
});

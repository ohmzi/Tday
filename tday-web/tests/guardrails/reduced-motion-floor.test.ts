import { readdirSync, readFileSync, statSync } from "fs";
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
